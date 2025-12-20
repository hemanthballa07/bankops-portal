# BankOps Portal

# BankOps Portal
**Production-style BankOps portal built with Java Spring Boot + Angular on Azure, featuring RBAC, unit tests, CI/CD, correlation IDs, and incident/case workflows.**

## Overview
- **Enterprise Operations**: Manage Customers, Accounts, Transactions, and Customer Support Cases.
- **Traceability**: End-to-end `correlationId` tracking for every transaction and log event.
- **Security**: Role-Based Access Control (RBAC) and dev-only basic auth for secure access.
- **Resilience**: Structured logging and incident investigation workflows.
- **Modern Stack**: Full-stack architecture with production-grade CI/CD pipelines.

## Modules
1.  **Customers**: Create and manage customer profiles.
2.  **Accounts**: Chequing/Savings management with business rules.
3.  **Transactions**: Deposits/Withdrawals with overdraft protection.
4.  **Incident Console**: Support case management linked to transaction logs.

## Tech Stack
- **Backend**: Java 17, Spring Boot 3.2 (Web, Data JPA, Security), H2 (Local), JUnit 5.
- **Frontend**: Angular 17, Angular Material, TypeScript, Jasmine/Karma.
- **Infrastructure**: Azure App Service, Azure SQL, Azure DevOps Pipelines.

## Getting Started

### Backend
1.  `cd backend`
2.  Running locally: `mvn spring-boot:run`
    - API: `http://localhost:8080/api`
    - H2 Console: `http://localhost:8080/api/h2-console` (JDBC: `jdbc:h2:mem:testdb`)
    - **Note:** Context path is `/api`. Uses dev-only basic auth (User: `user` / `password`).

### Frontend
1.  `cd frontend`
2.  `npm install && npm start`
    - UI: `http://localhost:4200`

### Testing
- **Backend**: `mvn test`
- **Frontend**: `npm test`

For detailed testing steps and curl scripts, see **[docs/TESTING_GUIDE.md](docs/TESTING_GUIDE.md)**.

## CI/CD
Automated pipelines in Azure DevOps build, test, and deploy both backend and frontend artifacts to Azure App Services. See `azure-pipelines/` for definitions.

## Documentation & Notes
- **[docs/PROJECT_NOTES.md](docs/PROJECT_NOTES.md)**: Critical fixes, review notes, and future improvements.
- **[docs/TESTING_GUIDE.md](docs/TESTING_GUIDE.md)**: Detailed testing guide and known issues.
- **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**: System design and data flow.
