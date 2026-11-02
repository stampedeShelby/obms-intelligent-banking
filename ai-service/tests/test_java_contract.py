"""Guards the HTTP contract between the Spring Boot backend and this service.

Why this test exists: the Java client sends a record serialised by Jackson,
which produces camelCase keys, while the Pydantic models here are written in
snake_case and rely on explicit `alias` declarations to accept them. Nothing
else in either test suite notices if those drift apart - the backend's own
tests run with `obms.fraud.enabled: false`, so they exercise the degraded
fallback and never serialise a real request. A renamed field on either side
would therefore ship silently, every score request would fail validation, and
the backend would quietly fall back to its conservative rule for every
transaction while appearing healthy.

So this test reads the field names straight out of the Java source and asserts
the API accepts exactly those.
"""
from __future__ import annotations

import re
from decimal import Decimal
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.main import app

JAVA_CLIENT = (
    Path(__file__).resolve().parents[2]
    / "backend/src/main/java/com/obms/service/fraud/FraudScoringClient.java"
)

ROUTINE = {
    "amount": "2500.00",
    "customerMeanDebit": "2400.00",
    "customerStdDebit": "600.00",
    "customerMaxDebit": "8000.00",
    "historyCount": 80,
    "hourOfDay": 14,
    "isNewBeneficiary": False,
    "beneficiaryAgeHours": 2400,
    "recentDebitCount1h": 0,
    "recentDebitCount24h": 2,
    "distinctPayees7d": 3,
    "balanceRatio": "0.0200",
    "category": "FOOD",
}

ANOMALOUS = {
    **ROUTINE,
    "amount": "95000.00",
    "hourOfDay": 3,
    "isNewBeneficiary": True,
    "beneficiaryAgeHours": 0,
    "recentDebitCount1h": 5,
    "recentDebitCount24h": 9,
    "distinctPayees7d": 7,
    "balanceRatio": "0.9500",
    "category": "TRANSFER",
}


@pytest.fixture(scope="module")
def client() -> TestClient:
    with TestClient(app) as test_client:
        yield test_client


def java_request_fields() -> list[str]:
    """Extract the component names of the Java ScoringRequest record."""
    source = JAVA_CLIENT.read_text(encoding="utf-8")
    start = source.index("public record ScoringRequest(")
    block = source[start : source.index(")", start)]
    return re.findall(r"(?:BigDecimal|long|int|boolean|String)\s+(\w+)", block)


@pytest.mark.skipif(not JAVA_CLIENT.exists(), reason="backend sources not present")
def test_payload_matches_the_java_record_exactly() -> None:
    """The payload below must stay in step with the Java record it imitates."""
    assert sorted(java_request_fields()) == sorted(ROUTINE.keys())


@pytest.mark.skipif(not JAVA_CLIENT.exists(), reason="backend sources not present")
def test_every_java_field_is_accepted_by_the_api(client: TestClient) -> None:
    """Posting exactly what Java sends must validate, not 422."""
    response = client.post("/api/v1/fraud/score", json=ROUTINE)
    assert response.status_code == 200, response.text


def test_routine_transaction_scores_low(client: TestClient) -> None:
    body = client.post("/api/v1/fraud/score", json=ROUTINE).json()
    assert body["risk_score"] < 40, body
    assert body["model_version"]


def test_anomalous_transaction_scores_higher_with_reasons(client: TestClient) -> None:
    routine = client.post("/api/v1/fraud/score", json=ROUTINE).json()
    anomalous = client.post("/api/v1/fraud/score", json=ANOMALOUS).json()

    assert anomalous["risk_score"] > routine["risk_score"], (routine, anomalous)
    # An unexplained score is not acceptable output from this service: the
    # banking UI renders these factors instead of a bare number.
    assert anomalous["factors"], anomalous
    assert anomalous["primary_reason"]
    for factor in anomalous["factors"]:
        assert factor["feature"]
        assert factor["explanation"]


def test_score_is_bounded(client: TestClient) -> None:
    """The Java side maps the score to risk bands, so it must stay in 0-100."""
    extreme = {**ANOMALOUS, "amount": "99999999.00", "balanceRatio": "1.0000"}
    body = client.post("/api/v1/fraud/score", json=extreme).json()
    assert 0 <= body["risk_score"] <= 100, body


def test_missing_optional_fields_fall_back_to_defaults(client: TestClient) -> None:
    """A new customer with no history must still score, not error."""
    response = client.post("/api/v1/fraud/score", json={"amount": "500.00"})
    assert response.status_code == 200, response.text
    assert Decimal(str(response.json()["risk_score"])) >= 0
