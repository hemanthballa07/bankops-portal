# Production Runbook: Incident Investigation Using Correlation IDs

## Overview

This runbook describes how to investigate production issues in BankOps Portal using the correlation ID system. Every transaction request generates a unique correlation ID that enables end-to-end traceability.

## What is a Correlation ID?

A **correlation ID** is a unique identifier (UUID) that:
- Is generated when a transaction is created
- Is returned to the client in the API response
- Is included in all related log entries
- Is stored in the database for querying

## Common Scenarios

### Scenario 1: Customer Reports Transaction Issue

**Customer says**: "I tried to deposit $100 but it didn't go through."

**Steps to investigate**:

1. **Get the correlation ID from the customer**
   - Ask the customer to check their transaction confirmation
   - Or search for the transaction in the system

2. **Use the Incident Console**
   - Navigate to: `/incidents/{correlationId}`
   - Or use the API: `GET /api/incidents/{correlationId}`

3. **Review the incident timeline**
   - Transaction details (amount, type, account, status)
   - Related support cases (if any)
   - Log events in chronological order

4. **Analyze the log events**
   - Look for ERROR or WARN level logs
   - Check the `context_json` field for detailed error information
   - Identify where in the flow the issue occurred

5. **Check related cases**
   - If a case exists, review the case notes
   - Update case status if needed

### Scenario 2: System Error Investigation

**Alert**: High error rate on transaction endpoint.

**Steps to investigate**:

1. **Query log_events table for recent errors**
   ```sql
   SELECT correlation_id, level, message, context_json, created_at
   FROM log_events
   WHERE level = 'ERROR'
     AND created_at >= NOW() - INTERVAL '1 hour'
   ORDER BY created_at DESC;
   ```

2. **Group by correlation_id to find patterns**
   ```sql
   SELECT correlation_id, COUNT(*) as error_count
   FROM log_events
   WHERE level = 'ERROR'
     AND created_at >= NOW() - INTERVAL '1 hour'
   GROUP BY correlation_id
   ORDER BY error_count DESC;
   ```

3. **For each problematic correlation_id**:
   - Use Incident Console to view full timeline
   - Identify common failure points
   - Check if related transactions failed

### Scenario 3: Balance Discrepancy

**Customer says**: "My balance doesn't match my transaction history."

**Steps to investigate**:

1. **Get customer account ID**
   - Search customer by name/email
   - Navigate to account details

2. **Review all transactions for the account**
   - API: `GET /api/accounts/{accountId}/transactions`
   - Verify transaction amounts and types
   - Check for any failed transactions

3. **For each suspicious transaction**:
   - Get the correlation ID from the transaction
   - Use Incident Console to view full log trail
   - Verify transaction was processed correctly

4. **Check for duplicate transactions**
   - Look for multiple transactions with same amount/time
   - Review log events for retry logic

## Using the Incident Console

### Via UI

1. Navigate to the Incident Console page
2. Enter the correlation ID in the search field
3. Review the displayed timeline:
   - **Transaction Summary**: Amount, type, account, status, timestamp
   - **Related Case**: Case details if a case was created
   - **Log Timeline**: Chronological list of all log events

### Via API

```bash
curl -X GET "http://localhost:8080/api/incidents/{correlationId}" \
  -H "Authorization: Bearer {token}"
```

**Response structure**:
```json
{
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "transaction": {
    "id": 123,
    "accountId": 45,
    "type": "DEPOSIT",
    "amount": 100.00,
    "status": "COMPLETED",
    "createdAt": "2025-01-15T10:30:00Z"
  },
  "case": {
    "id": 67,
    "status": "INVESTIGATING",
    "summary": "Customer reported missing deposit"
  },
  "logEvents": [
    {
      "id": 890,
      "level": "INFO",
      "message": "Transaction created",
      "contextJson": "{\"accountId\":45,\"amount\":100.00}",
      "createdAt": "2025-01-15T10:30:00Z"
    },
    {
      "id": 891,
      "level": "INFO",
      "message": "Balance updated",
      "contextJson": "{\"previousBalance\":500.00,\"newBalance\":600.00}",
      "createdAt": "2025-01-15T10:30:00.100Z"
    }
  ]
}
```

## Database Queries

### Find all log events for a correlation ID

```sql
SELECT 
  id,
  level,
  message,
  context_json,
  created_at
FROM log_events
WHERE correlation_id = '550e8400-e29b-41d4-a716-446655440000'
ORDER BY created_at ASC;
```

### Find transactions with errors

```sql
SELECT 
  t.id,
  t.correlation_id,
  t.account_id,
  t.type,
  t.amount,
  t.status,
  COUNT(le.id) as error_count
FROM transactions t
LEFT JOIN log_events le ON t.correlation_id = le.correlation_id AND le.level = 'ERROR'
WHERE t.created_at >= NOW() - INTERVAL '24 hours'
GROUP BY t.id, t.correlation_id, t.account_id, t.type, t.amount, t.status
HAVING COUNT(le.id) > 0
ORDER BY t.created_at DESC;
```

### Find cases linked to a correlation ID

```sql
SELECT 
  c.id,
  c.status,
  c.severity,
  c.summary,
  c.created_at
FROM cases c
INNER JOIN transactions t ON c.transaction_id = t.id
WHERE t.correlation_id = '550e8400-e29b-41d4-a716-446655440000';
```

## Log Event Levels

- **DEBUG**: Detailed diagnostic information (development only)
- **INFO**: General informational messages (transaction created, balance updated)
- **WARN**: Warning messages (potential issues, but operation continues)
- **ERROR**: Error messages (operation failed, requires investigation)

## Best Practices

1. **Always start with the correlation ID** - It's the key to end-to-end traceability
2. **Review log events chronologically** - Understand the sequence of operations
3. **Check context_json** - Contains structured data about the event
4. **Link to support cases** - Update cases with investigation findings
5. **Document findings** - Add notes to the support case for future reference

## Troubleshooting

### Correlation ID not found

- Verify the correlation ID is correct (UUID format)
- Check if the transaction was created (query transactions table)
- Review application logs for any errors during transaction creation

### Missing log events

- Check if logging is enabled and working
- Verify database connection is healthy
- Review application logs for logging errors

### Performance issues

- For large correlation IDs with many log events, consider pagination
- Add database indexes on `correlation_id` and `created_at` columns
- Use query time ranges to limit result sets

