import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const INTEGRATION_DIR = path.resolve(__dirname, "..", "..");
const FIXTURE_FILE = path.join(INTEGRATION_DIR, "fixtures", "retail-smoke.csv");

export async function getDirtyTotal(ctx, baseUrl) {
  const res = await ctx.http("GET", `${baseUrl}/api/cleaning-data/dirty?page=0&size=15`);
  if (res.status !== 200 || !res.json) {
    throw new Error(`dirty total lookup failed with HTTP ${res.status}`);
  }

  return Number(res.json.totalEntries ?? 0);
}

export async function uploadFixture(ctx, baseUrl) {
  const form = new FormData();
  const fixtureBlob = new Blob([await readFile(FIXTURE_FILE, "utf8")], {
    type: "text/csv"
  });
  form.set("file", fixtureBlob, "retail-smoke.csv");

  const res = await ctx.http("POST", `${baseUrl}/api/ingest/upload`, { body: form });
  if (res.status !== 200) {
    throw new Error(`upload failed with HTTP ${res.status}: ${res.text}`);
  }
}

export async function createCleaningJob(ctx, baseUrl, batchSize = 2000) {
  const res = await ctx.http("POST", `${baseUrl}/api/cleaning-jobs`, {
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ batchSize })
  });

  if (res.status !== 202 || !res.json?.jobId) {
    throw new Error(`job creation failed with HTTP ${res.status}: ${res.text}`);
  }

  return String(res.json.jobId);
}

export async function waitForCompletedCleaningJob(ctx, baseUrl, jobId, attempts = 120) {
  for (let i = 1; i <= attempts; i += 1) {
    const res = await ctx.http("GET", `${baseUrl}/api/cleaning-jobs/${jobId}`);
    if (res.status !== 200 || !res.json) {
      throw new Error(`job polling failed with HTTP ${res.status}`);
    }

    const status = String(res.json.status ?? "UNKNOWN");
    if (status === "COMPLETED") {
      return;
    }

    if (status === "FAILED") {
      throw new Error("job status became FAILED");
    }

    await ctx.wait(1000);
  }

  throw new Error("job did not complete before timeout");
}
