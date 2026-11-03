const pptxgen = require("pptxgenjs");
const { THEME } = require("./theme.js");
const { defineLayouts } = require("./layouts.js");
const { card, dot, stat, bullets } = require("./helpers.js");
const { applyTheme } = require("/root/.claude/skills/synced/cfd1e8ef-e01e-4491-a153-a6322a92c87e_75343b80-b3f3-4bbd-8c35-d8c501d575f9/pptx/scripts/apply_theme.js");

(async () => {
const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE";            // 13.3 x 7.5
pres.theme = { headFontFace: THEME.headFontFace, bodyFontFace: THEME.bodyFontFace };
pres.author = "Sujith Reddy P";
pres.title = "OBMS: Intelligent Online Banking Management System";
pres.subject = "CCA Phase-2, Building Enterprise Applications (BCS703B)";
const C = pres.SchemeColor;
defineLayouts(pres, C);

// ===================== 1. Title =====================
pres.addSection({ title: "Opening" });
let s = pres.addSlide({ masterName: "TITLE_DARK", sectionTitle: "Opening" });
s.addText("OBMS", { x: 0.7, y: 1.15, w: 6, h: 0.6, fontSize: 15, bold: true,
  color: THEME.colors.accent1, charSpacing: 3, isTextBox: true, margin: 0 });
s.addText("Intelligent Online Banking\nManagement System", { placeholder: "title" });
s.addText([
  { text: "CCA Phase-2  ·  Building Enterprise Applications (BCS703B)", options: { breakLine: true } },
  { text: "Information Science and Engineering  ·  2026-2027", options: { breakLine: true, fontSize: 14 } },
  { text: "\nSUJITH REDDY P  ·  USN [YOUR USN]        [TEAMMATE NAME]  ·  USN [TEAMMATE USN]",
    options: { breakLine: true, fontSize: 13, color: "AFBCD6" } },
  { text: "Under the guidance of [GUIDE NAME]", options: { fontSize: 13, color: "AFBCD6" } },
], { placeholder: "body" });
s.addNotes("Phase-1 was a design. Phase-2 is a working, tested, deployed system. Everything in this deck is a measured figure, not an estimate.");

// ===================== 2. Phase-1 to Phase-2 =====================
pres.addSection({ title: "From design to implementation" });
s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "From design to implementation" });
s.addText("From a Phase-1 design to a running system", { placeholder: "title" });
const cols = [
  { t: "Phase-1 delivered", items: ["Four-layer architecture on paper", "8 functional, 4 non-functional requirements", "A justified technology stack", "Five entities in the data layer"], fill: THEME.colors.lt2, accent: THEME.colors.accent6 },
  { t: "Phase-2 delivers", items: ["A deployed four-service application", "19 entities, 54 REST endpoints, 23 screens", "307 automated tests, 0 critical findings", "Verified against a live deployment"], fill: "E8F6F6", accent: THEME.colors.accent1 },
];
cols.forEach((c, i) => {
  const x = 0.6 + i * 6.3;
  card(s, C, { x, y: 1.55, w: 5.95, h: 3.5, name: "col" + i, fill: c.fill });
  s.addText(c.t, { x: x + 0.35, y: 1.8, w: 5.2, h: 0.4, fontSize: 17, bold: true,
    color: c.accent, isTextBox: true, margin: 0, fontFace: "Cambria" });
  s.addText(bullets(c.items), { x: x + 0.35, y: 2.35, w: 5.25, h: 2.5, fontSize: 13,
    color: C.text1, isTextBox: true, margin: 0 });
});
s.addText("The architecture did not change. The scope grew, and every growth is tested.", {
  x: 0.6, y: 5.35, w: 12.1, h: 0.5, fontSize: 14, italic: true, color: THEME.colors.accent6,
  isTextBox: true, margin: 0 });
s.addNotes("Be ready for: why does Phase-2 exceed Phase-1 scope? Answer: Phase-1 listed these as future work; the layered architecture and stack are unchanged.");

// ===================== 3. Architecture =====================
s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "From design to implementation" });
s.addText("The four layers, as built", { placeholder: "title" });
const layers = [
  ["Presentation", "React 19 · TypeScript · Vite · Tailwind", "23 screens, route guards by role", THEME.colors.accent1],
  ["Application", "Java 21 · Spring Boot 3.3 · Spring Security", "Controller to Service to Repository, 54 endpoints", THEME.colors.accent2],
  ["Data", "MySQL 8.4 · JPA / Hibernate", "20 tables, DECIMAL money, schema generated from the mappings", THEME.colors.dk2],
  ["Infrastructure", "Docker Compose · GitHub Actions", "Four services, four CI jobs on every push", THEME.colors.accent6],
];
layers.forEach((l, i) => {
  const y = 1.5 + i * 1.22;
  card(s, C, { x: 0.6, y, w: 12.1, h: 1.05, name: "layer" + i });
  dot(s, C, { x: 0.85, y: y + 0.22, d: 0.6, color: l[3], label: String(i + 1), name: "num" + i });
  s.addText(l[0], { x: 1.65, y: y + 0.13, w: 2.6, h: 0.42, fontSize: 16, bold: true,
    color: l[3], isTextBox: true, margin: 0, fontFace: "Cambria" });
  s.addText(l[1], { x: 1.65, y: y + 0.55, w: 4.3, h: 0.35, fontSize: 11,
    color: THEME.colors.accent6, isTextBox: true, margin: 0 });
  s.addText(l[2], { x: 6.2, y: y + 0.28, w: 6.25, h: 0.55, fontSize: 12.5,
    color: C.text1, isTextBox: true, margin: 0, valign: "middle" });
});
s.addNotes("A single layered application, not microservices. Phase-1 rejected microservices explicitly as disproportionate; the PDF reference design proposed them. Conflict resolved in favour of the report.");

// ===================== 4. Why not microservices =====================
s = pres.addSlide({ masterName: "CONTENT_TINT", sectionTitle: "From design to implementation" });
s.addText("One deliberate architectural decision", { placeholder: "title" });
card(s, C, { x: 0.6, y: 1.55, w: 5.9, h: 2.1, name: "rejected", fill: "FBEDEB" });
s.addText("Considered and rejected", { x: 0.95, y: 1.8, w: 5.2, h: 0.4, fontSize: 15, bold: true,
  color: THEME.colors.accent4, isTextBox: true, margin: 0, fontFace: "Cambria" });
s.addText(bullets(["Microservices per domain", "Kafka event backbone", "Kubernetes, multi-region failover"]),
  { x: 0.95, y: 2.28, w: 5.2, h: 1.25, fontSize: 13, color: C.text1, isTextBox: true, margin: 0 });
card(s, C, { x: 6.8, y: 1.55, w: 5.9, h: 2.1, name: "chosen", fill: "E8F6F6" });
s.addText("Chosen", { x: 7.15, y: 1.8, w: 5.2, h: 0.4, fontSize: 15, bold: true,
  color: THEME.colors.accent1, isTextBox: true, margin: 0, fontFace: "Cambria" });
s.addText(bullets(["One layered Spring Boot application", "A relational database with real transactions", "A single Python service, only for the model"]),
  { x: 7.15, y: 2.28, w: 5.2, h: 1.25, fontSize: 13, color: C.text1, isTextBox: true, margin: 0 });
s.addText("Why", { x: 0.6, y: 3.95, w: 12.1, h: 0.4, fontSize: 15, bold: true, color: C.text2,
  isTextBox: true, margin: 0, fontFace: "Cambria" });
s.addText(bullets([
  "A transfer must be atomic. One relational transaction gives that for free; across services it needs a saga and compensating writes.",
  "Six features built by two people in one semester do not benefit from independent deployability.",
  "Every one of the four required architecture layers is still exercised and defensible.",
]), { x: 0.6, y: 4.4, w: 12.1, h: 1.9, fontSize: 13.5, color: C.text1, isTextBox: true, margin: 0 });
s.addNotes("This is the slide to linger on if asked about scalability. The honest position: the design is right for the scope, and the report documents when you would revisit it.");

// ===================== 5. Core banking =====================
pres.addSection({ title: "What was built" });
s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "What was built" });
s.addText("Core banking, every requirement implemented", { placeholder: "title" });
const fr = [
  ["FR-1", "Registration and simulated KYC"],
  ["FR-2", "Two-factor login, OTP hashed and scoped"],
  ["FR-3", "Accounts and transaction history"],
  ["FR-4", "Internal fund transfer with limits"],
  ["FR-5", "Beneficiaries, verified before payable"],
  ["FR-6", "Bill payment against a biller catalogue"],
  ["FR-7", "PDF statements for any date range"],
  ["FR-8", "Notifications on every transaction"],
];
fr.forEach((f, i) => {
  const x = 0.6 + (i % 2) * 6.3;
  const y = 1.55 + Math.floor(i / 2) * 1.12;
  card(s, C, { x, y, w: 5.95, h: 0.95, name: "fr" + i });
  s.addText(f[0], { x: x + 0.28, y: y + 0.24, w: 0.95, h: 0.45, fontSize: 13, bold: true,
    color: THEME.colors.accent1, isTextBox: true, margin: 0, fontFace: "Cambria" });
  s.addText(f[1], { x: x + 1.3, y: y + 0.2, w: 4.5, h: 0.55, fontSize: 12.5, color: C.text1,
    isTextBox: true, margin: 0, valign: "middle" });
});
s.addText("All eight Phase-1 functional requirements are implemented and covered by tests.", {
  x: 0.6, y: 6.1, w: 12.1, h: 0.4, fontSize: 13, italic: true, color: THEME.colors.accent6,
  isTextBox: true, margin: 0 });

// ===================== 6. Intelligence modules =====================
s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "What was built" });
s.addText("Seven modules beyond the requirement", { placeholder: "title" });
const mods = [
  ["Fraud detection", "Isolation Forest, 4,000 samples, every score explained"],
  ["Financial Copilot", "Natural-language questions, answered from own records only"],
  ["Financial health score", "0-100 from five weighted indicators, formula shown"],
  ["What-if simulator", "Projections from real cash flow, no money moves"],
  ["Spending analytics", "Categorisation the customer can correct"],
  ["Savings goals", "Required monthly contribution from actual behaviour"],
  ["Adaptive security", "Verification demanded by risk, not by a fixed rule"],
];
mods.forEach((m, i) => {
  const y = 1.5 + i * 0.73;
  s.addShape("roundRect", { x: 0.6, y, w: 12.1, h: 0.62, rectRadius: 0.06,
    fill: { color: i % 2 ? C.background1 : C.background2 },
    line: { color: "E6ECF5", width: 0.75 }, objectName: "mod" + i });
  dot(s, C, { x: 0.82, y: y + 0.11, d: 0.4, color: THEME.colors.accent2, label: String(i + 1), name: "mdot" + i });
  s.addText(m[0], { x: 1.42, y: y + 0.08, w: 3.5, h: 0.45, fontSize: 13.5, bold: true,
    color: C.text2, isTextBox: true, margin: 0, valign: "middle" });
  s.addText(m[1], { x: 5.0, y: y + 0.08, w: 7.5, h: 0.45, fontSize: 12, color: C.text1,
    isTextBox: true, margin: 0, valign: "middle" });
});
s.addNotes("Be precise if asked which of these is machine learning: fraud detection is a trained Isolation Forest. Categorisation is a transparent rule set. Knowing the difference is the strong answer.");

// ===================== 7. The integrated workflow =====================
s = pres.addSlide({ masterName: "CONTENT_TINT", sectionTitle: "What was built" });
s.addText("The modules are wired to each other, not side by side", { placeholder: "title" });
const steps = [
  ["Customer\ninitiates", THEME.colors.accent2],
  ["Ownership\nand limits", THEME.colors.accent2],
  ["Risk\nscored", THEME.colors.accent1],
  ["Verification\nby risk", THEME.colors.accent3],
  ["Atomic\nsettlement", THEME.colors.accent5],
  ["Analytics\nupdated", THEME.colors.dk2],
];
steps.forEach((st, i) => {
  const x = 0.6 + i * 2.06;
  s.addShape("roundRect", { x, y: 2.1, w: 1.82, h: 1.5, rectRadius: 0.08,
    fill: { color: st[1] }, line: { width: 0 }, objectName: "step" + i });
  s.addText(st[0], { x, y: 2.1, w: 1.82, h: 1.5, align: "center", valign: "middle",
    fontSize: 12.5, bold: true, color: "FFFFFF", isTextBox: true, margin: 0 });
  if (i < steps.length - 1) {
    s.addShape("chevron", { x: x + 1.86, y: 2.68, w: 0.18, h: 0.34,
      fill: { color: "C3CCDC" }, line: { width: 0 }, objectName: "arrow" + i });
  }
});
s.addText("One customer action runs the whole chain", { x: 0.6, y: 4.0, w: 12.1, h: 0.4,
  fontSize: 15, bold: true, color: C.text2, isTextBox: true, margin: 0, fontFace: "Cambria" });
s.addText(bullets([
  "A low-risk transfer settles immediately. A medium-risk one demands a one-time code bound to that exact transfer.",
  "A high-risk one is held for an analyst, who can release or reject it; release re-checks the balance before settling.",
  "A critical one is refused outright, and the customer is told why in plain language.",
]), { x: 0.6, y: 4.45, w: 12.1, h: 1.85, fontSize: 13.5, color: C.text1, isTextBox: true, margin: 0 });
s.addNotes("This is the differentiator versus a CRUD banking project. Say it plainly: the fraud score changes what the transfer does next.");

// ===================== 8. Money safety =====================
s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "What was built" });
s.addText("How the money is kept correct", { placeholder: "title" });
const safety = [
  ["One writer", "LedgerService is the only component that changes a balance, so there is a single path to review."],
  ["Atomic double entry", "Debit and credit commit in one transaction. A half-applied transfer is not representable."],
  ["Ordered row locks", "Accounts are locked PESSIMISTIC_WRITE in ascending id order, which also prevents deadlock between opposing transfers."],
  ["DECIMAL, never float", "All money is BigDecimal mapped to DECIMAL(19,2). A CI test fails the build if any money column is floating point."],
  ["Idempotency keys", "Inserted under a unique constraint in the same transaction as the ledger write, so a retry cannot debit twice."],
];
safety.forEach((it, i) => {
  const y = 1.5 + i * 1.03;
  card(s, C, { x: 0.6, y, w: 12.1, h: 0.88, name: "safe" + i });
  s.addText(it[0], { x: 0.9, y: y + 0.1, w: 2.75, h: 0.68, fontSize: 13.5, bold: true,
    color: THEME.colors.accent1, isTextBox: true, margin: 0, valign: "middle", fontFace: "Cambria" });
  s.addText(it[1], { x: 3.8, y: y + 0.08, w: 8.6, h: 0.72, fontSize: 12, color: C.text1,
    isTextBox: true, margin: 0, valign: "middle" });
});

// ===================== 9. Fraud detection =====================
pres.addSection({ title: "Evidence" });
s = pres.addSlide({ masterName: "CONTENT_CHART", sectionTitle: "Evidence" });
s.addText("A score is useless unless it explains itself", { placeholder: "title" });
s.addChart([{ type: pres.ChartType.bar, data: [{
  name: "Score contribution",
  labels: ["Amount deviation", "Amount multiple", "Above personal max", "Payee spread", "Unusual hour", "Rapid transactions"],
  values: [30, 15, 14, 12, 10, 10],
}] }], {
  placeholder: "chart", barDir: "bar",
  showTitle: true, title: "Why one transfer scored 77 of 100", titleFontSize: 13,
  titleColor: THEME.colors.dk2, titleFontFace: "+mn-lt",
  chartColors: [THEME.colors.accent1],
  showValue: true, dataLabelPosition: "outEnd", dataLabelFontSize: 10,
  dataLabelColor: THEME.colors.dk2, dataLabelFontFace: "+mn-lt",
  catAxisLabelColor: THEME.colors.accent6, catAxisLabelFontSize: 10, catAxisLabelFontFace: "+mn-lt",
  valAxisLabelColor: THEME.colors.accent6, valAxisLabelFontSize: 10, valAxisLabelFontFace: "+mn-lt",
  valGridLine: { color: "EDF1F7", size: 1 }, catGridLine: { style: "none" },
  showLegend: false, valAxisMaxVal: 35,
});
s.addText([
  { text: "Isolation Forest, 4,000 synthetic samples\n", options: { bold: true, breakLine: true, color: THEME.colors.dk2 } },
  { text: "Observed live during testing:\n\n", options: { breakLine: true, fontSize: 12, color: THEME.colors.accent6 } },
  { text: "Routine transfer  →  score 0\n", options: { breakLine: true, bold: true, color: THEME.colors.accent5 } },
  { text: "₹2,500 at 2pm to an established payee\n\n", options: { breakLine: true, fontSize: 11.5, color: THEME.colors.accent6 } },
  { text: "Anomalous transfer  →  score 78\n", options: { breakLine: true, bold: true, color: THEME.colors.accent4 } },
  { text: "₹95,000 at 3am to a payee added minutes earlier\n\n", options: { breakLine: true, fontSize: 11.5, color: THEME.colors.accent6 } },
  { text: "The model never sees a name, an account number or an email address. Behaviour only.", options: { italic: true, fontSize: 12 } },
], { placeholder: "body" });
s.addNotes("The contributions shown are the real factors returned by the service for a 60,000 rupee transfer during the live test pass.");

// ===================== 10. Testing =====================
s = pres.addSlide({ masterName: "CONTENT_CHART", sectionTitle: "Evidence" });
s.addText("307 tests, every one passing", { placeholder: "title" });
s.addChart([{ type: pres.ChartType.doughnut, data: [{
  name: "Tests",
  labels: ["Backend unit (89)", "Backend API (68)", "Backend integration (49)", "AI service (66)", "Frontend (31)", "MySQL integration (4)"],
  values: [89, 68, 49, 66, 31, 4],
}] }], {
  placeholder: "chart", holeSize: 52,
  showTitle: true, title: "307 passing tests by kind", titleFontSize: 13,
  titleColor: THEME.colors.dk2, titleFontFace: "+mn-lt",
  chartColors: [THEME.colors.accent1, THEME.colors.accent2, THEME.colors.dk2,
    THEME.colors.accent5, THEME.colors.accent3, THEME.colors.accent6],
  showLegend: true, legendPos: "b", legendFontSize: 10, legendColor: THEME.colors.accent6,
  showValue: false, dataLabelFontFace: "+mn-lt",
});
s.addText([
  { text: "0 failures.  0 errors.  0 skipped.\n\n", options: { bold: true, breakLine: true, fontSize: 16, color: THEME.colors.accent5 } },
  { text: "What the tests actually assert:\n", options: { breakLine: true, bold: true, color: THEME.colors.dk2 } },
  ...bullets([
    "A failed transfer leaves no trace at all",
    "Balances never breach the minimum",
    "One customer cannot touch another's account",
    "A statement reconciles against the ledger",
    "Every money column is DECIMAL, checked against real MySQL",
    "The Java to Python request contract cannot drift",
  ]).map(b => ({ ...b, options: { ...b.options, fontSize: 12.5 } })),
], { placeholder: "body" });

// ===================== 11. Static analysis =====================
s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "Evidence" });
s.addText("Static analysis: zero critical findings", { placeholder: "title" });
[["0", "Critical bugs", THEME.colors.accent5],
 ["0", "Vulnerabilities", THEME.colors.accent5],
 ["40", "Advisory warnings", THEME.colors.accent3],
 ["4", "Analysers in CI", THEME.colors.accent1]].forEach((st, i) => {
  const x = 0.6 + i * 3.1;
  card(s, C, { x, y: 1.55, w: 2.85, h: 1.95, name: "sa" + i });
  stat(s, C, { x: x + 0.3, y: 1.75, w: 2.3, value: st[0], label: st[1], color: st[2] });
});
s.addText("The gate, and one defect it was hiding", { x: 0.6, y: 3.85, w: 12.1, h: 0.4,
  fontSize: 15, bold: true, color: C.text2, isTextBox: true, margin: 0, fontFace: "Cambria" });
s.addText(bullets([
  "SpotBugs and PMD on Java, Ruff on Python, the TypeScript compiler and ESLint on the frontend.",
  "The build fails on any SpotBugs finding of rank 1 to 4. The 40 remaining are rank 16 to 18 advisories, printed in full on every run rather than suppressed.",
  "The original gate counted findings with grep under a shell that aborts on a non-match, so it failed hardest when the report was clean. Nine CI runs failed silently before it was found.",
]), { x: 0.6, y: 4.3, w: 12.1, h: 2.0, fontSize: 13, color: C.text1, isTextBox: true, margin: 0 });

// ===================== 12. CI pipeline =====================
s = pres.addSlide({ masterName: "CONTENT_TINT", sectionTitle: "Evidence" });
s.addText("Every push runs the whole thing", { placeholder: "title" });
const jobs = [
  ["Backend", "Compile, 206 tests,\nSpotBugs, PMD", THEME.colors.accent2],
  ["MySQL", "Boots on MySQL 8.4,\ngenerates the schema", THEME.colors.dk2],
  ["AI service", "Ruff, 66 Pytest tests", THEME.colors.accent1],
  ["Frontend", "Typecheck, ESLint,\n31 tests, build", THEME.colors.accent5],
];
jobs.forEach((j, i) => {
  const x = 0.6 + i * 3.1;
  card(s, C, { x, y: 1.6, w: 2.85, h: 2.3, name: "job" + i, fill: C.background1 });
  s.addShape("roundRect", { x: x + 0.3, y: 1.85, w: 2.25, h: 0.42, rectRadius: 0.06,
    fill: { color: j[2] }, line: { width: 0 }, objectName: "jobtag" + i });
  s.addText(j[0], { x: x + 0.3, y: 1.85, w: 2.25, h: 0.42, align: "center", valign: "middle",
    fontSize: 13, bold: true, color: "FFFFFF", isTextBox: true, margin: 0 });
  s.addText(j[1], { x: x + 0.3, y: 2.45, w: 2.25, h: 1.2, fontSize: 11.5, color: C.text1,
    isTextBox: true, margin: 0, valign: "top" });
  s.addText("PASSING", { x: x + 0.3, y: 3.42, w: 2.25, h: 0.35, fontSize: 10, bold: true,
    color: THEME.colors.accent5, isTextBox: true, margin: 0, charSpacing: 1 });
});
s.addText(bullets([
  "The schema scripts in the repository are generated by CI from the JPA mappings, so they cannot drift from the code.",
  "The MySQL job is the only place the mappings meet a real database; every other backend test runs on in-memory H2.",
  "25 pipeline runs over the course of the phase; the final run is green on all four jobs.",
]), { x: 0.6, y: 4.3, w: 12.1, h: 2.0, fontSize: 13, color: C.text1, isTextBox: true, margin: 0 });

// ===================== 13. The six defects =====================
pres.addSection({ title: "What running it revealed" });
s = pres.addSlide({ masterName: "SECTION", sectionTitle: "What running it revealed" });
s.addText("307 passing tests did not mean it worked", { placeholder: "title" });
s.addText("Deploying the system and testing it by hand found six defects the suite could not reach", { placeholder: "body" });
s.addNotes("This is the most interesting slide in the deck. Do not rush it.");

s = pres.addSlide({ masterName: "CONTENT_TINT", sectionTitle: "What running it revealed" });
s.addText("Six defects, and why the tests missed them", { placeholder: "title" });
s.addTable([
  [{ text: "Defect", options: { bold: true, color: "FFFFFF", fill: { color: THEME.colors.dk2 }, fontSize: 11 } },
   { text: "Why 307 passing tests missed it", options: { bold: true, color: "FFFFFF", fill: { color: THEME.colors.dk2 }, fontSize: 11 } }],
  ["Crashed on first start-up", "An immutable role set; the demo seeder is disabled in tests, and registration avoids the failing path"],
  ["Brute-force lockout did nothing", "The counter was incremented, then the rejection rolled the transaction back and discarded it"],
  ["Held transfers could never complete", "A hold cannot be produced through the API in tests, because fraud scoring is disabled there"],
  ["Wrong date on customer references", "The server runs in UTC; the test compared against the server's own zone, and CI ran before the boundary"],
  ["Unknown URLs returned 500", "A catch-all handler relabelled Spring's own 404 as a server fault"],
  ["A password was logged at start-up", "Nothing asserted on what the application wrote to its log"],
], {
  x: 0.6, y: 1.5, w: 12.1, colW: [4.0, 8.1], border: { type: "solid", color: "DDE4EF", pt: 0.75 },
  fill: { color: C.background1 }, fontSize: 11.5, color: THEME.colors.dk2,
  rowH: 0.58, valign: "middle", autoPage: false,
});
s.addText("Four were invisible because the test profile disables the demo seeder and the fraud service. A fifth was hidden by the hour at which CI happened to run.", {
  x: 0.6, y: 5.9, w: 12.1, h: 0.75, fontSize: 13, italic: true, color: THEME.colors.dk2,
  isTextBox: true, margin: 0 });
s.addNotes("The lesson to state out loud: a green suite measures what it was pointed at. All six are now fixed and each has a regression test.");

// ===================== 14. Live verification =====================
s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "What running it revealed" });
s.addText("Access control, checked against the live system", { placeholder: "title" });
const checks = [
  ["A token issued after the password step but before the one-time code", "Refused by the whole API"],
  ["A valid customer token against staff, admin and analyst endpoints", "403 on all seven"],
  ["A valid analyst token against admin endpoints", "403 on all three"],
  ["An administrator reading a customer's account", "403 - admin rights are not account access"],
  ["Account identifiers walked with one customer's token", "Only that customer's own accounts returned"],
  ["Another customer's account given as the transfer source", "403 - ownership proved from the token"],
  ["The Copilot asked three times for another customer's data", "Only the caller's own figures, every time"],
];
checks.forEach((c, i) => {
  const y = 1.5 + i * 0.68;
  s.addShape("roundRect", { x: 0.6, y, w: 12.1, h: 0.58, rectRadius: 0.05,
    fill: { color: i % 2 ? C.background1 : C.background2 }, line: { color: "E6ECF5", width: 0.75 },
    objectName: "chk" + i });
  s.addText(c[0], { x: 0.85, y: y + 0.04, w: 7.3, h: 0.5, fontSize: 12, color: C.text1,
    isTextBox: true, margin: 0, valign: "middle" });
  s.addText(c[1], { x: 8.3, y: y + 0.04, w: 4.2, h: 0.5, fontSize: 12, bold: true,
    color: THEME.colors.accent5, isTextBox: true, margin: 0, valign: "middle" });
});
s.addText("A held transfer of ₹60,000 was released by an analyst and settled exactly once: 183,532 to 123,532. A repeated release moved no further money.", {
  x: 0.6, y: 6.3, w: 12.1, h: 0.42, fontSize: 12, italic: true, color: THEME.colors.accent6,
  isTextBox: true, margin: 0 });

// ===================== 15. Limitations =====================
pres.addSection({ title: "Closing" });
s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "Closing" });
s.addText("What this system is not", { placeholder: "title" });
const lims = [
  ["A simulated bank", "No connection to NEFT, RTGS, IMPS or UPI. No real money moves anywhere."],
  ["Not a credit score", "The financial health score is a wellness indicator, computed from the customer's own data and shown with its formula."],
  ["Two kinds of intelligence", "Fraud detection is a trained Isolation Forest. Spending categorisation is a transparent rule set. The project does not claim the second is machine learning."],
  ["Transfers stay inside the bank", "External payees are rejected at registration rather than accepted and failed later."],
  ["Known, unfixed", "Own-account transfers are risk-scored like external ones, so a small internal sweep can still ask for a code."],
];
lims.forEach((l, i) => {
  const y = 1.5 + i * 1.03;
  card(s, C, { x: 0.6, y, w: 12.1, h: 0.88, name: "lim" + i, fill: i === 4 ? "FDF3E6" : C.background2 });
  s.addText(l[0], { x: 0.9, y: y + 0.1, w: 3.1, h: 0.68, fontSize: 13, bold: true,
    color: i === 4 ? THEME.colors.accent3 : THEME.colors.dk2, isTextBox: true, margin: 0,
    valign: "middle", fontFace: "Cambria" });
  s.addText(l[1], { x: 4.15, y: y + 0.08, w: 8.25, h: 0.72, fontSize: 12, color: C.text1,
    isTextBox: true, margin: 0, valign: "middle" });
});
s.addNotes("Stating limits plainly reads as rigour. Volunteer these before an examiner has to ask.");

// ===================== 16. Closing =====================
s = pres.addSlide({ masterName: "TITLE_DARK", sectionTitle: "Closing" });
s.addText("THANK YOU  ·  QUESTIONS", { x: 0.7, y: 1.15, w: 8, h: 0.5, fontSize: 14, bold: true,
  color: THEME.colors.accent1, charSpacing: 2, isTextBox: true, margin: 0 });
s.addText("Phase-2, in four numbers", { placeholder: "title" });
[["307", "tests passing"], ["0", "critical findings"], ["6", "defects found by running it"], ["4", "CI jobs green"]]
  .forEach((st, i) => {
    const x = 0.75 + i * 3.1;
    s.addText(st[0], { x, y: 4.1, w: 2.7, h: 0.95, fontSize: 46, bold: true,
      color: THEME.colors.accent1, isTextBox: true, margin: 0, fontFace: "Cambria" });
    s.addText(st[1], { x, y: 5.08, w: 2.7, h: 0.5, fontSize: 12, color: "AFBCD6",
      isTextBox: true, margin: 0 });
  });
s.addText("github.com/stampedeShelby/obms-intelligent-banking", {
  x: 0.75, y: 6.1, w: 9, h: 0.4, fontSize: 13, color: THEME.colors.accent1, isTextBox: true, margin: 0 });


await pres.writeFile({ fileName: "OBMS_Phase2_Presentation.pptx" });
await applyTheme("OBMS_Phase2_Presentation.pptx", THEME);
console.log("deck written");
})();
