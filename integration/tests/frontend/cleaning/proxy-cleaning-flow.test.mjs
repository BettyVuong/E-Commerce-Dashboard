import {
  createCleaningJob,
  getDirtyTotal,
  uploadFixture,
  waitForCompletedCleaningJob
} from "../../_shared/cleaning-flow-helpers.mjs";

export function defineTests(ctx) {
  return [
    {
      name: "frontend cleaning feature: proxy flow persists via backend",
      run: async () => {
        const beforeTotal = await getDirtyTotal(ctx, ctx.frontendBaseUrl);

        await uploadFixture(ctx, ctx.frontendBaseUrl);
        const jobId = await createCleaningJob(ctx, ctx.frontendBaseUrl);
        await waitForCompletedCleaningJob(ctx, ctx.frontendBaseUrl, jobId);

        const afterTotal = await getDirtyTotal(ctx, ctx.frontendBaseUrl);
        if (afterTotal <= beforeTotal) {
          throw new Error(
            `expected dirty_data totalEntries to increase via frontend flow (before=${beforeTotal}, after=${afterTotal})`
          );
        }
      }
    },
    {
      name: "frontend cleaning feature: invalid page returns 400",
      run: async () => {
        const res = await ctx.http(
          "GET",
          `${ctx.frontendBaseUrl}/api/cleaning-data/dirty?page=-1&size=15`
        );

        if (res.status !== 400) {
          throw new Error(`expected 400 for invalid page via frontend proxy, got ${res.status}`);
        }
      }
    }
  ];
}
