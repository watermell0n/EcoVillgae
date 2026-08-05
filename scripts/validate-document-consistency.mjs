import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const root = process.cwd();
const excludedDirectories = new Set([
  ".codex",
  ".docker-config",
  ".git",
  ".gradle",
  ".pytest_cache",
  ".ruff_cache",
  ".venv",
  "build",
  "node_modules",
]);

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), "utf8");
}

function walkMarkdown(directory = ".") {
  const absoluteDirectory = path.join(root, directory);
  return fs.readdirSync(absoluteDirectory, { withFileTypes: true }).flatMap((entry) => {
    const relativePath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      const isGeneratedPythonCache = entry.name.startsWith(".pytest_cache");
      return excludedDirectories.has(entry.name) || isGeneratedPythonCache
        ? []
        : walkMarkdown(relativePath);
    }
    return entry.isFile() && entry.name.endsWith(".md") ? [relativePath] : [];
  });
}

function matches(text, pattern) {
  return [...text.matchAll(pattern)].map((match) => match[1]);
}

function unique(values) {
  return [...new Set(values)].sort();
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function formatList(values) {
  return values.length === 0 ? "없음" : values.join(", ");
}

const markdownFiles = walkMarkdown().sort();
const markdown = new Map(markdownFiles.map((file) => [file, read(file)]));

const brokenLinks = [];
for (const [file, content] of markdown) {
  for (const match of content.matchAll(/\[[^\]]*\]\(([^)]+)\)/g)) {
    let target = match[1].trim();
    if (/^(?:https?:|mailto:|#)/i.test(target)) continue;
    if (target.startsWith("<") && target.endsWith(">")) target = target.slice(1, -1);
    target = target.split("#", 1)[0];
    if (target === "") continue;
    const resolved = path.resolve(root, path.dirname(file), decodeURIComponent(target));
    if (!fs.existsSync(resolved)) brokenLinks.push(`${file} -> ${target}`);
  }
}
assert(brokenLinks.length === 0, `깨진 로컬 문서 링크\n${brokenLinks.join("\n")}`);

const definitionSources = [
  {
    name: "요구사항",
    expected: 82,
    files: ["docs/REQUIREMENTS_BASELINE.md"],
    pattern: /^\| ((?:SYS|AUTH|CLS|CRS|ECO|TRD|FIN|QZ|AI|DAT)-\d{3}) \|/gm,
    reference: /(?<![A-Z0-9-])((?:SYS|AUTH|CLS|CRS|ECO|TRD|FIN|QZ|AI|DAT)-\d{3})(?!\d)/g,
  },
  {
    name: "상태 머신",
    expected: 23,
    files: ["docs/STATE_MACHINES.md"],
    pattern: /^\| (SM-[A-Z]+-\d{3}) \|/gm,
    reference: /(?<![A-Z0-9-])(SM-[A-Z]+-\d{3})(?!\d)/g,
  },
  {
    name: "권한 규칙",
    expected: 62,
    files: ["docs/PERMISSION_MATRIX.md"],
    pattern: /^\| (PM-[A-Z]+-\d{3}) \|/gm,
    reference: /(?<![A-Z0-9-])(PM-[A-Z]+-\d{3})(?!\d)/g,
  },
  {
    name: "화면",
    expected: 76,
    files: ["docs/SCREEN_SPEC.md"],
    pattern: /^\| (SCR-[A-Z]+-\d{3}) \|/gm,
    reference: /(?<![A-Z0-9-])(SCR-[A-Z]+-\d{3})(?!\d)/g,
  },
  {
    name: "화면 행동",
    expected: 71,
    files: ["docs/SCREEN_SPEC.md"],
    pattern: /^\| (UIA-[A-Z]+-\d{3}) \|/gm,
    reference: /(?<![A-Z0-9-])(UIA-[A-Z]+-\d{3})(?!\d)/g,
  },
  {
    name: "HTTP API",
    expected: 142,
    files: ["docs/API_COMMON_RULES.md", "docs/HTTP_API_SPEC.md"],
    pattern: /^\| (API-[A-Z]+-\d{3})(?: \|| ·)/gm,
    reference: /(?<![A-Z0-9-])(API-[A-Z]+-\d{3})(?!\d)/g,
  },
  {
    name: "WebSocket 채널",
    expected: 10,
    files: ["docs/WEBSOCKET_SPEC.md"],
    pattern: /^\| (WS-[A-Z]+-\d{3}) \|/gm,
    reference: /(?<![A-Z0-9-])(WS-[A-Z]+-\d{3})(?!\d)/g,
  },
  {
    name: "WebSocket 이벤트",
    expected: 48,
    files: ["docs/WEBSOCKET_SPEC.md"],
    pattern: /^\| (EVT-[A-Z]+-\d{3}) \|/gm,
    reference: /(?<![A-Z0-9-])(EVT-[A-Z]+-\d{3})(?!\d)/g,
  },
  {
    name: "논리 데이터 규칙",
    expected: 14,
    files: ["docs/DATA_MODEL.md"],
    pattern: /^\| (DM-\d{3}) \|/gm,
    reference: /(?<![A-Z0-9-])(DM-\d{3})(?!\d)/g,
  },
  {
    name: "핵심 유일성",
    expected: 21,
    files: ["docs/PHYSICAL_SCHEMA.md"],
    pattern: /^\| (UK-\d{3}) \|/gm,
    reference: /(?<![A-Z0-9-])(UK-\d{3})(?!\d)/g,
  },
  {
    name: "검사 제약",
    expected: 42,
    files: ["docs/PHYSICAL_SCHEMA.md"],
    pattern: /^\| (CK-\d{3}) \|/gm,
    reference: /(?<![A-Z0-9-])(CK-\d{3})(?!\d)/g,
  },
  {
    name: "조회 인덱스",
    expected: 40,
    files: ["docs/PHYSICAL_SCHEMA.md"],
    pattern: /^\| (IX-\d{3}) \|/gm,
    reference: /(?<![A-Z0-9-])(IX-\d{3})(?!\d)/g,
  },
  {
    name: "DB 인수 기준",
    expected: 22,
    files: ["docs/PHYSICAL_SCHEMA.md"],
    pattern: /^\| (DBT-\d{3}) \|/gm,
    reference: /(?<![A-Z0-9-])(DBT-\d{3})(?!\d)/g,
  },
];

const corpus = [...markdown.values(), read("contracts/openapi.yaml"), read("contracts/asyncapi.yaml")].join("\n");
const idCounts = {};
const definitionsByName = new Map();
for (const source of definitionSources) {
  const definitionsWithDuplicates = source.files.flatMap((file) =>
    matches(read(file), new RegExp(source.pattern.source, source.pattern.flags)),
  );
  const definitions = unique(definitionsWithDuplicates);
  const duplicateDefinitions = unique(
    definitionsWithDuplicates.filter(
      (value, index, values) => values.indexOf(value) !== index,
    ),
  );
  assert(
    duplicateDefinitions.length === 0,
    `${source.name} 정의 중복: ${formatList(duplicateDefinitions)}`,
  );
  assert(
    definitions.length === source.expected,
    `${source.name} 정의 수가 ${source.expected}개가 아닙니다: ${definitions.length}`,
  );

  const references = unique(matches(corpus, new RegExp(source.reference.source, source.reference.flags)));
  const definitionSet = new Set(definitions);
  const undefinedReferences = references.filter((value) => !definitionSet.has(value));
  assert(
    undefinedReferences.length === 0,
    `정의되지 않은 ${source.name} ID: ${formatList(undefinedReferences)}`,
  );
  idCounts[source.name] = definitions.length;
  definitionsByName.set(source.name, definitions);
}

const rtm = read("docs/RTM.md");
const rtmRequirementSection = rtm.split(/^## 9\. /m, 1)[0];
const rtmRequirements = unique(
  matches(
    rtmRequirementSection,
    /^\| ((?:SYS|AUTH|CLS|CRS|ECO|TRD|FIN|QZ|AI|DAT)-\d{3}) \|/gm,
  ),
);
const baselineRequirements = definitionsByName.get("요구사항");
const rtmMissingRequirements = baselineRequirements.filter(
  (requirement) => !rtmRequirements.includes(requirement),
);
const rtmUnexpectedRequirements = rtmRequirements.filter(
  (requirement) => !baselineRequirements.includes(requirement),
);
assert(
  rtmMissingRequirements.length === 0 && rtmUnexpectedRequirements.length === 0,
  `RTM 요구사항 행 불일치\n누락: ${formatList(rtmMissingRequirements)}\n예상 밖: ${formatList(rtmUnexpectedRequirements)}`,
);

const dbTestedRequirements = unique(
  matches(
    rtmRequirementSection,
    /^\| ((?:SYS|AUTH|CLS|CRS|ECO|TRD|FIN|QZ|AI|DAT)-\d{3}) \|[^\n]*DBT-\d{3}[^\n]*\|$/gm,
  ),
);
const documentedDbTestCoverage = Number(
  rtm.match(/^\| 합계 \|[^\n]*\| (\d+) \|$/m)?.[1] ?? Number.NaN,
);
assert(
  documentedDbTestCoverage === dbTestedRequirements.length,
  `RTM DB 테스트 커버리지 합계 불일치: 표 ${documentedDbTestCoverage}, 실제 연결 ${dbTestedRequirements.length}`,
);

const rtmCoverageSections = [
  ["플랫폼·인증", "## 2. ", "## 3. "],
  ["경제교실·코스", "## 3. ", "## 4. "],
  ["경제·개인거래", "## 4. ", "## 5. "],
  ["금융", "## 5. ", "## 6. "],
  ["퀴즈", "## 6. ", "## 7. "],
  ["AI", "## 7. ", "## 8. "],
  ["데이터·운영", "## 8. ", "## 9. "],
];
const rtmCoverageSummary = rtm.split("## 14. ", 2)[1]?.split("## 15. ", 1)[0] ?? "";
for (const [label, startMarker, endMarker] of rtmCoverageSections) {
  const section = rtm.split(startMarker, 2)[1]?.split(endMarker, 1)[0] ?? "";
  const sectionRequirements = unique(
    matches(
      section,
      /^\| ((?:SYS|AUTH|CLS|CRS|ECO|TRD|FIN|QZ|AI|DAT)-\d{3}) \|/gm,
    ),
  );
  const sectionDbTestedRequirements = unique(
    matches(
      section,
      /^\| ((?:SYS|AUTH|CLS|CRS|ECO|TRD|FIN|QZ|AI|DAT)-\d{3}) \|[^\n]*DBT-\d{3}[^\n]*\|$/gm,
    ),
  );
  const summaryLine = rtmCoverageSummary
    .split(/\r?\n/)
    .find((line) => line.startsWith(`| ${label} |`));
  const summaryCells = summaryLine?.split("|").map((cell) => cell.trim()).filter(Boolean) ?? [];
  const documentedRequirements = Number(summaryCells[1]);
  const documentedDbTests = Number(summaryCells.at(-1));
  assert(
    documentedRequirements === sectionRequirements.length &&
      documentedDbTests === sectionDbTestedRequirements.length,
    `${label} RTM 요약 불일치: 요구사항 ${documentedRequirements}/${sectionRequirements.length}, DBT 연결 ${documentedDbTests}/${sectionDbTestedRequirements.length}`,
  );
}

const versionByFile = new Map();
for (const [file, content] of markdown) {
  const version = content.match(/^- 버전: (v\d+\.\d+)$/m)?.[1];
  if (version) versionByFile.set(path.resolve(root, file), version);
}

const staleVersionLinks = [];
for (const [file, content] of markdown) {
  for (const match of content.matchAll(/\[[^\]]*?\s(v\d+\.\d+)\]\(([^)#]+)(?:#[^)]+)?\)/g)) {
    const [, linkedVersion, target] = match;
    const resolved = path.resolve(root, path.dirname(file), decodeURIComponent(target));
    const actualVersion = versionByFile.get(resolved);
    if (actualVersion && linkedVersion !== actualVersion) {
      staleVersionLinks.push(`${file} -> ${target}: ${linkedVersion} / ${actualVersion}`);
    }
  }
}
assert(
  staleVersionLinks.length === 0,
  `문서 링크 버전 불일치\n${staleVersionLinks.join("\n")}`,
);

const forbiddenStalePhrases = [
  "PDM-",
  "Testcontainers 18개",
  "Testcontainers 20개",
  "DBT-001~017",
  "RTM v1.1",
  "기준 Git 커밋",
  "설계 기준 Git 커밋",
];
const stalePhraseHits = forbiddenStalePhrases.flatMap((phrase) =>
  [...markdown]
    .filter(([, content]) => content.includes(phrase))
    .map(([file]) => `${file}: ${phrase}`),
);
assert(stalePhraseHits.length === 0, `오래된 표현이 남았습니다\n${stalePhraseHits.join("\n")}`);

console.log(
  JSON.stringify(
    {
      status: "PASS",
      markdown_files: markdownFiles.length,
      broken_local_links: 0,
      stale_version_links: 0,
      undefined_id_references: 0,
      rtm_requirements: rtmRequirements.length,
      rtm_requirements_with_db_tests: dbTestedRequirements.length,
      definitions: idCounts,
    },
    null,
    2,
  ),
);
