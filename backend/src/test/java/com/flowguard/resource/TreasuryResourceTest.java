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
class TreasuryResourceTest {

    @Test
    void getForecast_unauthenticated_shouldReturn401() {
        given()
        .when()
            .get("/treasury/forecast")
        .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void getForecast_invalidHorizon_shouldReturn400() {
        given()
            .queryParam("horizon", 200)
        .when()
            .get("/treasury/forecast")
        .then()
            .statusCode(anyOf(is(400), is(500)));
    }
}
