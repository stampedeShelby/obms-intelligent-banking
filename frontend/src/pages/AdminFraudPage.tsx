import { useCallback, useEffect, useState } from 'react';
import { Loader2 } from 'lucide-react';

import { errorMessage } from '../api/client';
import { admin } from '../api/endpoints';
import type { Page, RiskFactor } from '../api/types';
import { Banner, EmptyState, PageHeader, RiskBadge, SkeletonCard } from '../components/ui';
import { dateTime, humanise, money } from '../lib/format';

interface AlertDetail {
  id: number;
  customerNumber: string;
  customerName: string;
  evaluatedRef: string | null;
  amount: number;
  riskScore: number;
  riskLevel: string;
  primaryReason: string;
  factorsJson: string | null;
  modelVersion: string;
  decision: string;
  status: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewNotes: string | null;
  markedFalsePositive: boolean;
  raisedAt: string;
}

const STATUSES = ['OPEN', 'UNDER_REVIEW', 'CONFIRMED_FRAUD', 'FALSE_POSITIVE', 'CLOSED'];

/** Fraud investigation panel for analysts and administrators. */
export default function AdminFraudPage() {
  const [filter, setFilter] = useState('OPEN');
  const [data, setData] = useState<Page<AlertDetail> | null>(null);
  const [expanded, setExpanded] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busyId, setBusyId] = useState<number | null>(null);
  const [notes, setNotes] = useState('');

  const load = useCallback(
    (status: string) =>
      admin
        .investigationQueue(status, 0, 25)
        .then(setData)
        .catch((caught) => setError(errorMessage(caught, 'Could not load the queue.'))),
    [],
  );

  useEffect(() => {
    setData(null);
    load(filter);
  }, [filter, load]);

  async function review(alertId: number, status: string) {
    setError('');
    setNotice('');
    setBusyId(alertId);
    try {
      await admin.reviewAlert(alertId, status, notes.trim() || undefined);
      await load(filter);
      setNotes('');
      setExpanded(null);
      setNotice(`Alert set to ${humanise(status)}.`);
    } catch (caught) {
      setError(errorMessage(caught, 'Could not record that review.'));
    } finally {
      setBusyId(null);
    }
  }

  function parseFactors(json: string | null): RiskFactor[] {
    if (!json) return [];
    try {
      const parsed: unknown = JSON.parse(json);
      return Array.isArray(parsed) ? (parsed as RiskFactor[]) : [];
    } catch {
      return [];
    }
  }

  return (
    <>
      <PageHeader
        title="Fraud investigation"
        subtitle="Alerts ordered by risk score. Each one carries the per-feature explanation the model produced."
      />

      <div className="mb-4 flex flex-wrap gap-1 rounded-xl bg-navy-100 p-1 dark:bg-navy-800">
        {STATUSES.map((status) => (
          <button
            key={status}
            type="button"
            onClick={() => setFilter(status)}
            className={[
              'rounded-lg px-3 py-1.5 text-xs font-semibold transition',
              filter === status
                ? 'bg-white text-navy-900 shadow-sm dark:bg-navy-950 dark:text-white'
                : 'text-navy-600 dark:text-navy-300',
            ].join(' ')}
          >
            {humanise(status)}
          </button>
        ))}
      </div>

      <div className="space-y-4">
        {error && <Banner tone="error">{error}</Banner>}
        {notice && <Banner tone="success">{notice}</Banner>}

        {!data ? (
          <SkeletonCard lines={4} />
        ) : data.content.length === 0 ? (
          <EmptyState
            title={`No ${humanise(filter).toLowerCase()} alerts`}
            message="Nothing in this queue. Try another status filter."
          />
        ) : (
          <ul className="space-y-3">
            {data.content.map((alert) => {
              const factors = parseFactors(alert.factorsJson);
              const open = expanded === alert.id;

              return (
                <li key={alert.id} className="card">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <RiskBadge level={alert.riskLevel} score={alert.riskScore} />
                        <span className="tabular font-bold text-navy-900 dark:text-white">
                          {money(alert.amount)}
                        </span>
                        <span className="badge bg-navy-100 text-navy-600 dark:bg-navy-800 dark:text-navy-300">
                          {humanise(alert.decision)}
                        </span>
                        {alert.markedFalsePositive && (
                          <span className="badge bg-navy-100 text-navy-500 dark:bg-navy-800">
                            Customer says it was them
                          </span>
                        )}
                      </div>

                      <p className="mt-2 text-sm font-medium text-navy-900 dark:text-white">
                        {alert.customerName}{' '}
                        <span className="tabular font-normal text-navy-500 dark:text-navy-400">
                          {alert.customerNumber}
                        </span>
                      </p>
                      <p className="mt-1 text-sm leading-relaxed text-navy-700 dark:text-navy-200">
                        {alert.primaryReason}
                      </p>
                      <p className="tabular mt-1 text-xs text-navy-500 dark:text-navy-400">
                        {dateTime(alert.raisedAt)}
                        {alert.evaluatedRef && ` · ${alert.evaluatedRef}`} · model{' '}
                        {alert.modelVersion}
                      </p>
                      {alert.reviewedBy && (
                        <p className="mt-1 text-xs text-navy-500 dark:text-navy-400">
                          Reviewed by {alert.reviewedBy} on {dateTime(alert.reviewedAt)}
                          {alert.reviewNotes && ` — ${alert.reviewNotes}`}
                        </p>
                      )}
                    </div>

                    <button
                      type="button"
                      className="btn-secondary shrink-0 px-3 py-1.5 text-xs"
                      onClick={() => setExpanded(open ? null : alert.id)}
                    >
                      {open ? 'Close' : 'Investigate'}
                    </button>
                  </div>

                  {open && (
                    <div className="mt-4 space-y-4 border-t border-navy-100 pt-4 dark:border-navy-800">
                      {factors.length > 0 && (
                        <div>
                          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-navy-500 dark:text-navy-400">
                            Model explanation
                          </p>
                          <ul className="space-y-2.5">
                            {factors.map((factor) => (
                              <li
                                key={factor.feature}
                                className="rounded-xl border border-navy-100 p-3 dark:border-navy-800"
                              >
                                <div className="flex items-baseline justify-between gap-3">
                                  <p className="text-sm font-medium text-navy-900 dark:text-white">
                                    {humanise(factor.feature)}
                                    {factor.value && (
                                      <span className="ml-2 font-mono text-xs font-normal text-navy-500 dark:text-navy-400">
                                        {factor.value}
                                      </span>
                                    )}
                                  </p>
                                  <span className="tabular shrink-0 text-xs font-semibold text-navy-500 dark:text-navy-400">
                                    +{factor.contribution}
                                  </span>
                                </div>
                                <p className="mt-0.5 text-sm text-navy-600 dark:text-navy-300">
                                  {factor.explanation}
                                </p>
                              </li>
                            ))}
                          </ul>
                        </div>
                      )}

                      <div>
                        <label className="label" htmlFor={`notes-${alert.id}`}>
                          Investigation notes
                        </label>
                        <input
                          id={`notes-${alert.id}`}
                          maxLength={500}
                          className="input"
                          value={notes}
                          onChange={(event) => setNotes(event.target.value)}
                          placeholder="What you found and why you reached this outcome"
                        />
                      </div>

                      <div className="flex flex-wrap gap-2">
                        <button
                          type="button"
                          className="btn bg-red-600 text-white hover:bg-red-700"
                          onClick={() => review(alert.id, 'CONFIRMED_FRAUD')}
                          disabled={busyId === alert.id}
                        >
                          {busyId === alert.id && (
                            <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
                          )}
                          Confirm fraud
                        </button>
                        <button
                          type="button"
                          className="btn bg-emerald-600 text-white hover:bg-emerald-700"
                          onClick={() => review(alert.id, 'FALSE_POSITIVE')}
                          disabled={busyId === alert.id}
                        >
                          False positive
                        </button>
                        <button
                          type="button"
                          className="btn-secondary"
                          onClick={() => review(alert.id, 'UNDER_REVIEW')}
                          disabled={busyId === alert.id}
                        >
                          Keep under review
                        </button>
                        <button
                          type="button"
                          className="btn-secondary"
                          onClick={() => review(alert.id, 'CLOSED')}
                          disabled={busyId === alert.id}
                        >
                          Close
                        </button>
                      </div>
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </>
  );
}
