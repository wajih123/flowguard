from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker
import os

DATABASE_URL = os.getenv(
    "DATABASE_URL",
    "postgresql+psycopg2://flowguard:flowguard_secret@localhost:5432/flowguard"
)

engine = create_engine(DATABASE_URL, pool_size=5, max_overflow=10)
SessionLocal = sessionmaker(bind=engine)

def get_training_data(min_days: int = 90) -> list[dict]:
    """
    Fetch aggregated transaction data for ML training.
    Returns list of {account_id, series: [{date, balance}]}
    Only accounts with min_days of history.
    Uses the daily_balances materialized view (created by Flyway V3).
    """
    with SessionLocal() as session:
        result = session.execute(text("""
            SELECT
                account_id,
                json_agg(
                    json_build_object(
                        'date', date::text,
                        'balance', cumulative_balance
                    ) ORDER BY date
                ) AS series,
                COUNT(*) AS day_count
            FROM daily_balances
            GROUP BY account_id
            HAVING COUNT(*) >= :min_days
        """), {"min_days": min_days})

        rows = result.fetchall()
        return [
            {
                "account_id": str(row.account_id),
                "series":     row.series,
                "day_count":  row.day_count
            }
            for row in rows
        ]


def get_user_series(user_id: str, min_days: int = 7) -> list[dict]:
    """
    Fetch daily balance series for a user's primary account.
    Returns list of {"date": str, "balance": float} sorted by date.
    Returns empty list if the user has no accounts or insufficient data.
    """
    with SessionLocal() as session:
        result = session.execute(text("""
            SELECT
                db.date::text AS date,
                db.cumulative_balance AS balance
            FROM daily_balances db
            JOIN accounts a ON a.id = db.account_id
            WHERE a.user_id = CAST(:uid AS uuid)
              AND a.status = 'ACTIVE'
            ORDER BY db.date
        """), {"uid": user_id})

        rows = result.fetchall()
        return [{"date": str(row.date), "balance": float(row.balance)} for row in rows]
