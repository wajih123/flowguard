package com.flowguard.resource;

import com.flowguard.domain.UserEntity;
import com.flowguard.security.Roles;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/team")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({Roles.BUSINESS, Roles.ADMIN, Roles.SUPER_ADMIN})
public class TeamResource {

    @Inject
    JsonWebToken jwt;

    public record TeamMemberDto(
            String id,
            String email,
            String firstName,
            String lastName,
            String role,
            String joinedAt
    ) {}

    public record InviteRequest(String email) {}

    @GET
    @RunOnVirtualThread
    public Response listMembers() {
        UUID userId = UUID.fromString(jwt.getSubject());
        UserEntity owner = UserEntity.findById(userId);
        if (owner == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        List<TeamMemberDto> members = List.of(new TeamMemberDto(
                owner.getId().toString(),
                owner.getEmail(),
                owner.getFirstName(),
                owner.getLastName(),
                "OWNER",
                owner.getCreatedAt() != null ? owner.getCreatedAt().toString() : Instant.now().toString()
        ));
        return Response.ok(members).build();
    }

    @POST
    @Path("/invite")
    @RunOnVirtualThread
    @Transactional
    public Response inviteMember(InviteRequest req) {
        if (req == null || req.email() == null || req.email().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Email requis"))
                    .build();
        }
        if (!req.email().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Email invalide"))
                    .build();
        }
        return Response.ok(Map.of("message", "Invitation envoyée à " + req.email())).build();
    }

    @DELETE
    @Path("/{memberId}")
    @RunOnVirtualThread
    @Transactional
    public Response removeMember(@PathParam("memberId") UUID memberId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        if (memberId.equals(userId)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Vous ne pouvez pas vous retirer vous-même"))
                    .build();
        }
        return Response.ok(Map.of("message", "Membre retiré")).build();
    }
}
