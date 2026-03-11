import {
  createCleaningJob,
  getDirtyTotal,
  uploadFixture,
  waitForCompletedCleaningJob
} from "../../_shared/cleaning-flow-helpers.mjs";

export function defineTests(ctx) {
  return [
    {
      name: "backend cleaning feature: persists and reads through API",
      run: async () => {
        await uploadFixture(ctx, ctx.backendBaseUrl);
        const jobId = await createCleaningJob(ctx, ctx.backendBaseUrl);
        await waitForCompletedCleaningJob(ctx, ctx.backendBaseUrl, jobId);

        const dirtyTotal = await getDirtyTotal(ctx, ctx.backendBaseUrl);
        if (dirtyTotal < 1) {
          throw new Error(`expected dirty_data totalEntries >= 1, got ${dirtyTotal}`);
        }
      }
    },
    {
      name: "backend cleaning feature: invalid page returns 400",
      run: async () => {
        const res = await ctx.http(
          "GET",
          `${ctx.backendBaseUrl}/api/cleaning-data/dirty?page=-1&size=15`
        );

        if (res.status !== 400) {
          throw new Error(`expected 400 for invalid page, got ${res.status}`);
        }
      }
    }
  ];
}
