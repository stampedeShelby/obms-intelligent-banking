import { useEffect, useState } from 'react';
import { Lightbulb, TrendingDown, TrendingUp, TriangleAlert } from 'lucide-react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { errorMessage } from '../api/client';
import { intelligence } from '../api/endpoints';
import type { SpendingAnalytics } from '../api/types';
import { Banner, EmptyState, PageHeader, SkeletonGrid, StatTile } from '../components/ui';
import { humanise, money, percent } from '../lib/format';

const COLOURS = ['#0e234c', '#04c8b1', '#466fa9', '#e8a33d', '#688dc1', '#058076', '#9bb4d8', '#c7d5ea'];

/** Spending analytics with category breakdown, trend and generated insights. */
export default function AnalyticsPage() {
  const [data, setData] = useState<SpendingAnalytics | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    intelligence
      .analytics()
      .then(setData)
      .catch((caught) => setError(errorMessage(caught, 'Could not load your analytics.')));
  }, []);

  if (error) {
    return (
      <>
        <PageHeader title="Spending analytics" />
        <Banner tone="error">{error}</Banner>
      </>
    );
  }

  if (!data) {
    return (
      <>
        <PageHeader title="Spending analytics" />
        <SkeletonGrid count={3} />
      </>
    );
  }

  const net = data.totalIncome - data.totalSpend;
  const trendUp = data.monthOverMonth > 0;

  return (
    <>
      <PageHeader
        title="Spending analytics"
        subtitle={`Period ${data.period}. Transfers between your own accounts are excluded from both income and spending.`}
      />

      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatTile label="Spent" value={<span className="tabular">{money(data.totalSpend)}</span>} tone="negative" />
        <StatTile label="Received" value={<span className="tabular">{money(data.totalIncome)}</span>} tone="positive" />
        <StatTile
          label="Net"
          value={<span className="tabular">{money(net)}</span>}
          tone={net >= 0 ? 'positive' : 'negative'}
        />
        <StatTile
          label="Vs last month"
          value={
            <span className="tabular inline-flex items-center gap-1.5">
              {trendUp ? (
                <TrendingUp className="h-5 w-5 text-red-500" aria-hidden />
              ) : (
                <TrendingDown className="h-5 w-5 text-emerald-500" aria-hidden />
              )}
              {percent(Math.abs(data.monthOverMonth))}
            </span>
          }
          hint={trendUp ? 'Spending is up' : 'Spending is down'}
        />
      </section>

      {data.insights.length > 0 && (
        <section className="mt-5 space-y-2">
          {data.insights.map((insight) => (
            <Banner
              key={insight.title}
              tone={
                insight.severity === 'WARNING'
                  ? 'warning'
                  : insight.severity === 'SUGGESTION'
                    ? 'info'
                    : 'info'
              }
              title={insight.title}
            >
              {insight.detail}
            </Banner>
          ))}
        </section>
      )}

      <div className="mt-5 grid gap-5 lg:grid-cols-2">
        <div className="card">
          <h2 className="mb-3 font-semibold text-navy-900 dark:text-white">By category</h2>
          {data.categories.length === 0 ? (
            <p className="py-10 text-center text-sm text-navy-500 dark:text-navy-400">
              No spending recorded this month.
            </p>
          ) : (
            <>
              <div className="h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={data.categories.map((slice) => ({
                        name: humanise(slice.category),
                        value: slice.total,
                      }))}
                      dataKey="value"
                      nameKey="name"
                      innerRadius={52}
                      outerRadius={88}
                      paddingAngle={2}
                    >
                      {data.categories.map((_, index) => (
                        <Cell key={index} fill={COLOURS[index % COLOURS.length]} />
                      ))}
                    </Pie>
                    <Tooltip formatter={(value: number) => money(value)} />
                    <Legend verticalAlign="bottom" height={30} iconSize={8} />
                  </PieChart>
                </ResponsiveContainer>
              </div>

              <table className="mt-3 w-full text-sm">
                <thead>
                  <tr className="border-b border-navy-100 text-left text-xs uppercase tracking-wide text-navy-500 dark:border-navy-800 dark:text-navy-400">
                    <th className="pb-2 font-semibold">Category</th>
                    <th className="pb-2 text-right font-semibold">Spent</th>
                    <th className="pb-2 text-right font-semibold">Share</th>
                    <th className="pb-2 text-right font-semibold">Count</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-navy-100 dark:divide-navy-800">
                  {data.categories.map((slice, index) => (
                    <tr key={slice.category}>
                      <td className="py-2">
                        <span className="flex items-center gap-2 text-navy-800 dark:text-navy-100">
                          <span
                            className="h-2.5 w-2.5 rounded-full"
                            style={{ background: COLOURS[index % COLOURS.length] }}
                          />
                          {humanise(slice.category)}
                        </span>
                      </td>
                      <td className="tabular py-2 text-right font-medium text-navy-900 dark:text-white">
                        {money(slice.total)}
                      </td>
                      <td className="tabular py-2 text-right text-navy-500 dark:text-navy-400">
                        {percent(slice.share)}
                      </td>
                      <td className="tabular py-2 text-right text-navy-500 dark:text-navy-400">
                        {slice.transactions}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}
        </div>

        <div className="space-y-5">
          <div className="card">
            <h2 className="mb-3 font-semibold text-navy-900 dark:text-white">
              Income and spending by month
            </h2>
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={data.trend} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e3eaf5" vertical={false} />
                  <XAxis dataKey="period" tick={{ fontSize: 10 }} />
                  <YAxis tick={{ fontSize: 10 }} width={60} />
                  <Tooltip formatter={(value: number) => money(value)} />
                  <Legend iconSize={8} />
                  <Bar name="Received" dataKey="income" fill="#04c8b1" radius={[4, 4, 0, 0]} />
                  <Bar name="Spent" dataKey="expense" fill="#0e234c" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>

          <div className="card">
            <h2 className="mb-3 font-semibold text-navy-900 dark:text-white">Net position</h2>
            <div className="h-48">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={data.trend} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e3eaf5" vertical={false} />
                  <XAxis dataKey="period" tick={{ fontSize: 10 }} />
                  <YAxis tick={{ fontSize: 10 }} width={60} />
                  <Tooltip formatter={(value: number) => money(value)} />
                  <Line type="monotone" dataKey="net" stroke="#466fa9" strokeWidth={2.5} dot={false} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>
      </div>

      {data.recurring.length > 0 && (
        <div className="card mt-5">
          <div className="mb-3 flex items-center gap-2">
            <Lightbulb className="h-4 w-4 text-navy-500 dark:text-navy-400" aria-hidden />
            <h2 className="font-semibold text-navy-900 dark:text-white">Recurring charges</h2>
          </div>
          <p className="mb-3 text-sm text-navy-600 dark:text-navy-300">
            These appeared in at least three separate months, which is what distinguishes a
            subscription from a few purchases in one week.
          </p>
          <ul className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {data.recurring.map((item) => (
              <li
                key={item.label}
                className="flex items-center justify-between gap-3 rounded-xl border border-navy-100 p-3 text-sm dark:border-navy-800"
              >
                <div className="min-w-0">
                  <p className="truncate font-medium text-navy-900 dark:text-white">{item.label}</p>
                  <p className="text-xs text-navy-500 dark:text-navy-400">
                    {humanise(item.category)} · {item.occurrences} months
                  </p>
                </div>
                <span className="tabular shrink-0 font-semibold text-navy-900 dark:text-white">
                  {money(item.typicalAmount)}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {data.uncategorisedCount > 0 && (
        <div className="mt-5">
          <Banner tone="info" title={`${data.uncategorisedCount} transactions are uncategorised`}>
            <span className="inline-flex items-center gap-1.5">
              <TriangleAlert className="h-3.5 w-3.5" aria-hidden />
              Assign categories on the Accounts page to complete this breakdown. Your corrections
              are kept and never overwritten.
            </span>
          </Banner>
        </div>
      )}

      {data.categories.length === 0 && data.totalIncome === 0 && (
        <div className="mt-5">
          <EmptyState
            title="No activity this month"
            message="Once transactions are recorded, this page breaks them down by category with a twelve-month trend."
          />
        </div>
      )}
    </>
  );
}
