package com.flowguard.cache;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.redis.client.RedisClient;
import io.quarkus.redis.client.RedisClientName;
import io.vertx.redis.client.Response;

@ApplicationScoped
public class RedisCacheService {

    @Inject
    @RedisClientName("default")
    RedisClient redisClient;

    @ConfigProperty(name = "redis.ttl.default", defaultValue = "3600")
    int defaultTtl;

    public void set(String key, String value, int ttlSeconds) {
        redisClient.setex(key, String.valueOf(ttlSeconds), value);
    }

    public void set(String key, String value) {
        set(key, value, defaultTtl);
    }

    public String get(String key) {
        Response resp = redisClient.get(key);
        return resp == null ? null : resp.toString();
    }

    public void delete(String key) {
        redisClient.del(key);
    }
}
