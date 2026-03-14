package com.flowguard.resource;

import com.flowguard.cache.RedisCacheService;
import com.flowguard.dto.FeatureFlagDto;
import com.flowguard.dto.SystemConfigDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Public (unauthenticated) config endpoints consumed by web/mobile to load
 * feature flags and non-sensitive system settings at startup.
 * Results are cached in Redis for 5 minutes.
 */
@Path("/api/config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
public class PublicResource {

    private static final int    CACHE_TTL        = 300;
    private static final String FLAGS_CACHE_KEY  = "public:flags";
    private static final String CONFIG_CACHE_KEY = "public:config";

    @Inject AgroalDataSource  ds;
    @Inject RedisCacheService cache;
    @Inject ObjectMapper      mapper;

    // ── GET /api/config/flags ────────────────────────────────────────────────

    @GET
    @Path("/flags")
    @RunOnVirtualThread
    public Response getFlags() {
        String cached = cache.get(FLAGS_CACHE_KEY);
        if (cached != null) {
            return Response.ok(cached).type(MediaType.APPLICATION_JSON).build();
        }
        List<FeatureFlagDto> flags = loadFlags();
        cacheAndReturn(FLAGS_CACHE_KEY, flags);
        return Response.ok(flags).build();
    }

    // ── GET /api/config/system ───────────────────────────────────────────────

    @GET
    @Path("/system")
    @RunOnVirtualThread
    public Response getSystemConfig() {
        String cached = cache.get(CONFIG_CACHE_KEY);
        if (cached != null) {
            return Response.ok(cached).type(MediaType.APPLICATION_JSON).build();
        }
        List<SystemConfigDto> configs = loadSystemConfig();
        cacheAndReturn(CONFIG_CACHE_KEY, configs);
        return Response.ok(configs).build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<FeatureFlagDto> loadFlags() {
        List<FeatureFlagDto> result = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, flag_key, enabled, description, updated_at FROM feature_flags ORDER BY flag_key");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new FeatureFlagDto(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("flag_key"),
                        rs.getBoolean("enabled"),
                        rs.getString("description"),
                        rs.getTimestamp("updated_at").toInstant()
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load feature flags", e);
        }
        return result;
    }

    private List<SystemConfigDto> loadSystemConfig() {
        List<SystemConfigDto> result = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, config_key, config_value, value_type, description, updated_at FROM system_config ORDER BY config_key");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new SystemConfigDto(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("config_key"),
                        rs.getString("config_value"),
                        rs.getString("value_type"),
                        rs.getString("description"),
                        rs.getTimestamp("updated_at").toInstant()
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load system config", e);
        }
        return result;
    }

    private void cacheAndReturn(String key, Object value) {
        try {
            cache.set(key, mapper.writeValueAsString(value), CACHE_TTL);
        } catch (Exception ignored) {
            // Cache population failure is non-fatal
        }
    }
}
