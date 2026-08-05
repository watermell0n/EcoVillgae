import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const root = process.cwd();
const httpSpecPath = path.join(root, "docs", "HTTP_API_SPEC.md");
const websocketSpecPath = path.join(root, "docs", "WEBSOCKET_SPEC.md");
const rtmPath = path.join(root, "docs", "RTM.md");
const contractsDir = path.join(root, "contracts");

const httpMarkdown = fs.readFileSync(httpSpecPath, "utf8");
const websocketMarkdown = fs.readFileSync(websocketSpecPath, "utf8");
const rtmMarkdown = fs.readFileSync(rtmPath, "utf8");

function tableCells(line) {
  const sentinel = "\u0000";
  return line
    .replaceAll("\\|", sentinel)
    .split("|")
    .slice(1, -1)
    .map((cell) => cell.trim().replaceAll(sentinel, "|"));
}

function unique(values) {
  return [...new Set(values.filter(Boolean))];
}

function ids(text, prefix) {
  return unique(text.match(new RegExp(`${prefix}-[A-Z]+-\\d{3}`, "g")) ?? []);
}

function cleanMarkdown(text) {
  return text.replaceAll("`", "").replaceAll("·", ", ").replace(/\s+/g, " ").trim();
}

function pascalFromId(id) {
  return id
    .split("-")
    .map((part) => part[0] + part.slice(1).toLowerCase())
    .join("");
}

function camelFromId(id) {
  const pascal = pascalFromId(id);
  return pascal[0].toLowerCase() + pascal.slice(1);
}

function normalizeHttpPath(rawPath) {
  return rawPath
    .split("?", 1)[0]
    .replaceAll("{C}", "{classroom_id}")
    .replaceAll("{S}", "{student_id}")
    .replaceAll("{R}", "{resource_id}");
}

function parseHttpOperations(markdown) {
  const operations = [];

  for (const line of markdown.split(/\r?\n/)) {
    if (!line.startsWith("| API-")) continue;
    const cells = tableCells(line);
    const idMatch = cells[0]?.match(/API-[A-Z]+-\d{3}/);
    const routeMatch = cells[1]?.match(/`?(GET|POST|PUT|PATCH|DELETE) ([^`]+)`?/);
    if (!idMatch || !routeMatch) continue;

    const apiId = idMatch[0];
    const method = routeMatch[1];
    const rawPath = routeMatch[2].trim();
    const actionRow = cells.length >= 7;
    const queryRow = cells.length === 5;
    const supportRow = cells.length === 3;

    let roles = "";
    let contract = "";
    let permissionText = "";
    let errorText = "";
    let traceText = "";

    if (actionRow) {
      roles = cells[2];
      contract = cells[3];
      permissionText = cells[4];
      errorText = cells[5];
      traceText = cells[6];
    } else if (queryRow) {
      roles = cells[2];
      contract = cells[3];
      traceText = cells[4];
    } else if (supportRow) {
      contract = cells[2];
      roles =
        apiId === "API-AUTH-004"
          ? "갱신 토큰 보유 사용자"
          : apiId === "API-AUTH-005" || apiId === "API-AUTH-006" || apiId === "API-AUTH-007"
            ? "인증 사용자"
            : "";
    }

    operations.push({
      apiId,
      uiActionIds: ids(cells[0], "UIA"),
      method,
      rawPath,
      path: normalizeHttpPath(rawPath),
      roles: cleanMarkdown(roles),
      contract: cleanMarkdown(contract),
      permissionIds: ids(permissionText, "PM"),
      stateMachineIds: ids(permissionText, "SM"),
      errorCodes: unique(errorText.match(/[A-Z][A-Z0-9_]{2,}/g) ?? []),
      rtmIds: unique(traceText.match(/\b[A-Z]{2,4}-\d{3}\b/g) ?? []),
    });
  }

  return operations;
}

function parseMvpRequirementIds(markdown) {
  const result = new Set();
  for (const line of markdown.split(/\r?\n/)) {
    if (!/^\| [A-Z]{2,4}-\d{3} \|/.test(line)) continue;
    const cells = tableCells(line);
    const requirementId = cells[0]?.match(/^[A-Z]{2,4}-\d{3}$/)?.[0];
    if (requirementId && cells[1]?.includes("MVP")) result.add(requirementId);
  }
  return result;
}

const mvpRequirementIds = parseMvpRequirementIds(rtmMarkdown);
const mvpSupportApiIds = new Set([
  "API-AI-003",
  "API-AI-006",
  "API-AUTH-004",
  "API-AUTH-005",
  "API-AUTH-006",
  "API-AUTH-007",
  "API-AUTH-008",
  "API-FIN-007",
  "API-FIN-004",
  "API-FIN-008",
  "API-FIN-009",
  "API-FIN-010",
  "API-FIN-011",
  "API-FIN-012",
  "API-EST-001",
  "API-EST-002",
  "API-EST-003",
  "API-EST-004",
  "API-EST-005",
  "API-EST-006",
  "API-EST-007",
  "API-EST-008",
  "API-EST-009",
  "API-EST-010",
  "API-EST-011",
  "API-EST-012",
  "API-EST-013",
  "API-EST-014",
  "API-EST-015",
  "API-EST-016",
  "API-EST-017",
  "API-EST-018",
  "API-EST-019",
  "API-EST-020",
  "API-EST-021",
  "API-EST-022",
  "API-MOD-002",
  "API-POL-001",
  "API-QZ-006",
  "API-QZ-008",
  "API-QZ-009",
  "API-READ-001",
  "API-READ-003",
  "API-READ-016",
  "API-READ-018",
  "API-READ-019",
  "API-READ-027",
  "API-READ-028",
  "API-READ-029",
  "API-READ-030",
]);

function implementationStatus(operation) {
  return operation.rtmIds.some((id) => mvpRequirementIds.has(id)) ||
    mvpSupportApiIds.has(operation.apiId)
    ? "mvp-contract"
    : "planned";
}

function parseChannels(markdown) {
  const channels = [];
  for (const line of markdown.split(/\r?\n/)) {
    if (!line.startsWith("| WS-")) continue;
    const cells = tableCells(line);
    const channelId = cells[0]?.match(/WS-[A-Z]+-\d{3}/)?.[0];
    const logicalAddress = cells[1]?.match(/`([^`]+)`/)?.[1];
    if (!channelId || !logicalAddress) continue;
    channels.push({
      channelId,
      logicalAddress,
      roles: cleanMarkdown(cells[2]),
      authorization: cleanMarkdown(cells[3]),
      purpose: cleanMarkdown(cells[4]),
    });
  }
  return channels;
}

function parseEvents(markdown) {
  const events = [];
  for (const line of markdown.split(/\r?\n/)) {
    if (!line.startsWith("| EVT-")) continue;
    const cells = tableCells(line);
    const eventId = cells[0]?.match(/EVT-[A-Z]+-\d{3}/)?.[0];
    const eventType = cells[1]?.match(/`([^`]+)`/)?.[1];
    if (!eventId || !eventType) continue;

    const channelIds = ids(cells[2], "WS");
    const payloadText = cells.length >= 6 ? cells[4] : cells[3];
    const causeText = cells.length >= 6 ? cells[3] : "";
    const rtmText = cells.at(-1) ?? "";

    events.push({
      eventId,
      eventType,
      channelIds,
      audience: cleanMarkdown(cells[2]),
      cause: cleanMarkdown(causeText),
      payloadText: cleanMarkdown(payloadText),
      apiIds: ids(causeText, "API"),
      stateMachineIds: ids(causeText, "SM"),
      rtmIds: unique(rtmText.match(/\b[A-Z]{2,4}-\d{3}\b/g) ?? []),
    });
  }
  return events;
}

function propertySchema(name, explicitValues = []) {
  const lower = name.toLowerCase();
  let schema;

  if (explicitValues.length > 0) {
    const values = explicitValues.map((value) =>
      value === "true" ? true : value === "false" ? false : value,
    );
    const primitiveType = typeof values[0];
    schema = { type: primitiveType === "boolean" ? "boolean" : "string", enum: values };
  } else if (
    lower === "paused" ||
    lower === "retryable" ||
    lower === "leaderboard_public" ||
    lower === "enabled" ||
    lower === "multiple_choice"
  ) {
    schema = { type: "boolean" };
  } else if (lower.endsWith("_ids")) {
    schema = {
      type: "array",
      items: { type: "string", format: "uuid" },
      uniqueItems: true,
    };
  } else if (lower.endsWith("_id") || lower === "id") {
    schema = { type: "string", format: "uuid" };
  } else if (
    lower.endsWith("_at") ||
    lower.endsWith("_from") ||
    lower.endsWith("_until") ||
    lower === "server_time" ||
    lower === "client_time"
  ) {
    schema = { type: "string", format: "date-time" };
  } else if (lower.endsWith("_url") || lower === "action_url" || lower === "summary_url") {
    schema = { type: "string", format: "uri-reference" };
  } else if (
    lower.endsWith("_count") ||
    lower.endsWith("_version") ||
    lower.endsWith("_amount") ||
    lower.endsWith("_price") ||
    lower.endsWith("_quantity") ||
    lower.endsWith("_bps") ||
    lower === "amount" ||
    lower === "balance" ||
    lower === "delta" ||
    lower === "price" ||
    lower === "principal" ||
    lower === "quantity" ||
    lower === "installments" ||
    lower === "duration_minutes" ||
    lower === "number" ||
    lower === "progress_percent"
  ) {
    schema = { type: "integer", format: "int64" };
  } else if (
    lower === "options" ||
    lower === "available_actions" ||
    lower === "affected_question_ids" ||
    lower === "scopes" ||
    lower === "public_fields"
  ) {
    schema = { type: "array", items: { type: "string" } };
  } else if (
    lower === "option_counts" ||
    lower === "reward_policy_public" ||
    lower === "edited_values" ||
    lower === "edits" ||
    lower === "period"
  ) {
    schema = { type: "object", additionalProperties: true };
  } else {
    schema = { type: "string" };
  }

  return schema;
}

function schemaForField(field) {
  return field.schema ?? propertySchema(field.name, field.values ?? []);
}

function extractEnglishFields(text) {
  const input = (text.split("→")[0] ?? text).replaceAll("`", "");
  const fields = [];
  const pattern =
    /\b([a-z][a-z0-9_]*)(\?)?(?:=([A-Z][A-Z0-9_]*(?:\|[A-Z][A-Z0-9_]*)*|true|false))?/g;
  let match;
  while ((match = pattern.exec(input)) !== null) {
    const name = match[1];
    if (["operation_id", "state_version"].includes(name)) continue;
    const values = match[3]?.split("|") ?? [];
    fields.push({ name, optional: Boolean(match[2]), values });
  }

  return [...new Map(fields.map((field) => [field.name, field])).values()];
}

const requestFieldOverrides = {
  "API-AUTH-002": [
    { name: "classroom_code" },
    { name: "attendance_number", schema: { type: "integer", minimum: 1 } },
    { name: "nickname" },
    { name: "verification_method" },
    { name: "verification_value", schema: { type: "string", writeOnly: true } },
  ],
  "API-AUTH-007": [
    {
      name: "channel_ids",
      schema: {
        type: "array",
        minItems: 1,
        uniqueItems: true,
        items: { type: "string", pattern: "^WS-[A-Z]+-\\d{3}$" },
      },
    },
    {
      name: "resource_ids",
      optional: true,
      schema: {
        type: "array",
        uniqueItems: true,
        items: { type: "string", format: "uuid" },
      },
    },
  ],
  "API-CLS-001": [
    { name: "school_id" },
    { name: "display_name", optional: true, schema: { type: ["string", "null"], maxLength: 120 } },
    { name: "class_number", optional: true, schema: { type: ["integer", "null"], minimum: 1, maximum: 99 } },
    { name: "description", optional: true, schema: { type: ["string", "null"], maxLength: 500 } },
    {
      name: "currency_name",
      schema: {
        type: "string",
        minLength: 1,
        maxLength: 10,
        pattern: "^[가-힣A-Za-z0-9]{1,10}$",
        description:
          "NFKC 정규화와 앞뒤 공백 제거 후 검증하며 서비스 금칙어를 허용하지 않는 생성 후 불변 표시명",
      },
    },
    { name: "grade", schema: { type: "integer", enum: [4, 5, 6] } },
    {
      name: "course_size",
      values: ["ONE_SESSION", "ONE_WEEK", "ONE_MONTH", "SEMESTER"],
    },
    { name: "starts_on", schema: { type: "string", format: "date" } },
    { name: "ends_on", schema: { type: "string", format: "date" } },
    { name: "student_count", schema: { type: "integer", minimum: 1 } },
    { name: "timezone", schema: { type: "string", default: "Asia/Seoul" } },
    {
      name: "salary_policy",
      optional: true,
      schema: { type: "object", additionalProperties: true },
    },
    {
      name: "module_codes",
      schema: { type: "array", uniqueItems: true, items: { type: "string" } },
    },
    {
      name: "autonomous_schedules",
      optional: true,
      schema: { type: "array", items: { type: "object", additionalProperties: true } },
    },
  ],
  "API-CLS-002": [
    { name: "display_name", optional: true, schema: { type: ["string", "null"], maxLength: 120 } },
    { name: "class_number", optional: true, schema: { type: ["integer", "null"], minimum: 1, maximum: 99 } },
    { name: "description", optional: true, schema: { type: ["string", "null"], maxLength: 500 } },
    { name: "grade", schema: { type: "integer", enum: [4, 5, 6] } },
    {
      name: "course_size",
      values: ["ONE_SESSION", "ONE_WEEK", "ONE_MONTH", "SEMESTER"],
    },
    { name: "starts_on", schema: { type: "string", format: "date" } },
    { name: "ends_on", schema: { type: "string", format: "date" } },
    { name: "recommended_course_id", optional: true },
    {
      name: "difficulty_profile",
      values: ["GRADE_4_BASIC", "GRADE_5_STANDARD", "GRADE_6_ADVANCED"],
    },
    { name: "expected_policy_version", schema: { type: "integer", minimum: 0 } },
    {
      name: "classroom_code",
      optional: true,
      schema: { type: "string", minLength: 8, maxLength: 8 },
    },
    {
      name: "currency_name",
      optional: true,
      schema: { type: "string", minLength: 1, maxLength: 10 },
    },
  ],
  "API-CLS-003": [
    {
      name: "students",
      schema: {
        type: "array",
        items: {
          type: "object",
          required: ["attendance_number", "nickname"],
          properties: {
            student_id: { type: "string", format: "uuid" },
            attendance_number: { type: "integer", minimum: 1 },
            nickname: { type: "string", minLength: 1, maxLength: 40 },
            role_code: { type: ["string", "null"] },
          },
          additionalProperties: false,
        },
      },
    },
    {
      name: "salary_roles",
      optional: true,
      schema: { type: "array", items: { type: "object", additionalProperties: true } },
    },
    { name: "expected_policy_version", schema: { type: "integer", minimum: 0 } },
  ],
  "API-CLS-006": [
    { name: "timezone", schema: { type: "string", default: "Asia/Seoul" } },
    {
      name: "schedules",
      schema: {
        type: "array",
        items: {
          type: "object",
          required: ["days_of_week", "local_start_time", "local_end_time", "module_codes"],
          properties: {
            days_of_week: {
              type: "array",
              uniqueItems: true,
              items: {
                type: "string",
                enum: ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"],
              },
            },
            local_start_time: { type: "string", pattern: "^([01]\\d|2[0-3]):[0-5]\\d$" },
            local_end_time: { type: "string", pattern: "^([01]\\d|2[0-3]):[0-5]\\d$" },
            module_codes: { type: "array", items: { type: "string" }, uniqueItems: true },
          },
          additionalProperties: false,
        },
      },
    },
  ],
  "API-CLS-007": [
    { name: "command", values: ["OPEN", "CLOSE", "EXTEND"] },
    {
      name: "duration_minutes",
      optional: true,
      schema: { type: "integer", minimum: 1, maximum: 480 },
    },
    {
      name: "preflight_run_id",
      optional: true,
      schema: { type: ["string", "null"], format: "uuid" },
    },
    {
      name: "preflight_warning_acknowledgements",
      optional: true,
      schema: {
        type: "array",
        items: { type: "string", minLength: 1 },
        uniqueItems: true,
      },
    },
  ],
  "API-CLS-008": [
    { name: "reauthentication_proof", schema: { type: "string", writeOnly: true } },
    { name: "acknowledged_blocking_items", schema: { type: "boolean", const: true } },
    { name: "reason" },
  ],
  "API-MOD-001": [
    { name: "expected_state_version", schema: { type: "integer", minimum: 0 } },
    { name: "confirm_prerequisites", schema: { type: "boolean", const: true } },
    {
      name: "initial_policy_values",
      optional: true,
      schema: { type: "object", additionalProperties: true },
    },
    {
      name: "recommendation_action_ids",
      optional: true,
      schema: {
        type: "array",
        uniqueItems: true,
        items: { type: "string", minLength: 1, maxLength: 100 },
      },
    },
  ],
  "API-MOD-002": [
    { name: "reason", schema: { type: "string", minLength: 1, maxLength: 500 } },
  ],
  "API-POL-001": [
    { name: "price_adjustment_bps", schema: { type: "integer", format: "int32", minimum: -10000, maximum: 100000 } },
    { name: "inflation_rate_bps", schema: { type: "integer", format: "int32", minimum: -10000, maximum: 100000 } },
    { name: "income_tax_rate_bps", schema: { type: "integer", minimum: 0, maximum: 10000 } },
    { name: "vat_rate_bps", schema: { type: "integer", minimum: 0, maximum: 10000 } },
    { name: "trade_tax_rate_bps", schema: { type: "integer", minimum: 0, maximum: 10000 } },
    { name: "deposit_rate_bps", schema: { type: "integer", format: "int32", minimum: 0, maximum: 10000 } },
    { name: "loan_rate_bps", schema: { type: "integer", format: "int32", minimum: 0, maximum: 10000 } },
    { name: "stock_policy", schema: { type: "object", additionalProperties: true } },
    { name: "reward_policy", schema: { type: "object", additionalProperties: true } },
    { name: "limits", schema: { type: "object", additionalProperties: true } },
    { name: "expected_policy_version", schema: { type: "integer", minimum: 0 } },
  ],
  "API-POL-002": [
    {
      name: "use_categories",
      schema: { type: "array", items: { type: "object", additionalProperties: true } },
    },
    {
      name: "budget_allocations",
      schema: { type: "array", items: { type: "object", additionalProperties: true } },
    },
    { name: "expected_policy_version", schema: { type: "integer", minimum: 0 } },
  ],
  "API-TXN-001": [
    {
      name: "payout_type",
      values: ["SALARY", "ACTIVITY", "QUIZ", "MANUAL_COMPENSATION"],
    },
    { name: "student_ids" },
    { name: "period_key", schema: { type: "string", minLength: 1, maxLength: 40 } },
    { name: "amount", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "policy_version", schema: { type: "integer", minimum: 0 } },
    { name: "reason" },
  ],
  "API-TXN-002": [
    {
      name: "recovery_type",
      values: ["AMOUNT_CORRECTION", "ITEM_CORRECTION", "QUIZ_REGRADING"],
    },
    { name: "amount_delta", schema: { type: "integer", format: "int64" } },
    {
      name: "item_adjustments",
      optional: true,
      schema: { type: "array", items: { type: "object", additionalProperties: true } },
    },
    { name: "reason" },
  ],
  "API-SHP-003": [{ name: "pickup_note", optional: true }],
  "API-SHP-004": [{ name: "student_id" }],
  "API-CNT-002": [
    { name: "character_source_id" },
    { name: "display_name" },
    { name: "visible", schema: { type: "boolean" } },
    { name: "sale_enabled", schema: { type: "boolean" } },
  ],
  "API-TRD-001": [
    { name: "counterpart_student_id" },
    { name: "offered_amount", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "requested_amount", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "offered_item_ids" },
    { name: "requested_item_ids" },
    { name: "description", optional: true },
  ],
  "API-FIN-007": [
    { name: "investment_mode", values: ["SHORT_TERM", "LONG_TERM"] },
    { name: "prefer_historical_index", schema: { type: "boolean" } },
  ],
  "API-FIN-008": [
    { name: "command", values: ["START"] },
    { name: "expected_state_version", schema: { type: "integer", format: "int64", minimum: 0 } },
  ],
  "API-FIN-009": [
    { name: "side", values: ["BUY"] },
    { name: "asset_id" },
    { name: "quantity", schema: { type: "integer", minimum: 1, maximum: 100 } },
    { name: "expected_price_version", schema: { type: "integer", minimum: 1 } },
  ],
  "API-FIN-010": [
    { name: "maximum_principal", schema: { type: "integer", format: "int64", minimum: 1 } },
    { name: "interest_rate_bps", schema: { type: "integer", minimum: 0, maximum: 2000 } },
    { name: "term_units", schema: { type: "integer", minimum: 7, maximum: 84, multipleOf: 7 } },
    { name: "installment_count", schema: { type: "integer", minimum: 1, maximum: 12 } },
    { name: "grace_units", schema: { type: "integer", minimum: 0, maximum: 7 } },
    { name: "on_time_credit", schema: { type: "integer", minimum: 0, maximum: 100 } },
    { name: "overdue_credit", schema: { type: "integer", minimum: -100, maximum: 0 } },
    { name: "repaid_credit", schema: { type: "integer", minimum: 0, maximum: 100 } },
    { name: "expected_policy_version", optional: true, schema: { type: "integer", minimum: 0 } },
  ],
  "API-FIN-011": [
    { name: "command", values: ["APPLY", "CANCEL"] },
    { name: "application_id", optional: true, schema: { type: ["string", "null"], format: "uuid" } },
    { name: "amount", optional: true, schema: { type: ["integer", "null"], format: "int64", minimum: 1 } },
    { name: "installments", optional: true, schema: { type: ["integer", "null"], minimum: 1 } },
  ],
  "API-FIN-012": [
    { name: "decision", values: ["APPROVE", "REJECT"] },
    { name: "approved_amount", optional: true, schema: { type: ["integer", "null"], format: "int64", minimum: 1 } },
    { name: "reason", optional: true, schema: { type: ["string", "null"] } },
  ],
  "API-EST-002": [
    { name: "rows", schema: { type: "integer", minimum: 1, maximum: 26 } },
    { name: "columns", schema: { type: "integer", minimum: 1, maximum: 26 } },
  ],
  "API-EST-003": [
    { name: "coordinate", optional: true }, { name: "display_name", optional: true },
    { name: "row_index", optional: true, schema: { type: ["integer", "null"], minimum: 1 } },
    { name: "column_index", optional: true, schema: { type: ["integer", "null"], minimum: 1 } },
    { name: "tier", optional: true, values: ["GENERAL", "POPULAR"] },
    { name: "is_public", schema: { type: "boolean" } }, { name: "active", schema: { type: "boolean" } },
    { name: "allows_rent", schema: { type: "boolean" } }, { name: "allows_jeonse", schema: { type: "boolean" } },
    { name: "allows_sale", schema: { type: "boolean" } }, { name: "allows_subscription", schema: { type: "boolean" } },
    { name: "sale_price", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "deposit_amount", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "monthly_rent", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "payment_cycle_days", schema: { type: "integer", minimum: 1, maximum: 365 } },
    { name: "contract_term_days", schema: { type: "integer", minimum: 1, maximum: 365 } },
  ],
  "API-EST-004": [
    { name: "display_name", optional: true }, { name: "tier", optional: true, values: ["GENERAL", "POPULAR"] },
    { name: "is_public", schema: { type: "boolean" } }, { name: "active", schema: { type: "boolean" } },
    { name: "allows_rent", schema: { type: "boolean" } }, { name: "allows_jeonse", schema: { type: "boolean" } },
    { name: "allows_sale", schema: { type: "boolean" } }, { name: "allows_subscription", schema: { type: "boolean" } },
    { name: "sale_price", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "deposit_amount", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "monthly_rent", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "payment_cycle_days", schema: { type: "integer", minimum: 1, maximum: 365 } },
    { name: "contract_term_days", schema: { type: "integer", minimum: 1, maximum: 365 } },
  ],
  "API-EST-008": [
    { name: "contract_type", values: ["RENT", "JEONSE", "SALE"] },
    { name: "subscription_application_id", optional: true, schema: { type: ["string", "null"], format: "uuid" } },
  ],
  "API-EST-009": [{ name: "reason", optional: true }],
  "API-EST-010": [{ name: "reason", optional: true }],
  "API-EST-011": [],
  "API-EST-012": [
    { name: "lease_type", values: ["RENT", "JEONSE"] },
    { name: "deposit_amount", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "monthly_rent", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "payment_cycle_days", schema: { type: "integer", minimum: 1, maximum: 365 } },
    { name: "contract_term_days", schema: { type: "integer", minimum: 1, maximum: 365 } },
    { name: "is_public", schema: { type: "boolean" } },
  ],
  "API-EST-013": [
    { name: "lease_type", values: ["RENT", "JEONSE"] },
    { name: "deposit_amount", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "monthly_rent", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "payment_cycle_days", schema: { type: "integer", minimum: 1, maximum: 365 } },
    { name: "contract_term_days", schema: { type: "integer", minimum: 1, maximum: 365 } },
    { name: "is_public", schema: { type: "boolean" } },
  ],
  "API-EST-015": [
    { name: "title" }, { name: "opens_at", schema: { type: "string", format: "date-time" } },
    { name: "closes_at", schema: { type: "string", format: "date-time" } },
    { name: "sale_price", schema: { type: "integer", format: "int64", minimum: 1 } },
    { name: "supply_quantity", schema: { type: "integer", minimum: 1 } },
    { name: "minimum_deposit_count", schema: { type: "integer", minimum: 0 } },
    { name: "minimum_balance", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "property_ids", schema: { type: "array", minItems: 1, uniqueItems: true, items: { type: "string", format: "uuid" } } },
  ],
  "API-EST-016": [],
  "API-EST-017": [],
  "API-EST-019": [
    { name: "classroom_id", schema: { type: "string", format: "uuid" } },
  ],
  "API-EST-020": [
    { name: "classroom_id", schema: { type: "string", format: "uuid" } },
    { name: "amount", schema: { type: "integer", format: "int64", minimum: 1 } },
  ],
  "API-EST-022": [
    { name: "enabled", schema: { type: "boolean" } },
    { name: "amount", optional: true, schema: { type: ["integer", "null"], format: "int64", minimum: 1 } },
    { name: "cycle_days", optional: true, schema: { type: ["integer", "null"], minimum: 1, maximum: 365 } },
  ],
  "API-QZ-002": [
    { name: "grade", schema: { type: "integer", enum: [4, 5, 6] } },
    { name: "topic" },
    { name: "learning_objective" },
    { name: "content_type", values: ["ECONOMY", "NONSENSE"] },
    { name: "question_type", values: ["MULTIPLE_CHOICE", "OX"] },
    { name: "question_count", schema: { type: "integer", minimum: 1, maximum: 30 } },
  ],
  "API-QZ-006": [
    {
      name: "answers",
      schema: {
        type: "array",
        items: {
          type: "object",
          required: ["question_id", "option_id"],
          properties: {
            question_id: { type: "string", format: "uuid" },
            option_id: { type: "string", format: "uuid" },
          },
          additionalProperties: false,
        },
      },
    },
    { name: "answer_sheet_version", schema: { type: "integer", minimum: 0 } },
    { name: "submit", schema: { type: "boolean" } },
  ],
  "API-QZ-009": [
    { name: "invalidated_question_ids" },
    { name: "reason" },
    { name: "expected_result_version", schema: { type: "integer", minimum: 0 } },
  ],
  "API-AI-001": [
    { name: "question" },
    { name: "period", schema: { $ref: "#/components/schemas/Period" } },
    {
      name: "metric_scopes",
      schema: { type: "array", uniqueItems: true, items: { type: "string" } },
    },
    { name: "include_policy_history", optional: true, schema: { type: "boolean" } },
  ],
  "API-AI-002": [
    {
      name: "analysis_mode",
      values: ["SETUP_PRECHECK", "ON_DEMAND", "AUTO_MONITOR"],
    },
    {
      name: "trigger_context",
      optional: true,
      schema: { type: "object", additionalProperties: true },
    },
    { name: "period", optional: true, schema: { $ref: "#/components/schemas/Period" } },
    {
      name: "metric_scopes",
      optional: true,
      schema: { type: "array", uniqueItems: true, items: { type: "string" } },
    },
  ],
  "API-AI-003": [
    { name: "command", values: ["APPLY", "REJECT", "ACKNOWLEDGE"] },
    {
      name: "edited_values",
      optional: true,
      schema: {
        type: "array",
        maxItems: 100,
        items: {
          type: "object",
          required: ["action_id", "proposed_value"],
          properties: {
            action_id: { type: "string", minLength: 1, maxLength: 100 },
            proposed_value: { type: "integer", minimum: 0 },
          },
          additionalProperties: false,
        },
      },
    },
    { name: "reason", schema: { type: "string", minLength: 1, maxLength: 1000 } },
    { name: "expected_policy_version", optional: true, schema: { type: "integer", minimum: 0 } },
  ],
  "API-DAT-002": [
    { name: "source_classroom_id" },
    { name: "copy_course", schema: { type: "boolean" } },
    { name: "copy_modules", schema: { type: "boolean" } },
    { name: "approved_quiz_ids", optional: true },
  ],
  "API-ADM-001": [
    { name: "status", values: ["ACTIVE", "SUSPENDED"] },
    {
      name: "operational_metadata",
      optional: true,
      schema: { type: "object", additionalProperties: true },
    },
  ],
  "API-ADM-003": [
    { name: "new_owner_teacher_id" },
    { name: "confirmed_co_teacher_ids" },
    { name: "reason" },
  ],
  "API-AUTH-003": [
    { name: "command", values: ["COMPLETE"] },
    { name: "reset_request_id" },
    { name: "new_password", schema: { type: "string", format: "password", minLength: 8, writeOnly: true } },
    { name: "reason" },
  ],
  "API-ACT-001": [
    { name: "participant_ids" },
    { name: "public_fields", schema: { type: "array", uniqueItems: true, items: { type: "string" } } },
  ],
  "API-ACT-002": [
    { name: "command", values: ["END", "CANCEL"] },
    { name: "reason", optional: true },
  ],
  "API-DSP-001": [
    { name: "command", values: ["CONNECT", "DISCONNECT"] },
    { name: "display_session_id", optional: true },
  ],
  "API-SHP-001": [
    { name: "expected_policy_version", schema: { type: "integer", minimum: 0 } },
    { name: "command", values: ["CREATE", "UPDATE", "PUBLISH", "UNPUBLISH"] },
    { name: "product_id", optional: true },
    { name: "name" },
    { name: "description", optional: true },
    { name: "product_kind", values: ["DIGITAL", "PHYSICAL", "CHARACTER"] },
    { name: "unit_price", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "vat_rate_bps", schema: { type: "integer", minimum: 0, maximum: 10000 } },
    { name: "stock_quantity", schema: { type: "integer", minimum: 0 } },
    { name: "character_source_id", optional: true },
    { name: "expected_product_version", optional: true, schema: { type: "integer", minimum: 0 } },
  ],
  "API-SHP-002": [
    { name: "product_id" },
    { name: "quantity", schema: { type: "integer", minimum: 1 } },
    { name: "expected_product_version", schema: { type: "integer", minimum: 1 } },
  ],
  "API-ITM-001": [{ name: "command", values: ["EQUIP", "UNEQUIP"] }],
  "API-CNT-001": [
    { name: "command", values: ["APPROVE", "REJECT", "PUBLISH", "UNPUBLISH"] },
    { name: "source_title" },
    { name: "source_url", schema: { type: "string", format: "uri" } },
    { name: "rights_status" },
    { name: "rights_evidence_key" },
    { name: "reviewed_at", schema: { type: "string", format: "date-time" } },
    { name: "regions", schema: { type: "array", minItems: 1, uniqueItems: true, items: { type: "string" } } },
  ],
  "API-TRD-002": [{ name: "command", values: ["CANCEL"] }],
  "API-TRD-003": [
    { name: "decision", values: ["ACCEPT", "REJECT"] },
    { name: "reason", optional: true },
  ],
  "API-TRD-004": [
    { name: "decision", values: ["APPROVE", "REJECT"] },
    { name: "reason" },
  ],
  "API-FIN-001": [
    { name: "command", values: ["CREATE", "UPDATE", "PUBLISH", "STOP"] },
    { name: "product_id", optional: true },
    { name: "product_type", values: ["DEPOSIT", "SAVINGS"] },
    { name: "name" },
    { name: "term_units", schema: { type: "integer", minimum: 7, maximum: 84, multipleOf: 7 } },
    { name: "interest_rate_bps", schema: { type: "integer", minimum: 0, maximum: 10000 } },
    { name: "early_termination_rate_bps", schema: { type: "integer", minimum: 0, maximum: 10000 } },
    { name: "installment_count", optional: true, schema: { type: "integer", minimum: 2, maximum: 12 } },
    { name: "installment_amount", optional: true, schema: { type: "integer", format: "int64", minimum: 1 } },
    { name: "minimum_amount", schema: { type: "integer", format: "int64", minimum: 1 } },
    { name: "maximum_amount", schema: { type: "integer", format: "int64", minimum: 1 } },
    { name: "expected_product_version", optional: true, schema: { type: "integer", minimum: 0 } },
  ],
  "API-FIN-002": [
    { name: "product_id" },
    { name: "principal", schema: { type: "integer", format: "int64", minimum: 1 } },
    { name: "expected_product_version", schema: { type: "integer", minimum: 0 } },
  ],
  "API-FIN-003": [{ name: "command", values: ["EARLY_TERMINATE"] }],
  "API-FIN-004": [
    { name: "product_id", schema: { type: "string", format: "uuid" } },
    { name: "installment_amount", schema: { type: "integer", format: "int64", minimum: 1 } },
    { name: "expected_product_version", schema: { type: "integer", minimum: 1 } },
  ],
  "API-QZ-001": [
    { name: "command", values: ["CREATE", "UPDATE"] },
    { name: "question_id", optional: true },
    { name: "content_type", values: ["ECONOMY", "NONSENSE"] },
    { name: "question_type", values: ["MULTIPLE_CHOICE", "OX"] },
    { name: "prompt" },
    {
      name: "options",
      schema: {
        type: "array",
        minItems: 2,
        items: {
          type: "object",
          required: ["option_key", "option_text", "is_correct", "sort_order"],
          properties: {
            option_key: { type: "string" },
            option_text: { type: "string" },
            is_correct: { type: "boolean" },
            sort_order: { type: "integer", minimum: 0 },
          },
          additionalProperties: false,
        },
      },
    },
    { name: "explanation", optional: true },
    { name: "expected_question_version", optional: true, schema: { type: "integer", minimum: 0 } },
  ],
  "API-QZ-003": [
    { name: "command", values: ["APPROVE", "PUBLISH", "RETIRE"] },
    { name: "reason", optional: true },
  ],
  "API-QZ-004": [
    { name: "command", values: ["START", "UPDATE_REWARD", "CLOSE_SUBMISSIONS"] },
    { name: "question_version_ids" },
    { name: "participant_ids" },
    { name: "mode", values: ["GAME", "TEST"] },
    { name: "content_type", values: ["ECONOMY", "NONSENSE"] },
    { name: "reward_policy_public", schema: { type: "object", additionalProperties: { type: ["integer", "boolean", "string"] } } },
    { name: "leaderboard_public", optional: true, schema: { type: "boolean" } },
  ],
  "API-QZ-005": [
    { name: "question_id" },
    { name: "option_id" },
    { name: "client_answered_at", schema: { type: "string", format: "date-time" } },
  ],
  "API-QZ-007": [
    { name: "command", values: ["CONFIRM", "PUBLISH", "CLOSE"] },
    { name: "leaderboard_public", optional: true, schema: { type: "boolean" } },
    { name: "expected_result_version", optional: true, schema: { type: "integer", minimum: 0 } },
  ],
  "API-QZ-008": [
    { name: "question_id" },
    { name: "reason_code" },
    { name: "description" },
  ],
  "API-VOT-001": [
    {
      name: "command",
      values: ["CREATE", "OPEN", "CLOSE", "CONFIRM_RESULT"],
    },
    { name: "title", optional: true },
    { name: "description", optional: true },
    {
      name: "options",
      optional: true,
      schema: {
        type: "array",
        minItems: 2,
        maxItems: 10,
        items: {
          type: "object",
          required: ["label", "budget_amount"],
          properties: {
            label: { type: "string", minLength: 1, maxLength: 160 },
            description: { type: ["string", "null"], maxLength: 2000 },
            budget_amount: {
              type: "integer",
              format: "int64",
              minimum: 0,
            },
          },
          additionalProperties: false,
        },
      },
    },
    {
      name: "participant_ids",
      optional: true,
      schema: {
        type: "array",
        minItems: 1,
        uniqueItems: true,
        items: { type: "string", format: "uuid" },
      },
    },
    {
      name: "public_display_enabled",
      optional: true,
      schema: { type: "boolean" },
    },
    { name: "selected_use", optional: true },
  ],
  "API-VOT-002": [{ name: "option_id" }],
  "API-AI-004": [
    {
      name: "template_type",
      values: ["PRICE", "SUPPLY", "TAX", "INTEREST_RATE", "INVESTMENT_NEWS", "REWARD", "PUBLIC_FUND"],
    },
    { name: "interest_rate_scope", optional: true, values: ["DEPOSIT", "SAVINGS", "LOAN"] },
    { name: "teacher_context", optional: true, schema: { type: "string", maxLength: 1000 } },
  ],
  "API-AI-005": [
    // 학급 AI는 양방향 토글입니다. 끄기와 다시 켜기 모두 사유와 정책 버전을 남깁니다.
    // 학교 단위 API-ADM-004는 권한·우선순위·일괄 이력을 별도로 정해야 해서 끄기 전용을 유지합니다.
    { name: "enabled", schema: { type: "boolean" } },
    { name: "reason", schema: { type: "string", minLength: 1 } },
  ],
  "API-AI-006": [
    { name: "decision", values: ["APPROVE", "REJECT"] },
    {
      name: "edits",
      optional: true,
      schema: {
        type: "object",
        properties: {
          title: { type: "string", minLength: 1, maxLength: 150 },
          narrative: { type: "string", minLength: 1, maxLength: 2000 },
          choices: {
            type: "array",
            maxItems: 5,
            items: {
              type: "object",
              required: ["choice_key", "label"],
              properties: {
                choice_key: { type: "string", pattern: "^[A-Z][A-Z0-9_]{0,31}$" },
                label: { type: "string", minLength: 1, maxLength: 200 },
              },
              additionalProperties: false,
            },
          },
          progress_messages: {
            type: "array",
            minItems: 1,
            maxItems: 10,
            items: { type: "string", minLength: 1, maxLength: 300 },
          },
        },
        additionalProperties: false,
      },
    },
    { name: "reason", schema: { type: "string", minLength: 1, maxLength: 1000 } },
    { name: "expected_event_version", schema: { type: "integer", minimum: 1 } },
  ],
  "API-DAT-001": [
    { name: "format", values: ["PDF", "CSV"] },
    { name: "report_type" },
    { name: "period", schema: { $ref: "#/components/schemas/Period" } },
    { name: "student_scope", optional: true, schema: { type: "array", uniqueItems: true, items: { type: "string", format: "uuid" } } },
    { name: "reason", optional: true },
  ],
  "API-ADM-004": [
    { name: "enabled", schema: { type: "boolean", const: false } },
    { name: "reason", schema: { type: "string", minLength: 1 } },
  ],
  "API-OPS-001": [
    { name: "provider" },
    { name: "global_daily_limit", schema: { type: "integer", minimum: 0 } },
    { name: "model_routes", schema: { type: "object", additionalProperties: true } },
    { name: "approval_reference" },
    { name: "expected_policy_version", schema: { type: "integer", minimum: 0 } },
  ],
};

const mutationContextFields = [
  { name: "expected_state_version", optional: true, schema: { type: "integer", format: "int64", minimum: 0 } },
  { name: "screen_id", optional: true, schema: { type: "string", pattern: "^SCR-[A-Z]+-\\d{3}$" } },
  { name: "ui_action_id", optional: true, schema: { type: "string", pattern: "^UIA-[A-Z]+-\\d{3}$" } },
  { name: "client_occurred_at", optional: true, schema: { type: "string", format: "date-time" } },
];

const requiredExpectedStateVersionApis = new Set(["API-ITM-001", "API-SHP-001"]);

function mutationContextFor(operation) {
  if (["API-AI-002", "API-AI-003"].includes(operation.apiId)) return [];
  return mutationContextFields.map((field) => {
    if (
      field.name === "expected_state_version"
      && requiredExpectedStateVersionApis.has(operation.apiId)
    ) {
      return { ...field, optional: false };
    }
    return field;
  });
}

function projectionItemSchema(extraProperties = {}, required = []) {
  return {
    type: "object",
    properties: {
      resource_id: { type: "string", format: "uuid" },
      label: { type: "string" },
      status: { type: "string" },
      version: { type: "integer", minimum: 0 },
      state_version: { type: "integer", format: "int64", minimum: 0 },
      ...extraProperties,
    },
    ...(required.length > 0 ? { required } : {}),
    additionalProperties: false,
  };
}

const projectionItems = (extraProperties = {}, required = []) => ({
  type: "array",
  items: projectionItemSchema(extraProperties, required),
});

const responseFieldOverrides = {
  "API-FIN-007": [
    { name: "market_id", schema: { type: "string", format: "uuid" } },
    { name: "scenario_version_id", schema: { type: "string", format: "uuid" } },
    { name: "market_status", schema: { type: "string", enum: ["DRAFT", "OPEN"] } },
    { name: "investment_mode", schema: { type: "string", enum: ["SHORT_TERM", "LONG_TERM"] } },
    { name: "scenario_source", schema: { type: "string", enum: ["AI_GENERATED", "HISTORICAL_INDEX"] } },
    { name: "settlement_rule", schema: { type: "string", enum: ["NEXT_DAY_MANDATORY", "COURSE_END_FINAL"] } },
    { name: "step_count", schema: { type: "integer", minimum: 2 } },
    { name: "state_version", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "assets", schema: { type: "array", minItems: 4, maxItems: 4, items: { type: "object", additionalProperties: true } } },
  ],
  "API-FIN-008": [
    { name: "market_id", schema: { type: "string", format: "uuid" } },
    { name: "scenario_version_id", schema: { type: "string", format: "uuid" } },
    { name: "market_status", schema: { type: "string", enum: ["OPEN"] } },
    { name: "investment_mode", schema: { type: "string", enum: ["SHORT_TERM", "LONG_TERM"] } },
    { name: "scenario_source", schema: { type: "string", enum: ["AI_GENERATED", "HISTORICAL_INDEX"] } },
    { name: "settlement_rule", schema: { type: "string", enum: ["NEXT_DAY_MANDATORY", "COURSE_END_FINAL"] } },
    { name: "step_count", schema: { type: "integer", minimum: 2 } },
    { name: "state_version", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "assets", schema: { type: "array", minItems: 4, maxItems: 4, items: { type: "object", additionalProperties: true } } },
  ],
  "API-FIN-009": [
    { name: "order_id", schema: { type: "string", format: "uuid" } },
    { name: "status", schema: { type: "string", enum: ["EXECUTED"] } },
    { name: "quantity", schema: { type: "integer", minimum: 1, maximum: 100 } },
    { name: "executed_price", schema: { type: "integer", format: "int64", minimum: 1 } },
    { name: "price_version", schema: { type: "integer", minimum: 1 } },
    { name: "total_amount", schema: { type: "integer", format: "int64", minimum: 1 } },
    { name: "position_quantity", schema: { type: "integer", minimum: 1 } },
    { name: "average_price", schema: { type: "integer", format: "int64", minimum: 1 } },
    { name: "state_version", schema: { type: "integer", format: "int64", minimum: 0 } },
  ],
  "API-FIN-010": [
    { name: "version", schema: { type: "integer", minimum: 1 } },
    { name: "maximum_principal", schema: { type: "integer", format: "int64", minimum: 1 } },
    { name: "interest_rate_bps", schema: { type: "integer", minimum: 0, maximum: 2000 } },
    { name: "term_units", schema: { type: "integer", minimum: 1 } },
    { name: "installment_count", schema: { type: "integer", minimum: 1, maximum: 12 } },
    { name: "grace_units", schema: { type: "integer", minimum: 0, maximum: 7 } },
    { name: "on_time_credit", schema: { type: "integer" } },
    { name: "overdue_credit", schema: { type: "integer" } },
    { name: "repaid_credit", schema: { type: "integer" } },
  ],
  "API-FIN-011": [
    { name: "application_id", schema: { type: "string", format: "uuid" } },
    { name: "student_id", schema: { type: "string", format: "uuid" } },
    { name: "student_nickname", schema: { type: "string" } },
    { name: "requested_amount", schema: { type: "integer", format: "int64" } },
    { name: "approved_amount", optional: true, schema: { type: ["integer", "null"], format: "int64" } },
    { name: "credit_score", schema: { type: "integer" } },
    { name: "credit_level", schema: { type: "string" } },
    { name: "status", schema: { type: "string" } },
    { name: "created_at", schema: { type: "string", format: "date-time" } },
  ],
  "API-FIN-012": [
    { name: "application_id", schema: { type: "string", format: "uuid" } },
    { name: "student_id", schema: { type: "string", format: "uuid" } },
    { name: "student_nickname", schema: { type: "string" } },
    { name: "requested_amount", schema: { type: "integer", format: "int64" } },
    { name: "approved_amount", optional: true, schema: { type: ["integer", "null"], format: "int64" } },
    { name: "credit_score", schema: { type: "integer" } },
    { name: "credit_level", schema: { type: "string" } },
    { name: "status", schema: { type: "string" } },
    { name: "created_at", schema: { type: "string", format: "date-time" } },
  ],
  "API-READ-018": [
    { name: "credit_score", schema: { type: "integer" } },
    { name: "credit_level", schema: { type: "string" } },
    { name: "available_limit", schema: { type: "integer", format: "int64" } },
    { name: "interest_rate_bps", schema: { type: "integer" } },
    { name: "term_units", schema: { type: "integer" } },
    { name: "installment_count", schema: { type: "integer" } },
    { name: "can_apply", schema: { type: "boolean" } },
    { name: "pending_application_id", optional: true, schema: { type: ["string", "null"], format: "uuid" } },
    { name: "pending_application_status", optional: true, schema: { type: ["string", "null"] } },
    { name: "loans", schema: { type: "array", items: { type: "object", additionalProperties: true } } },
  ],
  "API-READ-019": [
    { name: "policy", schema: { type: "object", additionalProperties: true } },
    { name: "applications", schema: { type: "array", items: { type: "object", additionalProperties: true } } },
  ],
  "API-READ-016": [
    { name: "market_id", schema: { type: "string", format: "uuid" } },
    { name: "scenario_version_id", schema: { type: "string", format: "uuid" } },
    { name: "status", schema: { type: "string", enum: ["DRAFT", "OPEN", "PAUSED", "CLOSED"] } },
    { name: "investment_mode", schema: { type: "string", enum: ["SHORT_TERM", "LONG_TERM"] } },
    { name: "scenario_source", schema: { type: "string", enum: ["AI_GENERATED", "HISTORICAL_INDEX"] } },
    { name: "settlement_rule", schema: { type: "string", enum: ["NEXT_DAY_MANDATORY", "COURSE_END_FINAL"] } },
    { name: "current_step", schema: { type: "integer", minimum: 0 } },
    { name: "state_version", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "teacher_view", schema: { type: "boolean" } },
    { name: "assets", schema: { type: "array", items: { type: "object", additionalProperties: true } } },
  ],
  "API-QZ-002": [
    { name: "operation_id", schema: { type: "string", format: "uuid" } },
    { name: "status", schema: { type: "string" } },
    { name: "status_url", schema: { type: "string" } },
    { name: "ai_run_id", schema: { type: "string", format: "uuid" } },
    { name: "review_status", schema: { type: "string", enum: ["REVIEW_REQUIRED"] } },
    {
      name: "question_ids",
      schema: {
        type: "array",
        minItems: 1,
        maxItems: 30,
        items: { type: "string", format: "uuid" },
      },
    },
  ],
  "API-SHP-001": [
    { name: "product_id", schema: { type: "string", format: "uuid" } },
    { name: "status", schema: { type: "string" } },
    { name: "state_version", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "version", schema: { type: "integer", minimum: 1 } },
  ],
  "API-CRS-001": [
    {
      name: "recommendations",
      schema: {
        type: "array",
        items: {
          type: "object",
          required: [
            "recommended_course_id",
            "template_code",
            "name",
            "grade",
            "course_size",
            "version",
            "data_version",
            "published_at",
            "difficulty_profile",
            "learning_focus",
            "recommendation_reason",
            "modules",
          ],
          properties: {
            recommended_course_id: { type: "string", format: "uuid" },
            template_code: { type: "string" },
            name: { type: "string" },
            grade: { type: "integer", enum: [4, 5, 6] },
            course_size: {
              type: "string",
              enum: ["ONE_SESSION", "ONE_WEEK", "ONE_MONTH", "SEMESTER"],
            },
            version: { type: "integer", minimum: 1 },
            data_version: { type: "string" },
            published_at: { type: "string", format: "date-time" },
            difficulty_profile: { type: "object", additionalProperties: true },
            learning_focus: { type: "string" },
            recommendation_reason: { type: "string" },
            modules: {
              type: "array",
              items: {
                type: "object",
                required: [
                  "module_code",
                  "label",
                  "recommendation_level",
                  "prerequisites",
                ],
                properties: {
                  module_code: { type: "string" },
                  label: { type: "string" },
                  recommendation_level: {
                    type: "string",
                    enum: ["FOUNDATION", "CORE", "OPTIONAL"],
                  },
                  prerequisites: {
                    type: "array",
                    uniqueItems: true,
                    items: { type: "string" },
                  },
                  exclusion_impact: { type: ["string", "null"] },
                },
                additionalProperties: false,
              },
            },
          },
          additionalProperties: false,
        },
      },
    },
  ],
  "API-AUTH-001": [
    { name: "access_token", schema: { type: "string", readOnly: true, "x-sensitive": true } },
    { name: "refresh_token", schema: { type: "string", readOnly: true, "x-sensitive": true } },
    { name: "expires_at", schema: { type: "string", format: "date-time" } },
    { name: "classroom_id", schema: { type: "string", format: "uuid" } },
    { name: "student_id", schema: { type: "string", format: "uuid" } },
    { name: "currency_name", schema: { type: "string", minLength: 1, maxLength: 10 } },
    { name: "wallet_balance", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "role", schema: { type: "string", enum: ["STU"] } },
  ],
  "API-AUTH-002": [
    { name: "reset_request_id", schema: { type: "string", format: "uuid" } },
    { name: "expires_at", schema: { type: "string", format: "date-time" } },
    { name: "verification_method", schema: { type: "string" } },
  ],
  "API-AUTH-003": [
    { name: "credential_version", schema: { type: "integer", format: "int64", minimum: 1 } },
    { name: "completed_at", schema: { type: "string", format: "date-time" } },
  ],
  "API-AUTH-004": [
    { name: "access_token", schema: { type: "string", readOnly: true, "x-sensitive": true } },
    {
      name: "refresh_token",
      optional: true,
      schema: { type: "string", readOnly: true, "x-sensitive": true },
    },
    { name: "expires_at", schema: { type: "string", format: "date-time" } },
    { name: "session_id", schema: { type: "string", format: "uuid" } },
    { name: "role", schema: { type: "string", enum: ["STU", "TCH"] } },
  ],
  "API-AUTH-006": [
    { name: "session_id", schema: { type: "string", format: "uuid" } },
    { name: "user_id", schema: { type: "string", format: "uuid" } },
    { name: "display_name", schema: { type: "string" } },
    { name: "role", schema: { type: "string", enum: ["STU", "TCH"] } },
    { name: "access_token_expires_at", schema: { type: "string", format: "date-time" } },
    {
      name: "schools",
      schema: {
        type: "array",
        items: {
          type: "object",
          required: ["school_id", "role"],
          properties: {
            school_id: { type: "string", format: "uuid" },
            role: { type: "string", enum: ["TEACHER", "SCHOOL_ADMIN"] },
          },
          additionalProperties: false,
        },
      },
    },
    {
      name: "classrooms",
      schema: {
        type: "array",
        items: {
          type: "object",
          required: ["classroom_id", "classroom_code", "role"],
          properties: {
            classroom_id: { type: "string", format: "uuid" },
            classroom_code: { type: "string", minLength: 8, maxLength: 8 },
            role: { type: "string", enum: ["STU", "OWN", "COT"] },
          },
          additionalProperties: false,
        },
      },
    },
  ],
  "API-AUTH-007": [
    { name: "ticket", schema: { type: "string", minLength: 20, readOnly: true, "x-sensitive": true } },
    { name: "expires_at", schema: { type: "string", format: "date-time" } },
  ],
  "API-AUTH-008": [
    { name: "access_token", schema: { type: "string", readOnly: true, "x-sensitive": true } },
    { name: "expires_at", schema: { type: "string", format: "date-time" } },
    { name: "session_id", schema: { type: "string", format: "uuid" } },
    { name: "teacher_id", schema: { type: "string", format: "uuid" } },
    { name: "display_name", schema: { type: "string" } },
    { name: "role", schema: { type: "string", enum: ["TCH"] } },
  ],
  "API-CLS-001": [
    { name: "classroom_id", schema: { type: "string", format: "uuid" } },
    { name: "classroom_code", schema: { type: "string", minLength: 8, maxLength: 8 } },
    { name: "currency_name", schema: { type: "string", minLength: 1, maxLength: 10 } },
    { name: "status", schema: { type: "string" } },
    { name: "state_version", schema: { type: "integer", format: "int64", minimum: 0 } },
  ],
  "API-MOD-001": [
    { name: "module_code", schema: { type: "string" } },
    { name: "status", schema: { type: "string", enum: ["ACTIVE"] } },
    { name: "state_version", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "policy_area", optional: true, schema: { type: ["string", "null"] } },
    { name: "policy_version", optional: true, schema: { type: ["integer", "null"], minimum: 1 } },
    { name: "policy_version_id", optional: true, schema: { type: ["string", "null"], format: "uuid" } },
    { name: "rule_version", schema: { type: "string" } },
    { name: "replayed", schema: { type: "boolean" } },
  ],
  "API-MOD-002": [
    { name: "module_id", schema: { type: "string", format: "uuid" } },
    { name: "module_code", schema: { type: "string" } },
    { name: "status", schema: { type: "string", enum: ["STOPPING"] } },
    { name: "state_version", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "cleanup_required", schema: { type: "boolean", const: true } },
    {
      name: "cleanup_blockers",
      schema: { type: "array", items: { type: "string" }, uniqueItems: true },
    },
    { name: "replayed", schema: { type: "boolean" } },
  ],
  "API-POL-001": [
    { name: "policy_version_id", schema: { type: "string", format: "uuid" } },
    { name: "policy_version", schema: { type: "integer", minimum: 1 } },
    { name: "status", schema: { type: "string", enum: ["ACTIVE"] } },
    { name: "state_version", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "replayed", schema: { type: "boolean" } },
  ],
  "API-CLS-002": [
    { name: "classroom_id", schema: { type: "string", format: "uuid" } },
    { name: "policy_version", schema: { type: "integer", minimum: 1 } },
    { name: "state_version", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "replayed", schema: { type: "boolean" } },
  ],
  "API-CLS-003": [
    { name: "classroom_id", schema: { type: "string", format: "uuid" } },
    { name: "student_count", schema: { type: "integer", minimum: 0 } },
    { name: "salary_role_count", schema: { type: "integer", minimum: 0 } },
    { name: "state_version", schema: { type: "integer", format: "int64", minimum: 0 } },
  ],
  "API-CLS-007": [
    { name: "mode", schema: { type: "string", enum: ["CLOSED", "AUTONOMOUS"] } },
    { name: "source", schema: { type: "string", enum: ["MANUAL"] } },
    {
      name: "expires_at",
      optional: true,
      schema: { type: ["string", "null"], format: "date-time" },
    },
    {
      name: "module_codes",
      schema: { type: "array", items: { type: "string" }, uniqueItems: true },
    },
    { name: "state_version", schema: { type: "integer", format: "int64", minimum: 0 } },
  ],
  "API-ACT-001": [
    { name: "activity_id", schema: { type: "string", format: "uuid" } },
    { name: "status", schema: { type: "string", enum: ["LIVE"] } },
    { name: "state_version", schema: { type: "integer", format: "int64", minimum: 0 } },
  ],
  "API-ACT-002": [
    { name: "activity_id", schema: { type: "string", format: "uuid" } },
    { name: "status", schema: { type: "string", enum: ["FINALIZING", "CANCELED"] } },
    { name: "state_version", schema: { type: "integer", format: "int64", minimum: 0 } },
  ],
  "API-DSP-001": [
    { name: "display_session_id", schema: { type: "string", format: "uuid" } },
    { name: "display_token", optional: true, schema: { type: "string", readOnly: true, "x-sensitive": true } },
    { name: "expires_at", optional: true, schema: { type: "string", format: "date-time" } },
    { name: "status", schema: { type: "string" } },
  ],
  "API-TXN-001": [
    { name: "transaction_group_id", schema: { type: "string", format: "uuid" } },
    { name: "status", schema: { type: "string", enum: ["COMMITTED"] } },
    { name: "state_version", schema: { type: "integer", format: "int64", minimum: 0 } },
  ],
  "API-TXN-002": [
    { name: "transaction_group_id", schema: { type: "string", format: "uuid" } },
    { name: "recovery_of_group_id", schema: { type: "string", format: "uuid" } },
    { name: "status", schema: { type: "string", enum: ["COMMITTED"] } },
  ],
  "API-SHP-002": [
    { name: "order_id", schema: { type: "string", format: "uuid" } },
    { name: "status", schema: { type: "string" } },
    { name: "total_amount", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "state_version", schema: { type: "integer", format: "int64", minimum: 0 } },
  ],
  "API-ITM-001": [
    { name: "inventory_item_id", schema: { type: "string", format: "uuid" } },
    { name: "status", schema: { type: "string", enum: ["AVAILABLE", "EQUIPPED"] } },
    { name: "state_version", schema: { type: "integer", format: "int64", minimum: 0 } },
  ],
  "API-VOT-002": [
    { name: "receipt_hash", schema: { type: "string" } },
    { name: "cast_at", schema: { type: "string", format: "date-time" } },
  ],
  "API-AI-001": [
    { name: "ai_run_id", schema: { type: "string", format: "uuid" } },
    { name: "answer", schema: { type: "string", minLength: 1, maxLength: 3000 } },
    { name: "period", schema: { $ref: "#/components/schemas/Period" } },
    { name: "data_as_of", schema: { type: "string", format: "date-time" } },
    {
      name: "evidence",
      schema: {
        type: "array",
        minItems: 1,
        maxItems: 100,
        items: {
          type: "object",
          required: ["fact_id", "label", "value", "unit"],
          properties: {
            fact_id: { type: "string", minLength: 1, maxLength: 100 },
            label: { type: "string", minLength: 1, maxLength: 150 },
            value: { type: ["string", "integer"] },
            unit: { type: "string", enum: ["AMOUNT", "COUNT", "RATE_BPS", "STATUS", "TEXT"] },
          },
          additionalProperties: false,
        },
      },
    },
    {
      name: "recommendations",
      schema: {
        type: "array",
        maxItems: 5,
        items: {
          type: "object",
          required: ["text", "side_effects"],
          properties: {
            text: { type: "string", minLength: 1, maxLength: 700 },
            side_effects: {
              type: "array",
              minItems: 1,
              maxItems: 5,
              items: { type: "string", minLength: 1, maxLength: 300 },
            },
          },
          additionalProperties: false,
        },
      },
    },
  ],
  "API-AI-002": [
    { name: "operation_id", schema: { type: "string", format: "uuid" } },
    { name: "ai_run_id", schema: { type: "string", format: "uuid" } },
    { name: "operation_status", schema: { type: "string" } },
    { name: "run_status", schema: { type: "string" } },
    { name: "fallback_used", schema: { type: "boolean" } },
    { name: "fallback_code", schema: { type: ["string", "null"] } },
    { name: "proposal_id", schema: { type: ["string", "null"], format: "uuid" } },
    { name: "summary", schema: { type: "string" } },
    { name: "overall_severity", optional: true, schema: { type: "string" } },
    { name: "candidates", optional: true, schema: { type: "array", items: { type: "object", additionalProperties: true } } },
    { name: "checks", optional: true, schema: { type: "array", items: { type: "object", additionalProperties: true } } },
    { name: "analysis_mode", optional: true, schema: { type: "string" } },
    { name: "analysis_severity", optional: true, schema: { type: "string" } },
    { name: "sample", optional: true, schema: { type: "object", additionalProperties: true } },
    { name: "insufficient_codes", optional: true, schema: { type: "array", items: { type: "string" } } },
    { name: "metrics", optional: true, schema: { type: "array", items: { type: "object", additionalProperties: true } } },
  ],
  "API-AI-003": [
    { name: "proposal_id", schema: { type: "string", format: "uuid" } },
    { name: "ai_run_id", schema: { type: "string", format: "uuid" } },
    { name: "decision_status", schema: { type: "string", enum: ["APPLIED", "REJECTED", "ACKNOWLEDGED"] } },
    { name: "policy_version", optional: true, schema: { type: "integer", minimum: 1 } },
    { name: "policy_version_id", optional: true, schema: { type: ["string", "null"], format: "uuid" } },
    {
      name: "applied_values",
      schema: {
        type: "array",
        maxItems: 100,
        items: {
          type: "object",
          required: ["action_id", "proposed_value", "unit"],
          properties: {
            action_id: { type: "string", minLength: 1, maxLength: 100 },
            proposed_value: { type: "integer", minimum: 0 },
            unit: { type: "string", enum: ["AMOUNT", "COUNT", "RATE_BPS"] },
          },
          additionalProperties: false,
        },
      },
    },
    { name: "state_version", schema: { type: "integer", format: "int64", minimum: 0 } },
  ],
  "API-AI-006": [
    { name: "event_id", schema: { type: "string", format: "uuid" } },
    { name: "ai_run_id", schema: { type: "string", format: "uuid" } },
    { name: "status", schema: { type: "string", enum: ["APPROVED", "REJECTED"] } },
    { name: "state_version", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "published_at", optional: true, schema: { type: "string", format: "date-time" } },
  ],
  "API-ADM-004": [
    { name: "school_id", schema: { type: "string", format: "uuid" } },
    { name: "version", schema: { type: "integer", minimum: 1 } },
    { name: "enabled", schema: { type: "boolean" } },
    { name: "effective_from", schema: { type: "string", format: "date-time" } },
  ],
  "API-READ-001": [
    { name: "user_id", schema: { type: "string", format: "uuid" } },
    { name: "roles", schema: { type: "array", items: { type: "string" }, uniqueItems: true } },
    { name: "classrooms", schema: projectionItems({ classroom_code: { type: "string" }, currency_name: { type: "string" } }) },
    { name: "start_screen", schema: { type: "string" } },
  ],
  "API-READ-002": [
    { name: "classroom_id", schema: { type: "string", format: "uuid" } },
    { name: "classroom_code", optional: true, schema: { type: "string" } },
    { name: "currency_name", schema: { type: "string" } },
    { name: "grade", schema: { type: "integer", enum: [4, 5, 6] } },
    { name: "course_size", schema: { type: "string" } },
    { name: "planned_student_count", schema: { type: "integer", minimum: 1 } },
    { name: "status", schema: { type: "string" } },
    { name: "state_version", schema: { type: "integer", format: "int64", minimum: 0 } },
  ],
  "API-READ-003": [
    { name: "classroom_id", schema: { type: "string", format: "uuid" } },
    { name: "classroom_code", schema: { type: "string" } },
    { name: "display_name", optional: true, schema: { type: ["string", "null"] } },
    { name: "class_number", optional: true, schema: { type: ["integer", "null"] } },
    { name: "description", optional: true, schema: { type: ["string", "null"] } },
    { name: "currency_name", schema: { type: "string" } },
    { name: "grade", schema: { type: "integer", enum: [4, 5, 6] } },
    { name: "course_size", schema: { type: "string", enum: ["ONE_SESSION", "ONE_WEEK", "ONE_MONTH", "SEMESTER"] } },
    { name: "student_count", schema: { type: "integer", minimum: 0 } },
    { name: "starts_on", schema: { type: ["string", "null"], format: "date" } },
    { name: "ends_on", schema: { type: ["string", "null"], format: "date" } },
    { name: "recommended_course_id", optional: true, schema: { type: ["string", "null"], format: "uuid" } },
    { name: "difficulty_profile", schema: { type: "string" } },
    { name: "state_version", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "module_codes", schema: { type: "array", items: { type: "string" }, uniqueItems: true } },
    { name: "autonomous_schedules", schema: projectionItems({ module_codes: { type: "array", items: { type: "string" }, uniqueItems: true } }) },
    { name: "policy_versions", schema: projectionItems({ policy_area: { type: "string" } }) },
  ],
  "API-READ-004": [
    { name: "students", schema: projectionItems({ attendance_number: { type: "integer", minimum: 1 }, nickname: { type: "string" }, role_code: { type: ["string", "null"] } }) },
    { name: "salary_roles", schema: projectionItems({ role_code: { type: "string" }, gross_amount: { type: "integer", format: "int64", minimum: 0 } }) },
  ],
  "API-READ-006": [
    { name: "modules", schema: projectionItems({ module_code: { type: "string" }, config: { type: "object", additionalProperties: true } }) },
    { name: "policy_versions", schema: projectionItems({ policy_area: { type: "string" }, values: { type: "object", additionalProperties: true } }) },
  ],
  "API-READ-007": [
    { name: "balance", schema: { type: "integer", format: "int64" } },
    { name: "recent_transactions", schema: projectionItems({ amount: { type: "integer", format: "int64" }, occurred_at: { type: "string", format: "date-time" } }) },
  ],
  "API-READ-008": [{ name: "payslips", schema: projectionItems({ net_amount: { type: "integer", format: "int64" }, issued_at: { type: "string", format: "date-time" } }) }],
  "API-READ-009": [{ name: "transactions", schema: projectionItems({ amount: { type: "integer", format: "int64" }, occurred_at: { type: "string", format: "date-time" } }) }],
  "API-READ-010": [{
    name: "products",
    schema: projectionItems({
      unit_price: { type: "integer", format: "int64", minimum: 0 },
      vat_rate_bps: { type: "integer", minimum: 0, maximum: 10000 },
      stock_quantity: { type: "integer", minimum: 0 },
      product_kind: { type: "string" },
      character_code: { type: ["string", "null"] },
    }, [
      "resource_id",
      "label",
      "status",
      "version",
      "state_version",
      "unit_price",
      "vat_rate_bps",
      "available_quantity",
      "product_kind",
      "character_code",
    ]),
  }],
  "API-READ-011": [{ name: "orders", schema: projectionItems({ total_amount: { type: "integer", format: "int64", minimum: 0 }, ordered_at: { type: "string", format: "date-time" } }) }],
  "API-READ-012": [{
    name: "inventory_items",
    schema: projectionItems(
      { character_code: { type: "string" }, equipped: { type: "boolean" } },
      ["resource_id", "label", "status", "version", "state_version", "character_code", "equipped"],
    ),
  }],
  "API-READ-035": [
    { name: "profile_id", schema: { type: "string", format: "uuid" } },
    { name: "classroom_student_id", schema: { type: "string", format: "uuid" } },
    { name: "state_version", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "equipment_changed_at", schema: { type: "string", format: "date-time" } },
    { name: "created_at", schema: { type: "string", format: "date-time" } },
    { name: "updated_at", schema: { type: "string", format: "date-time" } },
    {
      name: "equipped_inventory_items",
      schema: {
        type: "object",
        propertyNames: {
          enum: ["BACKGROUND", "EFFECT", "BACK", "BOTTOM", "SHOES", "TOP", "NECK", "FACE", "HAND", "HAT"],
        },
        additionalProperties: { type: "string", format: "uuid" },
      },
    },
  ],
  "API-READ-013": [{ name: "trades", schema: projectionItems({ counterpart_nickname: { type: "string" }, direction: { type: "string" } }) }],
  "API-READ-014": [{ name: "financial_products", schema: projectionItems({ product_type: { type: "string" }, interest_rate_bps: { type: "integer" } }) }],
  "API-READ-015": [
    { name: "deposit_accounts", schema: projectionItems({ principal_amount: { type: "integer", format: "int64" }, matures_at: { type: "string", format: "date-time" } }) },
    { name: "savings_accounts", schema: projectionItems({ principal_balance: { type: "integer", format: "int64" }, matures_at: { type: "string", format: "date-time" } }) },
  ],
  "API-READ-020": [{
    name: "questions",
    schema: projectionItems({
      content_type: { type: "string", enum: ["ECONOMY", "NONSENSE"] },
      question_type: { type: "string", enum: ["MULTIPLE_CHOICE", "OX"] },
      prompt: { type: "string" },
      explanation: { type: ["string", "null"] },
      source: { type: "string", enum: ["TEACHER", "AI"] },
      ai_run_id: { type: ["string", "null"], format: "uuid" },
      options: {
        type: "array",
        minItems: 2,
        maxItems: 5,
        items: {
          type: "object",
          required: ["option_id", "option_key", "option_text", "correct", "sort_order"],
          properties: {
            option_id: { type: "string", format: "uuid" },
            option_key: { type: "string" },
            option_text: { type: "string" },
            correct: { type: "boolean" },
            sort_order: { type: "integer", minimum: 0 },
          },
          additionalProperties: false,
        },
      },
    }, [
      "resource_id",
      "label",
      "status",
      "version",
      "state_version",
      "content_type",
      "question_type",
      "prompt",
      "explanation",
      "source",
      "ai_run_id",
      "options",
    ]),
  }],
  "API-READ-021": [
    { name: "quiz_session_id", schema: { type: "string", format: "uuid" } },
    { name: "mode", schema: { type: "string", enum: ["GAME", "TEST"] } },
    { name: "status", schema: { type: "string" } },
    { name: "questions", schema: projectionItems({ prompt: { type: "string" }, options: { type: "array", items: { type: "string" } } }) },
  ],
  "API-READ-022": [
    { name: "result_version", schema: { type: "integer", minimum: 1 } },
    { name: "status", schema: { type: "string" } },
    { name: "summary", schema: { type: "object", additionalProperties: { type: ["string", "integer", "boolean"] } } },
    { name: "leaderboard", optional: true, schema: projectionItems({ rank: { type: "integer", minimum: 1, maximum: 3 }, nickname: { type: "string" }, character_code: { type: ["string", "null"] } }) },
  ],
  "API-VOT-001": [
    { name: "vote_id", schema: { type: "string", format: "uuid" } },
    {
      name: "status",
      schema: {
        type: "string",
        enum: ["READY", "OPEN", "CLOSED", "RESULT_PUBLISHED"],
      },
    },
    {
      name: "state_version",
      schema: { type: "integer", format: "int64", minimum: 0 },
    },
    {
      name: "selected_option_id",
      optional: true,
      schema: { type: ["string", "null"], format: "uuid" },
    },
    {
      name: "transaction_group_id",
      optional: true,
      schema: { type: ["string", "null"], format: "uuid" },
    },
  ],
  "API-VOT-002": [
    { name: "receipt_hash", schema: { type: "string" } },
    { name: "cast_at", schema: { type: "string", format: "date-time" } },
  ],
  "API-READ-023": [
    { name: "vote_id", schema: { type: "string", format: "uuid" } },
    { name: "title", schema: { type: "string" } },
    { name: "description", optional: true, schema: { type: ["string", "null"] } },
    { name: "anonymous", schema: { type: "boolean", const: true } },
    {
      name: "status",
      schema: {
        type: "string",
        enum: ["READY", "OPEN", "CLOSED", "RESULT_PUBLISHED"],
      },
    },
    {
      name: "state_version",
      schema: { type: "integer", format: "int64", minimum: 0 },
    },
    { name: "opens_at", optional: true, schema: { type: ["string", "null"], format: "date-time" } },
    { name: "closes_at", optional: true, schema: { type: ["string", "null"], format: "date-time" } },
    { name: "public_fund_balance", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "participant_count", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "ballot_count", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "eligible", optional: true, schema: { type: ["boolean", "null"] } },
    { name: "has_voted", optional: true, schema: { type: ["boolean", "null"] } },
    { name: "selected_option_id", optional: true, schema: { type: ["string", "null"], format: "uuid" } },
    { name: "selected_use", optional: true, schema: { type: ["string", "null"] } },
    { name: "transaction_group_id", optional: true, schema: { type: ["string", "null"], format: "uuid" } },
    {
      name: "options",
      schema: {
        type: "array",
        items: {
          type: "object",
          required: ["option_id", "label", "budget_amount"],
          properties: {
            option_id: { type: "string", format: "uuid" },
            label: { type: "string" },
            description: { type: ["string", "null"] },
            budget_amount: { type: "integer", format: "int64", minimum: 0 },
            vote_count: { type: ["integer", "null"], format: "int64", minimum: 0 },
          },
          additionalProperties: false,
        },
      },
    },
  ],
  "API-READ-028": [
    { name: "classroom_id", schema: { type: "string", format: "uuid" } },
    {
      name: "period",
      schema: {
        type: "object",
        required: ["from", "to"],
        properties: {
          from: { type: "string", format: "date-time" },
          to: { type: "string", format: "date-time" },
        },
        additionalProperties: false,
      },
    },
    { name: "total_currency_amount", schema: { type: "integer", format: "int64" } },
    { name: "wallet_balance_amount", schema: { type: "integer", format: "int64" } },
    { name: "average_wallet_balance", schema: { type: "integer", format: "int64" } },
    { name: "spending_amount", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "savings_principal_amount", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "public_fund_balance", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "active_student_count", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "quiz_participant_count", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "quiz_submission_count", schema: { type: "integer", format: "int64", minimum: 0 } },
    { name: "generated_at", schema: { type: "string", format: "date-time" } },
  ],
  "API-READ-024": [
    { name: "activity_id", schema: { type: "string", format: "uuid" } },
    { name: "status", schema: { type: "string" } },
    { name: "participant_count", schema: { type: "integer", minimum: 0 } },
  ],
  "API-READ-025": [
    { name: "activity_id", schema: { type: "string", format: "uuid" } },
    { name: "scene", schema: { type: "string" } },
    { name: "timer", optional: true, schema: { type: "object", required: ["server_time", "ends_at"], properties: { server_time: { type: "string", format: "date-time" }, ends_at: { type: "string", format: "date-time" } }, additionalProperties: false } },
    { name: "public_data", schema: { type: "object", additionalProperties: { type: ["string", "integer", "boolean", "array", "object", "null"] } } },
  ],
  "API-READ-027": [
    {
      name: "items",
      schema: {
        type: "array",
        items: {
          type: "object",
          required: [
            "ai_run_id",
            "feature_type",
            "run_type",
            "trigger_type",
            "status",
            "requested_at",
            "analysis_period_from",
            "prompt_version",
            "fallback_used",
            "validation_codes",
            "evidence_codes",
            "rule_versions",
            "proposals",
          ],
          properties: {
            ai_run_id: { type: "string", format: "uuid" },
            operation_id: { type: ["string", "null"], format: "uuid" },
            feature_type: {
              type: "string",
              enum: ["BALANCING", "TEACHER_CHAT", "ECONOMIC_EVENT", "QUIZ_DRAFT"],
            },
            run_type: {
              type: "string",
              enum: [
                "SETUP_PRECHECK",
                "ON_DEMAND",
                "AUTO_MONITOR",
                "TEACHER_CHAT",
                "ECONOMIC_EVENT_DRAFT",
                "QUIZ_DRAFT",
              ],
            },
            trigger_type: { type: "string" },
            status: {
              type: "string",
              enum: [
                "REQUESTED",
                "DATA_CHECK",
                "INSUFFICIENT_DATA",
                "GENERATING",
                "VALIDATING",
                "AWAITING_TEACHER",
                "ACKNOWLEDGED",
                "COMPLETED",
                "FAILED",
                "TIMED_OUT",
                "REJECTED_OUTPUT",
                "APPROVED",
                "REJECTED",
                "EXPIRED",
                "APPLYING",
                "APPLY_FAILED",
              ],
            },
            requested_at: { type: "string", format: "date-time" },
            completed_at: { type: ["string", "null"], format: "date-time" },
            analysis_period_from: { type: ["string", "null"], format: "date-time" },
            data_as_of: { type: ["string", "null"], format: "date-time" },
            prompt_version: { type: "string" },
            provider: { type: ["string", "null"] },
            model_id: { type: ["string", "null"] },
            safety_status: { type: ["string", "null"] },
            fallback_used: { type: "boolean" },
            fallback_code: { type: ["string", "null"] },
            severity: {
              type: ["string", "null"],
              enum: ["PASS", "NORMAL", "OBSERVE", "WARN", "BLOCK", null],
            },
            validation_codes: { type: "array", items: { type: "string" }, uniqueItems: true },
            evidence_codes: { type: "array", items: { type: "string" }, uniqueItems: true },
            rule_versions: {
              type: "object",
              additionalProperties: { type: "string" },
            },
            proposals: {
              type: "array",
              maxItems: 100,
              items: {
                type: "object",
                required: [
                  "proposal_id",
                  "proposal_type",
                  "source_type",
                  "status",
                  "summary",
                  "proposed_values",
                  "allowed_ranges",
                  "created_at",
                  "expires_at",
                  "status_changed_at",
                  "state_version",
                ],
                properties: {
                  proposal_id: { type: "string", format: "uuid" },
                  proposal_type: { type: "string" },
                  source_type: { type: "string" },
                  status: { type: "string" },
                  severity: {
                    type: ["string", "null"],
                    enum: ["PASS", "NORMAL", "OBSERVE", "WARN", "BLOCK", null],
                  },
                  metric_code: { type: ["string", "null"] },
                  direction: { type: ["string", "null"] },
                  dedupe_key: { type: ["string", "null"] },
                  summary: { type: "string", maxLength: 1500 },
                  context_type: { type: ["string", "null"] },
                  target_type: { type: ["string", "null"] },
                  target_id: { type: ["string", "null"], format: "uuid" },
                  target_version: {
                    type: ["integer", "null"],
                    format: "int64",
                    minimum: 0,
                  },
                  proposed_values: {
                    type: "array",
                    maxItems: 100,
                    items: {
                      type: "object",
                      required: [
                        "action_id",
                        "target_type",
                        "target_id",
                        "current_value",
                        "proposed_value",
                        "unit",
                        "label",
                        "side_effects",
                        "evidence_metric_codes",
                      ],
                      properties: {
                        action_id: { type: ["string", "null"] },
                        target_type: { type: ["string", "null"] },
                        target_id: { type: ["string", "null"], format: "uuid" },
                        current_value: {
                          type: ["integer", "null"],
                          format: "int64",
                        },
                        proposed_value: {
                          type: ["integer", "null"],
                          format: "int64",
                        },
                        unit: { type: ["string", "null"] },
                        label: { type: ["string", "null"] },
                        rationale: { type: ["string", "null"] },
                        expected_effect: { type: ["string", "null"] },
                        side_effects: { type: "array", items: { type: "string" } },
                        evidence_metric_codes: { type: "array", items: { type: "string" } },
                        priority: { type: ["integer", "null"] },
                      },
                      additionalProperties: false,
                    },
                  },
                  allowed_ranges: {
                    type: "array",
                    maxItems: 100,
                    items: {
                      type: "object",
                      required: [
                        "action_id",
                        "target_type",
                        "target_id",
                        "current_value",
                        "allowed_min_value",
                        "allowed_max_value",
                      ],
                      properties: {
                        action_id: { type: ["string", "null"] },
                        target_type: { type: ["string", "null"] },
                        target_id: { type: ["string", "null"], format: "uuid" },
                        current_value: {
                          type: ["integer", "null"],
                          format: "int64",
                        },
                        allowed_min_value: {
                          type: ["integer", "null"],
                          format: "int64",
                        },
                        allowed_max_value: {
                          type: ["integer", "null"],
                          format: "int64",
                        },
                      },
                      additionalProperties: false,
                    },
                  },
                  created_at: { type: "string", format: "date-time" },
                  expires_at: { type: "string", format: "date-time" },
                  status_changed_at: { type: "string", format: "date-time" },
                  decision_status: { type: ["string", "null"] },
                  decided_at: { type: ["string", "null"], format: "date-time" },
                  policy_version: { type: ["integer", "null"], minimum: 1 },
                  applied_policy_version_id: {
                    type: ["string", "null"],
                    format: "uuid",
                  },
                  state_version: {
                    type: "integer",
                    format: "int64",
                    minimum: 0,
                  },
                },
                additionalProperties: false,
              },
            },
          },
          additionalProperties: false,
        },
      },
    },
  ],
  "API-READ-029": [
    {
      name: "items",
      schema: {
        type: "array",
        items: {
          type: "object",
          required: [
            "audit_event_id",
            "actor_type",
            "action",
            "target_type",
            "occurred_at",
          ],
          properties: {
            audit_event_id: { type: "string", format: "uuid" },
            actor_type: { type: "string" },
            actor_id: { type: ["string", "null"], format: "uuid" },
            action: { type: "string" },
            target_type: { type: "string" },
            target_id: { type: ["string", "null"], format: "uuid" },
            request_id: { type: ["string", "null"] },
            correlation_id: { type: ["string", "null"] },
            before_summary: { type: ["object", "null"], additionalProperties: true },
            after_summary: { type: ["object", "null"], additionalProperties: true },
            occurred_at: { type: "string", format: "date-time" },
          },
          additionalProperties: false,
        },
      },
    },
  ],
  "API-READ-030": [
    { name: "operation_id", schema: { type: "string", format: "uuid" } },
    { name: "operation_type", schema: { type: "string" } },
    {
      name: "status",
      schema: { type: "string", enum: ["RUNNING", "COMPLETED", "FAILED"] },
    },
    { name: "progress_percent", schema: { type: "integer", minimum: 0, maximum: 100 } },
    {
      name: "result_url",
      optional: true,
      schema: { type: ["string", "null"], format: "uri-reference" },
    },
    { name: "error_code", optional: true, schema: { type: ["string", "null"] } },
    { name: "started_at", schema: { type: "string", format: "date-time" } },
    {
      name: "completed_at",
      optional: true,
      schema: { type: ["string", "null"], format: "date-time" },
    },
    {
      name: "expires_at",
      optional: true,
      schema: { type: ["string", "null"], format: "date-time" },
    },
    {
      name: "state_version",
      schema: { type: "integer", format: "int64", minimum: 0 },
    },
  ],
};

const resourceIdByDomain = {
  MOD: "module_id",
  POL: "policy_version_id",
  SHP: "product_id",
  ITM: "inventory_item_id",
  CNT: "character_source_id",
  TRD: "trade_id",
  FIN: "financial_resource_id",
  EST: "property_id",
  QZ: "quiz_resource_id",
  VOT: "vote_id",
  AI: "ai_resource_id",
  ADM: "administration_resource_id",
};

function responseFields(operation) {
  const explicit = responseFieldOverrides[operation.apiId];
  if (explicit) return explicit;
  const resourceName = resourceIdByDomain[operation.apiId.split("-")[1]] ?? "resource_id";
  return [
    { name: resourceName, schema: { type: "string", format: "uuid" } },
    { name: "status", schema: { type: "string" } },
    { name: "state_version", optional: true, schema: { type: "integer", format: "int64", minimum: 0 } },
  ];
}

const storageByDomain = {
  AUTH: ["classrooms", "classroom_students", "student_credentials", "student_sessions"],
  CRS: ["course_templates", "course_template_versions", "course_template_modules"],
  CLS: ["classrooms", "classroom_teachers", "classroom_students", "salary_roles", "classroom_modules", "autonomous_schedules", "autonomous_schedule_modules"],
  MOD: ["classroom_modules", "state_transitions"],
  POL: ["classroom_policy_versions", "policy_snapshots"],
  ACT: ["activity_sessions", "activity_participants", "realtime_streams", "outbox_events", "audit_events"],
  DSP: ["activity_sessions", "redis.display-session", "realtime_streams", "outbox_events", "audit_events"],
  TXN: ["transaction_groups", "ledger_entries", "ledger_accounts", "policy_snapshots"],
  SHP: ["store_products", "store_product_versions", "store_stocks", "store_orders", "store_order_items"],
  ITM: ["inventory_items", "student_character_profiles", "character_equipment"],
  CNT: ["character_catalog", "classroom_characters"],
  TRD: ["trades", "trade_money_legs", "trade_item_legs", "trade_decisions", "transaction_groups"],
  FIN: ["financial_products", "financial_product_versions", "deposit_accounts", "savings_accounts", "investment_orders", "loans", "credit_profiles", "transaction_groups"],
  EST: ["property_layouts", "properties", "property_contracts", "property_contract_payments", "property_lease_listings", "subscription_offers", "subscription_accounts", "subscription_auto_deposit_policies", "subscription_applications", "ledger_accounts", "ledger_entries", "transaction_groups"],
  QZ: ["quiz_questions", "quiz_question_versions", "quiz_options", "quiz_sessions", "quiz_submissions", "quiz_answers", "quiz_result_versions"],
  VOT: ["votes", "vote_options", "vote_eligibilities", "vote_ballots", "view.public_vote_aggregates"],
  AI: ["ai_runs", "ai_artifacts", "ai_proposals", "ai_decisions", "economic_event_drafts", "classroom_policy_versions"],
  DAT: ["async_operations", "export_operations", "report_snapshots"],
  ADM: ["schools", "school_memberships", "classroom_teachers", "school_ai_setting_versions"],
  OPS: ["external_secrets_manager", "audit_events"],
};

const storageByReadApi = {
  "API-READ-001": ["users", "school_memberships", "classroom_teachers", "classroom_students", "classrooms"],
  "API-READ-002": ["classrooms", "course_template_versions"],
  "API-READ-003": ["classrooms", "classroom_modules", "autonomous_schedules", "autonomous_schedule_modules", "classroom_policy_versions"],
  "API-READ-004": ["classroom_students", "salary_roles"],
  "API-READ-006": ["classroom_modules", "classroom_policy_versions"],
  "API-READ-007": ["ledger_accounts", "ledger_entries", "transaction_groups"],
  "API-READ-008": ["payslips"],
  "API-READ-009": ["transaction_groups", "ledger_entries", "transaction_business_refs"],
  "API-READ-010": ["store_products", "store_product_versions", "store_stocks", "classroom_characters"],
  "API-READ-011": ["store_orders", "store_order_items"],
  "API-READ-012": ["inventory_items", "character_catalog", "student_character_profiles", "character_equipment"],
  "API-READ-035": ["student_character_profiles", "character_equipment"],
  "API-READ-013": ["trades", "trade_money_legs", "trade_item_legs", "trade_decisions"],
  "API-READ-014": ["financial_products", "financial_product_versions"],
  "API-READ-015": ["deposit_accounts", "savings_accounts", "savings_installments"],
  "API-READ-020": ["quiz_questions", "quiz_question_versions", "quiz_options"],
  "API-READ-021": ["quiz_sessions", "quiz_session_questions", "quiz_question_versions", "quiz_options"],
  "API-READ-022": ["quiz_result_versions", "quiz_student_results", "view.public_quiz_leaderboard"],
  "API-READ-023": [
    "votes",
    "vote_options",
    "vote_eligibilities",
    "vote_ballots",
    "ledger_accounts",
    "transaction_groups",
    "transaction_business_refs",
  ],
  "API-READ-024": ["activity_sessions", "activity_participants"],
  "API-READ-025": ["activity_sessions", "activity_participants", "redis.display-session", "realtime_streams", "outbox_events"],
  "API-READ-027": ["ai_runs", "ai_artifacts", "ai_proposals", "ai_decisions", "economic_event_drafts"],
  "API-READ-028": [
    "classroom_students",
    "ledger_accounts",
    "ledger_entries",
    "transaction_groups",
    "deposit_accounts",
    "savings_accounts",
    "quiz_sessions",
    "quiz_submissions",
  ],
  "API-READ-029": ["audit_events"],
  "API-READ-030": ["async_operations"],
};

function storageTargets(operation) {
  if (operation.apiId === "API-AUTH-001") {
    return [
      "classrooms",
      "classroom_students",
      "student_credentials",
      "device_links",
      "student_sessions",
      "ledger_accounts",
    ];
  }
  if (operation.apiId === "API-AUTH-002") return ["classrooms", "classroom_students", "redis.password-reset"];
  if (operation.apiId === "API-AUTH-003") return ["redis.password-reset", "student_credentials", "student_sessions"];
  if (operation.apiId === "API-AUTH-004") return ["redis.teacher-session", "student_sessions"];
  if (operation.apiId === "API-AUTH-005") return ["redis.teacher-session", "student_sessions"];
  if (operation.apiId === "API-AUTH-006") {
    return [
      "users",
      "school_memberships",
      "classroom_teachers",
      "classrooms",
      "classroom_students",
      "student_sessions",
      "redis.teacher-session",
    ];
  }
  if (operation.apiId === "API-AUTH-007") return ["redis.realtime-ticket"];
  if (operation.apiId === "API-AUTH-008") {
    return [
      "external_idp.id-token",
      "users",
      "school_memberships",
      "classroom_teachers",
      "redis.teacher-session",
    ];
  }
  if (operation.apiId === "API-DSP-001") {
    return [
      "activity_sessions",
      "redis.display-session",
      "realtime_streams",
      "outbox_events",
      "audit_events",
    ];
  }
  if (operation.apiId === "API-CLS-007") {
    return [
      "classrooms",
      "classroom_policy_versions",
      "classroom_modules",
      "autonomous_schedules",
      "autonomous_schedule_modules",
      "activity_sessions",
      "realtime_streams",
      "outbox_events",
      "audit_events",
      "ai_runs",
    ];
  }
  if (operation.apiId === "API-TRD-004") {
    return [
      "trades",
      "trade_money_legs",
      "trade_item_legs",
      "trade_decisions",
      "transaction_groups",
      "realtime_streams",
      "outbox_events",
      "audit_events",
    ];
  }
  if (operation.apiId === "API-ADM-004") return ["school_ai_setting_versions", "audit_events"];
  if (operation.apiId === "API-AI-005") return ["classroom_policy_versions", "audit_events"];
  if (operation.apiId === "API-QZ-008") {
    return [
      "quiz_sessions",
      "quiz_session_questions",
      "activity_participants",
      "quiz_question_issues",
      "idempotency_records",
    ];
  }
  if (operation.apiId === "API-QZ-009") {
    return [
      "quiz_sessions",
      "quiz_session_questions",
      "quiz_submissions",
      "quiz_answers",
      "quiz_result_versions",
      "quiz_student_results",
      "quiz_question_issues",
      "regrading_runs",
      "transaction_groups",
      "ledger_entries",
      "idempotency_records",
    ];
  }
  if (operation.apiId.startsWith("API-READ-")) return storageByReadApi[operation.apiId] ?? ["service_projection"];
  return storageByDomain[operation.apiId.split("-")[1]] ?? ["service_projection"];
}

const exactFieldTargets = {
  classroom_code: "classrooms.classroom_code",
  currency_name: "classrooms.currency_name",
  wallet_balance: "ledger_accounts.current_balance",
  student_count: "classrooms.planned_student_count",
  planned_student_count: "classrooms.planned_student_count",
  attendance_number: "classroom_students.roster_number",
  course_size: "classrooms.scale_label",
  starts_on: "classrooms.actual_starts_on",
  ends_on: "classrooms.actual_ends_on",
  timezone: "classrooms.time_zone",
  role_code: "salary_roles.role_code",
  module_codes: "autonomous_schedule_modules.module_code",
  expected_state_version: "service_validation.state_version",
  expected_policy_version: "service_validation.policy_version",
  password: "student_credentials.password_hash",
  new_password: "student_credentials.password_hash",
  ticket: "redis.realtime-ticket.token_hash",
  display_token: "redis.display-session.token_hash",
  reset_request_id: "redis.password-reset.request_id",
  id_token: "external_idp.id-token",
};

function schemaType(schema) {
  if (schema.$ref) return schema.$ref;
  return Array.isArray(schema.type) ? schema.type.join("|") : schema.type ?? "any";
}

function manifestField(field, direction, targets) {
  const target = exactFieldTargets[field.name] ??
    (direction === "request"
      ? `service_input.${field.name}`
      : `projection.${field.name}`);
  return {
    name: field.name,
    required: !field.optional,
    type: schemaType(schemaForField(field)),
    target,
    transform: exactFieldTargets[field.name]
      ? "명시된 이름·자료형 변환 후 저장 또는 조회"
      : `${targets.join(", ")}에서 검증·조합하는 ${direction} 필드`,
    nullable: Boolean(field.optional),
  };
}

function requestSchema(operation) {
  if (operation.apiId === "API-AUTH-001") {
    return { $ref: "#/components/schemas/StudentSessionRequest" };
  }
  if (operation.apiId === "API-AUTH-008") {
    return { $ref: "#/components/schemas/TeacherSessionRequest" };
  }

  const strict = implementationStatus(operation) === "mvp-contract";
  const domainFields = requestFieldOverrides[operation.apiId] ?? extractEnglishFields(operation.contract);
  const fields = [...mutationContextFor(operation), ...domainFields];
  const properties = {};
  const required = [];
  for (const field of fields) {
    properties[field.name] = schemaForField(field);
    if (!field.optional) required.push(field.name);
  }

  return {
    type: "object",
    description: strict
      ? `HTTP_API_SPEC.md의 ${operation.apiId} MVP 입력 계약입니다. 선언하지 않은 최상위 필드는 거절합니다.`
      : `${operation.apiId}는 후속 범위이며 구현 스프린트에서 세부 DTO를 확정합니다.`,
    properties,
    ...(required.length > 0 ? { required } : {}),
    additionalProperties: !strict,
  };
}

function successResponseSchema(operation) {
  const fields = responseFields(operation);
  if (operation.apiId === "API-AUTH-004") {
    const dataSchema = (selectedFields, role) => {
      const properties = {};
      const required = [];
      for (const field of selectedFields) {
        properties[field.name] = field.name === "role"
          ? { type: "string", enum: [role] }
          : schemaForField(field);
        required.push(field.name);
      }
      return {
        type: "object",
        title: role === "STU" ? "StudentTokenRefresh" : "TeacherTokenRefresh",
        required,
        properties,
        additionalProperties: false,
      };
    };
    return {
      type: "object",
      required: ["data", "meta"],
      properties: {
        data: {
          oneOf: [
            dataSchema(fields, "STU"),
            dataSchema(fields.filter((field) => field.name !== "refresh_token"), "TCH"),
          ],
        },
        meta: { $ref: "#/components/schemas/Meta" },
      },
      additionalProperties: false,
    };
  }
  const properties = {};
  const required = [];
  for (const field of fields) {
    properties[field.name] = schemaForField(field);
    if (!field.optional) required.push(field.name);
  }
  return {
    type: "object",
    required: ["data", "meta"],
    properties: {
      data: {
        type: "object",
        required,
        properties,
        additionalProperties: false,
      },
      meta: { $ref: "#/components/schemas/Meta" },
    },
    additionalProperties: false,
  };
}

function tagForApi(apiId) {
  const domain = apiId.split("-")[1];
  const tags = {
    AUTH: "Authentication",
    CRS: "Course recommendations",
    CLS: "Classrooms",
    MOD: "Modules",
    POL: "Policies",
    ACT: "Guided activities",
    DSP: "Public display",
    TXN: "Ledger",
    SHP: "Store",
    ITM: "Inventory",
    CNT: "Characters",
    TRD: "Trades",
    FIN: "Finance",
    EST: "Property",
    QZ: "Quiz",
    VOT: "Vote",
    AI: "AI",
    DAT: "Data",
    ADM: "School administration",
    OPS: "Platform operations",
    READ: "Queries",
  };
  return tags[domain] ?? domain;
}

function successStatus(operation) {
  if (operation.method === "DELETE") return "204";
  if (
    operation.contract.includes("202 operation_id") ||
    ["API-CLS-008", "API-QZ-002", "API-QZ-009", "API-AI-002", "API-DAT-001", "API-DAT-002"].includes(
      operation.apiId,
    )
  ) {
    return "202";
  }
  if (operation.apiId === "API-AI-001") return "200";
  if (
    operation.method === "POST" &&
    !operation.path.endsWith("/commands") &&
    !operation.path.includes("-commands") &&
    !operation.path.includes("decisions") &&
    !operation.path.includes("confirmations")
  ) {
    return "201";
  }
  return "200";
}

function parameterRefs(operation) {
  const parameters = [{ $ref: "#/components/parameters/XCorrelationId" }];
  const names = [...operation.path.matchAll(/\{([^}]+)\}/g)].map((match) => match[1]);
  for (const name of names) {
    const componentName = `${name
      .split("_")
      .map((part) => part[0].toUpperCase() + part.slice(1))
      .join("")}Path`;
    parameters.push({ $ref: `#/components/parameters/${componentName}` });
  }
  if (operation.method !== "GET") {
    parameters.push({ $ref: "#/components/parameters/IdempotencyKey" });
  }
  if (operation.apiId === "API-CRS-001") {
    parameters.push(
      {
        name: "grade",
        in: "query",
        required: false,
        schema: { type: "integer", enum: [4, 5, 6] },
        description: "course_size와 함께 지정하거나 둘 다 생략합니다.",
      },
      {
        name: "course_size",
        in: "query",
        required: false,
        schema: {
          type: "string",
          enum: ["ONE_SESSION", "ONE_WEEK", "ONE_MONTH", "SEMESTER"],
        },
        description: "grade와 함께 지정하거나 둘 다 생략합니다.",
      },
    );
  }
  if (operation.apiId === "API-READ-028") {
    parameters.push(
      {
        name: "from",
        in: "query",
        required: true,
        schema: { type: "string", format: "date-time" },
      },
      {
        name: "to",
        in: "query",
        required: true,
        schema: { type: "string", format: "date-time" },
      },
    );
  }
  if (["API-EST-007", "API-EST-018", "API-EST-021"].includes(operation.apiId)) {
    parameters.push({
      name: "classroom_id",
      in: "query",
      required: true,
      schema: { type: "string", format: "uuid" },
    });
  }

  const pagedQueries = new Set([
    "API-READ-008",
    "API-READ-009",
    "API-READ-011",
    "API-READ-013",
    "API-READ-014",
    "API-READ-015",
    "API-READ-019",
    "API-READ-020",
    "API-READ-027",
    "API-READ-029",
    "API-READ-031",
    "API-READ-032",
    "API-READ-034",
  ]);
  if (pagedQueries.has(operation.apiId)) {
    parameters.push(
      { $ref: "#/components/parameters/Cursor" },
      { $ref: "#/components/parameters/Limit" },
    );
  }
  return parameters;
}

function securityFor(operation) {
  if (["API-AUTH-001", "API-AUTH-002", "API-AUTH-008"].includes(operation.apiId)) return [];
  if (operation.apiId === "API-AUTH-004")
    return [{ refreshCookie: [] }, { refreshHeader: [] }];
  if (operation.apiId === "API-READ-025") return [{ displayBearer: [] }];
  return [{ bearerAuth: [] }];
}

function buildOpenApi(operations) {
  const paths = {};
  for (const operation of operations) {
    const method = operation.method.toLowerCase();
    const status = successStatus(operation);
    const contractStatus = implementationStatus(operation);
    const targets = storageTargets(operation);
    const op = {
      operationId: camelFromId(operation.apiId),
      "x-api-id": operation.apiId,
      "x-implementation-status": contractStatus,
      "x-schema-closure": contractStatus === "mvp-contract" ? "strict" : "planned",
      "x-storage-targets": targets,
      ...(operation.uiActionIds.length > 0 ? { "x-ui-action-ids": operation.uiActionIds } : {}),
      "x-roles": operation.roles ? operation.roles.split(/,\s*|·/) : [],
      "x-permission-ids": operation.permissionIds,
      "x-state-machine-ids": operation.stateMachineIds,
      "x-rtm-ids": operation.rtmIds,
      tags: [tagForApi(operation.apiId)],
      summary: operation.contract || operation.apiId,
      description: [
        `사람이 검토하는 원문 기준: \`${operation.apiId}\` in \`docs/HTTP_API_SPEC.md\`.`,
        operation.roles ? `허용 역할: ${operation.roles}.` : "",
        operation.contract ? `핵심 계약: ${operation.contract}.` : "",
        operation.apiId === "API-AUTH-004"
          ? "학생 앱은 X-Refresh-Token 헤더를 사용하고 새 갱신 토큰을 본문으로 받습니다. 교사 웹은 HttpOnly 갱신 쿠키를 사용하며 갱신 토큰을 본문으로 받지 않습니다."
          : "",
      ]
        .filter(Boolean)
        .join("\n\n"),
      security: securityFor(operation),
      parameters: parameterRefs(operation),
      responses: {
        [status]:
          status === "204"
            ? { description: "성공적으로 처리했으며 응답 본문이 없습니다." }
            : status === "202" && !Object.hasOwn(responseFieldOverrides, operation.apiId) && operation.apiId !== "API-AI-002"
              ? { $ref: "#/components/responses/Accepted" }
              : contractStatus === "mvp-contract"
                ? {
                    description: "MVP 작업별 명시적 성공 응답",
                    content: { "application/json": { schema: successResponseSchema(operation) } },
                  }
                : { $ref: "#/components/responses/Success" },
        "400": { $ref: "#/components/responses/BadRequest" },
        "401": { $ref: "#/components/responses/Unauthorized" },
        "403": { $ref: "#/components/responses/Forbidden" },
        "404": { $ref: "#/components/responses/NotFound" },
        "409": { $ref: "#/components/responses/Conflict" },
        "422": { $ref: "#/components/responses/UnprocessableEntity" },
        "429": { $ref: "#/components/responses/TooManyRequests" },
        "500": { $ref: "#/components/responses/InternalError" },
        "503": { $ref: "#/components/responses/ServiceUnavailable" },
      },
    };

    if (["API-AUTH-004", "API-AUTH-008"].includes(operation.apiId)) {
      op.responses[status].headers = {
        "Set-Cookie": {
          description: "교사 웹에서만 사용하는 HttpOnly 회전형 갱신 쿠키. 학생 응답에는 설정하지 않습니다.",
          schema: { type: "string" },
        },
      };
    }

    if (!["GET", "DELETE"].includes(operation.method) && operation.apiId !== "API-AUTH-004") {
      op.requestBody = {
        required: true,
        content: {
          "application/json": {
            schema: requestSchema(operation),
          },
        },
      };
    }

    paths[operation.path] ??= {};
    paths[operation.path][method] = op;
  }

  const pathParameters = {};
  const pathNames = unique(
    operations.flatMap((operation) =>
      [...operation.path.matchAll(/\{([^}]+)\}/g)].map((match) => match[1]),
    ),
  );
  for (const name of pathNames) {
    const componentName = `${name
      .split("_")
      .map((part) => part[0].toUpperCase() + part.slice(1))
      .join("")}Path`;
    pathParameters[componentName] = {
      name,
      in: "path",
      required: true,
      description: `${name} 식별자`,
      schema: {
        type: "string",
        ...(name === "module_code" ? {} : { format: "uuid" }),
      },
    };
  }

  return {
    openapi: "3.1.0",
    jsonSchemaDialect: "https://json-schema.org/draft/2020-12/schema",
    info: {
      title: "이코빌리지 Core HTTP API",
      version: "0.1.0",
      summary: "학생 앱, 교사용 앱·웹, 학교 관리자와 플랫폼 운영자가 사용하는 REST API",
      description:
        "HTTP_API_SPEC.md의 110개 작업을 기계 판독 계약으로 고정한 기준선입니다. 모든 변경은 멱등성 키를 사용하고, 금액·세금·잔액·권한은 서버가 다시 계산합니다.",
      license: { name: "Proprietary", identifier: "LicenseRef-Proprietary" },
    },
    servers: [
      { url: "https://api.ecovillage.kr/api/v1", description: "운영 환경 예시" },
      { url: "https://api.dev.ecovillage.kr/api/v1", description: "개발 환경 예시" },
    ],
    tags: unique(operations.map((operation) => tagForApi(operation.apiId))).map((name) => ({
      name,
      description: `${name} 영역의 HTTP 작업`,
    })),
    paths,
    components: {
      securitySchemes: {
        bearerAuth: {
          type: "http",
          scheme: "bearer",
          bearerFormat: "JWT",
          description: "학생 Expo 앱과 교사·관리자 웹이 메모리에 유지하는 짧은 수명 액세스 토큰",
        },
        refreshCookie: {
          type: "apiKey",
          in: "cookie",
          name: "ecovillage_teacher_refresh",
          description: "교사·관리자 웹의 회전형 갱신 토큰",
        },
        refreshHeader: {
          type: "apiKey",
          in: "header",
          name: "X-Refresh-Token",
          description: "Expo SecureStore에서 읽어 전송하는 학생 앱의 회전형 갱신 토큰",
        },
        displayBearer: {
          type: "http",
          scheme: "bearer",
          bearerFormat: "Opaque",
          description: "교사가 발급한 단기·단일 활동 공용 화면 토큰",
        },
      },
      parameters: {
        XCorrelationId: {
          name: "X-Correlation-Id",
          in: "header",
          required: false,
          description: "분산 추적 ID. 생략하면 게이트웨이가 생성합니다.",
          schema: { type: "string", format: "uuid" },
        },
        IdempotencyKey: {
          name: "Idempotency-Key",
          in: "header",
          required: true,
          description: "사용자 행동마다 새로 생성하는 UUID. 같은 본문의 안전한 재시도에 재사용합니다.",
          schema: { type: "string", format: "uuid" },
        },
        Cursor: {
          name: "cursor",
          in: "query",
          required: false,
          schema: { type: "string" },
        },
        Limit: {
          name: "limit",
          in: "query",
          required: false,
          schema: { type: "integer", minimum: 1, maximum: 100, default: 20 },
        },
        ...pathParameters,
      },
      schemas: {
        StudentSessionRequest: {
          type: "object",
          required: [
            "classroom_code",
            "attendance_number",
            "nickname",
            "password",
            "device_id",
          ],
          properties: {
            classroom_code: {
              type: "string",
              minLength: 8,
              maxLength: 8,
              pattern: "^[A-HJ-KM-NP-Za-hj-km-np-z2-9]{8}$",
              description: "앞뒤 공백 제거·영문 대문자 정규화 후 8자 학급 코드",
            },
            attendance_number: { type: "integer", minimum: 1 },
            nickname: { type: "string", minLength: 1, maxLength: 40 },
            password: { type: "string", format: "password", minLength: 8, writeOnly: true },
            device_id: { type: "string", format: "uuid" },
          },
          additionalProperties: false,
        },
        TeacherSessionRequest: {
          type: "object",
          required: ["id_token"],
          properties: {
            id_token: {
              type: "string",
              minLength: 1,
              maxLength: 20000,
              writeOnly: true,
              description: "Authorization Code + PKCE 완료 후 외부 OIDC 제공자가 발급한 ID 토큰",
            },
          },
          additionalProperties: false,
        },
        Period: {
          type: "object",
          required: ["from", "to"],
          properties: {
            from: { type: "string", format: "date-time" },
            to: { type: "string", format: "date-time" },
          },
          additionalProperties: false,
        },
        Meta: {
          type: "object",
          required: ["request_id", "correlation_id", "server_time"],
          properties: {
            request_id: { type: "string", format: "uuid" },
            correlation_id: { type: "string", format: "uuid" },
            server_time: { type: "string", format: "date-time" },
            event_sequence: { type: "integer", format: "int64", minimum: 0 },
            page: { $ref: "#/components/schemas/PageMeta" },
          },
          additionalProperties: false,
        },
        PageMeta: {
          type: "object",
          required: ["has_more"],
          properties: {
            next_cursor: { type: ["string", "null"] },
            has_more: { type: "boolean" },
          },
          additionalProperties: false,
        },
        SuccessResponse: {
          type: "object",
          required: ["data", "meta"],
          properties: {
            data: { type: ["object", "array"], additionalProperties: true },
            meta: { $ref: "#/components/schemas/Meta" },
          },
          additionalProperties: false,
        },
        AsyncOperationAcceptedResponse: {
          type: "object",
          required: ["data", "meta"],
          properties: {
            data: {
              type: "object",
              required: ["operation_id", "status", "status_url"],
              properties: {
                operation_id: { type: "string", format: "uuid" },
                status: { type: "string", enum: ["QUEUED", "RUNNING"] },
                status_url: { type: "string", format: "uri-reference" },
              },
              additionalProperties: false,
            },
            meta: { $ref: "#/components/schemas/Meta" },
          },
          additionalProperties: false,
        },
        ErrorDetail: {
          type: "object",
          required: ["reason"],
          properties: {
            field: { type: "string" },
            reason: { type: "string" },
            current_value: true,
          },
          additionalProperties: false,
        },
        ErrorResponse: {
          type: "object",
          required: ["error", "meta"],
          properties: {
            error: {
              type: "object",
              required: ["code", "message", "retryable"],
              properties: {
                code: { type: "string", pattern: "^[A-Z][A-Z0-9_]+$" },
                message: { type: "string" },
                retryable: { type: "boolean" },
                details: {
                  type: "array",
                  items: { $ref: "#/components/schemas/ErrorDetail" },
                },
              },
              additionalProperties: false,
            },
            meta: { $ref: "#/components/schemas/Meta" },
          },
          additionalProperties: false,
        },
      },
      responses: {
        Success: {
          description: "성공",
          content: {
            "application/json": { schema: { $ref: "#/components/schemas/SuccessResponse" } },
          },
        },
        Accepted: {
          description: "비동기 작업 접수",
          content: {
            "application/json": {
              schema: { $ref: "#/components/schemas/AsyncOperationAcceptedResponse" },
            },
          },
        },
        BadRequest: errorResponse("요청 형식 또는 필수 입력 오류"),
        Unauthorized: errorResponse("인증 필요 또는 세션 만료"),
        Forbidden: errorResponse("역할·소속·모듈·앱 모드 정책 위반"),
        NotFound: errorResponse("리소스가 없거나 접근 범위 밖"),
        Conflict: errorResponse("멱등성·버전·상태 전이 충돌"),
        UnprocessableEntity: errorResponse("잔액·재고·한도·정책 조건 불충족"),
        TooManyRequests: errorResponse("요청 또는 AI 사용 한도 초과", {
          "Retry-After": {
            description: "재시도까지 기다릴 초",
            schema: { type: "integer", minimum: 1 },
          },
        }),
        InternalError: errorResponse("내부 처리 오류"),
        ServiceUnavailable: errorResponse("의존 서비스 또는 비동기 큐 사용 불가"),
      },
    },
  };
}

function errorResponse(description, headers = undefined) {
  return {
    description,
    ...(headers ? { headers } : {}),
    content: {
      "application/json": { schema: { $ref: "#/components/schemas/ErrorResponse" } },
    },
  };
}

const eventPayloadOverrides = {
  "EVT-QZ-007": [
    { name: "result_type", values: ["GAME_PUBLIC", "TEST_PRIVATE"] },
    { name: "result_version" },
    { name: "score", schema: { type: "integer", minimum: 0 } },
    { name: "rank", optional: true, schema: { type: "integer", minimum: 1 } },
  ],
  "EVT-DSP-001": [
    { name: "scene", values: ["WAITING", "QUIZ", "VOTE", "EVENT", "RESULT"] },
    { name: "title", optional: true },
  ],
  "EVT-DSP-002": [
    { name: "server_time" },
    { name: "ends_at" },
    { name: "paused" },
  ],
  "EVT-DSP-003": [
    { name: "question_id" },
    { name: "number" },
    { name: "prompt" },
    {
      name: "options",
      schema: {
        type: "array",
        minItems: 2,
        items: {
          type: "object",
          required: ["option_id", "label"],
          properties: {
            option_id: { type: "string", format: "uuid" },
            label: { type: "string" },
          },
          additionalProperties: false,
        },
      },
    },
  ],
  "EVT-DSP-004": [
    { name: "submitted_count" },
    { name: "total_count" },
    { name: "option_counts" },
  ],
  "EVT-DSP-005": [
    { name: "result_type", values: ["GAME", "VOTE", "EVENT"] },
    { name: "title" },
    {
      name: "winners",
      optional: true,
      schema: {
        type: "array",
        maxItems: 3,
        items: {
          type: "object",
          required: ["rank", "nickname"],
          properties: {
            rank: { type: "integer", minimum: 1, maximum: 3 },
            nickname: { type: "string" },
            equipped_character_id: { type: "string", format: "uuid" },
          },
          additionalProperties: false,
        },
      },
    },
    {
      name: "result_summary",
      optional: true,
      schema: {
        type: "object",
        description: "교사가 공개 승인한 투표·경제 이벤트 결과 요약",
        additionalProperties: true,
      },
    },
  ],
  "EVT-DSP-006": [{ name: "reason_code" }],
};

function eventFields(event) {
  if (eventPayloadOverrides[event.eventId]) return eventPayloadOverrides[event.eventId];
  const fields = extractEnglishFields(event.payloadText);
  if (event.eventId === "EVT-VOT-001") {
    const multipleChoice = fields.find((field) => field.name === "multiple_choice");
    if (multipleChoice) multipleChoice.values = ["false"];
  }
  return fields;
}

function eventMessage(event) {
  const fields = eventFields(event);
  const properties = {};
  const required = [];
  for (const field of fields) {
    properties[field.name] = schemaForField(field);
    if (!field.optional) required.push(field.name);
  }

  return {
    name: event.eventType,
    title: event.eventId,
    summary: event.payloadText,
    contentType: "application/json",
    correlationId: { $ref: "#/components/correlationIds/correlationId" },
    "x-event-id": event.eventId,
    "x-channel-ids": event.channelIds,
    "x-origin-api-ids": event.apiIds,
    "x-state-machine-ids": event.stateMachineIds,
    "x-rtm-ids": event.rtmIds,
    payload: {
      allOf: [
        { $ref: "#/components/schemas/EventEnvelope" },
        {
          type: "object",
          properties: {
            event_type: { const: event.eventType },
            payload: {
              type: "object",
              properties,
              ...(required.length > 0 ? { required } : {}),
              additionalProperties: false,
            },
          },
        },
      ],
    },
  };
}

function controlMessages() {
  const messages = {
    SubscriptionSubscribe: {
      name: "subscription.subscribe",
      title: "채널 구독",
      contentType: "application/json",
      payload: {
        type: "object",
        required: ["type", "request_id", "channel_id"],
        properties: {
          type: { const: "subscription.subscribe" },
          request_id: { type: "string", format: "uuid" },
          channel_id: { type: "string", pattern: "^WS-[A-Z]+-\\d{3}$" },
          resource_id: { type: "string", format: "uuid" },
          last_sequence: { type: "integer", format: "int64", minimum: 0 },
        },
        additionalProperties: false,
      },
    },
    SubscriptionUnsubscribe: {
      name: "subscription.unsubscribe",
      title: "채널 구독 해제",
      contentType: "application/json",
      payload: {
        type: "object",
        required: ["type", "request_id", "subscription_id"],
        properties: {
          type: { const: "subscription.unsubscribe" },
          request_id: { type: "string", format: "uuid" },
          subscription_id: { type: "string", format: "uuid" },
        },
        additionalProperties: false,
      },
    },
    ConnectionPing: {
      name: "connection.ping",
      title: "애플리케이션 연결 확인",
      contentType: "application/json",
      payload: {
        type: "object",
        required: ["type", "client_time"],
        properties: {
          type: { const: "connection.ping" },
          client_time: { type: "string", format: "date-time" },
        },
        additionalProperties: false,
      },
    },
    EventAck: {
      name: "event.ack",
      title: "마지막 적용 sequence 진단",
      contentType: "application/json",
      payload: {
        type: "object",
        required: ["type", "subscription_id", "sequence"],
        properties: {
          type: { const: "event.ack" },
          subscription_id: { type: "string", format: "uuid" },
          sequence: { type: "integer", format: "int64", minimum: 0 },
        },
        additionalProperties: false,
      },
    },
  };
  return messages;
}

function buildAsyncApi(channels, events) {
  const componentMessages = {};
  for (const event of events) {
    componentMessages[pascalFromId(event.eventId)] = eventMessage(event);
  }
  Object.assign(componentMessages, controlMessages());

  const channelObjects = {};
  const operations = {};
  const controlNames = [
    "SubscriptionSubscribe",
    "SubscriptionUnsubscribe",
    "ConnectionPing",
    "EventAck",
  ];

  for (const channel of channels) {
    const channelKey = camelFromId(channel.channelId);
    const channelEvents = events.filter((event) => event.channelIds.includes(channel.channelId));
    const messages = {};
    for (const event of channelEvents) {
      const messageName = pascalFromId(event.eventId);
      messages[messageName] = { $ref: `#/components/messages/${messageName}` };
    }
    for (const controlName of controlNames) {
      messages[controlName] = { $ref: `#/components/messages/${controlName}` };
    }

    channelObjects[channelKey] = {
      address: "/ws/v1",
      title: channel.channelId,
      summary: channel.purpose,
      description: [
        `논리 구독 주소: \`${channel.logicalAddress}\`.`,
        `허용 역할: ${channel.roles}.`,
        `권한 조건: ${channel.authorization}.`,
      ].join("\n\n"),
      "x-channel-id": channel.channelId,
      "x-logical-address": channel.logicalAddress,
      messages,
    };

    operations[`send${pascalFromId(channel.channelId)}Events`] = {
      action: "send",
      channel: { $ref: `#/channels/${channelKey}` },
      summary: `${channel.channelId}에서 커밋된 서버 이벤트 전송`,
      messages: channelEvents.map((event) => ({
        $ref: `#/channels/${channelKey}/messages/${pascalFromId(event.eventId)}`,
      })),
    };
    operations[`receive${pascalFromId(channel.channelId)}Control`] = {
      action: "receive",
      channel: { $ref: `#/channels/${channelKey}` },
      summary: `${channel.channelId} 구독·해제·진단 제어 메시지 수신`,
      messages: controlNames.map((name) => ({
        $ref: `#/channels/${channelKey}/messages/${name}`,
      })),
    };
  }

  return {
    asyncapi: "3.1.0",
    id: "urn:ecovillage:realtime-api",
    info: {
      title: "이코빌리지 Realtime WebSocket API",
      version: "0.1.0",
      description:
        "WEBSOCKET_SPEC.md의 논리 채널 10개와 도메인 이벤트 48개를 기계 판독 계약으로 고정합니다. 변경 명령은 HTTP로만 받고 WebSocket은 커밋된 상태를 최소 1회 전달합니다.",
    },
    defaultContentType: "application/json",
    servers: {
      production: {
        host: "api.ecovillage.kr",
        pathname: "/ws/v1",
        protocol: "wss",
        description: "운영 WebSocket Gateway",
        security: [{ $ref: "#/components/securitySchemes/realtimeTicket" }],
        bindings: {
          ws: {
            query: {
              type: "object",
              required: ["ticket"],
              properties: {
                ticket: {
                  type: "string",
                  minLength: 20,
                  description: "API-AUTH-007 또는 API-DSP-001에서 발급한 60초·1회용 티켓",
                },
              },
              additionalProperties: false,
            },
            bindingVersion: "0.1.0",
          },
        },
      },
      local: {
        host: "localhost:8080",
        pathname: "/ws/v1",
        protocol: "ws",
        description: "로컬 개발 WebSocket Gateway",
        security: [{ $ref: "#/components/securitySchemes/realtimeTicket" }],
      },
    },
    channels: channelObjects,
    operations,
    components: {
      securitySchemes: {
        realtimeTicket: {
          type: "httpApiKey",
          name: "ticket",
          in: "query",
          description: "60초 만료·1회 사용·역할 및 리소스 범위 바인딩 접속 티켓",
        },
      },
      correlationIds: {
        correlationId: {
          description: "HTTP 요청부터 outbox, Redis와 Gateway까지 연결하는 상관관계 ID",
          location: "$message.payload#/correlation_id",
        },
      },
      schemas: {
        EventEnvelope: {
          type: "object",
          required: [
            "event_id",
            "event_type",
            "occurred_at",
            "classroom_id",
            "resource_id",
            "state_version",
            "sequence",
            "correlation_id",
            "payload",
          ],
          properties: {
            event_id: { type: "string", format: "uuid" },
            event_type: { type: "string" },
            occurred_at: { type: "string", format: "date-time" },
            classroom_id: { type: "string", format: "uuid" },
            resource_id: { type: "string", format: "uuid" },
            state_version: { type: "integer", format: "int64", minimum: 0 },
            sequence: { type: "integer", format: "int64", minimum: 1 },
            correlation_id: { type: "string", format: "uuid" },
            originating_request_id: { type: "string", format: "uuid" },
            payload: { type: "object" },
          },
          additionalProperties: false,
        },
      },
      messages: componentMessages,
    },
  };
}

function yamlScalar(value) {
  if (value === null) return "null";
  if (typeof value === "boolean" || typeof value === "number") return String(value);
  return JSON.stringify(value);
}

function toYaml(value, indent = 0) {
  const pad = " ".repeat(indent);
  if (Array.isArray(value)) {
    if (value.length === 0) return `${pad}[]`;
    return value
      .map((item) => {
        if (item !== null && typeof item === "object") {
          const rendered = toYaml(item, indent + 2);
          const lines = rendered.split("\n");
          return `${pad}- ${lines[0].trimStart()}\n${lines
            .slice(1)
            .map((line) => line)
            .join("\n")}`.trimEnd();
        }
        return `${pad}- ${yamlScalar(item)}`;
      })
      .join("\n");
  }
  if (value !== null && typeof value === "object") {
    const entries = Object.entries(value);
    if (entries.length === 0) return `${pad}{}`;
    return entries
      .map(([key, item]) => {
        const renderedKey = /^[A-Za-z0-9_.$/-]+$/.test(key) ? key : JSON.stringify(key);
        if (item !== null && typeof item === "object") {
          const rendered = toYaml(item, indent + 2);
          if ((Array.isArray(item) && item.length === 0) || (!Array.isArray(item) && Object.keys(item).length === 0)) {
            return `${pad}${renderedKey}: ${rendered.trim()}`;
          }
          return `${pad}${renderedKey}:\n${rendered}`;
        }
        return `${pad}${renderedKey}: ${yamlScalar(item)}`;
      })
      .join("\n");
  }
  return `${pad}${yamlScalar(value)}`;
}

const studentSessionRequestFields = [
  { name: "classroom_code" },
  { name: "attendance_number", schema: { type: "integer", minimum: 1 } },
  { name: "nickname", schema: { type: "string", minLength: 1, maxLength: 40 } },
  { name: "password", schema: { type: "string", format: "password", minLength: 8, writeOnly: true } },
  { name: "device_id" },
];

const teacherSessionRequestFields = [
  {
    name: "id_token",
    schema: {
      type: "string",
      minLength: 1,
      maxLength: 20000,
      writeOnly: true,
    },
  },
];

const acceptedResponseFields = [
  { name: "operation_id", schema: { type: "string", format: "uuid" } },
  { name: "status", schema: { type: "string", enum: ["QUEUED", "RUNNING"] } },
  { name: "status_url", schema: { type: "string", format: "uri-reference" } },
];

function hasRequestBody(operation) {
  return !["GET", "DELETE"].includes(operation.method) && operation.apiId !== "API-AUTH-004";
}

function requestFieldsForManifest(operation) {
  if (!hasRequestBody(operation)) return [];
  if (operation.apiId === "API-AUTH-001") return studentSessionRequestFields;
  if (operation.apiId === "API-AUTH-008") return teacherSessionRequestFields;
  const domainFields = requestFieldOverrides[operation.apiId] ?? extractEnglishFields(operation.contract);
  return [...mutationContextFor(operation), ...domainFields];
}

function responseFieldsForManifest(operation) {
  const status = successStatus(operation);
  if (status === "204") return [];
  if (status === "202" && !Object.hasOwn(responseFieldOverrides, operation.apiId) && operation.apiId !== "API-AI-002") {
    return acceptedResponseFields;
  }
  return responseFields(operation);
}

function buildApiFieldMapping(operations) {
  return {
    version: "1.0.0",
    generated_from: ["docs/HTTP_API_SPEC.md", "docs/RTM.md", "docs/PHYSICAL_SCHEMA.md"],
    policy: {
      mvp_contract: "요청·응답 최상위 DTO를 닫고 저장·조회 경계를 확정",
      planned: "경로·권한·추적성만 예약하며 구현 스프린트에서 DTO를 확정",
      secrets: "원문 비밀번호·토큰은 저장하거나 로그에 남기지 않고 해시 또는 외부 세션 참조만 보관",
    },
    operations: operations.map((operation) => {
      const contractStatus = implementationStatus(operation);
      const targets = storageTargets(operation);
      const requestFields = requestFieldsForManifest(operation);
      const responseFieldsForOperation = responseFieldsForManifest(operation);
      const explicitRequest =
        !hasRequestBody(operation) ||
        operation.apiId === "API-AUTH-001" ||
        operation.apiId === "API-AUTH-008" ||
        Object.hasOwn(requestFieldOverrides, operation.apiId);
      return {
        api_id: operation.apiId,
        method: operation.method,
        path: operation.path,
        implementation_status: contractStatus,
        storage_targets: targets,
        request: {
          mode: hasRequestBody(operation)
            ? contractStatus === "mvp-contract" ? "strict" : "planned"
            : "none",
          schema_source: explicitRequest ? "explicit" : "inferred-planned",
          fields: requestFields.map((field) => ({
            ...manifestField(field, "request", targets),
            visibility: schemaForField(field).writeOnly ? "secret-input" : "role-scoped",
          })),
        },
        response: {
          mode: successStatus(operation) === "204"
            ? "none"
            : contractStatus === "mvp-contract" ? "strict" : "planned",
          fields: responseFieldsForOperation.map((field) => ({
            ...manifestField(field, "response", targets),
            visibility: schemaForField(field)["x-sensitive"] || schemaForField(field).writeOnly
              ? "one-time-secret"
              : operation.apiId === "API-READ-025" ? "public-approved" : "role-scoped",
          })),
        },
      };
    }),
  };
}

function assertGeneration(httpOperations, channels, events, openapi, asyncapi, fieldMapping) {
  const apiIds = httpOperations.map((operation) => operation.apiId);
  const routeKeys = httpOperations.map((operation) => `${operation.method} ${operation.path}`);
  const eventIds = events.map((event) => event.eventId);
  const eventTypes = events.map((event) => event.eventType);

  const assertions = [
    [httpOperations.length === 132, `HTTP 작업 수가 132개가 아닙니다: ${httpOperations.length}`],
    [new Set(apiIds).size === 132, "API ID가 중복되었습니다."],
    [new Set(routeKeys).size === 132, "HTTP 메서드·경로가 중복되었습니다."],
    [channels.length === 10, `WebSocket 채널 수가 10이 아닙니다: ${channels.length}`],
    [events.length === 48, `이벤트 수가 48이 아닙니다: ${events.length}`],
    [new Set(eventIds).size === 48, "이벤트 ID가 중복되었습니다."],
    [new Set(eventTypes).size === 48, "event_type이 중복되었습니다."],
    [
      events.every((event) => event.channelIds.length > 0),
      "채널에 연결되지 않은 이벤트가 있습니다.",
    ],
    [
      Object.values(openapi.paths).reduce(
        (sum, item) =>
          sum +
          Object.keys(item).filter((key) =>
            ["get", "post", "put", "patch", "delete"].includes(key),
          ).length,
        0,
          ) === 132,
      "OpenAPI 작업 수가 132개가 아닙니다.",
    ],
    [Object.keys(asyncapi.channels).length === 10, "AsyncAPI 채널 수가 10이 아닙니다."],
    [
      Object.keys(asyncapi.components.messages).filter((name) => name.startsWith("Evt")).length ===
        48,
      "AsyncAPI 이벤트 메시지 수가 48이 아닙니다.",
    ],
    [
      fieldMapping.operations.every(
        (operation) =>
          operation.implementation_status !== "mvp-contract" ||
          operation.request.mode === "none" ||
          operation.request.schema_source === "explicit",
      ),
      "MVP 요청 중 명시적 DTO가 아닌 작업이 있습니다.",
    ],
    [
      fieldMapping.operations.every(
        (operation) =>
          operation.implementation_status !== "mvp-contract" ||
          operation.storage_targets.length > 0,
      ),
      "MVP 작업 중 저장·조회 경계가 없는 작업이 있습니다.",
    ],
  ];

  const failures = assertions.filter(([ok]) => !ok).map(([, message]) => message);
  if (failures.length > 0) throw new Error(failures.join("\n"));
}

const httpOperations = parseHttpOperations(httpMarkdown);
const channels = parseChannels(websocketMarkdown);
const events = parseEvents(websocketMarkdown);
const openapi = buildOpenApi(httpOperations);
const asyncapi = buildAsyncApi(channels, events);
const fieldMapping = buildApiFieldMapping(httpOperations);

assertGeneration(httpOperations, channels, events, openapi, asyncapi, fieldMapping);
fs.mkdirSync(contractsDir, { recursive: true });
fs.writeFileSync(path.join(contractsDir, "openapi.yaml"), `${toYaml(openapi)}\n`, "utf8");
fs.writeFileSync(path.join(contractsDir, "asyncapi.yaml"), `${toYaml(asyncapi)}\n`, "utf8");
fs.writeFileSync(
  path.join(contractsDir, "api-field-mapping.json"),
  `${JSON.stringify(fieldMapping, null, 2)}\n`,
  "utf8",
);

console.log(
  JSON.stringify(
    {
      openapi_operations: httpOperations.length,
      asyncapi_channels: channels.length,
      asyncapi_events: events.length,
      mvp_contract_operations: fieldMapping.operations.filter(
        (operation) => operation.implementation_status === "mvp-contract",
      ).length,
      output: [
        "contracts/openapi.yaml",
        "contracts/asyncapi.yaml",
        "contracts/api-field-mapping.json",
      ],
    },
    null,
    2,
  ),
);
