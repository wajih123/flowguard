package com.flowguard.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class BenchmarkResourceTest {

    @Test
    void benchmarks_unauthenticated_shouldReturn401() {
        given()
        .when()
            .get("/benchmarks/RETAIL/MICRO")
        .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void benchmarks_authenticated_shouldReturnBenchmarkDto() {
        given()
        .when()
            .get("/benchmarks/RETAIL/MICRO")
        .then()
            .statusCode(200)
            .body("$", is(instanceOf(java.util.List.class)));
    }

    @Test
    @TestSecurity(user = "test-user", roles = "user")
    @JwtSecurity(claims = {
        @Claim(key = "sub", value = "00000000-0000-0000-0000-000000000001")
    })
    void benchmarks_missingSector_shouldReturn400() {
        given()
        .when()
            .get("/benchmarks")
        .then()
            .statusCode(anyOf(is(400), is(404)));
    }
}
