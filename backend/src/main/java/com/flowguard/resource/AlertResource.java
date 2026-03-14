package com.flowguard.resource;

import com.flowguard.dto.AlertDto;
import com.flowguard.service.AlertService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.UUID;

@Path("/alerts")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class AlertResource {

    @Inject
    AlertService alertService;

    @Inject
    JsonWebToken jwt;

    @GET
    @RunOnVirtualThread
    public Response getAlerts(@QueryParam("unreadOnly") @DefaultValue("false") boolean unreadOnly) {
        UUID userId = UUID.fromString(jwt.getSubject());
        List<AlertDto> alerts = unreadOnly
                ? alertService.getUnreadAlerts(userId)
                : alertService.getAlertsByUserId(userId);

        return Response.ok(alerts)
                .header("X-Unread-Count", alertService.getUnreadCount(userId))
                .build();
    }

    @PUT
    @Path("/{alertId}/read")
    @RunOnVirtualThread
    public Response markAsRead(@PathParam("alertId") UUID alertId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        try {
            alertService.markAsRead(alertId, userId);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new AuthResource.ErrorBody(e.getMessage()))
                    .build();
        }
    }

    @PUT
    @Path("/read-all")
    @RunOnVirtualThread
    public Response markAllAsRead() {
        UUID userId = UUID.fromString(jwt.getSubject());
        alertService.markAllAsRead(userId);
        return Response.noContent().build();
    }

    @GET
    @Path("/unread-count")
    @RunOnVirtualThread
    public Response getUnreadCount() {
        UUID userId = UUID.fromString(jwt.getSubject());
        long count = alertService.getUnreadCount(userId);
        return Response.ok(new CountResponse(count)).build();
    }

    public record CountResponse(long count) {}
}
