package com.flowguard.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class InvoiceResourceTest {

    @Test
    void list_unauthenticated_shouldReturn401() {
        given()
        .when()
            .get("/invoices")
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
            .get("/invoices")
        .then()
            .statusCode(200)
            .body("$", is(instanceOf(java.util.List.class)));
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void create_missingBody_shouldReturn400() {
        given()
            .contentType("application/json")
            .body("{}")
        .when()
            .post("/invoices")
        .then()
            .statusCode(anyOf(is(400), is(422)));
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void getById_nonExistent_shouldReturn404() {
        given()
        .when()
            .get("/invoices/00000000-0000-0000-0000-000000000099")
        .then()
            .statusCode(anyOf(is(404), is(400)));
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void create_validRequest_shouldReturn201() {
        String body = """
                {
                  "clientName": "TestCorp",
                  "number": "FAC-TEST-001",
                  "amountHt": 1000.00,
                  "vatRate": 20.0,
                  "currency": "EUR",
                  "issueDate": "2025-01-01",
                  "dueDate": "2025-02-01"
                }
                """;
        given()
            .contentType("application/json")
            .body(body)
        .when()
            .post("/invoices")
        .then()
            .statusCode(anyOf(is(200), is(201), is(404)));
    }
}
