import { readFileSync, existsSync } from "node:fs";
import { resolve } from "node:path";

const root = resolve(import.meta.dirname, "..");
const failures = [];

function read(relativePath) {
  const path = resolve(root, relativePath);
  if (!existsSync(path)) {
    failures.push(`missing file: ${relativePath}`);
    return "";
  }
  return readFileSync(path, "utf8");
}

function requirePattern(text, pattern, message) {
  if (!pattern.test(text)) {
    failures.push(message);
  }
}

const compose = read("compose.yaml");
requirePattern(compose, /^\s{2}postgres:\s*$/m, "compose: postgres service missing");
requirePattern(compose, /postgres:17-alpine/, "compose: PostgreSQL 17 image missing");
requirePattern(compose, /^\s{2}redis:\s*$/m, "compose: redis service missing");
requirePattern(compose, /healthcheck:/, "compose: healthcheck missing");
requirePattern(
  compose,
  /^\s{2}otel-collector:\s*$/m,
  "compose: OpenTelemetry Collector service missing",
);
requirePattern(
  compose,
  /otel\/opentelemetry-collector:0\.157\.0/,
  "compose: pinned OpenTelemetry Collector image missing",
);

if (existsSync(resolve(root, "backend/src/main/java/com/b201/ecovillage/EcoVillageApplication.java"))) {
  requirePattern(compose, /^\s{2}backend:\s*$/m, "compose: backend service missing");
  requirePattern(compose, /dockerfile:\s+backend\/Dockerfile/, "compose: backend Dockerfile missing");

  const dockerfile = read("backend/Dockerfile");
  requirePattern(dockerfile, /eclipse-temurin:21-jdk/, "backend image: Java 21 build image missing");
  requirePattern(dockerfile, /eclipse-temurin:21-jre/, "backend image: Java 21 runtime image missing");
  requirePattern(dockerfile, /USER spring:spring/, "backend image: non-root runtime user missing");
}

const env = read(".env.example");
for (const key of [
  "POSTGRES_DB",
  "POSTGRES_USER",
  "POSTGRES_PASSWORD",
  "POSTGRES_HOST",
  "REDIS_HOST",
  "AWS_REGION",
  "OTEL_GRPC_PORT",
  "OTEL_HTTP_PORT",
  "OTEL_EXPORTER_OTLP_ENDPOINT",
]) {
  requirePattern(env, new RegExp(`^${key}=`, "m"), `env: ${key} missing`);
}

const jenkins = read("Jenkinsfile");
for (const stage of [
  "Toolchain",
  "Contracts and Schema",
  "Terraform",
  "Backend",
  "Backend Image",
  "PostgreSQL Test Guard",
]) {
  requirePattern(
    jenkins,
    new RegExp(`stage\\('${stage}'\\)`),
    `Jenkinsfile: ${stage} stage missing`,
  );
}

const terraform = read("infra/terraform/terraform.tf");
requirePattern(terraform, /required_version/, "terraform: CLI version constraint missing");
requirePattern(terraform, /hashicorp\/aws/, "terraform: AWS provider missing");
requirePattern(terraform, /backend\s+"s3"/, "terraform: S3 backend contract missing");

const gitignore = read(".gitignore");
requirePattern(
  gitignore,
  /infra\/terraform\/backend\.\*\.hcl/,
  "gitignore: environment backend config exclusion missing",
);
requirePattern(
  gitignore,
  /infra\/terraform\/\*\.tfvars/,
  "gitignore: environment tfvars exclusion missing",
);

const providers = read("infra/terraform/providers.tf");
requirePattern(providers, /region = var\.aws_region/, "terraform: region variable not wired");
requirePattern(providers, /default_tags/, "terraform: default tags missing");

const baseline = read("docs/INFRASTRUCTURE_BASELINE.md");
for (const service of [
  "ECS Fargate",
  "RDS",
  "ElastiCache",
  "S3",
  "CloudFront",
  "SQS",
  "Bedrock",
  "OpenTelemetry",
  "Sentry",
]) {
  requirePattern(baseline, new RegExp(service), `baseline: ${service} missing`);
}

if (failures.length > 0) {
  console.error("Infrastructure validation FAILED");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Infrastructure validation PASS");
console.log("- local services: PostgreSQL 17, Redis, OpenTelemetry Collector");
console.log("- CI: Jenkins contract, Terraform, backend and DB guard stages");
console.log("- Terraform: version, AWS provider, Seoul region and common tags");
console.log("- target stack: ECS, RDS, ElastiCache, S3, CloudFront, SQS, Bedrock, observability");
