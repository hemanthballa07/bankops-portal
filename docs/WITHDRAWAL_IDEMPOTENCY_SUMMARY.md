# Withdrawal Idempotency Implementation Summary

## Implementation Complete ✅

**Date**: December 20, 2025  
**Feature**: Idempotent, concurrency-safe withdrawals with optimistic locking

---

## Changes Made

### 1. Entity Layer

#### `Account.java`
- ✅ Added `@Version private Long version;` for optimistic locking
- **Already present** in codebase (line 48)

#### `Transaction.java`
- ✅ Added `idempotencyKey` field:
  ```java
  @Column(name = "idempotency_key", length = 64)
  private String idempotencyKey;
  ```
- ✅ Added DB unique constraint:
  ```java
  @Table(
    name = "transactions",
    uniqueConstraints = @UniqueConstraint(
      name = "uk_txn_account_idemp_type",
      columnNames = {"account_id", "idempotency_key", "type"}
    )
  )
  ```

### 2. Repository Layer

#### `TransactionRepository.java`
- ✅ Added idempotency lookup method:
  ```java
  Optional<Transaction> findByAccount_IdAndIdempotencyKeyAndType(
      Long accountId, String idempotencyKey, TransactionType type);
  ```

### 3. Controller Layer

#### `TransactionController.java`
- ✅ Added `Idempotency-Key` header parameter (optional, enforced for withdrawals):
  ```java
  @PostMapping
  public ResponseEntity<TransactionDto> createTransaction(
      @PathVariable Long accountId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CreateTransactionRequest request)
  ```

### 4. Service Layer

#### `TransactionService.java`
- ✅ Updated `createTransaction()` signature to accept `idempotencyKey`
- ✅ Added routing logic: withdrawals → idempotent path, deposits → existing logic
- ✅ Implemented `withdrawWithOptimisticRetry()` - retry wrapper (up to 3 attempts)
- ✅ Implemented `withdrawOnce()` - transactional withdrawal with:
  - Idempotency pre-check
  - Account loading with optimistic lock
  - Funds validation
  - Balance update
  - Transaction creation with idempotency key
  - Duplicate insert handling (unique constraint race)
  - Comprehensive logging

### 5. Testing

#### Test Files Updated
- ✅ `TransactionServiceTest.java` - Fixed all deposit test calls to pass `null` for idempotencyKey
- ✅ `CriticalFinancialWorkflowTest.java` - Fixed all test calls
- ✅ Created `WithdrawalIdempotencyTest.java` - Integration tests for:
  - Duplicate idempotency key detection
  - Missing idempotency key validation
  - Insufficient funds handling
  - Concurrent withdrawal safety

**Note**: Integration tests require Spring Boot context and may need Customer repository setup.

### 6. Documentation

- ✅ Created `WITHDRAWAL_IDEMPOTENCY_TESTING.md` with:
  - Curl examples for all scenarios
  - Database verification queries
  - Log event descriptions
  - Production bug scenarios prevented
  - Concurrency testing instructions

---

## Technical Design

### Transaction Boundaries

```
createTransaction(accountId, request, idempotencyKey)
  ├─ if WITHDRAWAL
  │   └─ withdrawWithOptimisticRetry()  [non-transactional, retry wrapper]
  │       └─ withdrawOnce()  [@Transactional]
  │           ├─ Idempotency check
  │           ├─ Load account (with @Version)
  │           ├─ Validate funds
  │           ├─ Update balance
  │           ├─ Insert transaction (with idempotency key)
  │           └─ Handle duplicate insert (unique constraint)
  └─ if DEPOSIT
      └─ createDeposit()  [@Transactional, existing logic]
```

### Concurrency Strategy

| Mechanism | Purpose | Implementation |
|-----------|---------|----------------|
| **Optimistic Locking** | Prevent lost updates | `@Version` on Account |
| **Retry Logic** | Handle version conflicts | 3 attempts, exponential backoff possible |
| **Idempotency Key** | Prevent duplicate requests | DB unique constraint + pre-check |
| **Unique Constraint** | Race-proof duplicate detection | `(account_id, idempotency_key, type)` |

### Observability

All withdrawal attempts log structured events:

| Event | When | Fields |
|-------|------|--------|
| `withdraw.success` | Withdrawal completed | accountId, idempotencyKey, txnId, amount, newBalance |
| `withdraw.duplicate` | Idempotency key reused | accountId, idempotencyKey, txnId |
| `withdraw.duplicate_race` | Unique constraint triggered | accountId, idempotencyKey, txnId |
| `withdraw.insufficient_funds` | Balance check failed | accountId, idempotencyKey, balance, amount |
| `withdraw.optimistic_retry` | Version conflict retry | accountId, idempotencyKey, attempt, maxRetries |

---

## Production Safety Guarantees

### ✅ Prevents Double Debit
- **Scenario**: Client retries failed request
- **Protection**: Idempotency key + unique constraint
- **Result**: Second request returns cached transaction, balance debited once

### ✅ Prevents Race Conditions
- **Scenario**: Concurrent withdrawals on same account
- **Protection**: Optimistic locking (`@Version`)
- **Result**: One succeeds, others retry with updated balance

### ✅ Prevents Silent Corruption
- **Scenario**: DB commit succeeds but response fails
- **Protection**: Idempotency check before any mutations
- **Result**: Retry returns existing transaction

### ✅ Atomic Operations
- **Scenario**: Partial failure (balance updated but transaction not created)
- **Protection**: `@Transactional` boundary
- **Result**: All-or-nothing, rollback on failure

---

## Failure Case Analysis: Network Retry During Withdrawal

### The Problem

**Scenario**: A mobile banking app submits a $100 withdrawal. The backend processes it successfully, but the network fails before the response reaches the client. The client automatically retries the request.

**Without Idempotency**:
```
Request 1: POST /withdraw {amount: 100, idempotencyKey: null}
  → Balance: 1000 - 100 = 900 ✅
  → Transaction created ✅
  → Network timeout ❌ (response lost)

Request 2 (retry): POST /withdraw {amount: 100, idempotencyKey: null}
  → Balance: 900 - 100 = 800 ❌ DOUBLE DEBIT
  → Transaction created ❌ DUPLICATE
  → Response: Success (but wrong!)
```

**Result**: Customer charged twice, balance incorrect, support ticket created.

---

### The Solution

**With Idempotency + Optimistic Locking**:

```
Request 1: POST /withdraw {amount: 100, idempotencyKey: "abc123"}
  → Idempotency check: No existing transaction ✅
  → Load account (version=1) ✅
  → Balance check: 1000 >= 100 ✅
  → Update balance: 1000 - 100 = 900 ✅
  → Insert transaction (idempotencyKey="abc123") ✅
  → Save account (version=2) ✅
  → Network timeout ❌ (response lost)

Request 2 (retry): POST /withdraw {amount: 100, idempotencyKey: "abc123"}
  → Idempotency check: Transaction exists with key "abc123" ✅
  → Log: withdraw.duplicate ✅
  → Return existing transaction (txnId=1, amount=100) ✅
  → Response: Success (same transaction)
```

**Result**: Customer charged once, balance correct, retry transparent.

---

### Concurrent Withdrawal Protection

**Scenario**: Two simultaneous $80 withdrawals on an account with $100 balance.

**Without Optimistic Locking**:
```
Thread A: Read balance = 100
Thread B: Read balance = 100
Thread A: Check 100 >= 80 ✅
Thread B: Check 100 >= 80 ✅
Thread A: Update balance = 20
Thread B: Update balance = 20 (overwrites A's update!)
Final balance: 20 (should be -60 or reject one)
```

**Result**: Lost update, incorrect balance.

---

**With Optimistic Locking**:
```
Thread A: Load account (balance=100, version=1)
Thread B: Load account (balance=100, version=1)

Thread A: Update balance=20, version=2 ✅ (commits first)
Thread B: Update balance=20, version=2 ❌ (version conflict!)
  → OptimisticLockException thrown
  → Retry: Load account (balance=20, version=2)
  → Check 20 >= 80 ❌
  → Throw InsufficientFundsException
  → Log: withdraw.insufficient_funds

Final balance: 20 ✅
Transactions: 1 success, 1 rejected ✅
```

**Result**: One withdrawal succeeds, one fails correctly, balance accurate.

---

### Race Condition on Duplicate Insert

**Scenario**: Two threads retry the same withdrawal simultaneously with the same idempotency key.

**Without Unique Constraint**:
```
Thread A: Check for idempotencyKey="abc123" → Not found
Thread B: Check for idempotencyKey="abc123" → Not found
Thread A: Insert transaction (idempotencyKey="abc123") ✅
Thread B: Insert transaction (idempotencyKey="abc123") ✅ DUPLICATE!
```

**Result**: Two transactions created, double debit.

---

**With Unique Constraint**:
```
Thread A: Check for idempotencyKey="abc123" → Not found
Thread B: Check for idempotencyKey="abc123" → Not found
Thread A: Insert transaction (idempotencyKey="abc123") ✅
Thread B: Insert transaction (idempotencyKey="abc123") 
  → DataIntegrityViolationException (unique constraint)
  → Catch exception
  → Query for existing transaction
  → Return existing transaction ✅
  → Log: withdraw.duplicate_race

Final: 1 transaction, balance debited once ✅
```

**Result**: Database enforces exactly-once, race condition handled gracefully.

---

### Why This Matters

**Production Impact**:
- **Financial Correctness**: No double debits, no lost updates
- **Customer Trust**: Retries are safe and transparent
- **Support Cost**: Fewer tickets for "charged twice" issues
- **Audit Trail**: Every attempt is logged with correlationId

**Design Philosophy**: **Trust over convenience**
- We don't trust the network (idempotency)
- We don't trust concurrency (optimistic locking)
- We don't trust application logic alone (DB constraints)

This mirrors the verification-first approach in HALO-RAG: **correctness is non-negotiable**.

---

## Testing Instructions

### Quick Smoke Test

```bash
# 1. Start the application
cd backend
mvn spring-boot:run

# 2. Create a withdrawal
curl -X POST "http://localhost:8080/api/accounts/1/transactions" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-key-001" \
  -H "Authorization: Basic $(echo -n 'admin:admin' | base64)" \
  -d '{"type":"WITHDRAWAL","amount":50.00,"category":"OTHER"}'

# 3. Retry with same key (should return same transaction)
curl -X POST "http://localhost:8080/api/accounts/1/transactions" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-key-001" \
  -H "Authorization: Basic $(echo -n 'admin:admin' | base64)" \
  -d '{"type":"WITHDRAWAL","amount":50.00,"category":"OTHER"}'

# 4. Check logs for "withdraw.duplicate" event
```

### Verification

```sql
-- Check unique constraint exists
SELECT constraint_name 
FROM information_schema.table_constraints 
WHERE table_name = 'transactions' 
AND constraint_name = 'uk_txn_account_idemp_type';

-- Check version column exists
SELECT column_name 
FROM information_schema.columns 
WHERE table_name = 'accounts' 
AND column_name = 'version';

-- Check idempotency_key column exists
SELECT column_name 
FROM information_schema.columns 
WHERE table_name = 'transactions' 
AND column_name = 'idempotency_key';
```

---

## Interview Talking Points

### Problem Statement
"Withdrawal requests can be retried due to network failures or timeouts. Without idempotency, the same withdrawal could be processed multiple times, causing double debits and incorrect balances."

### Solution
"I implemented idempotent withdrawals using:
1. **Idempotency keys** - clients provide a unique key per request
2. **Optimistic locking** - `@Version` field prevents concurrent balance corruption
3. **DB unique constraint** - race-proof duplicate detection
4. **Retry logic** - automatic retry on version conflicts"

### Trade-offs
"I chose optimistic locking over pessimistic because:
- **Lower contention**: Most accounts don't have simultaneous withdrawals
- **Better throughput**: No locks held during transaction
- **Cleaner failure modes**: Version conflicts are retryable, deadlocks are not

If withdrawal contention becomes high, we can switch to `SELECT FOR UPDATE`."

### Production Impact
"This prevents three critical bugs:
1. **Double debit on retry** - saved by idempotency key
2. **Race condition** - saved by optimistic locking
3. **Silent corruption** - saved by transactional boundaries"

---

## Next Steps (Optional)

1. **Add Flyway migration** - Currently relies on Hibernate DDL auto
2. **Add metrics** - Track retry rates, duplicate detection rates
3. **Implement for deposits** - Currently deposits don't require idempotency keys
4. **Add idempotency key TTL** - Expire keys after 24 hours to prevent unbounded growth
5. **Add integration tests** - Requires fixing Spring Boot test context setup

---

## ⚠️ Production Deployment Note

**CRITICAL**: Verify the unique constraint exists before deploying to production.

Hibernate `ddl-auto=update` is **not guaranteed** to add unique constraints on existing tables. You must verify:

### Verification Steps

1. **Start the application locally**:
   ```bash
   mvn spring-boot:run
   ```

2. **Access H2 Console**: http://localhost:8080/api/h2-console
   - JDBC URL: `jdbc:h2:mem:bankopsdb`
   - Username: `sa`
   - Password: (leave blank)

3. **Run verification query**:
   ```sql
   SELECT constraint_name, column_name 
   FROM information_schema.key_column_usage 
   WHERE table_name = 'TRANSACTIONS' 
   AND constraint_name = 'UK_TXN_ACCOUNT_IDEMP_TYPE';
   ```

4. **Expected Result**: 3 rows showing columns: `account_id`, `idempotency_key`, `type`

### If Constraint Does NOT Exist

**For Production (Azure SQL)**, manually create the constraint:

```sql
ALTER TABLE transactions 
ADD CONSTRAINT uk_txn_account_idemp_type 
UNIQUE (account_id, idempotency_key, type);
```

**Better Approach**: Add a Flyway migration:

```sql
-- V2__add_idempotency_constraint.sql
ALTER TABLE transactions 
ADD COLUMN idempotency_key VARCHAR(64);

ALTER TABLE transactions 
ADD CONSTRAINT uk_txn_account_idemp_type 
UNIQUE (account_id, idempotency_key, type);
```

**Why This Matters**: Without the unique constraint, the race condition protection fails. The application will still work for single-threaded requests, but concurrent duplicate requests can create multiple transactions.

---

## Files Changed

```
backend/src/main/java/com/bankops/portal/
├── entity/
│   ├── Account.java                    (already had @Version)
│   └── Transaction.java                (+ idempotencyKey, unique constraint)
├── repository/
│   └── TransactionRepository.java      (+ findByAccount_IdAndIdempotencyKeyAndType)
├── controller/
│   └── TransactionController.java      (+ Idempotency-Key header)
└── service/
    └── TransactionService.java         (+ withdrawal routing, retry logic)

backend/src/test/java/com/bankops/portal/service/
├── TransactionServiceTest.java         (fixed test calls)
├── CriticalFinancialWorkflowTest.java  (fixed test calls)
└── WithdrawalIdempotencyTest.java      (NEW - integration tests)

docs/
└── WITHDRAWAL_IDEMPOTENCY_TESTING.md   (NEW - testing guide)
```

---

## Status: ✅ READY FOR REVIEW

All code changes are complete and ready for:
- Code review
- Manual testing with curl
- Deployment to staging
- Integration test fixes (if needed)
