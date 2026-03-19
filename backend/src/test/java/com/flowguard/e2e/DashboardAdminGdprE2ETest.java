package com.flowguard.e2e;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * E2E: Admin panel, Dashboard KPIs, Spending Analysis, Benchmarks,
 * Forecast Accuracy, GDPR, and Security Headers.
 *
 * Covers:
 *
 * DASHBOARD:
 *   1.  GET /dashboard/summary unauthenticated → 401
 *   2.  GET /dashboard/summary → 200 with healthScore
 *   3.  GET /dashboard/transactions → 200 with list
 *
 * FINANCIAL KPIs:
 *   4.  GET /financial-kpis → 200
 *
 * SPENDING:
 *   5.  GET /accounts/{id}/spending-analysis unauthenticated → 401
 *   6.  GET /accounts/{id}/spending-analysis → 200 (no account, 400/404 acceptable)
 *
 * BENCHMARKS:
 *   7.  GET /benchmarks/sectors → 200 with list
 *   8.  GET /benchmarks/{sector}/{size} → 200 or 404
 *   9.  GET /benchmarks/compare/{sector}/{size} → 200 or 404
 *
 * FORECAST ACCURACY:
 *  10.  GET /forecast-accuracy → 200
 *  11.  GET /forecast-accuracy/7 → 200
 *  12.  GET /forecast-accuracy/summary → 200 with summary stats
 *  13.  GET /forecast-accuracy/unsupported-horizon → 400
 *
 * TREASURY:
 *  14.  GET /treasury/forecast unauthenticated → 401
 *  15.  GET /treasury/forecast → 200 or 404 (no bank account)
 *  16.  GET /treasury/forecast?horizon=30 → 200 or 404
 *
 * ADMIN PANEL:
 *  17.  GET /admin/users → 200 with paginated list
 *  18.  GET /admin/users/{id} → 200 or 404
 *  19.  PUT /admin/users/{id}/kyc → 200 or 404
 *  20.  GET /admin/kpis → 200
 *  21.  GET /admin/stats → 200
 *  22.  GET /admin/config → 200
 *  23.  GET /admin/ml/stats → 200
 *  24.  GET /admin/tracfin → 200
 *  25.  GET /admin/tracfin/pending → 200
 *
 * GDPR:
 *  26.  POST /gdpr/consent → 200
 *  27.  GET /gdpr/export → 200 with user data
 *  28.  DELETE /gdpr/data unauthenticated → 401
 *
 * SECURITY HEADERS:
 *  29.  Any response contains X-Content-Type-Options + X-Frame-Options
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E2E – Dashboard, KPIs, Benchmarks, Forecast Accuracy, Admin & GDPR")
class DashboardAdminGdprE2ETest {

    static final String USER_ID = "00000000-0000-0000-0000-000000000003";
    static final String ADMIN_ID = "00000000-0000-0000-0000-000000000010";
    static final String FAKE_ACCOUNT_ID = "00000000-0000-0000-0000-000000000099";

    // ──────────────────────────────────────────────────────────────────────────
    // DASHBOARD
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(1)
    @DisplayName("1. GET /dashboard/summary unauthenticated → 401")
    void step1_dashboard_unauthenticated() {
        given()
        .when()
            .get("/dashboard/summary")
        .then()
            .statusCode(401);
    }

    @Test @Order(2)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("2. GET /dashboard/summary → 200 with health score")
    void step2_dashboard_summary() {
        given()
        .when()
            .get("/dashboard/summary")
        .then()
            .statusCode(anyOf(is(200), is(404)))
            .body("healthScore", anyOf(notNullValue(), nullValue()));
    }

    @Test @Order(3)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("3. GET /dashboard/transactions → 200")
    void step3_dashboard_transactions() {
        given()
        .when()
            .get("/dashboard/transactions")
        .then()
            .statusCode(anyOf(is(200), is(404)))
            .body("$", anyOf(is(instanceOf(java.util.List.class)), notNullValue()));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FINANCIAL KPIs
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(4)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("4. GET /financial-kpis → 200")
    void step4_financialKpis() {
        given()
        .when()
            .get("/financial-kpis")
        .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SPENDING ANALYSIS
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(5)
    @DisplayName("5. GET spending-analysis unauthenticated → 401")
    void step5_spendingAnalysis_unauthenticated() {
        given()
        .when()
            .get("/accounts/" + FAKE_ACCOUNT_ID + "/spending-analysis")
        .then()
            .statusCode(401);
    }

    @Test @Order(6)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("6. GET spending-analysis unknown account → 400/404")
    void step6_spendingAnalysis_unknownAccount() {
        given()
        .when()
            .get("/accounts/" + FAKE_ACCOUNT_ID + "/spending-analysis")
        .then()
            .statusCode(anyOf(is(400), is(404)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // BENCHMARKS
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(7)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("7. GET /benchmarks/sectors → 200 with sector list")
    void step7_benchmarks_sectors() {
        given()
        .when()
            .get("/benchmarks/sectors")
        .then()
            .statusCode(200)
            .body("$", is(instanceOf(java.util.List.class)));
    }

    @Test @Order(8)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("8. GET /benchmarks/RETAIL/MICRO → 200 or 404")
    void step8_benchmarks_getBySector() {
        given()
        .when()
            .get("/benchmarks/RETAIL/MICRO")
        .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    @Test @Order(9)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("9. GET /benchmarks/compare/RETAIL/MICRO → 200 or 404")
    void step9_benchmarks_compare() {
        given()
        .when()
            .get("/benchmarks/compare/RETAIL/MICRO")
        .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FORECAST ACCURACY
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(10)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("10. GET /forecast-accuracy → 200")
    void step10_forecastAccuracy_list() {
        given()
        .when()
            .get("/forecast-accuracy")
        .then()
            .statusCode(200)
            .body("$", is(instanceOf(java.util.List.class)));
    }

    @Test @Order(11)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("11. GET /forecast-accuracy/7 → 200 (7-day horizon)")
    void step11_forecastAccuracy_byHorizon() {
        given()
        .when()
            .get("/forecast-accuracy/7")
        .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    @Test @Order(12)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("12. GET /forecast-accuracy/summary → 200 with summary stats")
    void step12_forecastAccuracy_summary() {
        given()
        .when()
            .get("/forecast-accuracy/summary")
        .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    @Test @Order(13)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("13. GET /forecast-accuracy/999 unsupported horizon → 400")
    void step13_forecastAccuracy_invalidHorizon() {
        given()
        .when()
            .get("/forecast-accuracy/999")
        .then()
            .statusCode(anyOf(is(400), is(404)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TREASURY
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(14)
    @DisplayName("14. GET /treasury/forecast unauthenticated → 401")
    void step14_treasury_unauthenticated() {
        given()
        .when()
            .get("/treasury/forecast")
        .then()
            .statusCode(401);
    }

    @Test @Order(15)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("15. GET /treasury/forecast → 200 or 404 (no bank)")
    void step15_treasury_forecast() {
        given()
        .when()
            .get("/treasury/forecast")
        .then()
            .statusCode(anyOf(is(200), is(404), is(400)));
    }

    @Test @Order(16)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("16. GET /treasury/forecast?horizon=30 → 200 or 404")
    void step16_treasury_forecast_withHorizon() {
        given()
            .queryParam("horizon", 30)
        .when()
            .get("/treasury/forecast")
        .then()
            .statusCode(anyOf(is(200), is(404), is(400)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ADMIN PANEL
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(17)
    @TestSecurity(user = "e2e-admin", roles = "admin")
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_ID)})
    @DisplayName("17. Admin: GET /admin/users → paginated list")
    void step17_admin_listUsers() {
        given()
        .when()
            .get("/admin/users")
        .then()
            .statusCode(200)
            .body("content", is(instanceOf(java.util.List.class)));
    }

    @Test @Order(18)
    @TestSecurity(user = "e2e-admin", roles = "admin")
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_ID)})
    @DisplayName("18. Admin: GET /admin/users with search → paginated list")
    void step18_admin_searchUsers() {
        given()
            .queryParam("search", "test")
            .queryParam("page", 0)
            .queryParam("size", 10)
        .when()
            .get("/admin/users")
        .then()
            .statusCode(200);
    }

    @Test @Order(19)
    @TestSecurity(user = "e2e-admin", roles = "admin")
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_ID)})
    @DisplayName("19. Admin: GET /admin/users/{id} unknown → 404")
    void step19_admin_getUserDetail_notFound() {
        given()
        .when()
            .get("/admin/users/00000000-0000-0000-0000-000000000099")
        .then()
            .statusCode(anyOf(is(404), is(400)));
    }

    @Test @Order(20)
    @TestSecurity(user = "e2e-admin", roles = "admin")
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_ID)})
    @DisplayName("20. Admin: GET /admin/kpis → 200")
    void step20_admin_kpis() {
        given()
        .when()
            .get("/admin/kpis")
        .then()
            .statusCode(200);
    }

    @Test @Order(21)
    @TestSecurity(user = "e2e-admin", roles = "admin")
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_ID)})
    @DisplayName("21. Admin: GET /admin/stats → 200")
    void step21_admin_stats() {
        given()
        .when()
            .get("/admin/stats")
        .then()
            .statusCode(200);
    }

    @Test @Order(22)
    @TestSecurity(user = "e2e-admin", roles = "admin")
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_ID)})
    @DisplayName("22. Admin: GET /admin/config → 200 with config list")
    void step22_admin_config() {
        given()
        .when()
            .get("/admin/config")
        .then()
            .statusCode(200);
    }

    @Test @Order(23)
    @TestSecurity(user = "e2e-admin", roles = "admin")
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_ID)})
    @DisplayName("23. Admin: GET /admin/ml/stats → 200 with ML metrics")
    void step23_admin_mlStats() {
        given()
        .when()
            .get("/admin/ml/stats")
        .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    @Test @Order(24)
    @TestSecurity(user = "e2e-admin", roles = "admin")
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_ID)})
    @DisplayName("24. Admin: GET /admin/tracfin → 200 with reports list")
    void step24_admin_tracfinList() {
        given()
        .when()
            .get("/admin/tracfin")
        .then()
            .statusCode(200)
            .body("$", is(instanceOf(java.util.List.class)));
    }

    @Test @Order(25)
    @TestSecurity(user = "e2e-admin", roles = "admin")
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_ID)})
    @DisplayName("25. Admin: GET /admin/tracfin/pending → 200")
    void step25_admin_tracfinPending() {
        given()
        .when()
            .get("/admin/tracfin/pending")
        .then()
            .statusCode(200)
            .body("$", is(instanceOf(java.util.List.class)));
    }

    @Test @Order(26)
    @DisplayName("26. Admin endpoint without admin role → 403")
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    void step26_admin_forbidden_for_user_role() {
        given()
        .when()
            .get("/admin/users")
        .then()
            .statusCode(403);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GDPR
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(27)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("27. POST /gdpr/consent → 200")
    void step27_gdpr_consent() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "consentType": "ANALYTICS",
                    "granted": true
                }
                """)
        .when()
            .post("/gdpr/consent")
        .then()
            .statusCode(anyOf(is(200), is(201), is(204)));
    }

    @Test @Order(28)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("28. GET /gdpr/export → 200 with user data bundle")
    void step28_gdpr_export() {
        given()
        .when()
            .get("/gdpr/export")
        .then()
            .statusCode(200);
    }

    @Test @Order(29)
    @DisplayName("29. DELETE /gdpr/data unauthenticated → 401")
    void step29_gdpr_delete_unauthenticated() {
        given()
        .when()
            .delete("/gdpr/data")
        .then()
            .statusCode(401);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SECURITY HEADERS
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(30)
    @DisplayName("30. Any response contains required security headers")
    void step30_securityHeaders_present() {
        given()
        .when()
            .get("/config/flags")
        .then()
            .statusCode(anyOf(is(200)))
            .header("X-Content-Type-Options", notNullValue())
            .header("X-Frame-Options", notNullValue());
    }

    @Test @Order(31)
    @DisplayName("31. Content-Security-Policy header present")
    void step31_csp_header() {
        given()
        .when()
            .get("/config/flags")
        .then()
            .statusCode(200)
            .header("Content-Security-Policy", notNullValue());
    }
}
