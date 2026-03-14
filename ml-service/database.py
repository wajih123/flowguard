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
