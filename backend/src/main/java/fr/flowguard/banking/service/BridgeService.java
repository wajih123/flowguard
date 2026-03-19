package fr.flowguard.banking.service;

import fr.flowguard.banking.client.BridgeApiClient;
import fr.flowguard.banking.entity.BankAccountEntity;
import fr.flowguard.banking.entity.BridgeConnectSessionEntity;
import fr.flowguard.banking.entity.BridgeUserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import fr.flowguard.banking.entity.TransactionEntity;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class BridgeService {

    private static final Logger LOG = Logger.getLogger(BridgeService.class);

    @Inject
    BridgeApiClient bridgeClient;

    @ConfigProperty(name = "bridge.redirect-url")
    String redirectUrl;

    @ConfigProperty(name = "bridge.mock-mode", defaultValue = "false")
    boolean mockMode;

    @Transactional
    public ConnectStartResult startConnect(String userId) {
        String state = UUID.randomUUID().toString().replace("-", "");

        BridgeConnectSessionEntity connectSess = new BridgeConnectSessionEntity();
        connectSess.userId = userId;
        connectSess.state = state;
        connectSess.expiresAt = Instant.now().plus(Duration.ofHours(1));

        if (mockMode) {
            String callbackUrl = redirectUrl + "?context=" + state;
            connectSess.connectUrl = callbackUrl;
            connectSess.persist();
            LOG.infof("[MOCK] Connect session cree pour userId=%s state=%s", userId, state);
            return new ConnectStartResult(callbackUrl, state);
        }

        try {
            BridgeUserEntity bridgeUser = getOrCreateBridgeUser(userId);
            String accessToken = getUserToken(bridgeUser);

            // User email: use placeholder derived from userId (pre-fills Bridge UI)
            String userEmail = "user-" + userId.replace("-", "").substring(0, Math.min(12, userId.replace("-", "").length())) + "@flowguard.io";

            BridgeApiClient.ConnectSessionResponse session =
                bridgeClient.createConnectSession(accessToken, userEmail, state, redirectUrl);

            String connectUrl = session.getConnectUrl();
            if (connectUrl == null) throw new IllegalStateException("Bridge n'a pas retourne d'URL");

            connectSess.connectUrl = connectUrl;
            connectSess.persist();
            LOG.infof("Connect session cree pour userId=%s state=%s", userId, state);
            return new ConnectStartResult(connectUrl, state);

        } catch (BridgeApiClient.BridgeApiException e) {
            LOG.warnf("Bridge API indisponible (%d), passage en mode mock", e.statusCode);
            String callbackUrl = redirectUrl + "?context=" + state;
            connectSess.connectUrl = callbackUrl;
            connectSess.persist();
            return new ConnectStartResult(callbackUrl, state);
        }
    }

    @Transactional
    public SyncResult handleCallback(String state, String userId) {
        BridgeConnectSessionEntity session = BridgeConnectSessionEntity.findByState(state);
        if (session == null || !session.isValid()) {
            throw new IllegalArgumentException("Session invalide ou expiree");
        }
        if (!session.userId.equals(userId)) {
            throw new SecurityException("Session ne correspond pas a l'utilisateur");
        }
        session.used = true;

        if (mockMode) {
            return addMockAccounts(userId);
        }

        BridgeUserEntity bridgeUser = BridgeUserEntity.findByUserId(userId);
        if (bridgeUser == null) {
            LOG.warnf("[Bridge] Callback: aucun utilisateur Bridge pour userId=%s", userId);
            return new SyncResult(0, Instant.now());
        }
        String accessToken = getUserToken(bridgeUser);
        return syncAccountsInternal(userId, bridgeUser.bridgeUuid, accessToken);
    }

    @Transactional
    public SyncResult syncAccounts(String userId) {
        BridgeUserEntity bridgeUser = BridgeUserEntity.findByUserId(userId);
        if (bridgeUser == null) {
            if (mockMode) return addMockAccounts(userId);
            throw new IllegalStateException("Aucun compte Bridge connecte pour cet utilisateur");
        }
        String accessToken = getUserToken(bridgeUser);
        return syncAccountsInternal(userId, bridgeUser.bridgeUuid, accessToken);
    }

    public List<BankAccountEntity> getAccounts(String userId) {
        return BankAccountEntity.findActiveByUserId(userId);
    }

    // ── Private bridge helpers ────────────────────────────────────────────────

    /**
     * Gets existing BridgeUserEntity or creates a new Bridge user via API.
     * Uses FlowGuard userId as the external_user_id.
     */
    private BridgeUserEntity getOrCreateBridgeUser(String userId) {
        BridgeUserEntity existing = BridgeUserEntity.findByUserId(userId);
        if (existing != null) return existing;

        String bridgeUuid;
        try {
            BridgeApiClient.CreateUserResponse resp = bridgeClient.createUser(userId);
            bridgeUuid = resp.uuid;
        } catch (BridgeApiClient.BridgeApiException e) {
            if (e.statusCode == 409) {
                // User already exists — fetch their UUID
                BridgeApiClient.GetUsersResponse existing2 = bridgeClient.getUserByExternalId(userId);
                if (existing2.resources != null && !existing2.resources.isEmpty()) {
                    bridgeUuid = existing2.resources.get(0).uuid;
                } else {
                    throw new IllegalStateException("Bridge user conflict but not found: " + userId);
                }
            } else {
                throw e;
            }
        }

        BridgeUserEntity entity = new BridgeUserEntity();
        entity.userId = userId;
        entity.bridgeUuid = bridgeUuid;
        // bridgeEmail is NOT NULL in DB — set a placeholder so constraint is satisfied
        entity.bridgeEmail = "user-" + userId.replace("-", "").substring(0, Math.min(12, userId.replace("-", "").length())) + "@flowguard.io";
        entity.createdAt = Instant.now();
        entity.persist();

        LOG.infof("Bridge user cree: userId=%s bridgeUuid=%s", userId, bridgeUuid);
        return entity;
    }

    /**
     * Gets a fresh user access token, using cached value if still valid.
     */
    private String getUserToken(BridgeUserEntity bridgeUser) {
        if (bridgeUser.isTokenValid()) {
            LOG.debugf("[Bridge] Using cached token for user=%s", bridgeUser.userId);
            return bridgeUser.bridgeAccessToken;
        }

        BridgeApiClient.TokenResponse resp = bridgeClient.getUserToken(bridgeUser.bridgeUuid);
        bridgeUser.bridgeAccessToken = resp.access_token;
        // Bridge tokens are valid ~30 minutes; cache for 25 min to be safe
        bridgeUser.bridgeTokenExpiresAt = Instant.now().plus(Duration.ofMinutes(25));
        return resp.access_token;
    }

    private SyncResult syncAccountsInternal(String userId, String bridgeUuid, String accessToken) {
        BridgeApiClient.AccountsListResponse resp = bridgeClient.listAccounts(accessToken);
        Instant now = Instant.now();
        int count = 0;

        if (resp.resources != null) {
            for (BridgeApiClient.AccountsListResponse.AccountDto dto : resp.resources) {
                String bridgeAccountId = dto.id + "_" + userId;
                BankAccountEntity acc = BankAccountEntity.findByBridgeAccountId(bridgeAccountId);
                if (acc == null) {
                    acc = new BankAccountEntity();
                    acc.userId = userId;
                    acc.bridgeAccountId = bridgeAccountId;
                }
                acc.bankName = dto.provider_name != null ? dto.provider_name : "Banque";
                acc.accountName = dto.name;
                acc.ibanMasked = dto.iban != null ? maskIban(dto.iban) : null;
                acc.currentBalance = dto.balance != null ? BigDecimal.valueOf(dto.balance) : BigDecimal.ZERO;
                acc.currency = dto.currency_code != null ? dto.currency_code : "EUR";
                acc.accountType = dto.type != null ? dto.type : "CHECKING";
                acc.syncStatus = "OK";
                acc.lastSyncAt = now;
                if (acc.id == null) {
                    acc.persist();
                }
                count++;
            }
        }

        // Sync transactions for each account from Bridge
        if (resp.resources != null) {
            for (BridgeApiClient.AccountsListResponse.AccountDto dto : resp.resources) {
                if (dto.id == null) continue;
                String baId = dto.id + "_" + userId;
                BankAccountEntity acct = BankAccountEntity.findByBridgeAccountId(baId);
                if (acct != null) syncTransactionsForAccount(acct.id, dto.id, accessToken);
            }
        }

        LOG.infof("Sync OK: %d comptes pour userId=%s", count, userId);
        return new SyncResult(count, now);
    }


    // ─────────────────────────────────────────────────────────────────────────
    // Bridge transaction sync
    // ─────────────────────────────────────────────────────────────────────────

    @jakarta.transaction.Transactional
    void syncTransactionsForAccount(String accountId, Long bridgeAccountId, String accessToken) {
        try {
            BridgeApiClient.TransactionsListResponse resp =
                bridgeClient.listTransactions(accessToken, bridgeAccountId);
            if (resp == null || resp.resources == null) return;

            int synced = 0;
            for (BridgeApiClient.TransactionsListResponse.TransactionDto dto : resp.resources) {
                if (Boolean.TRUE.equals(dto.deleted)) continue;
                String externalId = "bridge_" + dto.id;
                if (TransactionEntity.findByExternalId(externalId) != null) continue;

                TransactionEntity tx = new TransactionEntity();
                tx.accountId  = accountId;
                tx.externalId = externalId;
                tx.amount     = dto.amount != null ? java.math.BigDecimal.valueOf(dto.amount) : java.math.BigDecimal.ZERO;
                tx.currency   = dto.currency_code != null ? dto.currency_code : "EUR";

                String raw = (dto.clean_description != null && !dto.clean_description.isBlank())
                    ? dto.clean_description : dto.provider_description;
                tx.label = raw != null ? raw : "";

                tx.category = mapBridgeCategory(dto.category_id, dto.operation_type);

                try {
                    tx.transactionDate = dto.date != null ? LocalDate.parse(dto.date) : LocalDate.now();
                } catch (Exception e) {
                    tx.transactionDate = LocalDate.now();
                }
                try {
                    tx.bookingDate = dto.booking_date != null
                        ? LocalDate.parse(dto.booking_date) : tx.transactionDate;
                } catch (Exception ignored) {
                    tx.bookingDate = tx.transactionDate;
                }
                tx.persist();
                synced++;
            }
            LOG.infof("[Bridge] %d transactions synced for accountId=%s", synced, accountId);
        } catch (Exception e) {
            LOG.warnf("[Bridge] Transaction sync error accountId=%s: %s", accountId, e.getMessage());
        }
    }

    private String mapBridgeCategory(Integer catId, String opType) {
        if (opType != null) switch (opType) {
            case "bank_fee": return "CHARGES_BANCAIRES";
            case "loan":     return "CREDIT";
            case "salary":   return "SALAIRE";
            case "transfer": return "VIREMENT";
        }
        if (catId == null) return "AUTRE";
        if (catId >= 1   && catId < 20)  return "SALAIRE";
        if (catId >= 60  && catId < 100) return "ALIMENTATION";
        if (catId >= 100 && catId < 150) return "TRANSPORT";
        if (catId >= 150 && catId < 200) return "LOISIRS";
        if (catId >= 200 && catId < 260) return "LOGEMENT";
        if (catId >= 260 && catId < 300) return "SANTE";
        if (catId >= 300 && catId < 350) return "ABONNEMENT";
        return "AUTRE";
    }

    private static String maskIban(String iban) {
        if (iban == null || iban.length() < 8) return iban;
        String clean = iban.replace(" ", "");
        return clean.substring(0, 4) + " **** **** " + clean.substring(clean.length() - 4);
    }

    // ── Mock accounts for development/demo ───────────────────────────────────

    private SyncResult addMockAccounts(String userId) {
        Instant now = Instant.now();
        int count = 0;

        Object[][] mockData = {
            {"mock-boursorama-001", "Boursorama Banque", "Compte courant", "FR76 1234 5678 9012 3456 7890 123", 4250.80, "CHECKING"},
            {"mock-boursorama-002", "Boursorama Banque", "Livret A", null, 15000.00, "SAVINGS"},
            {"mock-bnp-001", "BNP Paribas", "Compte courant", "FR76 3000 4000 0123 4567 8901 234", 1820.45, "CHECKING"}
        };

        for (Object[] row : mockData) {
            String bridgeAccId = (String) row[0];
            BankAccountEntity existing = BankAccountEntity.findByBridgeAccountId(bridgeAccId + "_" + userId);
            if (existing == null) {
                BankAccountEntity acc = new BankAccountEntity();
                acc.userId = userId;
                acc.bridgeAccountId = bridgeAccId + "_" + userId;
                acc.bankName = (String) row[1];
                acc.accountName = (String) row[2];
                acc.ibanMasked = (String) row[3];
                acc.currentBalance = BigDecimal.valueOf((Double) row[4]);
                acc.currency = "EUR";
                acc.accountType = (String) row[5];
                acc.syncStatus = "OK";
                acc.lastSyncAt = now;
                acc.persist();
                count++;
            }
        }

        LOG.infof("[MOCK] %d comptes sandbox ajoutes pour userId=%s", count, userId);
        return new SyncResult(count, now);
    }

    // ── Result records ────────────────────────────────────────────────────────

    public record ConnectStartResult(String connectUrl, String state) {}
    public record SyncResult(int accountsSynced, Instant syncedAt) {}
}
