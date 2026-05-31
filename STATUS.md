# STATUS — bankops-portal

## Current phase
Trifecta Steps 1–3 + Step A (e2e) complete. Step 4 (UI redesign) in progress.

- **Step 1 (fluxa fraud-grpc)** — shipped 2026-05-30, commit `d71210f` (fluxa repo, local-only). Service live on `:9095`, rules-only eval, k6 p99 ~26ms.
- **Step 2 (bankops ↔ fluxa gRPC)** — code-complete 2026-05-27. Full gRPC integration with FAIL_OPEN/FAIL_CLOSED policy.
- **Step 3 (HELD badge + release/reject UI)** — done 2026-05-28.
- **Step A (e2e verification)** — CLOSED 2026-05-30. Four bankops bugs fixed. Full chain proven: POST → 202/HELD → EvaluateTransaction RPC → P1 SupportCase.
- **Step 4 (UI redesign)** — IN PROGRESS 2026-05-30. Dark sidebar + Dashboard + Fraud Review Center built. See "In progress" for what's left.

## Done
- Full `/understand` analysis run (2026-05-26, commit `9a461977`)
- **Trifecta Step 2 — Fluxa fraud-eval gate (2026-05-27)**
  - Vendored proto, `FluxaFraudClient`, sealed `FluxaEvalOutcome` (6 variants), HELD status, P1 SupportCase auto-creation.
  - 12 Fluxa-related tests green; pre-existing propagation + test-exclusion fixes applied.
- **Trifecta Step 3 — HELD badge + release/reject (2026-05-28)**
  - `RELEASED`/`REJECTED` statuses, release/reject endpoints, Angular amber/rose badges, inline action buttons.
- **Trifecta Step A — e2e verification (2026-05-30)**
  - Bug 1: Missing `-Dspring-boot.run.profiles=local` → `enabled=false`.
  - Bug 2: 80ms Netty cold-start → DEADLINE_EXCEEDED. Fix: PT0.08S → PT0.3S base / PT2S local.
  - Bug 3: `merchant=""` → Fluxa INVALID_ARGUMENT. Fix: `""` → `"UNSPECIFIED"` in `TransactionService.java` (deposit line 373, withdrawal line 108).
  - Bug 4: P1 SLA upgrade no-oped (`slaDueAt != null` guard). Fix: `reloaded.setSlaDueAt(null)` before P1 call in `CaseService.createForFraud`.
  - Final proof correlationId: `1d00449f-5517-44aa-9a4c-abeda2924085` — rpc ok, 26ms, fluxa confirmed.
- **Step 4 partial — Dark sidebar + Dashboard + Fraud Review Center (2026-05-30)**
  - Dark chrome (`#0F172A`) sidebar + brand bar; `$chrome-*` tokens in `_variables.scss`.
  - New nav: Dashboard (home), **Fraud Review** (amber `gpp_bad`), OPERATIONS divider, Customers, Cases, Incident Console.
  - New backend: `GET /api/transactions?status=HELD` — `TransactionSearchController` + `TransactionService.getTransactionsByStatus` + `TransactionRepository.findAllByStatusOrderByCreatedAtDesc`.
  - New Angular: `CaseService.getKpis()`, `TransactionService.getHeldTransactions()`, `CaseKpi` model.
  - New screen `/dashboard`: 4 KPI cards + 2-column feed (recent fraud holds + case queue). Default route changed.
  - New screen `/fraud-review`: HELD table, per-row release/reject, batch select/action, spinner states, empty state.
  - Angular build: 0 hard errors; CSS budget raised to 10kb in `angular.json`.

## In progress
- **Step 4 remaining screens** (~11 of 15+ still to build):
  - Auth / login screen (no route exists yet)
  - Redesign existing screens to match new dark chrome: Customers, Account Detail, Cases, Incident Console, Audit Trail
  - New screens: Reports & Analytics, Admin settings (fraud rules, SLA config, agent management), Notifications rail

## Next
- Pick up Step 4 from existing screens redesign (Customers list is the logical next after Dashboard).
- Add `CreateTransactionRequest.merchant` field so Fluxa's `blocked_merchant` rule can fire.
- Fix pre-existing broken test files (`SlaServiceTest`, `AssignmentServiceTest`, `TimelineServiceTest` excluded via pom).
- **Commit + push** all bankops changes — Steps 2–4 partial are all uncommitted.

## Open decisions
- Should shadow-mode swallow `InvalidArgument` (current) or surface 400 in observer mode?
- Final typography + density decisions for redesigned screens.

## Reference
- **Restart sequence (H2 wipes on restart — always re-seed after):**
  ```bash
  # Kill running: find PIDs via `ps aux | grep BankOps`
  cd bankops-portal && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
    mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
  # Re-seed after startup:
  curl -sX POST localhost:8080/api/customers -u support:password \
    -H 'Content-Type: application/json' \
    -d '{"firstName":"Alice","lastName":"Smith","email":"alice@example.com","phone":"555-0100"}'
  curl -sX POST localhost:8080/api/customers/1/accounts -u support:password \
    -H 'Content-Type: application/json' -d '{"type":"CHEQUING"}'
  curl -sX POST localhost:8080/api/accounts/1/transactions -u support:password \
    -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-held-1' \
    -d '{"type":"DEPOSIT","amount":99999,"description":"luxury car"}'
  ```
- Backend: `localhost:8080/api` (requires `-Dspring-boot.run.profiles=local`)
- Frontend: `localhost:4200` (ng serve already running)
- Tests: `cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test`
- Fluxa stack: `cd ../fluxa && make up` (fraud-grpc :9095, `amount_threshold=500.00`)
- Roles: ADMIN (full), SUPPORT (ops+audit), USER (read-only)
