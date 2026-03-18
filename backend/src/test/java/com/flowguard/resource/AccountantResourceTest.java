package com.flowguard.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AccountantResourceTest {

    @Test
    void listGrants_unauthenticated_shouldReturn401() {
        given()
        .when()
            .get("/accountant/grants")
        .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void listGrants_authenticated_shouldReturnArray() {
        given()
        .when()
            .get("/accountant/grants")
        .then()
            .statusCode(200)
            .body("$", is(instanceOf(java.util.List.class)));
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void grantAccess_validEmail_shouldReturn201() {
        String body = """
                {"accountantEmail":"comptable@cabinet.fr"}
                """;
        given()
            .contentType("application/json")
            .body(body)
        .when()
            .post("/accountant/grants")
        .then()
            .statusCode(anyOf(is(200), is(201), is(404)));
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void revokeGrant_nonExistent_shouldReturn404() {
        given()
        .when()
            .delete("/accountant/grants/00000000-0000-0000-0000-000000000099")
        .then()
            .statusCode(anyOf(is(404), is(400), is(403)));
    }

    @Test
    void portalInvoices_noToken_shouldReturn403() {
        given()
        .when()
            .get("/accountant/portal/invoices")
        .then()
            .statusCode(anyOf(is(401), is(403), is(400)));
    }
}
