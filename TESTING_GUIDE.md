# Testing Guide

## Known Issues

### Java 23 Compatibility Issue
There's a known issue with Mockito and Java 23 where Mockito cannot mock certain classes. This affects unit tests that mock `LoggingService`.

**Workaround Options:**
1. Use Java 17 or 21 for testing (recommended)
2. Use integration tests instead of unit tests for TransactionService
3. Refactor LoggingService to use an interface

## Running Tests

### Backend Tests

**Prerequisites:** Java 17 or 21 (Java 23 has compatibility issues with Mockito)

```bash
cd backend
mvn clean test
```

**Run specific test class:**
```bash
mvn test -Dtest=TransactionServiceTest
```

**Run integration tests only:**
```bash
mvn test -Dtest=*IntegrationTest
```

### Frontend Tests

```bash
cd frontend
npm install  # First time only
npm test
```

**Run with coverage:**
```bash
npm test -- --code-coverage
```

## Manual Testing

### Start Backend

```bash
cd backend
mvn spring-boot:run
```

Backend will run on: `http://localhost:8080/api`

**Test Health Check:**
```bash
curl http://localhost:8080/health
```

**Test API (with authentication):**
```bash
curl -u user:password http://localhost:8080/api/customers
```

### Start Frontend

```bash
cd frontend
npm install  # First time only
npm start
```

Frontend will run on: `http://localhost:4200`

## Integration Testing

### Test Transaction Flow

1. **Create Customer:**
```bash
curl -u user:password -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "phone": "555-1234"
  }'
```

2. **Create Account:**
```bash
curl -u user:password -X POST http://localhost:8080/api/customers/1/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "type": "CHEQUING"
  }'
```

3. **Create Transaction:**
```bash
curl -u user:password -X POST http://localhost:8080/api/accounts/1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "type": "DEPOSIT",
    "amount": 100.00
  }'
```

4. **Check Correlation ID in response** - Should return a UUID

5. **Search Incident by Correlation ID:**
```bash
curl -u support:password http://localhost:8080/api/incidents/{correlationId}
```

## Test Coverage

### Current Coverage

✅ **Working:**
- Integration tests (TransactionIntegrationTest)
- CaseService unit tests
- Frontend component tests (TransactionFormComponent, IncidentConsoleComponent)

⚠️ **Known Issues:**
- TransactionService unit tests (Mockito + Java 23 compatibility)

## Recommended Testing Strategy

1. **For Development:** Use integration tests which work reliably
2. **For CI/CD:** Use Java 17 or 21 to avoid Mockito compatibility issues
3. **For Manual Testing:** Use the frontend UI and curl commands above

