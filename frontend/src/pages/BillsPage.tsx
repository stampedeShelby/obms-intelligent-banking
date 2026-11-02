import { useEffect, useState } from 'react';
import { CheckCircle2, Loader2, Receipt } from 'lucide-react';

import { errorMessage } from '../api/client';
import { banking, bills } from '../api/endpoints';
import type { AccountSummary, BillItem, Biller, Page } from '../api/types';
import {
  Banner,
  EmptyState,
  PageHeader,
  SimulationNotice,
  SkeletonCard,
} from '../components/ui';
import { date, dateTime, humanise, maskAccount, money } from '../lib/format';

/** Bill payment against the simulated biller catalogue. */
export default function BillsPage() {
  const [billers, setBillers] = useState<Biller[]>([]);
  const [accounts, setAccounts] = useState<AccountSummary[]>([]);
  const [history, setHistory] = useState<Page<BillItem> | null>(null);
  const [upcoming, setUpcoming] = useState<BillItem[]>([]);
  const [loading, setLoading] = useState(true);

  const [billerId, setBillerId] = useState<number | null>(null);
  const [accountId, setAccountId] = useState<number | null>(null);
  const [identifier, setIdentifier] = useState('');
  const [amount, setAmount] = useState('');

  const [error, setError] = useState('');
  const [receipt, setReceipt] = useState<{ reference: string; billerName: string; amount: number } | null>(null);
  const [busy, setBusy] = useState(false);

  function reload() {
    return Promise.all([bills.history(0, 10), bills.upcoming(21)]).then(([pageData, due]) => {
      setHistory(pageData);
      setUpcoming(due);
    });
  }

  useEffect(() => {
    Promise.all([bills.billers(), banking.accounts(), bills.history(0, 10), bills.upcoming(21)])
      .then(([billerList, accountList, pageData, due]) => {
        setBillers(billerList);
        setAccounts(accountList);
        setHistory(pageData);
        setUpcoming(due);
        setBillerId(billerList[0]?.id ?? null);
        setAccountId(accountList[0]?.id ?? null);
      })
      .catch((caught) => setError(errorMessage(caught, 'Could not load bill payments.')))
      .finally(() => setLoading(false));
  }, []);

  const biller = billers.find((item) => item.id === billerId) ?? null;

  async function pay(event: React.FormEvent) {
    event.preventDefault();
    if (billerId === null || accountId === null) return;

    setError('');
    setBusy(true);
    try {
      const response = await bills.pay({
        billerId,
        accountId,
        consumerIdentifier: identifier.trim(),
        amount: Number(amount).toFixed(2),
      });
      setReceipt({
        reference: response.reference,
        billerName: response.billerName,
        amount: response.amount,
      });
      setIdentifier('');
      setAmount('');
      await reload();
    } catch (caught) {
      setError(errorMessage(caught, 'The payment could not be completed.'));
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return (
      <>
        <PageHeader title="Bill payments" />
        <SkeletonCard lines={4} />
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Bill payments"
        subtitle="Electricity, water, mobile, broadband, gas and credit card bills."
      />

      <div className="grid gap-5 lg:grid-cols-5">
        <div className="space-y-4 lg:col-span-3">
          {error && <Banner tone="error">{error}</Banner>}

          {receipt && (
            <div className="card">
              <div className="flex items-start gap-3">
                <span className="inline-flex rounded-full bg-emerald-50 p-2.5 dark:bg-emerald-950/40">
                  <CheckCircle2 className="h-5 w-5 text-emerald-600 dark:text-emerald-400" aria-hidden />
                </span>
                <div>
                  <p className="font-semibold text-navy-900 dark:text-white">Payment successful</p>
                  <p className="tabular mt-0.5 text-sm text-navy-600 dark:text-navy-300">
                    {money(receipt.amount)} paid to {receipt.billerName}
                  </p>
                  <p className="tabular mt-1 text-xs text-navy-500 dark:text-navy-400">
                    Reference {receipt.reference}
                  </p>
                </div>
              </div>
            </div>
          )}

          <form onSubmit={pay} className="card space-y-4">
            <h2 className="font-semibold text-navy-900 dark:text-white">Pay a bill</h2>

            <div>
              <label className="label" htmlFor="biller">
                Biller
              </label>
              <select
                id="biller"
                className="input"
                value={billerId ?? ''}
                onChange={(event) => setBillerId(Number(event.target.value))}
              >
                {billers.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name} · {humanise(item.category)}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="label" htmlFor="identifier">
                {biller?.identifierLabel ?? 'Consumer number'}
              </label>
              <input
                id="identifier"
                required
                className="input tabular"
                value={identifier}
                onChange={(event) => setIdentifier(event.target.value)}
                placeholder={biller?.identifierLabel}
              />
            </div>

            <div>
              <label className="label" htmlFor="account">
                Pay from
              </label>
              <select
                id="account"
                className="input"
                value={accountId ?? ''}
                onChange={(event) => setAccountId(Number(event.target.value))}
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
              <label className="label" htmlFor="billAmount">
                Amount
              </label>
              <input
                id="billAmount"
                inputMode="decimal"
                required
                className="input tabular text-lg"
                value={amount}
                onChange={(event) => setAmount(event.target.value.replace(/[^\d.]/g, ''))}
                placeholder="0.00"
              />
              {biller && (
                <p className="tabular mt-1.5 text-xs text-navy-500 dark:text-navy-400">
                  {biller.name} accepts between {money(biller.minAmount)} and{' '}
                  {money(biller.maxAmount)}.
                </p>
              )}
            </div>

            <button
              type="submit"
              className="btn-primary w-full"
              disabled={busy || !identifier || !amount}
            >
              {busy ? (
                <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
              ) : (
                <Receipt className="h-4 w-4" aria-hidden />
              )}
              Pay bill
            </button>

            <SimulationNotice />
          </form>
        </div>

        <div className="space-y-4 lg:col-span-2">
          {upcoming.length > 0 && (
            <div className="card">
              <h2 className="mb-3 font-semibold text-navy-900 dark:text-white">Due soon</h2>
              <ul className="space-y-2.5">
                {upcoming.map((bill) => (
                  <li key={bill.reference} className="flex items-center justify-between gap-3 text-sm">
                    <div className="min-w-0">
                      <p className="truncate font-medium text-navy-900 dark:text-white">
                        {bill.billerName}
                      </p>
                      <p className="text-xs text-navy-500 dark:text-navy-400">
                        {bill.consumerIdentifier} · due {date(bill.dueDate)}
                      </p>
                    </div>
                    <span className="tabular font-semibold text-navy-900 dark:text-white">
                      {money(bill.amount)}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          <div className="card">
            <h2 className="mb-3 font-semibold text-navy-900 dark:text-white">Payment history</h2>
            {!history || history.content.length === 0 ? (
              <p className="py-6 text-center text-sm text-navy-500 dark:text-navy-400">
                No payments yet.
              </p>
            ) : (
              <ul className="divide-y divide-navy-100 dark:divide-navy-800">
                {history.content.map((bill) => (
                  <li key={bill.reference} className="py-3">
                    <div className="flex items-baseline justify-between gap-3">
                      <p className="truncate text-sm font-medium text-navy-900 dark:text-white">
                        {bill.billerName}
                      </p>
                      <span className="tabular text-sm font-semibold text-navy-900 dark:text-white">
                        {money(bill.amount)}
                      </span>
                    </div>
                    <p className="text-xs text-navy-500 dark:text-navy-400">
                      {bill.paidAt ? dateTime(bill.paidAt) : `Due ${date(bill.dueDate)}`} ·{' '}
                      {humanise(bill.status)}
                    </p>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </div>

      {billers.length === 0 && (
        <div className="mt-5">
          <EmptyState
            title="No billers configured"
            message="The biller catalogue is seeded at startup. Restart the backend if this is empty."
          />
        </div>
      )}
    </>
  );
}
