package com.flowguard.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class ForecastAccuracyResourceTest {

    @Test
    void list_unauthenticated_shouldReturn401() {
        given()
        .when()
            .get("/forecast-accuracy")
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
            .get("/forecast-accuracy")
        .then()
            .statusCode(200)
            .body("$", is(instanceOf(java.util.List.class)));
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void summary_authenticated_shouldReturnSummary() {
        given()
        .when()
            .get("/forecast-accuracy/summary")
        .then()
            .statusCode(200)
            .body("averageAccuracyPct", notNullValue())
            .body("totalEntries", notNullValue());
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void byHorizon_authenticated_shouldReturnArray() {
        given()
        .when()
            .get("/forecast-accuracy/30")
        .then()
            .statusCode(200)
            .body("$", is(instanceOf(java.util.List.class)));
    }
}
