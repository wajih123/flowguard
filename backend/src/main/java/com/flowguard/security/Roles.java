package com.flowguard.security;

/**
 * Central role constants — mirror the values stored in the JWT "groups" claim
 * and the users.role column.
 */
public final class Roles {

    private Roles() {}

    /** Standard authenticated user (personal / business). */
    public static final String USER  = "user";

    /** Business-plan user. */
    public static final String BUSINESS = "business";

    /** FlowGuard team member — full back-office access. */
    public static final String ADMIN = "admin";

    /** Founder / CTO — unrestricted super-admin access. */
    public static final String SUPER_ADMIN = "super_admin";
}
