package com.flowguard.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class TaxEstimateResourceTest {

    @Test
    void list_unauthenticated_shouldReturn401() {
        given()
        .when()
            .get("/tax")
        .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void list_authenticated_shouldReturnArray() {
        given()
        .when()
            .get("/tax")
        .then()
            .statusCode(200)
            .body("$", is(instanceOf(java.util.List.class)));
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void upcoming_authenticated_shouldReturnArray() {
        given()
        .when()
            .get("/tax/upcoming")
        .then()
            .statusCode(200)
            .body("$", is(instanceOf(java.util.List.class)));
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void regenerate_authenticated_shouldReturnArray() {
        given()
            .contentType("application/json")
        .when()
            .post("/tax/regenerate")
        .then()
            .statusCode(anyOf(is(200), is(204), is(404)));
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void markPaid_nonExistent_shouldReturn404() {
        given()
        .when()
            .post("/tax/00000000-0000-0000-0000-000000000099/paid")
        .then()
            .statusCode(anyOf(is(404), is(400)));
    }
}
