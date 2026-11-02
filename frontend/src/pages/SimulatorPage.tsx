import { useState } from 'react';
import { Loader2, Play } from 'lucide-react';
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { errorMessage } from '../api/client';
import { intelligence } from '../api/endpoints';
import type { SimulationResult } from '../api/types';
import { Banner, PageHeader, SimulationNotice } from '../components/ui';
import { humanise, money } from '../lib/format';

const CATEGORIES = ['FOOD', 'SHOPPING', 'UTILITIES', 'TRANSPORT', 'ENTERTAINMENT', 'EDUCATION', 'RENT'];

/**
 * What-if simulator.
 *
 * Every projection comes back with its assumptions, which are displayed rather
 * than buried. The simulator cannot change a balance - the endpoint behind it
 * is read-only.
 */
export default function SimulatorPage() {
  const [months, setMonths] = useState(12);
  const [monthlySaving, setMonthlySaving] = useState('');
  const [expenseChange, setExpenseChange] = useState('');
  const [incomeChange, setIncomeChange] = useState('');
  const [category, setCategory] = useState('');
  const [categoryReduction, setCategoryReduction] = useState('');

  const [result, setResult] = useState<SimulationResult | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function run(event: React.FormEvent) {
    event.preventDefault();
    setError('');
    setBusy(true);
    try {
      setResult(
        await intelligence.simulate({
          months,
          monthlySavingAmount: monthlySaving ? Number(monthlySaving).toFixed(2) : null,
          expenseChangePercent: expenseChange ? Number(expenseChange).toFixed(2) : null,
          incomeChangePercent: incomeChange ? Number(incomeChange).toFixed(2) : null,
          category: category || null,
          categoryReductionPercent: categoryReduction
            ? Number(categoryReduction).toFixed(2)
            : null,
        }),
      );
    } catch (caught) {
      setError(errorMessage(caught, 'Could not run the simulation.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader
        title="What-if simulator"
        subtitle="Test a financial decision before you make it. Nothing here moves money."
      />

      <div className="grid gap-5 lg:grid-cols-5">
        <form onSubmit={run} className="card space-y-4 lg:col-span-2">
          <h2 className="font-semibold text-navy-900 dark:text-white">Build a scenario</h2>

          <div>
            <label className="label" htmlFor="months">
              Horizon: {months} months
            </label>
            <input
              id="months"
              type="range"
              min={1}
              max={60}
              value={months}
              onChange={(event) => setMonths(Number(event.target.value))}
              className="w-full accent-teal-600"
            />
          </div>

          <div>
            <label className="label" htmlFor="monthlySaving">
              Set aside each month
            </label>
            <input
              id="monthlySaving"
              inputMode="decimal"
              className="input tabular"
              value={monthlySaving}
              onChange={(event) => setMonthlySaving(event.target.value.replace(/[^\d.]/g, ''))}
              placeholder="e.g. 3000"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label" htmlFor="expenseChange">
                Spending change %
              </label>
              <input
                id="expenseChange"
                inputMode="decimal"
                className="input tabular"
                value={expenseChange}
                onChange={(event) => setExpenseChange(event.target.value.replace(/[^\d.-]/g, ''))}
                placeholder="-10"
              />
            </div>
            <div>
              <label className="label" htmlFor="incomeChange">
                Income change %
              </label>
              <input
                id="incomeChange"
                inputMode="decimal"
                className="input tabular"
                value={incomeChange}
                onChange={(event) => setIncomeChange(event.target.value.replace(/[^\d.-]/g, ''))}
                placeholder="-10"
              />
            </div>
          </div>

          <div className="rounded-xl border border-navy-100 p-3 dark:border-navy-800">
            <p className="mb-2 text-sm font-medium text-navy-800 dark:text-navy-100">
              Or cut one category
            </p>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="label" htmlFor="category">
                  Category
                </label>
                <select
                  id="category"
                  className="input"
                  value={category}
                  onChange={(event) => setCategory(event.target.value)}
                >
                  <option value="">None</option>
                  {CATEGORIES.map((item) => (
                    <option key={item} value={item}>
                      {humanise(item)}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="label" htmlFor="categoryReduction">
                  Reduce by %
                </label>
                <input
                  id="categoryReduction"
                  inputMode="decimal"
                  className="input tabular"
                  value={categoryReduction}
                  onChange={(event) =>
                    setCategoryReduction(event.target.value.replace(/[^\d.]/g, ''))
                  }
                  placeholder="15"
                  disabled={!category}
                />
              </div>
            </div>
          </div>

          <button type="submit" className="btn-primary w-full" disabled={busy}>
            {busy ? (
              <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
            ) : (
              <Play className="h-4 w-4" aria-hidden />
            )}
            Run simulation
          </button>

          <SimulationNotice />
        </form>

        <div className="space-y-5 lg:col-span-3">
          {error && <Banner tone="error">{error}</Banner>}

          {!result ? (
            <div className="card flex h-full min-h-72 flex-col items-center justify-center text-center">
              <p className="font-semibold text-navy-800 dark:text-navy-100">
                Set a scenario and run it
              </p>
              <p className="mt-1 max-w-sm text-sm text-navy-500 dark:text-navy-400">
                The projection uses your actual averages from the last three months. No investment
                return is assumed, and every assumption is listed with the result.
              </p>
            </div>
          ) : (
            <>
              {!result.dataSufficient && (
                <Banner tone="warning" title="Limited history">
                  There is not much transaction history to project from yet, so treat these figures
                  as rough.
                </Banner>
              )}

              <div className="card">
                <h2 className="mb-1 font-semibold text-navy-900 dark:text-white">Result</h2>
                <p className="text-sm leading-relaxed text-navy-700 dark:text-navy-200">
                  {result.verdict}
                </p>

                <div className="mt-4 grid gap-3 sm:grid-cols-2">
                  {[result.current, result.proposed].map((side) => (
                    <div
                      key={side.label}
                      className="rounded-xl border border-navy-100 p-4 dark:border-navy-800"
                    >
                      <p className="text-xs font-semibold uppercase tracking-wide text-navy-500 dark:text-navy-400">
                        {side.label}
                      </p>
                      <dl className="mt-2 space-y-1.5 text-sm">
                        <Row label="Income / month" value={money(side.monthlyIncome)} />
                        <Row label="Spending / month" value={money(side.monthlyExpense)} />
                        <Row label="Net / month" value={money(side.monthlyNet)} />
                        <Row
                          label={`After ${result.months}m`}
                          value={money(side.endBalance)}
                          strong
                        />
                      </dl>
                    </div>
                  ))}
                </div>

                <div className="mt-4 rounded-xl bg-navy-50 p-4 dark:bg-navy-950">
                  <div className="flex items-baseline justify-between gap-3">
                    <p className="text-sm text-navy-600 dark:text-navy-300">
                      Difference over {result.months} months
                    </p>
                    <p
                      className={`tabular text-xl font-bold ${
                        result.difference >= 0
                          ? 'text-emerald-600 dark:text-emerald-400'
                          : 'text-red-600 dark:text-red-400'
                      }`}
                    >
                      {result.difference >= 0 ? '+' : ''}
                      {money(result.difference)}
                    </p>
                  </div>
                  <p className="tabular mt-1 text-xs text-navy-500 dark:text-navy-400">
                    Accumulating {money(result.monthlyAccumulation)} a month
                  </p>
                </div>
              </div>

              <div className="card">
                <h2 className="mb-3 font-semibold text-navy-900 dark:text-white">Projection</h2>
                <div className="h-64">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={result.series} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#e3eaf5" vertical={false} />
                      <XAxis dataKey="period" tick={{ fontSize: 10 }} />
                      <YAxis tick={{ fontSize: 10 }} width={64} />
                      <Tooltip formatter={(value: number) => money(value)} />
                      <Legend iconSize={8} />
                      <Line
                        name="Current path"
                        type="monotone"
                        dataKey="current"
                        stroke="#9bb4d8"
                        strokeWidth={2}
                        strokeDasharray="5 4"
                        dot={false}
                      />
                      <Line
                        name="Proposed path"
                        type="monotone"
                        dataKey="projected"
                        stroke="#04c8b1"
                        strokeWidth={2.5}
                        dot={false}
                      />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              </div>

              <div className="card">
                <h2 className="mb-2 font-semibold text-navy-900 dark:text-white">Assumptions</h2>
                <ul className="list-disc space-y-1.5 pl-5 text-sm text-navy-600 dark:text-navy-300">
                  {result.assumptions.map((assumption) => (
                    <li key={assumption}>{assumption}</li>
                  ))}
                </ul>
              </div>
            </>
          )}
        </div>
      </div>
    </>
  );
}

function Row({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-navy-500 dark:text-navy-400">{label}</dt>
      <dd
        className={`tabular ${
          strong ? 'font-bold text-navy-900 dark:text-white' : 'font-medium text-navy-800 dark:text-navy-100'
        }`}
      >
        {value}
      </dd>
    </div>
  );
}
