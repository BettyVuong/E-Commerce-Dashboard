import { mkdir, readdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, "..");
const ARTIFACT_DIR = path.join(ROOT_DIR, "integration", "artifacts");
const JUNIT_FILE = path.join(ARTIFACT_DIR, "junit", "integration-tests.xml");
const TESTS_ROOT = path.join(ROOT_DIR, "integration", "tests");

const BACKEND_BASE_URL = process.env.BACKEND_BASE_URL ?? "http://localhost:8080";
const FRONTEND_BASE_URL = process.env.FRONTEND_BASE_URL ?? "http://localhost:3000";
const BACKEND_READY_PATH = process.env.BACKEND_READY_PATH ?? "/api/v3/api-docs/swagger-config";
const FRONTEND_READY_PATH = process.env.FRONTEND_READY_PATH ?? "/";
const FRONTEND_PROXY_READY_PATH = process.env.FRONTEND_PROXY_READY_PATH ?? "/api/v3/api-docs/swagger-config";

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function xmlEscape(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&apos;");
}

async function ensureArtifactDirs() {
  await mkdir(path.join(ARTIFACT_DIR, "junit"), { recursive: true });
}

async function http(method, url, { headers = {}, body } = {}) {
  const response = await fetch(url, { method, headers, body });
  const text = await response.text();
  let json = null;

  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = null;
  }

  return {
    status: response.status,
    text,
    json
  };
}

async function waitForReachable({ name, url, attempts = 60, delayMs = 2000 }) {
  for (let i = 1; i <= attempts; i += 1) {
    try {
      const res = await fetch(url, { method: "GET" });
      if (res.ok) {
        console.log(`[ready] ${name}`);
        return;
      }
      console.log(`[wait] ${name} HTTP ${res.status} (${i}/${attempts})`);
    } catch (error) {
      console.log(`[wait] ${name} network error (${i}/${attempts}): ${error.message}`);
    }

    await wait(delayMs);
  }

  throw new Error(`Timeout waiting for ${name}`);
}

async function waitForServices() {
  await waitForReachable({
    name: "backend API",
    url: `${BACKEND_BASE_URL}${BACKEND_READY_PATH}`
  });

  await waitForReachable({
    name: "frontend",
    url: `${FRONTEND_BASE_URL}${FRONTEND_READY_PATH}`
  });

  await waitForReachable({
    name: "frontend API proxy",
    url: `${FRONTEND_BASE_URL}${FRONTEND_PROXY_READY_PATH}`
  });
}

async function collectTestFiles(directoryPath) {
  const entries = await readdir(directoryPath, { withFileTypes: true });
  const files = [];

  for (const entry of entries) {
    const absolute = path.join(directoryPath, entry.name);
    if (entry.isDirectory()) {
      files.push(...(await collectTestFiles(absolute)));
      continue;
    }

    if (entry.isFile() && entry.name.endsWith(".test.mjs")) {
      files.push(absolute);
    }
  }

  return files;
}

async function loadDiscoveredTests(ctx) {
  const selectedScope =
    process.argv.find((arg) => arg.startsWith("--scope="))?.split("=")[1] ??
    process.env.INTEGRATION_SCOPE ??
    "all";

  const scopes =
    selectedScope === "all"
      ? ["backend", "frontend"]
      : [selectedScope];

  for (const scope of scopes) {
    if (scope !== "backend" && scope !== "frontend") {
      throw new Error(`Invalid scope '${scope}'. Use all, backend, or frontend.`);
    }
  }

  const testFiles = [];
  for (const scope of scopes) {
    const scopeDir = path.join(TESTS_ROOT, scope);
    testFiles.push(...(await collectTestFiles(scopeDir)));
  }

  testFiles.sort((a, b) => a.localeCompare(b));

  const tests = [];

  for (const testFile of testFiles) {
    const moduleUrl = pathToFileURL(testFile).href;
    const testModule = await import(moduleUrl);

    if (typeof testModule.defineTests !== "function") {
      throw new Error(`Test module missing defineTests(ctx): ${path.relative(ROOT_DIR, testFile)}`);
    }

    const moduleTests = testModule.defineTests(ctx);
    if (!Array.isArray(moduleTests)) {
      throw new Error(`defineTests(ctx) must return an array: ${path.relative(ROOT_DIR, testFile)}`);
    }

    for (const moduleTest of moduleTests) {
      if (!moduleTest?.name || typeof moduleTest.run !== "function") {
        throw new Error(`Invalid test entry in ${path.relative(ROOT_DIR, testFile)}`);
      }
      tests.push(moduleTest);
    }
  }

  return tests;
}

async function writeJUnit(results) {
  const failures = results.filter((r) => !r.passed).length;
  const testCasesXml = results
    .map((result) => {
      if (result.passed) {
        return `<testcase classname=\"integration\" name=\"${xmlEscape(result.name)}\"/>`;
      }

      const message = xmlEscape(result.errorMessage ?? "unknown test failure");
      return `<testcase classname=\"integration\" name=\"${xmlEscape(result.name)}\"><failure message=\"${message}\">${message}</failure></testcase>`;
    })
    .join("\n");

  const xml = `<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testsuite name=\"full-integration\" tests=\"${results.length}\" failures=\"${failures}\">\n${testCasesXml}\n</testsuite>\n`;

  await writeFile(JUNIT_FILE, xml, "utf8");
}

async function main() {
  await ensureArtifactDirs();
  await waitForServices();

  const ctx = {
    backendBaseUrl: BACKEND_BASE_URL,
    frontendBaseUrl: FRONTEND_BASE_URL,
    http,
    wait
  };

  const tests = await loadDiscoveredTests(ctx);

  if (tests.length === 0) {
    throw new Error("No integration tests found under integration/tests");
  }

  const results = [];

  for (const test of tests) {
    process.stdout.write(`[test] ${test.name}\n`);
    try {
      await test.run();
      process.stdout.write(`[pass] ${test.name}\n`);
      results.push({ name: test.name, passed: true });
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      process.stdout.write(`[fail] ${test.name}: ${errorMessage}\n`);
      results.push({ name: test.name, passed: false, errorMessage });
    }
  }

  await writeJUnit(results);

  const failures = results.filter((r) => !r.passed).length;
  if (failures > 0) {
    throw new Error(`Integration tests failed: ${failures}/${results.length}`);
  }

  process.stdout.write(`Integration tests passed: ${results.length}/${results.length}\n`);
}

main().catch((error) => {
  process.stderr.write(`${error.message}\n`);
  process.exitCode = 1;
});
