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
            String pong = redis.execute(String.class, "PING");
            if ("PONG".equalsIgnoreCase(pong)) {
                return HealthCheckResponse.builder()
                        .name("redis")
                        .up()
                        .build();
            }
            return HealthCheckResponse.builder()
                    .name("redis")
                    .down()
                    .withData("response", pong)
                    .build();
        } catch (Exception e) {
            LOG.warnf("Redis health check failed: %s", e.getMessage());
            return HealthCheckResponse.builder()
                    .name("redis")
                    .down()
                    .withData("reason", e.getMessage())
                    .build();
        }
    }
}
