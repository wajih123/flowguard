package com.flowguard.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Firebase Cloud Messaging integration for real-time push notifications.
 *
 * <p>Tokens are stored in the {@code push_tokens} table (one per user-device pair).
 * Fired by AlertService when severity ≥ HIGH.
 *
 * <p>Required env var: {@code FIREBASE_SERVICE_ACCOUNT_JSON} — contents of the
 * Firebase service account JSON file from the Firebase Console.
 */
@ApplicationScoped
public class PushNotificationService {

    private static final Logger LOG = Logger.getLogger(PushNotificationService.class);

    @ConfigProperty(name = "firebase.service-account-json", defaultValue = "")
    String serviceAccountJson;

    @Inject
    EntityManager em;

    private FirebaseMessaging messaging;

    void onStart(@Observes StartupEvent ev) {
        if (serviceAccountJson == null || serviceAccountJson.isBlank()) {
            LOG.warn("Firebase not configured (FIREBASE_SERVICE_ACCOUNT_JSON not set). Push notifications disabled.");
            return;
        }
        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)));

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
            messaging = FirebaseMessaging.getInstance();
            LOG.info("Firebase initialized successfully");
        } catch (IOException e) {
            LOG.errorf("Failed to initialize Firebase: %s", e.getMessage());
        }
    }

    // ── Token management ───────────────────────────────────────────────────────

    /**
     * Registers or updates a FCM device token for a user.
     *
     * @param userId   FlowGuard user ID
     * @param fcmToken FCM token from the mobile client
     * @param platform "ios" or "android"
     */
    @Transactional
    public void registerToken(UUID userId, String fcmToken, String platform) {
        em.createNativeQuery("""
            INSERT INTO push_tokens (id, user_id, fcm_token, platform, created_at, updated_at)
            VALUES (gen_random_uuid(), :userId, :token, :platform, NOW(), NOW())
            ON CONFLICT (user_id, fcm_token) DO UPDATE SET updated_at = NOW(), platform = :platform
            """)
          .setParameter("userId", userId)
          .setParameter("token", fcmToken)
          .setParameter("platform", platform)
          .executeUpdate();
    }

    /**
     * Removes a FCM token (on logout or device unregistration).
     */
    @Transactional
    public void removeToken(UUID userId, String fcmToken) {
        em.createNativeQuery(
            "DELETE FROM push_tokens WHERE user_id = :userId AND fcm_token = :token")
          .setParameter("userId", userId)
          .setParameter("token", fcmToken)
          .executeUpdate();
    }

    // ── Sending notifications ──────────────────────────────────────────────────

    /**
     * Sends a push notification to all devices registered for a user.
     *
     * @param userId   the target user
     * @param title    notification title
     * @param body     notification body
     * @param deepLink optional deep link (e.g. "flowguard://alerts/123")
     */
    public void sendToUser(UUID userId, String title, String body, Optional<String> deepLink) {
        if (messaging == null) return;

        List<String> tokens = getTokensForUser(userId);
        if (tokens.isEmpty()) return;

        for (String token : tokens) {
            sendToToken(token, title, body, deepLink);
        }
    }

    /**
     * Sends a financial alert push notification.
     */
    public void sendAlert(UUID userId, String alertType, String message, UUID alertId) {
        sendToUser(userId,
            titleForAlert(alertType),
            message,
            Optional.of("flowguard://alerts/" + alertId));
    }

    private void sendToToken(String fcmToken, String title, String body, Optional<String> deepLink) {
        try {
            Message.Builder builder = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder().setSound("default").build())
                            .build());

            deepLink.ifPresent(link ->
                builder.putData("deepLink", link));

            String messageId = messaging.send(builder.build());
            LOG.debugf("Push sent to token=%s messageId=%s", fcmToken.substring(0, 10) + "...", messageId);

        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                    || e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                LOG.warnf("Invalid FCM token detected, removing: %s", e.getMessage());
                removeInvalidToken(fcmToken);
            } else {
                LOG.warnf("FCM send failed for token %s: %s", fcmToken.substring(0, 10) + "...", e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> getTokensForUser(UUID userId) {
        return (List<String>) em.createNativeQuery(
            "SELECT fcm_token FROM push_tokens WHERE user_id = :userId AND active = TRUE")
          .setParameter("userId", userId)
          .getResultList();
    }

    @Transactional
    void removeInvalidToken(String fcmToken) {
        em.createNativeQuery("DELETE FROM push_tokens WHERE fcm_token = :token")
          .setParameter("token", fcmToken)
          .executeUpdate();
    }

    private String titleForAlert(String alertType) {
        return switch (alertType) {
            case "LOW_BALANCE"           -> "⚠️ Solde faible";
            case "ANOMALY_DETECTED"      -> "🔍 Dépense inhabituelle";
            case "CREDIT_DUE"            -> "📅 Remboursement à venir";
            case "FORECAST_NEGATIVE"     -> "📉 Trésorerie prévisionnelle basse";
            case "BRIDGE_DISCONNECTED"   -> "🔗 Connexion bancaire interrompue";
            case "CONSENT_EXPIRING"      -> "⏰ Autorisation bancaire bientôt expirée";
            default                      -> "🔔 FlowGuard";
        };
    }
}
