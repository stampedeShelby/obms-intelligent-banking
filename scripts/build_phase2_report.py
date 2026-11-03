"""Build the OBMS Phase-2 report from the supplied BEA template.

The template is reused as the base document so the heading styles, fonts and
page setup are the institute's own rather than reinvented. The title block is
kept and its placeholders filled; everything after it is replaced with the
project's actual Phase-2 content.
"""
from __future__ import annotations

import os
import sys
from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Inches, Pt
from PIL import Image

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

SHOTS = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                     "..", "docs", "screenshots")
_fig = [0]

def placeholder(caption, image=None):
    """Insert the screenshot if it has been captured, else leave a slot."""
    path = os.path.join(SHOTS, image) if image else None
    _fig[0] += 1
    if path and os.path.exists(path):
        with Image.open(path) as im:
            ratio = im.height / im.width
        width = Inches(6.0)
        if ratio * 6.0 > 7.2:                     # keep a tall shot on one page
            width = Inches(7.2 / ratio)
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.add_run().add_picture(path, width=width)
        p.paragraph_format.space_before = Pt(6)
        cap = doc.add_paragraph()
        cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
        run = cap.add_run(f"Fig. {_fig[0]} — {caption}")
        run.italic = True
        run.font.size = Pt(8.5)
        cap.paragraph_format.space_after = Pt(10)
        return
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run(f"[ PASTE SCREENSHOT HERE — Fig. {_fig[0]}: {caption} ]")
    run.bold = True
    run.font.size = Pt(9)
    p.paragraph_format.space_before = Pt(6)
    p.paragraph_format.space_after = Pt(6)

# ---------------------------------------------------------------- abstract
h1("ABSTRACT")
para(
    "This report documents the Phase-2 implementation of the Online Banking "
    "Management System (OBMS) designed in Phase-1. The design was "
    "realised as a working full-stack application in retail "
    "banking, built as a layered Spring Boot monolith following the "
    "Controller–Service–Repository structure and the technology "
    "justification recorded in the Phase-1 report, rather than the "
    "microservices blueprint deliberately rejected there as "
    "disproportionate to a course-scale system. The "
    "repository is organised as three cooperating services: a Java 21 and "
    "Spring Boot 3.3 backend with Spring Security, JPA and MySQL 8; a React 19, "
    "TypeScript and Vite single-page frontend; and a Python FastAPI service "
    "carrying the scikit-learn Isolation Forest model used for explainable "
    "transaction anomaly detection. Static analysis was run with "
    "SpotBugs and PMD on the backend, Ruff on the AI service, and the "
    "TypeScript compiler with ESLint on the frontend; the backend analysis "
    "reports zero critical bugs and zero vulnerabilities, with 40 remaining "
    "low-severity advisory warnings. Automated testing comprises 307 passing tests — 210 JUnit 5 "
    "unit, integration and MockMvc API tests on the backend, 66 Pytest tests "
    "on the AI service and 31 Vitest tests on the frontend — covering "
    "input validation, monetary calculation, transfer atomicity, "
    "authorisation boundaries and every principal API endpoint. Four backend "
    "tests run against a real MySQL 8.4 server, verifying the JPA mappings "
    "and generating the schema scripts; six pin the HTTP contract with the "
    "Python service. "
    "A manual pass against the deployed stack then found and corrected six "
    "defects the automated suite had not reached, including an inoperative "
    "brute-force lockout. All of this runs on a four-job GitHub Actions "
    "pipeline, which completes "
    "successfully and "
    "demonstrates a system in which a customer can register, "
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
        ["CI Jobs",
         "Four, all passing: backend (compile, test, SpotBugs, PMD); MySQL "
         "integration and schema generation; AI service (Ruff, Pytest); "
         "frontend (TypeScript typecheck, ESLint, Vitest, production build)"],
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
        ["Backend (Java)", "SpotBugs 4.8.6.6",
         "Bytecode defect detection: null dereferences, resource leaks, "
         "concurrency and correctness bugs"],
        ["Backend (Java)", "PMD 3.25.0 with CPD",
         "Source-level code smells and copy-paste duplication detection"],
        ["AI service (Python)", "Ruff", "Lint and static correctness checks"],
        ["Frontend (TypeScript)", "tsc --noEmit and ESLint",
         "Whole-project type checking and lint rules"],
    ],
)

h2("Scope of Analysis")
para(
    "The figures below describe what the analysers actually traversed, so the "
    "zero-critical result is read against a known volume of code rather than "
    "an unstated one."
)
table(
    ["Measure", "Count"],
    [
        ["Java source classes (main)", "100"],
        ["Java classes instrumented by JaCoCo", "158"],
        ["Java test classes", "21"],
        ["JPA entity classes", "19"],
        ["Relational tables in the generated schema", "20"],
        ["REST endpoints across 9 controllers", "61"],
        ["React screens (TypeScript)", "23"],
        ["TypeScript / TSX source files", "41"],
        ["Python modules in the AI service", "5"],
        ["Frontend modules transformed by the production build", "2,295"],
    ],
)

h2("Quality Metrics Summary")
table(
    ["Metric", "Result"],
    [
        ["Critical Bugs", "0"],
        ["Vulnerabilities", "0"],
        ["Code Warnings", "40 (all low-severity advisory, SpotBugs bug rank 16–18)"],
        ["SpotBugs severity breakdown",
         "scariest (rank 1–4): 0   |   scary (5–9): 0   |   "
         "troubling (10–14): 0   |   of concern (15–20): 40"],
        ["PMD / CPD", "No blocking violations; build passes"],
        ["Ruff (AI service)", "All checks passed"],
        ["TypeScript + ESLint (frontend)", "No type errors, no lint errors"],
    ],
)
para(
    "The build gate fails on any SpotBugs finding of bug rank 1–4, which is "
    "the range SpotBugs labels “scariest” and which corresponds to the "
    "critical-bug criterion. The 40 remaining findings are rank 16–18 "
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
        ["Generated schema was never committed: the guard used `git diff`, "
         "which does not detect untracked files",
         ".github/workflows/ci.yml",
         "The schema-generation job dumped schema.sql and seed.sql correctly "
         "(466 lines, 20 tables) but its commit step reported 'Schema "
         "unchanged' and exited, because on the first run both files were "
         "untracked and `git diff` compares only tracked files. Now stages "
         "first and compares against the index. Verified in a scratch "
         "repository that new files are detected and that a second run with "
         "identical content still commits nothing, so regeneration creates no "
         "churn commits."],
        ["EI_EXPOSE_REP / EI_EXPOSE_REP2 × 86 (bug rank 18)",
         "backend/.../dto/, backend/.../controller/",
         "Java records whose components are lists. Suppressed for the dto and "
         "controller packages only, with the reasoning recorded in "
         "spotbugs-exclude.xml: these are immutable response envelopes, built "
         "in a service method and serialised immediately, and the lists they "
         "carry already come from Stream.toList() or List.of() and are "
         "unmodifiable. The exclusion does not extend to ledger, transfer or "
         "security code. Total findings fell from 88 to 38; the LoginAttemptService added later in the cycle contributes two more of the same rank-18 advisory, so the current run reports 40."],
    ],
)

h2("Verification Proof")
para(
    "The CI workflow prints the full SpotBugs severity table to the GitHub "
    "Actions job summary on every run, and the run below completed "
    "successfully with zero critical findings."
)
bullets([f"CI runs: {RUN_URL}"])
placeholder("SpotBugs severity table and build-gate verdict for the backend: zero findings of bug rank 1-4",
            "S1_spotbugs_zero_critical.png")

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
        ["Backend — unit (5 classes)", "89", "0", "0", "0"],
        ["Backend — integration on H2 (8 classes)", "49", "0", "0", "0"],
        ["Backend — API / security (4 classes)", "68", "0", "0", "0"],
        ["Backend — integration on real MySQL 8.4 (1 class)", "4", "0", "0", "0"],
        ["Backend subtotal", "210", "0", "0", "0"],
        ["AI service (Pytest)", "66", "0", "0", "0"],
        ["Frontend (Vitest)", "31", "0", "0", "0"],
        ["TOTAL", "307", "0", "0", "0"],
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
        ["TC-15", "Integration", "MySqlSchemaIT — contextStartsAgainstMySql",
         "Boot the application against a real MySQL 8.4 server",
         "Context starts and the connection reports MySQL, proving the JPA "
         "mappings work against the deployment database and not only H2", "Pass"],
        ["TC-16", "Integration", "MySqlSchemaIT — monetaryColumnsAreDecimal",
         "Inspect JDBC metadata for all ten monetary columns",
         "Every column is DECIMAL(19,2); a BigDecimal field mapped to a "
         "floating-point column would fail here", "Pass"],
        ["TC-17", "API", "POST /api/v1/fraud/score — Java/Python contract guard",
         "Post the exact camelCase payload the Spring Boot client sends, with "
         "field names read from the Java record",
         "200 OK with all 13 fields accepted, confirming Jackson's camelCase "
         "output matches the service's Pydantic aliases", "Pass"],
        ["TC-18", "API", "POST /api/v1/fraud/score — anomaly discrimination",
         "Routine transfer (2,500 at 2pm, established payee) versus anomalous "
         "one (95,000 at 3am, new payee, 5 debits in the hour)",
         "Risk score rises from 0 to 78 with a populated primary reason and "
         "per-factor explanations, not a bare number", "Pass"],
        ["TC-19", "Integration", "LoginLockoutTest — correctPasswordRefusedWhileLocked",
         "Five wrong passwords, then the correct one",
         "Rejected while the lockout stands; the failure counter commits even "
         "though the login throws", "Pass"],
        ["TC-20", "Integration", "HeldTransferResolutionTest — releaseSettlesTheTransfer",
         "An analyst releases a transfer held by fraud controls",
         "Settles exactly once, source debited and destination credited by the "
         "held amount, alert closed as a false positive", "Pass"],
        ["TC-21", "Integration", "HeldTransferResolutionTest — releaseRefusesWhenFundsNoLongerAvailable",
         "The account is drawn down while the transfer sits under review",
         "Release refused; the account is not overdrawn and the transfer stays "
         "pending", "Pass"],
        ["TC-22", "API", "ErrorMappingApiTest — unmatchedPathIsNotFound",
         "GET on an API path with no handler", "404, not 500", "Pass"],
        ["TC-23", "Integration", "StartupHygieneTest — noInMemoryUserDetailsManager",
         "Inspect the application context after start-up",
         "No auto-configured in-memory account exists, so no credential is "
         "written to the log", "Pass"],
        ["TC-24", "Unit", "ReferencesTest — transactionReferenceUsesBankDate",
         "Generate a transaction reference",
         "Carries the bank's own date, not the server's timezone date", "Pass"],
    ],
)

h2("Execution Proof")
para(
    "Every push runs the full suite on GitHub Actions and publishes a test "
    "summary table to the run page. The most recent run reports 210 backend "
    "tests (206 on H2 plus 4 against a real MySQL 8.4 server), 66 AI-service "
    "tests and 31 frontend tests, with zero failures and "
    "zero errors."
)
bullets([f"CI runs: {RUN_URL}"])
placeholder("Backend suite on GitHub Actions: 206 tests, no failures, errors or skips",
            "S2_backend_206_tests.png")
placeholder("AI-service suite (66 Pytest cases) and frontend suite (31 Vitest cases), both green",
            "S3_ai_66_frontend_31.png")

# ------------------------------------------------------- core implementation
h2("Manual Verification Against the Running System")
para(
    "Beyond the automated suite, the deployed stack was exercised directly "
    "through a browser against the Docker Compose deployment, driving the "
    "REST API with real sessions for a customer, a fraud analyst and an "
    "administrator. This is reported separately because it found defects the "
    "automated tests did not, and the reason it could is instructive: each "
    "defect sat in a place the test configuration happened to exclude."
)
table(
    ["#", "Defect found in the running system", "Why 279 passing tests missed it", "Resolution"],
    [
        ["1", "Backend crashed on first start-up with "
              "UnsupportedOperationException during demo seeding",
         "Role sets were built with Set.of(...), which Hibernate cannot clear "
         "during a merge. The demo seeder is disabled under the test profile, "
         "and registration takes the persist path rather than merge, so no "
         "test ever saved an existing user with roles.",
         "Mutable HashSet at all three assignment sites; RoleAssignmentTest "
         "reproduces the merge path inside one transaction."],
        ["2", "Brute-force lockout was entirely inoperative: six wrong "
              "passwords followed by the correct one signed in normally",
         "AuthService.login is @Transactional; it incremented the failure "
         "counter and then threw to reject the attempt, so the transaction "
         "rolled back and discarded the increment. The counter stayed at zero "
         "permanently, so the threshold was never reached.",
         "Counter moved to LoginAttemptService running in REQUIRES_NEW, so the "
         "record of an attempt commits independently of the rejection. Five "
         "regression tests."],
        ["3", "A transfer held by fraud controls could never be completed; "
              "clearing the alert as a false positive left the money unmoved "
              "and the customer uninformed",
         "No code path settled a held transfer: review() touched only the "
         "alert row, and confirm() accepts only AWAITING_VERIFICATION while a "
         "hold sits at PENDING. A hold cannot be produced through the API "
         "under the test profile, because fraud scoring is disabled there.",
         "Analyst Release and Reject endpoints that re-validate balance and "
         "limits before settling, are idempotent, and notify the customer. "
         "Six tests, verified end to end in the running system."],
        ["4", "Customer-visible transaction references carried the wrong date "
              "between midnight and 05:30 IST",
         "References were stamped with LocalDate.now() in the server's zone; "
         "the container runs in UTC. A statement test that should have caught "
         "the mismatch was passing only because CI happened to run before the "
         "timezone boundary each day.",
         "All business dates routed through a single BankTime source of truth; "
         "ReferencesTest guards the formats."],
        ["5", "Any unmatched API path returned 500 INTERNAL_ERROR instead of "
              "404",
         "The catch-all @ExceptionHandler(Exception.class) intercepted the "
         "exceptions Spring raises for an unmatched path, an unsupported "
         "method and an unreadable body, relabelling client mistakes as "
         "server faults.",
         "Explicit handlers for 404, 405 and 400; ErrorMappingApiTest also "
         "asserts no error body leaks a stack trace."],
        ["6", "A generated credential was written to the application log on "
              "every start-up",
         "With no UserDetailsService bean, Spring Boot auto-configures an "
         "in-memory account and prints its password. Not reachable, since "
         "neither HTTP Basic nor form login is enabled, but it should not "
         "exist in a banking application.",
         "UserDetailsServiceAutoConfiguration excluded; StartupHygieneTest "
         "asserts no in-memory user manager is registered."],
    ],
)
para(
    "The common thread is worth stating plainly: a green suite measures what "
    "it was pointed at. Four of these six defects were invisible to the "
    "automated tests because the test profile disables the demo seeder and "
    "the fraud service, and a fifth was hidden by the time of day CI ran. "
    "Running the assembled system was what exposed them.",
    italic=True, size=9,
)

h2("Verification of Access Control in the Running System")
para(
    "The same pass checked authorisation against live sessions rather than "
    "mocks. All results below were observed against the deployed application."
)
table(
    ["Check", "Result"],
    [["Protected endpoints called with no token (8 endpoints)", "401 on all"],
     ["A pre-authentication token (issued after the password step but before "
      "the one-time code) used against the API",
      "401 — the second factor cannot be bypassed by replaying the "
      "first response"],
     ["Forged and malformed bearer tokens", "401"],
     ["Wrong password versus unknown username", "Byte-identical responses, so "
      "accounts cannot be enumerated"],
     ["Password reset for a registered versus unregistered address",
      "Identical responses"],
     ["A valid CUSTOMER token against 7 staff, admin and analyst endpoints",
      "403 on all"],
     ["A valid FRAUD_ANALYST token against admin and staff endpoints",
      "403 on all; the investigation endpoints returned 200"],
     ["An ADMIN token reading a customer's account",
      "403 — administrative rights do not confer access to account data"],
     ["Account identifiers 1–24 walked with one customer's token",
      "Only that customer's own two accounts returned; no other record "
      "disclosed"],
     ["Another customer's account supplied as the transfer source",
      "403 — ownership is proved against the token, never the request body"],
     ["Seven invalid transfers (negative, zero, three decimals, same account, "
      "above balance, no destination)", "All rejected with specific codes"],
     ["A transfer above the verification threshold",
      "Held for review at risk 77/100 with per-factor explanations; both "
      "balances unchanged until an analyst released it"],
     ["Release of the held transfer",
      "Settled exactly once: 183,532.00 to 123,532.00 on the source and "
      "42,000.00 to 102,000.00 on the destination. A repeated release "
      "returned the same reference and moved no further money"],
     ["AI Copilot asked for another customer's balance, for all customers' "
      "balances, and given an instruction-style prompt",
      "Every answer contained only the authenticated customer's own figures"],
     ["Statement download", "A valid PDF of 26,993 bytes"],
     ["Audit trail after the analyst decisions",
      "82 entries, with transfer.hold.released and transfer.hold.rejected "
      "both attributed to analyst@demo.obms"]],
)
placeholder("Fraud investigation queue: a held transfer awaiting analyst review, with its risk band, pending reference and the model that scored it",
            "S4_fraud_queue.png")

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
placeholder("Customer dashboard: balances, month-to-date activity and the financial health score",
            "S5_customer_dashboard.png")
placeholder("Risk-based authentication: a medium-risk transfer requires a one-time code before any money moves",
            "S6_transfer_stepup.png")

h2("Module 2 — Core Business Logic / Processing")
para(
    "The server exposes 61 REST endpoints across nine controllers. The central operation is the internal fund transfer, and it is "
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
    "The risk score is a blend, not a sum: the larger of the rule and model "
    "contributions plus 40 percent of the smaller, capped at 100. Rules and "
    "the Isolation Forest read the same features, so adding them outright "
    "would count one signal twice. A transfer whose rule factors total 57 and "
    "whose model contributes 31 therefore scores 69, which is what the "
    "explanation screen displays alongside the individual factors.",
    "Client-supplied idempotency keys are inserted under a unique constraint "
    "in the same transaction as the ledger write, so a retried request cannot "
    "produce a second debit.",
])
placeholder("A held transfer with the per-factor explanation behind its risk score, shown to the customer",
            "S7_fraud_explanation.png")

h2("Module 3 — Data Persistence & Integration")
para(
    "Data is persisted in MySQL 8 through Spring Data JPA and Hibernate, with "
    "the relational schema derived from the Phase-1 data-layer design and "
    "extended for the intelligence modules. The automated test suite runs the "
    "same code against an in-memory H2 database under a separate Spring "
    "profile, so behaviour is verified on every build without requiring a "
    "database server. A dedicated CI job additionally boots the application "
    "against a real MySQL 8.4 server, which is what produces "
    "database/schema.sql and database/seed.sql: those files are generated from "
    "the JPA mappings on every run rather than written by hand, so they cannot "
    "drift from the code."
)
bullets([
    "19 JPA entity classes map to 20 relational tables, emitted as a 466-line database/schema.sql. Entities: customers, users, roles, KYC records, accounts, transactions, "
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
    "applies a documented conservative fallback rather than failing open. "
    "The request format is pinned by a contract test that reads the field "
    "names out of the Java client and asserts the Python service accepts "
    "exactly those, so a rename on either side fails the build instead of "
    "silently sending every transaction down the fallback path.",
])
placeholder("Swagger UI: the generated OpenAPI description of the REST interface",
            "S8_swagger_api.png")

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
