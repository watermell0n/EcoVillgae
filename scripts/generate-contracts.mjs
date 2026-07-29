import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const root = process.cwd();
const httpSpecPath = path.join(root, "docs", "HTTP_API_SPEC.md");
const websocketSpecPath = path.join(root, "docs", "WEBSOCKET_SPEC.md");
const contractsDir = path.join(root, "contracts");

const httpMarkdown = fs.readFileSync(httpSpecPath, "utf8");
const websocketMarkdown = fs.readFileSync(websocketSpecPath, "utf8");

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
    { name: "grade", schema: { type: "integer", enum: [4, 5, 6] } },
    {
      name: "course_size",
      values: ["ONE_SESSION", "ONE_WEEK", "ONE_MONTH", "SEMESTER"],
    },
    { name: "starts_on", schema: { type: "string", format: "date" } },
    { name: "ends_on", schema: { type: "string", format: "date" } },
    { name: "student_count", schema: { type: "integer", minimum: 1 } },
    { name: "timezone", schema: { type: "string", default: "Asia/Seoul" } },
    { name: "salary_policy", schema: { type: "object", additionalProperties: true } },
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
  ],
  "API-CLS-003": [
    {
      name: "students",
      schema: {
        type: "array",
        items: {
          type: "object",
          required: ["attendance_number", "nickname", "role_code"],
          properties: {
            student_id: { type: "string", format: "uuid" },
            attendance_number: { type: "integer", minimum: 1 },
            nickname: { type: "string", minLength: 1, maxLength: 40 },
            role_code: { type: "string" },
          },
          additionalProperties: false,
        },
      },
    },
    {
      name: "salary_roles",
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
  "API-CLS-008": [
    { name: "reauthentication_proof", schema: { type: "string", writeOnly: true } },
    { name: "acknowledged_blocking_items", schema: { type: "boolean", const: true } },
    { name: "reason" },
  ],
  "API-MOD-001": [
    { name: "confirm_prerequisites", schema: { type: "boolean", const: true } },
  ],
  "API-POL-001": [
    { name: "price_adjustment_bps", schema: { type: "integer", format: "int32" } },
    { name: "inflation_rate_bps", schema: { type: "integer", format: "int32" } },
    { name: "income_tax_rate_bps", schema: { type: "integer", minimum: 0, maximum: 10000 } },
    { name: "vat_rate_bps", schema: { type: "integer", minimum: 0, maximum: 10000 } },
    { name: "trade_tax_rate_bps", schema: { type: "integer", minimum: 0, maximum: 10000 } },
    { name: "deposit_rate_bps", schema: { type: "integer", format: "int32" } },
    { name: "loan_rate_bps", schema: { type: "integer", format: "int32" } },
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
    {
      name: "assets",
      schema: { type: "array", minItems: 4, maxItems: 4, items: { type: "object", additionalProperties: true } },
    },
    {
      name: "price_steps",
      schema: { type: "array", minItems: 1, items: { type: "object", additionalProperties: true } },
    },
    {
      name: "events",
      schema: { type: "array", items: { type: "object", additionalProperties: true } },
    },
    { name: "limits", schema: { type: "object", additionalProperties: true } },
    { name: "loss_cap_bps", schema: { type: "integer", minimum: 0, maximum: 10000 } },
    { name: "expected_policy_version", schema: { type: "integer", minimum: 0 } },
  ],
  "API-FIN-010": [
    { name: "max_amount", schema: { type: "integer", format: "int64", minimum: 1 } },
    { name: "base_rate_bps", schema: { type: "integer", minimum: 0 } },
    {
      name: "installment_options",
      schema: { type: "array", uniqueItems: true, items: { type: "integer", minimum: 1 } },
    },
    { name: "grace_period_days", schema: { type: "integer", minimum: 0 } },
    {
      name: "credit_adjustment_rules",
      schema: { type: "object", additionalProperties: true },
    },
    { name: "expected_policy_version", schema: { type: "integer", minimum: 0 } },
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
    { name: "period", schema: { $ref: "#/components/schemas/Period" } },
    {
      name: "metric_scopes",
      schema: { type: "array", minItems: 1, uniqueItems: true, items: { type: "string" } },
    },
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
  "API-OPS-001": [
    { name: "provider" },
    { name: "global_daily_limit", schema: { type: "integer", minimum: 0 } },
    { name: "model_routes", schema: { type: "object", additionalProperties: true } },
    { name: "approval_reference" },
    { name: "expected_policy_version", schema: { type: "integer", minimum: 0 } },
  ],
};

function requestSchema(operation) {
  if (operation.apiId === "API-AUTH-001") {
    return { $ref: "#/components/schemas/StudentSessionRequest" };
  }

  const fields = requestFieldOverrides[operation.apiId] ?? extractEnglishFields(operation.contract);
  const properties = {};
  const required = [];
  for (const field of fields) {
    properties[field.name] = schemaForField(field);
    if (!field.optional) required.push(field.name);
  }

  return {
    allOf: [
      { $ref: "#/components/schemas/MutationContext" },
      {
        type: "object",
        description:
          fields.length > 0
            ? `도메인 필드는 HTTP_API_SPEC.md의 ${operation.apiId} 계약을 구조화했습니다.`
            : `${operation.apiId}의 세부 도메인 DTO는 구현 스프린트에서 이 기준 계약을 확장합니다.`,
        properties,
        ...(required.length > 0 ? { required } : {}),
        additionalProperties: true,
      },
    ],
  };
}

function tagForApi(apiId) {
  const domain = apiId.split("-")[1];
  const tags = {
    AUTH: "Authentication",
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
    "API-READ-028",
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
  if (["API-AUTH-001", "API-AUTH-002"].includes(operation.apiId)) return [];
  if (operation.apiId === "API-AUTH-004")
    return [{ refreshCookie: [] }, { refreshHeader: [] }];
  if (operation.apiId === "API-READ-025") return [{ displayBearer: [] }];
  return [{ bearerAuth: [] }, { cookieAuth: [] }];
}

function buildOpenApi(operations) {
  const paths = {};
  for (const operation of operations) {
    const method = operation.method.toLowerCase();
    const status = successStatus(operation);
    const op = {
      operationId: camelFromId(operation.apiId),
      "x-api-id": operation.apiId,
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
      ]
        .filter(Boolean)
        .join("\n\n"),
      security: securityFor(operation),
      parameters: parameterRefs(operation),
      responses: {
        [status]:
          status === "204"
            ? { description: "성공적으로 처리했으며 응답 본문이 없습니다." }
            : status === "202"
              ? { $ref: "#/components/responses/Accepted" }
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
        "HTTP_API_SPEC.md의 106개 작업을 기계 판독 계약으로 고정한 기준선입니다. 모든 변경은 멱등성 키를 사용하고, 금액·세금·잔액·권한은 서버가 다시 계산합니다.",
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
          description: "학생·교사용 앱과 내부 서비스의 짧은 수명 액세스 토큰",
        },
        cookieAuth: {
          type: "apiKey",
          in: "cookie",
          name: "ECOVILLAGE_SESSION",
          description: "교사·관리자 웹의 HttpOnly·Secure 세션 쿠키",
        },
        refreshCookie: {
          type: "apiKey",
          in: "cookie",
          name: "ECOVILLAGE_REFRESH",
          description: "교사·관리자 웹의 회전형 갱신 토큰",
        },
        refreshHeader: {
          type: "apiKey",
          in: "header",
          name: "X-Refresh-Token",
          description: "Expo SecureStore에서 읽어 전송하는 학생·교사용 앱의 회전형 갱신 토큰",
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
        MutationContext: {
          type: "object",
          properties: {
            expected_state_version: { type: "integer", format: "int64", minimum: 0 },
            screen_id: { type: "string", pattern: "^SCR-[A-Z]+-\\d{3}$" },
            ui_action_id: { type: "string", pattern: "^UIA-[A-Z]+-\\d{3}$" },
            client_occurred_at: { type: "string", format: "date-time" },
          },
          additionalProperties: true,
        },
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
            classroom_code: { type: "string", minLength: 4, maxLength: 32 },
            attendance_number: { type: "integer", minimum: 1 },
            nickname: { type: "string", minLength: 1, maxLength: 40 },
            password: { type: "string", format: "password", minLength: 8, writeOnly: true },
            device_id: { type: "string", format: "uuid" },
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

function assertGeneration(httpOperations, channels, events, openapi, asyncapi) {
  const apiIds = httpOperations.map((operation) => operation.apiId);
  const routeKeys = httpOperations.map((operation) => `${operation.method} ${operation.path}`);
  const eventIds = events.map((event) => event.eventId);
  const eventTypes = events.map((event) => event.eventType);

  const assertions = [
    [httpOperations.length === 106, `HTTP 작업 수가 106이 아닙니다: ${httpOperations.length}`],
    [new Set(apiIds).size === 106, "API ID가 중복되었습니다."],
    [new Set(routeKeys).size === 106, "HTTP 메서드·경로가 중복되었습니다."],
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
      ) === 106,
      "OpenAPI 작업 수가 106이 아닙니다.",
    ],
    [Object.keys(asyncapi.channels).length === 10, "AsyncAPI 채널 수가 10이 아닙니다."],
    [
      Object.keys(asyncapi.components.messages).filter((name) => name.startsWith("Evt")).length ===
        48,
      "AsyncAPI 이벤트 메시지 수가 48이 아닙니다.",
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

assertGeneration(httpOperations, channels, events, openapi, asyncapi);
fs.mkdirSync(contractsDir, { recursive: true });
fs.writeFileSync(path.join(contractsDir, "openapi.yaml"), `${toYaml(openapi)}\n`, "utf8");
fs.writeFileSync(path.join(contractsDir, "asyncapi.yaml"), `${toYaml(asyncapi)}\n`, "utf8");

console.log(
  JSON.stringify(
    {
      openapi_operations: httpOperations.length,
      asyncapi_channels: channels.length,
      asyncapi_events: events.length,
      output: ["contracts/openapi.yaml", "contracts/asyncapi.yaml"],
    },
    null,
    2,
  ),
);
