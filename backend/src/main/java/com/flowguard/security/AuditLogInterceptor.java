package com.flowguard.security;

import io.agroal.api.AgroalDataSource;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;

/**
 * CDI interceptor that writes an audit entry to audit_log for every method
 * annotated with {@link AdminAction}.
 *
 * <p>Uses a direct JDBC connection so that audit writes are independent of any
 * JPA transaction that might be rolled back by the intercepted method.
 */
@AdminAction
@Interceptor
@Priority(Interceptor.Priority.APPLICATION + 10)
public class AuditLogInterceptor {

    private static final Logger LOG = Logger.getLogger(AuditLogInterceptor.class);

    private static final String INSERT_SQL =
        "INSERT INTO audit_log (actor_id, actor_email, actor_role, action, target_type, target_id, ip_address, created_at) " +
        "VALUES (CAST(? AS UUID), ?, ?, ?, ?, ?, ?, ?)";

    @Inject
    JsonWebToken jwt;

    @Inject
    AgroalDataSource ds;

    @AroundInvoke
    Object audit(InvocationContext ctx) throws Exception {
        Object result = ctx.proceed();

        try {
            AdminAction annotation = ctx.getMethod().getAnnotation(AdminAction.class);
            if (annotation == null) {
                annotation = ctx.getTarget().getClass().getAnnotation(AdminAction.class);
            }

            String action = (annotation != null && !annotation.value().isEmpty())
                ? annotation.value()
                : ctx.getMethod().getDeclaringClass().getSimpleName() + "." + ctx.getMethod().getName();

            String actorId     = jwt.getSubject();
            String actorEmail  = jwt.getClaim("email");
            String actorRole   = resolveRole();

            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                ps.setString(1, actorId);
                ps.setString(2, actorEmail);
                ps.setString(3, actorRole);
                ps.setString(4, action);
                ps.setString(5, null);
                ps.setString(6, null);
                ps.setString(7, null);
                ps.setObject(8, Instant.now());
                ps.executeUpdate();
            }
        } catch (Exception e) {
            // Never let audit failure break the actual request
            LOG.warnf("AuditLogInterceptor: failed to write audit log — %s", e.getMessage());
        }

        return result;
    }

    private String resolveRole() {
        if (jwt.getGroups() == null) return "UNKNOWN";
        if (jwt.getGroups().contains(Roles.SUPER_ADMIN)) return Roles.SUPER_ADMIN;
        if (jwt.getGroups().contains(Roles.ADMIN))       return Roles.ADMIN;
        return "UNKNOWN";
    }
}
