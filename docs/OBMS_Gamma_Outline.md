# OBMS: Intelligent Online Banking Management System

Phase-2 Project — Building Enterprise Applications (BCS703B)
B.E. Information Science and Engineering, BMSIT&M, VTU · 2026–27
Repository: github.com/stampedeShelby/obms-intelligent-banking

---

## The Problem

Online banking tells you *what* happened, never *why*.

- A transfer is blocked and the customer is told only that it was "flagged for security reasons"
- Fraud models are opaque: a single risk number with nothing behind it
- Analysts inherit alerts with no stated reason, so every review starts from zero
- Customers get spending charts but no answer to "can I afford this?"

Opacity is the product gap. OBMS closes it.

---

## What OBMS Is

A working three-tier banking application where every automated decision states its reasoning.

- Full retail banking: accounts, transfers, beneficiaries, bill payments, PDF statements
- Fraud detection that reports the contribution of each feature to the score
- A financial copilot that answers in plain English from the customer's own transactions
- Deployed as four containers under Docker Compose; one command brings the stack up

Simulated environment: no real payment network, no real customer data, no real funds.

---

## Architecture

Three tiers, each independently testable.

- **Presentation** — React 19 + TypeScript + Vite + Tailwind; 23 screens, Recharts for charts
- **Application** — Spring Boot 3.3.5 on Java 21; 61 REST endpoints across 9 controllers
- **Intelligence** — FastAPI on Python 3.12 with a scikit-learn Isolation Forest
- **Persistence** — MySQL 8.4 via Spring Data JPA; 19 entities mapped to 20 tables

The backend calls the AI service over REST with behavioural features only — no names, account numbers or email addresses leave the banking tier.

---

## Explainable Fraud Detection

The core innovation: a score that can defend itself.

- A rule layer contributes points with a plain-language reason for each
- An Isolation Forest scores the full feature combination, mapped to 0–55 points
- The blend is the larger layer plus 40% of the smaller, capped at 100 — not a sum, because both layers read the same features and adding them would count one signal twice
- Every alert carries its per-factor breakdown to both the customer and the analyst

Worked example: rules 57 + model 31 → score 69, displayed with all four contributing factors.

---

## Risk-Based Authentication

The score drives the action, and the action is proportionate.

- **Allow** — settles immediately
- **Step up** — a one-time code bound to that specific pending transfer, so a code issued for a small transfer cannot authorise a large one
- **Hold** — queued for analyst review; no money moves
- **Block** — refused outright

Thresholds: medium 40, high 65, critical 85.

---

## Money Safety

The ledger is the part that must never be wrong.

- `LedgerService` is the only component in the system that writes an account balance
- Double-entry debit and credit legs post inside one transaction, so a half-applied transfer cannot exist
- Row locks are taken in ascending account-id order, preventing both concurrent over-debit and the deadlock that simultaneous A→B and B→A transfers would cause
- All money is `BigDecimal` on `DECIMAL(19,2)` — no floating-point value anywhere
- Idempotency keys under a unique constraint in the same transaction as the ledger write, so a retried request cannot debit twice

---

## Security Posture

- Two-factor authentication; the pre-auth token is rejected by every business endpoint
- Ownership proved from the session token, never from an identifier in the request body
- Passwords and OTP codes stored only as BCrypt hashes (work factor 12)
- KYC identifiers masked to a tail before persistence
- The client holds its token in memory only, never in localStorage — a refresh signs the user out by design
- Append-only audit log written in a separate transaction, so the record of an attempt survives the rollback of the attempt

---

## Static Code Analysis

| Metric | Result |
|---|---|
| Critical bugs | 0 |
| Vulnerabilities | 0 |
| Code warnings | 40, all bug rank 16–18 |

Tools: SpotBugs 4.8.6.6, PMD 3.25.0 with CPD, Ruff 0.9.4, tsc + ESLint.
The build fails on any finding of bug rank 1–4. All 40 remaining advisories are printed on every run rather than suppressed.

---

## Testing — 307 Tests, Zero Failures

| Suite | Tests |
|---|---|
| Backend unit | 89 |
| Backend integration (H2) | 49 |
| Backend API / security | 68 |
| Backend integration (real MySQL 8.4) | 4 |
| AI service (Pytest) | 66 |
| Frontend (Vitest) | 31 |
| **Total** | **307** |

Four CI jobs run on every push. A contract test reads field names out of the Java client and asserts the Python service accepts exactly those, so a rename on either side fails the build instead of silently degrading to the fallback path.

---

## What Running It Revealed

Six real defects, none of which the passing test suite could see.

- An immutable role set crashed the application at startup
- Brute-force lockout was inoperative: the counter incremented, then the rollback erased it
- Held transfers had no resolution path — the demo surfaced this, not the tests
- Customer-facing references carried the server's timezone date, not the bank's
- Unmatched paths returned 500 instead of 404
- A development credential was written to the startup log

Four were hidden because the test profile disables the seeder and the fraud service; a fifth depended on the hour CI happened to run.

---

## Manual Verification Against the Running System

Checks performed against the live stack, not against mocks.

- 8 unauthenticated endpoints correctly refused
- A pre-auth token rejected by the API — two-factor cannot be bypassed
- Identical responses for known and unknown usernames: no account enumeration
- Customer, analyst and admin each refused the others' endpoints
- Account ids 1–24 walked; only the caller's own accounts returned
- A released ₹60,000 hold settled exactly once; repeating the release returned the same reference
- The copilot, probed with injection-style prompts, returned only the caller's own figures

---

## Engineering Discipline

Verification that holds up because the tooling itself was verified.

- The SpotBugs gate silently passed a failing build for nine runs — a shell pipeline bug — and was replaced with an XML parser
- The schema was never committed because the guard used `git diff`, which cannot see untracked files
- Generated SQL is normalised so regeneration creates no churn commits
- `database/schema.sql` is generated from the JPA mappings on every run, so it cannot drift from the code

---

## Results

- 307 automated tests, 0 failures, across four CI jobs
- 0 critical static-analysis findings against 100 Java classes, 41 TypeScript files and 5 Python modules
- 61 REST endpoints, 23 screens, 20 relational tables
- Six defects found by running the system, each fixed and covered by a regression test
- One command — `docker compose up` — brings the full stack up

---

## Conclusion

OBMS shows that explainability is an architectural property, not a feature bolted on at the end.

- The reason travels with the decision, from the model through the API to both the customer and the analyst
- Safety properties are enforced in one place each — one ledger writer, one source of business dates, one authorisation path
- Testing a banking system means running it: the suite was green while six real defects were live

**Future work:** real payment-network integration, model retraining on production traffic, analyst release/reject controls in the UI, and per-customer risk thresholds.
