# BankOps Portal

**Production-style internal banking operations portal — Java Spring Boot + Angular — with role-based access control, end-to-end correlation-ID tracing, incident/case workflows, and a real-time gRPC fraud-evaluation gate on the transaction hot path.**

## Overview

BankOps Portal is an internal tool for bank operations staff to manage customers, accounts, and transactions, investigate incidents, and review fraud holds. Every transaction is evaluated in-line by a fraud service before it settles; flagged transactions are held and routed to a prioritized case queue.

- **Operations** — manage Customers, Accounts, Transactions, and Support Cases.
- **Fraud gate** — each transaction is scored synchronously over gRPC; a flagged transaction is placed on `HELD` and never touches the balance until an operator releases or rejects it.
- **Case management** — a flagged transaction auto-creates a P1 (high-priority) support case carrying the fraud reason and a 24h SLA.
- **Audit timeline** — immutable, RBAC-protected trail of state changes with JSON snapshots and a diff view.
- **Traceability** — an end-to-end `correlationId` on every transaction, fraud evaluation, and log event.
- **Security** — RBAC with ADMIN / SUPPORT / USER roles, per-user authentication, and guarded routes on the SPA.

## Fraud detection (Fluxa integration)

The portal integrates with **Fluxa**, a companion gRPC fraud-evaluation service (run separately), on the transaction hot path:

```
POST /api/accounts/{id}/transactions
  → TransactionService
      → Fluxa EvaluateTransaction (gRPC, once per logical transaction)
          ├─ FLAG  → transaction saved as HELD, balance untouched, P1 SupportCase created
          └─ ALLOW → normal deposit / withdrawal flow
```

- **Rules + ML** — Fluxa blends a rules engine (amount threshold, velocity, **blocked merchant**, high-risk currency) with an advisory ML score. The portal forwards a `merchant` on each request so the blocked-merchant rule can fire.
- **Directional failure policy** — configurable per environment; e.g. fail-open on deposits, fail-closed on withdrawals in `prod`.
- **HELD → RELEASED / REJECTED** — operators action holds from the Fraud Review screen; every transition is audited.
- **Exactly-once evaluation** — the fraud call is lifted above optimistic-lock retries and idempotency replays, so it fires once per logical transaction.

## Modules
1. **Dashboard** — KPI cards plus a live feed of recent fraud holds and the case queue.
2. **Fraud Review** — held-transaction queue with per-row and batch release/reject.
3. **Customers / Accounts** — profiles and Chequing/Savings management with business rules.
4. **Transactions** — deposits/withdrawals with overdraft protection, idempotency keys, and the fraud gate.
5. **Cases / Incident Console** — support-case management linked to transaction logs and correlation IDs.
6. **Audit Timeline** — operational audit trail for debugging and compliance.

## Tech Stack
- **Backend** — Java 17, Spring Boot 3.2 (Web, Data JPA, Security), gRPC, H2 (local), JUnit 5 / Mockito.
- **Frontend** — Angular 17, Angular Material, TypeScript, Jasmine/Karma.
- **Infrastructure** — Azure App Service, Azure SQL, Azure DevOps Pipelines.

## Requirements
- JDK 17 (the Maven build targets Java 17 bytecode; JDK 17–21 to run the build) and Maven 3.9+.
- Node.js 22 LTS + npm (for the Angular workspace).
- For the fraud gate: the Fluxa service reachable on `localhost:9095` (optional locally — see note below).

## Getting Started

### Backend
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```
- API base: `http://localhost:8080/api` (the servlet context path is `/api`).
- Health: `/api/health` · Identity: `/api/whoami` · H2 console: `/api/h2-console` (JDBC `jdbc:h2:mem:testdb`).
- The `local` profile seeds a demo customer + account (`id=1`) on an empty database.
- **Login credentials** (all use password `password`):
  - `admin` — ADMIN + SUPPORT + USER
  - `support` — SUPPORT + USER
  - `user` — USER

> The fraud gate is enabled in the `local` profile and expects Fluxa on `localhost:9095`. If Fluxa is unavailable, deposits fail open so the portal stays usable; withdrawals follow the configured failure policy.

### Frontend
```bash
cd frontend
npm install && npm start    # http://localhost:4200
```

### Testing
- Backend: `cd backend && mvn test`
- Frontend: `cd frontend && npm test`

See **[docs/TESTING_GUIDE.md](docs/TESTING_GUIDE.md)** for detailed steps and curl scripts.

## CI/CD
Automated Azure DevOps pipelines build, test, and deploy the backend and frontend artifacts to Azure App Services. See `azure-pipelines/` for the definitions.

## Documentation
- **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** — system design and data flow.
- **[docs/RUNBOOK.md](docs/RUNBOOK.md)** — operational runbook.
- **[docs/PROJECT_NOTES.md](docs/PROJECT_NOTES.md)** — fixes, review notes, and future improvements.
- **[docs/TESTING_GUIDE.md](docs/TESTING_GUIDE.md)** — testing guide and known issues.
- **[docs/DEMO_SCRIPT.md](docs/DEMO_SCRIPT.md)** — demo walkthrough.

## License
See [LICENSE](LICENSE).
