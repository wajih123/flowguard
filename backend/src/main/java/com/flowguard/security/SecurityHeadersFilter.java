package com.flowguard.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Adds mandatory security headers to every HTTP response.
 *
 * <p>Required for ACPR/DSP2 compliance and fintech best-practice (OWASP ASVS 14.4).
 */
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class SecurityHeadersFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext resp) {

        var headers = resp.getHeaders();

        // Prevent MITM — browsers must use HTTPS for 1 year, including subdomains
        headers.putSingle("Strict-Transport-Security",
                "max-age=31536000; includeSubDomains; preload");

        // Prevent clickjacking (iframe embedding by malicious sites)
        headers.putSingle("X-Frame-Options", "DENY");

        // Prevent content-type sniffing (polyglot file attacks)
        headers.putSingle("X-Content-Type-Options", "nosniff");

        // Limit referrer leakage
        headers.putSingle("Referrer-Policy", "strict-origin-when-cross-origin");

        // Disable dangerous browser features
        headers.putSingle("Permissions-Policy",
                "geolocation=(), microphone=(), camera=(), payment=(), usb=()");

        // Content Security Policy — strict, API-only backend
        headers.putSingle("Content-Security-Policy",
                "default-src 'none'; frame-ancestors 'none'");

        // Disable all caching for financial API responses (prevent proxy/browser caching of sensitive data)
        String path = req.getUriInfo().getPath();
        if (isFinancialPath(path)) {
            headers.putSingle("Cache-Control", "no-store, no-cache, must-revalidate, private");
            headers.putSingle("Pragma", "no-cache");
            headers.putSingle("Expires", "0");
        }

        // Remove server information disclosure
        headers.remove("Server");
        headers.remove("X-Powered-By");
    }

    private boolean isFinancialPath(String path) {
        return path != null && (
                path.contains("/transactions") ||
                path.contains("/accounts") ||
                path.contains("/predictions") ||
                path.contains("/flash-credit") ||
                path.contains("/gdpr") ||
                path.contains("/auth")
        );
    }
}
