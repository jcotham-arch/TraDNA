FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app
COPY pyproject.toml README.md ./
COPY src ./src
COPY alembic.ini ./alembic.ini
COPY migrations ./migrations
COPY start.sh ./start.sh
RUN pip install --no-cache-dir .
RUN chmod +x ./start.sh

EXPOSE 8000
CMD ["./start.sh"]
