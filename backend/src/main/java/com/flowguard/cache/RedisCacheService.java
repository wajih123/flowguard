package com.flowguard.cache;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.redis.client.RedisClient;
import io.vertx.redis.client.Response;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class RedisCacheService {

    private static final Logger LOG = Logger.getLogger(RedisCacheService.class);

    @Inject
    RedisClient redisClient;

    @ConfigProperty(name = "redis.ttl.default", defaultValue = "3600")
    int defaultTtl;

    /** In-memory fallback used when Redis is unavailable (e.g. test environment). */
    private final ConcurrentHashMap<String, String> fallback = new ConcurrentHashMap<>();
    private volatile boolean useFallback = false;

    public void set(String key, String value, int ttlSeconds) {
        if (useFallback) {
            fallback.put(key, value);
            return;
        }
        try {
            redisClient.setex(key, String.valueOf(ttlSeconds), value);
        } catch (Exception e) {
            LOG.warnf("Redis unavailable, switching to in-memory fallback: %s", e.getMessage());
            useFallback = true;
            fallback.put(key, value);
        }
    }

    public void set(String key, String value) {
        set(key, value, defaultTtl);
    }

    public String get(String key) {
        if (useFallback) {
            return fallback.get(key);
        }
        try {
            Response resp = redisClient.get(key);
            return resp == null ? null : resp.toString();
        } catch (Exception e) {
            LOG.warnf("Redis unavailable, switching to in-memory fallback: %s", e.getMessage());
            useFallback = true;
            return fallback.get(key);
        }
    }

    public void delete(String key) {
        if (useFallback) {
            fallback.remove(key);
            return;
        }
        try {
            redisClient.del(List.of(key));
        } catch (Exception e) {
            LOG.warnf("Redis unavailable, switching to in-memory fallback: %s", e.getMessage());
            useFallback = true;
            fallback.remove(key);
        }
    }
}
