package com.flowguard.dto;

/**
 * Returned by POST /auth/login when the user's credential check passes and an
 * OTP has been sent.  The client must call POST /auth/verify-otp to complete
 * authentication.
 */
public record MfaChallengeResponse(
        boolean mfaRequired,
        String  sessionToken,
        String  maskedEmail) {

    /** Convenience constructor – mfaRequired is always true in this context. */
    public MfaChallengeResponse(String sessionToken, String maskedEmail) {
        this(true, sessionToken, maskedEmail);
    }
}
