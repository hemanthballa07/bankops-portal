# STATUS — bankops-portal

## Current phase
Trifecta Steps 1–3 + Step A (e2e) complete. Step 4 (UI redesign) in progress.

- **Step 1 (fluxa fraud-grpc)** — shipped 2026-05-30, commit `d71210f` (fluxa repo, local-only). Service live on `:9095`, rules-only eval, k6 p99 ~26ms.
- **Step 2 (bankops ↔ fluxa gRPC)** — code-complete 2026-05-27. Full gRPC integration with FAIL_OPEN/FAIL_CLOSED policy.
- **Step 3 (HELD badge + release/reject UI)** — done 2026-05-28.
- **Step A (e2e verification)** — CLOSED 2026-05-30. Four bankops bugs fixed. Full chain proven: POST → 202/HELD → EvaluateTransaction RPC → P1 SupportCase.
- **Step 4 (UI redesign)** — IN PROGRESS 2026-05-30. Dark sidebar + Dashboard + Fraud Review Center built. See "In progress" for what's left.
- **Trifecta console e2e (CORS + durable seed)** — CLOSED 2026-05-31. trifecta-console (`:3001`) → bankops verified in-browser end-to-end; commits `03749e3` (CORS), `46337d1` (seeder).

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
- **Trifecta console e2e — CORS + restart-durable seed (2026-05-31)**
  - CORS (commit `03749e3`): added `http://localhost:3001` (trifecta-console) to `CorsConfig` allowed origins; `LocalConsoleSecurityConfig` now calls `.cors(Customizer.withDefaults())` so the `CorsConfigurationSource` bean applies to its `@Order(1)` chain. Fixes browser-side 403 preflight + missing ACAO that made the console silently fall back to mock data and fire-and-forget releases.
  - Seed (commit `46337d1`): `LocalDataSeeder` — `@Profile("local")` `CommandLineRunner`, idempotent (`count()==0`), seeds Customer id=1 + Account id=1 (CHEQUING, balance 250000) on empty H2. Root-cause fix for account 1 vanishing on every restart (in-memory H2 + no data seeding).
  - Fluxa verified the full in-browser chain with `Origin :3001`: POST→202/HELD → gRPC FLAG 24.5ms → P1 case → console `GET ?status=HELD` (real data, no mock fallback) → SSE match → release preflight 200 → release POST 200/RELEASED → queue cleared. (Network-contract CORS leg proven; pixel React click-through deferred — Fluxa's Playwright MCP lacks a connected browser bridge.)

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
- **Restart sequence** — H2 wipes on restart, but `LocalDataSeeder` (`@Profile("local")`) now auto-seeds Customer id=1 + Account id=1 (CHEQUING, balance 250000) on an empty DB, so manual customer/account re-seed is **no longer needed**:
  ```bash
  # Kill running: lsof -tiTCP:8080 -sTCP:LISTEN | xargs kill   (or `ps aux | grep BankOps`)
  cd bankops-portal && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
    mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
  # On boot, log confirms: "Local seed created customer id=1 + account id=1 (balance 250000.00)"
  # Create a HELD demo txn (amount > 500 threshold) when needed:
  curl -sX POST localhost:8080/api/accounts/1/transactions -u support:password \
    -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-held-1' \
    -d '{"type":"DEPOSIT","amount":99999,"description":"luxury car"}'
  ```
- Backend: `localhost:8080/api` (requires `-Dspring-boot.run.profiles=local`)
- Frontend: `localhost:4200` (ng serve already running)
- Tests: `cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test`
- Fluxa stack: `cd ../fluxa && make up` (fraud-grpc :9095, `amount_threshold=500.00`)
- Roles: ADMIN (full), SUPPORT (ops+audit), USER (read-only)
