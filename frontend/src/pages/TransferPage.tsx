import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2, Loader2, ShieldAlert, ShieldCheck } from 'lucide-react';

import { errorMessage } from '../api/client';
import { banking, beneficiaries as beneficiariesApi, transfers } from '../api/endpoints';
import type { AccountSummary, BeneficiaryView, TransferResult } from '../api/types';
import { Banner, PageHeader, RiskBadge, SimulationNotice, Spinner } from '../components/ui';
import { humanise, maskAccount, money } from '../lib/format';

type Destination = { kind: 'beneficiary'; id: number } | { kind: 'own'; id: number };

/**
 * Fund transfer with risk-based verification.
 *
 * The screen follows whatever the API decides. It does not predict the
 * outcome: the server scores the transfer, and this page renders one of three
 * results. The risk factors are shown whenever the customer is asked for
 * something extra, so a challenge is never unexplained.
 */
export default function TransferPage() {
  const [accounts, setAccounts] = useState<AccountSummary[]>([]);
  const [payees, setPayees] = useState<BeneficiaryView[]>([]);
  const [loading, setLoading] = useState(true);

  const [sourceId, setSourceId] = useState<number | null>(null);
  const [destination, setDestination] = useState<Destination | null>(null);
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');

  const [result, setResult] = useState<TransferResult | null>(null);
  const [otp, setOtp] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    Promise.all([banking.accounts(), beneficiariesApi.list()])
      .then(([accountList, payeeList]) => {
        if (cancelled) return;
        setAccounts(accountList);
        setPayees(payeeList.filter((payee) => payee.payable));
        setSourceId(accountList[0]?.id ?? null);
      })
      .catch((caught) => {
        if (!cancelled) setError(errorMessage(caught, 'Could not load your accounts.'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const source = useMemo(
    () => accounts.find((account) => account.id === sourceId) ?? null,
    [accounts, sourceId],
  );

  const ownDestinations = useMemo(
    () => accounts.filter((account) => account.id !== sourceId),
    [accounts, sourceId],
  );

  const numericAmount = Number(amount);
  const exceedsBalance = Boolean(source) && numericAmount > (source?.availableBalance ?? 0);
  const canSubmit =
    sourceId !== null &&
    destination !== null &&
    amount !== '' &&
    numericAmount > 0 &&
    !exceedsBalance &&
    !busy;

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (sourceId === null || destination === null) return;

    setError('');
    setBusy(true);
    try {
      const payload = {
        sourceAccountId: sourceId,
        beneficiaryId: destination.kind === 'beneficiary' ? destination.id : null,
        destinationAccountId: destination.kind === 'own' ? destination.id : null,
        amount: Number(amount).toFixed(2),
        description: description.trim() || undefined,
      };
      setResult(await transfers.initiate(payload));
    } catch (caught) {
      setError(errorMessage(caught, 'The transfer could not be completed.'));
    } finally {
      setBusy(false);
    }
  }

  async function confirm(event: React.FormEvent) {
    event.preventDefault();
    if (!result?.pendingRef) return;

    setError('');
    setBusy(true);
    try {
      setResult(await transfers.confirm(result.pendingRef, otp.trim()));
      setOtp('');
    } catch (caught) {
      setError(errorMessage(caught, 'That code was not accepted.'));
    } finally {
      setBusy(false);
    }
  }

  function startOver() {
    setResult(null);
    setAmount('');
    setDescription('');
    setDestination(null);
    setOtp('');
    setError('');
    banking.accounts().then(setAccounts).catch(() => undefined);
  }

  if (loading) {
    return (
      <>
        <PageHeader title="Transfer money" />
        <Spinner label="Loading your accounts" />
      </>
    );
  }

  // ---- completed -------------------------------------------------------
  if (result?.outcome === 'COMPLETED') {
    return (
      <>
        <PageHeader title="Transfer complete" />
        <div className="mx-auto max-w-lg space-y-4">
          <div className="card text-center">
            <div className="mx-auto mb-3 inline-flex rounded-full bg-emerald-50 p-3 dark:bg-emerald-950/40">
              <CheckCircle2 className="h-7 w-7 text-emerald-600 dark:text-emerald-400" aria-hidden />
            </div>
            <p className="tabular text-3xl font-bold text-navy-900 dark:text-white">
              {money(result.amount)}
            </p>
            <p className="mt-1 text-sm text-navy-600 dark:text-navy-300">{result.message}</p>

            <dl className="mt-5 space-y-2.5 rounded-xl bg-navy-50 p-4 text-left text-sm dark:bg-navy-950">
              <Row label="Reference" value={<span className="tabular">{result.reference}</span>} />
              {result.balanceAfter !== null && (
                <Row
                  label="Balance after"
                  value={<span className="tabular">{money(result.balanceAfter)}</span>}
                />
              )}
              {result.riskLevel && (
                <Row label="Risk assessment" value={<RiskBadge level={result.riskLevel} score={result.riskScore} />} />
              )}
            </dl>

            <div className="mt-5 flex gap-2">
              <button type="button" className="btn-secondary flex-1" onClick={startOver}>
                Make another transfer
              </button>
              <Link to="/app" className="btn-primary flex-1">
                Back to dashboard
              </Link>
            </div>
          </div>
          <SimulationNotice />
        </div>
      </>
    );
  }

  // ---- held for review -------------------------------------------------
  if (result?.outcome === 'HELD_FOR_REVIEW') {
    return (
      <>
        <PageHeader title="Transfer held for review" />
        <div className="mx-auto max-w-lg space-y-4">
          <Banner tone="warning" title="This transfer is being reviewed">
            {result.message} No money has moved. Our team will look at it and you will be notified.
          </Banner>
          <RiskFactorList result={result} />
          <button type="button" className="btn-secondary w-full" onClick={startOver}>
            Back to transfers
          </button>
        </div>
      </>
    );
  }

  // ---- awaiting OTP ----------------------------------------------------
  if (result?.outcome === 'VERIFICATION_REQUIRED') {
    return (
      <>
        <PageHeader title="Verify this transfer" />
        <div className="mx-auto max-w-lg space-y-4">
          <div className="card">
            <div className="mb-3 inline-flex rounded-xl bg-navy-900 p-2.5 dark:bg-teal-500">
              <ShieldCheck className="h-5 w-5 text-white dark:text-navy-950" aria-hidden />
            </div>
            <p className="tabular text-2xl font-bold text-navy-900 dark:text-white">
              {money(result.amount)}
            </p>
            <p className="mt-1.5 text-sm text-navy-600 dark:text-navy-300">{result.message}</p>
            <p className="mt-3 text-sm text-navy-600 dark:text-navy-300">
              A one-time code has been sent to you. No money has moved yet.
            </p>

            {error && (
              <div className="mt-4">
                <Banner tone="error">{error}</Banner>
              </div>
            )}

            <form onSubmit={confirm} className="mt-5 space-y-4">
              <div>
                <label className="label" htmlFor="otp">
                  Verification code
                </label>
                <input
                  id="otp"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  maxLength={8}
                  required
                  className="input tabular text-center text-2xl tracking-[0.4em]"
                  value={otp}
                  onChange={(event) => setOtp(event.target.value.replace(/\D/g, ''))}
                  placeholder="______"
                />
              </div>
              <button type="submit" className="btn-primary w-full" disabled={busy || otp.length < 4}>
                {busy && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
                Confirm transfer
              </button>
            </form>

            <button type="button" className="btn-ghost mt-3 w-full text-sm" onClick={startOver}>
              Cancel this transfer
            </button>
          </div>

          <RiskFactorList result={result} />
          <SimulationNotice />
        </div>
      </>
    );
  }

  // ---- the form --------------------------------------------------------
  return (
    <>
      <PageHeader
        title="Transfer money"
        subtitle="To one of your own accounts, or to a verified payee."
      />

      <div className="mx-auto max-w-lg space-y-4">
        {error && <Banner tone="error">{error}</Banner>}

        {payees.length === 0 && (
          <Banner tone="info" title="No verified payees yet">
            You can transfer between your own accounts now.{' '}
            <Link to="/app/beneficiaries" className="font-semibold underline">
              Add and verify a payee
            </Link>{' '}
            to send money to someone else.
          </Banner>
        )}

        <form onSubmit={submit} className="card space-y-4">
          <div>
            <label className="label" htmlFor="source">
              From
            </label>
            <select
              id="source"
              className="input"
              value={sourceId ?? ''}
              onChange={(event) => {
                setSourceId(Number(event.target.value));
                setDestination(null);
              }}
            >
              {accounts.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.nickname ?? humanise(account.accountType)} ·{' '}
                  {maskAccount(account.accountNumber)} · {money(account.availableBalance)} available
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="label" htmlFor="destination">
              To
            </label>
            <select
              id="destination"
              className="input"
              value={destination ? `${destination.kind}:${destination.id}` : ''}
              onChange={(event) => {
                const [kind, id] = event.target.value.split(':');
                if (!kind || !id) {
                  setDestination(null);
                  return;
                }
                setDestination({ kind: kind as 'beneficiary' | 'own', id: Number(id) });
              }}
            >
              <option value="">Select a destination</option>
              {ownDestinations.length > 0 && (
                <optgroup label="Your accounts">
                  {ownDestinations.map((account) => (
                    <option key={account.id} value={`own:${account.id}`}>
                      {account.nickname ?? humanise(account.accountType)} ·{' '}
                      {maskAccount(account.accountNumber)}
                    </option>
                  ))}
                </optgroup>
              )}
              {payees.length > 0 && (
                <optgroup label="Verified payees">
                  {payees.map((payee) => (
                    <option key={payee.id} value={`beneficiary:${payee.id}`}>
                      {payee.nickname ?? payee.beneficiaryName} · {payee.maskedAccountNumber}
                    </option>
                  ))}
                </optgroup>
              )}
            </select>
          </div>

          <div>
            <label className="label" htmlFor="amount">
              Amount
            </label>
            <input
              id="amount"
              inputMode="decimal"
              required
              className="input tabular text-lg"
              value={amount}
              onChange={(event) => setAmount(event.target.value.replace(/[^\d.]/g, ''))}
              placeholder="0.00"
            />
            {exceedsBalance && (
              <p className="mt-1.5 text-xs font-medium text-red-600 dark:text-red-400">
                That is more than the {money(source?.availableBalance)} available on this account.
              </p>
            )}
            {source && !exceedsBalance && (
              <p className="tabular mt-1.5 text-xs text-navy-500 dark:text-navy-400">
                Daily limit {money(source.dailyTransferLimit)}. Transfers of{' '}
                {money(25000)} or more need a one-time code.
              </p>
            )}
          </div>

          <div>
            <label className="label" htmlFor="description">
              Description (optional)
            </label>
            <input
              id="description"
              maxLength={255}
              className="input"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="Shows on your statement"
            />
          </div>

          <button type="submit" className="btn-primary w-full" disabled={!canSubmit}>
            {busy && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
            Continue
          </button>
        </form>

        <SimulationNotice />
      </div>
    </>
  );
}

function RiskFactorList({ result }: { result: TransferResult }) {
  if (result.riskFactors.length === 0) return null;

  return (
    <div className="card">
      <div className="mb-3 flex items-center gap-2">
        <ShieldAlert className="h-4 w-4 text-navy-500 dark:text-navy-400" aria-hidden />
        <h2 className="font-semibold text-navy-900 dark:text-white">Why this was flagged</h2>
        <RiskBadge level={result.riskLevel} score={result.riskScore} />
      </div>
      <ul className="space-y-2.5">
        {result.riskFactors.map((factor) => (
          <li
            key={factor.feature}
            className="rounded-xl border border-navy-100 p-3 dark:border-navy-800"
          >
            <div className="flex items-baseline justify-between gap-3">
              <p className="text-sm font-medium text-navy-900 dark:text-white">
                {humanise(factor.feature)}
              </p>
              <span className="tabular text-xs font-semibold text-navy-500 dark:text-navy-400">
                +{factor.contribution}
              </span>
            </div>
            <p className="mt-1 text-sm text-navy-600 dark:text-navy-300">{factor.explanation}</p>
          </li>
        ))}
      </ul>
    </div>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-4">
      <dt className="text-navy-500 dark:text-navy-400">{label}</dt>
      <dd className="font-semibold text-navy-900 dark:text-white">{value}</dd>
    </div>
  );
}
