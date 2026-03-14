import redis
import json
import os
from typing import Any

r = redis.Redis(
    host=os.getenv("REDIS_HOST", "localhost"),
    port=int(os.getenv("REDIS_PORT", "6379")),
    password=os.getenv("REDIS_PASSWORD", ""),
    decode_responses=True
)

def get_cached(key: str) -> Any | None:
    """Get JSON value from Redis. Returns None if missing or expired."""
    try:
        val = r.get(key)
        return json.loads(val) if val else None
    except Exception:
        return None

def set_cached(key: str, value: Any, ttl_seconds: int = 3600) -> None:
    """Store JSON value in Redis with TTL."""
    try:
        r.setex(key, ttl_seconds, json.dumps(value))
    except Exception:
        pass  # Cache failure is non-fatal

def delete_cached(key: str) -> None:
    try:
        r.delete(key)
    except Exception:
        pass
