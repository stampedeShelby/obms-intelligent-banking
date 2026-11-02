import { useEffect, useState } from 'react';
import { Check, Loader2, X } from 'lucide-react';

import { errorMessage } from '../api/client';
import { admin } from '../api/endpoints';
import type { Page } from '../api/types';
import { Banner, EmptyState, PageHeader, SkeletonCard } from '../components/ui';
import { dateTime, humanise } from '../lib/format';

interface KycQueueItem {
  kycId: number;
  customerNumber: string;
  fullName: string;
  email: string;
  idProofType: string;
  idProofMasked: string;
  addressProofType: string;
  status: string;
  submittedAt: string | null;
}

/** KYC verification queue for branch staff and administrators. */
export default function AdminKycPage() {
  const [data, setData] = useState<Page<KycQueueItem> | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busyId, setBusyId] = useState<number | null>(null);
  const [rejecting, setRejecting] = useState<number | null>(null);
  const [reason, setReason] = useState('');

  function refresh() {
    return admin.kycQueue(0, 25).then(setData);
  }

  useEffect(() => {
    refresh().catch((caught) => setError(errorMessage(caught, 'Could not load the KYC queue.')));
  }, []);

  async function decide(kycId: number, approve: boolean, rejectionReason?: string) {
    setError('');
    setNotice('');
    setBusyId(kycId);
    try {
      await admin.decideKyc(kycId, approve, rejectionReason);
      await refresh();
      setRejecting(null);
      setReason('');
      setNotice(approve ? 'Verified. Transfers are now available to the customer.' : 'Rejected. The customer has been told why.');
    } catch (caught) {
      setError(errorMessage(caught, 'Could not record that decision.'));
    } finally {
      setBusyId(null);
    }
  }

  if (!data) {
    return (
      <>
        <PageHeader title="KYC verification" />
        <SkeletonCard lines={4} />
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="KYC verification"
        subtitle="Submissions awaiting review. Approving one unlocks transfers for that customer."
      />

      <div className="space-y-4">
        {error && <Banner tone="error">{error}</Banner>}
        {notice && <Banner tone="success">{notice}</Banner>}

        {data.content.length === 0 ? (
          <EmptyState
            title="Queue is clear"
            message="There are no KYC submissions awaiting review."
          />
        ) : (
          <ul className="space-y-3">
            {data.content.map((item) => (
              <li key={item.kycId} className="card">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div className="min-w-0">
                    <p className="font-semibold text-navy-900 dark:text-white">{item.fullName}</p>
                    <p className="tabular text-sm text-navy-600 dark:text-navy-300">
                      {item.customerNumber} · {item.email}
                    </p>
                    <dl className="mt-3 grid gap-x-6 gap-y-1 text-sm sm:grid-cols-2">
                      <div className="flex gap-2">
                        <dt className="text-navy-500 dark:text-navy-400">ID proof</dt>
                        <dd className="font-medium text-navy-900 dark:text-white">
                          {humanise(item.idProofType)}{' '}
                          <span className="tabular font-normal text-navy-500">
                            {item.idProofMasked}
                          </span>
                        </dd>
                      </div>
                      <div className="flex gap-2">
                        <dt className="text-navy-500 dark:text-navy-400">Address proof</dt>
                        <dd className="font-medium text-navy-900 dark:text-white">
                          {humanise(item.addressProofType)}
                        </dd>
                      </div>
                    </dl>
                    <p className="mt-2 text-xs text-navy-500 dark:text-navy-400">
                      Submitted {dateTime(item.submittedAt)}
                    </p>
                  </div>

                  <div className="flex shrink-0 gap-2">
                    <button
                      type="button"
                      className="btn bg-emerald-600 text-white hover:bg-emerald-700"
                      onClick={() => decide(item.kycId, true)}
                      disabled={busyId === item.kycId}
                    >
                      {busyId === item.kycId ? (
                        <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
                      ) : (
                        <Check className="h-4 w-4" aria-hidden />
                      )}
                      Approve
                    </button>
                    <button
                      type="button"
                      className="btn-secondary"
                      onClick={() => setRejecting(rejecting === item.kycId ? null : item.kycId)}
                      disabled={busyId === item.kycId}
                    >
                      <X className="h-4 w-4" aria-hidden />
                      Reject
                    </button>
                  </div>
                </div>

                {rejecting === item.kycId && (
                  <form
                    className="mt-4 border-t border-navy-100 pt-4 dark:border-navy-800"
                    onSubmit={(event) => {
                      event.preventDefault();
                      decide(item.kycId, false, reason.trim());
                    }}
                  >
                    <label className="label" htmlFor={`reason-${item.kycId}`}>
                      Reason for rejection
                    </label>
                    <input
                      id={`reason-${item.kycId}`}
                      required
                      maxLength={255}
                      className="input"
                      value={reason}
                      onChange={(event) => setReason(event.target.value)}
                      placeholder="The customer will see this"
                    />
                    <p className="mt-1.5 text-xs text-navy-500 dark:text-navy-400">
                      A rejection must state a reason. The customer is shown it and can submit
                      again.
                    </p>
                    <div className="mt-3 flex gap-2">
                      <button
                        type="submit"
                        className="btn bg-red-600 text-white hover:bg-red-700"
                        disabled={!reason.trim() || busyId === item.kycId}
                      >
                        Confirm rejection
                      </button>
                      <button
                        type="button"
                        className="btn-ghost"
                        onClick={() => {
                          setRejecting(null);
                          setReason('');
                        }}
                      >
                        Cancel
                      </button>
                    </div>
                  </form>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </>
  );
}
