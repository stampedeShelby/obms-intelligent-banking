import { useEffect, useState } from 'react';
import { CheckCircle2, Loader2, Plus, Target, TriangleAlert } from 'lucide-react';

import { errorMessage } from '../api/client';
import { banking, intelligence } from '../api/endpoints';
import type { AccountSummary, GoalView } from '../api/types';
import { Banner, ConfirmDialog, EmptyState, PageHeader, SkeletonCard } from '../components/ui';
import { date, humanise, maskAccount, money, percent } from '../lib/format';

/** Savings goals with honest, surplus-aware recommendations. */
export default function GoalsPage() {
  const [goals, setGoals] = useState<GoalView[]>([]);
  const [accounts, setAccounts] = useState<AccountSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [creating, setCreating] = useState(false);
  const [abandonTarget, setAbandonTarget] = useState<GoalView | null>(null);
  const [contributing, setContributing] = useState<GoalView | null>(null);
  const [contribution, setContribution] = useState('');
  const [fundingAccount, setFundingAccount] = useState<number | null>(null);

  const [form, setForm] = useState({ name: '', targetAmount: '', targetDate: '' });

  function refresh() {
    return intelligence.goals().then(setGoals);
  }

  useEffect(() => {
    Promise.all([intelligence.goals(), banking.accounts()])
      .then(([goalList, accountList]) => {
        setGoals(goalList);
        setAccounts(accountList);
        setFundingAccount(accountList[0]?.id ?? null);
      })
      .catch((caught) => setError(errorMessage(caught, 'Could not load your goals.')))
      .finally(() => setLoading(false));
  }, []);

  async function create(event: React.FormEvent) {
    event.preventDefault();
    setError('');
    setBusy(true);
    try {
      await intelligence.createGoal({
        name: form.name.trim(),
        targetAmount: Number(form.targetAmount).toFixed(2),
        targetDate: form.targetDate,
      });
      await refresh();
      setCreating(false);
      setForm({ name: '', targetAmount: '', targetDate: '' });
      setNotice('Goal created.');
    } catch (caught) {
      setError(errorMessage(caught, 'Could not create the goal.'));
    } finally {
      setBusy(false);
    }
  }

  async function contribute(event: React.FormEvent) {
    event.preventDefault();
    if (!contributing || fundingAccount === null) return;
    setError('');
    setBusy(true);
    try {
      await intelligence.contribute(
        contributing.id,
        Number(contribution).toFixed(2),
        fundingAccount,
      );
      await Promise.all([refresh(), banking.accounts().then(setAccounts)]);
      setContributing(null);
      setContribution('');
      setNotice('Contribution added and your account debited.');
    } catch (caught) {
      setError(errorMessage(caught, 'Could not add the contribution.'));
    } finally {
      setBusy(false);
    }
  }

  async function abandon() {
    if (!abandonTarget) return;
    setBusy(true);
    try {
      await intelligence.abandonGoal(abandonTarget.id);
      await refresh();
      setNotice(`${abandonTarget.name} was abandoned.`);
    } catch (caught) {
      setError(errorMessage(caught, 'Could not abandon the goal.'));
    } finally {
      setBusy(false);
      setAbandonTarget(null);
    }
  }

  const minDate = new Date(Date.now() + 86_400_000).toISOString().slice(0, 10);

  return (
    <>
      <PageHeader
        title="Savings goals"
        subtitle="Each goal compares what you would need to save against what your surplus actually supports."
        actions={
          <button type="button" className="btn-primary" onClick={() => setCreating((open) => !open)}>
            <Plus className="h-4 w-4" aria-hidden />
            New goal
          </button>
        }
      />

      <div className="space-y-4">
        {error && <Banner tone="error">{error}</Banner>}
        {notice && <Banner tone="success">{notice}</Banner>}

        {creating && (
          <form onSubmit={create} className="card space-y-4">
            <h2 className="font-semibold text-navy-900 dark:text-white">New savings goal</h2>
            <div className="grid gap-4 sm:grid-cols-3">
              <div>
                <label className="label" htmlFor="goalName">
                  Name
                </label>
                <input
                  id="goalName"
                  required
                  className="input"
                  value={form.name}
                  onChange={(event) => setForm((c) => ({ ...c, name: event.target.value }))}
                  placeholder="Emergency fund"
                />
              </div>
              <div>
                <label className="label" htmlFor="goalTarget">
                  Target amount
                </label>
                <input
                  id="goalTarget"
                  inputMode="decimal"
                  required
                  className="input tabular"
                  value={form.targetAmount}
                  onChange={(event) =>
                    setForm((c) => ({ ...c, targetAmount: event.target.value.replace(/[^\d.]/g, '') }))
                  }
                />
              </div>
              <div>
                <label className="label" htmlFor="goalDate">
                  Target date
                </label>
                <input
                  id="goalDate"
                  type="date"
                  required
                  min={minDate}
                  className="input"
                  value={form.targetDate}
                  onChange={(event) => setForm((c) => ({ ...c, targetDate: event.target.value }))}
                />
              </div>
            </div>
            <div className="flex gap-2">
              <button type="submit" className="btn-primary" disabled={busy}>
                {busy && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
                Create goal
              </button>
              <button type="button" className="btn-secondary" onClick={() => setCreating(false)}>
                Cancel
              </button>
            </div>
          </form>
        )}

        {contributing && (
          <form onSubmit={contribute} className="card space-y-4">
            <h2 className="font-semibold text-navy-900 dark:text-white">
              Add to {contributing.name}
            </h2>
            <p className="text-sm text-navy-600 dark:text-navy-300">
              This debits the account you choose, so the contribution appears on your statement.
            </p>
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="label" htmlFor="contribution">
                  Amount
                </label>
                <input
                  id="contribution"
                  inputMode="decimal"
                  required
                  className="input tabular"
                  value={contribution}
                  onChange={(event) => setContribution(event.target.value.replace(/[^\d.]/g, ''))}
                />
              </div>
              <div>
                <label className="label" htmlFor="fundingAccount">
                  From
                </label>
                <select
                  id="fundingAccount"
                  className="input"
                  value={fundingAccount ?? ''}
                  onChange={(event) => setFundingAccount(Number(event.target.value))}
                >
                  {accounts.map((account) => (
                    <option key={account.id} value={account.id}>
                      {account.nickname ?? humanise(account.accountType)} ·{' '}
                      {maskAccount(account.accountNumber)} · {money(account.availableBalance)}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <div className="flex gap-2">
              <button type="submit" className="btn-primary" disabled={busy || !contribution}>
                {busy && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
                Add contribution
              </button>
              <button type="button" className="btn-secondary" onClick={() => setContributing(null)}>
                Cancel
              </button>
            </div>
          </form>
        )}

        {loading ? (
          <SkeletonCard lines={4} />
        ) : goals.length === 0 ? (
          <EmptyState
            title="No savings goals yet"
            message="Name something you are saving for. The goal works out what you need to set aside each month and tells you honestly whether your surplus supports it."
            action={
              <button type="button" className="btn-primary" onClick={() => setCreating(true)}>
                <Plus className="h-4 w-4" aria-hidden />
                Create a goal
              </button>
            }
          />
        ) : (
          <ul className="grid gap-4 lg:grid-cols-2">
            {goals.map((goal) => (
              <li key={goal.id} className="card">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="inline-flex rounded-lg bg-navy-100 p-1.5 dark:bg-navy-800">
                        <Target className="h-4 w-4 text-navy-700 dark:text-navy-200" aria-hidden />
                      </span>
                      <p className="truncate font-semibold text-navy-900 dark:text-white">
                        {goal.name}
                      </p>
                    </div>
                    <p className="mt-1 text-xs text-navy-500 dark:text-navy-400">
                      Target {date(goal.targetDate)} · {goal.monthsRemaining} months left
                    </p>
                  </div>
                  {goal.status === 'ACHIEVED' ? (
                    <span className="badge bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300">
                      <CheckCircle2 className="h-3 w-3" aria-hidden />
                      Achieved
                    </span>
                  ) : goal.onTrack ? (
                    <span className="badge bg-teal-50 text-teal-700 dark:bg-teal-950/40 dark:text-teal-300">
                      On track
                    </span>
                  ) : (
                    <span className="badge bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300">
                      <TriangleAlert className="h-3 w-3" aria-hidden />
                      Behind
                    </span>
                  )}
                </div>

                <div className="mt-4">
                  <div className="flex items-baseline justify-between text-sm">
                    <span className="tabular font-semibold text-navy-900 dark:text-white">
                      {money(goal.savedAmount)}
                    </span>
                    <span className="tabular text-navy-500 dark:text-navy-400">
                      of {money(goal.targetAmount)}
                    </span>
                  </div>
                  <div className="mt-1.5 h-2.5 overflow-hidden rounded-full bg-navy-100 dark:bg-navy-800">
                    <div
                      className={`h-full rounded-full ${
                        goal.status === 'ACHIEVED'
                          ? 'bg-emerald-500'
                          : goal.onTrack
                            ? 'bg-teal-500'
                            : 'bg-amber-500'
                      }`}
                      style={{ width: `${Math.min(goal.progressPercent, 100)}%` }}
                    />
                  </div>
                  <p className="tabular mt-1 text-xs text-navy-500 dark:text-navy-400">
                    {percent(goal.progressPercent)} · {money(goal.remainingAmount)} to go
                  </p>
                </div>

                <dl className="mt-4 grid grid-cols-2 gap-3 rounded-xl bg-navy-50 p-3 text-sm dark:bg-navy-950">
                  <div>
                    <dt className="text-xs text-navy-500 dark:text-navy-400">Needed / month</dt>
                    <dd className="tabular font-semibold text-navy-900 dark:text-white">
                      {money(goal.requiredMonthly)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-navy-500 dark:text-navy-400">Your surplus supports</dt>
                    <dd className="tabular font-semibold text-navy-900 dark:text-white">
                      {money(goal.recommendedMonthly)}
                    </dd>
                  </div>
                </dl>

                <p className="mt-3 text-sm leading-relaxed text-navy-600 dark:text-navy-300">
                  {goal.advice}
                </p>

                {goal.status === 'ACTIVE' && (
                  <div className="mt-4 flex gap-2">
                    <button
                      type="button"
                      className="btn-primary flex-1"
                      onClick={() => setContributing(goal)}
                    >
                      Add money
                    </button>
                    <button
                      type="button"
                      className="btn-secondary"
                      onClick={() => setAbandonTarget(goal)}
                    >
                      Abandon
                    </button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>

      <ConfirmDialog
        open={abandonTarget !== null}
        title="Abandon this goal?"
        tone="danger"
        confirmLabel="Abandon"
        busy={busy}
        message={
          <>
            {abandonTarget?.name} will be marked abandoned. Money you already contributed stays in
            your account history and is not returned automatically.
          </>
        }
        onConfirm={abandon}
        onCancel={() => setAbandonTarget(null)}
      />
    </>
  );
}
