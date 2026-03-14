package com.flowguard.security;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;
import java.util.stream.Collectors;

@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        Map<String, String> violations = exception.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> extractFieldName(violation),
                        ConstraintViolation::getMessage,
                        (msg1, msg2) -> msg1
                ));

        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ValidationErrorResponse("VALIDATION_ERROR", "Erreurs de validation", violations))
                .build();
    }

    private String extractFieldName(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1) : path;
    }

    public record ValidationErrorResponse(String code, String message, Map<String, String> violations) {}
}
