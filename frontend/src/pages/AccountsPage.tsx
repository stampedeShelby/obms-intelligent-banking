import { useEffect, useMemo, useState } from 'react';
import { ArrowDownLeft, ArrowUpRight, ChevronLeft, ChevronRight, Pencil } from 'lucide-react';

import { errorMessage } from '../api/client';
import { banking } from '../api/endpoints';
import type { AccountSummary, Page, TransactionView } from '../api/types';
import { Banner, EmptyState, PageHeader, RiskBadge, SkeletonCard, StatTile } from '../components/ui';
import { dateTime, humanise, maskAccount, money } from '../lib/format';

const CATEGORIES = [
  'FOOD', 'SHOPPING', 'UTILITIES', 'TRANSPORT', 'HEALTHCARE',
  'ENTERTAINMENT', 'EDUCATION', 'RENT', 'TRANSFER', 'INCOME', 'SAVINGS', 'OTHER',
];

/** Accounts with paged, filterable transaction history. */
export default function AccountsPage() {
  const [accounts, setAccounts] = useState<AccountSummary[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [history, setHistory] = useState<Page<TransactionView> | null>(null);
  const [page, setPage] = useState(0);
  const [directionFilter, setDirectionFilter] = useState<'ALL' | 'DEBIT' | 'CREDIT'>('ALL');
  const [editing, setEditing] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    banking
      .accounts()
      .then((list) => {
        setAccounts(list);
        setSelected(list[0]?.id ?? null);
      })
      .catch((caught) => setError(errorMessage(caught, 'Could not load your accounts.')))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (selected === null) return;
    let cancelled = false;
    banking
      .history(selected, page, 20)
      .then((data) => {
        if (!cancelled) setHistory(data);
      })
      .catch((caught) => {
        if (!cancelled) setError(errorMessage(caught, 'Could not load the transaction history.'));
      });
    return () => {
      cancelled = true;
    };
  }, [selected, page]);

  const account = useMemo(
    () => accounts.find((item) => item.id === selected) ?? null,
    [accounts, selected],
  );

  const rows = useMemo(() => {
    const content = history?.content ?? [];
    if (directionFilter === 'ALL') return content;
    return content.filter((txn) => txn.direction === directionFilter);
  }, [history, directionFilter]);

  async function recategorise(reference: string, category: string) {
    try {
      const updated = await banking.recategorise(reference, category);
      setHistory((current) =>
        current
          ? {
              ...current,
              content: current.content.map((txn) =>
                txn.reference === reference ? updated : txn,
              ),
            }
          : current,
      );
      setEditing(null);
    } catch (caught) {
      setError(errorMessage(caught, 'Could not change the category.'));
    }
  }

  if (loading) {
    return (
      <>
        <PageHeader title="Accounts" />
        <SkeletonCard lines={4} />
      </>
    );
  }

  if (accounts.length === 0) {
    return (
      <>
        <PageHeader title="Accounts" />
        <EmptyState title="No accounts" message="No accounts are open on your customer record." />
      </>
    );
  }

  return (
    <>
      <PageHeader title="Accounts" subtitle="Balances and full transaction history." />

      {error && (
        <div className="mb-4">
          <Banner tone="error">{error}</Banner>
        </div>
      )}

      <div className="mb-5 flex flex-wrap gap-2">
        {accounts.map((item) => (
          <button
            key={item.id}
            type="button"
            onClick={() => {
              setSelected(item.id);
              setPage(0);
            }}
            className={[
              'rounded-xl border px-4 py-3 text-left transition',
              item.id === selected
                ? 'border-navy-900 bg-navy-900 text-white dark:border-teal-500 dark:bg-teal-500 dark:text-navy-950'
                : 'border-navy-200 bg-white text-navy-800 hover:bg-navy-50 dark:border-navy-700 dark:bg-navy-900 dark:text-navy-100',
            ].join(' ')}
          >
            <p className="text-sm font-semibold">
              {item.nickname ?? humanise(item.accountType)}
            </p>
            <p className="tabular text-xs opacity-80">{maskAccount(item.accountNumber)}</p>
          </button>
        ))}
      </div>

      {account && (
        <section className="mb-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatTile label="Balance" value={<span className="tabular">{money(account.balance)}</span>} />
          <StatTile
            label="Available"
            value={<span className="tabular">{money(account.availableBalance)}</span>}
            hint="Balance less the minimum balance floor"
          />
          <StatTile
            label="Daily transfer limit"
            value={<span className="tabular">{money(account.dailyTransferLimit)}</span>}
          />
          <StatTile label="Status" value={humanise(account.status)} hint={account.ifscCode} />
        </section>
      )}

      <div className="card">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <h2 className="font-semibold text-navy-900 dark:text-white">Transaction history</h2>
          <div className="flex gap-1 rounded-xl bg-navy-100 p-1 dark:bg-navy-800">
            {(['ALL', 'DEBIT', 'CREDIT'] as const).map((option) => (
              <button
                key={option}
                type="button"
                onClick={() => setDirectionFilter(option)}
                className={[
                  'rounded-lg px-3 py-1.5 text-xs font-semibold transition',
                  directionFilter === option
                    ? 'bg-white text-navy-900 shadow-sm dark:bg-navy-950 dark:text-white'
                    : 'text-navy-600 dark:text-navy-300',
                ].join(' ')}
              >
                {option === 'ALL' ? 'All' : option === 'DEBIT' ? 'Money out' : 'Money in'}
              </button>
            ))}
          </div>
        </div>

        {rows.length === 0 ? (
          <p className="py-10 text-center text-sm text-navy-500 dark:text-navy-400">
            No transactions match this filter.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-navy-100 text-left text-xs uppercase tracking-wide text-navy-500 dark:border-navy-800 dark:text-navy-400">
                  <th className="pb-2 pr-3 font-semibold">Date</th>
                  <th className="pb-2 pr-3 font-semibold">Description</th>
                  <th className="pb-2 pr-3 font-semibold">Category</th>
                  <th className="pb-2 pr-3 text-right font-semibold">Amount</th>
                  <th className="pb-2 text-right font-semibold">Balance</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-navy-100 dark:divide-navy-800">
                {rows.map((txn) => {
                  const isDebit = txn.direction === 'DEBIT';
                  return (
                    <tr key={txn.reference}>
                      <td className="py-3 pr-3 align-top text-xs text-navy-500 dark:text-navy-400">
                        {dateTime(txn.bookedAt)}
                        <br />
                        <span className="tabular">{txn.reference}</span>
                      </td>
                      <td className="py-3 pr-3 align-top">
                        <p className="font-medium text-navy-900 dark:text-white">
                          {txn.description ?? humanise(txn.transactionType)}
                        </p>
                        {txn.counterpartyName && (
                          <p className="text-xs text-navy-500 dark:text-navy-400">
                            {txn.counterpartyName}
                          </p>
                        )}
                        {txn.riskLevel && txn.riskLevel !== 'LOW' && (
                          <span className="mt-1 inline-block">
                            <RiskBadge level={txn.riskLevel} score={txn.riskScore} />
                          </span>
                        )}
                      </td>
                      <td className="py-3 pr-3 align-top">
                        {editing === txn.reference ? (
                          <select
                            className="input py-1.5 text-xs"
                            defaultValue={txn.category ?? 'OTHER'}
                            onChange={(event) => recategorise(txn.reference, event.target.value)}
                            onBlur={() => setEditing(null)}
                          >
                            {CATEGORIES.map((category) => (
                              <option key={category} value={category}>
                                {humanise(category)}
                              </option>
                            ))}
                          </select>
                        ) : (
                          <button
                            type="button"
                            onClick={() => setEditing(txn.reference)}
                            className="inline-flex items-center gap-1 rounded-lg bg-navy-100 px-2 py-1 text-xs font-medium text-navy-700 hover:bg-navy-200 dark:bg-navy-800 dark:text-navy-200"
                            title="Change the category"
                          >
                            {humanise(txn.category ?? 'OTHER')}
                            <Pencil className="h-3 w-3" aria-hidden />
                          </button>
                        )}
                        {txn.categoryUserSet && (
                          <p className="mt-1 text-[10px] text-navy-400">Set by you</p>
                        )}
                      </td>
                      <td className="py-3 pr-3 align-top text-right">
                        <span
                          className={`tabular inline-flex items-center gap-1 font-semibold ${
                            isDebit
                              ? 'text-navy-900 dark:text-white'
                              : 'text-emerald-600 dark:text-emerald-400'
                          }`}
                        >
                          {isDebit ? (
                            <ArrowUpRight className="h-3.5 w-3.5" aria-hidden />
                          ) : (
                            <ArrowDownLeft className="h-3.5 w-3.5" aria-hidden />
                          )}
                          {money(txn.amount)}
                        </span>
                      </td>
                      <td className="tabular py-3 align-top text-right text-navy-600 dark:text-navy-300">
                        {money(txn.balanceAfter)}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {history && history.totalPages > 1 && (
          <div className="mt-4 flex items-center justify-between border-t border-navy-100 pt-4 dark:border-navy-800">
            <p className="text-xs text-navy-500 dark:text-navy-400">
              Page {history.number + 1} of {history.totalPages} · {history.totalElements}{' '}
              transactions
            </p>
            <div className="flex gap-2">
              <button
                type="button"
                className="btn-secondary px-3 py-1.5"
                disabled={page === 0}
                onClick={() => setPage((current) => Math.max(0, current - 1))}
              >
                <ChevronLeft className="h-4 w-4" aria-hidden />
                Previous
              </button>
              <button
                type="button"
                className="btn-secondary px-3 py-1.5"
                disabled={page + 1 >= history.totalPages}
                onClick={() => setPage((current) => current + 1)}
              >
                Next
                <ChevronRight className="h-4 w-4" aria-hidden />
              </button>
            </div>
          </div>
        )}
      </div>
    </>
  );
}
