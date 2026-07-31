import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const root = process.cwd();
const mapping = JSON.parse(
  fs.readFileSync(path.join(root, "contracts", "api-field-mapping.json"), "utf8"),
);
const openapi = fs.readFileSync(path.join(root, "contracts", "openapi.yaml"), "utf8");
const ephemeral = fs.readFileSync(
  path.join(root, "docs", "EPHEMERAL_AUTH_STORAGE.md"),
  "utf8",
);
const migrationDirectory = path.join(
  root,
  "backend",
  "src",
  "main",
  "resources",
  "db",
  "migration",
);
const sql = fs
  .readdirSync(migrationDirectory)
  .filter((file) => /^V.+\.sql$/.test(file))
  .map((file) => fs.readFileSync(path.join(migrationDirectory, file), "utf8"))
  .join("\n");

const errors = [];
const operations = mapping.operations ?? [];
const apiIds = operations.map((operation) => operation.api_id);
const mvp = operations.filter(
  (operation) => operation.implementation_status === "mvp-contract",
);
const planned = operations.filter(
  (operation) => operation.implementation_status === "planned",
);

function requireCondition(condition, message) {
  if (!condition) errors.push(message);
}

requireCondition(operations.length === 109, `expected 109 operations, found ${operations.length}`);
requireCondition(new Set(apiIds).size === 109, "api-field-mapping has duplicate API IDs");
requireCondition(mvp.length === 73, `expected 73 MVP/support contracts, found ${mvp.length}`);
requireCondition(planned.length === 36, `expected 36 planned contracts, found ${planned.length}`);

const openapiStatusCount = [...openapi.matchAll(/^\s+x-implementation-status:/gm)].length;
const openapiStrictCount = [
  ...openapi.matchAll(/^\s+x-schema-closure: "strict"$/gm),
].length;
requireCondition(openapiStatusCount === 109, `OpenAPI status markers: ${openapiStatusCount}`);
requireCondition(openapiStrictCount === mvp.length, `OpenAPI strict markers: ${openapiStrictCount}`);

for (const operation of mvp) {
  requireCondition(operation.storage_targets.length > 0, `${operation.api_id}: no storage target`);
  requireCondition(
    operation.request.mode === "none" || operation.request.mode === "strict",
    `${operation.api_id}: MVP request is not strict`,
  );
  requireCondition(
    operation.request.mode === "none" || operation.request.schema_source === "explicit",
    `${operation.api_id}: MVP request is inferred`,
  );
  requireCondition(
    operation.response.mode === "none" || operation.response.mode === "strict",
    `${operation.api_id}: MVP response is not strict`,
  );
  requireCondition(
    operation.response.mode === "none" || operation.response.fields.length > 0,
    `${operation.api_id}: MVP response has no fields`,
  );

  for (const [direction, contract] of [
    ["request", operation.request],
    ["response", operation.response],
  ]) {
    for (const field of contract.fields) {
      requireCondition(Boolean(field.name), `${operation.api_id} ${direction}: field name missing`);
      requireCondition(Boolean(field.type), `${operation.api_id}.${field.name}: type missing`);
      requireCondition(Boolean(field.target), `${operation.api_id}.${field.name}: target missing`);
      requireCondition(Boolean(field.transform), `${operation.api_id}.${field.name}: transform missing`);
      requireCondition(Boolean(field.visibility), `${operation.api_id}.${field.name}: visibility missing`);
    }
  }
}

const implementedTables = new Set(
  [...sql.matchAll(/^CREATE TABLE ([a-z][a-z0-9_]*) \(/gm)].map((match) => match[1]),
);
const virtualTarget = /^(?:redis\.|external_idp\.|view\.|service_projection$|external_secrets_manager$)/;
for (const operation of operations) {
  for (const target of operation.storage_targets) {
    requireCondition(
      implementedTables.has(target) || virtualTarget.test(target),
      `${operation.api_id}: unknown storage target ${target}`,
    );
  }
}

for (const requiredSql of [
  "planned_student_count integer",
  "role_code varchar(40)",
  "CREATE TABLE autonomous_schedule_modules",
  "CREATE TABLE school_ai_setting_versions",
]) {
  requireCondition(sql.includes(requiredSql), `missing SQL mapping: ${requiredSql}`);
}

for (const contractText of [
  "auth:password-reset:v1:{sha256(reset_request_id)}",
  "auth:realtime-ticket:v1:{sha256(ticket)}",
  "auth:display-session:v1:{sha256(display_token)}",
  "10분",
  "60초",
  "8시간",
  "원자적 `GET`+`DEL`",
]) {
  requireCondition(ephemeral.includes(contractText), `ephemeral auth rule missing: ${contractText}`);
}

if (errors.length > 0) {
  console.error("API/schema consistency validation failed:");
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log(
  `API/schema consistency PASS: ${operations.length} operations, ` +
    `${mvp.length} strict MVP/support contracts, ${planned.length} planned contracts`,
);
