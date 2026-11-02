# OBMS — Intelligent Online Banking Management System

A layered, full-stack online banking application built for **Building Enterprise
Applications (BCS703B)**, CCA Phase-2. It implements the eight functional and four
non-functional requirements specified in our Phase-1 report, and adds a layer of
financial intelligence on top: explainable fraud detection, spending analytics, a
financial wellness score, a what-if simulator, savings goals and a financial copilot.

> **This is a simulated banking environment.** It connects to no real payment
> network, holds no real customer data, and moves no real funds. Every balance,
> transaction and biller in it is synthetic.

---

## Contents

- [What this is](#what-this-is)
- [Architecture](#architecture)
- [Technology stack](#technology-stack)
- [Repository layout](#repository-layout)
- [Running it](#running-it)
- [Demo credentials](#demo-credentials)
- [The integrated workflow](#the-integrated-workflow)
- [How the money is kept safe](#how-the-money-is-kept-safe)
- [Security controls](#security-controls)
- [The AI components, honestly described](#the-ai-components-honestly-described)
- [Testing](#testing)
- [Static analysis](#static-analysis)
- [API documentation](#api-documentation)
- [Known limitations](#known-limitations)

---

## What this is

Phase-1 designed a four-layer banking application and justified a deliberately
simple layered architecture over microservices. Phase-2 builds it.

The core banking half is conventional and correct: registration with simulated
KYC, two-factor sign-in, account and transaction viewing, internal transfers with
beneficiary management, bill payment, PDF statements and notifications.

The second half is what makes it more than a CRUD application. Rather than seven
features sitting beside each other, they are wired into one path:

```
customer initiates a transfer
    → ownership, balance and daily-limit checks
    → fraud scoring, from this customer's own behavioural history
    → risk-based authentication (allow / step-up / hold / block)
    → atomic settlement through the ledger
    → analytics, health score and copilot all read the new ledger rows
```

Each stage feeds the next. A blocked transfer leaves an explainable alert an
analyst can investigate; a settled one changes the spending breakdown, the
wellness score and what the copilot will tell you next time you ask.

---

## Architecture

Four layers, as the Phase-1 report specifies.

**Logical layer** — React screens call REST controllers, which delegate to
service classes, which use Spring Data repositories.

```
React screens → Controllers → Services → Repositories → MySQL
                                  ↓
                          AI service (FastAPI)
```

**Technical layer** — `Controller → Service → Repository` inside a single Spring
Boot application. One internal service-to-service call goes out over HTTP: the
Java backend asks the Python service to score a transaction. That boundary exists
because the model needs the Python ML ecosystem, and it is the only place the
application is not a single process.

**Data layer** — one relational schema, 19 entities, foreign keys and unique
constraints throughout. `DECIMAL(19,2)` for every monetary column; floating point
is never used for money.

**Infrastructure layer** — GitHub Actions builds, tests and statically analyses on
every push. Docker Compose brings up MySQL, the backend, the AI service and the
frontend together.

### Why not microservices

The Phase-1 report considered and rejected an event-driven microservices design,
and Phase-2 holds that line. For eight features built by a small team in one
semester, a single well-structured layered application is easier to build, reason
about, test and demonstrate, and it still exercises all four required layers. A
Kafka backbone would add operational complexity disproportionate to the scope.
The project idea document explored a microservices blueprint; that is noted there
as a direction if the system were ever taken to production scale.

---

## Technology stack

| Layer | Choice | Why |
|---|---|---|
| Frontend | React 18, TypeScript, Vite, Tailwind CSS, Recharts | Component model fits a fixed set of screens; TypeScript catches contract drift against the API at compile time |
| Backend | Java 21, Spring Boot 3.3, Spring Security, Spring Data JPA | `Controller → Service → Repository` maps directly onto the required logical layer; Spring Security provides the authentication and authorisation primitives FR-2 and NFR-1 need |
| Database | MySQL 8 | Transfers need strict consistency — a transfer must fully complete or not happen at all. A relational schema with foreign keys also matches the Phase-1 data-layer diagram |
| Tests | H2 in-memory | The suite runs with no database server, identically on a laptop and in CI. MySQL remains the deployment target |
| AI service | Python 3.12, FastAPI, scikit-learn | Isolation Forest and the surrounding numerical work belong in the Python ecosystem; this is the technical justification for introducing a second language |
| Auth | JWT (HS256), BCrypt (work factor 12) | Stateless tokens suit a SPA; BCrypt is the hash NFR-1 calls for |
| Statements | OpenPDF | Server-side PDF generation for FR-7 |
| API docs | springdoc-openapi | Live Swagger UI, used to capture the API test evidence |

---

## Repository layout

```
obms/
├── backend/                         # Spring Boot application
│   ├── src/main/java/com/obms/
│   │   ├── ObmsApplication.java
│   │   ├── config/                  # typed properties, OpenAPI, seeders
│   │   ├── common/                  # exceptions, error shape, reference generators
│   │   ├── controller/              # REST endpoints
│   │   ├── domain/                  # JPA entities
│   │   ├── dto/                     # request/response records
│   │   ├── repository/              # Spring Data interfaces
│   │   ├── security/                # JWT, filter chain, principal
│   │   └── service/                 # business logic
│   │       ├── copilot/             # intent detection and answer composition
│   │       └── fraud/               # scoring client and risk workflow
│   ├── src/main/resources/
│   │   └── application.yml          # mysql and h2 profiles
│   ├── src/test/java/com/obms/
│   │   ├── unit/                    # pure logic, no Spring context
│   │   ├── integration/             # real transactions against H2
│   │   ├── api/                     # MockMvc, full filter chain
│   │   └── support/                 # test data factory, token helper
│   ├── spotbugs-exclude.xml
│   └── pom.xml
├── ai-service/                      # FastAPI fraud scoring service
│   ├── app/
│   │   ├── main.py                  # endpoints
│   │   ├── model.py                 # Isolation Forest + rule layer
│   │   ├── features.py              # feature engineering
│   │   └── schemas.py               # request/response models
│   ├── tests/
│   └── requirements.txt
├── frontend/                        # React single-page application
│   ├── src/
│   │   ├── api/                     # typed HTTP client and endpoints
│   │   ├── components/              # layout and shared UI
│   │   ├── context/                 # auth and theme
│   │   ├── lib/                     # formatting helpers
│   │   └── pages/                   # 24 screens
│   └── package.json
├── database/                        # schema DDL and seed SQL
├── docs/                            # architecture notes, API reference, evidence
├── .github/workflows/ci.yml         # build, test and static analysis pipeline
├── docker-compose.yml
└── README.md
```

### Branching

- `main` — stable. Every commit here has passed CI.
- `dev` — integration branch for work in progress.

---

## Running it

### With Docker Compose (everything at once)

```bash
docker compose up --build
```

- Frontend — http://localhost:5173
- Backend — http://localhost:8080
- Swagger UI — http://localhost:8080/swagger-ui.html
- AI service — http://localhost:8000/docs

### Manually

**1. MySQL**

```bash
mysql -u root -p
CREATE DATABASE obms CHARACTER SET utf8mb4;
CREATE USER 'obms_user'@'localhost' IDENTIFIED BY 'obms_password';
GRANT ALL PRIVILEGES ON obms.* TO 'obms_user'@'localhost';
FLUSH PRIVILEGES;
```

**2. AI service**

```bash
cd ai-service
python -m venv .venv && source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

**3. Backend**

```bash
cd backend
# OBMS_DEMO seeds demo customers and six months of history.
# OBMS_OTP_DEBUG writes one-time codes to the log, since there is no SMTP server.
OBMS_DEMO=true OBMS_OTP_DEBUG=true mvn spring-boot:run
```

**4. Frontend**

```bash
cd frontend
npm install
npm run dev
```

### Environment variables

Copy `.env.example` and adjust. Nothing in the repository contains a real
credential; the development defaults are clearly marked as such in
`application.yml`.

| Variable | Default | Purpose |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | `localhost`, `3306`, `obms` | MySQL connection |
| `DB_USERNAME`, `DB_PASSWORD` | `obms_user`, `obms_password` | MySQL credentials |
| `OBMS_JWT_SECRET` | development default | JWT signing key; must be at least 32 bytes |
| `OBMS_DEMO` | `false` | Seed demo data |
| `OBMS_OTP_DEBUG` | `false` | Write one-time codes to the log. **Local demo only** |
| `AI_SERVICE_URL` | `http://localhost:8000` | Fraud scoring service |
| `SMTP_HOST`, `SMTP_PORT` | `localhost`, `1025` | Email delivery; simulated when absent |

---

## Demo credentials

Seeded when `OBMS_DEMO=true`. Password is the same for all: `Demo@Obms2026!`

| Email | Role | What it shows |
|---|---|---|
| `priya@demo.obms` | Customer | Six months of history, two accounts, a verified payee, goals, bills due |
| `arjun@demo.obms` | Customer | The counterparty for transfers |
| `kavya@demo.obms` | Customer | KYC pending, so transfers are locked |
| `staff@demo.obms` | Bank staff | KYC verification queue |
| `analyst@demo.obms` | Fraud analyst | Investigation panel with model explanations |
| `admin@demo.obms` | Admin | Audit log, account status, configuration |

Sign-in is two-factor. With no SMTP server running, start the backend with
`OBMS_OTP_DEBUG=true` and the code appears in the application log.

### Demonstration scenarios

| # | Scenario | How |
|---|---|---|
| 1 | Normal transfer | As Priya, send ₹2,000 to the verified payee. Settles immediately, LOW risk |
| 2 | Insufficient balance | Attempt more than the available balance. Refused before any scoring |
| 3 | Risk-based step-up | Send ₹40,000. Above the threshold, so a one-time code is required and no money moves until it is entered |
| 4 | High-risk transfer | Add a new payee, verify it, then immediately send a large amount. The model flags the fresh payee and the amount deviation |
| 5 | Fraud investigation | As the analyst, open the queue and read the per-feature explanation |
| 6 | Copilot | Ask "How much did I spend on food this month?" and check the figures against the Accounts page |
| 7 | Health score | Open Financial health and read the five-indicator breakdown |
| 8 | What-if | Simulate cutting shopping by 15% over 12 months |
| 9 | Savings goal | Create a goal with an unrealistic target and see the simulator say so |
| 10 | Statement | Download a PDF and check that opening + credits − debits = closing |

---

## The integrated workflow

The fraud module is not a separate screen. It sits in the transfer path:

1. The customer submits a transfer.
2. The backend checks ownership against the **token's** customer id, never the
   request body; then balance, account state and the daily limit.
3. `FraudService` assembles behavioural features from this customer's own debit
   history — mean, standard deviation, personal maximum, hour of day, payee age,
   recent velocity, payee spread, balance ratio.
4. Those features go to the Python service, which returns a 0–100 score with
   per-feature contributions.
5. The **Java side** maps the score to a band and decides: `ALLOW`, `STEP_UP`,
   `HOLD` or `BLOCK`. Keeping the decision in the banking layer means the bank can
   retune its risk appetite without redeploying the model, and means the safety
   behaviour survives the model being offline.
6. A `STEP_UP` issues a one-time code **bound to that specific pending transfer**,
   so a code cannot be used to authorise a different amount.
7. Settlement goes through `LedgerService`, the only component that changes a
   balance.
8. An explainable alert is written for every scored transaction, not just the
   suspicious ones.

If the AI service is unreachable, a documented fallback applies: small transfers
proceed, larger ones get a step-up challenge the customer can still complete.
Failing open on everything would make the control useless; failing closed on
everything would take the bank offline whenever the model restarts.

---

## How the money is kept safe

`LedgerService` is the single balance-mutating path in the application, which
makes the guarantee checkable — there is one code path to review.

**Atomicity.** `postDoubleEntry` runs in one transaction. Both the debit and the
credit, and both ledger rows, commit together or not at all. A half-applied
transfer is not representable.

**Concurrency.** Accounts are re-read under `PESSIMISTIC_WRITE` row locks inside
that transaction, so the balance used for the sufficiency check is the locked one.
Two concurrent debits cannot both pass the check. `@Version` on the account gives
optimistic locking as a second line of defence.

**Deadlock avoidance.** Locks are always taken in ascending account-id order.
Without a deterministic order, simultaneous A→B and B→A transfers would each hold
one lock and wait for the other.

**No negative balances.** A final guard rejects any computed balance below the
account's minimum, so even a caller that skipped validation cannot overdraw.

**Double-entry recording.** A transfer writes two rows sharing a
`transferGroupRef`, with `balanceAfter` captured at write time. A statement can
therefore show a running balance without recomputing history, and a discrepancy
can be traced to the exact row where it appeared.

**Duplicate prevention.** Money-moving requests carry an `Idempotency-Key`,
inserted under a unique constraint inside the same transaction as the ledger
write. A retried request cannot commit a second debit. A time-window heuristic on
amount and payee was rejected because it would also reject legitimate repeat
payments.

This is tested, not asserted: `LedgerAtomicityTest` runs twelve concurrent
threads against an account holding five times the transfer amount and verifies
that the balance never goes negative and that debits and credits balance exactly.

---

## Security controls

| Control | Implementation |
|---|---|
| Password storage | BCrypt, work factor 12 |
| Two-factor | OTP hashed with BCrypt before storage; never returned by any endpoint; single use; expires in 5 minutes; exhausted after 5 attempts; scoped to one purpose **and** one target operation |
| Token split | The password step yields a pre-auth token with no authorities, accepted only by the OTP endpoint. Skipping the second factor is not possible by replaying the first response |
| Account enumeration | Unknown username and wrong password return an identical code and message. `forgot-password` always returns 202 |
| Lockout | Five consecutive failures lock the account for fifteen minutes |
| Authorisation | Enforced twice: URL patterns in the filter chain, and `@PreAuthorize` on service methods. A future refactor that moves a method behind a different URL cannot silently widen access |
| Data isolation | Every query is scoped by the customer id taken from the verified token. No endpoint accepts a customer identifier from the request body |
| SQL injection | Spring Data parameterised queries throughout; no string-concatenated SQL |
| Error handling | Unexpected exceptions are logged server-side; clients get a generic message with no stack trace |
| Secrets | No credentials in the repository. Development defaults are explicitly marked and overridden by environment |
| Logging | No password, OTP, token or full document number is ever written to a log |
| PII minimisation | KYC identifiers stored masked to the last four characters; beneficiary account numbers masked in the UI; the AI service receives no identifying data at all |
| Audit trail | Append-only. Writes use `REQUIRES_NEW`, so an attempt that rolls back is still recorded. No endpoint updates or deletes a row |
| Token storage | The SPA holds the access token in memory, not `localStorage`, so an injected script cannot read it |

---

## The AI components, honestly described

The project specification said: do not claim AI where there is none. So:

**Fraud detection — a real trained model.** An Isolation Forest over nine
behavioural features, blended with an explainable rule layer. Both contributions
are reported separately in every response, so the blend does not have to be taken
on trust. The model layer is capped at 55 of 100 points, which means reaching the
CRITICAL band requires the rules to agree — an unexplainable score cannot block a
customer's money on its own. Scoring is deterministic, so an investigator
re-running an assessment sees exactly what the customer saw.

*Its limitation, stated plainly:* the forest is trained on **synthetic** behaviour
generated in `model.py`, not on real labelled fraud. It therefore learns "unusual
relative to a plausible population", not "fraudulent". It would need retraining on
real outcomes before any production use. The false-positive feedback the
application collects is stored for exactly that purpose rather than being applied
automatically — a customer dismissing an alert is a signal, not a licence to
weaken the control.

**Transaction categorisation — rules, and described as rules.** A deterministic
keyword classifier. It is not presented as machine learning. The requirement was
that categorisation be functional and explainable, and a rule the customer can
read and correct beats an opaque model they cannot. A manual correction is never
overwritten.

**Financial copilot — natural language over real analytics.** Intent detection
plus parameter extraction route a question to the same services that power the
analytics, health and simulator screens. Every figure is computed in Java from the
authenticated customer's own ledger, so the copilot cannot contradict the
dashboard and cannot invent a number. Each reply reports its generator honestly:
`rule_engine` when the deterministic engine answered. If a language model is wired
in later it is given the computed figures and asked only to phrase them — it is
never the source of a number.

**Health score and simulator — transparent arithmetic.** Five weighted indicators
summing to 100, each returning the figures it was computed from. The simulator
assumes no investment return, states every assumption in its response, and is
read-only by construction: it holds no repository that can write and calls no
ledger method, so it cannot move money.

---

## Testing

```bash
cd backend && mvn test            # 178 tests
cd ai-service && pytest -v        # 60 tests
cd frontend && npm run test       # 31 tests
```

**269 tests in total, all passing.** Every push runs all three suites in CI.

| Suite | Count | What it covers |
|---|---|---|
| Backend unit | 63 | Categorisation rules, copilot intent and parameter extraction, money invariants, OTP lifecycle, privacy masking, score banding |
| Backend integration | 33 | Ledger atomicity including a 12-thread concurrency test, transfer limits, idempotency, ownership, statement reconciliation, reference data |
| Backend API | 82 | MockMvc against the real filter chain: validation, 401/403 boundaries, cross-customer access prevention, forged tokens, the intelligence endpoints |
| AI service | 60 | Feature engineering edge cases, rule layer, score ordering across a risk gradient, determinism, API validation |
| Frontend | 31 | Formatting helpers, shared UI components, accessibility roles |

The integration tests deliberately do **not** run inside a surrounding
transaction. The usual `@Transactional` test pattern would roll everything back at
the end, but it would also mean the service's own transaction boundaries never
actually commit — and the behaviour under test *is* the transaction boundary.

### Two defects the suite caught

Worth recording, because they are the reason the tests exist.

1. **Fraud alert foreign key violation.** `recordAssessment` ran in
   `REQUIRES_NEW` while holding a foreign key to a ledger row created in the
   caller's uncommitted transaction, so the insert could not see it. Fixed by
   splitting into `recordAssessment` (`REQUIRED`, joins the ledger write so the key
   resolves and the two commit or roll back together) and `recordBlockedAttempt`
   (`REQUIRES_NEW`, no foreign key, so a refused attempt survives the rollback it
   describes).

2. **Amount extraction returned 500 for "5000".** The grouped-digits branch of the
   regex used `*` rather than `+`, so it matched the leading three digits instead
   of falling through to the plain-number branch. The copilot would have quietly
   answered a question about ₹5,000 using ₹500.

---

## Static analysis

```bash
cd backend
mvn spotbugs:spotbugs   # report at target/spotbugsXml.xml and target/spotbugs.html
mvn pmd:pmd pmd:cpd     # report at target/pmd.xml and target/cpd.xml
```

CI fails the build on any SpotBugs finding of rank 1–9, so the zero-critical
threshold is enforced by the pipeline rather than trusted to a human reading the
HTML. The frontend adds ESLint with `--max-warnings 0`, and the AI service adds
Ruff.

Every entry in `spotbugs-exclude.xml` is a justified suppression of a pattern that
is correct in a Spring application — mutable JPA entity state, Lombok-generated
methods — and each carries a comment saying why. Nothing in it suppresses a real
correctness or security finding.

---

## API documentation

With the backend running: **http://localhost:8080/swagger-ui.html**

The bearer scheme is declared, so Swagger UI can hold a token and exercise
protected endpoints. Full endpoint reference in [`docs/API.md`](docs/API.md).

---

## Known limitations

Stated rather than hidden.

1. **Transfers are internal only.** The Phase-1 report scopes transfers to
   accounts within the bank. NEFT, RTGS, IMPS and UPI are out of scope; a
   beneficiary that does not resolve to an OBMS account is rejected at
   registration rather than failing later at transfer time.
2. **The fraud model is trained on synthetic data.** See above.
3. **Email and SMS are simulated** unless SMTP credentials are configured. An
   in-app notification is always written, and the `dispatched` flag records which
   actually happened — the system never claims to have sent a message it did not.
4. **No token revocation.** Tokens are short-lived (30 minutes) instead. A
   revocation list would need a shared store, which the layered design avoids.
5. **No scheduled or standing payments.** Out of Phase-1 scope.
6. **The copilot has no language model attached by default.** It is a working
   natural-language interface over real analytics, and it says so in every reply
   rather than implying a model produced the prose.
7. **Refreshing the browser signs you out,** because the token is held in memory
   by design.
8. **Single instance.** Horizontal scaling would need the pessimistic locking
   strategy revisited for a read-replica topology.
9. **Not production-ready.** No PCI-DSS or ISO 27001 compliance work, no
   penetration test, no disaster recovery. This is a course project.

---

## Licence

Academic project, submitted for coursework. Not for production use.
