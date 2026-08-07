import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const root = path.resolve(import.meta.dirname, "..");
const modelPath = path.join(root, "docs", "DATA_MODEL.md");
const source = fs.readFileSync(modelPath, "utf8");
const errors = [];

const requiredEntities = [
  "CLASSROOMS",
  "CLASSROOM_TEACHERS",
  "CLASSROOM_STUDENTS",
  "CLASSROOM_MODULES",
  "CLASSROOM_POLICY_VERSIONS",
  "SCHOOL_AI_SETTING_VERSIONS",
  "AUTONOMOUS_SCHEDULE_MODULES",
  "LEDGER_ACCOUNTS",
  "TRANSACTION_GROUPS",
  "LEDGER_ENTRIES",
  "POLICY_SNAPSHOTS",
  "IDEMPOTENCY_RECORDS",
  "STATE_TRANSITIONS",
  "OUTBOX_EVENTS",
  "STORE_ORDERS",
  "INVENTORY_ITEMS",
  "TRADES",
  "FINANCIAL_PRODUCT_VERSIONS",
  "DEPOSIT_ACCOUNTS",
  "SAVINGS_ACCOUNTS",
  "INVESTMENT_MARKETS",
  "INVESTMENT_ORDERS",
  "LOANS",
  "CREDIT_PROFILES",
  "ACTIVITY_SESSIONS",
  "QUIZ_QUESTION_VERSIONS",
  "QUIZ_SESSIONS",
  "QUIZ_RESULT_VERSIONS",
  "VOTES",
  "VOTE_BALLOTS",
  "AI_RUNS",
  "AUDIT_EVENTS",
];

const mermaidBlocks = [
  ...source.matchAll(/```mermaid\s*\n([\s\S]*?)```/g),
].map((match) => match[1]);

// 6 -> 7: 부동산 자리·임대·매매·청약 도메인(9장)이 자기 ER 다이어그램을 갖는다.
if (mermaidBlocks.length !== 7) {
  errors.push(`ER diagram count must be 7, found ${mermaidBlocks.length}`);
}

const declared = new Set();
const referenced = new Set();
const relationPattern =
  /^\s*([A-Z][A-Z0-9_]*)\s+[|o{}.-]+--[|o{}.-]+\s+([A-Z][A-Z0-9_]*)\s*:/;
const declarationPattern = /^\s*([A-Z][A-Z0-9_]*)\s*\{$/;

for (const block of mermaidBlocks) {
  if (!block.trimStart().startsWith("erDiagram")) {
    errors.push("Every Mermaid block must be an erDiagram");
  }

  for (const line of block.split(/\r?\n/)) {
    const declaration = line.match(declarationPattern);
    if (declaration) {
      declared.add(declaration[1]);
    }

    const relation = line.match(relationPattern);
    if (relation) {
      referenced.add(relation[1]);
      referenced.add(relation[2]);
    }
  }
}

for (const entity of requiredEntities) {
  if (!declared.has(entity) && !referenced.has(entity)) {
    errors.push(`Required entity is missing: ${entity}`);
  }
}

const decisionIds = [...source.matchAll(/\| (DM-\d{3}) \|/g)].map(
  (match) => match[1],
);
const expectedDecisions = Array.from(
  { length: 14 },
  (_, index) => `DM-${String(index + 1).padStart(3, "0")}`,
);
if (decisionIds.join(",") !== expectedDecisions.join(",")) {
  errors.push(
    `Decision IDs must be sequential DM-001..DM-014, found ${decisionIds.join(",")}`,
  );
}

const requiredRules = [
  "sum(ledger_entries.amount) = 0",
  "balance_before + amount = balance_after",
  "COMMITTED",
  "recovery_of_group_id",
  "root_transaction_group_id",
  "policy_snapshot_sources",
  "outbox_events",
  "idempotency_key",
  "state_version",
];
for (const rule of requiredRules) {
  if (!source.includes(rule)) {
    errors.push(`Required invariant text is missing: ${rule}`);
  }
}

const markdownLinks = [...source.matchAll(/\[[^\]]+\]\(([^)]+)\)/g)]
  .map((match) => match[1])
  .filter((target) => !target.startsWith("http") && !target.startsWith("#"));
for (const target of markdownLinks) {
  const resolved = path.resolve(path.dirname(modelPath), target);
  if (!fs.existsSync(resolved)) {
    errors.push(`Broken local link: ${target}`);
  }
}

if (errors.length > 0) {
  console.error("Data model validation failed:");
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log(
  `Data model validation PASS: ${mermaidBlocks.length} ER diagrams, ` +
    `${new Set([...declared, ...referenced]).size} entities, ` +
    `${decisionIds.length} decisions, ${markdownLinks.length} local links`,
);
