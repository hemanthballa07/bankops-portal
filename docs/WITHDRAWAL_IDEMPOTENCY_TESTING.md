# Withdrawal Idempotency Testing Guide

## Overview

This document provides curl examples and testing instructions for the idempotent withdrawal feature implemented in BankOps Portal.

## What Was Implemented

### 1. Idempotency Key Enforcement
- **Requirement**: All withdrawal requests MUST include an `Idempotency-Key` header
- **Purpose**: Prevents double debits when clients retry failed requests
- **Implementation**: DB unique constraint on `(account_id, idempotency_key, type)`

### 2. Optimistic Locking
- **Mechanism**: `@Version` field on `Account` entity
- **Purpose**: Prevents concurrent withdrawals from causing race conditions
- **Behavior**: Automatic retry (up to 3 attempts) on optimistic lock failures

### 3. Observability
All withdrawal attempts are logged with:
- `correlationId`
- `accountId`
- `idempotencyKey`
- `amount`
- `outcome` (SUCCESS | DUPLICATE | INSUFFICIENT_FUNDS | OPTIMISTIC_RETRY)
- `transactionId`

---

## Testing Scenarios

### Scenario 1: Successful Withdrawal

```bash
curl -X POST "http://localhost:8080/api/accounts/1/transactions" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440001" \
  -H "Authorization: Basic $(echo -n 'admin:admin' | base64)" \
  -d '{
    "type": "WITHDRAWAL",
    "amount": 100.00,
    "description": "ATM withdrawal",
    "category": "CASH"
  }'
```

**Expected Result**:
- HTTP 201 Created
- Balance decremented by 100.00
- Transaction created with status COMPLETED

---

### Scenario 2: Duplicate Idempotency Key (Retry)

```bash
# First request (succeeds)
curl -X POST "http://localhost:8080/api/accounts/1/transactions" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440002" \
  -H "Authorization: Basic $(echo -n 'admin:admin' | base64)" \
  -d '{
    "type": "WITHDRAWAL",
    "amount": 50.00,
    "description": "Test withdrawal",
    "category": "OTHER"
  }'

# Retry with SAME idempotency key (returns cached result)
curl -X POST "http://localhost:8080/api/accounts/1/transactions" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440002" \
  -H "Authorization: Basic $(echo -n 'admin:admin' | base64)" \
  -d '{
    "type": "WITHDRAWAL",
    "amount": 50.00,
    "description": "Test withdrawal",
    "category": "OTHER"
  }'
```

**Expected Result**:
- Both requests return HTTP 201
- **Same transaction ID** in both responses
- **Same correlationId** in both responses
- Balance decremented **only once** (by 50.00)
- Logs show `withdraw.duplicate` event

---

### Scenario 3: Missing Idempotency Key (Error)

```bash
curl -X POST "http://localhost:8080/api/accounts/1/transactions" \
  -H "Content-Type: application/json" \
  -H "Authorization: Basic $(echo -n 'admin:admin' | base64)" \
  -d '{
    "type": "WITHDRAWAL",
    "amount": 25.00,
    "description": "Missing key test",
    "category": "OTHER"
  }'
```

**Expected Result**:
- HTTP 400 Bad Request
- Error message: "Idempotency-Key header is required for withdrawals"
- No transaction created
- Balance unchanged

---

### Scenario 4: Insufficient Funds

```bash
curl -X POST "http://localhost:8080/api/accounts/1/transactions" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440003" \
  -H "Authorization: Basic $(echo -n 'admin:admin' | base64)" \
  -d '{
    "type": "WITHDRAWAL",
    "amount": 999999.00,
    "description": "Overdraft test",
    "category": "OTHER"
  }'
```

**Expected Result**:
- HTTP 500 Internal Server Error (or 400 depending on error handling)
- Error message: "Insufficient funds. Overdraft not enabled."
- No transaction created
- Balance unchanged
- Logs show `withdraw.insufficient_funds` event

---

### Scenario 5: Deposit (No Idempotency Key Required)

```bash
curl -X POST "http://localhost:8080/api/accounts/1/transactions" \
  -H "Content-Type: application/json" \
  -H "Authorization: Basic $(echo -n 'admin:admin' | base64)" \
  -d '{
    "type": "DEPOSIT",
    "amount": 200.00,
    "description": "Paycheck deposit",
    "category": "OTHER"
  }'
```

**Expected Result**:
- HTTP 201 Created
- Balance incremented by 200.00
- **No idempotency key required** (deposits use existing logic)

---

## Verification Checklist

After running the above scenarios, verify:

### Database Checks

```sql
-- Check account balance
SELECT id, balance, version FROM accounts WHERE id = 1;

-- Check transactions
SELECT id, type, amount, idempotency_key, status, correlation_id, created_at 
FROM transactions 
WHERE account_id = 1 
ORDER BY created_at DESC;

-- Verify unique constraint exists
SELECT constraint_name, column_name 
FROM information_schema.key_column_usage 
WHERE table_name = 'transactions' 
AND constraint_name = 'uk_txn_account_idemp_type';
```

### Log Checks

Search application logs for:
- `withdraw.success` - Successful withdrawal
- `withdraw.duplicate` - Idempotency key reused
- `withdraw.duplicate_race` - Unique constraint triggered during concurrent insert
- `withdraw.insufficient_funds` - Balance check failed
- `withdraw.optimistic_retry` - Optimistic lock retry

---

## Concurrency Testing (Advanced)

To test concurrent withdrawals, use a tool like Apache Bench or write a simple script:

```bash
# Fire 10 concurrent requests with DIFFERENT idempotency keys
for i in {1..10}; do
  curl -X POST "http://localhost:8080/api/accounts/1/transactions" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: concurrent-test-$i" \
    -H "Authorization: Basic $(echo -n 'admin:admin' | base64)" \
    -d '{
      "type": "WITHDRAWAL",
      "amount": 10.00,
      "description": "Concurrent test",
      "category": "OTHER"
    }' &
done
wait

# Verify final balance is correct (initial - 100.00)
```

**Expected Result**:
- All 10 withdrawals succeed (if sufficient balance)
- Final balance is exactly `initial_balance - 100.00`
- No double debits
- Logs may show `withdraw.optimistic_retry` events

---

## Production Bugs This Prevents

### 1. **Double Debit on Network Retry**
- **Scenario**: Client submits withdrawal, network times out, client retries
- **Without idempotency**: Two withdrawals processed, balance debited twice
- **With idempotency**: Second request returns cached result, balance debited once

### 2. **Race Condition on Concurrent Withdrawals**
- **Scenario**: Two withdrawals hit same account simultaneously
- **Without optimistic locking**: Both read balance=100, both deduct 80, final balance=-60 (invalid)
- **With optimistic locking**: One succeeds, one fails with version mismatch, retries with updated balance

### 3. **Silent Data Corruption**
- **Scenario**: Withdrawal fails after DB commit but before response sent
- **Without idempotency**: Client retries, creates duplicate transaction
- **With idempotency**: Duplicate detected, original transaction returned

---

## Implementation Details

### Files Modified

1. **`Transaction.java`** - Added `idempotencyKey` field + unique constraint
2. **`Account.java`** - Added `@Version` field for optimistic locking
3. **`TransactionRepository.java`** - Added `findByAccount_IdAndIdempotencyKeyAndType()`
4. **`TransactionController.java`** - Added `Idempotency-Key` header parameter
5. **`TransactionService.java`** - Added withdrawal routing + retry logic

### Transaction Boundaries

- **Deposit**: Single `@Transactional` method (existing logic)
- **Withdrawal**: 
  - Outer retry wrapper (non-transactional)
  - Inner `@Transactional` method with idempotency check + balance update + txn insert

### Failure Modes

| Failure | Behavior |
|---------|----------|
| Optimistic lock conflict | Retry up to 3 times, then throw exception |
| Unique constraint violation | Return existing transaction (race condition handled) |
| Insufficient funds | Throw exception, no transaction created |
| Missing idempotency key | Throw exception immediately |

---

## Next Steps (Optional Enhancements)

1. **Add Flyway migration** for unique constraint (currently relies on Hibernate DDL)
2. **Expose idempotency key in response** for client debugging
3. **Add metrics** for retry rates and duplicate detection
4. **Implement idempotency for deposits** (currently optional)
5. **Add TTL for idempotency keys** (e.g., expire after 24 hours)

---

## Questions?

For issues or questions, check:
- Application logs for `withdraw.*` events
- Database constraint violations
- Optimistic lock retry counts
