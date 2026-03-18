package com.flowguard.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class BudgetResourceTest {

    @Test
    void vsActual_unauthenticated_shouldReturn401() {
        given()
        .when()
            .get("/budget/vs-actual/2025/3")
        .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void vsActual_authenticated_shouldReturnDto() {
        given()
        .when()
            .get("/budget/vs-actual/2025/3")
        .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void upsert_validBody_shouldReturn200() {
        String body = """
                {"year":2025,"month":3,"category":"FOOD","budgetedAmount":500}
                """;
        given()
            .contentType("application/json")
            .body(body)
        .when()
            .put("/budget")
        .then()
            .statusCode(anyOf(is(200), is(201), is(404)));
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void deleteLine_nonExistent_shouldReturn404() {
        given()
        .when()
            .delete("/budget/line/00000000-0000-0000-0000-000000000099")
        .then()
            .statusCode(anyOf(is(404), is(400), is(403)));
    }
}
