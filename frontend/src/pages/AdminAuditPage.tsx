import { useCallback, useEffect, useState } from 'react';
import { ChevronLeft, ChevronRight, Search } from 'lucide-react';

import { errorMessage } from '../api/client';
import { admin } from '../api/endpoints';
import type { Page } from '../api/types';
import { Banner, EmptyState, PageHeader, SkeletonCard } from '../components/ui';
import { dateTime } from '../lib/format';

interface AuditRow {
  id: number;
  actor: string;
  action: string;
  entityType: string | null;
  entityRef: string | null;
  detail: string | null;
  ipAddress: string | null;
  success: boolean;
  at: string;
}

/** The audit trail. Read only: no endpoint in the API updates or deletes a row. */
export default function AdminAuditPage() {
  const [data, setData] = useState<Page<AuditRow> | null>(null);
  const [action, setAction] = useState('');
  const [actor, setActor] = useState('');
  const [page, setPage] = useState(0);
  const [error, setError] = useState('');

  const load = useCallback(
    (nextPage: number, filterAction: string, filterActor: string) =>
      admin
        .audit(filterAction, filterActor, nextPage, 30)
        .then(setData)
        .catch((caught) => setError(errorMessage(caught, 'Could not load the audit trail.'))),
    [],
  );

  useEffect(() => {
    load(page, action, actor);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  function search(event: React.FormEvent) {
    event.preventDefault();
    setPage(0);
    setData(null);
    load(0, action.trim(), actor.trim());
  }

  return (
    <>
      <PageHeader
        title="Audit log"
        subtitle="Every security and money-relevant action. Append only — nothing in the API can alter a row."
      />

      <form onSubmit={search} className="card mb-4 flex flex-wrap items-end gap-3">
        <div className="min-w-48 flex-1">
          <label className="label" htmlFor="action">
            Action
          </label>
          <input
            id="action"
            className="input font-mono text-sm"
            value={action}
            onChange={(event) => setAction(event.target.value)}
            placeholder="transfer.executed"
          />
        </div>
        <div className="min-w-48 flex-1">
          <label className="label" htmlFor="actor">
            Actor
          </label>
          <input
            id="actor"
            className="input text-sm"
            value={actor}
            onChange={(event) => setActor(event.target.value)}
            placeholder="user@example.com"
          />
        </div>
        <button type="submit" className="btn-primary">
          <Search className="h-4 w-4" aria-hidden />
          Search
        </button>
      </form>

      {error && (
        <div className="mb-4">
          <Banner tone="error">{error}</Banner>
        </div>
      )}

      {!data ? (
        <SkeletonCard lines={6} />
      ) : data.content.length === 0 ? (
        <EmptyState
          title="No matching entries"
          message="Nothing in the audit trail matches those filters."
        />
      ) : (
        <div className="card">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-navy-100 text-left text-xs uppercase tracking-wide text-navy-500 dark:border-navy-800 dark:text-navy-400">
                  <th className="pb-2 pr-3 font-semibold">When</th>
                  <th className="pb-2 pr-3 font-semibold">Actor</th>
                  <th className="pb-2 pr-3 font-semibold">Action</th>
                  <th className="pb-2 pr-3 font-semibold">Entity</th>
                  <th className="pb-2 pr-3 font-semibold">Detail</th>
                  <th className="pb-2 font-semibold">Result</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-navy-100 dark:divide-navy-800">
                {data.content.map((row) => (
                  <tr key={row.id}>
                    <td className="py-2.5 pr-3 align-top text-xs text-navy-500 dark:text-navy-400">
                      {dateTime(row.at)}
                      {row.ipAddress && (
                        <>
                          <br />
                          <span className="tabular">{row.ipAddress}</span>
                        </>
                      )}
                    </td>
                    <td className="py-2.5 pr-3 align-top text-navy-800 dark:text-navy-100">
                      {row.actor}
                    </td>
                    <td className="py-2.5 pr-3 align-top">
                      <code className="rounded bg-navy-100 px-1.5 py-0.5 font-mono text-xs text-navy-800 dark:bg-navy-800 dark:text-navy-100">
                        {row.action}
                      </code>
                    </td>
                    <td className="py-2.5 pr-3 align-top text-xs text-navy-600 dark:text-navy-300">
                      {row.entityType}
                      {row.entityRef && (
                        <>
                          <br />
                          <span className="tabular">{row.entityRef}</span>
                        </>
                      )}
                    </td>
                    <td className="py-2.5 pr-3 align-top text-navy-600 dark:text-navy-300">
                      {row.detail ?? '—'}
                    </td>
                    <td className="py-2.5 align-top">
                      <span
                        className={`badge ${
                          row.success
                            ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300'
                            : 'bg-red-50 text-red-700 dark:bg-red-950/40 dark:text-red-300'
                        }`}
                      >
                        {row.success ? 'Success' : 'Failed'}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {data.totalPages > 1 && (
            <div className="mt-4 flex items-center justify-between border-t border-navy-100 pt-4 dark:border-navy-800">
              <p className="text-xs text-navy-500 dark:text-navy-400">
                Page {data.number + 1} of {data.totalPages} · {data.totalElements} entries
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
                  disabled={page + 1 >= data.totalPages}
                  onClick={() => setPage((current) => current + 1)}
                >
                  Next
                  <ChevronRight className="h-4 w-4" aria-hidden />
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </>
  );
}
