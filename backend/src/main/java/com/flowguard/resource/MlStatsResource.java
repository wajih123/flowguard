package com.flowguard.resource;

import com.flowguard.security.Roles;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ML monitoring & stats — admin only.
 * Surfaces model versions, MAE progressions, retrain log, and quality drift.
 */
@Path("/admin/ml")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({Roles.ADMIN, Roles.SUPER_ADMIN})
public class MlStatsResource {

    @Inject
    AgroalDataSource ds;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "flowguard.ml-service.url")
    String mlServiceUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ──────────────────────────────────────────────────────────────────────────
    //  GET /api/admin/ml/stats
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns all ML monitoring data:
     *   - activeVersion: the current ACTIVE model with its metrics
     *   - modelHistory: last 15 model versions ordered by creation date
     *   - retrainLog: last 10 retrain events with duration
     *   - qualityLog: last 30 days of prediction quality / drift
     *   - totalModels / totalRetrains: aggregate counts
     */
    @GET
    @Path("/stats")
    @RunOnVirtualThread
    public Response getStats() {
        try (Connection conn = ds.getConnection()) {
            Map<String, Object> result = new HashMap<>();

            // ── Active model version ──────────────────────────────────────────
            Map<String, Object> activeVersion = null;
            String sqlActive = """
                SELECT version, mae_7d, mae_30d, mae_90d,
                       deficit_recall, deficit_precision,
                       n_users_trained, status, created_at
                FROM model_versions
                WHERE status = 'ACTIVE'
                ORDER BY created_at DESC
                LIMIT 1
                """;
            try (PreparedStatement ps = conn.prepareStatement(sqlActive);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    activeVersion = rowToMap(rs);
                }
            }
            result.put("activeVersion", activeVersion);

            // ── Model history (last 15 versions) ─────────────────────────────
            List<Map<String, Object>> modelHistory = new ArrayList<>();
            String sqlHistory = """
                SELECT version, mae_7d, mae_30d, mae_90d,
                       deficit_recall, deficit_precision,
                       n_users_trained, status, created_at
                FROM model_versions
                ORDER BY created_at DESC
                LIMIT 15
                """;
            try (PreparedStatement ps = conn.prepareStatement(sqlHistory);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    modelHistory.add(rowToMap(rs));
                }
            }
            result.put("modelHistory", modelHistory);
            result.put("totalModels", modelHistory.size());

            // ── Retrain log (last 10 events) ──────────────────────────────────
            List<Map<String, Object>> retrainLog = new ArrayList<>();
            String sqlRetrain = """
                SELECT started_at, completed_at, reason, status,
                       n_users, final_mae, error,
                       CASE
                         WHEN completed_at IS NOT NULL
                         THEN EXTRACT(EPOCH FROM (completed_at - started_at)) / 60.0
                         ELSE NULL
                       END AS duration_min
                FROM ml_retrain_log
                ORDER BY started_at DESC
                LIMIT 10
                """;
            try (PreparedStatement ps = conn.prepareStatement(sqlRetrain);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    retrainLog.add(rowToMap(rs));
                }
            }
            result.put("retrainLog", retrainLog);
            result.put("totalRetrains", retrainLog.size());

            // ── Daily quality log (last 30 days) ─────────────────────────────
            List<Map<String, Object>> qualityLog = new ArrayList<>();
            String sqlQuality = """
                SELECT log_date, mae_7d, mae_30d,
                       drift_ratio_7d, drift_ratio_30d,
                       alert_triggered
                FROM prediction_quality_log
                ORDER BY log_date DESC
                LIMIT 30
                """;
            try (PreparedStatement ps = conn.prepareStatement(sqlQuality);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    qualityLog.add(rowToMap(rs));
                }
            }
            result.put("qualityLog", qualityLog);

            return Response.ok(result).build();

        } catch (SQLException e) {
            return Response.serverError()
                    .entity(Map.of("error", "Database error: " + e.getMessage()))
                    .build();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  POST /api/admin/ml/retrain
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Triggers an on-demand ML retrain via the internal ML service.
     * Returns the ML service's JSON response as-is.
     */
    @POST
    @Path("/retrain")
    @RolesAllowed(Roles.SUPER_ADMIN)
    @RunOnVirtualThread
    public Response triggerRetrain() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mlServiceUrl + "/retrain"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            // Pass through the ML service response (200 = QUEUED, other = error)
            return Response.status(response.statusCode())
                    .entity(response.body())
                    .build();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Response.serverError()
                    .entity(Map.of("error", "Request interrupted"))
                    .build();
        } catch (Exception e) {
            return Response.serverError()
                    .entity(Map.of("error", "ML service unreachable: " + e.getMessage()))
                    .build();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────────────────────────────

    /** Converts the current ResultSet row into a plain Map (JSON-serialisable). */
    private Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        Map<String, Object> row = new HashMap<>(cols);
        for (int i = 1; i <= cols; i++) {
            String col = meta.getColumnLabel(i);
            Object val = rs.getObject(i);
            // Normalise Postgres Timestamp → ISO String so JSON serialises cleanly
            if (val instanceof Timestamp ts) {
                row.put(col, ts.toInstant().toString());
            } else if (val instanceof java.sql.Date d) {
                row.put(col, d.toLocalDate().toString());
            } else {
                row.put(col, val);
            }
        }
        return row;
    }
}
