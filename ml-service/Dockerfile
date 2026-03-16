# FlowGuard ML Service Dockerfile
# Python 3.11 — required by timesfm (>=3.10,<3.12)

FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

COPY requirements.txt ./
RUN pip install --upgrade pip && pip install -r requirements.txt

COPY . .

# Create non-root user (security hardening)
RUN adduser --disabled-password --gecos '' flowguard \
    && chown -R flowguard:flowguard /app

USER flowguard

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
