"""Shared fixtures for the AI service tests."""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

# Make the application package importable when pytest is run from this folder.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.model import FraudModel  # noqa: E402
from app.schemas import ScoringRequest  # noqa: E402


@pytest.fixture(scope="session")
def model() -> FraudModel:
    """A trained model, shared across the session to avoid refitting."""
    fitted = FraudModel()
    fitted.train(samples=1500)
    return fitted


@pytest.fixture
def routine_request() -> ScoringRequest:
    """A transaction that looks entirely ordinary for this customer.

    Amount close to their mean, daytime, long-established payee, low velocity,
    small share of the balance.
    """
    return ScoringRequest(
        amount="2500.00",
        customerMeanDebit="2400.00",
        customerStdDebit="600.00",
        customerMaxDebit="8000.00",
        historyCount=60,
        hourOfDay=14,
        isNewBeneficiary=False,
        beneficiaryAgeHours=2200,
        recentDebitCount1h=0,
        recentDebitCount24h=2,
        distinctPayees7d=3,
        balanceRatio="0.05",
        category="FOOD",
    )


@pytest.fixture
def suspicious_request() -> ScoringRequest:
    """A transaction that deviates on several axes at once.

    Far above the customer's usual amount, at 03:00, to a payee verified an
    hour ago, after a burst of other transactions, draining most of the
    balance. This is the demonstration's high-risk scenario.
    """
    return ScoringRequest(
        amount="95000.00",
        customerMeanDebit="2400.00",
        customerStdDebit="600.00",
        customerMaxDebit="8000.00",
        historyCount=60,
        hourOfDay=3,
        isNewBeneficiary=True,
        beneficiaryAgeHours=1,
        recentDebitCount1h=6,
        recentDebitCount24h=11,
        distinctPayees7d=9,
        balanceRatio="0.95",
        category="TRANSFER",
    )
