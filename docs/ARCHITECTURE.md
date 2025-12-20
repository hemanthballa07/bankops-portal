# Architecture Overview

## System Architecture

BankOps Portal follows a traditional three-tier architecture:

```
┌─────────────────────────────────────────────────────────┐
│                    Frontend (Angular)                    │
│  - Customers Module  - Accounts Module                   │
│  - Transactions Module - Cases Module                    │
│  - Incident Console                                      │
└────────────────────┬────────────────────────────────────┘
                     │ HTTP/REST
┌────────────────────▼────────────────────────────────────┐
│              Backend API (Spring Boot)                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │  Controllers │  │   Services   │  │  Repositories│  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  │
│         │                 │                  │          │
│  ┌──────▼─────────────────▼──────────────────▼──────┐  │
│  │         Spring Security (RBAC)                    │  │
│  └───────────────────────────────────────────────────┘  │
└────────────────────┬────────────────────────────────────┘
                     │ JDBC
┌────────────────────▼────────────────────────────────────┐
│              Database (H2 / Azure SQL)                   │
│  - customers  - accounts  - transactions                │
│  - cases      - log_events                              │
└──────────────────────────────────────────────────────────┘
```

## Data Flow

### Transaction Creation Flow

1. **Frontend** → User submits transaction form
2. **Backend Controller** → Receives request, validates input
3. **Transaction Service** → 
   - Generates `correlationId` (UUID)
   - Validates business rules (balance, overdraft)
   - Creates transaction record
   - Logs event with correlation ID
   - Persists log event to `log_events` table
4. **Response** → Returns transaction + correlation ID to frontend

### Incident Investigation Flow

1. **Support Staff** → Enters correlation ID in Incident Console
2. **Backend** → Queries:
   - `transactions` table (by correlation_id)
   - `cases` table (linked to transaction)
   - `log_events` table (by correlation_id, ordered by timestamp)
3. **Response** → Returns aggregated incident timeline

## Component Design

### Backend Layers

#### Controllers (REST API)
- Handle HTTP requests/responses
- Input validation
- Error handling
- Security annotations

#### Services (Business Logic)
- Transaction rules (overdraft, balance checks)
- Case status transitions
- Correlation ID generation
- Logging orchestration

#### Repositories (Data Access)
- JPA repositories for database operations
- Custom queries for filtering/searching

#### Entities (Domain Model)
- Customer, Account, Transaction, SupportCase, LogEvent
- JPA annotations for ORM mapping

### Frontend Layers

#### Components
- Presentational components (UI)
- Container components (data fetching)

#### Services
- API client services (HttpClient)
- Shared business logic
- Error handling

#### Models
- TypeScript interfaces matching backend DTOs

## Data Model

### Entity Relationships

```
Customer (1) ────< (N) Account
Account (1) ────< (N) Transaction
Transaction (1) ────< (N) LogEvent
Customer (1) ────< (N) SupportCase
Account (0..1) ────< (N) SupportCase
Transaction (0..1) ────< (N) SupportCase
```

### Key Tables

- **customers**: Customer profile information
- **accounts**: Account details (type, balance, status, overdraft flag)
- **transactions**: Transaction records with correlation_id
- **cases**: Support cases with status workflow
- **log_events**: Structured log entries for incident investigation

## Security

### Role-Based Access Control (RBAC)

- **ROLE_USER**: Can read/write customers, accounts, transactions
- **ROLE_SUPPORT**: Can manage cases and access incident console

### Security Implementation

- Spring Security with method-level security
- JWT tokens (future enhancement)
- Input validation on all endpoints
- SQL injection prevention (JPA parameterized queries)

## Logging Strategy

### Structured Logging

All logs include:
- Timestamp
- Log level (INFO, DEBUG, ERROR, etc.)
- Correlation ID (when available)
- Context JSON (structured data)

### Log Storage

- **Application logs**: Standard Spring Boot logging (console/file)
- **Database logs**: `log_events` table for incident investigation
- **Correlation**: All transaction-related logs include the same correlation ID

## Deployment Architecture

### Azure Deployment

```
┌─────────────────────────────────────────┐
│      Azure App Service (Frontend)       │
│      Static Web App / App Service       │
└─────────────────────────────────────────┘
                     │
                     │ HTTPS
                     │
┌────────────────────▼────────────────────┐
│      Azure App Service (Backend)        │
│      Spring Boot Application            │
└────────────────────┬────────────────────┘
                     │
                     │ JDBC
                     │
┌────────────────────▼────────────────────┐
│      Azure SQL Database                 │
│      Production Database                │
└─────────────────────────────────────────┘
```

### CI/CD Pipeline

1. **Source Control** → Azure DevOps Repos / GitHub
2. **Build** → Maven (backend) + npm (frontend)
3. **Test** → Unit tests + integration tests
4. **Package** → JAR (backend) + static files (frontend)
5. **Deploy** → Azure App Services

## Scalability Considerations

- **Stateless Backend**: Spring Boot app can scale horizontally
- **Database Connection Pooling**: HikariCP for efficient connections
- **Caching**: Future enhancement with Redis for frequently accessed data
- **API Rate Limiting**: Future enhancement for production hardening

