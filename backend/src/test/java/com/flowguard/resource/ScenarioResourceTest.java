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
class ScenarioResourceTest {

    @Test
    void runScenario_unauthenticated_shouldReturn401() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "type": "LATE_PAYMENT",
                    "amount": 5000,
                    "delayDays": 30
                }
                """)
        .when()
            .post("/scenario")
        .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void runScenario_missingType_shouldReturn400() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "amount": 5000,
                    "delayDays": 30
                }
                """)
        .when()
            .post("/scenario")
        .then()
            .statusCode(400);
    }
}
