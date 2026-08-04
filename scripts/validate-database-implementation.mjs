import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const migrationDirectory = path.join(
  root,
  "backend",
  "src",
  "main",
  "resources",
  "db",
  "migration",
);
const testFile = path.join(
  root,
  "backend",
  "src",
  "test",
  "java",
  "com",
  "b201",
  "ecovillage",
  "SchemaIntegrityTest.java",
);
const physicalSchemaFile = path.join(root, "docs", "PHYSICAL_SCHEMA.md");

const expectedMigrations = [
  "V1_1__identity_classroom.sql",
  "V1_2__ledger.sql",
  "V1_3__event_audit.sql",
  "V1_4__store_trade.sql",
  "V1_5__finance.sql",
  "V1_6__activity_quiz_vote.sql",
  "V1_7__ai_report.sql",
  "V1_8__constraints_triggers.sql",
  "V1_9__indexes.sql",
  "V1_10__classroom_identity_and_public_quiz.sql",
  "V1_11__mvp_schema_alignment.sql",
  "V1_12__seed_course_recommendations.sql",
  "V1_13__activity_public_fields.sql",
  "V1_14__ai_v1_2_execution.sql",
  "V1_15__quiz_result_workflow.sql",
  "V1_16__student_character_profiles_and_equipment.sql",
  "V1_17__basic_character_catalog.sql",
  "V1_18__teacher_local_credentials.sql",
  "V1_19__canonical_module_catalog.sql",
  "V1_20__investment_modes_and_historical_reference.sql",
  "V1_21__classroom_display_settings.sql",
];

function unique(values) {
  return [...new Set(values)];
}

function difference(left, right) {
  const rightSet = new Set(right);
  return left.filter((value) => !rightSet.has(value));
}

function duplicates(values) {
  const counts = new Map();
  for (const value of values) {
    counts.set(value, (counts.get(value) ?? 0) + 1);
  }
  return [...counts.entries()]
    .filter(([, count]) => count > 1)
    .map(([value]) => value);
}

const errors = [];
const migrationFiles = fs
  .readdirSync(migrationDirectory)
  .filter((file) => /^V.+\.sql$/.test(file))
  .sort((left, right) => left.localeCompare(right, undefined, { numeric: true }));

if (JSON.stringify(migrationFiles) !== JSON.stringify(expectedMigrations)) {
  errors.push(
    `migration files differ\nexpected=${expectedMigrations.join(",")}\nactual=${migrationFiles.join(",")}`,
  );
}

const sql = migrationFiles
  .map((file) => fs.readFileSync(path.join(migrationDirectory, file), "utf8"))
  .join("\n");
const physicalSchema = fs.readFileSync(physicalSchemaFile, "utf8");
const testSource = fs.readFileSync(testFile, "utf8");

const tableSection = physicalSchema.match(
  /<!-- TABLES:START -->([\s\S]*?)<!-- TABLES:END -->/,
)?.[1];
if (!tableSection) {
  errors.push("physical schema table markers are missing");
}

const documentedTables = unique(
  [...(tableSection ?? "").matchAll(/^\| `([a-z][a-z0-9_]*)` \|/gm)].map(
    (match) => match[1],
  ),
);
const implementedTables = [...sql.matchAll(/^CREATE TABLE ([a-z][a-z0-9_]*) \(/gm)].map(
  (match) => match[1],
);

for (const table of difference(documentedTables, implementedTables)) {
  errors.push(`documented table is not implemented: ${table}`);
}
for (const table of difference(implementedTables, documentedTables)) {
  errors.push(`implemented table is not documented: ${table}`);
}
for (const table of duplicates(implementedTables)) {
  errors.push(`table is created more than once: ${table}`);
}
if (implementedTables.length !== 88) {
  errors.push(`expected 88 tables, found ${implementedTables.length}`);
}

const documentedIndexes = [
  ...physicalSchema.matchAll(/^\| IX-\d{3} \| `([^`]+)` \|/gm),
].map((match) => match[1]);
const implementedIndexes = [
  ...sql.matchAll(/^CREATE (?:UNIQUE )?INDEX ([a-z][a-z0-9_]*)/gm),
].map((match) => match[1]);
for (const index of difference(documentedIndexes, implementedIndexes)) {
  errors.push(`documented IX index is not implemented: ${index}`);
}
if (documentedIndexes.length !== 40) {
  errors.push(`expected 40 documented indexes, found ${documentedIndexes.length}`);
}

const expectedDbTests = Array.from(
  { length: 22 },
  (_, index) => `DBT-${String(index + 1).padStart(3, "0")}`,
);
const implementedDbTests = unique([
  ...testSource.matchAll(/@DisplayName\("(DBT-\d{3}):/g),
].map((match) => match[1]));
for (const testId of difference(expectedDbTests, implementedDbTests)) {
  errors.push(`DB test is not automated: ${testId}`);
}

const constraintDeclarations = sql
  .split(/\r?\n/)
  .filter((line) => !/\bDROP CONSTRAINT\b/.test(line))
  .join("\n");
const droppedConstraints = new Set([
  ...sql.matchAll(/\bDROP CONSTRAINT ([a-z][a-z0-9_]*)/g),
].map((match) => match[1]));
for (const constraint of duplicates([
  ...constraintDeclarations.matchAll(/\bCONSTRAINT ([a-z][a-z0-9_]*)/g),
].map((match) => match[1]))) {
  if (!droppedConstraints.has(constraint)) {
    errors.push(`constraint name is duplicated: ${constraint}`);
  }
}
for (const trigger of duplicates([
  ...sql.matchAll(/^CREATE (?:CONSTRAINT )?TRIGGER ([a-z][a-z0-9_]*)/gm),
].map((match) => match[1]))) {
  errors.push(`trigger name is duplicated: ${trigger}`);
}
for (const functionName of duplicates([
  ...sql.matchAll(/^CREATE FUNCTION ([a-z][a-z0-9_]*)\(/gm),
].map((match) => match[1]))) {
  errors.push(`function name is duplicated: ${functionName}`);
}

if (errors.length > 0) {
  console.error("Database implementation validation failed:");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log("Database implementation validation passed.");
console.log(`- Flyway migrations: ${migrationFiles.length}`);
console.log(`- Physical tables: ${implementedTables.length}`);
console.log(`- Planned IX indexes: ${documentedIndexes.length}`);
console.log(`- Additional indexes: ${implementedIndexes.length - documentedIndexes.length}`);
console.log(`- Automated DBT criteria: ${expectedDbTests.length}`);
