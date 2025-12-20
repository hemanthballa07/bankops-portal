# BankOps Portal - Demo Script

Use this script to demonstrate the "Bank-Grade reliability" features of the portal.

## 1. Setup
**Goal**: Show the environment is clean and running.

1.  **Start Backend**: `cd backend && mvn spring-boot:run`
2.  **Start Frontend**: `cd frontend && npm start`
3.  **Open UI**: Navigate to `http://localhost:4200`
4.  **Login**: Use `user` / `password` (Basic Auth browser prompt for backend calls).

## 2. Customer & Account Creation
**Goal**: Show core entity management.

1.  Click **"Create Customer"**.
    - First Name: `Demo`
    - Last Name: `One`
    - Email: `demo.one@example.com`
2.  Click **"Create Account"** for this customer.
    - Type: `CHEQUING`
    - Balance: `$1000.00`
    - Overdraft: `Disabled` (Important for showing validation).

## 3. Transaction Integrity (The "Wow" Factor)
**Goal**: Demonstrate validation, atomicity, and resilience.

### Scenario A: Happy Path & Correlation ID
1.  Navigate to **Account Details**.
2.  Click **"Deposit"**.
    - Amount: `$500.00`
3.  **Verify**:
    - Balance updates to `$1500.00`.
    - Transaction appears in list.
    - **Highlight**: Copy the `Correlation ID` from the transaction table.
    - Open terminal and Run:
      ```bash
      curl -u user:password http://localhost:8080/api/transactions/by-correlation/<PASTE_ID>
      ```
    - *Explanation*: "Every action is traceable End-to-End."

### Scenario B: Business Rule Validation (Overdraft)
1.  Click **"Withdraw"**.
    - Amount: `$2000.00` (More than balance).
2.  **Verify**:
    - Error toast/message appears: "Insufficient funds".
    - Balance remains `$1500.00`.
    - **No Failed Transaction Log**: (Or "FAILED" status if implemented). Focus on the "Safe Failure".

## 4. Operational Support (Incident Workflow)
**Goal**: Show how support teams use the tool.

1.  Copy a `Transaction ID` from the table (e.g., `101`).
2.  Go to **"Incidents"** or **"Cases"**.
3.  Click **"Create Case"**.
    - Transaction ID: `101`
    - Summary: "Customer disputes fee."
    - Severity: `LOW`
4.  **Verify**:
    - Case is created.
    - Click case to details.
    - Shows linked Transaction data.

## 5. Swagger / API Docs (Tech Validation)
**Goal**: Show enterprise standards.

1.  Navigate to `http://localhost:8080/api/swagger-ui/index.html` (or `swagger-ui.html`).
2.  Show the auto-generated API specifications.

---

## Screenshot Placeholders
*(Add screenshots here to `docs/screenshots/`)*

1.  `dashboard-view.png` - Home screen.
2.  `account-details.png` - Showing transactions list.
3.  `transaction-flow.png` - Modal for deposit/withdraw.
4.  `traceability-console.png` - Swagger UI or Incident view.
