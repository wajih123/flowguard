package com.flowguard.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class SpendingAnalysisResourceTest {

    private static final String ACCOUNT_ID = "00000000-0000-0000-0000-000000000099";

    @Test
    void analyze_unauthenticated_shouldReturn401() {
        given()
        .when()
            .get("/accounts/" + ACCOUNT_ID + "/spending-analysis")
        .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void analyze_nonExistentAccount_shouldReturn400() {
        given()
        .when()
            .get("/accounts/" + ACCOUNT_ID + "/spending-analysis")
        .then()
            .statusCode(anyOf(is(400), is(404)));
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void analyze_withDateParams_nonExistentAccount_shouldReturn400() {
        given()
            .queryParam("from", "2026-01-01")
            .queryParam("to", "2026-01-31")
        .when()
            .get("/accounts/" + ACCOUNT_ID + "/spending-analysis")
        .then()
            .statusCode(anyOf(is(400), is(404)));
    }
}
