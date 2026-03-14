package com.flowguard.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Feature flag entry.
 */
public record FeatureFlagDto(
    UUID    id,
    String  flagKey,
    boolean enabled,
    String  description,
    Instant updatedAt
) {}
