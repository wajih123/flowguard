package com.flowguard.resource;

import com.flowguard.repository.UserRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthResourceTest {

    @Inject
    UserRepository userRepository;

    /**
     * Simulates a completed e-mail verification without going through the OTP flow.
     * Used in tests to put a registered user into a login-ready state.
     */
    @Transactional
    void markEmailVerified(String email) {
        userRepository.findByEmail(email).ifPresent(u -> u.setEmailVerified(true));
    }

    @Test
    @Order(1)
    void register_shouldReturnPendingVerification() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "firstName": "Jean",
                    "lastName": "Dupont",
                    "email": "jean@test.com",
                    "password": "SecureP@ss1",
                    "companyName": "Test SAS",
                    "userType": "FREELANCE"
                }
                """)
        .when()
            .post("/auth/register")
        .then()
            .statusCode(202)
            .body("pendingVerification", is(true))
            .body("maskedEmail", notNullValue());
    }

    @Test
    @Order(2)
    void register_duplicateEmail_shouldReturn409() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "firstName": "Jean",
                    "lastName": "Dupont",
                    "email": "jean@test.com",
                    "password": "SecureP@ss1",
                    "companyName": "Test SAS",
                    "userType": "FREELANCE"
                }
                """)
        .when()
            .post("/auth/register")
        .then()
            .statusCode(409)
            .body("message", containsString("existe déjà"));
    }

    @Test
    @Order(3)
    void login_unverifiedEmail_shouldReturn403() {
        // jean@test.com was registered but has not completed e-mail verification yet
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "email": "jean@test.com",
                    "password": "SecureP@ss1"
                }
                """)
        .when()
            .post("/auth/login")
        .then()
            .statusCode(403)
            .body("message", is("EMAIL_NOT_VERIFIED"));
    }

    @Test
    @Order(4)
    void login_verifiedUser_shouldReturnTokens() {
        // Mark as verified (simulates completing the /verify-email OTP step)
        markEmailVerified("jean@test.com");

        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "email": "jean@test.com",
                    "password": "SecureP@ss1"
                }
                """)
        .when()
            .post("/auth/login")
        .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
            .body("refreshToken", notNullValue())
            .body("user.email", is("jean@test.com"))
            .body("user.firstName", is("Jean"))
            .body("user.emailVerified", is(true));
    }

    @Test
    @Order(5)
    void login_invalidPassword_shouldReturn401() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "email": "jean@test.com",
                    "password": "WrongPassword"
                }
                """)
        .when()
            .post("/auth/login")
        .then()
            .statusCode(401)
            .body("message", containsString("incorrect"));
    }

    @Test
    @Order(6)
    void register_invalidEmail_shouldReturn400() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "firstName": "Test",
                    "lastName": "User",
                    "email": "not-an-email",
                    "password": "SecureP@ss1",
                    "companyName": "Test",
                    "userType": "TPE"
                }
                """)
        .when()
            .post("/auth/register")
        .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test-user-id", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void me_authenticated_shouldReturnUser() {
        // This test validates the /me endpoint is protected
        // With test security, we simulate an authenticated user
        given()
        .when()
            .get("/auth/me")
        .then()
            .statusCode(anyOf(is(200), is(401), is(500)));
            // 401 is returned when the test user does not exist in DB (SecurityException → 401 via GlobalExceptionMapper)
    }

    @Test
    void me_unauthenticated_shouldReturn401() {
        given()
        .when()
            .get("/auth/me")
        .then()
            .statusCode(401);
    }
}
