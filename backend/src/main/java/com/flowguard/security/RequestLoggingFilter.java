package com.flowguard.security;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.time.Instant;

@Provider
@PreMatching
public class RequestLoggingFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(RequestLoggingFilter.class);

    @Override
    public void filter(ContainerRequestContext requestContext) {
        requestContext.setProperty("requestStart", Instant.now());

        LOG.debugf("[%s] %s %s",
                requestContext.getMethod(),
                requestContext.getUriInfo().getRequestUri().getPath(),
                requestContext.getHeaderString("User-Agent")
        );
    }
}
