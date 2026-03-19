package fr.flowguard.accountant.resource;

import fr.flowguard.accountant.entity.AccountantGrantEntity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Path("/api/accountant")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Accountant", description = "Portail comptable")
public class AccountantResource {

    @Inject
    JsonWebToken jwt;

    @GET
    @Path("/grants")
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Lister les acces comptables")
    public Response listGrants() {
        String userId = jwt.getSubject();
        List<AccountantGrantEntity> grants = AccountantGrantEntity.findByUser(userId);
        return Response.ok(grants.stream().map(this::toDto).toList()).build();
    }

    @POST
    @Path("/grants")
    @Transactional
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Creer un acces comptable")
    public Response grantAccess(@QueryParam("email") String email) {
        String userId = jwt.getSubject();
        if (email == null || email.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorDto("email is required")).build();
        }
        AccountantGrantEntity grant = new AccountantGrantEntity();
        grant.id = UUID.randomUUID().toString();
        grant.userId = userId;
        grant.accountantEmail = email;
        grant.accessToken = UUID.randomUUID().toString().replace("-", "");
        grant.expiresAt = Instant.now().plus(30, ChronoUnit.DAYS);
        grant.persist();
        return Response.status(Response.Status.CREATED).entity(toDto(grant)).build();
    }

    @DELETE
    @Path("/grants/{grantId}")
    @Transactional
    @RolesAllowed({"ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"})
    @Operation(summary = "Revoquer un acces comptable")
    public Response revokeAccess(@PathParam("grantId") String grantId) {
        String userId = jwt.getSubject();
        AccountantGrantEntity grant = AccountantGrantEntity.findByIdAndUser(grantId, userId);
        if (grant == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorDto("Grant not found")).build();
        }
        grant.delete();
        return Response.noContent().build();
    }

    private GrantDto toDto(AccountantGrantEntity g) {
        return new GrantDto(g.id, g.accountantEmail, g.accessToken,
                g.expiresAt.toString(), g.createdAt.toString(), g.isExpired());
    }

    public record GrantDto(
        String id, String accountantEmail, String accessToken,
        String expiresAt, String createdAt, boolean expired
    ) {}

    public record ErrorDto(String message) {}
}