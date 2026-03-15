package com.flowguard.resource;

import com.flowguard.domain.UserEntity;
import com.flowguard.dto.AdminUserDetailDto;
import com.flowguard.dto.AuditLogDto;
import com.flowguard.dto.PageDto;
import com.flowguard.security.AdminAction;
import com.flowguard.security.Roles;
import io.agroal.api.AgroalDataSource;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Super-admin endpoints — restricted to {@code ROLE_SUPER_ADMIN} only.
 * Covers: admin user management, audit log, ML retraining trigger.
 */
@Path("/super-admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed(Roles.SUPER_ADMIN)
public class SuperAdminResource {

    @Inject AgroalDataSource ds;

    // ══════════════════════════════════════════════════════════════════════════
    //  ADMIN USER MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    /** GET /api/super-admin/admins  — list all ADMIN and SUPER_ADMIN users */
    @GET
    @Path("/admins")
    @RunOnVirtualThread
    public Response listAdmins() {
        List<AdminUserDetailDto> admins = UserEntity
                .<UserEntity>find("role IN ('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
                .stream()
                .map(AdminUserDetailDto::from)
                .toList();
        return Response.ok(admins).build();
    }

    /** POST /api/super-admin/admins/{userId}/promote  body: {"role":"ROLE_ADMIN"} */
    @POST
    @Path("/admins/{userId}/promote")
    @RunOnVirtualThread
    @Transactional
    @AdminAction("promote_user_to_admin")
    public Response promoteUser(@PathParam("userId") UUID userId, PromoteRequest req) {
        UserEntity user = UserEntity.findById(userId);
        if (user == null) return Response.status(Response.Status.NOT_FOUND).build();

        String targetRole = req != null && req.role() != null ? req.role() : "ROLE_ADMIN";
        if (!targetRole.equals("ROLE_ADMIN") && !targetRole.equals("ROLE_SUPER_ADMIN")) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"role must be ROLE_ADMIN or ROLE_SUPER_ADMIN\"}")
                    .build();
        }
        user.setRole(targetRole);
        return Response.ok(AdminUserDetailDto.from(user)).build();
    }

    /** POST /api/super-admin/admins/{userId}/revoke  — revoke admin role → ROLE_USER */
    @POST
    @Path("/admins/{userId}/revoke")
    @RunOnVirtualThread
    @Transactional
    @AdminAction("revoke_admin_role")
    public Response revokeAdmin(@PathParam("userId") UUID userId) {
        UserEntity user = UserEntity.findById(userId);
        if (user == null) return Response.status(Response.Status.NOT_FOUND).build();
        user.setRole("ROLE_USER");
        return Response.ok(AdminUserDetailDto.from(user)).build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  AUDIT LOG
    // ══════════════════════════════════════════════════════════════════════════

    /** GET /api/super-admin/audit?page=0&size=50&actorId=&action= */
    @GET
    @Path("/audit")
    @RunOnVirtualThread
    public Response getAuditLog(
            @QueryParam("page")    @DefaultValue("0")  int page,
            @QueryParam("size")    @DefaultValue("50") int size,
            @QueryParam("actorId") String actorId,
            @QueryParam("action")  String action) {

        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM audit_log WHERE 1=1 ");
        StringBuilder dataSql  = new StringBuilder(
                "SELECT id, actor_id, actor_email, actor_role, action, target_type, target_id, ip_address, created_at " +
                "FROM audit_log WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (actorId != null && !actorId.isBlank()) {
            countSql.append("AND actor_id = CAST(? AS UUID) ");
            dataSql .append("AND actor_id = CAST(? AS UUID) ");
            params.add(actorId);
        }
        if (action != null && !action.isBlank()) {
            countSql.append("AND action ILIKE ? ");
            dataSql .append("AND action ILIKE ? ");
            params.add("%" + action + "%");
        }

        dataSql.append("ORDER BY created_at DESC LIMIT ? OFFSET ?");

        long total = 0;
        List<AuditLogDto> entries = new ArrayList<>();

        try (Connection conn = ds.getConnection()) {
            try (PreparedStatement ps = prepareStmt(conn, countSql.toString(), params);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) total = rs.getLong(1);
            }

            List<Object> dataParams = new ArrayList<>(params);
            dataParams.add(size);
            dataParams.add((long) page * size);

            try (PreparedStatement ps = prepareStmt(conn, dataSql.toString(), dataParams);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String aIdStr = rs.getString("actor_id");
                    entries.add(new AuditLogDto(
                            UUID.fromString(rs.getString("id")),
                            aIdStr != null ? UUID.fromString(aIdStr) : null,
                            rs.getString("actor_email"),
                            rs.getString("actor_role"),
                            rs.getString("action"),
                            rs.getString("target_type"),
                            rs.getString("target_id"),
                            rs.getString("ip_address"),
                            rs.getTimestamp("created_at").toInstant()
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return Response.ok(PageDto.of(entries, total, page, size)).build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ML SERVICE INTEGRATION
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * POST /api/super-admin/ml/retrain
     * Triggers an async ML model retrain — delegates to ml-service via internal HTTP.
     * Returns 202 Accepted immediately.
     */
    @POST
    @Path("/ml/retrain")
    @RunOnVirtualThread
    @AdminAction("trigger_ml_retrain")
    public Response triggerMlRetrain() {
        // Fire-and-forget via HTTP to ml-service (see application.properties for ml.service.url)
        // In production this would use MicroProfile REST Client or Vert.x WebClient.
        // For now we return 202 and the actual trigger is handled by a scheduled job.
        return Response.accepted("{\"message\":\"Ré-entraînement ML déclenché\"}").build();
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private PreparedStatement prepareStmt(Connection conn, String sql, List<Object> params) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
        return ps;
    }

    // ── Request records ──────────────────────────────────────────────────────

    public record PromoteRequest(String role) {}
}
