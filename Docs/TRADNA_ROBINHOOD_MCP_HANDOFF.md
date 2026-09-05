# Robinhood MCP final connection handoff

## What is ready

TraDNA's backend has a read-only Robinhood adapter, immutable broker-event
ingestion, quote storage, synchronization run tracking, a supervised polling
scheduler, and an authenticated status API. The adapter contains an explicit
allowlist and cannot call order review, placement, cancellation, exercise,
transfer, watchlist mutation, or scanner mutation tools.

The only missing runtime component is an OAuth-authenticated Streamable HTTP
tool caller for `https://agent.robinhood.com/mcp/trading`. That caller is
injected into `RobinhoodAgenticReadOnly`; the rest of the pipeline is independent
of Robinhood credentials and covered by automated tests.

## Human authorization requirement

Robinhood requires the Agentic account and AI-agent authorization to be created
on a desktop device. The user must complete Robinhood's own OAuth page. Never
paste a password, one-time code, browser cookie, or OAuth token into source code,
ChatGPT, a shell command, an APK, or an environment file.

After the initial desktop authorization and backend deployment, Android can
display synchronization health, quotes, positions, and paper results. Routine
monitoring does not require the desktop to remain on when the backend is hosted
in the cloud. Reauthorization may require another browser approval if Robinhood
revokes or expires the grant.

## Deployment secrets

Configure these in the cloud platform's encrypted secret manager, never in Git:

- `TRADNA_DATABASE_URL`
- `TRADNA_CLIENT_API_TOKEN` (random, at least 32 characters)
- the MCP SDK's encrypted OAuth token/client-registration store key

Non-secret settings:

- `TRADNA_ROBINHOOD_MCP_URL=https://agent.robinhood.com/mcp/trading`
- `TRADNA_SYNC_INTERVAL_SECONDS=60`

## Activation sequence

1. Deploy the API and PostgreSQL database over TLS.
2. Verify `/v1/health` reports `execution: disabled`.
3. Complete Robinhood OAuth on a desktop browser.
4. Confirm the MCP server's advertised tools, retaining only TraDNA's read-only
   allowlist.
5. Create the dedicated Agentic brokerage-account reference in PostgreSQL.
6. Run one sync and reconcile counts against the sanitized August 29 baseline.
7. Allow the worker to poll the six-symbol paper universe.
8. Pair Android with the authenticated TraDNA API.
9. Accumulate five clean market sessions before reviewing any execution phase.

No account funding, historical accuracy result, elapsed date, or OAuth success
enables trading. Execution remains a separate future component and is disabled.
