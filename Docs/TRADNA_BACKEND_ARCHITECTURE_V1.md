# TraDNA backend architecture v1

## Decision

TraDNA will use a backend-authoritative architecture. Android remains the user
interface, approval surface, journal, notification client, and offline cache.

The backend owns continuous work, provider secrets, canonical state, versioned
analysis, model evaluation, broker connectivity, risk policy, and any future
execution.

## Non-negotiable safety rules

1. No production provider or broker secret ships in the APK.
2. Raw broker events are append-only and payload-hashed.
3. Derived trades and analyses carry algorithm versions.
4. Model output cannot call a broker directly.
5. Execution requires an independent deterministic risk gate.
6. Robinhood begins read-only through the official Agentic pathway.
7. No unofficial Robinhood mobile-session automation is permitted.
8. Paper evaluation precedes live execution.
9. A dedicated limited Agentic Account precedes any live rollout.
10. Every order review, approval, submission, and fill is audited.

## Migration strategy

The Android implementation remains the golden reference while backend domains
are ported. Each domain runs in parity before Android switches to its backend
response. Room becomes a synchronized cache only after server results match.

## Data flow

1. Broker/CSV events enter immutable ingestion.
2. Events are deduplicated but never silently discarded.
3. Versioned reconstruction produces stock and option episodes.
4. Market data is cached with provider and delay provenance.
5. Versioned analysis produces features and evidence.
6. Coaching uses evidence, sample size, and confidence.
7. Prospective recommendations are frozen before outcomes are known.
8. Calibration and paper evaluation gate any execution capability.

## Initial service boundary

The first deployment is a modular monolith: FastAPI, PostgreSQL, Alembic, and a
background worker. This avoids premature microservice complexity while keeping
broker execution isolated as a future module boundary.

