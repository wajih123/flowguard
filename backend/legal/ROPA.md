# FlowGuard — Registre des Traitements (ROPA)

# Conformité RGPD Art. 30 — Tenu par le Délégué à la Protection des Données (DPO)

# Dernière mise à jour : 2025-07-01

# Version : 1.2

---

## 1. Informations sur le Responsable de Traitement

| Champ               | Valeur                                 |
| ------------------- | -------------------------------------- |
| **Dénomination**    | FlowGuard SAS                          |
| **Forme juridique** | Société par Actions Simplifiée         |
| **SIRET**           | [À COMPLÉTER]                          |
| **Siège social**    | [À COMPLÉTER]                          |
| **Dirigeant**       | [À COMPLÉTER]                          |
| **Email DPO**       | dpo@flowguard.fr                       |
| **Téléphone DPO**   | [À COMPLÉTER]                          |
| **Référent CNIL**   | [Numéro de désignation CNIL à obtenir] |

---

## 2. Désignation du DPO (Art. 37 RGPD)

FlowGuard SAS a désigné un Délégué à la Protection des Données (DPO) conformément à l'article 37 du RGPD.

> **Obligations à remplir :**
>
> 1. Notifier la désignation du DPO à la CNIL via [notifications@cnil.fr](mailto:notifications@cnil.fr) ou le formulaire en ligne [CNIL — Désignation DPO](https://www.cnil.fr/fr/designation-dpo).
> 2. Le nom et les coordonnées du DPO doivent figurer dans la Politique de Confidentialité.
> 3. Le DPO doit être associé à toutes les décisions ayant un impact sur la protection des données.

---

## 3. Registre des Traitements

### Traitement #1 — Authentification et gestion des comptes utilisateurs

| Champ                       | Valeur                                                                                                    |
| --------------------------- | --------------------------------------------------------------------------------------------------------- |
| **Finalité**                | Création et gestion de comptes utilisateurs, authentification (JWT + MFA email OTP), gestion des sessions |
| **Base légale**             | Art. 6.1(b) — Exécution du contrat                                                                        |
| **Catégories de données**   | Nom, prénom, email, mot de passe (bcrypt), type d'utilisateur, statut KYC, rôle                           |
| **Catégories de personnes** | Utilisateurs inscrits (particuliers et entreprises)                                                       |
| **Destinataires**           | Aucun tiers — traitement interne uniquement                                                               |
| **Transferts hors UE**      | Aucun                                                                                                     |
| **Durée de conservation**   | 5 ans après résiliation du compte (Art. L561-12 CMF)                                                      |
| **Mesures de sécurité**     | Bcrypt (cost 12), JWT RS256, MFA email OTP, chiffrement AES-256-GCM des champs sensibles                  |
| **Sous-traitants**          | Hetzner Cloud (hébergement — serveurs en Allemagne, UE)                                                   |

---

### Traitement #2 — Agrégation bancaire (Open Banking DSP2)

| Champ                       | Valeur                                                                                                       |
| --------------------------- | ------------------------------------------------------------------------------------------------------------ |
| **Finalité**                | Agrégation des comptes bancaires, synchronisation des transactions, analyse de trésorerie                    |
| **Base légale**             | Art. 6.1(b) — Exécution du contrat ; Art. 6.1(a) — Consentement explicite (DSP2)                             |
| **Catégories de données**   | IBAN, solde, historique des transactions (libellé, montant, date, catégorie), données bancaires Open Banking |
| **Catégories de personnes** | Utilisateurs ayant connecté un compte bancaire                                                               |
| **Destinataires**           | Bridge API (PSP/agrégateur agréé DSP2), Nordigen/GoCardless                                                  |
| **Transferts hors UE**      | Aucun (Bridge : Paris, UE)                                                                                   |
| **Durée de conservation**   | Durée de la relation commerciale + 5 ans (données comptables)                                                |
| **Mesures de sécurité**     | OAuth2, webhook signature validation (HMAC-SHA256), chiffrement en transit (TLS 1.3)                         |
| **Sous-traitants**          | Bridge API (France), Nordigen/GoCardless (Lettonie, UE)                                                      |

---

### Traitement #3 — Flash Credit (crédit court terme)

| Champ                       | Valeur                                                                                   |
| --------------------------- | ---------------------------------------------------------------------------------------- |
| **Finalité**                | Octroi et gestion de microcrédits (Flash Credit), scoring de risque, recouvrement        |
| **Base légale**             | Art. 6.1(b) — Exécution du contrat ; Art. 6.1(c) — Obligation légale (LCC, TAEG)         |
| **Catégories de données**   | Montant, objet, statut de remboursement, historique des crédits, TAEG, clé d'idempotence |
| **Catégories de personnes** | Utilisateurs ayant souscrit un Flash Credit                                              |
| **Destinataires**           | Swan (IBAN virtuel / paiement), prestataire de scoring (si agréé ACPR)                   |
| **Transferts hors UE**      | Aucun                                                                                    |
| **Durée de conservation**   | 5 ans après remboursement complet (Code de la consommation Art. L224-x)                  |
| **Mesures de sécurité**     | Idempotence (clé idempotence UUID), validation TAEG, droits de rétractation 14 jours     |
| **Sous-traitants**          | Swan SAS (IBAN, paiements — agréé ACPR)                                                  |

---

### Traitement #4 — Analyse prédictive et IA (ML-Service)

| Champ                       | Valeur                                                                                              |
| --------------------------- | --------------------------------------------------------------------------------------------------- |
| **Finalité**                | Prédiction de trésorerie, détection d'anomalies, génération d'alertes proactives                    |
| **Base légale**             | Art. 6.1(b) — Exécution du contrat                                                                  |
| **Catégories de données**   | Historique transactionnel agrégé, patterns de dépenses, scores de risque                            |
| **Catégories de personnes** | Tous les utilisateurs avec comptes synchronisés                                                     |
| **Destinataires**           | Aucun tiers — traitement interne (ML-service hébergé sur même infrastructure)                       |
| **Transferts hors UE**      | Aucun                                                                                               |
| **Durée de conservation**   | Modèles ML : anonymisés. Données personnelles : même durée que Traitement #2                        |
| **Mesures de sécurité**     | Pseudonymisation des données d'entraînement, communication interne uniquement (réseau Docker privé) |
| **Sous-traitants**          | Hetzner Cloud                                                                                       |
| **Décision automatisée**    | Oui — Art. 22 RGPD applicable. Droit d'opposition et révision humaine disponibles.                  |

---

### Traitement #5 — Notifications push et alertes

| Champ                       | Valeur                                                                                                     |
| --------------------------- | ---------------------------------------------------------------------------------------------------------- |
| **Finalité**                | Envoi de notifications (alertes financières, OTP, informations compte)                                     |
| **Base légale**             | Art. 6.1(b) — Exécution du contrat                                                                         |
| **Catégories de données**   | Token FCM (Firebase), email, contenu de l'alerte                                                           |
| **Catégories de personnes** | Utilisateurs ayant activé les notifications                                                                |
| **Destinataires**           | Google Firebase Cloud Messaging (FCM)                                                                      |
| **Transferts hors UE**      | **Oui — Google LLC (États-Unis)** — Base : Clauses Contractuelles Types (SCCs) de la Commission européenne |
| **Durée de conservation**   | Tokens FCM : durée de la relation commerciale + 1 an                                                       |
| **Mesures de sécurité**     | Token Firebase sécurisé, TLS 1.3, révocabilité des tokens                                                  |
| **Sous-traitants**          | Google Firebase (USA — SCCs)                                                                               |

---

### Traitement #6 — LCB-FT / Conformité AML (Art. L561-x CMF)

| Champ                       | Valeur                                                                                                                                       |
| --------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| **Finalité**                | Lutte contre le blanchiment de capitaux et financement du terrorisme : vérification des listes de sanctions, déclarations de soupçon TRACFIN |
| **Base légale**             | Art. 6.1(c) — Obligation légale (Art. L561-5, L561-15 CMF)                                                                                   |
| **Catégories de données**   | Nom complet, date de naissance, résultats de criblage (score, source), narrative de soupçon                                                  |
| **Catégories de personnes** | Tous les utilisateurs à l'inscription ; utilisateurs identifiés comme à risque                                                               |
| **Destinataires**           | TRACFIN (service de renseignement financier — autorité publique)                                                                             |
| **Transferts hors UE**      | Aucun                                                                                                                                        |
| **Durée de conservation**   | **5 ans obligatoires** (Art. L561-12 CMF)                                                                                                    |
| **Mesures de sécurité**     | Accès restreint aux administrateurs compliance, journalisation immuable, interdiction de divulgation (tipping-off Art. L574-1)               |
| **Sous-traitants**          | Aucun — traitement strictement interne                                                                                                       |

---

### Traitement #7 — Journaux d'audit et sécurité

| Champ                       | Valeur                                                                                        |
| --------------------------- | --------------------------------------------------------------------------------------------- |
| **Finalité**                | Traçabilité des actions administratives, détection des incidents de sécurité, conformité ACPR |
| **Base légale**             | Art. 6.1(c) — Obligation légale ; Art. 6.1(f) — Intérêts légitimes (sécurité)                 |
| **Catégories de données**   | IP, user-agent, action effectuée, timestamp, identifiant utilisateur                          |
| **Catégories de personnes** | Administrateurs et utilisateurs authentifiés                                                  |
| **Destinataires**           | Prometheus/Grafana (monitoring interne)                                                       |
| **Transferts hors UE**      | Aucun                                                                                         |
| **Durée de conservation**   | 1 an (journaux opérationnels) ; 5 ans (journaux de conformité)                                |
| **Mesures de sécurité**     | Accès restreint, journaux en lecture seule pour utilisateurs, PostgreSQL Row-Level Security   |
| **Sous-traitants**          | Hetzner Cloud                                                                                 |

---

## 4. Analyse d'Impact (AIPD / DPIA — Art. 35 RGPD)

Les traitements suivants **nécessitent une AIPD** (décision automatisée avec effets significatifs) :

| Traitement             | Motif                                         | Statut        |
| ---------------------- | --------------------------------------------- | ------------- |
| Traitement #4 (IA/ML)  | Art. 22 RGPD — décision automatisée           | ⚠️ À réaliser |
| Traitement #6 (LCB-FT) | Surveillance systématique + données sensibles | ⚠️ À réaliser |
| Flash Credit scoring   | Décision de crédit automatisée                | ⚠️ À réaliser |

---

## 5. Droits des Personnes Concernées

Les droits suivants sont implémentés dans l'application :

| Droit           | Article RGPD | Endpoint API            | Délai légal |
| --------------- | ------------ | ----------------------- | ----------- |
| Accès           | Art. 15      | `GET /api/gdpr/export`  | 1 mois      |
| Portabilité     | Art. 20      | `GET /api/gdpr/export`  | 1 mois      |
| Rectification   | Art. 16      | `PUT /api/users/me`     | 1 mois      |
| Effacement      | Art. 17      | `DELETE /api/gdpr/data` | 1 mois      |
| Opposition (IA) | Art. 21      | [À implémenter]         | Sans délai  |
| Limitation      | Art. 18      | [À implémenter]         | 1 mois      |

---

## 6. Procédure de Notification de Violation (Art. 33 RGPD)

Voir le document [DATA_BREACH_PROCEDURE.md](./DATA_BREACH_PROCEDURE.md) pour la procédure complète.

**Délais légaux :**

- **72 heures** — Notification à la CNIL (Art. 33 RGPD) si violation à risque
- **Sans délai** — Notification aux personnes concernées si risque élevé (Art. 34 RGPD)

---

## 7. Historique des Modifications

| Date       | Version | Modification                          | Auteur        |
| ---------- | ------- | ------------------------------------- | ------------- |
| 2025-07-01 | 1.0     | Création initiale                     | DPO FlowGuard |
| 2025-07-01 | 1.1     | Ajout traitement LCB-FT (#6)          | DPO FlowGuard |
| 2025-07-01 | 1.2     | Ajout clés d'idempotence Flash Credit | DPO FlowGuard |
