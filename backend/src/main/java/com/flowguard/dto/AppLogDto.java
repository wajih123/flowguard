package com.flowguard.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Application log entry for admin logs page.
 */
public record AppLogDto(
    long    id,
    String  level,
    String  logger,
    String  message,
    String  stackTrace,
    Instant createdAt
) {}
