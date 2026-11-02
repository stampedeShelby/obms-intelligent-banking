import { useEffect, useState } from 'react';
import { ShieldCheck, ThumbsDown } from 'lucide-react';

import { errorMessage } from '../api/client';
import { intelligence } from '../api/endpoints';
import type { Page, RiskFactor } from '../api/types';
import { Banner, EmptyState, PageHeader, RiskBadge, SkeletonCard } from '../components/ui';
import { dateTime, humanise, money } from '../lib/format';

interface AlertRow {
  id: number;
  evaluatedRef: string | null;
  amount: number;
  riskScore: number;
  riskLevel: string;
  reason: string;
  factorsJson: string | null;
  decision: string;
  status: string;
  markedFalsePositive: boolean;
  raisedAt: string;
}

/** The customer's own fraud alerts, with the explanation behind each score. */
export default function AlertsPage() {
  const [data, setData] = useState<Page<AlertRow> | null>(null);
  const [expanded, setExpanded] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  function refresh() {
    return intelligence.alerts(0, 25).then(setData);
  }

  useEffect(() => {
    refresh().catch((caught) => setError(errorMessage(caught, 'Could not load your alerts.')));
  }, []);

  async function markFalsePositive(id: number) {
    setError('');
    try {
      await intelligence.markFalsePositive(id, 'Customer confirmed this was them.');
      await refresh();
      setNotice('Thanks. That feedback is recorded and used when the model is reviewed.');
    } catch (caught) {
      setError(errorMessage(caught, 'Could not record that feedback.'));
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

  if (!data) {
    return (
      <>
        <PageHeader title="Fraud alerts" />
        <SkeletonCard lines={4} />
      </>
    );
  }

  const notable = data.content.filter((alert) => alert.riskLevel !== 'LOW');

  return (
    <>
      <PageHeader
        title="Fraud alerts"
        subtitle="Every transfer is scored before it settles. These are the assessments on your account."
      />

      <div className="space-y-4">
        {error && <Banner tone="error">{error}</Banner>}
        {notice && <Banner tone="success">{notice}</Banner>}

        {notable.length === 0 && (
          <Banner tone="success" title="Nothing suspicious">
            No alerts above the low-risk band on your account. Scoring runs on every transfer, so
            anything unusual would appear here with the reason behind it.
          </Banner>
        )}

        {data.content.length === 0 ? (
          <EmptyState
            title="No assessments yet"
            message="Once you make a transfer it will be scored, and the assessment will appear here."
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
                            Reported as yours
                          </span>
                        )}
                      </div>
                      <p className="mt-2 text-sm leading-relaxed text-navy-700 dark:text-navy-200">
                        {alert.reason}
                      </p>
                      <p className="tabular mt-1 text-xs text-navy-500 dark:text-navy-400">
                        {dateTime(alert.raisedAt)}
                        {alert.evaluatedRef && ` · ${alert.evaluatedRef}`} ·{' '}
                        {humanise(alert.status)}
                      </p>
                    </div>

                    <div className="flex shrink-0 gap-2">
                      {factors.length > 0 && (
                        <button
                          type="button"
                          className="btn-secondary px-3 py-1.5 text-xs"
                          onClick={() => setExpanded(open ? null : alert.id)}
                        >
                          {open ? 'Hide detail' : 'Why?'}
                        </button>
                      )}
                      {alert.riskLevel !== 'LOW' && !alert.markedFalsePositive && (
                        <button
                          type="button"
                          className="btn-secondary px-3 py-1.5 text-xs"
                          onClick={() => markFalsePositive(alert.id)}
                        >
                          <ThumbsDown className="h-3.5 w-3.5" aria-hidden />
                          This was me
                        </button>
                      )}
                    </div>
                  </div>

                  {open && factors.length > 0 && (
                    <div className="mt-4 rounded-xl border border-navy-100 p-3 dark:border-navy-800">
                      <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-navy-500 dark:text-navy-400">
                        What contributed to this score
                      </p>
                      <ul className="space-y-2.5">
                        {factors.map((factor) => (
                          <li key={factor.feature}>
                            <div className="flex items-baseline justify-between gap-3">
                              <p className="text-sm font-medium text-navy-900 dark:text-white">
                                {humanise(factor.feature)}
                                {factor.value && (
                                  <span className="ml-2 font-normal text-navy-500 dark:text-navy-400">
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
                </li>
              );
            })}
          </ul>
        )}

        <div className="card bg-navy-50 dark:bg-navy-950">
          <div className="flex gap-3">
            <ShieldCheck
              className="mt-0.5 h-4 w-4 shrink-0 text-navy-500 dark:text-navy-400"
              aria-hidden
            />
            <div className="text-sm text-navy-600 dark:text-navy-300">
              <p className="font-semibold text-navy-800 dark:text-navy-100">
                About these assessments
              </p>
              <p className="mt-1">
                Scores come from an anomaly detection model combined with rules that each carry a
                stated reason. The model is trained on synthetic behaviour rather than real fraud,
                so it detects what is unusual for you rather than what is definitely fraudulent.
                When you tell us an alert was you, that feedback is recorded for review.
              </p>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
