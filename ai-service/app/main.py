"""FastAPI application exposing the fraud scoring endpoint.

The Java backend calls this service synchronously, with a short timeout, and
falls back to a conservative local rule when it cannot be reached. That means
this service can be restarted or retrained without the banking transfer path
losing its safety properties - a deliberate separation, so that the AI
component is an enhancement rather than a dependency of settlement.
"""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.model import MODEL_VERSION, FraudModel, primary_reason
from app.schemas import HealthResponse, ScoringRequest, ScoringResponse

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
LOGGER = logging.getLogger("obms.ai")

fraud_model = FraudModel()


@asynccontextmanager
async def lifespan(_: FastAPI):
    """Trains or loads the model before the first request arrives.

    Doing this at startup rather than lazily means the first customer
    transaction is not the one that pays the training cost.
    """
    LOGGER.info("Preparing fraud model")
    fraud_model.ensure_trained()
    LOGGER.info("Fraud model ready (%s, %d samples)",
                MODEL_VERSION, fraud_model.training_samples)
    yield
    LOGGER.info("AI service shutting down")


app = FastAPI(
    title="OBMS AI Service",
    version="1.0.0",
    description=(
        "Explainable fraud scoring for the Intelligent Online Banking Management "
        "System. Scores are produced by an Isolation Forest trained on synthetic "
        "behaviour, blended with an explainable rule layer. The model is trained "
        "on synthetic data, not on real fraud, and would require retraining on "
        "labelled outcomes before production use. This service receives no "
        "personally identifying data."
    ),
    lifespan=lifespan,
)


@app.get("/api/v1/health", response_model=HealthResponse, tags=["Operations"])
def health() -> HealthResponse:
    """Liveness and model status."""
    return HealthResponse(
        status="ok",
        model_version=MODEL_VERSION,
        model_trained=fraud_model.trained,
        training_samples=fraud_model.training_samples,
    )


@app.post("/api/v1/fraud/score", response_model=ScoringResponse, tags=["Fraud detection"])
def score(request: ScoringRequest) -> ScoringResponse:
    """Scores one proposed transaction.

    Returns a 0-100 score, a headline reason, and the per-feature
    contributions behind it. The caller decides what to do with the score;
    this service never decides to block anything.
    """
    blended, rule_points, model_points, anomaly, factors = fraud_model.score(request)

    return ScoringResponse(
        risk_score=blended,
        primary_reason=primary_reason(blended, factors),
        factors=factors,
        model_version=MODEL_VERSION,
        anomaly_score=anomaly,
        rule_score=rule_points,
        model_score=model_points,
    )


@app.post("/api/v1/fraud/retrain", tags=["Operations"])
def retrain() -> dict[str, object]:
    """Retrains the model from freshly generated synthetic data.

    Exposed so the demonstration can show the model being rebuilt. In a real
    deployment this would be an operator action behind authentication, not an
    open endpoint.
    """
    fraud_model.train()
    fraud_model.save()
    return {
        "status": "retrained",
        "model_version": MODEL_VERSION,
        "training_samples": fraud_model.training_samples,
    }
