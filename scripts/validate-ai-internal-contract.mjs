import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath), "utf8");
const assert = (condition, message) => {
  if (!condition) throw new Error(message);
};

const contract = read("contracts/ai-internal-openapi.yaml");
const internalDoc = read("docs/ai/INTERNAL_API_CONTRACT.md");
const metricsDoc = read("docs/ai/METRICS_AND_THRESHOLDS.md");
const safetyDoc = read("docs/ai/SAFETY_AND_EVALUATION.md");
const publicSpec = read("docs/HTTP_API_SPEC.md");
const fixtureCatalog = JSON.parse(
  read("ai-service/tests/fixtures/contract_v1_1_cases.json"),
);

assert(/^openapi: 3\.1\.0$/m.test(contract), "내부 AI 계약은 OpenAPI 3.1.0이어야 합니다.");
assert(/^  version: 1\.1\.0$/m.test(contract), "내부 AI 계약 버전은 1.1.0이어야 합니다.");
assert(internalDoc.includes("- 버전: v1.1"), "사람용 내부 AI 계약 버전은 v1.1이어야 합니다.");
assert(fixtureCatalog.schema_version === "1.1", "AI fixture schema_version은 1.1이어야 합니다.");

const expectedOperations = new Map([
  ["/internal/v1/generations/balancing", "generateBalancingProposal"],
  ["/internal/v1/generations/teacher-chat", "generateTeacherChatAnswer"],
  ["/internal/v1/generations/economic-event", "generateEconomicEventDraft"],
  ["/internal/v1/generations/quiz-draft", "generateQuizDraft"],
]);

const paths = [...contract.matchAll(/^  (\/internal\/v1\/generations\/[a-z-]+):$/gm)].map(
  (match) => match[1],
);
const operationIds = [...contract.matchAll(/^      operationId: ([A-Za-z][A-Za-z0-9]+)$/gm)].map(
  (match) => match[1],
);

assert(paths.length === expectedOperations.size, `내부 AI 경로 수가 4개가 아닙니다: ${paths.length}`);
assert(new Set(paths).size === paths.length, "내부 AI 경로가 중복되었습니다.");
assert(operationIds.length === expectedOperations.size, `내부 AI operationId 수가 4개가 아닙니다: ${operationIds.length}`);
assert(new Set(operationIds).size === operationIds.length, "내부 AI operationId가 중복되었습니다.");

for (const [endpoint, operationId] of expectedOperations) {
  assert(paths.includes(endpoint), `내부 AI 경로가 누락되었습니다: ${endpoint}`);
  assert(operationIds.includes(operationId), `내부 AI operationId가 누락되었습니다: ${operationId}`);
  assert(internalDoc.includes(endpoint), `사람이 읽는 내부 계약에 경로가 없습니다: ${endpoint}`);
}

for (const scope of [
  "ai:generate:balancing",
  "ai:generate:teacher-chat",
  "ai:generate:economic-event",
  "ai:generate:quiz-draft",
]) {
  assert(contract.includes(`x-required-scope: ${scope}`), `내부 서비스 scope가 누락되었습니다: ${scope}`);
}

const componentRefs = [...contract.matchAll(/\$ref: ['"]#\/components\/(?:schemas|parameters|responses)\/([^'"]+)['"]/g)].map(
  (match) => match[1],
);
const componentDefinitions = new Set(
  [...contract.matchAll(/^    ([A-Za-z][A-Za-z0-9]+):$/gm)].map((match) => match[1]),
);
const unresolvedRefs = [...new Set(componentRefs.filter((name) => !componentDefinitions.has(name)))];
assert(unresolvedRefs.length === 0, `해결되지 않은 내부 AI component 참조: ${unresolvedRefs.join(", ")}`);

const badIndent = contract
  .split(/\r?\n/)
  .filter((line) => line.trim() && /^ +/.test(line) && line.match(/^ +/)[0].length % 2 !== 0);
assert(badIndent.length === 0, `2칸 단위가 아닌 YAML 들여쓰기: ${badIndent.slice(0, 3).join(" | ")}`);

const eventTypes = [
  "PRICE",
  "SUPPLY",
  "TAX",
  "INTEREST_RATE",
  "INVESTMENT_NEWS",
  "REWARD",
  "PUBLIC_FUND",
];
for (const eventType of eventTypes) {
  assert(contract.includes(`- ${eventType}`), `내부 계약 이벤트 템플릿 누락: ${eventType}`);
  assert(internalDoc.includes(eventType), `내부 문서 이벤트 템플릿 누락: ${eventType}`);
  assert(publicSpec.includes(eventType), `외부 API 이벤트 템플릿 누락: ${eventType}`);
}

const analysisModes = ["SETUP_PRECHECK", "ON_DEMAND", "AUTO_MONITOR"];
const setupContexts = ["CLASSROOM_SETUP", "PRODUCT_DRAFT", "QUIZ_SESSION_DRAFT"];
const eventSources = ["TEACHER_REQUEST", "MONITOR_RECOMMENDATION"];
const quizDifficulties = ["INTRODUCTORY", "STANDARD", "ADVANCED"];
for (const value of [
  ...analysisModes,
  ...setupContexts,
  ...eventSources,
  ...quizDifficulties,
]) {
  assert(contract.includes(value), `내부 AI 계약 enum 누락: ${value}`);
  assert(internalDoc.includes(value), `내부 AI 문서 enum 누락: ${value}`);
}

for (const field of [
  "scenario_version_id",
  "asset_id",
  "price_step_id",
  "current_step_index",
  "public_price_facts",
  "difficulty",
  "concepts",
  "concept_tags",
]) {
  assert(contract.includes(`${field}:`), `내부 AI 계약 필드 누락: ${field}`);
  assert(internalDoc.includes(field), `내부 AI 문서 필드 누락: ${field}`);
}

const fixtureCases = [...fixtureCatalog.success, ...fixtureCatalog.invalid];
assert(fixtureCatalog.success.length === 12, "AI v1.1 성공 fixture는 12건이어야 합니다.");
assert(fixtureCatalog.invalid.length === 7, "AI v1.1 실패 fixture는 7건이어야 합니다.");
assert(
  new Set(fixtureCases.map((fixture) => fixture.id)).size === fixtureCases.length,
  "AI v1.1 fixture id가 중복되었습니다.",
);

const metricCodes = [
  "LIQUID_MONEY_CHANGE_RATE_BPS",
  "ISSUANCE_AMOUNT",
  "RECOVERY_AMOUNT",
  "ISSUANCE_RECOVERY_RATE_BPS",
  "WALLET_AVERAGE_CHANGE_RATE_BPS",
  "WALLET_MEDIAN_CHANGE_RATE_BPS",
  "ASSET_CONCENTRATION_TOP20_BPS",
  "CONSUMPTION_RATE_BPS",
  "PRODUCT_SELL_THROUGH_RATE_BPS",
  "PRODUCT_STOCKOUT_SHARE_BPS",
  "FINANCIAL_PARTICIPATION_RATE_BPS",
  "PAYMENT_MISS_RATE_BPS",
  "INVESTMENT_PARTICIPATION_RATE_BPS",
  "INVESTMENT_MEDIAN_RETURN_BPS",
  "INVESTMENT_LOSS_PARTICIPANT_RATE_BPS",
  "REWARD_ISSUANCE_SHARE_BPS",
];
for (const metricCode of metricCodes) {
  assert(metricsDoc.includes(metricCode), `지표 문서에 지표가 없습니다: ${metricCode}`);
  assert(contract.includes(`- ${metricCode}`), `내부 계약 enum에 지표가 없습니다: ${metricCode}`);
}

assert(metricsDoc.includes("활성 학생 수 | 5명 이상"), "활성 학생 최소 5명 기준이 누락되었습니다.");
assert(metricsDoc.includes("현재 구간 관련 거래 수 | 10건 이상"), "관련 거래 최소 10건 기준이 누락되었습니다.");
assert(metricsDoc.includes("현재 구간과 바로 전 같은 길이 구간"), "연속 두 구간 경고 기준이 누락되었습니다.");
assert(safetyDoc.includes("구조화 스키마 유효율 | 100%"), "구조화 출력 출시 게이트가 누락되었습니다.");
assert(safetyDoc.includes("교사 승인 우회·직접 적용 | 0건"), "승인 경계 출시 게이트가 누락되었습니다.");

for (const forbiddenField of ["student_id", "student_name", "nickname", "password", "access_token", "refresh_token"]) {
  const fieldPattern = new RegExp(`^\\s+${forbiddenField}:`, "m");
  assert(!fieldPattern.test(contract), `내부 AI payload에 금지 필드가 선언되었습니다: ${forbiddenField}`);
}

assert(publicSpec.includes("API-AI-004") && publicSpec.includes("economic-event-draft-operations"), "경제 이벤트 초안 외부 계약이 누락되었습니다.");
assert(publicSpec.includes("API-AI-006") && publicSpec.includes("economic-events/{R}/decisions"), "경제 이벤트 승인 외부 계약이 누락되었습니다.");

console.log(
  JSON.stringify(
    {
      status: "PASS",
      paths: paths.length,
      operation_ids: operationIds.length,
      component_refs: componentRefs.length,
      unresolved_refs: unresolvedRefs.length,
      event_templates: eventTypes.length,
      analysis_modes: analysisModes.length,
      setup_contexts: setupContexts.length,
      event_sources: eventSources.length,
      quiz_difficulties: quizDifficulties.length,
      success_fixtures: fixtureCatalog.success.length,
      invalid_fixtures: fixtureCatalog.invalid.length,
      metric_codes: metricCodes.length,
      forbidden_input_fields: 0,
    },
    null,
    2,
  ),
);
