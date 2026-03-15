# FlowGuard — Credentials & Configuration Reference

> ⚠️ **SECURITY**: This file documents credential locations and formats.
> Never commit actual production secrets to git.
> Production secrets live **only** in `infra/.env.prod` (gitignored) and as GitHub repository secrets.

---

## Table of Contents

1. [Infrastructure Overview](#infrastructure-overview)
2. [Development Environment](#development-environment)
3. [Production Environment — Hetzner Server](#production-environment)
4. [Third-Party API Keys](#third-party-api-keys)
5. [JWT Keys](#jwt-authentication-keys)
6. [GitHub Secrets (CI/CD)](#github-secrets-cicd)
7. [Deployment Commands](#deployment-commands)

---

## Infrastructure Overview

| Component    | Dev (local)             | Production (Hetzner)                             |
| ------------ | ----------------------- | ------------------------------------------------ |
| Server       | localhost / WSL         | `157.180.43.233` (CAX11)                         |
| OS           | Windows 11 + WSL Ubuntu | Ubuntu (ARM64)                                   |
| Frontend URL | `http://localhost:3000` | `http://157.180.43.233` → `https://flowguard.fr` |
| Backend URL  | `http://localhost:8080` | `https://flowguard.fr/api/` (via nginx)          |
| ML Service   | `http://localhost:8000` | `http://ml-service:8000` (internal Docker)       |

---

## Development Environment

### PostgreSQL (Dev)

| Parameter      | Value                                                                                 |
| -------------- | ------------------------------------------------------------------------------------- |
| Host           | `localhost`                                                                           |
| Port           | `5433` (Docker avoids conflict with local PG)                                         |
| Database       | `flowguard_dev`                                                                       |
| Username       | `flowguard`                                                                           |
| Password       | `flowguard_dev_password`                                                              |
| JDBC URL       | `jdbc:postgresql://localhost:5433/flowguard_dev`                                      |
| SQLAlchemy URL | `postgresql+psycopg2://flowguard:flowguard_dev_password@localhost:5433/flowguard_dev` |
| Adminer        | `http://localhost:8082`                                                               |

### Redis (Dev)

| Parameter | Value                                         |
| --------- | --------------------------------------------- |
| Host      | `localhost`                                   |
| Port      | `6379`                                        |
| Password  | `flowguard_dev_redis`                         |
| URL       | `redis://:flowguard_dev_redis@localhost:6379` |

### Backend Application (Dev / WSL)

| Parameter | Value                                                   |
| --------- | ------------------------------------------------------- |
| Port      | `8080`                                                  |
| Health    | `http://localhost:8080/q/health`                        |
| Swagger   | `http://localhost:8080/q/swagger-ui`                    |
| Start cmd | `cd /home/user/flowguard/backend && ./mvnw quarkus:dev` |
| Profile   | `%dev` (uses H2 for tests, mock mailer)                 |

### ML Service (Dev)

| Parameter | Value                                                                                                                                                                                               |
| --------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Port      | `8000`                                                                                                                                                                                              |
| Health    | `http://localhost:8000/health`                                                                                                                                                                      |
| v2 Health | `http://localhost:8000/v2/health`                                                                                                                                                                   |
| Predict   | `POST http://localhost:8000/v2/predict`                                                                                                                                                             |
| Start cmd | `cd C:\Users\User\flowguard\ml-service && $env:DATABASE_URL="postgresql+psycopg2://flowguard:flowguard_dev_password@localhost:5433/flowguard_dev"; python -m uvicorn main:app --reload --port 8000` |

### Frontend (Dev)

| Parameter | Value                                                               |
| --------- | ------------------------------------------------------------------- |
| Port      | `3000`                                                              |
| Start cmd | `cd C:\Users\User\flowguard\web && npm run dev`                     |
| API proxy | `/api/*` → `http://172.19.175.210:8080` (WSL IP, update if changed) |

---

## Production Environment

### Server Access

| Parameter  | Value                              |
| ---------- | ---------------------------------- |
| Provider   | Hetzner Cloud                      |
| Server     | `ubuntu-4gb-hel1-1` (CAX11, ARM64) |
| Public IP  | `157.180.43.233`                   |
| IPv6       | `2a01:4f9:c012:de00::/64`          |
| SSH        | `ssh root@157.180.43.233`          |
| Deploy dir | `/opt/flowguard`                   |

### PostgreSQL (Production)

| Parameter              | Value                                                    |
| ---------------------- | -------------------------------------------------------- |
| Host (internal Docker) | `postgres:5432`                                          |
| Database               | `flowguard`                                              |
| Username               | `flowguard`                                              |
| Password               | `Fl0wGu4rd_DB_Pr0d#2026` ← **change after first deploy** |
| JDBC URL               | `jdbc:postgresql://postgres:5432/flowguard`              |

### Redis (Production)

| Parameter              | Value                                                  |
| ---------------------- | ------------------------------------------------------ |
| Host (internal Docker) | `redis:6379`                                           |
| Password               | `Fl0wGu4rd_R3d1s#2026` ← **change after first deploy** |
| URL                    | `redis://:Fl0wGu4rd_R3d1s#2026@redis:6379`             |

### Services (Production — all internal Docker network)

| Service    | Container name      | Internal URL               |
| ---------- | ------------------- | -------------------------- |
| Frontend   | `flowguard-web`     | `http://web:80`            |
| Backend    | `flowguard-backend` | `http://backend:8080`      |
| ML Service | `flowguard-ml`      | `http://ml-service:8000`   |
| PostgreSQL | `flowguard-db`      | `postgres:5432`            |
| Redis      | `flowguard-redis`   | `redis:6379`               |
| Nginx      | `flowguard-nginx`   | ports `80`, `443` (public) |

---

## Application Accounts

### Super Admin (back-office full access)

| Field    | Value                              |
| -------- | ---------------------------------- |
| URL      | `https://157-180-43-233.sslip.io/admin` |
| Email    | `superadmin@flowguard.io`          |
| Password | `Admin@FlowGuard2026!`             |
| Role     | `ROLE_SUPER_ADMIN`                 |

### Admin (back-office standard access)

| Field    | Value                              |
| -------- | ---------------------------------- |
| URL      | `https://157-180-43-233.sslip.io/admin` |
| Email    | `admin@dev.fr`                     |
| Password | `Test1234!`                        |
| Role     | `ROLE_ADMIN`                       |

### Admin pages

| Page                  | Route              | Access            |
| --------------------- | ------------------ | ----------------- |
| Dashboard             | `/admin/dashboard` | Admin + SuperAdmin |
| Utilisateurs          | `/admin/users`     | Admin + SuperAdmin |
| Flash Crédits         | `/admin/credits`   | Admin + SuperAdmin |
| Alertes               | `/admin/alerts`    | Admin + SuperAdmin |
| KPIs                  | `/admin/kpis`      | Admin + SuperAdmin |
| Intelligence Artificielle | `/admin/ml`    | Admin + SuperAdmin |
| Feature Flags         | `/admin/flags`     | SuperAdmin only   |
| Configuration         | `/admin/config`    | SuperAdmin only   |
| Admins                | `/admin/admins`    | SuperAdmin only   |
| Audit                 | `/admin/audit`     | SuperAdmin only   |

---

## Third-Party API Keys

### Bridge API (Banking Aggregation)

| Environment               | Client ID                                     | Client Secret                                                                     |
| ------------------------- | --------------------------------------------- | --------------------------------------------------------------------------------- |
| **Sandbox** (dev+staging) | `sandbox_id_8854ed19940c493faeec55080d2bd98b` | `sandbox_secret_RImj9XJpPjl4vQIEqcV1vjfAWyUlZOrAa035DdezpU75HAxdUHMCORMLRipmO0wS` |
| **Production**            | _get from Bridge dashboard_                   | _get from Bridge dashboard_                                                       |

Dashboard: https://dashboard.bridgeapi.io → Settings → API Keys → Production

Sandbox redirect URL: `http://localhost:3000/banking/callback`
Production redirect URL: `https://flowguard.fr/banking/callback`

Demo Bank login (sandbox): username = `success`, password = anything

### Bridge API Version

- Version header: `Bridge-Version: 2025-01-15`
- Base URL: `https://api.bridgeapi.io`

---

## JWT Authentication Keys

Keys are stored as PEM files inside the backend source:

| File                       | Path                                                           | Purpose            |
| -------------------------- | -------------------------------------------------------------- | ------------------ |
| Private key (sign tokens)  | `backend/src/main/resources/META-INF/resources/privateKey.pem` | RS256 signing      |
| Public key (verify tokens) | `backend/src/main/resources/META-INF/resources/publicKey.pem`  | RS256 verification |

Key type: **RSA 2048-bit**
Algorithm: **RS256**
Issuer: `https://flowguard.fr`

> ⚠️ **For production**: Generate fresh keys and inject via environment or Docker secrets.
>
> ```bash
> openssl genrsa -out privateKey.pem 2048
> openssl rsa -in privateKey.pem -pubout -out publicKey.pem
> ```
>
> Then mount them into the backend container instead of bundling in the image.

---

## GitHub Secrets (CI/CD)

Go to: GitHub → Repository → Settings → Secrets and variables → Actions

Add the following **Repository secrets**:

| Secret name            | Value                                           |
| ---------------------- | ----------------------------------------------- |
| `SSH_PRIVATE_KEY`      | Private SSH key for `root@157.180.43.233`       |
| `POSTGRES_PASSWORD`    | `Fl0wGu4rd_DB_Pr0d#2026`                        |
| `REDIS_PASSWORD`       | `Fl0wGu4rd_R3d1s#2026`                          |
| `BRIDGE_CLIENT_ID`     | Bridge sandbox or production client ID          |
| `BRIDGE_CLIENT_SECRET` | Bridge sandbox or production secret             |
| `BRIDGE_REDIRECT_URL`  | `https://flowguard.fr/banking/callback`         |
| `CORS_ORIGINS`         | `https://flowguard.fr,https://www.flowguard.fr` |

### Generate SSH key for CI/CD

```bash
ssh-keygen -t ed25519 -C "flowguard-cicd" -f ~/.ssh/flowguard_deploy -N ""
# Add public key to server:
ssh-copy-id -i ~/.ssh/flowguard_deploy.pub root@157.180.43.233
# Add private key content to GitHub secret SSH_PRIVATE_KEY:
cat ~/.ssh/flowguard_deploy
```

---

## Deployment Commands

### First deployment (one-time setup)

```bash
# 1. SSH into server
ssh root@157.180.43.233

# 2. Clone the repo (update REPO_URL first)
git clone https://github.com/YOUR_ORG/flowguard.git /opt/flowguard

# 3. Edit production env
nano /opt/flowguard/infra/.env.prod

# 4. Run setup
bash /opt/flowguard/infra/setup-server.sh
```

### Manual redeploy (from local machine)

```bash
bash infra/deploy.sh root@157.180.43.233
```

### Manual redeploy (on server)

```bash
cd /opt/flowguard && bash infra/deploy.sh
```

### Enable HTTPS (after DNS is set up)

```bash
ssh root@157.180.43.233
bash /opt/flowguard/infra/enable-https.sh flowguard.fr admin@flowguard.fr
```

### View logs

```bash
docker logs flowguard-backend -f --tail 100
docker logs flowguard-ml -f --tail 100
docker logs flowguard-nginx -f --tail 100
```

### Database access (production)

```bash
docker exec -it flowguard-db psql -U flowguard -d flowguard
```

### Scale / restart individual service

```bash
cd /opt/flowguard
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env.prod restart backend
```

---

## Docker Compose Environments

| File                            | Purpose                               |
| ------------------------------- | ------------------------------------- |
| `docker-compose.yml`            | Local dev (hot-reload, ports exposed) |
| `infra/docker-compose.prod.yml` | Production (Hetzner server)           |

---

## Port Reference

| Port | Service            | Exposed              |
| ---- | ------------------ | -------------------- |
| 80   | nginx HTTP         | Public               |
| 443  | nginx HTTPS        | Public               |
| 8080 | Quarkus backend    | Internal only (prod) |
| 8000 | FastAPI ML service | Internal only (prod) |
| 5432 | PostgreSQL         | Internal only (prod) |
| 6379 | Redis              | Internal only (prod) |
| 3000 | React web (dev)    | Dev only             |
| 5433 | PostgreSQL (dev)   | Dev only             |
| 8082 | Adminer (dev)      | Dev only             |
