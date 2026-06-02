# STATUS — bankops-portal

## Current phase
Trifecta Steps 1–3 + Step A (e2e) complete. Step 5 (merchant field) + Step 6b (distributed tracing) DONE. **Trifecta 3rd leg (fluxguard rate limiting) — consumer COMPLETE 2026-06-02: TRANSACTION + OPS_RELEASE/OPS_REJECT + LOGIN, 186 backend tests green (on branch `feat/fluxguard-ratelimit`, unpushed).** Step 4 (UI redesign) in progress.

- **Step 1 (fluxa fraud-grpc)** — shipped 2026-05-30, commit `d71210f` (fluxa repo, local-only). Service live on `:9095`, rules-only eval, k6 p99 ~26ms.
- **Step 2 (bankops ↔ fluxa gRPC)** — code-complete 2026-05-27. Full gRPC integration with FAIL_OPEN/FAIL_CLOSED policy.
- **Step 3 (HELD badge + release/reject UI)** — done 2026-05-28.
- **Step A (e2e verification)** — CLOSED 2026-05-30. Four bankops bugs fixed. Full chain proven: POST → 202/HELD → EvaluateTransaction RPC → P1 SupportCase.
- **Step 4 (UI redesign)** — IN PROGRESS 2026-05-30. Dark sidebar + Dashboard + Fraud Review Center built. See "In progress" for what's left.
- **Trifecta console e2e (CORS + durable seed)** — CLOSED 2026-05-31. trifecta-console (`:3001`) → bankops verified in-browser end-to-end; commits `03749e3` (CORS), `46337d1` (seeder).
- **Step 5 (merchant field → Fluxa `blocked_merchant`)** — DONE 2026-06-01. `merchant` threaded DTO→service→gate (commits `b33268c`, `ae12c24`); live e2e proved `Amazon Marketplace` $42 → HELD/P1 while control `Joes Coffee` $42 → COMPLETED.
- **Step 6b (OpenTelemetry distributed tracing)** — DONE 2026-06-01 (commit `c076630`). Micrometer Tracing + OTel bridge + a `GrpcTelemetry` client interceptor propagate W3C trace context over the Fluxa gRPC hop. Live-proven: one trace `af3c89e6…` with **11 spans across `bankops-portal` + `fraud-grpc` + `ml-scorer`** (Java→Go→Python) in the shared Jaeger. Reported to Fluxa (trifecta msg 33).
- **Trifecta 3rd leg (fluxguard distributed rate limiter — consumer)** — COMPLETE 2026-06-02, branch `feat/fluxguard-ratelimit` (unpushed). Synchronous `CheckLimit` gRPC pre-check (`:9099`) ordered BEFORE the Fluxa fraud-eval, fail-open, `DENY`→429+`Retry-After`. **Phase 1**: `POLICY_TRANSACTION` (both deposit + withdrawal call sites, commit `5175180`) **+ `POLICY_OPS_RELEASE`/`POLICY_OPS_REJECT`** (HELD release/reject ops actions, this commit). **Phase 2**: `POLICY_LOGIN` `/whoami` brute-force throttle (commit `ce8d82f`). Shared-Jaeger via `GrpcTelemetry`. Proto frozen by fluxguard (trifecta msg 41), used verbatim. 186 backend tests green.

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
  - Fluxa verified the full in-browser chain with `Origin :3001`: POST→202/HELD → gRPC FLAG 24.5ms → P1 case → console `GET ?status=HELD` (real data, no mock fallback) → SSE match → release preflight 200 → release POST 200/RELEASED → queue cleared. (Network-contract CORS leg proven; pixel UI click-through skipped — a truthful real-data screenshot needs a standalone Playwright driver, and Fluxa's network-level proof was accepted. The MCP Playwright + headless-Chrome attempts only rendered the UI's built-in demo data.)
  - Endpoint 500-fixes (commit `9d16be1`): added `GET /accounts` (collection, `AccountService.getAllAccounts`) and `GET /accounts/{id}/transactions/{txId}` (single, `TransactionService.getTransaction`, reusing the release/reject account-match check). Both were 500 (no handler), now 200; verified live. Console uses neither.
- **Auth / login cluster (2026-05-31)**
  - Interceptor RBAC fix: `authInterceptor` now attaches the stored `AUTH_BASIC_V2` session credential (per-user Basic) instead of hardcoded `user:password`, so RBAC is real; it never clobbers a caller-set `Authorization` header.
  - `authGuard` (`CanActivateFn`) redirects unauthenticated users to `/login`; all ops routes guarded, `/login` route added.
  - Ops shell now renders only when authenticated (`showShell$` off the login route); avatar binds to `currentUser$.username`; logout button wired.
  - Dark-chrome login screen (`$chrome-*` palette: bg `#0F172A`, card `#1E293B`, accent `#3B82F6`); already-authenticated users redirect to `/dashboard`.
  - Startup hydration: `AuthService` resolves real `username`+`roles` from `/whoami` on reload (deferred microtask), so reloads no longer show "User"/empty roles.
  - 6 new unit specs (interceptor 4 + guard 2) pass headless; `ng build` clean. (Full `npm test` still blocked by the pre-existing `incident-console.component.spec.ts` compile error — out of scope.)
- **Cases screen redesign (2026-05-31)** — commit `a2fab75`
  - Replaced pre-redesign hardcoded `white` surfaces with semantic `$color-bg-primary` and applied the redesigned `.panel` convention (bordered, `$border-radius-xl`, no box-shadow) to the cases table, details drawer, note cards, and create-case form. Matches the Customers reference; `ng build` clean.
- **Frontend test suite unblocked + green (2026-05-31)** — commits `56c5961`, `6b9278b`
  - `incident-console.component.spec.ts` had a compile error (tested removed members `correlationId`/`searchIncident`/`incident`, never provided `ActivatedRoute`) that blocked Karma from running the ENTIRE suite (one type-check pass). Rewrote it against the current API (`filters.correlationId`, `searchIncidents()`, `incidentDetail`, `viewIncidentDetail`, `getStatusClass`) + provided `ActivatedRoute`/`NoopAnimations`.
  - `transaction-form.component.spec.ts` (7 tests) failed on `MatFormField`'s `@transitionMessages` animation — added `provideNoopAnimations()`.
  - `npm test` now runs **19/19 green** (was 0 executable). Only backend tests remain excluded (see Next).
- **Account Detail screen redesign (2026-06-01)** — commit `b843208`
  - The styles predated the redesign (no design-system import; raw px + hardcoded neutral hex). Imported `variables`+`mixins` and tokenized spacing/surfaces/neutral text (`#f5f5f5` → `$color-bg-secondary`; greys → `$color-text-secondary/tertiary`). Intentional Step-3 fraud-status badge colors (HELD amber / RELEASED / REJECTED) + held-row highlight preserved verbatim. `ng build` clean. (Conservative pass — Account Detail uses `mat-card`s, not custom panels, so no structural panel overhaul.)
- **Merchant field end-to-end → Fluxa `blocked_merchant` (2026-06-01)** — commits `b33268c` (backend), `ae12c24` (frontend)
  - Optional `merchant` on `CreateTransactionRequest`, threaded `DTO → TransactionService → EvaluateRequest.merchant` at **both** call sites (deposit + withdrawal), replacing the hardcoded `"UNSPECIFIED"`. New private `resolveMerchant()` helper + `FLUXA_DEFAULT_MERCHANT` constant: blank/omitted merchant → `"UNSPECIFIED"` fallback (never trips Fluxa's empty-merchant INVALID_ARGUMENT, the old Bug-3 path).
  - Optional Merchant input on the transaction form (`merchant?` added to the `CreateTransactionRequest` TS model).
  - Tests: **156 backend + 19 frontend green.** New `TransactionServiceTest` forwarding + UNSPECIFIED-fallback tests; new `FluxaFraudClientTest` regression test asserting `merchant` reaches `EvaluateRequest`.
  - **Live e2e** vs Fluxa `:9095` (fresh backend on `:8081`, real RPC): `Amazon Marketplace` $42 (under the 500 amount threshold) → **202 HELD** → P1/HIGH case summary `merchant="Amazon Marketplace" is blocked`; control `Joes Coffee` $42 → **201 COMPLETED**. Same amount, opposite outcome ⇒ merchant is the decider. corr `f32889d1-c8ab-46be-8492-fef55109f48d`. Reported to Fluxa (trifecta msg 31).
  - Fluxa blocklist (read from `../fluxa/rules.yaml`, **exact-match**): `Amazon Marketplace`, `Walmart Online`, `Target`.
- **OpenTelemetry distributed tracing — Step 6b (2026-06-01)** — commit `c076630`
  - Deps: `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` + `opentelemetry-grpc-1.6` (alpha BOM pinned `1.31.0-alpha` to match SB 3.2's OTel 1.31.0). HTTP server spans auto-instrumented; a `GrpcTelemetry` client interceptor on the Fluxa `ManagedChannel` (guarded by `ObjectProvider<OpenTelemetry>`) injects the W3C `traceparent` so Fluxa's otelgrpc server joins the trace.
  - Config: sampling `1.0` local / `0.0` default / **`management.tracing.enabled: false` in test** (hermetic — 157 tests green incl. a new `FluxaTracePropagationTest` asserting `traceparent` injection). OTLP/HTTP → shared Jaeger `:4318`. `traceId`/`spanId` added to log patterns.
  - **Live proof:** trace `af3c89e6a95628f4c4fdc731d0ce45ac` — 11 spans, services `bankops-portal` + `fraud-grpc` + `ml-scorer` in one trace (Java→Go→Python); deposit returned 201 (tracing fail-safe on the hot path). Reported to Fluxa (trifecta msg 33).
- **Incident Console + Audit Trail redesign — Step 4 (2026-06-01)** — commit `1dcf0dd`
  - **Incident Console**: imported shared `variables`+`mixins`; remapped the local `:host` "Chase-inspired" tokens to design-system values; gave the search/KPI/results cards the bordered-panel convention (`$border-radius-xl`, `$color-border`, no box-shadow); mapped log-level + status tints to `$color-error/warning/info-bg`; tokenized `white` surfaces. KPI icon gradients preserved (intentional accent visuals).
  - **Audit Trail (`audit-timeline`)**: converted plain `.css` → tokenized `.scss` (rail/dot → `$color-border`/`$chrome-accent`; **diff colors preserved** old=`$color-error` strikethrough / new=`$color-success`); repointed `styleUrl`; removed the old `.css`.
  - `ng build` clean; **19/19 specs green**. (Pre-existing 6kb component-style *warning* on incident-console unchanged in kind; well under the 10kb error cap.)
- **Reports & Analytics screen — Step 4 new screen (2026-06-01)** — commits `dd1c288` (backend), `3361862` (frontend)
  - New ops-wide read endpoint `GET /api/reports/summary` → `ReportSummaryDto` (`transactionsByStatus`/`casesByState`/`casesBySeverity` via `GROUP BY` count queries on `TransactionRepository`/`SupportCaseRepository`, **reusing** `CaseService.getKpis()`, + totals). `ReportsController`/`ReportsService` added; `SecurityConfig` gets `/reports/** → hasAnyRole(USER,SUPPORT)`.
  - New `/reports` route + OPERATIONS nav entry; `ReportsComponent` renders KPI cards + lightweight **CSS bar charts** (no charting dependency — matches the `mat-list`/KPI approach), tokenized; loading/empty states.
  - Built via the full loop (plan → critique×2 ⇄ patch → impl): critique caught a must-fix (test seed omitted required `customer`) + 4 should-fix (absolute `environment.apiUrl`, nav class, flaky `findAll()` ordering, missing `/reports` matcher).
  - **158 backend tests green** (new `ReportsIntegrationTest`) + `ng build` clean + 19/19 specs. **Live-proven**: seeded HELD+COMPLETED → `summary` returned `{COMPLETED:1, HELD:1}`, `casesBySeverity {HIGH:1}`, `casesByState {NEW:1}`, reused `caseKpis`, totals 2/1.
- **Admin · Agent Management — Step 4 new screen (2026-06-01)** — commits `ff4b774` (backend), `e2d639f` (frontend)
  - New **ADMIN-only** CRUD over the existing `Agent` model: `GET/POST/PUT /api/agents` + `PATCH /agents/{id}/active` (`AgentController`/`AgentService`, reusing `AgentRepository`/`AgentDto`); live per-agent `currentActiveCount` via `countByAssigneeIdAndStateIn(id, [NEW,IN_PROGRESS])`; `skills` JSON↔List via `ObjectMapper`; email uniqueness → 400. `SecurityConfig` gets `/agents/** → hasRole("ADMIN")`.
  - New `/admin` route + nav entry; `AdminAgentsComponent` — agent table (name/email/load bar/max/active toggle) + inline create form, tokenized.
  - Built via the full loop; critique-1 was **READY on iteration 1** (all load-bearing facts pre-verified: `IllegalArgumentException`→400, `AgentDto @Builder`, ADMIN role, `/agents` not claimed by the local console chain).
  - **163 backend tests green** (new `AgentManagementIntegrationTest`, 5 tests incl. USER→403) + `ng build` clean + 19/19 specs. **Live-proven** (admin): create→201, duplicate email→400, `support`→403, list shows the agent.
- **Frontend spec coverage — fraud-ops screens + service contracts (2026-06-01)** — commits `b7d50bb` (fraud-review + cases), `68a89f3` (dashboard), service specs pending commit
  - Added specs for the highest-traffic, previously-untested ops screens. `fraud-review.component.spec.ts` (20 tests): load success/error, `selectedRows`/`allSelected`/`someSelected` getters, `toggleAll`, single + batch release/reject incl. failure paths and the empty-selection no-op, `formatAmount`/`timeAgo`. `cases.component.spec.ts` (23 tests): init wiring, `enhanceCase` SLA-target + priority + customer-name mapping, `updateKPIs` aggregation, bulk selection, create/status/assign/timeline actions, and the note + resolve drawer flows (incl. blank/no-selection guards). `dashboard.component.spec.ts` (9 tests): `forkJoin` init loads KPIs + held feed + open-cases (`getCases('OPEN')`), each feed capped at 5, per-source `catchError` fallbacks (null kpis / empty feeds), `heldCount`/`formatAmount`/`timeAgo`.
  - **Service-layer contract specs** (`HttpTestingController`): `transaction.service.spec.ts` (8 tests — pins the fraud hot-path REST contract: create, `GET /transactions?status=HELD`, release/reject empty-body + forwarded-review-request, filter→query-param mapping, spending/monthly endpoints) + `case.service.spec.ts` (9 tests — create/get(+filters)/PATCH-status/PUT-assign/POST-note/link/PUT-resolve/GET-kpis, asserting method + URL + body for each).
  - **Login (auth) spec** — `login.component.spec.ts` (6 tests): authenticated-redirect-to-`/dashboard` on init, stay-on-login when not, `onSubmit` success (login with creds + navigate `/` + loading cleared) and failure (error message + loading cleared + no navigate), and the pre-submit error reset.
  - **Service contracts** (`HttpTestingController`): `customer.service.spec.ts` (4 — create/search(+query)/by-id), `reports.service.spec.ts` (1 — `GET /reports/summary`), `agent-admin.service.spec.ts` (4 — list/create/update/setActive `PATCH …/active`), `account.service.spec.ts` (4 — create/by-customer/by-id/PATCH). **6 of 7 services covered** (only `AuthService` remains — exercised indirectly via the interceptor/guard specs; its constructor has `sessionStorage`/`hydrate` side-effects).
  - **Leaf screens**: `customers.component.spec.ts` (7 — load/error, search, create reset/reload + error-keeps-form, toggle), `customer-detail.component.spec.ts` (8 — route-id load of customer+accounts, error logging, create-account guard/success/alert, `viewAccount` nav, toggle), `account-detail.component.spec.ts` (14 — route-id load + pagination mapping, `onFilterChange`/`onPageChange` (incl. the response-driven page/size re-sync), create-txn amount guard + success + correlation alert, `releaseHeld`/`rejectHeld` confirm-gating + reload + error-alert, `viewIncident` nav).
  - **transaction-filter** — `transaction-filter.component.spec.ts` (6 — `onFilterChange` emit mapping (page reset to 0), `Date`→`yyyy-MM-dd` formatting, falsy type/status/search→undefined, 300ms debounced search via `fakeAsync`/`tick`, `clearFilters` reset+emit).
  - **Final round — leaf screens + AuthService**: `spending-summary` (7 — ngOnChanges reload, accountId guard, total sum, error log, `formatCategory`, `getPercentage` /0 guard), `monthly-chart` (6 — same shape + `formatMonth`), `audit-timeline` (9 — route-param load, server/default error message, `onPageChange`, `getActionColor`, `parseJsonValue` JSON+fallback, `getChangedFields` key-union, `getFieldChange`), `case-timeline` (10 — constructor load via `MAT_DIALOG_DATA`, `loadMore` append, `filterByEventType` reset, error, `replayAt` snapshot, `closeReplay`, `toggleDetails`, badge map, relative time, `close`→dialogRef), `auth.service` (3 — unauthenticated state, `login` Basic-header + sessionStorage + user emit, response-omits-fields fallback). `page-header` skipped (pure `@Input`s, no logic); `AuthService.logout` not unit-tested (calls `window.location.reload`).
  - Test-only (no production code touched). **Suite 192/192 green** (was 34). **Frontend spec coverage is now comprehensive — every screen + all 7 services.** Commits: `b7d50bb` (fraud-review+cases), `68a89f3` (dashboard), `97ac12a` (tx+case services), `88fa021` (login), `6e774cd` (customer/reports/agent-admin services), `0258c48` (account service + customers/customer-detail/account-detail), `05fcd95` (transaction-filter), + this commit (spending-summary/monthly-chart/audit-timeline/case-timeline + AuthService). Learning captured: `CasesComponent` imports `MatDialogModule`, so its standalone injector resolves the **real** `MatDialog` — a root-level `useValue` spy is shadowed; spy on the component's injected instance (`component['dialog']`) instead, or the real dialog renders `CaseTimelineComponent` and fails on missing `HttpClient`.

- **Trifecta 3rd leg — fluxguard distributed rate limiter (consumer) (2026-06-02)** — branch `feat/fluxguard-ratelimit`
  - Service-layer design mirroring `FluxaFraudClient`: `FluxguardRateLimitClient` maps `CheckLimitResponse` → sealed `FluxguardRateLimitOutcome` (Allow/Denied/Unavailable/Disabled); `bankops.fluxguard.*` config (default off / local on `:9099`); `GrpcTelemetry` interceptor on the channel → shared Jaeger; `Denied`→`FluxguardRateLimitException`→429+`Retry-After` (`GlobalExceptionHandler`); fail-open on any transport error.
  - **Phase 1 — TRANSACTION** (commit `5175180`): `checkLimit` (POLICY_TRANSACTION) at BOTH fraud-eval call sites in `TransactionService` (withdrawal lifts it above the optimistic-retry; deposit inline), ordered BEFORE the Fluxa RPC so a throttled request never reaches fraud-eval.
  - **Phase 1 — OPS_RELEASE / OPS_REJECT** (this commit): `checkOpsRelease`/`checkOpsReject` (POLICY_OPS_RELEASE/REJECT, keyed by the acting principal via `resolveSubject()`) at the TOP of `releaseTransaction`/`rejectTransaction`, BEFORE any DB work — a throttled ops action returns 429 and never mutates state (status stays HELD, balance untouched). **Closes the verified gap** where the earlier handoff/memory claimed OPS_* was wired but it wasn't (only TRANSACTION + LOGIN were).
  - **Phase 2 — LOGIN** (commit `ce8d82f`): `/whoami` brute-force throttle — `WhoamiRateLimitFilter.checkLogin` + `LoginFailureAuthEntryPoint.reportLoginFailure` (wrong-creds-only), `client_ip` = socket peer (`getRemoteAddr()`, NOT `X-Forwarded-For`).
  - Proto frozen by fluxguard (trifecta msg 41), used verbatim — no contract change. **186 backend tests green** (new: 5 `FluxguardRateLimitClientTest` OPS cases asserting policy/subject/deny/fail-open/disabled + 2 `OpsRateLimitGateIntegrationTest` cases proving release/reject→429 with no state mutation). **Unpushed**: branch is ahead of `origin/main` by all 3 commits (`5175180`, `ce8d82f`, + this) — user pushes/merges (`git push` denied to Claude).

- **Notifications rail — Step-4 new screen (2026-06-02)** — branch `feat/notifications-rail`
  - New thin server-side aggregate `GET /api/notifications` → `NotificationsService` **derives a prioritized feed on-read** (no entity, self-clearing) from HELD transactions + active cases + `CaseService.getKpis()`. Categories `FRAUD_HOLD` (CRITICAL) / `CASE_UNASSIGNED` (WARNING) / `SLA_RISK` (BREACHED→CRITICAL, AT_RISK→WARNING) / `BACKLOG` (INFO footer). Per-section fail-soft (one bad query degrades only its section); **one item per case** (SLA risk beats unassigned-HIGH); 50-item cap with true `counts` before the cap. `NotificationsController` + `/notifications/**`→`hasAnyRole(USER,SUPPORT)`.
  - Frontend: `NotificationService` (30s **visibility-aware** poll via `timer(0,…)`+`refresh$`+`visibilitychange`, last-good on error, single initial fetch) + standalone `NotificationsRailComponent` (slide-out, severity-colored items, deep-link + close, backlog footer, empty/error states) + bell **badge = actionable (CRITICAL+WARNING)** wired in `app.component` (poll subscribed in `ngOnInit`).
  - Built via the full loop: brainstorm → spec → `writing-plans` → `critique-plan`×2 ⇄ `patch-plan` (converged READY: caught a non-deterministic polling test → `fakeAsync`, a phantom app-spec reference, and a double initial-fetch). **193 backend tests** (new `NotificationsServiceTest` 5 + `NotificationsIntegrationTest` 2) + **198 frontend specs** (new service 2 + component 4) green; `ng build` clean (only pre-existing budget/strictness warnings). **Unpushed** — user pushes/merges (`git push` denied to Claude).

## In progress
- **Step 4 — all existing screens on the dark-chrome design system + the net-new screens** (Reports & Analytics, Admin·Agent-Management, Notifications rail all done). Remaining Step-4 candidates are lower-priority/deferred:
  - SLA-config admin deferred (durations hardcoded in the `SlaPriority` enum — needs a config store + SLA-engine touch, see Open decisions); fraud-rules admin is cross-repo (Fluxa's `rules.yaml`).

## Next
- Step-4 net-new screens (Reports & Analytics, Admin·Agent-Management, Notifications rail) are all DONE. Remaining backlog is product-call work needing a brainstorm: **SLA-config admin** (deferred — durations hardcoded in `SlaPriority`; needs a config store + SLA-engine touch, full loop + care).
- Optional (Fluxa Step 5a, trifecta msg 29): surface the advisory `ml_score` as an "ML risk" chip on HELD txns/cases — needs re-vendoring the proto + regenerating stubs first. See Open decisions.
- Notifications rail v2 (deferred): system-incident (ERROR-log) alert category — distinct system-health axis from the work-queue; data not cleanly queryable yet.

## Open decisions
- Should shadow-mode swallow `InvalidArgument` (current) or surface 400 in observer mode?
- Final typography + density decisions for redesigned screens.
- Surface Fluxa's advisory `ml_score` (Step 5a, trifecta msg 29) as a UI chip? Low-signal for our feature-poor `EvaluateRequest`; Fluxa says advisory-only, don't gate on it. Cosmetic, deferred.

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
- Tests: `cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test` — fully green as of 2026-06-01 (156 tests, incl. `SlaServiceTest`/`AssignmentServiceTest`/`TimelineServiceTest`; the old "pom-excluded / broken" note for those three is stale).
- Fluxa stack: `cd ../fluxa && make up` (fraud-grpc :9095, `amount_threshold=500.00`; shared **Jaeger** UI :16686 + OTLP :4318/:4317). bankops exports traces to Jaeger :4318 when run with the `local` profile; view a trace at `http://localhost:16686/trace/<traceID>`.
- Roles: ADMIN (full), SUPPORT (ops+audit), USER (read-only)
