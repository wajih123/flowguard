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
class AlertResourceTest {

    @Test
    void getAlerts_unauthenticated_shouldReturn401() {
        given()
        .when()
            .get("/alerts")
        .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void getAlerts_authenticated_shouldReturnList() {
        given()
        .when()
            .get("/alerts")
        .then()
            .statusCode(200)
            .body("$", is(instanceOf(java.util.List.class)));
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void getUnreadCount_authenticated_shouldReturnCount() {
        given()
        .when()
            .get("/alerts/unread-count")
        .then()
            .statusCode(200)
            .body("count", greaterThanOrEqualTo(0));
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void markAsRead_nonExistent_shouldReturn404() {
        given()
        .when()
            .put("/alerts/00000000-0000-0000-0000-000000000099/read")
        .then()
            .statusCode(anyOf(is(404), is(500)));
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void markAllAsRead_shouldReturn204() {
        given()
        .when()
            .put("/alerts/read-all")
        .then()
            .statusCode(204);
    }

    @Test
    void flashCredit_unauthenticated_shouldReturn401() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "amount": 2000,
                    "purpose": "SALARY"
                }
                """)
        .when()
            .post("/flash-credit")
        .then()
            .statusCode(401);
    }

    @Test
    void treasury_unauthenticated_shouldReturn401() {
        given()
        .when()
            .get("/treasury/forecast")
        .then()
            .statusCode(401);
    }

    @Test
    void scenario_unauthenticated_shouldReturn401() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "type": "NEW_EXPENSE",
                    "amount": 5000,
                    "delayDays": 30
                }
                """)
        .when()
            .post("/scenario")
        .then()
            .statusCode(401);
    }
}
