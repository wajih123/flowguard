package com.flowguard.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

/**
 * JAX-RS filter that enforces IP blocklist and per-user global rate limiting.
 *
 * <p>Login-specific rate limiting is handled in {@link AuthService} directly,
 * as it needs access to the email before JWT authentication.
 *
 * <p>Priority: runs before authentication ({@code Priorities.AUTHENTICATION - 1}).
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 1)
public class RateLimitFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(RateLimitFilter.class);

    @Inject
    RedisRateLimiter rateLimiter;

    @Inject
    JsonWebToken jwt;

    @Override
    public void filter(ContainerRequestContext ctx) {
        // 1. Skip all Redis checks for public unauthenticated endpoints (fast path,
        //    no network I/O) — must be checked BEFORE any Redis call to prevent
        //    Redis latency from blocking login/register/health requests.
        String path = ctx.getUriInfo().getPath();
        if (isPublicPath(path)) {
            return;
        }

        String ip = extractClientIp(ctx);

        // 2. Check IP blocklist (Redis GET — only for non-public paths)
        if (rateLimiter.isBlocked(ip)) {
            ctx.abortWith(Response.status(Response.Status.FORBIDDEN)
                .entity("{\"error\":\"IP bloquée. Contactez le support : support@flowguard.fr\"}")
                .header("Content-Type", "application/json")
                .build());
            return;
        }

        // 3. Global per-user rate limit (requires JWT)
        try {
            if (jwt != null && jwt.getSubject() != null) {
                rateLimiter.checkGlobal(jwt.getSubject());
            }
        } catch (RedisRateLimiter.RateLimitExceededException e) {
            LOG.infof("Rate limit exceeded for user %s from IP %s", jwt.getSubject(), ip);
            ctx.abortWith(Response.status(429)
                .entity("{\"error\":\"" + e.getMessage() + "\"}")
                .header("Retry-After", String.valueOf(e.retryAfterSeconds))
                .header("Content-Type", "application/json")
                .build());
        }
    }

    private String extractClientIp(ContainerRequestContext ctx) {
        // Trust X-Forwarded-For from reverse proxy (nginx)
        String xff = ctx.getHeaderString("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String xReal = ctx.getHeaderString("X-Real-IP");
        if (xReal != null && !xReal.isBlank()) {
            return xReal.trim();
        }
        return "unknown";
    }

    private boolean isPublicPath(String path) {
        return path != null && (
            path.contains("/auth/register") ||
            path.contains("/auth/login")    ||
            path.contains("/auth/refresh")  ||
            path.contains("/public")        ||
            path.contains("/health")        ||
            path.contains("/metrics")
        );
    }
}
