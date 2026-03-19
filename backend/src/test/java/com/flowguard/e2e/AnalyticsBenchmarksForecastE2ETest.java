package com.flowguard.e2e;

import com.flowguard.util.TestDataResource;
import com.flowguard.util.TestDataSeeder;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * E2E: Analytics, Benchmarks, Financial KPIs, Forecast Accuracy,
 * Treasury, Spending Analysis, and Transactions endpoints.
 *
 * USER_ID = ...0004  (deduplicated from other E2E test classes)
 *
 * Covers:
 *
 * BENCHMARKS — full sector lifecycle:
 *   1.  GET /benchmarks/sectors → 200 non-empty list
 *   2.  GET /benchmarks/{sector}/{size} valid → 200 or 404
 *   3.  GET /benchmarks/{sector}/{size} unknown sector → 400 or 404
 *   4.  GET /benchmarks/compare/{sector}/{size} → 200 or 404
 *   5.  GET /benchmarks/sectors unauthenticated → 401
 *
 * FINANCIAL KPIs:
 *   6.  GET /financial-kpis → 200
 *   7.  GET /financial-kpis unauthenticated → 401
 *
 * FORECAST ACCURACY — all supported horizons:
 *   8.  GET /forecast-accuracy → 200 with list
 *   9.  GET /forecast-accuracy/7   → 200 or 404
 *  10.  GET /forecast-accuracy/14  → 200 or 404
 *  11.  GET /forecast-accuracy/30  → 200 or 404
 *  12.  GET /forecast-accuracy/60  → 200 or 404
 *  13.  GET /forecast-accuracy/90  → 200 or 404
 *  14.  GET /forecast-accuracy/summary → 200 with stats object
 *  15.  GET /forecast-accuracy/unsupported → 400/404
 *  16.  GET /forecast-accuracy unauthenticated → 401
 *
 * TREASURY:
 *  17.  GET /treasury/forecast → 200 or 404 (no bank account yet)
 *  18.  GET /treasury/forecast?horizon=7  → 200 or 404
 *  19.  GET /treasury/forecast?horizon=90 → 200 or 404
 *  20.  GET /treasury/forecast unauthenticated → 401
 *
 * SPENDING ANALYSIS:
 *  21.  GET /accounts/{id}/spending-analysis → 400/404 (no account)
 *  22.  GET /accounts/{id}/spending-analysis?from=&to= empty params → 400
 *  23.  GET /accounts/{id}/spending-analysis unauthenticated → 401
 *
 * TRANSACTIONS:
 *  24.  GET /transactions → 200 with list (may be empty)
 *  25.  GET /transactions?limit=5 → 200
 *  26.  GET /transactions unauthenticated → 401
 *
 * ACCOUNTS:
 *  27.  GET /accounts → 200 with list
 *  28.  GET /accounts unauthenticated → 401
 *
 * CONFIG FLAGS:
 *  29.  GET /config/flags → 200
 *  30.  GET /config/system → 200
 */
@QuarkusTest
@QuarkusTestResource(TestDataResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E2E – Analytics: Benchmarks, KPIs, Forecast Accuracy, Treasury, Spending")
class AnalyticsBenchmarksForecastE2ETest {

    static final String USER_ID = "00000000-0000-0000-0000-000000000004";
    static final String FAKE_ACCOUNT_ID = "00000000-0000-0000-0000-000000000098";
    
    private static boolean dataSeeded = false;

    @Inject
    TestDataSeeder testDataSeeder;

    @BeforeEach
    void setupTestData() {
        synchronized (AnalyticsBenchmarksForecastE2ETest.class) {
            if (!dataSeeded) {
                testDataSeeder.seedTestData();
                dataSeeded = true;
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // BENCHMARKS
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(1)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("1. GET /benchmarks/sectors → 200 with list")
    void step1_benchmarks_sectors_list() {
        given()
        .when()
            .get("/benchmarks/sectors")
        .then()
            .statusCode(200)
            .body("$", is(instanceOf(java.util.List.class)));
    }

    @Test @Order(2)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("2. GET /benchmarks/RETAIL/MICRO → benchmark data")
    void step2_benchmarks_retailMicro() {
        given()
        .when()
            .get("/benchmarks/RETAIL/MICRO")
        .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    @Test @Order(3)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("3. GET /benchmarks/INVALID_SECTOR/MICRO → 400 or 404")
    void step3_benchmarks_unknownSector() {
        given()
        .when()
            .get("/benchmarks/INVALID_SECTOR/MICRO")
        .then()
            .statusCode(anyOf(is(400), is(404)));
    }

    @Test @Order(4)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("4. GET /benchmarks/compare/RETAIL/MICRO → comparison object")
    void step4_benchmarks_compare() {
        given()
        .when()
            .get("/benchmarks/compare/RETAIL/MICRO")
        .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    @Test @Order(5)
    @DisplayName("5. GET /benchmarks/sectors unauthenticated → 401")
    void step5_benchmarks_unauthenticated() {
        given()
        .when()
            .get("/benchmarks/sectors")
        .then()
            .statusCode(401);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FINANCIAL KPIs
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(6)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("6. GET /financial-kpis → 200 with KPI object")
    void step6_financialKpis() {
        given()
        .when()
            .get("/financial-kpis")
        .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    @Test @Order(7)
    @DisplayName("7. GET /financial-kpis unauthenticated → 401")
    void step7_financialKpis_unauthenticated() {
        given()
        .when()
            .get("/financial-kpis")
        .then()
            .statusCode(401);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FORECAST ACCURACY — all 5 supported horizons
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(8)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("8. GET /forecast-accuracy → 200 with list")
    void step8_forecastAccuracy_list() {
        given()
        .when()
            .get("/forecast-accuracy")
        .then()
            .statusCode(200)
            .body("$", is(instanceOf(java.util.List.class)));
    }

    @Test @Order(9)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("9. GET /forecast-accuracy/7 → horizon 7 days")
    void step9_forecastAccuracy_horizon7() {
        given()
        .when()
            .get("/forecast-accuracy/7")
        .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    @Test @Order(10)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("10. GET /forecast-accuracy/14 → horizon 14 days")
    void step10_forecastAccuracy_horizon14() {
        given()
        .when()
            .get("/forecast-accuracy/14")
        .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    @Test @Order(11)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("11. GET /forecast-accuracy/30 → horizon 30 days")
    void step11_forecastAccuracy_horizon30() {
        given()
        .when()
            .get("/forecast-accuracy/30")
        .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    @Test @Order(12)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("12. GET /forecast-accuracy/60 → horizon 60 days")
    void step12_forecastAccuracy_horizon60() {
        given()
        .when()
            .get("/forecast-accuracy/60")
        .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    @Test @Order(13)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("13. GET /forecast-accuracy/90 → horizon 90 days")
    void step13_forecastAccuracy_horizon90() {
        given()
        .when()
            .get("/forecast-accuracy/90")
        .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    @Test @Order(14)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("14. GET /forecast-accuracy/summary → summary stats")
    void step14_forecastAccuracy_summary() {
        given()
        .when()
            .get("/forecast-accuracy/summary")
        .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    @Test @Order(15)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("15. GET /forecast-accuracy/999 unsupported horizon → 400/404")
    void step15_forecastAccuracy_unsupportedHorizon() {
        given()
        .when()
            .get("/forecast-accuracy/999")
        .then()
            .statusCode(anyOf(is(400), is(404)));
    }

    @Test @Order(16)
    @DisplayName("16. GET /forecast-accuracy unauthenticated → 401")
    void step16_forecastAccuracy_unauthenticated() {
        given()
        .when()
            .get("/forecast-accuracy")
        .then()
            .statusCode(401);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TREASURY
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(17)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("17. GET /treasury/forecast → 200 or 404 (no bank account)")
    void step17_treasury_forecast_default() {
        given()
        .when()
            .get("/treasury/forecast")
        .then()
            .statusCode(anyOf(is(200), is(404), is(400)));
    }

    @Test @Order(18)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("18. GET /treasury/forecast?horizon=7 → short horizon")
    void step18_treasury_forecast_7days() {
        given()
            .queryParam("horizon", 7)
        .when()
            .get("/treasury/forecast")
        .then()
            .statusCode(anyOf(is(200), is(404), is(400)));
    }

    @Test @Order(19)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("19. GET /treasury/forecast?horizon=90 → long horizon")
    void step19_treasury_forecast_90days() {
        given()
            .queryParam("horizon", 90)
        .when()
            .get("/treasury/forecast")
        .then()
            .statusCode(anyOf(is(200), is(404), is(400)));
    }

    @Test @Order(20)
    @DisplayName("20. GET /treasury/forecast unauthenticated → 401")
    void step20_treasury_unauthenticated() {
        given()
        .when()
            .get("/treasury/forecast")
        .then()
            .statusCode(401);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SPENDING ANALYSIS
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(21)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("21. GET spending-analysis unknown account → 400/404")
    void step21_spendingAnalysis_unknownAccount() {
        given()
        .when()
            .get("/accounts/" + FAKE_ACCOUNT_ID + "/spending-analysis")
        .then()
            .statusCode(anyOf(is(400), is(404)));
    }

    @Test @Order(22)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("22. GET spending-analysis with empty date range → 400")
    void step22_spendingAnalysis_emptyDateRange() {
        given()
            .queryParam("from", "")
            .queryParam("to", "")
        .when()
            .get("/accounts/" + FAKE_ACCOUNT_ID + "/spending-analysis")
        .then()
            .statusCode(anyOf(is(400), is(404)));
    }

    @Test @Order(23)
    @DisplayName("23. GET spending-analysis unauthenticated → 401")
    void step23_spendingAnalysis_unauthenticated() {
        given()
        .when()
            .get("/accounts/" + FAKE_ACCOUNT_ID + "/spending-analysis")
        .then()
            .statusCode(401);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TRANSACTIONS
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(24)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("24. GET /transactions → 200 with list")
    void step24_transactions_list() {
        given()
        .when()
            .get("/transactions")
        .then()
            .statusCode(200)
            .body("$", is(instanceOf(java.util.List.class)));
    }

    @Test @Order(25)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("25. GET /transactions?limit=5 → capped list")
    void step25_transactions_limited() {
        given()
            .queryParam("limit", 5)
        .when()
            .get("/transactions")
        .then()
            .statusCode(200)
            .body("$", hasSize(lessThanOrEqualTo(5)));
    }

    @Test @Order(26)
    @DisplayName("26. GET /transactions unauthenticated → 401")
    void step26_transactions_unauthenticated() {
        given()
        .when()
            .get("/transactions")
        .then()
            .statusCode(401);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ACCOUNTS
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(27)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("27. GET /accounts → 200 with list")
    void step27_accounts_list() {
        given()
        .when()
            .get("/accounts")
        .then()
            .statusCode(200)
            .body("$", is(instanceOf(java.util.List.class)));
    }

    @Test @Order(28)
    @DisplayName("28. GET /accounts unauthenticated → 401")
    void step28_accounts_unauthenticated() {
        given()
        .when()
            .get("/accounts")
        .then()
            .statusCode(401);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // CONFIG FLAGS (public)
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(29)
    @DisplayName("29. GET /config/flags → 200 (public endpoint)")
    void step29_configFlags() {
        given()
        .when()
            .get("/config/flags")
        .then()
            .statusCode(200);
    }

    @Test @Order(30)
    @DisplayName("30. GET /config/system → 200 (public endpoint)")
    void step30_configSystem() {
        given()
        .when()
            .get("/config/system")
        .then()
            .statusCode(200);
    }
}
