# BankOps Portal

**Bank-style internal operations portal built with Java Spring Boot + Angular, deployed on Azure with CI/CD, RBAC, structured logging, and incident/case workflows.**

## Overview

BankOps Portal is an enterprise-grade internal operations tool that demonstrates full-stack engineering capabilities. The system provides four core modules for managing bank operations:

1. **Customers** - Create, search, and view customer profiles
2. **Accounts** - Open/close accounts (Chequing/Savings), view account summaries and balances
3. **Transactions** - Post transactions (deposit/withdrawal), view transaction history with filtering, and enforce business rules (no negative balances unless overdraft enabled)
4. **Cases & Incident Console** - Create support cases linked to customers/accounts/transactions, manage case statuses (Open → Investigating → Resolved), and investigate incidents using correlation IDs

## Key Features

- **Correlation ID Tracking**: Every transaction request receives a unique `correlationId` for end-to-end traceability
- **Structured Logging**: All operations log events with correlation IDs for production debugging
- **Incident Investigation**: Search by correlation ID to view related transactions, cases, and log events
- **Role-Based Access Control (RBAC)**: Different permissions for users and support staff
- **Business Rule Validation**: Enforces banking rules (e.g., overdraft protection)
- **Enterprise Workflows**: Support case management with status transitions

## Tech Stack

### Backend
- **Java 17** with **Spring Boot 3.2**
- Spring Web, Spring Data JPA, Spring Security, Spring Validation
- H2 (local) / Azure SQL (production)
- JUnit 5 + Mockito for testing

### Frontend
- **Angular 17** with **Angular Material**
- TypeScript, RxJS
- Jasmine/Karma for testing

### Infrastructure
- Azure App Service (Backend & Frontend)
- Azure SQL Database
- Azure DevOps Pipelines (CI/CD)

## Project Structure

```
bankops-portal/
├── backend/              # Spring Boot REST API
│   ├── src/
│   │   ├── main/java/   # Java source code
│   │   └── main/resources/  # application.yaml, etc.
│   └── pom.xml
├── frontend/            # Angular application
│   ├── src/
│   │   ├── app/         # Angular components, services, routes
│   │   └── environments/
│   └── package.json
├── docs/                # Documentation
│   ├── ARCHITECTURE.md
│   └── RUNBOOK.md
├── azure-pipelines/     # CI/CD pipeline definitions
└── README.md
```

## Getting Started

### Prerequisites

- **Java 17+** and **Maven 3.8+**
- **Node.js 18+** and **npm**
- **Angular CLI 17+** (`npm install -g @angular/cli`)

### Local Development

#### Backend

1. Navigate to the backend directory:
   ```bash
   cd backend
   ```

2. Build and run the Spring Boot application:
   ```bash
   mvn spring-boot:run
   ```

   The API will be available at `http://localhost:8080/api`

3. H2 Console (for local development):
   - URL: `http://localhost:8080/api/h2-console`
   - JDBC URL: `jdbc:h2:mem:bankopsdb`
   - Username: `sa`
   - Password: (empty)

#### Frontend

1. Navigate to the frontend directory:
   ```bash
   cd frontend
   ```

2. Install dependencies:
   ```bash
   npm install
   ```

3. Start the development server:
   ```bash
   npm start
   # or
   ng serve
   ```

   The application will be available at `http://localhost:4200`

### Environment Variables

#### Backend (Production)

Set these environment variables for Azure SQL:

- `AZURE_SQL_URL` - Azure SQL connection string
- `AZURE_SQL_USERNAME` - Database username
- `AZURE_SQL_PASSWORD` - Database password

#### Frontend (Production)

- `API_URL` - Backend API URL (defaults to `http://localhost:8080/api` in development)

## How Correlation ID Works

Every transaction request generates a unique **correlation ID** (UUID) that is:

1. **Returned in the API response** - The client receives the correlation ID immediately
2. **Included in all log entries** - Structured logs include the correlation ID in the MDC (Mapped Diagnostic Context)
3. **Stored in the database** - The `log_events` table stores log entries with their correlation IDs
4. **Used for incident investigation** - The Incident Console allows searching by correlation ID to view:
   - The transaction details
   - Any linked support cases
   - A timeline of all log events for that correlation ID

This enables production debugging workflows where support staff can trace a customer issue from the initial transaction request through all system operations.

## API Endpoints

### Customers
- `POST /api/customers` - Create a new customer
- `GET /api/customers?query=` - Search customers
- `GET /api/customers/{id}` - Get customer details

### Accounts
- `POST /api/customers/{customerId}/accounts` - Open a new account
- `GET /api/customers/{customerId}/accounts` - List customer accounts
- `GET /api/accounts/{id}` - Get account details
- `PATCH /api/accounts/{id}` - Update account (status, overdraft)

### Transactions
- `POST /api/accounts/{accountId}/transactions` - Post a transaction (returns correlationId)
- `GET /api/accounts/{accountId}/transactions` - Get transaction history

### Cases
- `POST /api/cases` - Create a support case
- `GET /api/cases?status=&severity=` - List/filter cases
- `PATCH /api/cases/{id}` - Update case status

### Incident Console
- `GET /api/incidents/{correlationId}` - Get incident details (transaction + case + log events)

## Testing

### Backend Tests
```bash
cd backend
mvn test
```

### Frontend Tests
```bash
cd frontend
npm test
```

## CI/CD

The project includes Azure DevOps pipeline definitions in `azure-pipelines/`. The pipeline:

1. Builds the backend (Maven)
2. Runs backend unit tests
3. Builds the frontend (Angular)
4. Runs frontend tests
5. Packages artifacts
6. Deploys to Azure (Dev/Prod environments)

## Documentation

- **[ARCHITECTURE.md](docs/ARCHITECTURE.md)** - System architecture, data flow, and component design
- **[RUNBOOK.md](docs/RUNBOOK.md)** - Production debugging guide using correlation IDs

## License

MIT License - see [LICENSE](LICENSE) file for details.

