package fr.flowguard.auth.resource;

import fr.flowguard.auth.service.AuthService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.util.Map;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Auth", description = "Authentification FlowGuard")
public class AuthResource {

    @Inject
    AuthService authService;

    @GET
    @Path("/ping")
    @PermitAll
    @Operation(summary = "Health check Auth")
    public Response ping() {
        return Response.ok(Map.of("status", "ok", "service", "flowguard-auth", "version", "1.0.0")).build();
    }

    /**
     * POST /api/auth/register
     * Creates the account and sends a 6-digit OTP to the user email.
     * Returns { pendingVerification: true, maskedEmail } — NO tokens yet.
     * The client must call /api/auth/verify-email to complete registration.
     */
    @POST
    @Path("/register")
    @PermitAll
    @Operation(summary = "Creer un compte — renvoie un etat d attente de verification email")
    public Response register(@Valid RegisterRequest request) {
        try {
            AuthService.RegisterPendingResult result = authService.register(
                    request.email(), request.password(), request.firstName(), request.lastName()
            );
            return Response.status(Response.Status.CREATED).entity(result).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorResponse(e.getMessage())).build();
        }
    }

    /**
     * POST /api/auth/verify-email
     * One-time endpoint: validates the OTP, marks email_verified = true,
     * and returns access + refresh tokens.
     */
    @POST
    @Path("/verify-email")
    @PermitAll
    @Operation(summary = "Verifier l email avec le code OTP recu (une seule fois)")
    public Response verifyEmail(@Valid VerifyEmailRequest request) {
        try {
            AuthService.AuthResult result = authService.verifyEmail(request.email(), request.code());
            return Response.ok(result).build();
        } catch (SecurityException e) {
            return Response.status(422)
                    .entity(new ErrorResponse(e.getMessage())).build();
        }
    }

    @POST
    @Path("/login")
    @PermitAll
    @Operation(summary = "Se connecter")
    public Response login(@Valid LoginRequest request) {
        try {
            AuthService.AuthResult result = authService.login(request.email(), request.password());
            return Response.ok(result).build();
        } catch (AuthService.EmailNotVerifiedException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "EMAIL_NOT_VERIFIED", "maskedEmail", e.maskedEmail)).build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("Identifiants incorrects")).build();
        }
    }

    @POST
    @Path("/refresh")
    @PermitAll
    @Operation(summary = "Rafraichir le token")
    public Response refresh(@Valid RefreshRequest request) {
        try {
            AuthService.AuthResult result = authService.refresh(request.refreshToken());
            return Response.ok(result).build();
        } catch (SecurityException e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("Session expiree, veuillez vous reconnecter")).build();
        }
    }

    // ── Request / Response records ────────────────────────────────────────────

    public record RegisterRequest(
            @NotBlank(message = "Email requis") @Email(message = "Email invalide") String email,
            @NotBlank(message = "Mot de passe requis") @Size(min = 8, message = "Minimum 8 caracteres") String password,
            @NotBlank(message = "Prenom requis") String firstName,
            @NotBlank(message = "Nom requis") String lastName
    ) {}

    public record VerifyEmailRequest(
            @NotBlank(message = "Email requis") @Email(message = "Email invalide") String email,
            @NotBlank(message = "Code requis") @Size(min = 6, max = 6, message = "Code a 6 chiffres") String code
    ) {}

    public record LoginRequest(
            @NotBlank(message = "Email requis") String email,
            @NotBlank(message = "Mot de passe requis") String password
    ) {}

    public record RefreshRequest(
            @NotBlank(message = "Token requis") String refreshToken
    ) {}

    public record ErrorResponse(String error) {}
}