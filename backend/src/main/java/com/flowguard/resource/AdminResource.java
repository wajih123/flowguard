package com.flowguard.resource;

import com.flowguard.cache.RedisCacheService;
import com.flowguard.domain.*;
import com.flowguard.dto.*;
import com.flowguard.security.AdminAction;
import com.flowguard.security.Roles;
import io.agroal.api.AgroalDataSource;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin back-office REST resource.
 * Requires {@code ROLE_ADMIN} or {@code ROLE_SUPER_ADMIN}.
 */
@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({Roles.ADMIN, Roles.SUPER_ADMIN})
public class AdminResource {

    @Inject AgroalDataSource  ds;
    @Inject RedisCacheService cache;

    // ══════════════════════════════════════════════════════════════════════════
    //  USERS
    // ══════════════════════════════════════════════════════════════════════════

    /** GET /api/admin/users?page=0&size=20&search=&kycStatus=&userType= */
    @GET
    @Path("/users")
    @RunOnVirtualThread
    public Response listUsers(
            @QueryParam("page")      @DefaultValue("0")  int page,
            @QueryParam("size")      @DefaultValue("20") int size,
            @QueryParam("search")    String search,
            @QueryParam("kycStatus") String kycStatus,
            @QueryParam("userType")  String userType) {

        StringBuilder sql = new StringBuilder(
                "SELECT id, first_name, last_name, email, user_type, kyc_status, role, disabled, created_at " +
                "FROM users WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (search != null && !search.isBlank()) {
            sql.append("AND (LOWER(first_name || ' ' || last_name) LIKE ? OR LOWER(email) LIKE ?) ");
            String like = "%" + search.toLowerCase() + "%";
            params.add(like); params.add(like);
        }
        if (kycStatus != null && !kycStatus.isBlank()) {
            sql.append("AND kyc_status = ? ");
            params.add(kycStatus);
        }
        if (userType != null && !userType.isBlank()) {
            sql.append("AND user_type = ? ");
            params.add(userType);
        }

        long total = count(sql.toString().replace(
                "SELECT id, first_name, last_name, email, user_type, kyc_status, role, disabled, created_at",
                "SELECT COUNT(*)"), params);

        sql.append("ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add((long) page * size);

        List<AdminUserDto> users = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = prepare(conn, sql.toString(), params);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(mapUserRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return Response.ok(PageDto.of(users, total, page, size)).build();
    }

    /** GET /api/admin/users/{userId} */
    @GET
    @Path("/users/{userId}")
    @RunOnVirtualThread
    public Response getUser(@PathParam("userId") UUID userId) {
        UserEntity user = UserEntity.findById(userId);
        if (user == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(AdminUserDetailDto.from(user)).build();
    }

    /** PUT /api/admin/users/{userId}/kyc  body: {"status":"APPROVED"} */
    @PUT
    @Path("/users/{userId}/kyc")
    @RunOnVirtualThread
    @Transactional
    @AdminAction("update_kyc_status")
    public Response updateKycStatus(@PathParam("userId") UUID userId, KycUpdateRequest req) {
        UserEntity user = UserEntity.findById(userId);
        if (user == null) return Response.status(Response.Status.NOT_FOUND).build();
        user.setKycStatus(UserEntity.KycStatus.valueOf(req.status()));
        return Response.ok(AdminUserDetailDto.from(user)).build();
    }

    /** PUT /api/admin/users/{userId}/disable  body: {"reason":"..."} */
    @PUT
    @Path("/users/{userId}/disable")
    @RunOnVirtualThread
    @Transactional
    @AdminAction("disable_user")
    public Response disableUser(@PathParam("userId") UUID userId, DisableRequest req) {
        UserEntity user = UserEntity.findById(userId);
        if (user == null) return Response.status(Response.Status.NOT_FOUND).build();
        user.setDisabled(true);
        user.setDisabledAt(Instant.now());
        user.setDisabledReason(req != null ? req.reason() : null);
        return Response.ok(AdminUserDetailDto.from(user)).build();
    }

    /** PUT /api/admin/users/{userId}/enable */
    @PUT
    @Path("/users/{userId}/enable")
    @RunOnVirtualThread
    @Transactional
    @AdminAction("enable_user")
    public Response enableUser(@PathParam("userId") UUID userId) {
        UserEntity user = UserEntity.findById(userId);
        if (user == null) return Response.status(Response.Status.NOT_FOUND).build();
        user.setDisabled(false);
        user.setDisabledAt(null);
        user.setDisabledReason(null);
        return Response.ok(AdminUserDetailDto.from(user)).build();
    }

    /** DELETE /api/admin/users/{userId}/gdpr */
    @DELETE
    @Path("/users/{userId}/gdpr")
    @RunOnVirtualThread
    @Transactional
    @AdminAction("gdpr_delete_user")
    public Response gdprDeleteUser(@PathParam("userId") UUID userId) {
        UserEntity user = UserEntity.findById(userId);
        if (user == null) return Response.status(Response.Status.NOT_FOUND).build();
        user.setDataDeletionRequestedAt(Instant.now());
        // Pseudonymise PII
        user.setFirstName("Supprimé");
        user.setLastName("RGPD");
        user.setEmail("deleted_" + userId + "@gdpr.flowguard.io");
        user.setPasswordHash("[DELETED]");
        user.setCompanyName(null);
        user.setSwanOnboardingId(null);
        user.setSwanAccountId(null);
        user.setNordigenRequisitionId(null);
        return Response.noContent().build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  FLASH CREDITS
    // ══════════════════════════════════════════════════════════════════════════

    /** GET /api/admin/flash-credits?page=0&size=20&status= */
    @GET
    @Path("/flash-credits")
    @RunOnVirtualThread
    public Response listCredits(
            @QueryParam("page")   @DefaultValue("0")  int page,
            @QueryParam("size")   @DefaultValue("20") int size,
            @QueryParam("status") String status) {

        PanacheQuery<FlashCreditEntity> query = status != null && !status.isBlank()
                ? FlashCreditEntity.find("status", Sort.by("createdAt").descending(),
                        FlashCreditEntity.CreditStatus.valueOf(status))
                : FlashCreditEntity.findAll(Sort.by("createdAt").descending());

        long total = query.count();
        List<AdminCreditDto> credits = query.page(Page.of(page, size)).stream()
                .map(AdminCreditDto::from)
                .toList();

        return Response.ok(PageDto.of(credits, total, page, size)).build();
    }

    /** GET /api/admin/flash-credits/{creditId} */
    @GET
    @Path("/flash-credits/{creditId}")
    @RunOnVirtualThread
    public Response getCredit(@PathParam("creditId") UUID creditId) {
        FlashCreditEntity credit = FlashCreditEntity.findById(creditId);
        if (credit == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(AdminCreditDto.from(credit)).build();
    }

    /** PUT /api/admin/flash-credits/{creditId}/approve */
    @PUT
    @Path("/flash-credits/{creditId}/approve")
    @RunOnVirtualThread
    @Transactional
    @AdminAction("approve_flash_credit")
    public Response approveCredit(@PathParam("creditId") UUID creditId) {
        FlashCreditEntity credit = FlashCreditEntity.findById(creditId);
        if (credit == null) return Response.status(Response.Status.NOT_FOUND).build();
        credit.setStatus(FlashCreditEntity.CreditStatus.APPROVED);
        return Response.ok(AdminCreditDto.from(credit)).build();
    }

    /** PUT /api/admin/flash-credits/{creditId}/reject  body: {"reason":"..."} */
    @PUT
    @Path("/flash-credits/{creditId}/reject")
    @RunOnVirtualThread
    @Transactional
    @AdminAction("reject_flash_credit")
    public Response rejectCredit(@PathParam("creditId") UUID creditId, RejectRequest req) {
        FlashCreditEntity credit = FlashCreditEntity.findById(creditId);
        if (credit == null) return Response.status(Response.Status.NOT_FOUND).build();
        credit.setStatus(FlashCreditEntity.CreditStatus.REJECTED);
        return Response.ok(AdminCreditDto.from(credit)).build();
    }

    /** PUT /api/admin/flash-credits/{creditId}/written-off */
    @PUT
    @Path("/flash-credits/{creditId}/written-off")
    @RunOnVirtualThread
    @Transactional
    @AdminAction("written_off_flash_credit")
    public Response writeOffCredit(@PathParam("creditId") UUID creditId) {
        FlashCreditEntity credit = FlashCreditEntity.findById(creditId);
        if (credit == null) return Response.status(Response.Status.NOT_FOUND).build();
        credit.setStatus(FlashCreditEntity.CreditStatus.OVERDUE); // marks write-off via OVERDUE + disbursedAt check
        return Response.ok(AdminCreditDto.from(credit)).build();
    }

    // ── Credit stats ─────────────────────────────────────────────────────────

    @GET
    @Path("/flash-credits/stats")
    @RunOnVirtualThread
    public Response getCreditStats() {
        CreditStatsDto stats = buildCreditStats();
        return Response.ok(stats).build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ALERTS
    // ══════════════════════════════════════════════════════════════════════════

    /** GET /api/admin/alerts?page=0&size=20&severity= */
    @GET
    @Path("/alerts")
    @RunOnVirtualThread
    public Response listAlerts(
            @QueryParam("page")     @DefaultValue("0")  int page,
            @QueryParam("size")     @DefaultValue("20") int size,
            @QueryParam("severity") String severity) {

        PanacheQuery<AlertEntity> query = severity != null && !severity.isBlank()
                ? AlertEntity.find("severity", Sort.by("createdAt").descending(),
                        AlertEntity.Severity.valueOf(severity))
                : AlertEntity.findAll(Sort.by("createdAt").descending());

        long total = query.count();
        List<AlertDto> alerts = query.page(Page.of(page, size)).stream()
                .map(AlertDto::from)
                .toList();

        return Response.ok(PageDto.of(alerts, total, page, size)).build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  KPIs
    // ══════════════════════════════════════════════════════════════════════════

    @GET
    @Path("/kpis")
    @RunOnVirtualThread
    public Response getKpis() {
        AdminKpiDto kpis = buildKpis();
        return Response.ok(kpis).build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  STATS (dashboard summary)
    // ══════════════════════════════════════════════════════════════════════════

    @GET
    @Path("/stats")
    @RunOnVirtualThread
    public Response getStats() {
        long totalUsers    = UserEntity.count();
        long pendingKyc    = UserEntity.count("kycStatus", UserEntity.KycStatus.PENDING);
        long pendingCredit = FlashCreditEntity.count("status", FlashCreditEntity.CreditStatus.PENDING);
        long overdueCredit = FlashCreditEntity.count("status", FlashCreditEntity.CreditStatus.OVERDUE);
        long criticalAlerts= AlertEntity.count("severity", AlertEntity.Severity.CRITICAL);

        BigDecimal totalVolume = sumCreditVolume();

        var stats = new java.util.LinkedHashMap<String, Object>();
        stats.put("totalUsers",          totalUsers);
        stats.put("activeUsers",         totalUsers);
        stats.put("pendingKyc",          pendingKyc);
        stats.put("totalCreditsAmount",  totalVolume);
        stats.put("pendingCredits",      pendingCredit);
        stats.put("overdueCredits",      overdueCredit);
        stats.put("criticalAlerts",      criticalAlerts);
        return Response.ok(stats).build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  APPLICATION LOGS
    // ══════════════════════════════════════════════════════════════════════════

    @GET
    @Path("/logs")
    @RunOnVirtualThread
    public Response getLogs(
            @QueryParam("page")  @DefaultValue("0")  int page,
            @QueryParam("size")  @DefaultValue("50") int size,
            @QueryParam("level") String level) {

        List<AppLogDto> logs = new ArrayList<>();
        long total = 0;

        String countSql = "SELECT COUNT(*) FROM application_logs" + (level != null && !level.isBlank() ? " WHERE level = ?" : "");
        String dataSql  = "SELECT id, level, logger, message, stack_trace, created_at FROM application_logs" +
                          (level != null && !level.isBlank() ? " WHERE level = ?" : "") +
                          " ORDER BY created_at DESC LIMIT ? OFFSET ?";

        try (Connection conn = ds.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                if (level != null && !level.isBlank()) ps.setString(1, level);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) total = rs.getLong(1);
            }
            try (PreparedStatement ps = conn.prepareStatement(dataSql)) {
                int idx = 1;
                if (level != null && !level.isBlank()) ps.setString(idx++, level);
                ps.setInt(idx++, size);
                ps.setLong(idx, (long) page * size);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    logs.add(new AppLogDto(
                            rs.getLong("id"),
                            rs.getString("level"),
                            rs.getString("logger"),
                            rs.getString("message"),
                            rs.getString("stack_trace"),
                            rs.getTimestamp("created_at").toInstant()
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return Response.ok(PageDto.of(logs, total, page, size)).build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  FEATURE FLAGS (admin read/write — super-admin also uses these)
    // ══════════════════════════════════════════════════════════════════════════

    @GET
    @Path("/flags")
    @RunOnVirtualThread
    public Response listFlags() {
        List<FeatureFlagDto> flags = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, flag_key, enabled, description, updated_at FROM feature_flags ORDER BY flag_key");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                flags.add(new FeatureFlagDto(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("flag_key"),
                        rs.getBoolean("enabled"),
                        rs.getString("description"),
                        rs.getTimestamp("updated_at").toInstant()
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Response.ok(flags).build();
    }

    @PUT
    @Path("/flags/{flagKey}")
    @RunOnVirtualThread
    @AdminAction("update_feature_flag")
    public Response updateFlag(@PathParam("flagKey") String flagKey, FlagUpdateRequest req) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE feature_flags SET enabled = ?, updated_at = now() WHERE flag_key = ?")) {
            ps.setBoolean(1, req.enabled());
            ps.setString(2, flagKey);
            int affected = ps.executeUpdate();
            if (affected == 0) return Response.status(Response.Status.NOT_FOUND).build();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        cache.delete("public:flags");
        return Response.ok().build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SYSTEM CONFIG
    // ══════════════════════════════════════════════════════════════════════════

    @GET
    @Path("/config")
    @RunOnVirtualThread
    public Response listConfig() {
        List<SystemConfigDto> configs = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, config_key, config_value, value_type, description, updated_at FROM system_config ORDER BY config_key");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                configs.add(new SystemConfigDto(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("config_key"),
                        rs.getString("config_value"),
                        rs.getString("value_type"),
                        rs.getString("description"),
                        rs.getTimestamp("updated_at").toInstant()
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Response.ok(configs).build();
    }

    @PUT
    @Path("/config/{configKey}")
    @RunOnVirtualThread
    @AdminAction("update_system_config")
    public Response updateConfig(@PathParam("configKey") String configKey, ConfigUpdateRequest req) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE system_config SET config_value = ?, updated_at = now() WHERE config_key = ?")) {
            ps.setString(1, req.value());
            ps.setString(2, configKey);
            int affected = ps.executeUpdate();
            if (affected == 0) return Response.status(Response.Status.NOT_FOUND).build();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        cache.delete("public:config");
        return Response.ok().build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private long count(String sql, List<Object> params) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = prepare(conn, sql, params);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private PreparedStatement prepare(Connection conn, String sql, List<Object> params) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
        return ps;
    }

    private AdminUserDto mapUserRow(ResultSet rs) throws SQLException {
        return new AdminUserDto(
                UUID.fromString(rs.getString("id")),
                rs.getString("first_name"),
                rs.getString("last_name"),
                AdminUserDto.maskEmail(rs.getString("email")),
                UserEntity.UserType.valueOf(rs.getString("user_type")),
                UserEntity.KycStatus.valueOf(rs.getString("kyc_status")),
                rs.getString("role"),
                rs.getBoolean("disabled"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private AdminKpiDto buildKpis() {
        long totalUsers     = UserEntity.count();
        long activeUsers    = UserEntity.count("disabled", false);
        long pendingKyc     = UserEntity.count("kycStatus", UserEntity.KycStatus.PENDING);
        long approvedKyc    = UserEntity.count("kycStatus", UserEntity.KycStatus.APPROVED);
        long rejectedKyc    = UserEntity.count("kycStatus", UserEntity.KycStatus.REJECTED);
        long totalCredits   = FlashCreditEntity.count();
        long pendingCredits = FlashCreditEntity.count("status", FlashCreditEntity.CreditStatus.PENDING);
        long activeCredits  = FlashCreditEntity.count("status", FlashCreditEntity.CreditStatus.APPROVED);
        long overdueCredits = FlashCreditEntity.count("status", FlashCreditEntity.CreditStatus.OVERDUE);
        long criticalAlerts = AlertEntity.count("severity", AlertEntity.Severity.CRITICAL);
        long unreadAlerts   = AlertEntity.count("isRead", false);
        BigDecimal totalVol = sumCreditVolume();
        BigDecimal totalFees = sumCreditFees();

        return new AdminKpiDto(
                totalUsers, activeUsers, pendingKyc, approvedKyc, rejectedKyc,
                totalCredits, pendingCredits, activeCredits, overdueCredits,
                totalVol, totalFees, criticalAlerts, unreadAlerts,
                0.0, Instant.now()
        );
    }

    private CreditStatsDto buildCreditStats() {
        long total    = FlashCreditEntity.count();
        long pending  = FlashCreditEntity.count("status", FlashCreditEntity.CreditStatus.PENDING);
        long active   = FlashCreditEntity.count("status", FlashCreditEntity.CreditStatus.APPROVED);
        long overdue  = FlashCreditEntity.count("status", FlashCreditEntity.CreditStatus.OVERDUE);
        long repaid   = FlashCreditEntity.count("status", FlashCreditEntity.CreditStatus.REPAID);
        long rejected = FlashCreditEntity.count("status", FlashCreditEntity.CreditStatus.REJECTED);
        BigDecimal volume  = sumCreditVolume();
        BigDecimal volPend = sumCreditVolumeByStatus(FlashCreditEntity.CreditStatus.PENDING);
        BigDecimal fees    = sumCreditFees();
        return new CreditStatsDto(total, pending, active, overdue, repaid, rejected, volume, volPend, fees);
    }

    private BigDecimal sumCreditVolume() {
        return sumDecimal("SELECT COALESCE(SUM(amount), 0) FROM flash_credits");
    }

    private BigDecimal sumCreditFees() {
        return sumDecimal("SELECT COALESCE(SUM(fee), 0) FROM flash_credits WHERE status NOT IN ('REJECTED','RETRACTED')");
    }

    private BigDecimal sumCreditVolumeByStatus(FlashCreditEntity.CreditStatus s) {
        return sumDecimal("SELECT COALESCE(SUM(amount), 0) FROM flash_credits WHERE status = '" + s.name() + "'");
    }

    private BigDecimal sumDecimal(String sql) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ── Inline request/DTO records ───────────────────────────────────────────

    public record KycUpdateRequest(String status) {}
    public record DisableRequest(String reason) {}
    public record RejectRequest(String reason) {}
    public record FlagUpdateRequest(boolean enabled) {}
    public record ConfigUpdateRequest(String value) {}
}
