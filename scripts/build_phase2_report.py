"""Build the OBMS Phase-2 report from the supplied BEA template.

The template is reused as the base document so the heading styles, fonts and
page setup are the institute's own rather than reinvented. The title block is
kept and its placeholders filled; everything after it is replaced with the
project's actual Phase-2 content.
"""
from __future__ import annotations

import sys
from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Pt

TEMPLATE = sys.argv[1]
OUT = sys.argv[2]

TITLE = "OBMS: INTELLIGENT ONLINE BANKING MANAGEMENT SYSTEM"
REPO = "https://github.com/stampedeShelby/obms-intelligent-banking"
RUN_URL = REPO + "/actions"

doc = Document(TEMPLATE)
body = doc.element.body

# ---------------------------------------------------------------- title page
doc.paragraphs[4].runs[0].text = f"“{TITLE}”"
for r in doc.paragraphs[4].runs[1:]:
    r.text = ""

def set_text(par, text):
    if par.runs:
        par.runs[0].text = text
        for r in par.runs[1:]:
            r.text = ""
    else:
        par.add_run(text)

set_text(doc.paragraphs[8], "INFORMATION SCIENCE AND ENGINEERING")
set_text(doc.paragraphs[13], "SUJITH REDDY P\tUSN: [YOUR USN]")
set_text(doc.paragraphs[14], "[TEAMMATE NAME]\tUSN: [TEAMMATE USN]")
set_text(doc.paragraphs[19], "[GUIDE NAME]")

# Keep title-page elements (paragraphs 0..26); drop the rest of the body.
keep_until = doc.paragraphs[26]._p
dropping = False
for child in list(body):
    if child is keep_until:
        dropping = True
        continue
    if dropping and child.tag.endswith('}sectPr'):
        continue
    if dropping:
        body.remove(child)

# ---------------------------------------------------------------- helpers
def h1(text):
    doc.add_paragraph(text, style="Heading 1")

def h2(text):
    doc.add_paragraph(text, style="Heading 2")

def para(text, bold=False, italic=False, size=None):
    p = doc.add_paragraph()
    p.style = doc.styles["Body Text"]
    run = p.add_run(text)
    run.bold = bold
    run.italic = italic
    if size:
        run.font.size = Pt(size)
    return p

def bullets(items):
    for item in items:
        p = doc.add_paragraph(item, style="List Paragraph")
        p.paragraph_format.space_after = Pt(2)

def mono(block):
    for line in block.strip("\n").split("\n"):
        p = doc.add_paragraph()
        run = p.add_run(line if line else " ")
        run.font.name = "Consolas"
        run.font.size = Pt(8.5)
        pf = p.paragraph_format
        pf.space_after = Pt(0)
        pf.space_before = Pt(0)
        pf.line_spacing = 1.0

def table(headers, rows, widths=None):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    for i, head in enumerate(headers):
        cell = t.rows[0].cells[i]
        cell.text = ""
        run = cell.paragraphs[0].add_run(head)
        run.bold = True
        run.font.size = Pt(9)
    for row in rows:
        cells = t.add_row().cells
        for i, value in enumerate(row):
            cells[i].text = ""
            run = cells[i].paragraphs[0].add_run(str(value))
            run.font.size = Pt(8.5)
    return t

def placeholder(caption):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run(f"[ PASTE SCREENSHOT HERE — {caption} ]")
    run.bold = True
    run.font.size = Pt(9)
    p.paragraph_format.space_before = Pt(6)
    p.paragraph_format.space_after = Pt(6)

# ---------------------------------------------------------------- abstract
h1("ABSTRACT")
para(
    "This report documents the Phase-2 implementation of the Online Banking "
    "Management System (OBMS) designed in Phase-1. The four-layer design was "
    "realised as a working full-stack application in the domain of retail "
    "banking, built as a layered Spring Boot monolith following the "
    "Controller–Service–Repository structure and the technology "
    "justification recorded in the Phase-1 report, rather than the "
    "microservices and event-streaming blueprint considered and deliberately "
    "rejected there as disproportionate to a course-scale system. The "
    "repository is organised as three cooperating services: a Java 21 and "
    "Spring Boot 3.3 backend with Spring Security, JPA and MySQL 8; a React 19, "
    "TypeScript and Vite single-page frontend; and a Python FastAPI service "
    "carrying the scikit-learn Isolation Forest model used for explainable "
    "transaction anomaly detection. Static code analysis was executed with "
    "SpotBugs and PMD on the backend, Ruff on the AI service, and the "
    "TypeScript compiler with ESLint on the frontend; the backend analysis "
    "reports zero critical bugs and zero vulnerabilities, with 38 remaining "
    "low-severity advisory warnings, and three findings were fixed during the "
    "phase. Automated testing comprises 269 passing tests — 178 JUnit 5 "
    "unit, integration and MockMvc API tests on the backend, 60 Pytest tests "
    "on the AI service and 31 Vitest tests on the frontend — covering "
    "input validation, monetary calculation, transfer atomicity, "
    "authorisation boundaries and every principal API endpoint. All of this "
    "runs on a GitHub Actions pipeline, which completes successfully and "
    "demonstrates an operational system in which a customer can register, "
    "authenticate with two factors, transfer funds under risk-based "
    "verification, pay bills, download a PDF statement and view AI-generated "
    "financial insights."
)

# ------------------------------------------------- repository and structure
h1("REPOSITORY SETUP & CODE STRUCTURE")

h2("Repository Details")
table(
    ["Item", "Detail"],
    [
        ["Repository Link", REPO],
        ["Visibility", "Public"],
        ["Default Branch", "main — holds reviewed, CI-verified code only"],
        ["Branching Practice",
         "main is the stable integration branch. Feature work is developed on "
         "short-lived feature/* branches and merged after the CI workflow "
         "passes. Continuous integration runs on every push to main and on "
         "every pull request targeting it, so no change reaches main without "
         "a green build."],
        ["CI Evidence", RUN_URL],
    ],
)

h2("Project Directory Structure")
para("Separation of concerns across the three services and the four layers of the Phase-1 design:")
mono("""
obms-intelligent-banking/
|
+-- backend/                             Spring Boot application (application layer)
|   +-- src/main/java/com/obms/
|   |   +-- ObmsApplication.java         Entry point
|   |   +-- config/                      Typed properties, OpenAPI, reference + demo seeders
|   |   +-- security/                    JWT service, auth filter, principal, HTTP security policy
|   |   +-- domain/                      JPA entities (data layer) and domain enumerations
|   |   +-- repository/                  Spring Data repositories, incl. row-locking queries
|   |   +-- service/                     Business logic
|   |   |   +-- LedgerService.java       Sole writer of account balances; atomic double entry
|   |   |   +-- TransferService.java     Validation -> risk -> step-up auth -> settlement
|   |   |   +-- fraud/                   Risk assessment and AI-service client
|   |   |   +-- ...                      Auth, accounts, beneficiaries, bills, statements,
|   |   |                                analytics, health score, simulator, goals, copilot
|   |   +-- controller/                  REST controllers (9 classes, 54 endpoints)
|   |   +-- dto/                         Request/response records; entities never serialised
|   |   +-- common/                      Error model, global exception handler, reference IDs
|   +-- src/main/resources/
|   |   +-- application.yml              mysql and h2 profiles
|   +-- src/test/java/com/obms/
|   |   +-- unit/                        Service-level unit tests
|   |   +-- integration/                 Ledger atomicity, transfers, statement accuracy
|   |   +-- api/                         MockMvc API and authorisation tests
|   |   +-- support/                     Test base classes and data factory
|   +-- spotbugs-exclude.xml             Static-analysis exclusions, each justified
|   +-- pom.xml
|
+-- ai-service/                          FastAPI AI service
|   +-- app/
|   |   +-- main.py                      /fraud/score, /fraud/retrain, /health
|   |   +-- model.py                     Isolation Forest, training, explanation
|   |   +-- features.py                  Feature engineering
|   |   +-- schemas.py                   Pydantic request/response contracts
|   +-- tests/                           Pytest suite
|   +-- requirements.txt, ruff.toml
|
+-- frontend/                            React + TypeScript SPA (presentation layer)
|   +-- src/
|   |   +-- pages/                       23 screens
|   |   +-- components/                  Shared UI and chart components
|   |   +-- lib/                         API client, auth context, formatting
|   +-- package.json, vite.config.ts
|
+-- database/                            MySQL schema and seed scripts
+-- docs/                                ARCHITECTURE.md and this report
+-- scripts/                             CI analysis and test-summary scripts
+-- .github/workflows/ci.yml             Build, test and static-analysis pipeline
+-- docker-compose.yml                   MySQL + backend + AI service + frontend
+-- README.md                            Setup and run instructions
""")

h2("Execution & Setup Commands")
para("The whole stack, using Docker Compose:")
mono("""
cp .env.example .env
docker compose up --build
# Frontend  http://localhost:5173
# Backend   http://localhost:8080   Swagger UI at /swagger-ui.html
# AI service http://localhost:8000  OpenAPI at /docs
""")
para("Or each service individually:")
mono("""
# Backend (requires MySQL 8 running, or use the h2 profile)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=mysql

# Backend tests and static analysis
mvn test
mvn spotbugs:spotbugs pmd:pmd
python3 ../scripts/check_spotbugs.py target/spotbugsXml.xml

# AI service
cd ai-service
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
pytest -q

# Frontend
cd frontend
npm install
npm run dev
npm run typecheck && npm run lint && npm test && npm run build
""")

# ------------------------------------------------------- static code analysis
h1("STATIC CODE ANALYSIS")

h2("Tools Used")
table(
    ["Component", "Tool", "Purpose"],
    [
        ["Backend (Java)", "SpotBugs 4.8.6",
         "Bytecode defect detection: null dereferences, resource leaks, "
         "concurrency and correctness bugs"],
        ["Backend (Java)", "PMD 3.25 with CPD",
         "Source-level code smells and copy-paste duplication detection"],
        ["AI service (Python)", "Ruff", "Lint and static correctness checks"],
        ["Frontend (TypeScript)", "tsc --noEmit and ESLint",
         "Whole-project type checking and lint rules"],
    ],
)

h2("Quality Metrics Summary")
table(
    ["Metric", "Result"],
    [
        ["Critical Bugs", "0"],
        ["Vulnerabilities", "0"],
        ["Code Warnings", "38 (all low-severity advisory, SpotBugs bug rank 16–18)"],
        ["SpotBugs severity breakdown",
         "scariest (rank 1–4): 0   |   scary (5–9): 0   |   "
         "troubling (10–14): 0   |   of concern (15–20): 38"],
        ["PMD / CPD", "No blocking violations; build passes"],
        ["Ruff (AI service)", "All checks passed"],
        ["TypeScript + ESLint (frontend)", "No type errors, no lint errors"],
    ],
)
para(
    "The build gate fails on any SpotBugs finding of bug rank 1–4, which is "
    "the range SpotBugs labels “scariest” and which corresponds to the "
    "critical-bug criterion. The 38 remaining findings are rank 16–18 "
    "advisories, reported in full on every CI run rather than suppressed, so "
    "they stay visible for triage.",
    italic=True, size=9,
)

h2("Code Issues Resolved")
table(
    ["Detected", "File Name", "Fix / Refactoring Applied"],
    [
        ["Build gate failed silently whenever the report was clean "
         "(shell pipeline bug)",
         ".github/workflows/ci.yml",
         "The gate counted findings with grep piped into wc under bash -e with "
         "pipefail. grep exits non-zero when it matches nothing, so a clean "
         "report aborted the script before its first line of output and failed "
         "the build. Replaced with scripts/check_spotbugs.py, which parses the "
         "report with an XML parser, buckets findings by bug rank, prints every "
         "finding and exits non-zero only on rank 1–4. Verified against "
         "clean, failing and missing reports."],
        ["DLS_DEAD_LOCAL_STORE × 2 — dead store to local variable",
         "backend/.../config/DemoDataSeeder.java",
         "Local variables priyaCurrent and staffCustomer were assigned and never "
         "read. Confirmed first that neither was masking a missing association "
         "(createCustomer creates and links the UserAccount internally), then "
         "removed the variables and retained the calls for their side effects."],
        ["Redundant guard clause before a logging statement",
         "backend/.../config/DemoDataSeeder.java",
         "An early return restricted a debug log line to the ADMIN role for no "
         "reason, making seeding of other roles silent. Guard removed so all "
         "staff seeding is logged."],
        ["EI_EXPOSE_REP / EI_EXPOSE_REP2 × 86 (bug rank 18)",
         "backend/.../dto/, backend/.../controller/",
         "Java records whose components are lists. Suppressed for the dto and "
         "controller packages only, with the reasoning recorded in "
         "spotbugs-exclude.xml: these are immutable response envelopes, built "
         "in a service method and serialised immediately, and the lists they "
         "carry already come from Stream.toList() or List.of() and are "
         "unmodifiable. The exclusion does not extend to ledger, transfer or "
         "security code. Total findings fell from 88 to 38."],
    ],
)

h2("Verification Proof")
para(
    "The CI workflow prints the full SpotBugs severity table to the GitHub "
    "Actions job summary on every run, and the run below completed "
    "successfully with zero critical findings."
)
bullets([f"CI runs: {RUN_URL}"])
placeholder("GitHub Actions job summary showing the SpotBugs table with Critical = 0")

# ------------------------------------------------------- testing execution
h1("TESTING EXECUTION")

h2("Test Frameworks Used")
table(
    ["Component", "Framework", "Scope"],
    [
        ["Backend", "JUnit 5, Spring Boot Test, MockMvc, AssertJ",
         "Unit, integration and API-level tests against an H2 database"],
        ["AI service", "Pytest with FastAPI TestClient",
         "Feature engineering, model scoring, explanation output and API contracts"],
        ["Frontend", "Vitest with Testing Library",
         "Formatting utilities and UI component behaviour"],
    ],
)

h2("Test Suite Totals")
table(
    ["Suite", "Tests", "Failures", "Errors", "Skipped"],
    [
        ["Backend — unit (4 classes)", "83", "0", "0", "0"],
        ["Backend — integration (4 classes)", "33", "0", "0", "0"],
        ["Backend — API / security (3 classes)", "62", "0", "0", "0"],
        ["Backend subtotal", "178", "0", "0", "0"],
        ["AI service (Pytest)", "60", "0", "0", "0"],
        ["Frontend (Vitest)", "31", "0", "0", "0"],
        ["TOTAL", "269", "0", "0", "0"],
    ],
)

h2("Test Cases Table")
table(
    ["Test ID", "Test Level", "Function / Endpoint Tested", "Input / Action",
     "Expected Result", "Status"],
    [
        ["TC-01", "Unit", "AuthDtos registration validation — registerRejectsInvalidEmail",
         "Malformed email address", "400 with a field-level validation error", "Pass"],
        ["TC-02", "Unit", "Account.availableBalance — availableBalanceRespectsMinimum",
         "Balance 1,000 with minimum balance 500",
         "Returns 500 available, never a negative figure", "Pass"],
        ["TC-03", "Unit", "SavingsGoal.progressPercent — goalProgressCapped",
         "Saved amount above the target", "Progress capped at 100%, not 120%", "Pass"],
        ["TC-04", "Integration", "LedgerService.postDoubleEntry — transferMovesMoneyExactly",
         "Transfer of 5,000 between two accounts",
         "Source debited and destination credited by exactly 5,000; two ledger "
         "legs written under one group reference", "Pass"],
        ["TC-05", "Integration", "LedgerService — insufficientFundsLeavesNoTrace",
         "Transfer exceeding available balance",
         "Rejected; both balances unchanged and no ledger row written "
         "(transaction rolled back atomically)", "Pass"],
        ["TC-06", "Integration", "LedgerService — respectsMinimumBalance",
         "Transfer that would breach the minimum balance floor",
         "Rejected with INSUFFICIENT_FUNDS; balance never goes negative", "Pass"],
        ["TC-07", "Integration", "TransferService — largeTransferRequiresVerification",
         "Transfer at or above the step-up threshold",
         "Outcome VERIFICATION_REQUIRED with a pending reference; no money moved "
         "until the OTP is confirmed", "Pass"],
        ["TC-08", "Integration", "TransferService — wrongOtpRejected",
         "Confirmation with an incorrect OTP",
         "401 INVALID_OTP; attempt counted and transfer not settled", "Pass"],
        ["TC-09", "API", "POST /api/auth/login — loginRequiresSecondFactor",
         "Valid username and password",
         "200 with otpRequired true and a pre-auth token that carries no "
         "authority of its own", "Pass"],
        ["TC-10", "API", "POST /api/auth/register — registerRejectsWeakPassword",
         "Password failing the complexity policy", "400 Bad Request", "Pass"],
        ["TC-11", "API", "GET /api/accounts — protectedEndpointsRequireAuthentication",
         "Request with no bearer token", "401 Unauthorized", "Pass"],
        ["TC-12", "API", "GET /api/admin/** — customerCannotReachAdmin",
         "Authenticated CUSTOMER token against an admin endpoint",
         "403 Forbidden, enforced server-side by role", "Pass"],
        ["TC-13", "API", "POST /api/transfers — cannotSendFromAnotherCustomersAccount",
         "Another customer's account id in the request body",
         "403 NOT_OWNED; ownership is proved against the token, not the payload", "Pass"],
        ["TC-14", "API", "POST /api/auth/login — forgedTokenRejected",
         "JWT with a tampered payload and invalid signature", "401 Unauthorized", "Pass"],
    ],
)

h2("Execution Proof")
para(
    "Every push runs the full suite on GitHub Actions and publishes a test "
    "summary table to the run page. The most recent run reports 178 backend "
    "tests, 60 AI-service tests and 31 frontend tests, with zero failures and "
    "zero errors."
)
bullets([f"CI runs: {RUN_URL}"])
placeholder("Terminal or GitHub Actions output showing 'Tests run: 178, Failures: 0, Errors: 0'")
placeholder("Pytest output showing '60 passed' and Vitest output showing '31 passed'")

# ------------------------------------------------------- core implementation
h1("CORE IMPLEMENTATION")

h2("Module 1 — User Interface / Presentation Layer")
para(
    "A React 19 single-page application in TypeScript, built with Vite and "
    "styled with Tailwind CSS, with Recharts for financial charts. "
    "23 screens are implemented, protected by route guards that respect the "
    "role claims on the session token:"
)
bullets([
    "Public: landing page, login, registration, OTP verification, forgot password",
    "Banking: dashboard, accounts, transaction history, fund transfer, "
    "beneficiary management, bill payment, statements, notifications",
    "Intelligence: AI Financial Copilot, fraud alerts, financial health score, "
    "what-if simulator, spending analytics, savings goals",
    "Administration: admin dashboard, KYC verification queue, fraud "
    "investigation panel, audit log viewer",
    "Account: profile and settings",
])
placeholder("Customer dashboard screen")
placeholder("Fund transfer screen showing the risk-based verification step")

h2("Module 2 — Core Business Logic / Processing")
para(
    "The central operation is the internal fund transfer, and it is "
    "implemented as one integrated path rather than as independent features. "
    "A single customer action runs through ownership and account-state checks, "
    "available-balance and daily-limit validation, behavioural risk scoring, "
    "risk-based authentication, atomic settlement and notification:"
)
bullets([
    "Ownership is proved against the authenticated token, never against an "
    "identifier in the request body, which closes the usual insecure-direct-"
    "object-reference gap.",
    "LedgerService is the only component in the system that writes an account "
    "balance. It posts double-entry debit and credit legs inside one database "
    "transaction, so a half-applied transfer cannot be represented.",
    "Accounts are locked with PESSIMISTIC_WRITE row locks acquired in "
    "ascending account-id order, which both prevents two concurrent debits "
    "from passing the same sufficiency check and avoids the deadlock that "
    "simultaneous A-to-B and B-to-A transfers would otherwise cause. An "
    "optimistic-lock version column is the second line of defence.",
    "All money is handled as BigDecimal mapped to DECIMAL(19,2); no "
    "floating-point type is used for a monetary value anywhere.",
    "The risk score returned by the AI service is mapped to one of four "
    "decisions — allow, step up, hold, or block. A step-up issues an OTP "
    "bound to that specific pending transfer, so a code obtained for a small "
    "transfer cannot authorise a large one.",
    "Client-supplied idempotency keys are inserted under a unique constraint "
    "in the same transaction as the ledger write, so a retried request cannot "
    "produce a second debit.",
])
placeholder("Fraud alert detail showing the per-factor explanation of a risk score")

h2("Module 3 — Data Persistence & Integration")
para(
    "Data is persisted in MySQL 8 through Spring Data JPA and Hibernate, with "
    "the relational schema derived from the Phase-1 data-layer design and "
    "extended for the intelligence modules. The automated test suite runs the "
    "same code against an in-memory H2 database under a separate Spring "
    "profile, so behaviour is verified on every build without requiring a "
    "database server."
)
bullets([
    "Entities: customers, users, roles, KYC records, accounts, transactions, "
    "beneficiaries, billers, bill payments, pending transfers, OTP tokens, "
    "notifications, savings goals, fraud alerts, financial score snapshots, "
    "copilot conversations and messages, idempotency records and audit logs.",
    "Integrity: primary and foreign keys, unique constraints on account "
    "numbers, customer email and mobile, and transaction references; indexes "
    "on the columns the ledger and analytics queries filter by.",
    "Security of stored data: passwords and OTP codes are stored only as "
    "BCrypt hashes (work factor 12); KYC identifiers are reduced to a masked "
    "tail before persistence; no password, OTP or token value is written to a "
    "log.",
    "The audit log is append-only and its writes run in a separate "
    "transaction, so the record of an attempted operation survives the "
    "rollback of the operation itself.",
    "External integration: the backend calls the FastAPI AI service over REST "
    "for transaction scoring and copilot answers. The request carries "
    "behavioural features only — no names, account numbers or email "
    "addresses. If the service is unreachable within its timeout, the backend "
    "applies a documented conservative fallback rather than failing open.",
])
placeholder("Swagger UI listing the REST API, or a MySQL client showing the schema")

# ------------------------------------------------------- rubric
h1("EVALUATION RUBRICS")
para("Construction Readiness, Testing and Implementation")
para("(60 MARKS Scaled Down to 10 marks)")
table(
    ["S.No", "Evaluation Criteria", "Excellent", "Good", "Needs Improvement",
     "Max Marks", "Marks Obtained"],
    [
        ["1.", "Repo Setup & Code Structure",
         "Well-organized GitHub/GitLab repo setup with modular folder organization. (9-10 marks)",
         "Fairly organized repository with minor structural flaws. (6-8 marks)",
         "Disorganized repo; lacks clear modular structure. (0-5 marks)", "10", ""],
        ["2.", "Static Code Analysis",
         "Tool executed; zero critical bugs; report submitted. (13-15 marks)",
         "Tool executed; minor code bugs present. (9-12 marks)",
         "Static analysis not performed or results ignored. (0-8 marks)", "15", ""],
        ["3.", "Testing Execution",
         "Working Unit and API/Integration tests with passing logs (17-20 marks)",
         "Working unit tests, but limited integration test coverage. (11-16 marks)",
         "Missing unit/integration tests or execution errors. (0-10 marks)", "20", ""],
        ["4.", "Implementation",
         "Functional implementation of core modules matching architectural design. (13-15 marks)",
         "Partial implementation with minor functional bugs or gaps (9-12 marks)",
         "Incomplete or non-functional module implementation. (0-8 marks)", "15", ""],
        ["", "TOTAL MARKS", "", "", "", "60", ""],
    ],
)
doc.add_paragraph()
para("Signature of Course Co-Ordinator")

# ------------------------------------------------------- references
h1("REFERENCES/BIBLIOGRAPHY")
refs = [
    "Reserve Bank of India, “Master Direction on Digital Payment Security "
    "Controls,” RBI, 2021.",
    "OWASP Foundation, “OWASP Top Ten.” [Online]. Available: "
    "https://owasp.org/Top10/",
    "M. Fowler, Patterns of Enterprise Application Architecture. Boston, MA: "
    "Addison-Wesley, 2002.",
    "Spring Team, “Spring Boot Reference Documentation.” [Online]. "
    "Available: https://spring.io/projects/spring-boot",
    "Spring Team, “Spring Security Reference.” [Online]. Available: "
    "https://docs.spring.io/spring-security/reference/",
    "Oracle Corporation, “MySQL 8.0 Reference Manual.” [Online]. "
    "Available: https://dev.mysql.com/doc/refman/8.0/en/",
    "F. T. Liu, K. M. Ting and Z. Zhou, “Isolation Forest,” in Proc. "
    "8th IEEE Int. Conf. on Data Mining (ICDM), Pisa, Italy, 2008, pp. 413–422.",
    "F. Pedregosa et al., “Scikit-learn: Machine Learning in Python,” "
    "Journal of Machine Learning Research, vol. 12, pp. 2825–2830, 2011.",
    "S. Ramirez, “FastAPI Documentation.” [Online]. Available: "
    "https://fastapi.tiangolo.com/",
    "SpotBugs Team, “SpotBugs Documentation.” [Online]. Available: "
    "https://spotbugs.readthedocs.io/",
    "Meta Open Source, “React Documentation.” [Online]. Available: "
    "https://react.dev/",
    "M. T. Nygard, Release It! Design and Deploy Production-Ready Software, "
    "2nd ed. Raleigh, NC: Pragmatic Bookshelf, 2018.",
]
for i, ref in enumerate(refs, start=1):
    p = doc.add_paragraph()
    p.style = doc.styles["Body Text"]
    run = p.add_run(f"[{i}]\t{ref}")
    run.font.size = Pt(9.5)
    p.paragraph_format.space_after = Pt(3)

doc.save(OUT)
print("saved:", OUT)
print("paragraphs:", len(doc.paragraphs), "tables:", len(doc.tables))
