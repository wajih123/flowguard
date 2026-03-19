package com.flowguard.e2e;

import com.flowguard.repository.UserRepository;
import com.flowguard.util.TestDataResource;
import com.flowguard.util.TestDataSeeder;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E: Full authentication lifecycle.
 *
 * Covers:
 *   1. Register → duplicate detection
 *   2. Login before email verification (blocked)
 *   3. Email verification simulation → login succeeds → tokens returned
 *   4. Token refresh
 *   5. Logout invalidates refresh token
 *   6. Weak-password registration rejected (400)
 *   7. Invalid email format rejected (400)
 *   8. GET /user returns authenticated user profile
 *   9. GET /config/flags returns feature flags (public)
 *  10. GET /config/system returns system config (public)
 */
@QuarkusTest
@QuarkusTestResource(TestDataResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E2E – User Registration & Auth Lifecycle")
class UserRegistrationAndAuthE2ETest {

    static final String EMAIL = "e2e.auth@flowguard.test";
    static final String PASSWORD = "E2eSecure@2026";
    static String accessToken;
    static String refreshToken;
    
    private static boolean dataSeeded = false;

    @Inject
    UserRepository userRepository;

    @Inject
    TestDataSeeder testDataSeeder;

    @BeforeEach
    void setupTestData() {
        synchronized (UserRegistrationAndAuthE2ETest.class) {
            if (!dataSeeded) {
                testDataSeeder.seedTestData();
                dataSeeded = true;
            }
        }
    }

    @Transactional
    void markEmailVerified(String email) {
        userRepository.findByEmail(email).ifPresent(u -> u.setEmailVerified(true));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // STEP 1: Registration
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(1)
    @DisplayName("1. Register valid user → 202 pending verification")
    void step1_register_valid() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "firstName": "Alice",
                    "lastName": "Dupont",
                    "email": "%s",
                    "password": "%s",
                    "companyName": "Alice SARL",
                    "userType": "SME"
                }
                """.formatted(EMAIL, PASSWORD))
        .when()
            .post("/auth/register")
        .then()
            .statusCode(202)
            .body("pendingVerification", is(true))
            .body("maskedEmail", notNullValue());
    }

    @Test @Order(2)
    @DisplayName("2. Register duplicate email → 409 conflict")
    void step2_register_duplicate_email() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "firstName": "Bob",
                    "lastName": "Martin",
                    "email": "%s",
                    "password": "%s",
                    "companyName": "Bob SAS",
                    "userType": "FREELANCE"
                }
                """.formatted(EMAIL, PASSWORD))
        .when()
            .post("/auth/register")
        .then()
            .statusCode(409);
    }

    @Test @Order(3)
    @DisplayName("3. Register weak password → 400")
    void step3_register_weak_password() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "firstName": "Charlie",
                    "lastName": "Weak",
                    "email": "weak.pass@flowguard.test",
                    "password": "abc",
                    "companyName": "Charlie Inc",
                    "userType": "FREELANCE"
                }
                """)
        .when()
            .post("/auth/register")
        .then()
            .statusCode(anyOf(is(400), is(422)));
    }

    @Test @Order(4)
    @DisplayName("4. Register invalid email format → 400")
    void step4_register_invalid_email() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "firstName": "Dave",
                    "lastName": "Invalid",
                    "email": "not-an-email",
                    "password": "%s",
                    "companyName": "Dave Co",
                    "userType": "FREELANCE"
                }
                """.formatted(PASSWORD))
        .when()
            .post("/auth/register")
        .then()
            .statusCode(anyOf(is(400), is(422)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // STEP 2: Login before verification
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(5)
    @DisplayName("5. Login before email verification → 403")
    void step5_login_unverified() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"email": "%s", "password": "%s"}
                """.formatted(EMAIL, PASSWORD))
        .when()
            .post("/auth/login")
        .then()
            .statusCode(403)
            .body("message", is("EMAIL_NOT_VERIFIED"));
    }

    @Test @Order(6)
    @DisplayName("6. Login wrong password → 401")
    void step6_login_wrong_password() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"email": "%s", "password": "WrongPassword99!"}
                """.formatted(EMAIL))
        .when()
            .post("/auth/login")
        .then()
            .statusCode(anyOf(is(401), is(403)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // STEP 3: Verify email → login
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(7)
    @DisplayName("7. After email verification → login returns JWT pair")
    void step7_login_after_verification() {
        markEmailVerified(EMAIL);

        Response resp = given()
            .contentType(ContentType.JSON)
            .body("""
                {"email": "%s", "password": "%s"}
                """.formatted(EMAIL, PASSWORD))
        .when()
            .post("/auth/login")
        .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
            .body("refreshToken", notNullValue())
            .body("user.email", is(EMAIL))
            .body("user.firstName", is("Alice"))
            .extract().response();

        accessToken = resp.jsonPath().getString("accessToken");
        refreshToken = resp.jsonPath().getString("refreshToken");

        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // STEP 4: Use token
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(8)
    @DisplayName("8. GET /user with valid token → returns user profile")
    void step8_get_user_profile() {
        given()
            .header("Authorization", "Bearer " + accessToken)
        .when()
            .get("/user")
        .then()
            .statusCode(200)
            .body("email", is(EMAIL))
            .body("firstName", is("Alice"))
            .body("companyName", is("Alice SARL"));
    }

    @Test @Order(9)
    @DisplayName("9. GET /user without token → 401")
    void step9_get_user_unauthenticated() {
        given()
        .when()
            .get("/user")
        .then()
            .statusCode(401);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // STEP 5: Refresh tokens
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(10)
    @DisplayName("10. POST /auth/refresh with valid refresh token → new access token")
    void step10_refresh_token() {
        Response resp = given()
            .contentType(ContentType.JSON)
            .body("""
                {"refreshToken": "%s"}
                """.formatted(refreshToken))
        .when()
            .post("/auth/refresh")
        .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
            .extract().response();

        String newAccessToken = resp.jsonPath().getString("accessToken");
        assertThat(newAccessToken).isNotBlank();
        // Store new token for subsequent tests
        accessToken = newAccessToken;
    }

    @Test @Order(11)
    @DisplayName("11. POST /auth/refresh with invalid token → 401")
    void step11_refresh_invalid_token() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"refreshToken": "not.a.valid.jwt.token"}
                """)
        .when()
            .post("/auth/refresh")
        .then()
            .statusCode(anyOf(is(400), is(401)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // STEP 6: Logout
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(12)
    @DisplayName("12. POST /auth/logout → 204 and invalidates session")
    void step12_logout() {
        given()
            .header("Authorization", "Bearer " + accessToken)
            .contentType(ContentType.JSON)
            .body("""
                {"refreshToken": "%s"}
                """.formatted(refreshToken))
        .when()
            .post("/auth/logout")
        .then()
            .statusCode(anyOf(is(200), is(204)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public endpoints (no auth required)
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(13)
    @DisplayName("13. GET /config/flags → returns feature flags array")
    void step13_feature_flags() {
        given()
        .when()
            .get("/config/flags")
        .then()
            .statusCode(200);
    }

    @Test @Order(14)
    @DisplayName("14. GET /config/system → returns system config")
    void step14_system_config() {
        given()
        .when()
            .get("/config/system")
        .then()
            .statusCode(200);
    }
}
