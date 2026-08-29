from fastapi import FastAPI

from tradna_backend import __version__
from tradna_backend.config import Settings

settings = Settings.from_environment()
app = FastAPI(
    title="TraDNA API",
    version=__version__,
    description="Evidence-based trading coaching backend. No live execution endpoints.",
)


@app.get("/v1/health", tags=["system"])
def health() -> dict[str, str]:
    return {
        "status": "ok",
        "version": __version__,
        "environment": settings.environment,
        "execution": "disabled",
    }
