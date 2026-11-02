# OBMS Architecture

Phase-2 implementation notes against the four-layer design from Phase-1.

---

## 1. Logical layer

User-facing screens, the controllers that accept requests, and the services that
orchestrate business logic.

```
┌──────────────────────── React SPA (24 screens) ────────────────────────┐
│ Landing · Login · Register · OTP · Forgot password                     │
│ Dashboard · Accounts · Transfer · Beneficiaries · Bills · Statements   │
│ Notifications · Profile                                                │
│ Copilot · Analytics · Health · Simulator · Goals · Alerts              │
│ Admin dashboard · KYC queue · Fraud investigation · Audit log          │
└────────────────────────────────┬───────────────────────────────────────┘
                                 │ HTTPS, JSON, Bearer token
┌────────────────────────────────▼───────────────────────────────────────┐
│ Controllers                                                            │
│  AuthController · AccountController · TransferController               │
│  BeneficiaryController · BillPaymentController · StatementController   │
│  NotificationController · IntelligenceController · AdminController     │
└────────────────────────────────┬───────────────────────────────────────┘
┌────────────────────────────────▼───────────────────────────────────────┐
│ Services                                                               │
│  AuthService · CustomerService · AccountService                        │
│  TransferService ──► LedgerService  (the only balance-mutating path)   │
│  BeneficiaryService · BillPaymentService · StatementService            │
│  OtpService · NotificationService · AuditService                       │
│  CategorisationService · AnalyticsService · FinancialHealthService     │
│  SimulatorService · SavingsGoalService · DashboardService              │
│  CopilotService · FraudService · AdminService                          │
└────────────────────────────────┬───────────────────────────────────────┘
┌────────────────────────────────▼───────────────────────────────────────┐
│ Repositories (Spring Data JPA, 17 interfaces)                          │
└────────────────────────────────┬───────────────────────────────────────┘
                                 ▼
                              MySQL 8
```

**Why `LedgerService` is separate from `TransferService`.** The transfer service
decides *whether* money should move and what friction to apply. The ledger service
performs the movement. Keeping them apart means there is exactly one code path
that changes a balance, so the integrity argument is checkable by reading one
class rather than auditing every caller. Bill payments and savings-goal
contributions go through the same path.

---

## 2. Technical layer

### REST surface

| Area | Base path | Notes |
|---|---|---|
| Authentication | `/api/auth/**` | Register, login, OTP, password reset, profile, KYC |
| Accounts | `/api/accounts/**`, `/api/transactions/**` | Balances, paged history, recategorisation |
| Dashboard | `/api/dashboard` | One aggregated payload |
| Transfers | `/api/transfers`, `/api/transfers/confirm` | Two-step risk-based flow |
| Beneficiaries | `/api/beneficiaries/**` | Register, verify, rename, remove |
| Bills | `/api/bills/**` | Billers, pay, history, upcoming |
| Statements | `/api/statements/{accountId}` | Returns `application/pdf` |
| Notifications | `/api/notifications/**` | In-app centre |
| Intelligence | `/api/copilot/**`, `/api/analytics/**`, `/api/health-score`, `/api/simulator/**`, `/api/savings-goals/**`, `/api/fraud-alerts/**` | Customer-facing intelligence |
| Staff | `/api/staff/**` | ADMIN or BANK_STAFF |
| Fraud investigation | `/api/fraud/investigation/**` | ADMIN or FRAUD_ANALYST |
| Admin | `/api/admin/**` | ADMIN only |

### The one internal service-to-service call

```
TransferService
   │ 1. assemble behavioural features from this customer's own debit history
   ▼
FraudService
   │ 2. POST /api/v1/fraud/score   (3-second timeout)
   ▼
FastAPI AI service
   │ 3. Isolation Forest + rule layer
   ▼
   returns { risk_score, primary_reason, factors[], model_version,
             rule_score, model_score, anomaly_score }
   │ 4. Java maps score → band → decision
   ▼
TransferService applies ALLOW / STEP_UP / HOLD / BLOCK
```

The **decision stays on the Java side**. Two consequences, both deliberate:

1. The bank can retune its risk appetite through configuration without
   redeploying the model.
2. When the AI service is unreachable, the Java fallback still produces a safe
   outcome — small transfers proceed, larger ones get a step-up challenge the
   customer can complete. Settlement never depends on the model being up.

### Uniform error contract

Every failure returns the same shape, so the client has one thing to parse:

```json
{
  "timestamp": "2026-11-02T10:15:30Z",
  "status": 400,
  "code": "INSUFFICIENT_FUNDS",
  "message": "The account does not have enough available balance for this transfer.",
  "path": "/api/transfers",
  "fieldErrors": { "amount": "Amount must be at least 0.01" }
}
```

`code` is stable and machine-readable; `message` is safe to show a customer. No
stack trace or internal exception message ever reaches a client.

---

## 3. Data layer

### Entities

```
Customer ──1:1── Kyc
    │ 1:N
    ├──── Account ──1:N── Transaction ──0:1── FraudAlert
    │         │                 │
    │         │                 └──0:1── BillPayment ──N:1── Biller
    │         └──1:N── PendingTransfer
    ├──1:N── Beneficiary
    ├──1:N── SavingsGoal
    ├──1:N── Notification
    ├──1:N── FinancialScoreSnapshot
    └──1:N── CopilotConversation ──1:N── CopilotMessage

UserAccount ──N:M── Role          (login and authorities)
UserAccount ──1:1── Customer

OtpToken            (hashed codes, scoped by purpose and target)
IdempotencyRecord   (unique key per money-moving request)
AuditLog            (append only)
```

### Rules the schema enforces

- **`DECIMAL(19,2)` for every monetary column.** Floating point is never used for
  money anywhere in the application; `BigDecimal` throughout the Java side.
- **`@Version` on `Account`** for optimistic locking, behind the pessimistic row
  locks the transfer path takes.
- **Unique constraints** on account number, customer number, transaction
  reference, idempotency key, and `(owner_customer_id, account_number)` for
  beneficiaries.
- **Indexes** on `(account_id, booked_at)` for statement and history queries, on
  transaction reference, on transfer group reference, and on the OTP lookup tuple.
- **`balance_after` captured on every ledger row**, so a statement shows a running
  balance without recomputing history and a discrepancy is traceable to one row.
- **Double-entry recording**: an internal transfer writes two rows sharing a
  `transfer_group_ref`. A half-written transfer is detectable rather than
  invisible.

### Transaction integrity

```java
@Transactional(propagation = REQUIRED)
public DoubleEntry postDoubleEntry(...) {
    // 1. Lock both accounts in ascending id order - a fixed order is what
    //    prevents simultaneous A→B and B→A transfers from deadlocking.
    // 2. Read the balance from the LOCKED row, so two concurrent debits
    //    cannot both pass the sufficiency check.
    // 3. Reject any computed balance below the account's minimum.
    // 4. Write both legs. Both commit or neither does.
}
```

---

## 4. Infrastructure layer

```
developer push
      │
      ▼
GitHub Actions ──┬── Backend:  compile → 178 tests (H2) → SpotBugs → PMD
                 ├── AI:       ruff → 60 pytest
                 └── Frontend: tsc → eslint → 31 vitest → vite build
      │
      ├── fails the build on any SpotBugs finding of rank 1-9
      └── uploads surefire, SpotBugs, PMD and JaCoCo reports as artefacts
```

Runtime, via Docker Compose:

```
nginx :5173  ──/api──►  Spring Boot :8080  ──►  MySQL :3306
(static SPA)                   │
                               └──────────►  FastAPI :8000
```

Both application images run as non-root. MySQL has a healthcheck and the backend
waits on it, so the backend never starts against a database that is still
initialising.

---

## Requirements traceability

| Requirement | Where it is implemented | Tested by |
|---|---|---|
| FR-1 registration + KYC | `AuthService.register`, `CustomerService.submitKyc`, `AdminService.decideKyc` | `AuthApiTest`, `PrivacyMaskingTest` |
| FR-2 2FA login | `AuthService.login` / `verifyLoginOtp`, `OtpService`, `JwtService` pre-auth split | `AuthApiTest`, `MoneyRulesTest` |
| FR-3 balance + history | `AccountService`, `DashboardService` | `BankingApiTest` |
| FR-4 fund transfer | `TransferService`, `LedgerService` | `LedgerAtomicityTest`, `TransferServiceTest` |
| FR-5 beneficiaries | `BeneficiaryService` | `TransferServiceTest`, `BankingApiTest` |
| FR-6 bill payment | `BillPaymentService`, `ReferenceDataSeeder` | `BankingApiTest`, `ContextLoadTest` |
| FR-7 PDF statement | `StatementService` | `StatementAccuracyTest`, `BankingApiTest` |
| FR-8 notifications | `NotificationService` | `BankingApiTest` |
| NFR-1 security | BCrypt 12, JWT, HTTPS in deployment | `SecurityApiTest` |
| NFR-2 performance | Indexed queries, one aggregated dashboard call | — |
| NFR-3 availability | Degraded fraud fallback, email failures non-fatal | `TransferServiceTest` |
| NFR-4 usability | Consistent screens, loading skeletons, empty states, inline validation | `ui.test.tsx` |

---

## Innovation modules

| Module | Implementation | Honest description |
|---|---|---|
| Fraud detection | Isolation Forest (9 features) + rule layer, blended | A real trained model, on synthetic data; limitation documented |
| Financial copilot | Intent detection → the same services the screens use | Natural language over real analytics; reports `rule_engine` as its generator |
| Health score | 5 weighted indicators summing to 100 | Transparent arithmetic; explicitly not a credit score |
| What-if simulator | Linear projection from measured averages | Read-only by construction; assumptions returned with every result |
| Spending analytics | Keyword categoriser + aggregation | Described as rules, not ML; manual corrections respected |
| Savings goals | Required contribution vs measured surplus | Says plainly when a target is not reachable |
| Adaptive security | Risk band → ALLOW / STEP_UP / HOLD / BLOCK | A real backend workflow, not a frontend modal |
