#!/bin/sh
set -eu

alembic -c alembic.ini upgrade head
exec uvicorn tradna_backend.api.main:app --host 0.0.0.0 --port "${PORT:-8000}"
