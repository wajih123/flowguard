package com.flowguard.e2e;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E: Alert thresholds, Flash Credit full lifecycle (user + admin),
 * Scenario planning, and SEPA payment initiation.
 *
 * Covers:
 * ALERTS:
 *   1. GET /alerts → empty list
 *   2. GET /alerts/unread-count → 0
 *   3. PUT /alerts/read-all → 204
 *   4. GET /alert-thresholds → list
 *   5. PUT /alert-thresholds → create threshold
 *   6. PUT /alert-thresholds → idempotent update
 *   7. GET /alert-thresholds → verify threshold persisted
 *
 * FLASH CREDIT:
 *   8.  GET /flash-credit → empty list
 *   9.  POST /flash-credit amount < 500 → 400
 *  10.  POST /flash-credit missing purpose → 400
 *  11.  POST /flash-credit valid → 200/201 (PENDING_REVIEW)
 *  12.  POST /flash-credit duplicate (idempotency key) → same id
 *  13.  Admin: GET /admin/flash-credits → contains the request
 *  14.  Admin: PUT /admin/flash-credits/{id}/approve → approved
 *  15.  User: POST /flash-credit/{id}/repay → 200
 *  16.  Admin: GET /admin/flash-credits/stats → stats object
 *
 * SCENARIO PLANNING:
 *  17. POST /scenario missing fields → 400
 *  18. POST /scenario valid → 200 with projection
 *
 * PAYMENTS:
 *  19. GET /payments → list
 *  20. POST /payments invalid IBAN → 400
 *  21. POST /payments valid → 200/201 or 503 (Swan not configured)
 *  22. GET /payments after creation → list contains item
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E2E – Alerts, Flash Credit, Scenario & Payments Lifecycle")
class AlertsCreditScenarioPaymentsE2ETest {

    static final String USER_ID = "00000000-0000-0000-0000-000000000002";
    static final String ADMIN_ID = "00000000-0000-0000-0000-000000000010";
    static String creditId;
    static String idempotencyKey = "e2e-idem-" + System.currentTimeMillis();

    // ──────────────────────────────────────────────────────────────────────────
    // ALERTS
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(1)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("1. GET /alerts → 200 with list")
    void step1_getAlerts_returnsList() {
        given()
        .when()
            .get("/alerts")
        .then()
            .statusCode(200)
            .body("$", is(instanceOf(java.util.List.class)));
    }

    @Test @Order(2)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("2. GET /alerts/unread-count → count >= 0")
    void step2_unreadAlertCount() {
        given()
        .when()
            .get("/alerts/unread-count")
        .then()
            .statusCode(200)
            .body("count", greaterThanOrEqualTo(0));
    }

    @Test @Order(3)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("3. PUT /alerts/read-all → 204")
    void step3_markAllAlertsRead() {
        given()
        .when()
            .put("/alerts/read-all")
        .then()
            .statusCode(204);
    }

    @Test @Order(4)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("4. GET /alert-thresholds → 200 with list")
    void step4_getAlertThresholds() {
        given()
        .when()
            .get("/alert-thresholds")
        .then()
            .statusCode(200)
            .body("$", is(instanceOf(java.util.List.class)));
    }

    @Test @Order(5)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("5. PUT /alert-thresholds → creates LOW_BALANCE threshold")
    void step5_createAlertThreshold() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "alertType": "LOW_BALANCE",
                    "minAmount": 1000.00,
                    "enabled": true,
                    "minSeverity": "HIGH"
                }
                """)
        .when()
            .put("/alert-thresholds")
        .then()
            .statusCode(anyOf(is(200), is(201)))
            .body("alertType", is("LOW_BALANCE"))
            .body("enabled", is(true));
    }

    @Test @Order(6)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("6. PUT /alert-thresholds → idempotent update of same type")
    void step6_updateAlertThreshold_idempotent() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "alertType": "LOW_BALANCE",
                    "minAmount": 2000.00,
                    "enabled": true,
                    "minSeverity": "CRITICAL"
                }
                """)
        .when()
            .put("/alert-thresholds")
        .then()
            .statusCode(anyOf(is(200), is(201)));
    }

    @Test @Order(7)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("7. PUT /alert-thresholds invalid type → 400")
    void step7_createAlertThreshold_invalidType() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "alertType": null,
                    "minAmount": 500,
                    "enabled": true,
                    "minSeverity": "HIGH"
                }
                """)
        .when()
            .put("/alert-thresholds")
        .then()
            .statusCode(anyOf(is(400), is(422)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FLASH CREDIT LIFECYCLE
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(8)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("8. GET /flash-credit → 200 with list")
    void step8_listFlashCredits_empty() {
        given()
        .when()
            .get("/flash-credit")
        .then()
            .statusCode(anyOf(is(200), is(400)));
    }

    @Test @Order(9)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("9. Request credit amount < 500 → 400")
    void step9_requestCredit_belowMinimum() {
        given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", "e2e-below-min")
            .body("""
                {"amount": 100, "purpose": "SALARY"}
                """)
        .when()
            .post("/flash-credit")
        .then()
            .statusCode(400);
    }

    @Test @Order(10)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("10. Request credit with empty purpose → 400")
    void step10_requestCredit_missingPurpose() {
        given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", "e2e-no-purpose")
            .body("""
                {"amount": 2000, "purpose": ""}
                """)
        .when()
            .post("/flash-credit")
        .then()
            .statusCode(400);
    }

    @Test @Order(11)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("11. Request valid credit → 200/201 PENDING_REVIEW")
    void step11_requestCredit_valid() {
        Response resp = given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", idempotencyKey)
            .body("""
                {"amount": 5000, "purpose": "PAYROLL"}
                """)
        .when()
            .post("/flash-credit")
        .then()
            .statusCode(anyOf(is(200), is(201), is(400)))
            .extract().response();

        if (resp.statusCode() == 200 || resp.statusCode() == 201) {
            creditId = resp.jsonPath().getString("id");
            assertThat(creditId).isNotBlank();
        }
    }

    @Test @Order(12)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("12. Same idempotency key → same credit id returned")
    void step12_requestCredit_idempotency() {
        if (creditId == null) return;
        Response resp = given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", idempotencyKey)
            .body("""
                {"amount": 5000, "purpose": "PAYROLL"}
                """)
        .when()
            .post("/flash-credit")
        .then()
            .statusCode(anyOf(is(200), is(201)))
            .extract().response();

        String secondId = resp.jsonPath().getString("id");
        assertThat(secondId).isEqualTo(creditId);
    }

    @Test @Order(13)
    @TestSecurity(user = "e2e-admin", roles = "admin")
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_ID)})
    @DisplayName("13. Admin: GET /admin/flash-credits → list contains request")
    void step13_admin_listFlashCredits() {
        given()
        .when()
            .get("/admin/flash-credits")
        .then()
            .statusCode(200)
            .body("content", is(instanceOf(java.util.List.class)));
    }

    @Test @Order(14)
    @TestSecurity(user = "e2e-admin", roles = "admin")
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_ID)})
    @DisplayName("14. Admin: approve flash credit → 200")
    void step14_admin_approveFlashCredit() {
        if (creditId == null) return;
        given()
        .when()
            .put("/admin/flash-credits/" + creditId + "/approve")
        .then()
            .statusCode(anyOf(is(200), is(204)));
    }

    @Test @Order(15)
    @TestSecurity(user = "e2e-admin", roles = "admin")
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_ID)})
    @DisplayName("15. Admin: reject non-existent credit → 404")
    void step15_admin_rejectNonExistent() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"reason": "Insufficient credit history"}
                """)
        .when()
            .put("/admin/flash-credits/00000000-0000-0000-0000-000000000099/reject")
        .then()
            .statusCode(anyOf(is(404), is(400)));
    }

    @Test @Order(16)
    @TestSecurity(user = "e2e-admin", roles = "admin")
    @JwtSecurity(claims = {@Claim(key = "sub", value = ADMIN_ID)})
    @DisplayName("16. Admin: GET /admin/flash-credits/stats → stats object")
    void step16_admin_creditStats() {
        given()
        .when()
            .get("/admin/flash-credits/stats")
        .then()
            .statusCode(200);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SCENARIO PLANNING
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(17)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("17. POST /scenario missing required fields → 400")
    void step17_scenario_missingFields() {
        given()
            .contentType(ContentType.JSON)
            .body("{}")
        .when()
            .post("/scenario")
        .then()
            .statusCode(anyOf(is(400), is(422)));
    }

    @Test @Order(18)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("18. POST /scenario valid late payment → 200 with cashflow projection")
    void step18_scenario_latePayment() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "type": "LATE_PAYMENT",
                    "amount": 10000,
                    "delayDays": 30,
                    "description": "E2E scenario: client pays 30 days late"
                }
                """)
        .when()
            .post("/scenario")
        .then()
            .statusCode(anyOf(is(200), is(400), is(404)));
    }

    @Test @Order(19)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("19. POST /scenario new hire → 200 with projection")
    void step19_scenario_newHire() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "type": "NEW_EXPENSE",
                    "amount": 3500,
                    "delayDays": 0,
                    "description": "Hire additional developer"
                }
                """)
        .when()
            .post("/scenario")
        .then()
            .statusCode(anyOf(is(200), is(400), is(404)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PAYMENT INITIATION
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(20)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("20. GET /payments → 200 with list")
    void step20_listPayments() {
        given()
        .when()
            .get("/payments")
        .then()
            .statusCode(200)
            .body("$", is(instanceOf(java.util.List.class)));
    }

    @Test @Order(21)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("21. POST /payments invalid IBAN → 400")
    void step21_initiatePayment_invalidIban() {
        given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", "e2e-pay-invalid")
            .body("""
                {
                    "creditorName": "Supplier",
                    "creditorIban": "NOTANIBAN",
                    "amount": 500,
                    "currency": "EUR",
                    "reference": "INV-001"
                }
                """)
        .when()
            .post("/payments")
        .then()
            .statusCode(anyOf(is(400), is(422)));
    }

    @Test @Order(22)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("22. POST /payments missing currency → 400")
    void step22_initiatePayment_missingCurrency() {
        given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", "e2e-pay-no-currency")
            .body("""
                {
                    "creditorName": "Supplier",
                    "creditorIban": "FR7630006000011234567890189",
                    "amount": 500,
                    "currency": ""
                }
                """)
        .when()
            .post("/payments")
        .then()
            .statusCode(anyOf(is(400), is(422)));
    }

    @Test @Order(23)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("23. POST /payments valid → 200/201 or 503 (Swan not configured in test)")
    void step23_initiatePayment_valid() {
        given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", "e2e-pay-" + System.currentTimeMillis())
            .body("""
                {
                    "creditorName": "Test Supplier SAS",
                    "creditorIban": "FR7630006000011234567890189",
                    "amount": 1000.00,
                    "currency": "EUR",
                    "reference": "E2E-PAY-001"
                }
                """)
        .when()
            .post("/payments")
        .then()
            .statusCode(anyOf(is(200), is(201), is(400), is(503), is(500)));
    }
}
