# Integration Test Runner

This integration runner works on Windows, macOS, and Linux.

## Test organization

Tests are grouped by:

1. Entry path (`backend` or `frontend`)
2. Feature folder (`cleaning`, and future features)

Current structure:

```text
integration/
	tests/
		_shared/
			cleaning-flow-helpers.mjs
		backend/
			cleaning/
				ingest-cleaning-flow.test.mjs
		frontend/
			app-shell/
				root-shell.test.mjs
			cleaning/
				proxy-cleaning-flow.test.mjs
```

## Runner vs tests folder

`run-integration-tests.mjs` only handles framework concerns:

1. Service readiness checks.
2. Test discovery and execution.
3. JUnit report output.

Feature/domain logic belongs under `integration/tests/**`.

1. Put feature tests in `integration/tests/backend/<feature>/` or `integration/tests/frontend/<feature>/`.
2. Put reusable feature helpers in `integration/tests/_shared/`.

### Where a test should go

Put tests in `integration/tests/backend/<feature>/` when:

1. The test starts at backend API endpoints directly.
2. You want backend-service-level integration validation with real DB persistence.

Put tests in `integration/tests/frontend/<feature>/` when:

1. The test starts through frontend entry/proxy paths.
2. You want to validate frontend-to-backend wiring and persisted outcomes.

Important:

1. Both folders still represent full integration tests.
2. Every test should still validate cross-service behavior, not isolated unit behavior.

## Local usage

1. Start the stack:

```bash
docker compose -f compose.yaml up -d --build
```

2. Run integration tests:

```bash
npm --prefix integration run test
```

Optional scoped runs for parallel development:

```bash
npm --prefix integration run test:backend
npm --prefix integration run test:frontend
```

The runner automatically discovers all `*.test.mjs` files under `integration/tests/backend` and `integration/tests/frontend`.

3. Stop the stack:

```bash
docker compose -f compose.yaml down -v
```

## Environment variables

- `BACKEND_BASE_URL` (default: `http://localhost:8080`)
- `FRONTEND_BASE_URL` (default: `http://localhost:3000`)

## Output

JUnit XML is written to:

- `integration/artifacts/junit/integration-tests.xml`
