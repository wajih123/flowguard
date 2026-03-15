package com.flowguard.security;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        // JAX-RS exceptions (404, 405, 401…) already carry the right HTTP status.
        // Forward them as-is and log at WARN — they are not server errors.
        if (exception instanceof WebApplicationException wae) {
            int status = wae.getResponse().getStatus();
            if (status < 500) {
                LOG.warnf("HTTP %d: %s", status, exception.getMessage());
                return Response.status(status)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(new ErrorResponse(String.valueOf(status), exception.getMessage()))
                        .build();
            }
        }

        LOG.error("Unhandled exception", exception);

        if (exception instanceof IllegalArgumentException) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorResponse("BAD_REQUEST", exception.getMessage()))
                    .build();
        }

        if (exception instanceof IllegalStateException) {
            return Response.status(Response.Status.CONFLICT)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorResponse("CONFLICT", exception.getMessage()))
                    .build();
        }

        if (exception instanceof SecurityException) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorResponse("UNAUTHORIZED", "Accès non autorisé"))
                    .build();
        }

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("INTERNAL_ERROR", "Erreur interne du serveur"))
                .build();
    }

    public record ErrorResponse(String code, String message) {}
}
