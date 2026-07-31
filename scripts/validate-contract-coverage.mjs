import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const root = process.cwd();

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), "utf8");
}

function uniqueMatches(text, pattern) {
  return [...new Set([...text.matchAll(pattern)].map((match) => match[1]))].sort();
}

function difference(left, right) {
  const rightSet = new Set(right);
  return left.filter((value) => !rightSet.has(value));
}

function assertSame(label, expected, actual) {
  const missing = difference(expected, actual);
  const unexpected = difference(actual, expected);
  if (missing.length > 0 || unexpected.length > 0) {
    throw new Error(
      `${label} 불일치\n누락: ${missing.join(", ") || "없음"}\n예상 밖: ${
        unexpected.join(", ") || "없음"
      }`,
    );
  }
}

const httpMarkdown = read("docs/HTTP_API_SPEC.md");
const websocketMarkdown = read("docs/WEBSOCKET_SPEC.md");
const openapi = read("contracts/openapi.yaml");
const asyncapi = read("contracts/asyncapi.yaml");

const sourceApiIds = uniqueMatches(httpMarkdown, /^\| (API-[A-Z]+-\d{3})/gm);
const contractApiIds = uniqueMatches(openapi, /^\s+x-api-id: "(API-[A-Z]+-\d{3})"/gm);
const operationIds = uniqueMatches(openapi, /^\s+operationId: "([^"]+)"/gm);

const sourceChannelIds = uniqueMatches(websocketMarkdown, /^\| (WS-[A-Z]+-\d{3})/gm);
const contractChannelIds = uniqueMatches(
  asyncapi,
  /^\s+x-channel-id: "(WS-[A-Z]+-\d{3})"/gm,
);
const sourceEventIds = uniqueMatches(websocketMarkdown, /^\| (EVT-[A-Z]+-\d{3})/gm);
const contractEventIds = uniqueMatches(asyncapi, /^\s+x-event-id: "(EVT-[A-Z]+-\d{3})"/gm);
const sourceEventTypes = uniqueMatches(
  websocketMarkdown,
  /^\| EVT-[A-Z]+-\d{3} \| `([^`]+)`/gm,
);
const contractEventTypes = uniqueMatches(
  asyncapi,
  /^\s+name: "([a-z][a-z0-9_.]+)"\r?\n\s+title: "EVT-[A-Z]+-\d{3}"/gm,
);

assertSame("API ID", sourceApiIds, contractApiIds);
assertSame("WebSocket 채널 ID", sourceChannelIds, contractChannelIds);
assertSame("이벤트 ID", sourceEventIds, contractEventIds);
assertSame("event_type", sourceEventTypes, contractEventTypes);

if (sourceApiIds.length !== 108) throw new Error(`API 수가 108이 아닙니다: ${sourceApiIds.length}`);
if (operationIds.length !== 108)
  throw new Error(`operationId 수가 108이 아닙니다: ${operationIds.length}`);
if (sourceChannelIds.length !== 10)
  throw new Error(`채널 수가 10이 아닙니다: ${sourceChannelIds.length}`);
if (sourceEventIds.length !== 48)
  throw new Error(`이벤트 수가 48이 아닙니다: ${sourceEventIds.length}`);

console.log(
  JSON.stringify(
    {
      status: "PASS",
      openapi_operations: sourceApiIds.length,
      unique_operation_ids: operationIds.length,
      asyncapi_channels: sourceChannelIds.length,
      asyncapi_events: sourceEventIds.length,
      source_contract_id_diff: 0,
    },
    null,
    2,
  ),
);
