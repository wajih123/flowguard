package com.flowguard.health;

import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.RedisDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.jboss.logging.Logger;

/**
 * Liveness health check for Redis.
 *
 * <p>Executes a PING command with a short timeout.
 * Redis failure degrades rate limiting and caching (fail-open), but is still surfaced here.
 */
@Liveness
@ApplicationScoped
public class RedisHealthCheck implements HealthCheck {

    private static final Logger LOG = Logger.getLogger(RedisHealthCheck.class);

    @Inject
    RedisDataSource redis;

    @Override
    public HealthCheckResponse call() {
        try {
            // A simple GET on a non-existent key is a safe no-op that verifies connectivity
            redis.value(String.class).get("__health_check__");
            return HealthCheckResponse.builder()
                    .name("redis")
                    .up()
                    .build();
        } catch (Exception e) {
            // ConcurrentModificationException during CDI bean init is a transient
            // startup race — demote to DEBUG, real connection errors stay at WARN.
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.contains("ConcurrentModification") || msg.contains("synthetic bean")) {
                LOG.debugf("Redis health check transient init: %s", msg);
            } else {
                LOG.warnf("Redis health check failed: %s", msg);
            }
            return HealthCheckResponse.builder()
                    .name("redis")
                    .down()
                    .withData("reason", msg)
                    .build();
        }
    }
}
