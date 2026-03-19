package com.flowguard.resource;

import com.flowguard.cache.RedisCacheService;
import com.flowguard.domain.FeatureFlagEntity;
import com.flowguard.domain.SystemConfigEntity;
import com.flowguard.dto.FeatureFlagDto;
import com.flowguard.dto.SystemConfigDto;
import com.flowguard.repository.FeatureFlagRepository;
import com.flowguard.repository.SystemConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;

/**
 * Public (unauthenticated) config endpoints consumed by web/mobile to load
 * feature flags and non-sensitive system settings at startup.
 * Results are cached in Redis for 5 minutes.
 */
@Path("/config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
public class PublicResource {

    private static final int    CACHE_TTL        = 300;
    private static final String FLAGS_CACHE_KEY  = "public:flags";
    private static final String CONFIG_CACHE_KEY = "public:config";

    @Inject RedisCacheService    cache;
    @Inject ObjectMapper         mapper;
    @Inject FeatureFlagRepository flagRepository;
    @Inject SystemConfigRepository configRepository;

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
        try {
            for (FeatureFlagEntity entity : flagRepository.listAll()) {
                result.add(new FeatureFlagDto(
                        entity.id,
                        entity.flagKey,
                        entity.enabled,
                        entity.description,
                        entity.updatedAt
                ));
            }
        } catch (Exception e) {
            // If repository fails (e.g., table doesn't exist yet), return empty list
            // This can happen during initial startup before schema is created
        }
        return result;
    }

    private List<SystemConfigDto> loadSystemConfig() {
        List<SystemConfigDto> result = new ArrayList<>();
        try {
            for (SystemConfigEntity entity : configRepository.listAll()) {
                result.add(new SystemConfigDto(
                        entity.id,
                        entity.configKey,
                        entity.configValue,
                        entity.valueType,
                        entity.description,
                        entity.updatedAt
                ));
            }
        } catch (Exception e) {
            // If repository fails (e.g., table doesn't exist yet), return empty list
            // This can happen during initial startup before schema is created
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
