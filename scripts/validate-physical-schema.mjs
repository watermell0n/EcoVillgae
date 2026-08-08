import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const root = path.resolve(import.meta.dirname, "..");
const logicalPath = path.join(root, "docs", "DATA_MODEL.md");
const physicalPath = path.join(root, "docs", "PHYSICAL_SCHEMA.md");
const logical = fs.readFileSync(logicalPath, "utf8");
const physical = fs.readFileSync(physicalPath, "utf8");
const errors = [];

const logicalBlocks = [
  ...logical.matchAll(/```mermaid\s*\n([\s\S]*?)```/g),
].map((match) => match[1]);
const logicalEntities = new Set();
const relationPattern =
  /^\s*([A-Z][A-Z0-9_]*)\s+[|o{}.-]+--[|o{}.-]+\s+([A-Z][A-Z0-9_]*)\s*:/;
const declarationPattern = /^\s*([A-Z][A-Z0-9_]*)\s*\{$/;

for (const block of logicalBlocks) {
  for (const line of block.split(/\r?\n/)) {
    const relation = line.match(relationPattern);
    if (relation) {
      logicalEntities.add(relation[1].toLowerCase());
      logicalEntities.add(relation[2].toLowerCase());
    }
    const declaration = line.match(declarationPattern);
    if (declaration) logicalEntities.add(declaration[1].toLowerCase());
  }
}

const inventory = physical.match(
  /<!-- TABLES:START -->([\s\S]*?)<!-- TABLES:END -->/,
);
if (!inventory) {
  errors.push("Physical table inventory markers are missing");
}

const physicalTables = inventory
  ? [...inventory[1].matchAll(/^\| `([a-z][a-z0-9_]*)` \|/gm)].map(
      (match) => match[1],
    )
  : [];
const physicalSet = new Set(physicalTables);

if (physicalTables.length !== physicalSet.size) {
  const duplicates = physicalTables.filter(
    (table, index) => physicalTables.indexOf(table) !== index,
  );
  errors.push(`Duplicate physical tables: ${[...new Set(duplicates)].join(", ")}`);
}

const missing = [...logicalEntities].filter((table) => !physicalSet.has(table));
const extra = [...physicalSet].filter((table) => !logicalEntities.has(table));
if (missing.length > 0) {
  errors.push(`Logical entities without physical table: ${missing.join(", ")}`);
}
if (extra.length > 0) {
  errors.push(`Physical tables without logical entity: ${extra.join(", ")}`);
}

function validateSequentialIds(prefix, expectedCount) {
  const found = [
    ...physical.matchAll(new RegExp(`\\| (${prefix}-\\d{3}) \\|`, "g")),
  ].map((match) => match[1]);
  const expected = Array.from(
    { length: expectedCount },
    (_, index) => `${prefix}-${String(index + 1).padStart(3, "0")}`,
  );
  if (found.join(",") !== expected.join(",")) {
    errors.push(
      `${prefix} IDs must be sequential through ${expected.at(-1)}, found ${found.join(",")}`,
    );
  }
}

validateSequentialIds("UK", 21);
// 41 -> 43: CK-042(classrooms.class_number 범위)와 CK-043(loan_installments.status)이
// 문서에 추가됐는데 이 개수를 올리지 않아 검증이 실패하고 있었다.
validateSequentialIds("CK", 43);
validateSequentialIds("IX", 40);
validateSequentialIds("DBT", 22);

const requiredRules = [
  "UNIQUE (classroom_id, id)",
  "DEFERRABLE INITIALLY DEFERRED",
  "sum(ledger_entries.amount)=0",
  "balance_before+amount=balance_after",
  "recovery_of_group_id",
  "root_transaction_group_id",
  "policy_snapshots",
  "published_at IS NULL",
  "UUID v7",
  "DBT-019",
  "DBT-020",
];
for (const rule of requiredRules) {
  if (!physical.includes(rule)) {
    errors.push(`Required physical rule is missing: ${rule}`);
  }
}

const localLinks = [...physical.matchAll(/\[[^\]]+\]\(([^)]+)\)/g)]
  .map((match) => match[1])
  .filter((target) => !target.startsWith("http") && !target.startsWith("#"));
for (const target of localLinks) {
  const resolved = path.resolve(path.dirname(physicalPath), target);
  if (!fs.existsSync(resolved)) {
    errors.push(`Broken local link: ${target}`);
  }
}

if (errors.length > 0) {
  console.error("Physical schema validation failed:");
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log(
  `Physical schema validation PASS: ${physicalTables.length} tables, ` +
    "21 core uniqueness rules, 43 checks, 40 indexes, 22 DB tests",
);
