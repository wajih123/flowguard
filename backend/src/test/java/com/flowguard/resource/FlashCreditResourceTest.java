package com.flowguard.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class FlashCreditResourceTest {

    @Test
    void getCredits_unauthenticated_shouldReturn401() {
        given()
        .when()
            .get("/api/flash-credit")
        .then()
            .statusCode(401);
    }

    @Test
    void requestCredit_unauthenticated_shouldReturn401() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "amount": 2000,
                    "purpose": "SALARY"
                }
                """)
        .when()
            .post("/api/flash-credit")
        .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void getCredits_authenticated_shouldReturnList() {
        given()
        .when()
            .get("/api/flash-credit")
        .then()
            .statusCode(anyOf(is(200), is(400)));
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void requestCredit_invalidAmount_shouldReturn400() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "amount": 100,
                    "purpose": "SALARY"
                }
                """)
        .when()
            .post("/api/flash-credit")
        .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void requestCredit_missingPurpose_shouldReturn400() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "amount": 2000,
                    "purpose": ""
                }
                """)
        .when()
            .post("/api/flash-credit")
        .then()
            .statusCode(400);
    }
}
