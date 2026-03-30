/*
  ui-pie-api.test.mjs

  Browser-level integration test for pie endpoint consumption from the
  frontend runtime context. This validates that the frontend can request
  pie data through its API path without browser/runtime errors.
*/

import { chromium } from "playwright";
import { uploadFixture, createCleaningJob, waitForCompletedCleaningJob } from "../../_shared/cleaning-flow-helpers.mjs";

function toNumber(value) {
  const n = Number(value);
  if (Number.isNaN(n)) {
    throw new Error(`expected numeric value, got '${value}'`);
  }
  return n;
}

export function defineTests(ctx) {
  return [
    {
      name: "frontend pie browser path: pie API payload is consumable without runtime errors",
      run: async () => {
        await uploadFixture(ctx, ctx.frontendBaseUrl);
        const jobId = await createCleaningJob(ctx, ctx.frontendBaseUrl);
        await waitForCompletedCleaningJob(ctx, ctx.frontendBaseUrl, jobId);

        const browser = await chromium.launch({ headless: true });
        const page = await browser.newPage();
        const pageErrors = [];

        page.on("pageerror", (err) => {
          pageErrors.push(err.message);
        });

        try {
          await page.goto(ctx.frontendBaseUrl, { waitUntil: "networkidle" });
          await page.waitForSelector("text=View Existing Results", { timeout: 10000 });
          await page.click("text=View Existing Results");

          const piePayload = await page.evaluate(async () => {
            const start = encodeURIComponent("2010-01-01T00:00:00");
            const end = encodeURIComponent("2010-12-31T23:59:59");
            const response = await fetch(`/api/rfm?view=pie&startDate=${start}&endDate=${end}`);
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
          });

          if (piePayload.status !== 200) {
            throw new Error(`expected 200 from browser pie API call, got ${piePayload.status}: ${piePayload.text}`);
          }

          if (!piePayload.json || typeof piePayload.json !== "object") {
            throw new Error("expected pie JSON object from browser API call");
          }

          if (!Array.isArray(piePayload.json.slices)) {
            throw new Error("expected pie slices array from browser API call");
          }

          const totalRevenue = toNumber(piePayload.json.totalRevenue);
          if (totalRevenue < 0) {
            throw new Error(`expected non-negative totalRevenue, got ${totalRevenue}`);
          }

          for (const slice of piePayload.json.slices) {
            if (!("country" in slice) || !("revenue" in slice) || !("percentage" in slice)) {
              throw new Error("pie slice missing required keys in browser API payload");
            }
            toNumber(slice.revenue);
            toNumber(slice.percentage);
          }

          if (pageErrors.length > 0) {
            throw new Error(`frontend runtime errors detected: ${pageErrors.join(" | ")}`);
          }
        } finally {
          await browser.close();
        }
      }
    }
  ];
}

export default defineTests;