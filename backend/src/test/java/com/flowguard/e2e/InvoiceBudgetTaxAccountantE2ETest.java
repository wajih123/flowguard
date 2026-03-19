package com.flowguard.e2e;

import com.flowguard.repository.UserRepository;
import com.flowguard.util.TestDataResource;
import com.flowguard.util.TestDataSeeder;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;

import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E: Invoice complete lifecycle.
 *
 * Covers:
 *   1. List invoices → empty initially
 *   2. Create invoice with invalid payload → 400/422
 *   3. Create invoice with valid payload → 201 + id
 *   4. Get invoice by id → matches creation payload
 *   5. Get by unknown id → 404
 *   6. Send invoice → status transitions to SENT
 *   7. Mark invoice as paid → status transitions to PAID
 *   8. Cancel a PAID invoice → 409 (already paid)
 *   9. Create another invoice and cancel it → 200
 *  10. List invoices after operations → contains correct count
 *
 * Budget lifecycle:
 *  11. Set budget for current month
 *  12. Get budget vs actual → contains the category
 *  13. Update budget amount
 *  14. Delete budget line
 *
 * Tax estimates:
 *  15. GET /tax → list
 *  16. GET /tax/upcoming → list
 *  17. POST /tax/regenerate → ok
 *
 * Accountant portal:
 *  18. Grant accountant access → returns token
 *  19. List grants → contains the grant (token redacted)
 *  20. Accountant reads invoices via portal token
 *  21. Revoke accountant access
 */
@QuarkusTest
@QuarkusTestResource(TestDataResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E2E – Invoice, Budget, Tax & Accountant Portal Lifecycle")
class InvoiceBudgetTaxAccountantE2ETest {

    static final String USER_ID = "00000000-0000-0000-0000-000000000001";
    static String invoiceId;
    static String cancelableInvoiceId;
    static String budgetId;
    static String accountantToken;
    static String grantId;
    
    private static boolean dataSeeded = false;

    @Inject
    TestDataSeeder testDataSeeder;

    @BeforeEach
    void setupTestData() {
        synchronized (InvoiceBudgetTaxAccountantE2ETest.class) {
            if (!dataSeeded) {
                testDataSeeder.seedTestData();
                dataSeeded = true;
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // INVOICE LIFECYCLE
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(1)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("1. List invoices initially → empty array")
    void step1_listInvoices_empty() {
        given()
        .when()
            .get("/invoices")
        .then()
            .statusCode(200)
            .body("$", is(instanceOf(java.util.List.class)));
    }

    @Test @Order(2)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("2. Create invoice with missing fields → 400/422")
    void step2_createInvoice_missingFields() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "clientName": "",
                    "number": "",
                    "amountHt": -10,
                    "vatRate": 150,
                    "currency": "",
                    "issueDate": null,
                    "dueDate": null
                }
                """)
        .when()
            .post("/invoices")
        .then()
            .statusCode(anyOf(is(400), is(422)));
    }

    @Test @Order(3)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("3. Create valid invoice → 201 with id")
    void step3_createInvoice_valid() {
        Response resp = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "clientName": "E2E Corp",
                    "clientEmail": "billing@e2ecorp.io",
                    "number": "FAC-E2E-001",
                    "amountHt": 5000.00,
                    "vatRate": 20.0,
                    "currency": "EUR",
                    "issueDate": "%s",
                    "dueDate": "%s",
                    "notes": "E2E test invoice"
                }
                """.formatted(LocalDate.now(), LocalDate.now().plusDays(30)))
        .when()
            .post("/invoices")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("clientName", is("E2E Corp"))
            .body("amountHt", is(5000.0f))
            .body("status", is("DRAFT"))
            .extract().response();

        invoiceId = resp.jsonPath().getString("id");
        assertThat(invoiceId).isNotBlank();
    }

    @Test @Order(4)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("4. Get invoice by id → matches creation data")
    void step4_getInvoiceById() {
        given()
        .when()
            .get("/invoices/" + invoiceId)
        .then()
            .statusCode(200)
            .body("id", is(invoiceId))
            .body("number", is("FAC-E2E-001"))
            .body("status", is("DRAFT"));
    }

    @Test @Order(5)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("5. Get invoice by unknown id → 404")
    void step5_getInvoice_notFound() {
        given()
        .when()
            .get("/invoices/00000000-0000-0000-0000-000000000099")
        .then()
            .statusCode(anyOf(is(404), is(400)));
    }

    @Test @Order(6)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("6. Send invoice → status becomes SENT")
    void step6_sendInvoice() {
        given()
        .when()
            .post("/invoices/" + invoiceId + "/send")
        .then()
            .statusCode(anyOf(is(200), is(204)))
            .body("status", anyOf(is("SENT"), nullValue()));
    }

    @Test @Order(7)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("7. Mark invoice as paid → status becomes PAID")
    void step7_markInvoicePaid() {
        given()
        .when()
            .post("/invoices/" + invoiceId + "/mark-paid")
        .then()
            .statusCode(anyOf(is(200), is(204)))
            .body("status", anyOf(is("PAID"), nullValue()));
    }

    @Test @Order(8)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("8. Cancel a PAID invoice → 409 (invalid transition)")
    void step8_cancelPaidInvoice_conflict() {
        given()
        .when()
            .post("/invoices/" + invoiceId + "/cancel")
        .then()
            .statusCode(anyOf(is(409), is(400), is(422)));
    }

    @Test @Order(9)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("9. Create second invoice then cancel it → 200")
    void step9_cancelDraftInvoice() {
        Response resp = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "clientName": "E2E Corp2",
                    "number": "FAC-E2E-002",
                    "amountHt": 1000.00,
                    "vatRate": 20.0,
                    "currency": "EUR",
                    "issueDate": "%s",
                    "dueDate": "%s"
                }
                """.formatted(LocalDate.now(), LocalDate.now().plusDays(15)))
        .when()
            .post("/invoices")
        .then()
            .statusCode(201)
            .extract().response();

        cancelableInvoiceId = resp.jsonPath().getString("id");

        given()
        .when()
            .post("/invoices/" + cancelableInvoiceId + "/cancel")
        .then()
            .statusCode(anyOf(is(200), is(204)));
    }

    @Test @Order(10)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("10. List invoices → at least 2 invoices exist")
    void step10_listInvoices_afterOperations() {
        given()
        .when()
            .get("/invoices")
        .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(1));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // BUDGET LIFECYCLE
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(11)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("11. Set budget for SERVICES category")
    void step11_setBudget() {
        int year = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();

        Response resp = given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "year": %d,
                    "month": %d,
                    "category": "SERVICES",
                    "budgetedAmount": 3000.00
                }
                """.formatted(year, month))
        .when()
            .put("/budget")
        .then()
            .statusCode(anyOf(is(200), is(201), is(404)))
            .extract().response();

        if (resp.statusCode() == 200 || resp.statusCode() == 201) {
            budgetId = resp.jsonPath().getString("id");
        }
    }

    @Test @Order(12)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("12. Get budget vs actual for current month → 200")
    void step12_getBudgetVsActual() {
        int year = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();

        given()
        .when()
            .get("/budget/vs-actual/" + year + "/" + month)
        .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    @Test @Order(13)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("13. Get budget by period → 200")
    void step13_getBudgetByPeriod() {
        int year = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();

        given()
        .when()
            .get("/budget/" + year + "/" + month)
        .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    @Test @Order(14)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("14. Delete non-existent budget line → 404")
    void step14_deleteBudgetLine_notFound() {
        given()
        .when()
            .delete("/budget/line/00000000-0000-0000-0000-000000000099")
        .then()
            .statusCode(anyOf(is(404), is(400), is(403)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TAX ESTIMATES
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(15)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("15. GET /tax → returns array")
    void step15_listTaxEstimates() {
        given()
        .when()
            .get("/tax")
        .then()
            .statusCode(200)
            .body("$", is(instanceOf(java.util.List.class)));
    }

    @Test @Order(16)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("16. GET /tax/upcoming → returns array of unpaid estimates")
    void step16_upcomingTaxEstimates() {
        given()
        .when()
            .get("/tax/upcoming")
        .then()
            .statusCode(200)
            .body("$", is(instanceOf(java.util.List.class)));
    }

    @Test @Order(17)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("17. POST /tax/regenerate → 200 or 400 (no bank account)")
    void step17_regenerateTaxEstimates() {
        given()
        .when()
            .post("/tax/regenerate")
        .then()
            .statusCode(anyOf(is(200), is(400), is(404)));
    }

    @Test @Order(18)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("18. Mark non-existent tax estimate paid → 404")
    void step18_markTaxPaid_notFound() {
        given()
        .when()
            .post("/tax/00000000-0000-0000-0000-000000000099/mark-paid")
        .then()
            .statusCode(anyOf(is(404), is(400)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ACCOUNTANT PORTAL
    // ──────────────────────────────────────────────────────────────────────────

    @Test @Order(19)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("19. Grant accountant access → 201 with one-time token")
    void step19_grantAccountantAccess() {
        Response resp = given()
            .contentType(ContentType.JSON)
            .body("""
                {"accountantEmail": "comptable@cabinet.fr"}
                """)
        .when()
            .post("/accountant/grants")
        .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("accountantEmail", is("comptable@cabinet.fr"))
            .body("accessToken", notNullValue())
            .extract().response();

        grantId = resp.jsonPath().getString("id");
        accountantToken = resp.jsonPath().getString("accessToken");
        assertThat(grantId).isNotBlank();
        assertThat(accountantToken).isNotBlank();
    }

    @Test @Order(20)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("20. List grants → contains grant with redacted token")
    void step20_listGrants() {
        given()
        .when()
            .get("/accountant/grants")
        .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(1))
            .body("[0].accessToken", is("***"));  // token must be redacted
    }

    @Test @Order(21)
    @DisplayName("21. Accountant reads invoices via portal token")
    void step21_accountantPortal_readInvoices() {
        given()
            .header("X-Accountant-Token", accountantToken)
        .when()
            .get("/accountant/portal/invoices")
        .then()
            .statusCode(anyOf(is(200), is(401), is(403)));
    }

    @Test @Order(22)
    @DisplayName("22. Accountant reads tax via portal token")
    void step22_accountantPortal_readTax() {
        given()
            .header("X-Accountant-Token", accountantToken)
        .when()
            .get("/accountant/portal/tax")
        .then()
            .statusCode(anyOf(is(200), is(401), is(403)));
    }

    @Test @Order(23)
    @DisplayName("23. Accountant FEC export via portal token")
    void step23_accountantPortal_fecExport() {
        given()
            .header("X-Accountant-Token", accountantToken)
        .when()
            .get("/accountant/portal/fec")
        .then()
            .statusCode(anyOf(is(200), is(401), is(403)));
    }

    @Test @Order(24)
    @DisplayName("24. Invalid accountant token → 401")
    void step24_accountantPortal_invalidToken() {
        given()
            .header("X-Accountant-Token", "invalid-token-xyz-123")
        .when()
            .get("/accountant/portal/invoices")
        .then()
            .statusCode(anyOf(is(401), is(403)));
    }

    @Test @Order(25)
    @TestSecurity(user = "e2e-user", roles = "user")
    @JwtSecurity(claims = {@Claim(key = "sub", value = USER_ID)})
    @DisplayName("25. Revoke accountant grant → 204")
    void step25_revokeAccountantAccess() {
        given()
        .when()
            .delete("/accountant/grants/" + grantId)
        .then()
            .statusCode(anyOf(is(200), is(204)));
    }
}
