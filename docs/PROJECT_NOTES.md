# Project Notes

## Critical Fixes Applied

These critical issues have been resolved in the codebase:

1.  **CORS Configuration Added**: Added `CorsConfig.java` to allow frontend-backend communication.
2.  **HTTP Authentication Interceptor**: Frontend now sends Basic Auth headers with requests (`auth.interceptor.ts`).
3.  **Global Error Handling**: Added `error.interceptor.ts` for consistent error messages.
4.  **Environment-Based Credentials**: Moved hardcoded credentials to validation in `SecurityConfig.java` using properties.
5.  **Production URL Fix**: Fixed literal `${API_URL}` issue in `environment.prod.ts`.
6.  **Health Check Endpoint**: Added `/health` and Actuator endpoints for operations monitoring.
7.  **Correlation ID Validation**: Added UUID validation in `IncidentController`.

## Code Review Notes

### Critical Issues (Resolved)
- **Hardcoded Credentials**: Addressed by moving to environment variables/properties.
- **Transaction Atomicity**: Recommendation to ensure proper `@Transactional` boundaries.
- **Missing Input Validation**: Added for specific fields like Correlation ID.

### High Priority Issues (Outstanding)
- **Loading Indicators**: Frontend lacks visual feedback during API calls.
- **Transaction Error Handling**: Needs robust rollback and partial failure handling.
- **Unit Test Coverage**: Needs expansion for Customer, Account, and Incident services.
- **Auth Service**: Frontend should move from hardcoded in interceptor to a proper `AuthService`.

### Medium/Low Priority Improvements
- **API Documentation**: Add Swagger/OpenAPI.
- **Pagination**: Add to list endpoints to support scalability.
- **Rate Limiting**: Prevent API abuse.
- **Interactive API Versioning**: `/api/v1/`.

## Future Improvements

### Architecture
- **Auth**: Move to JWT or OAuth2 instead of Basic Auth + Session-less.
- **Database**: Add Flyway/Liquibase for schema migrations.
- **Caching**: Implement Redis for frequently accessed data (e.g., configurations).

### Developer Experience
- **Observability**: add OpenTelemetry/Zipkin for distributed tracing visualization beyond just logs.
- **Docker**: Add `docker-compose.yml` for one-command startup of backend + frontend + db.

### Deployment Environment Variables reference
**Backend:**
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `APP_USER_USERNAME` / `APP_USER_PASSWORD`
- `APP_SUPPORT_USERNAME` / `APP_SUPPORT_PASSWORD`
- `FRONTEND_URL`

**Frontend:**
- `API_URL`
