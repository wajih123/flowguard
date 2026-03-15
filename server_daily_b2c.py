#!/usr/bin/env python3
"""
FlowGuard — B2C Daily Transaction Refresh (server-side cron)
============================================================
Runs every day at 02:00 via cron on the server.
Generates one day of transactions for every B2C user in the database.

Cron entry:
    0 2 * * * /usr/bin/python3 /opt/flowguard/server_daily_b2c.py >> /opt/flowguard/b2c_daily.log 2>&1
"""

from __future__ import annotations

import csv
import io
import random
import subprocess
import sys
import uuid
from datetime import date, timedelta

# ── The profile config rebuilder must match server_gen_b2c.py EXACTLY ─────────

def build_profile_config(profile: str, rng: random.Random) -> dict:
    if profile == "B2C_SALARIE":
        return {
            "salary":     rng.uniform(1850, 3200),
            "loyer":      rng.uniform(500, 920),
            "mobile_day": rng.randint(5, 8),
            "inet_day":   rng.randint(5, 8),
        }
    if profile == "B2C_CADRE":
        return {"salary": rng.uniform(3200, 6500), "loyer": rng.uniform(1200, 2600)}
    if profile == "B2C_ETUDIANT":
        return {
            "allocation": rng.uniform(380, 750),
            "loyer":      rng.uniform(360, 680),
            "has_job":    rng.random() < 0.6,
        }
    if profile == "B2C_RETRAITE":
        return {
            "pension_cnav": rng.uniform(750, 1900),
            "pension_comp": rng.uniform(200, 650),
            "loyer":        rng.uniform(400, 820),
        }
    if profile == "B2C_FAMILLE":
        return {
            "salary1":  rng.uniform(2500, 4500),
            "salary2":  rng.uniform(2000, 3800),
            "loyer":    rng.uniform(1000, 1900),
            "garderie": rng.uniform(350, 950),
        }
    return {}


def cr(account_id, amount, label, category, d, recurring=False):
    tx_id = str(uuid.uuid4())
    return [tx_id, account_id, f"{abs(amount):.2f}", "CREDIT",
            label[:500], category, d.isoformat(),
            "t" if recurring else "f", f"b2c-{tx_id[:8]}"]


def db(account_id, amount, label, category, d, recurring=False):
    tx_id = str(uuid.uuid4())
    return [tx_id, account_id, f"{-abs(amount):.2f}", "DEBIT",
            label[:500], category, d.isoformat(),
            "t" if recurring else "f", f"b2c-{tx_id[:8]}"]


# ── Profile day generators (identical logic as server_gen_b2c.py) ──────────────

def gen_day_salarie(account_id, d, rng, cfg):
    rows = []
    salary, loyer, mobile_day, inet_day = (
        cfg["salary"], cfg["loyer"], cfg["mobile_day"], cfg["inet_day"])
    dow = d.weekday()

    if d.day in (27, 28, 1, 2, 3) and rng.random() < 0.40:
        rows.append(cr(account_id, salary * rng.uniform(0.96, 1.04),
                       rng.choice(["Virement salaire SARL Dubois",
                                   "Paie mensuelle SAS Renault Group",
                                   "Virement mensuel net SNCF",
                                   "Salaire LCL Banque", "Paie Carrefour net",
                                   "Virement mensuel Decathlon", "Salaire Orange SA"]),
                       "SALAIRE", d, True))
    if d.day == 1:
        rows.append(db(account_id, loyer, "Loyer mensuel appartement", "LOYER", d, True))
        rows.append(db(account_id, 84.10, "Navigo Mensuel RATP Ile-de-France", "TRANSPORT", d, True))
    if d.day == 15:
        rows.append(db(account_id, rng.uniform(60, 130), "EDF facture electricite mensuelle", "ENERGIE", d, True))
    if d.day == mobile_day:
        rows.append(db(account_id, rng.uniform(15, 35),
                       rng.choice(["SFR Mobile forfait", "Orange Mobile mensuel",
                                   "Bouygues Telecom mobile", "Free Mobile"]),
                       "TELECOM", d, True))
    if d.day == inet_day:
        rows.append(db(account_id, rng.uniform(25, 42),
                       rng.choice(["SFR Box Internet", "Orange Livebox mensuel",
                                   "Bouygues Bbox", "Free Haut Debit"]),
                       "TELECOM", d, True))
    if d.day == 8:
        if rng.random() < 0.70:
            rows.append(db(account_id, rng.uniform(8, 18), "Netflix abonnement mensuel", "ABONNEMENT", d, True))
        if rng.random() < 0.65:
            rows.append(db(account_id, rng.uniform(5, 12), "Spotify Premium", "ABONNEMENT", d, True))
        if rng.random() < 0.35:
            rows.append(db(account_id, rng.uniform(6, 14), "Amazon Prime abonnement", "ABONNEMENT", d, True))
    if d.day == 10:
        rows.append(db(account_id, rng.uniform(18, 45), "MAIF Assurance habitation", "ASSURANCE", d, True))
        if rng.random() < 0.60:
            rows.append(db(account_id, rng.uniform(35, 90), "AXA Assurance auto", "ASSURANCE", d, True))
    if dow in (0, 2, 4) and rng.random() < 0.65:
        rows.append(db(account_id, rng.uniform(18, 95),
                       rng.choice(["Carrefour Market courses", "Lidl caisse",
                                   "Leclerc Drive", "Intermarche courses",
                                   "Monoprix courses", "Aldi caisse"]),
                       "ALIMENTATION", d))
    if dow in (1, 3, 5) and rng.random() < 0.22:
        rows.append(db(account_id, rng.uniform(12, 50),
                       rng.choice(["McDonalds Paris", "Big Fernand burgers",
                                   "Sushi Shop commande", "Pizza Hut livraison",
                                   "Uber Eats commande", "Just Eat livraison"]),
                       "ALIMENTATION", d))
    if dow == 4 and rng.random() < 0.08:
        rows.append(db(account_id, rng.uniform(20, 140), "SNCF billet TGV", "TRANSPORT", d))
    if rng.random() < 0.08:
        rows.append(db(account_id, rng.uniform(15, 180),
                       rng.choice(["Amazon.fr commande", "Zalando commande",
                                   "Cdiscount achat", "Fnac achat en ligne"]),
                       "AUTRE", d))
    if rng.random() < 0.06:
        rows.append(db(account_id, rng.uniform(8, 65), "Pharmacie ordonnance produits sante", "AUTRE", d))
    return rows


def gen_day_cadre(account_id, d, rng, cfg):
    rows = []
    salary, loyer = cfg["salary"], cfg["loyer"]
    dow = d.weekday()

    if d.day in (27, 28, 1, 2, 3) and rng.random() < 0.40:
        rows.append(cr(account_id, salary * rng.uniform(0.97, 1.03),
                       rng.choice(["Virement salaire net CAC40 SAS",
                                   "Paie mensuelle groupe Michelin",
                                   "Salaire LOreal Paris cadre",
                                   "Virement net Total Energies",
                                   "Paie mensuelle LVMH SAS"]),
                       "SALAIRE", d, True))
    if d.day == 1:
        rows.append(db(account_id, loyer, "Loyer mensuel appartement Paris", "LOYER", d, True))
        rows.append(db(account_id, rng.uniform(35, 90), "Fitness Park abonnement club", "ABONNEMENT", d, True))
    if d.day == 15:
        rows.append(db(account_id, rng.uniform(100, 200), "EDF Engie facture energie", "ENERGIE", d, True))
    if d.day == 10:
        rows.append(db(account_id, rng.uniform(80, 200), "AXA Assurance auto habitation", "ASSURANCE", d, True))
        rows.append(db(account_id, rng.uniform(60, 140), "Mutuelle sante Alan Harmonie", "ASSURANCE", d, True))
    if d.day == 5:
        for label, lo, hi in [("Netflix Premium", 8, 18), ("Spotify Family", 10, 16),
                               ("iCloud Plus 200Go", 3, 4), ("Amazon Prime", 6, 7),
                               ("Disney Plus", 9, 12)]:
            if rng.random() < 0.60:
                rows.append(db(account_id, rng.uniform(lo, hi), label, "ABONNEMENT", d, True))
        rows.append(db(account_id, rng.uniform(30, 60), "Orange Pro mobile", "TELECOM", d, True))
        rows.append(db(account_id, rng.uniform(38, 60), "Orange Livebox Pro internet", "TELECOM", d, True))
    if dow < 5 and rng.random() < 0.45:
        rows.append(db(account_id, rng.uniform(30, 140),
                       rng.choice(["Restaurant gastronomique Paris", "Uber Eats premium",
                                   "Deliveroo commande", "Brasserie le Loir"]),
                       "ALIMENTATION", d))
    if dow in (0, 3, 5) and rng.random() < 0.60:
        rows.append(db(account_id, rng.uniform(50, 220),
                       rng.choice(["Monoprix Bio Paris", "Grand Frais courses",
                                   "Whole Foods Market", "Carrefour City"]),
                       "ALIMENTATION", d))
    if dow < 5 and rng.random() < 0.30:
        rows.append(db(account_id, rng.uniform(10, 45),
                       rng.choice(["Uber course Paris", "Bolt taxi", "Navigo Mensuel RATP"]),
                       "TRANSPORT", d))
    if rng.random() < 0.15:
        rows.append(db(account_id, rng.uniform(30, 500),
                       rng.choice(["Apple Store achat", "Amazon Premium",
                                   "FNAC achat tech", "Galeries Lafayette"]),
                       "AUTRE", d))
    return rows


def gen_day_etudiant(account_id, d, rng, cfg):
    rows = []
    allocation, loyer, has_job = cfg["allocation"], cfg["loyer"], cfg["has_job"]
    dow = d.weekday()

    if d.day in (1, 2, 3, 4, 5) and rng.random() < 0.35:
        rows.append(cr(account_id, allocation * rng.uniform(0.95, 1.05),
                       "Virement famille parents mensuel", "VIREMENT", d, True))
    if d.day == 5:
        rows.append(cr(account_id, rng.uniform(60, 280),
                       "CAF Aide Personnalisee au Logement APL", "VIREMENT", d, True))
    if d.day == 25 and has_job and rng.random() < 0.75:
        rows.append(cr(account_id, rng.uniform(300, 650),
                       rng.choice(["Salaire CDD McDo etudiant",
                                   "Paie mi-temps Carrefour",
                                   "Virement salaire contrat alternance"]),
                       "SALAIRE", d))
    if d.day == 1:
        rows.append(db(account_id, loyer, "Loyer residence etudiante Crous", "LOYER", d, True))
    if d.day == 5:
        rows.append(db(account_id, rng.uniform(5, 15),
                       rng.choice(["Free Mobile 2euros", "Bouygues B and You", "Red by SFR 5G"]),
                       "TELECOM", d, True))
        if rng.random() < 0.50:
            rows.append(db(account_id, rng.uniform(5, 12),
                           rng.choice(["Spotify Student", "Netflix partage"]),
                           "ABONNEMENT", d, True))
    if rng.random() < 0.55:
        rows.append(db(account_id, rng.uniform(4, 30),
                       rng.choice(["Kebab du quartier", "Brioche Doree",
                                   "McDonalds campus", "Paul boulangerie",
                                   "RU Resto U CROUS", "Franprix vite fait",
                                   "Aldi commission", "Lidl caisse proche"]),
                       "ALIMENTATION", d))
    if dow == 4 and rng.random() < 0.04:
        rows.append(db(account_id, rng.uniform(25, 120), "SNCF billet retour famille", "TRANSPORT", d))
    return rows


def gen_day_retraite(account_id, d, rng, cfg):
    rows = []
    pension_cnav, pension_comp, loyer = cfg["pension_cnav"], cfg["pension_comp"], cfg["loyer"]
    dow = d.weekday()

    if d.day == 9:
        rows.append(cr(account_id, pension_cnav * rng.uniform(0.99, 1.01),
                       "Pension retraite mensuelle CARSAT CNAV", "SALAIRE", d, True))
    if d.day == 10:
        rows.append(cr(account_id, pension_comp * rng.uniform(0.99, 1.01),
                       "Rente AGIRC-ARRCO retraite complementaire", "SALAIRE", d, True))
    if d.day == 1:
        rows.append(db(account_id, loyer, "Loyer charges copropriete", "LOYER", d, True))
    if d.day == 15:
        rows.append(db(account_id, rng.uniform(55, 110), "EDF facture electricite", "ENERGIE", d, True))
    if d.day == 5:
        rows.append(db(account_id, rng.uniform(80, 170),
                       rng.choice(["Mutuelle Harmonie Senior", "MNT Mutuelle",
                                   "Malakoff Humanis retraite"]),
                       "ASSURANCE", d, True))
        rows.append(db(account_id, rng.uniform(20, 35),
                       rng.choice(["SFR Senior Mobile", "Orange Senior Mobile",
                                   "Bouygues Telecom senior"]),
                       "TELECOM", d, True))
    if rng.random() < 0.20:
        rows.append(db(account_id, rng.uniform(12, 80),
                       rng.choice(["Pharmacie ordonnance traitements",
                                   "Docteur consultation remboursable",
                                   "Opticien Alain Afflelou lunettes"]),
                       "AUTRE", d))
    if dow in (1, 3, 5) and rng.random() < 0.65:
        rows.append(db(account_id, rng.uniform(30, 120),
                       rng.choice(["Marche local commercants", "Leclerc Drive",
                                   "Carrefour Market", "Casino Supermarche"]),
                       "ALIMENTATION", d))
    if dow == 3 and rng.random() < 0.15:
        rows.append(db(account_id, rng.uniform(10, 80),
                       rng.choice(["Cinema UGC", "Musee Louvre entree",
                                   "Voyage car club retraite",
                                   "Librairie Gibert achat livres"]),
                       "AUTRE", d))
    return rows


def gen_day_famille(account_id, d, rng, cfg):
    rows = []
    salary1, salary2, loyer, garderie = (
        cfg["salary1"], cfg["salary2"], cfg["loyer"], cfg["garderie"])
    dow = d.weekday()

    if d.day in (27, 28, 29) and rng.random() < 0.45:
        rows.append(cr(account_id, salary1 * rng.uniform(0.97, 1.03),
                       "Salaire mensuel net conjoint 1", "SALAIRE", d, True))
    if d.day in (2, 3, 4) and rng.random() < 0.45:
        rows.append(cr(account_id, salary2 * rng.uniform(0.97, 1.03),
                       "Salaire mensuel net conjoint 2", "SALAIRE", d, True))
    if d.day == 5:
        rows.append(cr(account_id, rng.uniform(300, 820),
                       "CAF Allocations familiales mensuel", "VIREMENT", d, True))
    if d.day == 1:
        rows.append(db(account_id, loyer,
                       rng.choice(["Pret immobilier LCL remboursement",
                                   "Credit Agricole mensualite immo",
                                   "BNP Paribas credit habitat"]),
                       "LOYER", d, True))
        rows.append(db(account_id, garderie, "Creche Garderie mensualite enfant", "TRANSPORT", d, True))
    if d.day == 15:
        rows.append(db(account_id, rng.uniform(110, 260), "EDF et Gaz de France mensuel", "ENERGIE", d, True))
    if d.day == 10:
        rows.append(db(account_id, rng.uniform(120, 300), "MACIF assurance auto habitation", "ASSURANCE", d, True))
        rows.append(db(account_id, rng.uniform(100, 210), "Mutuelle sante famille Alan collective", "ASSURANCE", d, True))
        rows.append(db(account_id, rng.uniform(80, 310),
                       rng.choice(["Club football enfant cotisation",
                                   "Danse classique cours mensuel",
                                   "Piscine municipale abonnement famille"]),
                       "AUTRE", d, True))
    if d.day == 5:
        rows.append(db(account_id, rng.uniform(30, 55), "SFR Orange abonnement mobile x2", "TELECOM", d, True))
        rows.append(db(account_id, rng.uniform(28, 45), "Freebox Revolution internet fixe", "TELECOM", d, True))
        rows.append(db(account_id, rng.uniform(8, 22), "Netflix famille abonnement", "ABONNEMENT", d, True))
    if dow in (0, 2, 4, 5) and rng.random() < 0.65:
        rows.append(db(account_id, rng.uniform(60, 250),
                       rng.choice(["Carrefour hypermarche courses famille",
                                   "Leclerc Drive course hebdomadaire",
                                   "Intermarche grosses courses",
                                   "Biocoop produits bio famille"]),
                       "ALIMENTATION", d))
    if dow == 6 and rng.random() < 0.30:
        rows.append(db(account_id, rng.uniform(45, 160),
                       rng.choice(["Buffalo Grill repas famille",
                                   "Hippopotamus restaurant",
                                   "Pizza Hut livraison famille"]),
                       "ALIMENTATION", d))
    if dow in (0, 4) and rng.random() < 0.25:
        rows.append(db(account_id, rng.uniform(15, 60),
                       rng.choice(["Essence Total station", "SNCF billet",
                                   "Uber transport ville"]),
                       "TRANSPORT", d))
    return rows


PROFILE_GEN = {
    "B2C_SALARIE":  gen_day_salarie,
    "B2C_CADRE":    gen_day_cadre,
    "B2C_ETUDIANT": gen_day_etudiant,
    "B2C_RETRAITE": gen_day_retraite,
    "B2C_FAMILLE":  gen_day_famille,
}

TX_FIELDS = [
    "id", "account_id", "amount", "type", "label",
    "category", "date", "is_recurring", "external_transaction_id",
]


def copy_to_pg(csv_bytes: bytes, copy_sql: str) -> str:
    proc = subprocess.Popen(
        ["docker", "exec", "-i", "flowguard-db",
         "psql", "-U", "flowguard", "-d", "flowguard", "-c", copy_sql],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    out, err = proc.communicate(csv_bytes)
    err_s = err.decode().strip()
    if err_s and "NOTICE" not in err_s:
        print(f"  PG ERR: {err_s[:300]}", file=sys.stderr)
    return out.decode().strip()


def rows_to_csv(headers: list, rows: list) -> bytes:
    buf = io.StringIO()
    w = csv.writer(buf)
    w.writerow(headers)
    w.writerows(rows)
    return buf.getvalue().encode("utf-8")


def pg_query(sql: str) -> str:
    proc = subprocess.run(
        ["docker", "exec", "flowguard-db",
         "psql", "-U", "flowguard", "-d", "flowguard", "-At", "-c", sql],
        capture_output=True,
    )
    return proc.stdout.decode().strip()


def main():
    today = date.today()
    print(f"[{today}] FlowGuard B2C daily refresh starting...")

    # Retrieve all B2C users with their account ids and user_type
    rows_raw = pg_query(
        "SELECT u.id, u.user_type, a.id "
        "FROM users u "
        "JOIN accounts a ON a.user_id = u.id "
        "WHERE u.user_type LIKE 'B2C_%' "
        "ORDER BY u.id;"
    )

    if not rows_raw:
        print("No B2C users found. Exiting.")
        return

    users = []
    for line in rows_raw.splitlines():
        parts = line.strip().split("|")
        if len(parts) == 3:
            users.append((parts[0].strip(), parts[1].strip(), parts[2].strip()))

    print(f"  Found {len(users):,} B2C accounts to refresh for {today}")

    tx_rows = []
    for (user_id, profile, account_id) in users:
        # Reconstruct the same config deterministically
        seed = int(uuid.UUID(user_id)) & 0xFFFFFFFF
        cfg_rng = random.Random(seed)
        cfg = build_profile_config(profile, cfg_rng)

        gen_fn = PROFILE_GEN.get(profile)
        if gen_fn is None:
            continue

        # Day-specific seed: same formula as server_gen_b2c.py
        day_seed = (seed ^ (today.toordinal() * 6364136223846793005)) & 0xFFFFFFFFFFFFFFFF
        day_rng  = random.Random(day_seed)
        tx_rows.extend(gen_fn(account_id, today, day_rng, cfg))

    if not tx_rows:
        print("  No transactions generated (weekend / profile rules).")
        return

    result = copy_to_pg(
        rows_to_csv(TX_FIELDS, tx_rows),
        "COPY transactions(id,account_id,amount,type,label,category,"
        "date,is_recurring,external_transaction_id) "
        "FROM STDIN WITH (FORMAT CSV, HEADER TRUE)",
    )
    print(f"  Loaded {len(tx_rows):,} transactions for {today}: {result}")
    print(f"[{today}] Done.")


if __name__ == "__main__":
    main()
