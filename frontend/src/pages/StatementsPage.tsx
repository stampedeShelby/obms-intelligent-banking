import { useEffect, useState } from 'react';
import { Download, FileText, Loader2 } from 'lucide-react';

import { errorMessage } from '../api/client';
import { banking } from '../api/endpoints';
import type { AccountSummary } from '../api/types';
import { Banner, PageHeader, SimulationNotice, SkeletonCard } from '../components/ui';
import { humanise, maskAccount, money } from '../lib/format';

function isoDaysAgo(days: number): string {
  const when = new Date();
  when.setDate(when.getDate() - days);
  return when.toISOString().slice(0, 10);
}

/** Statement generation and download (FR-7). */
export default function StatementsPage() {
  const [accounts, setAccounts] = useState<AccountSummary[]>([]);
  const [accountId, setAccountId] = useState<number | null>(null);
  const [from, setFrom] = useState(isoDaysAgo(30));
  const [to, setTo] = useState(new Date().toISOString().slice(0, 10));
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    banking
      .accounts()
      .then((list) => {
        setAccounts(list);
        setAccountId(list[0]?.id ?? null);
      })
      .catch((caught) => setError(errorMessage(caught, 'Could not load your accounts.')))
      .finally(() => setLoading(false));
  }, []);

  async function download(event: React.FormEvent) {
    event.preventDefault();
    if (accountId === null) return;

    setError('');
    setNotice('');
    setBusy(true);
    try {
      const blob = await banking.downloadStatement(accountId, from, to);
      const account = accounts.find((item) => item.id === accountId);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `OBMS_Statement_${account?.accountNumber ?? accountId}_${from}_to_${to}.pdf`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
      setNotice('Your statement has been downloaded.');
    } catch (caught) {
      setError(errorMessage(caught, 'Could not generate the statement.'));
    } finally {
      setBusy(false);
    }
  }

  const presets = [
    { label: 'Last 30 days', days: 30 },
    { label: 'Last 90 days', days: 90 },
    { label: 'Last 6 months', days: 182 },
    { label: 'Last year', days: 365 },
  ];

  if (loading) {
    return (
      <>
        <PageHeader title="Statements" />
        <SkeletonCard lines={3} />
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Statements"
        subtitle="Download a PDF statement for any account and date range."
      />

      <div className="mx-auto max-w-xl space-y-4">
        {error && <Banner tone="error">{error}</Banner>}
        {notice && <Banner tone="success">{notice}</Banner>}

        <form onSubmit={download} className="card space-y-4">
          <div className="flex items-center gap-2">
            <FileText className="h-5 w-5 text-navy-500 dark:text-navy-400" aria-hidden />
            <h2 className="font-semibold text-navy-900 dark:text-white">Generate a statement</h2>
          </div>

          <div>
            <label className="label" htmlFor="statementAccount">
              Account
            </label>
            <select
              id="statementAccount"
              className="input"
              value={accountId ?? ''}
              onChange={(event) => setAccountId(Number(event.target.value))}
            >
              {accounts.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.nickname ?? humanise(account.accountType)} ·{' '}
                  {maskAccount(account.accountNumber)} · {money(account.balance)}
                </option>
              ))}
            </select>
          </div>

          <div className="flex flex-wrap gap-2">
            {presets.map((preset) => (
              <button
                key={preset.label}
                type="button"
                className="btn-secondary px-3 py-1.5 text-xs"
                onClick={() => {
                  setFrom(isoDaysAgo(preset.days));
                  setTo(new Date().toISOString().slice(0, 10));
                }}
              >
                {preset.label}
              </button>
            ))}
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label className="label" htmlFor="from">
                From
              </label>
              <input
                id="from"
                type="date"
                required
                className="input"
                value={from}
                max={to}
                onChange={(event) => setFrom(event.target.value)}
              />
            </div>
            <div>
              <label className="label" htmlFor="to">
                To
              </label>
              <input
                id="to"
                type="date"
                required
                className="input"
                value={to}
                min={from}
                onChange={(event) => setTo(event.target.value)}
              />
            </div>
          </div>

          <button type="submit" className="btn-primary w-full" disabled={busy}>
            {busy ? (
              <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
            ) : (
              <Download className="h-4 w-4" aria-hidden />
            )}
            Download PDF
          </button>

          <p className="text-xs text-navy-500 dark:text-navy-400">
            The statement shows opening and closing balances, every debit and credit with its
            reference, and a running balance. Statements cover at most 400 days.
          </p>
        </form>

        <SimulationNotice />
      </div>
    </>
  );
}
