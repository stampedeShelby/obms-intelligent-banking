"""Request and response models for the fraud scoring API.

Field names use the snake_case wire format the Java backend sends. Nothing in
these models carries personally identifying data: no names, no account numbers,
no email addresses. The model needs behaviour, not identity, and keeping PII out
of this service means a compromise here exposes no customer records.
"""

from __future__ import annotations

from decimal import Decimal

from pydantic import BaseModel, ConfigDict, Field


class ScoringRequest(BaseModel):
    """Behavioural features for one proposed transaction.

    The backend assembles these from the customer's own ledger. Every field is
    a property of the customer's history or of the transaction itself.
    """

    model_config = ConfigDict(populate_by_name=True)

    amount: Decimal = Field(..., ge=0, description="Proposed transaction amount")
    customer_mean_debit: Decimal = Field(
        default=Decimal(0), ge=0, alias="customerMeanDebit",
        description="Mean of the customer's historical debits",
    )
    customer_std_debit: Decimal = Field(
        default=Decimal(0), ge=0, alias="customerStdDebit",
        description="Standard deviation of those debits",
    )
    customer_max_debit: Decimal = Field(
        default=Decimal(0), ge=0, alias="customerMaxDebit",
        description="Largest historical debit",
    )
    history_count: int = Field(
        default=0, ge=0, alias="historyCount",
        description="How many historical debits informed the statistics",
    )
    hour_of_day: int = Field(
        default=12, ge=0, le=23, alias="hourOfDay",
        description="Local hour of the attempt, 0-23",
    )
    is_new_beneficiary: bool = Field(
        default=False, alias="isNewBeneficiary",
        description="True when the payee was verified within the last day",
    )
    beneficiary_age_hours: int = Field(
        default=0, ge=0, alias="beneficiaryAgeHours",
        description="Hours since the payee was verified",
    )
    recent_debit_count_1h: int = Field(
        default=0, ge=0, alias="recentDebitCount1h",
        description="Debits in the last hour",
    )
    recent_debit_count_24h: int = Field(
        default=0, ge=0, alias="recentDebitCount24h",
        description="Debits in the last day",
    )
    distinct_payees_7d: int = Field(
        default=0, ge=0, alias="distinctPayees7d",
        description="Distinct payees paid in the last week",
    )
    balance_ratio: Decimal = Field(
        default=Decimal(0), ge=0, alias="balanceRatio",
        description="Amount as a fraction of available balance",
    )
    category: str = Field(default="OTHER", description="Spend category")


class RiskFactor(BaseModel):
    """One explainable contribution to the score.

    ``contribution`` is in score points, so the factors a customer or analyst
    sees add up to something close to the headline number rather than being
    unitless weights they cannot interpret.
    """

    feature: str
    value: str
    contribution: int
    explanation: str


class ScoringResponse(BaseModel):
    """The model's assessment.

    The banking backend owns the decision; this service returns only a score
    and the reasoning behind it.
    """

    risk_score: int = Field(..., ge=0, le=100)
    primary_reason: str
    factors: list[RiskFactor]
    model_version: str
    anomaly_score: float = Field(
        ..., description="Raw Isolation Forest decision function output, for debugging"
    )
    rule_score: int = Field(
        ..., description="Score contribution from the explainable rule layer"
    )
    model_score: int = Field(
        ..., description="Score contribution from the anomaly model"
    )


class HealthResponse(BaseModel):
    """Service liveness and model status."""

    status: str
    model_version: str
    model_trained: bool
    training_samples: int
