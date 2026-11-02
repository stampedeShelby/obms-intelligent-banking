import { useEffect, useState } from 'react';
import { CheckCircle2, Loader2, Plus, ShieldCheck, Trash2 } from 'lucide-react';

import { errorMessage } from '../api/client';
import { beneficiaries } from '../api/endpoints';
import type { BeneficiaryView } from '../api/types';
import { Banner, ConfirmDialog, EmptyState, PageHeader, SkeletonCard } from '../components/ui';
import { date, humanise } from '../lib/format';

/** Payee management with the OTP verification step. */
export default function BeneficiariesPage() {
  const [list, setList] = useState<BeneficiaryView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [adding, setAdding] = useState(false);
  const [busy, setBusy] = useState(false);
  const [removeTarget, setRemoveTarget] = useState<BeneficiaryView | null>(null);
  const [verifying, setVerifying] = useState<number | null>(null);
  const [code, setCode] = useState('');

  const [form, setForm] = useState({
    beneficiaryName: '',
    accountNumber: '',
    ifscCode: 'OBMS0000001',
    nickname: '',
  });

  function refresh() {
    return beneficiaries
      .list()
      .then(setList)
      .catch((caught) => setError(errorMessage(caught, 'Could not load your payees.')));
  }

  useEffect(() => {
    refresh().finally(() => setLoading(false));
  }, []);

  async function add(event: React.FormEvent) {
    event.preventDefault();
    setError('');
    setNotice('');
    setBusy(true);
    try {
      const created = await beneficiaries.add({
        beneficiaryName: form.beneficiaryName.trim(),
        accountNumber: form.accountNumber.trim(),
        ifscCode: form.ifscCode.trim().toUpperCase(),
        nickname: form.nickname.trim() || undefined,
      });
      await refresh();
      setAdding(false);
      setVerifying(created.id);
      setNotice('A verification code has been sent. Enter it to make this payee payable.');
      setForm({ beneficiaryName: '', accountNumber: '', ifscCode: 'OBMS0000001', nickname: '' });
    } catch (caught) {
      setError(errorMessage(caught, 'Could not add the payee.'));
    } finally {
      setBusy(false);
    }
  }

  async function verify(event: React.FormEvent) {
    event.preventDefault();
    if (verifying === null) return;
    setError('');
    setBusy(true);
    try {
      await beneficiaries.verify(verifying, code.trim());
      await refresh();
      setVerifying(null);
      setCode('');
      setNotice('Payee verified. You can now transfer to them.');
    } catch (caught) {
      setError(errorMessage(caught, 'That code was not accepted.'));
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    if (!removeTarget) return;
    setBusy(true);
    try {
      await beneficiaries.remove(removeTarget.id);
      await refresh();
      setNotice(`${removeTarget.nickname ?? removeTarget.beneficiaryName} was removed.`);
    } catch (caught) {
      setError(errorMessage(caught, 'Could not remove the payee.'));
    } finally {
      setBusy(false);
      setRemoveTarget(null);
    }
  }

  return (
    <>
      <PageHeader
        title="Beneficiaries"
        subtitle="A payee must be verified with a one-time code before it can receive money."
        actions={
          <button type="button" className="btn-primary" onClick={() => setAdding((open) => !open)}>
            <Plus className="h-4 w-4" aria-hidden />
            Add payee
          </button>
        }
      />

      <div className="space-y-4">
        {error && <Banner tone="error">{error}</Banner>}
        {notice && <Banner tone="success">{notice}</Banner>}

        {adding && (
          <form onSubmit={add} className="card space-y-4">
            <h2 className="font-semibold text-navy-900 dark:text-white">New payee</h2>
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className="label" htmlFor="beneficiaryName">
                  Payee name
                </label>
                <input
                  id="beneficiaryName"
                  required
                  className="input"
                  value={form.beneficiaryName}
                  onChange={(event) =>
                    setForm((current) => ({ ...current, beneficiaryName: event.target.value }))
                  }
                />
              </div>
              <div>
                <label className="label" htmlFor="nickname">
                  Nickname (optional)
                </label>
                <input
                  id="nickname"
                  className="input"
                  value={form.nickname}
                  onChange={(event) =>
                    setForm((current) => ({ ...current, nickname: event.target.value }))
                  }
                />
              </div>
              <div>
                <label className="label" htmlFor="accountNumber">
                  Account number
                </label>
                <input
                  id="accountNumber"
                  inputMode="numeric"
                  required
                  className="input tabular"
                  value={form.accountNumber}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      accountNumber: event.target.value.replace(/\D/g, ''),
                    }))
                  }
                />
              </div>
              <div>
                <label className="label" htmlFor="ifscCode">
                  IFSC code
                </label>
                <input
                  id="ifscCode"
                  required
                  className="input font-mono uppercase"
                  value={form.ifscCode}
                  onChange={(event) =>
                    setForm((current) => ({ ...current, ifscCode: event.target.value }))
                  }
                />
                <p className="mt-1.5 text-xs text-navy-500 dark:text-navy-400">
                  Transfers within OBMS only. Use OBMS0000001.
                </p>
              </div>
            </div>
            <div className="flex gap-2">
              <button type="submit" className="btn-primary" disabled={busy}>
                {busy && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
                Add and send code
              </button>
              <button type="button" className="btn-secondary" onClick={() => setAdding(false)}>
                Cancel
              </button>
            </div>
          </form>
        )}

        {verifying !== null && (
          <form onSubmit={verify} className="card space-y-4">
            <div className="flex items-center gap-2">
              <ShieldCheck className="h-5 w-5 text-teal-600 dark:text-teal-400" aria-hidden />
              <h2 className="font-semibold text-navy-900 dark:text-white">Verify this payee</h2>
            </div>
            <div className="max-w-xs">
              <label className="label" htmlFor="verifyCode">
                Verification code
              </label>
              <input
                id="verifyCode"
                inputMode="numeric"
                maxLength={8}
                required
                className="input tabular text-center text-xl tracking-[0.35em]"
                value={code}
                onChange={(event) => setCode(event.target.value.replace(/\D/g, ''))}
              />
            </div>
            <div className="flex gap-2">
              <button type="submit" className="btn-primary" disabled={busy || code.length < 4}>
                {busy && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
                Verify payee
              </button>
              <button
                type="button"
                className="btn-secondary"
                onClick={() => {
                  setVerifying(null);
                  setCode('');
                }}
              >
                Later
              </button>
            </div>
          </form>
        )}

        {loading ? (
          <SkeletonCard lines={3} />
        ) : list.length === 0 ? (
          <EmptyState
            title="No payees yet"
            message="Add someone you want to send money to. They become payable once you verify them with a one-time code."
            action={
              <button type="button" className="btn-primary" onClick={() => setAdding(true)}>
                <Plus className="h-4 w-4" aria-hidden />
                Add payee
              </button>
            }
          />
        ) : (
          <ul className="grid gap-3 sm:grid-cols-2">
            {list.map((payee) => (
              <li key={payee.id} className="card">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="truncate font-semibold text-navy-900 dark:text-white">
                      {payee.nickname ?? payee.beneficiaryName}
                    </p>
                    {payee.nickname && (
                      <p className="truncate text-xs text-navy-500 dark:text-navy-400">
                        {payee.beneficiaryName}
                      </p>
                    )}
                    <p className="tabular mt-1.5 text-sm text-navy-600 dark:text-navy-300">
                      {payee.maskedAccountNumber}
                    </p>
                    <p className="font-mono text-xs text-navy-500 dark:text-navy-400">
                      {payee.ifscCode}
                    </p>
                  </div>
                  <button
                    type="button"
                    className="btn-ghost p-2"
                    onClick={() => setRemoveTarget(payee)}
                    aria-label={`Remove ${payee.beneficiaryName}`}
                  >
                    <Trash2 className="h-4 w-4" aria-hidden />
                  </button>
                </div>

                <div className="mt-3 flex items-center justify-between gap-2">
                  {payee.payable ? (
                    <span className="badge bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300">
                      <CheckCircle2 className="h-3 w-3" aria-hidden />
                      Verified
                    </span>
                  ) : (
                    <span className="badge bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300">
                      {humanise(payee.status)}
                    </span>
                  )}

                  {!payee.payable && (
                    <button
                      type="button"
                      className="btn-secondary px-3 py-1.5 text-xs"
                      onClick={() => setVerifying(payee.id)}
                    >
                      Verify
                    </button>
                  )}
                </div>

                {payee.verifiedAt && (
                  <p className="mt-2 text-xs text-navy-400 dark:text-navy-500">
                    Verified {date(payee.verifiedAt)}
                  </p>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>

      <ConfirmDialog
        open={removeTarget !== null}
        title="Remove this payee?"
        tone="danger"
        confirmLabel="Remove"
        busy={busy}
        message={
          <>
            {removeTarget?.nickname ?? removeTarget?.beneficiaryName} will no longer be payable. Past
            transfers to them stay on your statement.
          </>
        }
        onConfirm={remove}
        onCancel={() => setRemoveTarget(null)}
      />
    </>
  );
}
