# FlowGuard — Procédure de Notification de Violation de Données

# RGPD Art. 33 & 34 — Plan de Réponse aux Incidents (PRI)

# Dernière mise à jour : 2025-07-01

---

## 1. Objectif

Ce document décrit la procédure à suivre en cas de violation de données personnelles
au sens de l'article 4.12 du RGPD, afin de respecter les obligations légales :

- **Art. 33 RGPD** : Notification à la CNIL dans les **72 heures**
- **Art. 34 RGPD** : Notification aux personnes concernées **sans délai** (risque élevé)
- **Art. 33.5 RGPD** : Documentation de TOUTES les violations dans le registre interne

---

## 2. Définition d'une Violation

Une violation de données est tout incident de sécurité entraînant, de manière accidentelle
ou illicite, la **destruction, perte, altération, divulgation non autorisée, ou accès non
autorisé** à des données personnelles.

**Exemples :**

- Accès non autorisé à la base de données
- Exfiltration de données (ransomware, SQL injection, breach API)
- Erreur d'envoi (email envoyé au mauvais destinataire)
- Perte d'un appareil contenant des sauvegardes
- Fuite via log applicatif

---

## 3. Étapes Immédiates (Heure 0-2)

```
DÉTECTION → CONFINEMENT → ÉVALUATION → NOTIFICATION
```

### Étape 1 — Détection et confinement (H+0 à H+1)

- [ ] **Isoler** le système compromis (bloquer l'accès, couper le service si nécessaire)
- [ ] **Préserver les preuves** : sauvegarder les logs, captures d'écran, timestamps
- [ ] **Contacter immédiatement** le DPO : `dpo@flowguard.fr`
- [ ] **Ouvrir un ticket** d'incident interne avec un UUID unique
- [ ] **Appeler** `DataBreachService.reportBreach()` pour déclencher les notifications automatiques

### Étape 2 — Évaluation initiale (H+1 à H+4)

Le DPO évalue :

- [ ] Quelles données ont été exposées ?
- [ ] Combien de personnes sont concernées ?
- [ ] Quelle est la probabilité d'un préjudice ?
- [ ] La violation est-elle toujours en cours ?

**Grille d'évaluation des risques (ENISA/CNIL) :**

| Gravité  | Critères                                                         | Action                              |
| -------- | ---------------------------------------------------------------- | ----------------------------------- |
| FAIBLE   | Données non-sensibles, accès interne, < 10 personnes             | Registre interne seulement          |
| MOYEN    | Données personnelles ordinaires, accès limité                    | Registre + évaluation CNIL 72h      |
| ÉLEVÉ    | Données financières/santé OU > 100 personnes                     | Notification CNIL obligatoire (72h) |
| CRITIQUE | Données très sensibles ET > 1000 personnes OU préjudice immédiat | CNIL + personnes concernées         |

---

## 4. Notification à la CNIL (Art. 33 — Délai : 72 heures)

### Quand notifier ?

Notification obligatoire si la violation est **susceptible d'engendrer un risque
pour les droits et libertés des personnes physiques** (probabilité et gravité non négligeables).

**En cas de doute, notifier.**

### Comment notifier ?

1. **Portail CNIL** : https://notifications.cnil.fr/notifications/index
2. **Email** : notifications@cnil.fr (confirmation uniquement)

### Informations requises par la CNIL

| Champ                                               | Source                    |
| --------------------------------------------------- | ------------------------- |
| Nature de la violation                              | Description de l'incident |
| Catégories et nombre approximatif de personnes      | Analyse technique         |
| Catégories et nombre approximatif d'enregistrements | Analyse technique         |
| Coordonnées du DPO                                  | dpo@flowguard.fr          |
| Conséquences probables                              | Évaluation DPO            |
| Mesures prises pour y remédier                      | Plan d'action             |

**Note :** Si l'information complète n'est pas disponible à H+72, il est possible de
notifier par étapes — notifier d'abord avec les informations disponibles, puis compléter.

---

## 5. Notification aux Personnes Concernées (Art. 34 — Sans délai)

### Quand notifier les utilisateurs ?

Obligatoire si la violation est **susceptible d'engendrer un risque ÉLEVÉ** pour
les droits et libertés des personnes concernées.

**Critères de risque élevé :**

- Données financières (IBAN, transactions) compromises
- Mots de passe potentiellement exposés
- Usurpation d'identité possible
- Vol de fonds potentiel

### Contenu de la notification aux utilisateurs

La notification doit contenir (Art. 34.2 RGPD) :

1. Description de la nature de la violation
2. Coordonnées du DPO
3. Conséquences probables
4. Mesures prises ou proposées

**Template email utilisateur :**

```
Objet : Information importante concernant la sécurité de votre compte FlowGuard

Madame, Monsieur,

Nous vous informons qu'un incident de sécurité a affecté FlowGuard [date].
[Description claire et non-technique de ce qui s'est passé.]

Données potentiellement concernées : [liste]

Ce que nous avons fait :
- [Mesure 1]
- [Mesure 2]

Ce que nous vous recommandons :
- Changer immédiatement votre mot de passe
- [Autres recommandations]

Notre Délégué à la Protection des Données est disponible à : dpo@flowguard.fr

Nous nous excusons sincèrement pour cet incident.
```

---

## 6. Registre des Violations (Art. 33.5)

**TOUTES** les violations (y compris celles considérées comme à risque faible)
doivent être documentées dans le registre interne.

Chaque entrée doit contenir :

- Date et heure de découverte
- Nature de la violation
- Données et personnes concernées
- Actions prises
- Justification en cas de non-notification à la CNIL

**Implémentation :** Le `DataBreachService.logToInternalRegister()` enregistre
chaque violation dans les logs applicatifs. Une table `breach_incidents` devra
être créée en production pour un stockage structuré (à implémenter).

---

## 7. Contacts d'urgence

| Rôle               | Contact                 | Disponibilité                    |
| ------------------ | ----------------------- | -------------------------------- |
| DPO FlowGuard      | dpo@flowguard.fr        | 24/7 (astreinte en cas de crise) |
| Compliance Officer | compliance@flowguard.fr | Heures ouvrées                   |
| CNIL (violations)  | notifications@cnil.fr   | 24/7 (formulaire en ligne)       |
| CTO / Technique    | [à compléter]           | Astreinte                        |

---

## 8. Post-incident

Après résolution, dans les 30 jours :

- [ ] Rapport post-mortem technique
- [ ] Mise à jour du ROPA si nécessaire
- [ ] Révision des mesures de sécurité
- [ ] Formation des équipes si une erreur humaine est impliquée
- [ ] Mise à jour du registre des violations avec le résultat final

---

_Référence : Art. 33, 34, 4.12 RGPD (Règlement UE 2016/679)_
_Guidelines EDPB sur la notification des violations de données personnelles (wp250rev.01)_
