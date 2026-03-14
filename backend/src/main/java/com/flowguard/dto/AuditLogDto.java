package com.flowguard.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit log entry for super-admin audit trail page.
 */
public record AuditLogDto(
    UUID    id,
    UUID    actorId,
    String  actorEmail,
    String  actorRole,
    String  action,
    String  targetType,
    String  targetId,
    String  ipAddress,
    Instant createdAt
) {}
