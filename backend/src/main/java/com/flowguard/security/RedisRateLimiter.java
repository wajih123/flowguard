package com.flowguard.security;

import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * Redis-backed distributed rate limiter using a sliding-window counter.
 *
 * <p>Uses Redis INCR + EXPIRE pattern:
 * <ul>
 *   <li>Key: {@code rl:{scope}:{identifier}}
 *   <li>Value: counter (incremented each attempt)
 *   <li>TTL: window duration (auto-expires)
 * </ul>
 *
 * <p>This replaces the in-memory {@link RateLimiter} for production deployments
 * where multiple backend instances must share state.
 */
@ApplicationScoped
public class RedisRateLimiter {

    private static final Logger LOG = Logger.getLogger(RedisRateLimiter.class);

    /* Login brute-force protection */
    public static final int LOGIN_MAX_ATTEMPTS   = 5;
    public static final int LOGIN_WINDOW_SECONDS = 900;   // 15 min

    /* Global API rate limit per authenticated user */
    public static final int GLOBAL_MAX_RPM       = 100;
    public static final int GLOBAL_WINDOW_SECONDS = 60;    // 1 min

    /* IP blocklist: auto-block after too many failures */
    private static final int BLOCK_THRESHOLD    = 50;
    private static final int BLOCK_WINDOW_HOURS = 1;

    @Inject
    RedisDataSource redis;

    /**
     * Check and record a login attempt for the given key (email or IP).
     *
     * @throws RateLimitExceededException if the login rate limit is reached
     */
    public void checkLogin(String identifier) {
        String key = "rl:login:" + identifier;
        long count = increment(key, LOGIN_WINDOW_SECONDS);
        if (count > LOGIN_MAX_ATTEMPTS) {
            long ttl = getTtl(key);
            throw new RateLimitExceededException(
                "Trop de tentatives de connexion. Réessayez dans " + (ttl / 60 + 1) + " minutes.",
                ttl
            );
        }
    }

    /**
     * Check and record a global API request for the given user ID.
     *
     * @throws RateLimitExceededException if the per-minute limit is reached
     */
    public void checkGlobal(String userId) {
        String key = "rl:global:" + userId;
        long count = increment(key, GLOBAL_WINDOW_SECONDS);
        if (count > GLOBAL_MAX_RPM) {
            throw new RateLimitExceededException(
                "Limite d'appels API atteinte. Maximum " + GLOBAL_MAX_RPM + " req/min.",
                GLOBAL_WINDOW_SECONDS
            );
        }
    }

    /**
     * Check if an IP is in the blocklist.
     */
    public boolean isBlocked(String ip) {
        String key = "rl:blocked:" + ip;
        try {
            return redis.value(String.class).get(key) != null;
        } catch (Exception e) {
            LOG.warnf("Redis unavailable for blocklist check: %s", e.getMessage());
            return false; // Fail open on Redis outage
        }
    }

    /**
     * Record an IP failure and auto-block after threshold.
     */
    public void recordIpFailure(String ip) {
        String failKey = "rl:ipfail:" + ip;
        long count = increment(failKey, BLOCK_WINDOW_HOURS * 3600);
        if (count >= BLOCK_THRESHOLD) {
            String blockKey = "rl:blocked:" + ip;
            try {
                redis.value(String.class).setex(blockKey, Duration.ofHours(24), "blocked");
                LOG.warnf("IP %s auto-blocked after %d failures in 1 hour", ip, count);
            } catch (Exception e) {
                LOG.warnf("Could not block IP %s in Redis: %s", ip, e.getMessage());
            }
        }
    }

    /**
     * Reset login attempts after successful authentication.
     */
    public void resetLogin(String identifier) {
        try {
            redis.key().del("rl:login:" + identifier);
        } catch (Exception e) {
            LOG.warnf("Could not reset rate limit for %s: %s", identifier, e.getMessage());
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private long increment(String key, long windowSeconds) {
        try {
            ValueCommands<String, String> cmds = redis.value(String.class);
            String raw = cmds.get(key);
            long count;
            if (raw == null) {
                cmds.setex(key, Duration.ofSeconds(windowSeconds), "1");
                count = 1;
            } else {
                // INCR equivalent via getset — works with quarkus redis client
                count = Long.parseLong(raw) + 1;
                cmds.setex(key, Duration.ofSeconds(windowSeconds), String.valueOf(count));
            }
            return count;
        } catch (Exception e) {
            LOG.warnf("Redis rate-limit error (failing open): %s", e.getMessage());
            return 0; // Fail open if Redis is unavailable
        }
    }

    private long getTtl(String key) {
        try {
            return redis.key().ttl(key);
        } catch (Exception e) {
            return LOGIN_WINDOW_SECONDS;
        }
    }

    public static class RateLimitExceededException extends RuntimeException {
        public final long retryAfterSeconds;

        public RateLimitExceededException(String message, long retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }
}
