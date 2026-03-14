package com.flowguard.resource;

import com.flowguard.dto.UserDto;
import com.flowguard.service.UserService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

@Path("/user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class UserResource {

    @Inject
    UserService userService;

    @Inject
    JsonWebToken jwt;

    @GET
    @RunOnVirtualThread
    public Response getUser() {
        UUID userId = UUID.fromString(jwt.getSubject());
        UserDto user = userService.getUserById(userId);
        return Response.ok(user).build();
    }
}
