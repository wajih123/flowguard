# FlowGuard — Quick Start Guide

## Architecture

```
flowguard/
├── mobile/          → React Native 0.76.x (Expo SDK 53, bare workflow)
├── backend/         → Quarkus 3.27 LTS (Java 21, virtual threads)
├── ml-service/      → FastAPI 0.115.x (Python 3.12, LSTM predictor)
├── docker-compose.yml
└── flowguard.code-workspace
```

## Prerequisites

| Tool              | Version   |
|-------------------|-----------|
| Node.js           | ≥ 20 LTS  |
| Java JDK          | 21        |
| Maven             | ≥ 3.9     |
| Python            | 3.12      |
| Docker & Compose  | Latest    |
| Android Studio    | Latest    |
| Xcode (macOS)     | ≥ 15      |

## 1. Clone & Open Workspace

```bash
# Open the multi-root workspace in VS Code
code flowguard.code-workspace
```

## 2. Environment Setup

```bash
cp .env.example .env
# Edit .env with your real credentials for Swan, Nordigen, etc.
```

## 3. Start Infrastructure (PostgreSQL + Redis)

```bash
docker compose up -d postgres redis adminer
```

- **PostgreSQL**: `localhost:5432` (user: flowguard, pass: flowguard)
- **Redis**: `localhost:6379`
- **Adminer**: [http://localhost:8082](http://localhost:8082)

## 4. Start Backend (Quarkus)

```bash
cd backend

# Generate JWT keys (required for authentication)
openssl genrsa -out src/main/resources/privateKey.pem 2048
openssl rsa -in src/main/resources/privateKey.pem -pubout -out src/main/resources/publicKey.pem

# Run in dev mode
./mvnw quarkus:dev
```

- **API**: [http://localhost:8080/api](http://localhost:8080/api)
- **Swagger UI**: [http://localhost:8080/api/swagger-ui](http://localhost:8080/api/swagger-ui)
- **Health**: [http://localhost:8080/api/health](http://localhost:8080/api/health)
- **Metrics**: [http://localhost:8080/api/metrics](http://localhost:8080/api/metrics)

## 5. Start ML Service

```bash
cd ml-service

# Create virtual environment
python -m venv .venv
# Windows
.venv\Scripts\activate
# macOS/Linux
source .venv/bin/activate

pip install -r requirements.txt

uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

- **ML API**: [http://localhost:8000](http://localhost:8000)
- **Health**: [http://localhost:8000/health](http://localhost:8000/health)
- **Docs**: [http://localhost:8000/docs](http://localhost:8000/docs)

## 6. Start Mobile App

```bash
cd mobile

npm install

# Android
npx react-native run-android

# iOS (macOS only)
cd ios && pod install && cd ..
npx react-native run-ios
```

## 7. Run Everything with Docker

```bash
# From project root — starts all 5 services
docker compose up --build
```

## 8. Run Backend Tests

```bash
cd backend
./mvnw test
```

## VS Code Tasks (Ctrl+Shift+P → Tasks: Run Task)

| Task                | Action                          |
|---------------------|---------------------------------|
| Docker Up           | `docker compose up -d`          |
| Quarkus Dev         | Backend in dev mode             |
| ML Service          | Python ML in dev mode           |
| React Native Start  | Metro bundler                   |
| Start FlowGuard     | Compound: all services          |
| Type Check          | `tsc --noEmit` on mobile        |
| Lint                | ESLint on mobile                |

## VS Code Debug Configurations (F5)

| Config                  | Target                     |
|-------------------------|----------------------------|
| React Native Android    | Attach to RN Android       |
| React Native iOS        | Attach to RN iOS           |
| Quarkus Attach          | Remote debug on port 5005  |
| ML Service Debug        | debugpy on port 5678       |

## Project Structure Summary

### Mobile (React Native)
- **src/theme/**: Colors, typography, spacing (dark fintech palette)
- **src/domain/**: TypeScript interfaces (Account, Transaction, Alert, etc.)
- **src/native/**: TurboModule specs + JS wrappers (SecureStorage, Biometric, CertPinning)
- **src/api/**: Typed API clients (FlowGuard, Nordigen, Swan)
- **src/store/**: Zustand stores (auth, account, alert)
- **src/hooks/**: React Query hooks (forecast, alerts, spending, flash-credit, scenario)
- **src/navigation/**: React Navigation (Root, Auth, MainTabs)
- **src/components/**: Shared UI (Button, Card, Loader, Input, ErrorScreen, EmptyState, Skeleton)
- **src/screens/**: All app screens (Dashboard, Forecast, Alerts, Spending, Scenario, FlashCredit, BankConnect, Auth, KYC)

### Backend (Quarkus)
- **domain/**: JPA entities (User, Account, Alert, FlashCredit)
- **dto/**: Java records for request/response
- **repository/**: Panache repositories
- **service/**: Business logic (Auth, Treasury, Alert, FlashCredit, Scenario)
- **resource/**: REST endpoints with `@RunOnVirtualThread`
- **websocket/**: Real-time alert delivery
- **security/**: Request logging, global + validation exception mappers

### ML Service (FastAPI)
- **predictor.py**: LSTM model + TreasuryPredictor class
- **main.py**: FastAPI app with /predict, /scenario, /health endpoints

## Key Design Decisions

- **New Architecture**: TurboModules for native modules (not legacy bridge)
- **Virtual Threads**: Every Quarkus resource method uses `@RunOnVirtualThread`
- **jakarta.\***: Never javax.* — Quarkus 3.x only
- **French UI**: All user-facing text is in French
- **Dark Theme**: Fintech-style dark palette (#0B1437 background)
- **4pt Grid**: All spacing follows 4-point grid system
- **Biometric Gate**: Flash credit requires biometric authentication
- **Zod Validation**: All forms validated with Zod schemas
