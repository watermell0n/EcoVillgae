import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const root = process.cwd();
const backendSource = path.join(root, "backend", "src", "main", "java");
const mappingPath = path.join(root, "contracts", "api-field-mapping.json");
const baselinePath = path.join(root, "contracts", "spring-openapi-baseline.json");
const reportPath = path.join(
  root,
  "docs",
  "reviews",
  "SPRING_OPENAPI_CONTRACT_AUDIT.md",
);
const writeBaseline = process.argv.includes("--write-baseline");

const mapping = JSON.parse(fs.readFileSync(mappingPath, "utf8"));
const operations = mapping.operations ?? [];

function javaFiles(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) return javaFiles(fullPath);
    return entry.isFile() && entry.name.endsWith(".java") ? [fullPath] : [];
  });
}

function quotedValue(text) {
  return text?.match(/"([^"]*)"/)?.[1] ?? "";
}

function balancedEnd(source, start, open, close) {
  let depth = 0;
  let inString = false;
  let escaped = false;
  for (let index = start; index < source.length; index += 1) {
    const character = source[index];
    if (inString) {
      if (escaped) escaped = false;
      else if (character === "\\") escaped = true;
      else if (character === '"') inString = false;
      continue;
    }
    if (character === '"') {
      inString = true;
      continue;
    }
    if (character === open) depth += 1;
    if (character === close) {
      depth -= 1;
      if (depth === 0) return index;
    }
  }
  return -1;
}

function splitTopLevel(text) {
  const result = [];
  let start = 0;
  let angle = 0;
  let round = 0;
  let square = 0;
  for (let index = 0; index < text.length; index += 1) {
    const character = text[index];
    if (character === "<") angle += 1;
    else if (character === ">") angle -= 1;
    else if (character === "(") round += 1;
    else if (character === ")") round -= 1;
    else if (character === "[") square += 1;
    else if (character === "]") square -= 1;
    else if (character === "," && angle === 0 && round === 0 && square === 0) {
      result.push(text.slice(start, index));
      start = index + 1;
    }
  }
  result.push(text.slice(start));
  return result;
}

function snakeCase(value) {
  return value
    .replace(/([a-z0-9])([A-Z])/g, "$1_$2")
    .replace(/([A-Z])([A-Z][a-z])/g, "$1_$2")
    .toLowerCase();
}

function normalizePath(value) {
  const withoutQuery = value.split("?")[0];
  const withSlash = withoutQuery.startsWith("/") ? withoutQuery : `/${withoutQuery}`;
  const normalized = withSlash
    .replace(/\/+/g, "/")
    .replace(/\{[^}]+\}/g, "{}")
    .replace(/\/$/, "");
  return normalized || "/";
}

function joinPaths(base, methodPath) {
  return normalizePath(`${base || ""}/${(methodPath || "").replace(/^\//, "")}`);
}

function simpleType(type) {
  const cleaned = type.replace(/\s+/g, " ").trim();
  if (cleaned.startsWith("Map<") || cleaned.startsWith("List<Map<")) return "Map";
  const generic = cleaned.match(/^[\w.$]+<([\s\S]+)>$/);
  const selected = generic ? generic[1].trim() : cleaned;
  return selected.split(".").at(-1).replace(/\[\]$/, "");
}

function apiResponseType(returnType) {
  const marker = "ApiResponse<";
  const markerIndex = returnType.indexOf(marker);
  if (markerIndex < 0) return null;
  const openIndex = markerIndex + marker.length - 1;
  const closeIndex = balancedEnd(returnType, openIndex, "<", ">");
  return closeIndex < 0 ? null : returnType.slice(openIndex + 1, closeIndex).trim();
}

function parseRecords(files) {
  const records = new Map();
  for (const file of files) {
    const source = fs.readFileSync(file, "utf8");
    const matcher = /\brecord\s+(\w+)\s*\(/g;
    for (const match of source.matchAll(matcher)) {
      const openIndex = match.index + match[0].lastIndexOf("(");
      const closeIndex = balancedEnd(source, openIndex, "(", ")");
      if (closeIndex < 0) continue;
      const components = source.slice(openIndex + 1, closeIndex);
      const fields = splitTopLevel(components)
        .map((component) => component
          .replace(/@\w+(?:\.\w+)*(?:\([^)]*\))?\s*/g, "")
          .trim()
          .match(/([A-Za-z_$][\w$]*)\s*$/)?.[1])
        .filter(Boolean)
        .map(snakeCase);
      records.set(match[1], {
        fields: [...new Set(fields)].sort(),
        file: path.relative(root, file).replaceAll("\\", "/"),
      });
    }
  }
  return records;
}

function annotationEnd(source, start) {
  const nameEnd = source.indexOf("Mapping", start) + "Mapping".length;
  let index = nameEnd;
  while (/\s/.test(source[index] ?? "")) index += 1;
  if (source[index] !== "(") return nameEnd;
  const end = balancedEnd(source, index, "(", ")");
  return end < 0 ? nameEnd : end + 1;
}

function parseControllers(files) {
  const endpoints = [];
  for (const file of files.filter((candidate) => candidate.endsWith("Controller.java"))) {
    const source = fs.readFileSync(file, "utf8");
    const classIndex = source.search(/public\s+class\s+\w+/);
    if (classIndex < 0) continue;
    const classPrefix = source.slice(0, classIndex);
    const classMapping = [...classPrefix.matchAll(/@RequestMapping\s*\(([^)]*)\)/g)].at(-1);
    const basePath = quotedValue(classMapping?.[1] ?? "");
    const mappingMatcher = /@(Get|Post|Put|Delete|Patch)Mapping\b/g;
    for (const mappingMatch of source.matchAll(mappingMatcher)) {
      if (mappingMatch.index < classIndex) continue;
      const end = annotationEnd(source, mappingMatch.index);
      const annotation = source.slice(mappingMatch.index, end);
      const declaration = source.slice(end).match(
        /^\s*(?:@\w+(?:\([^)]*\))?\s*)*public\s+([^\r\n]+?)\s+(\w+)\s*\(([\s\S]*?)\)\s*\{/,
      );
      if (!declaration) continue;
      const requestMatch = declaration[3].match(
        /@RequestBody(?:\s*\([^)]*\))?\s+([A-Za-z_$][\w.$]*(?:<[^>]+>)?)\s+\w+/,
      );
      endpoints.push({
        method: mappingMatch[1].toUpperCase(),
        path: joinPaths(basePath, quotedValue(annotation)),
        source_path: path.relative(root, file).replaceAll("\\", "/"),
        source_method: declaration[2],
        request_type: requestMatch ? simpleType(requestMatch[1]) : null,
        response_type: apiResponseType(declaration[1])
          ? simpleType(apiResponseType(declaration[1]))
          : null,
      });
    }
  }
  return endpoints;
}

function routeKey(method, routePath) {
  return `${method.toUpperCase()} ${normalizePath(routePath)}`;
}

function compareFields(expected, actual) {
  const expectedSet = new Set(expected);
  const actualSet = new Set(actual);
  return {
    missing_in_spring: expected.filter((field) => !actualSet.has(field)),
    extra_in_spring: actual.filter((field) => !expectedSet.has(field)),
  };
}

const responseTypeOverrides = {
  "API-AI-002": ["SetupPrecheckOperationResponse", "OperationalAnalysisResponse"],
};

function issue(kind, values) {
  return { kind, ...values };
}

const files = javaFiles(backendSource);
const records = parseRecords(files);
const endpoints = parseControllers(files);
const operationsByRoute = new Map(
  operations.map((operation) => [routeKey(operation.method, operation.path), operation]),
);
const issues = [];
const matchedApiIds = new Set();

for (const endpoint of endpoints) {
  const key = routeKey(endpoint.method, endpoint.path);
  const operation = operationsByRoute.get(key);
  if (!operation) {
    issues.push(issue("SPRING_ROUTE_NOT_IN_OPENAPI", {
      route: key,
      source: `${endpoint.source_path}#${endpoint.source_method}`,
    }));
    continue;
  }
  matchedApiIds.add(operation.api_id);
  if (operation.implementation_status === "planned") {
    issues.push(issue("IMPLEMENTED_ROUTE_MARKED_PLANNED", {
      api_id: operation.api_id,
      route: key,
      source: `${endpoint.source_path}#${endpoint.source_method}`,
    }));
  }

  for (const [direction, typeName] of [
    ["request", endpoint.request_type],
    ["response", endpoint.response_type],
  ]) {
    const contract = operation[direction];
    if (!contract || contract.mode === "none" || contract.mode === "planned") continue;
    // Some read projections are intentionally heterogeneous (layout cells, contract
    // timelines, and ledger summaries). Their explicit OpenAPI schema remains the
    // client contract, while Spring exposes a Map projection instead of a misleading
    // one-size-fits-all DTO.
    if (direction === "response" && typeName === "Map") continue;
    const typeNames = direction === "response" && responseTypeOverrides[operation.api_id]
      ? responseTypeOverrides[operation.api_id]
      : [typeName];
    const unresolved = typeNames.filter((candidate) => !candidate || !records.has(candidate));
    if (unresolved.length) {
      issues.push(issue(`${direction.toUpperCase()}_DTO_UNRESOLVED`, {
        api_id: operation.api_id,
        route: key,
        dto: unresolved.join(",") || null,
        source: `${endpoint.source_path}#${endpoint.source_method}`,
      }));
      continue;
    }
    const expected = [...new Set(contract.fields.map((field) => field.name))].sort();
    const actualRecords = typeNames.map((candidate) => records.get(candidate));
    const actualFields = [...new Set(actualRecords.flatMap((record) => record.fields))].sort();
    const difference = compareFields(expected, actualFields);
    if (difference.missing_in_spring.length || difference.extra_in_spring.length) {
      issues.push(issue(`${direction.toUpperCase()}_FIELDS_DIFFER`, {
        api_id: operation.api_id,
        route: key,
        dto: typeNames.join("|"),
        dto_source: actualRecords.map((record) => record.file).join("|"),
        ...difference,
      }));
    }
  }
}

issues.sort((left, right) => JSON.stringify(left).localeCompare(JSON.stringify(right)));
const fingerprints = issues.map((item) => JSON.stringify(item));
const summary = {
  openapi_operations: operations.length,
  spring_endpoints: endpoints.length,
  matched_spring_endpoints: matchedApiIds.size,
  current_known_differences: issues.length,
};

function markdownReport() {
  const rows = issues.length === 0
    ? "| 없음 | - | - |"
    : issues.map((item) => {
      const target = item.api_id ?? item.route;
      const detail = item.missing_in_spring || item.extra_in_spring
        ? `missing=${(item.missing_in_spring ?? []).join(",") || "-"}; extra=${(item.extra_in_spring ?? []).join(",") || "-"}`
        : item.dto ?? item.source ?? "-";
      return `| ${item.kind} | ${target} | ${detail.replaceAll("|", "\\|")} |`;
    }).join("\n");
  return `# Spring-OpenAPI 계약 차이 기준선

- 추적 이슈: \`S15P11B201-279\`
- 기준 입력: \`contracts/api-field-mapping.json\`, Spring Controller/record DTO
- 학생 클라이언트 기준: React Native + Expo + TypeScript

## 검사 범위

- Spring에 실제로 노출된 HTTP method/path가 OpenAPI에 존재하는지 검사합니다.
- 실제 구현됐지만 OpenAPI에서 \`planned\`로 남은 경로를 검사합니다.
- 구현된 strict API의 request/response 최상위 DTO 필드를 Jackson \`SNAKE_CASE\` 기준으로 비교합니다.
- 현재 차이는 \`contracts/spring-openapi-baseline.json\`에 고정하고, 추가·해소된 차이는 CI에서 검토를 요구합니다.
- OpenAPI에 있지만 아직 Spring에 없는 API는 백로그 상태일 수 있으므로 이 검사에서 오류로 보지 않습니다.

## 현재 요약

| 항목 | 수 |
| --- | ---: |
| OpenAPI 작업 | ${summary.openapi_operations} |
| Spring 엔드포인트 | ${summary.spring_endpoints} |
| OpenAPI와 연결된 Spring 엔드포인트 | ${summary.matched_spring_endpoints} |
| 기준선에 기록한 차이 | ${summary.current_known_differences} |

## 현재 차이

| 종류 | API/경로 | 상세 |
| --- | --- | --- |
${rows}

## 담당 작업 연결

| 범위 | Jira |
| --- | --- |
| 인증 bootstrap·명단·모듈/정책 조회 | \`S15P11B201-269\` |
| AI 실행·제안 및 operation 조회 | \`S15P11B201-270\` |
| 상품 command | \`S15P11B201-271\` |
| 상품·퀴즈 AI APPLY 라우팅 | \`S15P11B201-272\` |
| 코스·모듈 중지·정책 변경 | \`S15P11B201-273\` |
| 퀴즈 결과·보상·재채점 | \`S15P11B201-274\` |
| 교사 웹 상태 복원 | \`S15P11B201-275\` |
| 교사 상품·GAME AI UI | \`S15P11B201-276\` |
| Expo 인증·API 생성 타입 | \`S15P11B201-277\` |
| Expo 활성 모듈·동적 리소스 | \`S15P11B201-278\` |

## 클라이언트 규칙

Spring JSON은 \`snake_case\`입니다. TypeScript에서 camelCase를 쓰려면 생성기가 이름을 바꾸지 않는 한
응답 경계의 단일 mapper에서만 변환합니다. 수동 interface를 선언하고 Spring JSON을 그대로
camelCase 객체로 간주해서는 안 됩니다. 현재 Vite 학생 앱은 이전 참고용이며 이 기준선의 최종 소비자는 Expo 앱입니다.

## 실행

\`node scripts/validate-spring-openapi-contract.mjs\`

계약을 의도적으로 변경한 경우 검토 후 다음 명령으로 기준선과 이 문서를 갱신합니다.

\`node scripts/validate-spring-openapi-contract.mjs --write-baseline\`
`;
}

if (writeBaseline) {
  fs.writeFileSync(
    baselinePath,
    `${JSON.stringify({ version: 1, summary, differences: issues }, null, 2)}\n`,
  );
  fs.writeFileSync(reportPath, markdownReport());
  console.log(
    `Spring/OpenAPI baseline written: ${summary.spring_endpoints} endpoints, ` +
      `${summary.current_known_differences} known differences`,
  );
  process.exit(0);
}

if (!fs.existsSync(baselinePath)) {
  console.error("Spring/OpenAPI baseline is missing.");
  console.error("Review and run: node scripts/validate-spring-openapi-contract.mjs --write-baseline");
  process.exit(1);
}

const baseline = JSON.parse(fs.readFileSync(baselinePath, "utf8"));
const baselineFingerprints = (baseline.differences ?? []).map((item) => JSON.stringify(item));
const baselineSet = new Set(baselineFingerprints);
const currentSet = new Set(fingerprints);
const added = fingerprints.filter((fingerprint) => !baselineSet.has(fingerprint));
const resolved = baselineFingerprints.filter((fingerprint) => !currentSet.has(fingerprint));

if (added.length || resolved.length) {
  console.error("Spring/OpenAPI contract drift detected:");
  for (const fingerprint of added) console.error(`+ ${fingerprint}`);
  for (const fingerprint of resolved) console.error(`- ${fingerprint}`);
  console.error("Review the DTO/OpenAPI change before updating the baseline.");
  process.exit(1);
}

console.log(
  `Spring/OpenAPI contract PASS: ${summary.spring_endpoints} Spring endpoints, ` +
    `${summary.matched_spring_endpoints} matched, ` +
    `${summary.current_known_differences} reviewed differences`,
);
