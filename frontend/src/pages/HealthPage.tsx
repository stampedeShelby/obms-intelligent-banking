import { useEffect, useState } from 'react';
import { Info } from 'lucide-react';
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import { errorMessage } from '../api/client';
import { intelligence } from '../api/endpoints';
import type { FinancialHealth } from '../api/types';
import { Banner, PageHeader, SkeletonCard } from '../components/ui';
import { humanise } from '../lib/format';

/** Financial wellness score with the full indicator breakdown. */
export default function HealthPage() {
  const [data, setData] = useState<FinancialHealth | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    intelligence
      .healthScore()
      .then(setData)
      .catch((caught) => setError(errorMessage(caught, 'Could not load your health score.')));
  }, []);

  if (error) {
    return (
      <>
        <PageHeader title="Financial health" />
        <Banner tone="error">{error}</Banner>
      </>
    );
  }

  if (!data) {
    return (
      <>
        <PageHeader title="Financial health" />
        <SkeletonCard lines={5} />
      </>
    );
  }

  const bandColour =
    data.score >= 80
      ? 'from-emerald-600 to-teal-500'
      : data.score >= 60
        ? 'from-teal-600 to-teal-400'
        : data.score >= 40
          ? 'from-amber-500 to-amber-400'
          : 'from-red-600 to-orange-500';

  return (
    <>
      <PageHeader
        title="Financial health"
        subtitle={`Period ${data.period}. Formula version ${data.formulaVersion}.`}
      />

      <div className="grid gap-5 lg:grid-cols-3">
        <div className="space-y-5 lg:col-span-2">
          <div className="card">
            <div className="flex flex-wrap items-end gap-6">
              <div>
                <p className="text-xs font-semibold uppercase tracking-wide text-navy-500 dark:text-navy-400">
                  Your score
                </p>
                <div className="mt-1 flex items-end gap-2">
                  <p className="tabular text-5xl font-bold text-navy-900 dark:text-white">
                    {data.score}
                  </p>
                  <p className="pb-2 text-navy-500 dark:text-navy-400">/ 100</p>
                </div>
                <p className="mt-1 font-semibold text-teal-700 dark:text-teal-400">
                  {humanise(data.band)}
                </p>
              </div>

              <div className="min-w-48 flex-1">
                <div className="h-3 overflow-hidden rounded-full bg-navy-100 dark:bg-navy-800">
                  <div
                    className={`h-full rounded-full bg-gradient-to-r ${bandColour}`}
                    style={{ width: `${data.score}%` }}
                  />
                </div>
                <div className="mt-1.5 flex justify-between text-[10px] font-medium text-navy-400 dark:text-navy-500">
                  <span>0</span>
                  <span>40 Fair</span>
                  <span>60 Good</span>
                  <span>80 Excellent</span>
                  <span>100</span>
                </div>
              </div>
            </div>

            <p className="mt-4 text-sm leading-relaxed text-navy-700 dark:text-navy-200">
              {data.interpretation}
            </p>

            {!data.dataSufficient && (
              <div className="mt-4">
                <Banner tone="info" title="Based on limited history">
                  There is not much activity to measure yet, so treat this as indicative. It will
                  settle as more transactions accumulate.
                </Banner>
              </div>
            )}
          </div>

          <div className="card">
            <h2 className="mb-1 font-semibold text-navy-900 dark:text-white">
              How the score is built
            </h2>
            <p className="mb-4 text-sm text-navy-600 dark:text-navy-300">
              Five indicators, each worth a fixed number of points. The explanation under each one
              shows the figures it was computed from.
            </p>

            <ul className="space-y-4">
              {data.indicators.map((indicator) => {
                const share = indicator.weight > 0 ? (indicator.points / indicator.weight) * 100 : 0;
                return (
                  <li key={indicator.indicator}>
                    <div className="flex items-baseline justify-between gap-3">
                      <p className="font-medium text-navy-900 dark:text-white">{indicator.label}</p>
                      <p className="tabular shrink-0 text-sm font-semibold text-navy-700 dark:text-navy-200">
                        {indicator.points}
                        <span className="text-navy-400"> / {indicator.weight}</span>
                      </p>
                    </div>
                    <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-navy-100 dark:bg-navy-800">
                      <div
                        className={`h-full rounded-full ${
                          share >= 75 ? 'bg-teal-500' : share >= 40 ? 'bg-amber-500' : 'bg-red-500'
                        }`}
                        style={{ width: `${Math.min(share, 100)}%` }}
                      />
                    </div>
                    <p className="mt-1.5 text-sm text-navy-600 dark:text-navy-300">
                      {indicator.explanation}
                    </p>
                    <p className="tabular mt-0.5 text-xs text-navy-400 dark:text-navy-500">
                      Measured: {indicator.observed}
                      {indicator.unit}
                    </p>
                  </li>
                );
              })}
            </ul>
          </div>
        </div>

        <div className="space-y-5">
          {data.suggestions.length > 0 && (
            <div className="card">
              <h2 className="mb-1 font-semibold text-navy-900 dark:text-white">
                What would help most
              </h2>
              <p className="mb-3 text-xs text-navy-500 dark:text-navy-400">
                Ordered by how many points each would recover.
              </p>
              <ol className="space-y-3">
                {data.suggestions.map((suggestion, index) => (
                  <li
                    key={suggestion.action}
                    className="rounded-xl border border-navy-100 p-3 dark:border-navy-800"
                  >
                    <div className="flex items-baseline justify-between gap-2">
                      <p className="text-sm font-semibold text-navy-900 dark:text-white">
                        {index + 1}. {suggestion.action}
                      </p>
                      <span className="tabular shrink-0 rounded-full bg-teal-50 px-2 py-0.5 text-xs font-bold text-teal-700 dark:bg-teal-950/50 dark:text-teal-300">
                        +{suggestion.pointsAtStake}
                      </span>
                    </div>
                    <p className="mt-1 text-sm text-navy-600 dark:text-navy-300">
                      {suggestion.rationale}
                    </p>
                  </li>
                ))}
              </ol>
            </div>
          )}

          {data.history.length > 1 && (
            <div className="card">
              <h2 className="mb-3 font-semibold text-navy-900 dark:text-white">Score over time</h2>
              <div className="h-48">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={data.history} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e3eaf5" vertical={false} />
                    <XAxis dataKey="period" tick={{ fontSize: 10 }} />
                    <YAxis domain={[0, 100]} tick={{ fontSize: 10 }} width={32} />
                    <Tooltip />
                    <Line
                      type="monotone"
                      dataKey="score"
                      stroke="#04c8b1"
                      strokeWidth={2.5}
                      dot={{ r: 3 }}
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </div>
          )}

          <div className="card bg-navy-50 dark:bg-navy-950">
            <div className="flex gap-3">
              <Info className="mt-0.5 h-4 w-4 shrink-0 text-navy-500 dark:text-navy-400" aria-hidden />
              <div className="text-sm text-navy-600 dark:text-navy-300">
                <p className="font-semibold text-navy-800 dark:text-navy-100">
                  This is not a credit score
                </p>
                <p className="mt-1">
                  It is a simulated wellness measure computed only from your activity inside OBMS.
                  It is not shared with any credit bureau, has no bearing on lending, and exists to
                  show you where your own money is going.
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
