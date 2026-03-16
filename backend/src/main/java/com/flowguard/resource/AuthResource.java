package com.flowguard.resource;

import com.flowguard.dto.*;
import com.flowguard.dto.LoginRequest;
import com.flowguard.dto.RegisterRequest;
import com.flowguard.service.AuthService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @Inject
    JsonWebToken jwt;

    @POST
    @Path("/register")
    @PermitAll
    @RunOnVirtualThread
    public Response register(@Valid RegisterRequest dto) {
        try {
            AuthResponse response = authService.register(dto);
            return Response.status(Response.Status.CREATED).entity(response).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.CONFLICT).entity(new ErrorBody(e.getMessage())).build();
        }
    }

    @POST
    @Path("/login")
    @PermitAll
    @RunOnVirtualThread
    public Response login(@Valid LoginRequest dto) {
        try {
            MfaChallengeResponse challenge = authService.login(dto);
            return Response.ok(challenge).build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(new ErrorBody("Identifiants incorrects")).build();
        }
    }

    @POST
    @Path("/verify-otp")
    @PermitAll
    @RunOnVirtualThread
    public Response verifyOtp(@Valid VerifyOtpRequest dto) {
        try {
            AuthResponse response = authService.completeLogin(dto);
            return Response.ok(response).build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(new ErrorBody(e.getMessage())).build();
        }
    }

    @POST
    @Path("/refresh")
    @PermitAll
    @RunOnVirtualThread
    public Response refresh(@Valid RefreshDto dto) {
        try {
            AuthResponse response = authService.refresh(dto.refreshToken());
            return Response.ok(response).build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(new ErrorBody(e.getMessage())).build();
        }
    }

    @POST
    @Path("/logout")
    @RolesAllowed("user")
    @RunOnVirtualThread
    public Response logout(@Valid LogoutDto dto) {
        authService.logout(dto.refreshToken());
        return Response.ok().build();
    }

    @GET
    @Path("/me")
    @RolesAllowed("user")
    @RunOnVirtualThread
    public Response me() {
        String userId = jwt.getSubject();
        UserDto user = authService.getUser(userId);
        return Response.ok(user).build();
    }

    public record ErrorBody(String message) {}
}
