"""API tests for the fraud scoring service."""

from __future__ import annotations

from fastapi.testclient import TestClient

from app.main import app

ROUTINE = {
    "amount": "2500.00",
    "customerMeanDebit": "2400.00",
    "customerStdDebit": "600.00",
    "customerMaxDebit": "8000.00",
    "historyCount": 60,
    "hourOfDay": 14,
    "isNewBeneficiary": False,
    "beneficiaryAgeHours": 2200,
    "recentDebitCount1h": 0,
    "recentDebitCount24h": 2,
    "distinctPayees7d": 3,
    "balanceRatio": "0.05",
    "category": "FOOD",
}

SUSPICIOUS = {
    "amount": "95000.00",
    "customerMeanDebit": "2400.00",
    "customerStdDebit": "600.00",
    "customerMaxDebit": "8000.00",
    "historyCount": 60,
    "hourOfDay": 3,
    "isNewBeneficiary": True,
    "beneficiaryAgeHours": 1,
    "recentDebitCount1h": 6,
    "recentDebitCount24h": 11,
    "distinctPayees7d": 9,
    "balanceRatio": "0.95",
    "category": "TRANSFER",
}


def test_health_reports_a_trained_model():
    with TestClient(app) as client:
        response = client.get("/api/v1/health")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["model_trained"] is True
    assert body["training_samples"] > 0
    assert body["model_version"] == "isolation_forest_v1"


def test_score_returns_a_low_band_for_routine_activity():
    with TestClient(app) as client:
        response = client.post("/api/v1/fraud/score", json=ROUTINE)

    assert response.status_code == 200
    body = response.json()
    assert 0 <= body["risk_score"] < 40
    assert body["primary_reason"]
    assert body["model_version"] == "isolation_forest_v1"


def test_score_returns_a_high_band_for_suspicious_activity():
    with TestClient(app) as client:
        response = client.post("/api/v1/fraud/score", json=SUSPICIOUS)

    assert response.status_code == 200
    body = response.json()
    assert body["risk_score"] >= 65
    assert len(body["factors"]) >= 4


def test_response_always_explains_itself():
    """The specification requires explainable alerts, never a bare score."""
    with TestClient(app) as client:
        body = client.post("/api/v1/fraud/score", json=SUSPICIOUS).json()

    assert body["primary_reason"]
    for factor in body["factors"]:
        assert factor["feature"]
        assert factor["explanation"]
        assert len(factor["explanation"]) > 15
        assert isinstance(factor["contribution"], int)


def test_response_separates_rule_and_model_contributions():
    """Both layers are reported so the blend need not be taken on trust."""
    with TestClient(app) as client:
        body = client.post("/api/v1/fraud/score", json=SUSPICIOUS).json()

    assert "rule_score" in body
    assert "model_score" in body
    assert "anomaly_score" in body
    assert body["rule_score"] > 0


def test_score_accepts_a_minimal_payload():
    """Defaults must cover a brand new customer with no history at all."""
    with TestClient(app) as client:
        response = client.post("/api/v1/fraud/score", json={"amount": "1000.00"})

    assert response.status_code == 200
    assert 0 <= response.json()["risk_score"] <= 100


def test_score_rejects_a_negative_amount():
    with TestClient(app) as client:
        response = client.post("/api/v1/fraud/score", json={"amount": "-500.00"})

    assert response.status_code == 422


def test_score_rejects_an_out_of_range_hour():
    with TestClient(app) as client:
        response = client.post(
            "/api/v1/fraud/score", json={"amount": "500.00", "hourOfDay": 25}
        )

    assert response.status_code == 422


def test_score_rejects_a_missing_amount():
    with TestClient(app) as client:
        response = client.post("/api/v1/fraud/score", json={"hourOfDay": 10})

    assert response.status_code == 422


def test_scoring_is_repeatable_across_requests():
    with TestClient(app) as client:
        first = client.post("/api/v1/fraud/score", json=SUSPICIOUS).json()
        second = client.post("/api/v1/fraud/score", json=SUSPICIOUS).json()

    assert first["risk_score"] == second["risk_score"]
    assert first["primary_reason"] == second["primary_reason"]


def test_retrain_endpoint_rebuilds_the_model():
    with TestClient(app) as client:
        response = client.post("/api/v1/fraud/retrain")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "retrained"
    assert body["training_samples"] > 0


def test_score_ordering_holds_across_a_risk_gradient():
    """Scores must increase monotonically as behaviour gets more unusual."""
    gradient = []
    for amount, hour, new_payee, velocity in [
        ("2500.00", 14, False, 0),
        ("9000.00", 14, False, 0),
        ("9000.00", 3, False, 0),
        ("40000.00", 3, True, 2),
        ("95000.00", 3, True, 6),
    ]:
        payload = dict(ROUTINE)
        payload.update({
            "amount": amount,
            "hourOfDay": hour,
            "isNewBeneficiary": new_payee,
            "beneficiaryAgeHours": 1 if new_payee else 2200,
            "recentDebitCount1h": velocity,
        })
        with TestClient(app) as client:
            gradient.append(client.post("/api/v1/fraud/score", json=payload).json()["risk_score"])

    assert gradient[0] < gradient[-1], f"gradient did not increase: {gradient}"
    assert gradient == sorted(gradient), f"scores must not decrease as risk rises: {gradient}"


def test_openapi_document_is_served():
    with TestClient(app) as client:
        response = client.get("/openapi.json")

    assert response.status_code == 200
    assert "/api/v1/fraud/score" in response.json()["paths"]
