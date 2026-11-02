import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowDownLeft,
  ArrowUpRight,
  FileText,
  Receipt,
  Send,
  ShieldAlert,
  TriangleAlert,
} from 'lucide-react';
import {
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
} from 'recharts';

import { errorMessage } from '../api/client';
import { banking } from '../api/endpoints';
import type { Dashboard } from '../api/types';
import { Banner, EmptyState, PageHeader, RiskBadge, SkeletonGrid, StatTile } from '../components/ui';
import { date, humanise, maskAccount, money, percent } from '../lib/format';

const CATEGORY_COLOURS = [
  '#0e234c', '#04c8b1', '#466fa9', '#e8a33d',
  '#688dc1', '#058076', '#9bb4d8', '#c7d5ea',
];

/** The customer dashboard. Every figure comes from the API, none is a placeholder. */
export default function DashboardPage() {
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    banking
      .dashboard()
      .then((payload) => {
        if (!cancelled) setData(payload);
      })
      .catch((caught) => {
        if (!cancelled) setError(errorMessage(caught, 'Could not load the dashboard.'));
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (error) {
    return (
      <>
        <PageHeader title="Dashboard" />
        <Banner tone="error" title="Could not load your dashboard">
          {error}
        </Banner>
      </>
    );
  }

  if (!data) {
    return (
      <>
        <PageHeader title="Dashboard" subtitle="Loading your accounts" />
        <SkeletonGrid count={4} />
      </>
    );
  }

  const net = data.monthIncome - data.monthExpense;
  const kycPending = data.kycStatus !== 'VERIFIED';

  return (
    <>
      <PageHeader
        title={`Good to see you, ${data.displayName.split(' ')[0]}`}
        subtitle="Your accounts, this month's activity and anything that needs attention."
        actions={
          <>
            <Link to="/app/transfer" className="btn-primary">
              <Send className="h-4 w-4" aria-hidden />
              Transfer
            </Link>
            <Link to="/app/bills" className="btn-secondary">
              <Receipt className="h-4 w-4" aria-hidden />
              Pay a bill
            </Link>
          </>
        }
      />

      {kycPending && (
        <div className="mb-5">
          <Banner tone="warning" title="KYC verification is not complete">
            Transfers stay locked until your documents are verified.{' '}
            <Link to="/app/profile" className="font-semibold underline">
              Submit them from your profile
            </Link>
            .
          </Banner>
        </div>
      )}

      {data.activeAlerts.length > 0 && (
        <div className="mb-5 space-y-2">
          {data.activeAlerts.map((alert) => (
            <Banner key={alert.id} tone="error" title={`${alert.riskLevel} risk alert`}>
              <span className="tabular">{money(alert.amount)}</span> — {alert.reason}{' '}
              <Link to="/app/alerts" className="font-semibold underline">
                Review
              </Link>
            </Banner>
          ))}
        </div>
      )}

      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatTile
          label="Total balance"
          value={<span className="tabular">{money(data.totalBalance)}</span>}
          hint={`Across ${data.accounts.length} account${data.accounts.length === 1 ? '' : 's'}`}
        />
        <StatTile
          label="Received this month"
          value={<span className="tabular">{money(data.monthIncome)}</span>}
          tone="positive"
        />
        <StatTile
          label="Spent this month"
          value={<span className="tabular">{money(data.monthExpense)}</span>}
          tone="negative"
        />
        <StatTile
          label="Net this month"
          value={<span className="tabular">{money(net)}</span>}
          tone={net >= 0 ? 'positive' : 'negative'}
          hint={net >= 0 ? 'You kept more than you spent' : 'Spending exceeded income'}
        />
      </section>

      <div className="mt-5 grid gap-5 lg:grid-cols-3">
        <section className="lg:col-span-2 space-y-5">
          <div className="card">
            <h2 className="mb-4 font-semibold text-navy-900 dark:text-white">Your accounts</h2>
            <ul className="space-y-3">
              {data.accounts.map((account) => (
                <li
                  key={account.id}
                  className="flex items-center justify-between gap-4 rounded-xl border border-navy-100 p-4 dark:border-navy-800"
                >
                  <div className="min-w-0">
                    <p className="font-semibold text-navy-900 dark:text-white">
                      {account.nickname ?? humanise(account.accountType)}
                    </p>
                    <p className="tabular mt-0.5 text-xs text-navy-500 dark:text-navy-400">
                      {maskAccount(account.accountNumber)} · {account.ifscCode}
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="tabular font-bold text-navy-900 dark:text-white">
                      {money(account.balance)}
                    </p>
                    <p className="tabular text-xs text-navy-500 dark:text-navy-400">
                      {money(account.availableBalance)} available
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          </div>

          <div className="card">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="font-semibold text-navy-900 dark:text-white">Recent activity</h2>
              <Link
                to="/app/accounts"
                className="text-sm font-medium text-navy-600 hover:underline dark:text-teal-400"
              >
                View all
              </Link>
            </div>

            {data.recentTransactions.length === 0 ? (
              <p className="py-6 text-center text-sm text-navy-500 dark:text-navy-400">
                No transactions yet. Your activity will appear here.
              </p>
            ) : (
              <ul className="divide-y divide-navy-100 dark:divide-navy-800">
                {data.recentTransactions.slice(0, 7).map((txn) => {
                  const isDebit = txn.direction === 'DEBIT';
                  return (
                    <li key={txn.reference} className="flex items-center gap-3 py-3">
                      <span
                        className={`inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${
                          isDebit
                            ? 'bg-red-50 text-red-600 dark:bg-red-950/40'
                            : 'bg-emerald-50 text-emerald-600 dark:bg-emerald-950/40'
                        }`}
                      >
                        {isDebit ? (
                          <ArrowUpRight className="h-4 w-4" aria-hidden />
                        ) : (
                          <ArrowDownLeft className="h-4 w-4" aria-hidden />
                        )}
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-navy-900 dark:text-white">
                          {txn.description ?? txn.counterpartyName ?? humanise(txn.transactionType)}
                        </p>
                        <p className="text-xs text-navy-500 dark:text-navy-400">
                          {date(txn.bookedAt)}
                          {txn.category && ` · ${humanise(txn.category)}`}
                        </p>
                      </div>
                      <div className="text-right">
                        <p
                          className={`tabular text-sm font-semibold ${
                            isDebit
                              ? 'text-navy-900 dark:text-white'
                              : 'text-emerald-600 dark:text-emerald-400'
                          }`}
                        >
                          {isDebit ? '−' : '+'}
                          {money(txn.amount)}
                        </p>
                        {txn.riskLevel && txn.riskLevel !== 'LOW' && (
                          <RiskBadge level={txn.riskLevel} score={txn.riskScore} />
                        )}
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </section>

        <section className="space-y-5">
          <div className="card">
            <h2 className="mb-1 font-semibold text-navy-900 dark:text-white">Financial health</h2>
            {data.healthScore === null ? (
              <p className="py-4 text-sm text-navy-500 dark:text-navy-400">
                Not enough activity yet to score.
              </p>
            ) : (
              <>
                <div className="flex items-end gap-2">
                  <p className="tabular text-4xl font-bold text-navy-900 dark:text-white">
                    {data.healthScore}
                  </p>
                  <p className="pb-1 text-sm text-navy-500 dark:text-navy-400">/ 100</p>
                </div>
                <p className="mt-1 text-sm font-medium text-teal-700 dark:text-teal-400">
                  {humanise(data.healthBand)}
                </p>
                <div className="mt-3 h-2 overflow-hidden rounded-full bg-navy-100 dark:bg-navy-800">
                  <div
                    className="h-full rounded-full bg-gradient-to-r from-navy-900 to-teal-500"
                    style={{ width: `${data.healthScore}%` }}
                  />
                </div>
                <Link
                  to="/app/health"
                  className="mt-3 inline-block text-sm font-medium text-navy-600 hover:underline dark:text-teal-400"
                >
                  See the breakdown
                </Link>
                <p className="mt-2 text-xs text-navy-400 dark:text-navy-500">
                  A wellness score from your own activity. Not a credit score.
                </p>
              </>
            )}
          </div>

          <div className="card">
            <h2 className="mb-3 font-semibold text-navy-900 dark:text-white">
              Where your money went
            </h2>
            {data.topCategories.length === 0 ? (
              <p className="py-4 text-sm text-navy-500 dark:text-navy-400">
                No spending recorded this month.
              </p>
            ) : (
              <>
                <div className="h-44">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={data.topCategories.map((slice) => ({
                          name: humanise(slice.category),
                          value: slice.total,
                        }))}
                        dataKey="value"
                        nameKey="name"
                        innerRadius={38}
                        outerRadius={64}
                        paddingAngle={2}
                      >
                        {data.topCategories.map((_, index) => (
                          <Cell key={index} fill={CATEGORY_COLOURS[index % CATEGORY_COLOURS.length]} />
                        ))}
                      </Pie>
                      <Tooltip formatter={(value: number) => money(value)} />
                      <Legend verticalAlign="bottom" height={28} iconSize={8} />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
                <ul className="mt-2 space-y-1.5">
                  {data.topCategories.map((slice, index) => (
                    <li key={slice.category} className="flex items-center justify-between text-sm">
                      <span className="flex items-center gap-2 text-navy-700 dark:text-navy-200">
                        <span
                          className="h-2.5 w-2.5 rounded-full"
                          style={{ background: CATEGORY_COLOURS[index % CATEGORY_COLOURS.length] }}
                        />
                        {humanise(slice.category)}
                      </span>
                      <span className="tabular font-medium text-navy-900 dark:text-white">
                        {money(slice.total)}{' '}
                        <span className="text-navy-400">({percent(slice.share)})</span>
                      </span>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </div>

          {data.goals.length > 0 && (
            <div className="card">
              <h2 className="mb-3 font-semibold text-navy-900 dark:text-white">Savings goals</h2>
              <ul className="space-y-4">
                {data.goals.map((goal) => (
                  <li key={goal.id}>
                    <div className="flex items-baseline justify-between text-sm">
                      <span className="font-medium text-navy-900 dark:text-white">{goal.name}</span>
                      <span className="tabular text-navy-500 dark:text-navy-400">
                        {percent(goal.progressPercent)}
                      </span>
                    </div>
                    <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-navy-100 dark:bg-navy-800">
                      <div
                        className={`h-full rounded-full ${
                          goal.onTrack ? 'bg-teal-500' : 'bg-amber-500'
                        }`}
                        style={{ width: `${Math.min(goal.progressPercent, 100)}%` }}
                      />
                    </div>
                    <p className="tabular mt-1 text-xs text-navy-500 dark:text-navy-400">
                      {money(goal.savedAmount)} of {money(goal.targetAmount)}
                      {!goal.onTrack && (
                        <span className="ml-1 inline-flex items-center gap-1 text-amber-600 dark:text-amber-400">
                          <TriangleAlert className="h-3 w-3" aria-hidden />
                          behind
                        </span>
                      )}
                    </p>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {data.upcomingBills.length > 0 && (
            <div className="card">
              <h2 className="mb-3 font-semibold text-navy-900 dark:text-white">Bills due soon</h2>
              <ul className="space-y-2.5">
                {data.upcomingBills.map((bill) => (
                  <li key={bill.reference} className="flex items-center justify-between gap-3 text-sm">
                    <div className="min-w-0">
                      <p className="truncate font-medium text-navy-900 dark:text-white">
                        {bill.billerName}
                      </p>
                      <p className="text-xs text-navy-500 dark:text-navy-400">
                        Due {date(bill.dueDate)}
                      </p>
                    </div>
                    <span className="tabular font-semibold text-navy-900 dark:text-white">
                      {money(bill.amount)}
                    </span>
                  </li>
                ))}
              </ul>
              <Link to="/app/bills" className="btn-secondary mt-4 w-full">
                Go to bill payments
              </Link>
            </div>
          )}

          <div className="card">
            <h2 className="mb-3 font-semibold text-navy-900 dark:text-white">Quick actions</h2>
            <div className="grid grid-cols-2 gap-2">
              <Link to="/app/transfer" className="btn-secondary justify-start">
                <Send className="h-4 w-4" aria-hidden />
                Transfer
              </Link>
              <Link to="/app/bills" className="btn-secondary justify-start">
                <Receipt className="h-4 w-4" aria-hidden />
                Bills
              </Link>
              <Link to="/app/statements" className="btn-secondary justify-start">
                <FileText className="h-4 w-4" aria-hidden />
                Statement
              </Link>
              <Link to="/app/alerts" className="btn-secondary justify-start">
                <ShieldAlert className="h-4 w-4" aria-hidden />
                Alerts
              </Link>
            </div>
          </div>
        </section>
      </div>

      {data.accounts.length === 0 && (
        <div className="mt-5">
          <EmptyState
            title="No accounts yet"
            message="Once an account is opened for you it will appear here with its balance and activity."
          />
        </div>
      )}
    </>
  );
}
