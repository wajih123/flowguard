package com.flowguard.util;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

/**
 * Quarkus test resource that ensures test data seeding happens after container initialization.
 * Note: Data seeding now happens in individual test @BeforeEach methods instead.
 */
public class TestDataResource implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        // Container not yet initialized at this point
        // Data seeding will be handled by individual test initialization
        return Map.of();
    }

    @Override
    public void stop() {
        // Nothing to clean up
    }
}
