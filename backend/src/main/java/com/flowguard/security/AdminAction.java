package com.flowguard.security;

import jakarta.interceptor.InterceptorBinding;
import java.lang.annotation.*;

/**
 * CDI interceptor binding — place on any resource method (or class) that mutates
 * state so that {@link AuditLogInterceptor} automatically records it in audit_log.
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface AdminAction {
    /** Human-readable description recorded in audit_log.action. */
    String value() default "";
}
