package com.flowguard.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * System configuration entry.
 */
public record SystemConfigDto(
    UUID    id,
    String  configKey,
    String  configValue,
    String  valueType,
    String  description,
    Instant updatedAt
) {}
