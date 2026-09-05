# TraDNA backend

This service is the future authoritative home for brokerage events, market data,
historical reconstruction, coaching evidence, model evaluation, and guarded
execution. The Android application remains the client and offline cache.

## Safety boundary

- No Robinhood credentials are accepted in environment variables or the APK.
- No order-placement endpoint exists.
- Provider keys are read from backend environment variables only.
- Raw brokerage events are immutable; derived analyses are versioned.
- CSV reports retain a source hash for provenance, not the original document.
- Brokerage-data upload endpoints remain unavailable until authentication is implemented.

## Local development

Copy `.env.example` to `.env`, use non-production local values, then run:

```text
docker compose up --build
```

The public API health endpoint is `GET /v1/health`. After configuring a random
`TRADNA_CLIENT_API_TOKEN` of at least 32 characters, an authenticated client can
send a Robinhood export as a `text/csv` body to
`POST /v1/imports/robinhood-csv/analyze`. The response contains reconciliation
totals only; the endpoint does not retain the CSV. Use TLS outside localhost.

Docker Desktop or another Docker-compatible runtime is required for the local
PostgreSQL container. Domain tests and offline migration compilation do not
require Docker.

Dependency-free financial domain tests can run with Python's standard library:

```text
PYTHONPATH=src python -m unittest discover -s tests -v
```

Set `TRADNA_GOLDEN_CSV` to a local Robinhood CSV path to run the private parity
test. The CSV and its contents are never copied into the repository.

## Live read-only boundary

`GET /v1/live/status` is bearer-authenticated and returns the latest broker sync
state and one latest quote per symbol. The Robinhood adapter accepts an injected
MCP tool caller and enforces a read-only tool allowlist. OAuth token storage and
the Streamable HTTP connection are deliberately the final deployment step; no
order tools are exposed by the adapter.

The container runs all Alembic migrations before starting the API. Production
deployments must provide PostgreSQL, TLS, a random client token, and encrypted
OAuth storage.
