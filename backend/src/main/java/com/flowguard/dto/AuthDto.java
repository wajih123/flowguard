package com.flowguard.dto;

// Legacy DTOs — kept for backward compatibility with AuthResource.
// Only used within this package or by the resource layer.
record RegisterDto(
    String email,
    String password,
    String firstName,
    String lastName,
    String userType
) {}

record LoginDto(
    String email,
    String password
) {}

record UserDtoLegacy(
    String id,
    String email,
    String firstName,
    String lastName,
    String userType,
    String kycStatus
) {}
