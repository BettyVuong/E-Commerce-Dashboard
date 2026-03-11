# How To Make Full Integration Tests (Frontend + Backend + DB)

This project now includes a **full-stack integration test harness** under `integration/` and a CI `integration` stage in `.gitlab-ci.yml`.

Use this page as the team wiki reference for adding more integration tests.

## What "full integration" means in this project

A full integration test must validate behavior across all live services:

1. Frontend (React dev server)
2. Backend (Spring Boot API)
3. Database (MariaDB)

A valid test should run against the real running stack, not mocked services.

## Current baseline that is already wired

The baseline smoke tests live in `integration/run-integration-tests.mjs` and currently cover:

1. Backend end-to-end flow with DB persistence.
2. Frontend-to-backend communication using frontend `/api` proxy.
3. Positive path and failure path assertions.
4. JUnit report output for GitLab merge blocking.

## File map

- `compose.yaml`: local/full stack definition.
- `.gitlab-ci.yml`: integration stage/job (`integration-full-stack`).
- `integration/run-integration-tests.mjs`: framework runner only (readiness + discovery + execution + JUnit).
- `integration/package.json`: local npm script entrypoint for integration tests.
- `integration/tests/_shared/*.mjs`: reusable helpers for features.
- `integration/tests/backend/<feature>/*.test.mjs`: tests that start from backend entrypoints.
- `integration/tests/frontend/<feature>/*.test.mjs`: tests that start from frontend entrypoints.
- `integration/fixtures/retail-smoke.csv`: smoke test input fixture.
- `integration/artifacts/`: logs and JUnit XML generated in CI.

Contributor rule:

1. Team members should only add/edit files under `integration/tests/**` for feature work.
2. `run-integration-tests.mjs` should stay generic and not include feature-specific API flow logic.

## Organizing tests for team parallel development

Yes, you can and should organize full integration tests by both:

1. Entry path (`backend` vs `frontend`)
2. Feature (`cleaning`, future features)

This keeps ownership clear and reduces merge conflicts.

### Placement rules

Use `integration/tests/backend/<feature>/` when:

1. The flow is initiated by backend API calls.
2. The main risk is backend/data behavior (service + DB + response).

Use `integration/tests/frontend/<feature>/` when:

1. The flow is initiated through frontend-facing paths.
2. The main risk is frontend-to-backend wiring plus persisted outcome.

Even when sorted this way, tests are still full integration tests because they run against live frontend/backend/database services.

## How integration CI works

The `integration-full-stack` job does this:

1. Starts full stack with `docker compose -f compose.yaml up -d --build`.
2. Waits until DB ping, backend API, and frontend are reachable.
3. Runs integration tests.
4. Always uploads artifacts:
   - JUnit XML
   - backend/frontend/database logs
   - full container logs
5. Fails pipeline if any integration test fails.

It runs on:

1. Merge request pipelines.
2. Pushes to the default branch.

## How to add a new integration test

### Step 1: Pick the user/system flow

Choose one real flow, for example:

- Upload file -> cleaning job -> cleaned/invalid data retrieval.
- View existing results -> paginated browse.

### Step 2: Add or reuse a fixture

Put stable fixture files in `integration/fixtures/`.

Tips:

1. Keep fixtures small and deterministic.
2. Include edge-case fixture files for failure paths.

### Step 3: Add a test module under the right folder

Backend feature example:

`integration/tests/backend/cleaning/my-flow.test.mjs`

Frontend feature example:

`integration/tests/frontend/cleaning/my-flow.test.mjs`

Pattern:

```javascript
export function defineTests(ctx) {
   return [
      {
         name: "cleaning feature: my new full integration flow",
         run: async () => {
            const res = await ctx.http("GET", `${ctx.backendBaseUrl}/api/your-endpoint`);
            if (res.status !== 200) {
               throw new Error(`expected 200, got ${res.status}: ${res.text}`);
            }
         }
      }
   ];
}
```

The runner auto-discovers `*.test.mjs`, so you do not need to edit `run-integration-tests.mjs` for each new test file.

### Step 4: Include a failure-path test

For each positive flow, add one negative/failure-path test.

Examples:

1. Invalid pagination (`page=-1`) returns 400.
2. Invalid request payload returns 400.
3. Missing resource returns 404.

### Step 5: Ensure assertion validates persistence

At least one assertion should prove DB-backed persistence happened.

Examples:

1. `totalEntries` increases after upload.
2. Newly persisted row can be fetched by API.

### Step 6: Verify locally

From repo root:

```bash
docker compose -f compose.yaml up -d --build
npm --prefix integration run test
docker compose -f compose.yaml down -v
```

Parallel team workflows:

```bash
npm --prefix integration run test:backend
npm --prefix integration run test:frontend
```

Prerequisite:

1. Node.js 20+ installed locally.

## Test design standards for this repo

1. Use stable API-level assertions first.
2. Keep each test focused on one behavior.
3. Use clear failure messages (include expected and actual values).
4. Keep timeout/retry values explicit in code.
5. Always include at least one positive and one failure scenario per feature area.

## Recommended next expansion

1. Add a browser UI E2E layer (Playwright) for click-level user flows.
2. Add one feature folder at a time under both `backend/` and `frontend/` as needed.
3. Add additional fixtures for invalid data and large batch behavior.
4. Add schema/data reset helper if tests need strict isolation per case.

## Troubleshooting quick guide

1. Stack does not come up:
   - Check `integration/artifacts/container-logs.log`.
2. Backend unreachable:
   - Check `integration/artifacts/backend.log`.
3. Frontend proxy failures:
   - Check `integration/artifacts/frontend.log` and `frontend/src/setupProxy.js`.
4. DB connectivity issues:
   - Check `integration/artifacts/database.log` and DB env vars in `compose.yaml`.

## Quick team rule of thumb

1. Sort by feature first (`cleaning`, `rfm`, etc.).
2. Within each feature, split tests by entry path (`backend` or `frontend`).
3. Keep each test full-stack by asserting a persisted outcome or cross-service behavior.
