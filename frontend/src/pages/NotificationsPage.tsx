import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { BellOff, CheckCheck } from 'lucide-react';

import { errorMessage } from '../api/client';
import { notifications } from '../api/endpoints';
import type { NotificationItem, Page } from '../api/types';
import { Banner, EmptyState, PageHeader, SkeletonCard } from '../components/ui';
import { dateTime, humanise } from '../lib/format';

/** In-app notification centre. */
export default function NotificationsPage() {
  const [data, setData] = useState<Page<NotificationItem> | null>(null);
  const [error, setError] = useState('');

  function refresh() {
    return notifications.list(0, 50).then(setData);
  }

  useEffect(() => {
    refresh().catch((caught) =>
      setError(errorMessage(caught, 'Could not load your notifications.')),
    );
  }, []);

  async function markRead(id: number) {
    try {
      await notifications.markRead(id);
      await refresh();
    } catch (caught) {
      setError(errorMessage(caught, 'Could not update that notification.'));
    }
  }

  async function markAllRead() {
    try {
      await notifications.markAllRead();
      await refresh();
    } catch (caught) {
      setError(errorMessage(caught, 'Could not update your notifications.'));
    }
  }

  if (!data) {
    return (
      <>
        <PageHeader title="Notifications" />
        <SkeletonCard lines={4} />
      </>
    );
  }

  const unread = data.content.filter((item) => !item.read).length;

  return (
    <>
      <PageHeader
        title="Notifications"
        subtitle={unread > 0 ? `${unread} unread` : 'You are up to date.'}
        actions={
          unread > 0 ? (
            <button type="button" className="btn-secondary" onClick={markAllRead}>
              <CheckCheck className="h-4 w-4" aria-hidden />
              Mark all read
            </button>
          ) : undefined
        }
      />

      <div className="space-y-3">
        {error && <Banner tone="error">{error}</Banner>}

        {data.content.length === 0 ? (
          <EmptyState
            title="No notifications"
            message="Transaction confirmations, sign-in alerts and anything that needs your attention will appear here."
          />
        ) : (
          <ul className="space-y-2">
            {data.content.map((item) => (
              <li
                key={item.id}
                className={[
                  'card flex items-start gap-3',
                  item.read ? '' : 'border-l-4 border-l-teal-500',
                ].join(' ')}
              >
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-semibold text-navy-900 dark:text-white">{item.title}</p>
                    <span className="badge bg-navy-100 text-navy-600 dark:bg-navy-800 dark:text-navy-300">
                      {humanise(item.type)}
                    </span>
                    {!item.dispatched && (
                      <span
                        className="badge bg-navy-100 text-navy-500 dark:bg-navy-800"
                        title="The email channel was unavailable, so this is in-app only"
                      >
                        <BellOff className="h-3 w-3" aria-hidden />
                        In-app only
                      </span>
                    )}
                  </div>
                  <p className="mt-1 text-sm leading-relaxed text-navy-600 dark:text-navy-300">
                    {item.body}
                  </p>
                  <p className="mt-1.5 text-xs text-navy-500 dark:text-navy-400">
                    {dateTime(item.createdAt)}
                  </p>
                  {item.linkTarget && (
                    <Link
                      to={`/app${item.linkTarget}`}
                      className="mt-2 inline-block text-sm font-medium text-navy-700 hover:underline dark:text-teal-400"
                    >
                      Open
                    </Link>
                  )}
                </div>

                {!item.read && (
                  <button
                    type="button"
                    className="btn-ghost shrink-0 px-2.5 py-1.5 text-xs"
                    onClick={() => markRead(item.id)}
                  >
                    Mark read
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </>
  );
}
