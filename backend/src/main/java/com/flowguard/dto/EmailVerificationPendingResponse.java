package com.flowguard.dto;

/**
 * Returned by POST /auth/register when e-mail verification is required.
 * The client should display the OTP input screen and call POST /auth/verify-email.
 */
public record EmailVerificationPendingResponse(
        boolean pendingVerification,
        String maskedEmail
) {
    public EmailVerificationPendingResponse(String maskedEmail) {
        this(true, maskedEmail);
    }
}
