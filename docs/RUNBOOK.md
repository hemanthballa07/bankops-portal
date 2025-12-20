# Operations Runbook

## Overview
This runbook provides procedures for supporting the BankOps Portal in production, focusing on incident investigation using correlation IDs.

## Incident Investigation Workflow

When a customer reports an issue or a system alert triggers, follow these steps:

### 1. Identify the Transaction
Ask the customer for details, or search logs for the error.
**Goal:** Find the `correlationId`.

### 2. Search by Correlation ID
Use the Incident Console or API to retrieve the full trace.

**API Command:**
```bash
# Get full incident context (Transaction + Cases + Logs)
curl -u support:password http://localhost:8080/api/incidents/{correlationId}

# Get raw logs only
curl -u support:password http://localhost:8080/api/log-events/by-correlation/{correlationId}
```

### 3. Analyze the Trace
Check the sequence of events in the logs.
- **INFO**: "Transaction request received"
- **WARN**: "Transaction failed: Insufficient funds" ?
- **ERROR**: Database or system errors?

### 4. Verify Transaction Status
Check the final state of the transaction.
```bash
curl -u support:password http://localhost:8080/api/transactions/by-correlation/{correlationId}
```
- **PENDING**: Transaction started but didn't complete (Investigate system crash/rollback).
- **COMPLETED**: Balance should be updated.
- **FAILED**: Business rule or system error.

### 5. Resolution
- **If System Error**: Create a bug report with the correlation ID.
- **If User Error**: Explain the reason (e.g. insufficient funds) to the customer.
- **Update Case**: Link the case to the correlation ID and close it.

## Common Issues

### Transaction Rolled Back
If logs show an error mid-transaction, the database changes should be rolled back.
- **Check**: Account balance should be unchanged.
- **Logs**: Look for "Transaction marked for rollback".

### Missing Logs
If `log_events` table is missing entries but application logs show them:
- The database logging might have failed.
- Check application logs (file/console) for "Failed to persist log event".
- **Note**: Transaction continuity is preserved even if log persistence fails.

## Maintenance

### Database
- **H2 Console (Dev)**: `/api/h2-console`
- **Azure SQL**: Use Azure Portal Query Editor.

### Monitoring
- **Health Check**: `/actuator/health`
- **Azure Insights**: Check App Service "Log Stream".
