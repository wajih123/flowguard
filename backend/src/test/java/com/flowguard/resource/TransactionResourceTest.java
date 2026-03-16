package com.flowguard.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class TransactionResourceTest {

    private static final String ACCOUNT_ID = "00000000-0000-0000-0000-000000000099";

    @Test
    void getTransactions_unauthenticated_shouldReturn401() {
        given()
        .when()
            .get("/accounts/" + ACCOUNT_ID + "/transactions")
        .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void getTransactions_nonExistentAccount_shouldReturn400() {
        given()
        .when()
            .get("/accounts/" + ACCOUNT_ID + "/transactions")
        .then()
            .statusCode(anyOf(is(400), is(404)));
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void getRecurring_nonExistentAccount_shouldReturn400() {
        given()
        .when()
            .get("/accounts/" + ACCOUNT_ID + "/transactions/recurring")
        .then()
            .statusCode(anyOf(is(400), is(404)));
    }
}
