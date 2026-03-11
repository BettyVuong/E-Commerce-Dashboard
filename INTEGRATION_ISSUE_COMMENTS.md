# Integration Testing Team Log (Issue Comment Source)

This file is a running development log for issue comments while building and validating full integration tests.

## Current Status (2026-03-11)

1. Full integration framework is implemented and runnable.
2. Tests are organized by entry path and feature:
  - `integration/tests/backend/<feature>/*.test.mjs`
  - `integration/tests/frontend/<feature>/*.test.mjs`
3. Cross-platform Node runner is in place (Windows/macOS/Linux + CI).
4. Local verification passed:
  - `npm --prefix integration run test` -> passed (`4/4`)
  - `npm --prefix integration run test:backend` -> passed (`2/2`)
  - `npm --prefix integration run test:frontend` -> passed (`2/2`)
5. CI migration in progress for course Kubernetes runner constraints:
  - DinD-based compose job failed on shared runner (`Cannot connect to Docker daemon`).
  - Integration job was migrated to non-DinD mode (MariaDB service + backend/frontend started in job container).
  - Backend startup diagnostics were added to surface failures quickly in CI logs.

## Chronological Work Log

### 2026-03-10 - Integration framework and CI stage

Completed:
1. Added `integration` stage to `.gitlab-ci.yml` after lint/test stages.
2. Added CI job `integration-full-stack` that starts compose services and runs integration tests.
3. Added artifact publishing (JUnit + backend/frontend/database/container logs).

Notes:
1. CI remains merge-blocking on integration failures.

### 2026-03-10 - Cross-platform runner migration

Completed:
1. Replaced shell scripts with Node runner: `integration/run-integration-tests.mjs`.
2. Added npm commands in `integration/package.json` for team-friendly execution.
3. Added test discovery so teammates can add files without editing the main runner.

### 2026-03-10 - Test organization for team parallelism

Completed:
1. Added backend feature test file:
  - `integration/tests/backend/cleaning/ingest-cleaning-flow.test.mjs`
2. Added frontend feature test file:
  - `integration/tests/frontend/cleaning/proxy-cleaning-flow.test.mjs`
3. Added scoped runs:
  - `npm --prefix integration run test:backend`
  - `npm --prefix integration run test:frontend`

### 2026-03-11 - CI adaptation for Kubernetes runner (no DinD)

Completed:
1. Reworked `integration-full-stack` in `.gitlab-ci.yml` to run without Docker-in-Docker.
2. Replaced compose orchestration in CI with:
  - GitLab service `mariadb:10.6` (alias `database`)
  - backend started via `./gradlew --no-daemon clean bootRun`
  - frontend started via `npm run start`
3. Added backend/frontend log capture and artifact bundling under `integration/artifacts/`.

Notes:
1. This is tailored to course/shared GitLab runners that typically do not allow privileged DinD.
2. Startup time is longer due to `apt-get` + Java install in the job image.

## Bugs Found and Resolved

### Bug 1 - Backend container build failed on Windows checkout

Summary:
1. `docker compose up --build` failed at backend image build with `./gradlew: not found`.

Root Cause:
1. Wrapper script compatibility issue from Windows checkout (line endings/execute bit in Linux container).

Fix:
1. Updated `backend/Dockerfile` to normalize and chmod wrapper:
  - `RUN sed -i 's/\r$//' gradlew && chmod +x gradlew`

Verification:
1. `docker compose -f compose.yaml up -d --build` completed successfully.

### Bug 2 - Frontend readiness check false-negative (HTTP 404)

Summary:
1. Full integration runner failed while waiting for frontend readiness.
2. Frontend root (`/`) returned backend 404 JSON due proxy behavior.

Fix:
1. Updated frontend readiness probe in `integration/run-integration-tests.mjs` to use proxy API endpoint:
  - `${FRONTEND_BASE_URL}/api/cleaning-data/dirty?page=0&size=15`

Verification:
1. Full suite passed (`4/4`).
2. Backend scope passed (`2/2`).
3. Frontend scope passed (`2/2`).

### Bug 3 - GitLab Kubernetes runner cannot use DinD daemon

Summary:
1. Integration CI failed before tests started with:
  - `Cannot connect to the Docker daemon at tcp://docker:2375`
2. `after_script` also failed to write logs in some cases because artifact directories were not guaranteed.

Root Cause:
1. Course/shared Kubernetes runner does not expose a usable privileged Docker daemon for DinD.

Fix:
1. Added early artifact directory creation in CI setup.
2. Added explicit Docker-daemon availability check with clear error messaging.
3. Migrated integration execution to non-DinD mode (job container processes + DB service).

Verification:
1. Job now proceeds past earlier DinD failure point and starts integration script steps.

### Bug 4 - Backend never became ready in non-DinD CI job

Summary:
1. CI progressed to backend startup but readiness loop kept printing `[wait] backend (x/60)`.

Likely Root Causes:
1. Backend readiness default expected `/actuator/health`, which is not configured in this project.
2. Flyway is configured for `filesystem:/flyway/sql`, but that path did not exist in non-DinD job.
3. Backend process exit was not surfaced early, delaying diagnosis.

Fix:
1. In CI, copy migration scripts to `/flyway/sql` before `bootRun`:
  - `mkdir -p /flyway/sql && cp -R ../database/scripts/. /flyway/sql/`
2. Updated backend default readiness probe for CI loop to:
  - `http://localhost:8080/api/docs.html`
3. Added fail-fast backend process check (`kill -0`) and tail-on-failure logs.
4. Removed masked integration failure (`|| true`) from integration runner invocation.

Verification:
1. Pending rerun on GitLab CI after latest patch.

### Bug 5 - Flyway migration failed because baseline table was never seeded in CI service DB

Summary:
1. Backend startup failed with Flyway error in CI:
  - `Migration V2__update_dirty_data.sql failed`
  - `Table 'template_db.dirty_data' doesn't exist`

Root Cause:
1. In non-DinD CI mode, MariaDB runs as a plain GitLab service container and does not automatically execute `database/scripts/setup.sql` like the compose/database image flow does.
2. Flyway starts at `V2__update_dirty_data.sql`, which assumes `dirty_data` already exists.

Fix:
1. Installed `mariadb-client` in integration job.
2. Added explicit DB readiness wait with `mariadb-admin ping`.
3. Seeded baseline schema before backend startup:
  - `mariadb -h database -uroot -p"$DB_PASSWORD" "$DB_DATABASE" < database/scripts/setup.sql`

Verification:
1. Pending rerun on GitLab CI after database seeding patch.

## Paste-Ready Issue Comment

```md
### Integration Progress Update (2026-03-11)

Completed:
- [x] Added dedicated `integration` stage in `.gitlab-ci.yml`.
- [x] Added merge-blocking `integration-full-stack` CI job with artifacts (JUnit + service/container logs).
- [x] Migrated integration execution from shell scripts to cross-platform Node runner.
- [x] Organized integration tests by entry path and feature:
  - `integration/tests/backend/<feature>/*.test.mjs`
  - `integration/tests/frontend/<feature>/*.test.mjs`
- [x] Added scoped commands for team parallel work:
  - `npm --prefix integration run test:backend`
  - `npm --prefix integration run test:frontend`
- [x] Adapted integration CI for course Kubernetes runner without DinD.
- [x] Added backend startup diagnostics (fail-fast checks + log tail on readiness timeout).

Validation Run (local):
- `docker compose -f compose.yaml up -d --build` -> pass
- `npm --prefix integration run test` -> pass (4/4)
- `npm --prefix integration run test:backend` -> pass (2/2)
- `npm --prefix integration run test:frontend` -> pass (2/2)

Validation Run (CI):
- DinD-based job failed on shared runner (`Cannot connect to Docker daemon`).
- Non-DinD integration job now starts backend/frontend orchestration path.
- Latest backend readiness hardening patch: rerun pending.
- Flyway baseline seeding fix added for service DB (`setup.sql` import): rerun pending.

Bugs Found + Fixed:
- Backend image build failure on Windows checkout (`./gradlew: not found`) fixed in `backend/Dockerfile` by normalizing/chmod wrapper.
- Frontend readiness false-negative (404 on `/`) fixed by probing frontend proxy API endpoint instead of root path.
- DinD daemon unavailable on course Kubernetes runner: mitigated by migrating integration job to non-DinD pattern.
- Backend readiness stall in CI: mitigated by creating `/flyway/sql`, using a valid readiness default, and fail-fast log diagnostics.
- Flyway `V2` failure (`dirty_data` missing) in non-DinD CI: mitigated by importing `database/scripts/setup.sql` before backend boot.

Next:
- [ ] Add more feature folders and test cases (`rfm`, `basket`, etc.).
- [ ] Add a browser-driven E2E layer (Playwright) for click-level flows.
- [ ] Confirm green CI run after latest non-DinD backend readiness fixes.
- [ ] Confirm green CI run after DB seeding patch for Flyway baseline tables.
```

## Acceptance Criteria Snapshot

1. Dedicated `integration` stage after lint/unit stages: done.
2. Full stack started in isolated CI environment: in progress (non-DinD migration applied; rerun pending).
3. Readiness checks for DB/backend/frontend paths: in progress (backend readiness hardening applied; rerun pending).
4. Backend DB-backed end-to-end scenario: done.
5. Frontend-to-backend scenario with persistence: done.
6. Positive + failure-path scenarios: done.
7. Integration artifacts published: done.
8. Merge blocked on integration failures: done.
