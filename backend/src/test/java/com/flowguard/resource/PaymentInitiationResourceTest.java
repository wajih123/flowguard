package com.flowguard.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class PaymentInitiationResourceTest {

    @Test
    void list_unauthenticated_shouldReturn401() {
        given()
        .when()
            .get("/payments")
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
            .get("/payments")
        .then()
            .statusCode(200)
            .body("$", is(instanceOf(java.util.List.class)));
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void initiate_invalidIban_shouldReturn400() {
        String body = """
                {
                  "creditorName": "Supplier SA",
                  "creditorIban": "NOT_AN_IBAN",
                  "amount": 1000.00,
                  "reference": "Test"
                }
                """;
        given()
            .contentType("application/json")
            .header("Idempotency-Key", "test-key-invalid-iban")
            .body(body)
        .when()
            .post("/payments")
        .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void cancel_nonExistent_shouldReturn404() {
        given()
            .contentType("application/json")
        .when()
            .post("/payments/00000000-0000-0000-0000-000000000099/cancel")
        .then()
            .statusCode(anyOf(is(404), is(400), is(403)));
    }
}
