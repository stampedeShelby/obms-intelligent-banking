import type { ReactNode } from 'react';
import { AlertTriangle, CheckCircle2, Info, Loader2, Inbox } from 'lucide-react';

import { riskClasses } from '../lib/format';

/** Page heading with optional actions on the right. */
export function PageHeader({
  title,
  subtitle,
  actions,
}: {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}) {
  return (
    <header className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-navy-900 dark:text-white">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-navy-600 dark:text-navy-300">{subtitle}</p>}
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </header>
  );
}

/** A labelled statistic. */
export function StatTile({
  label,
  value,
  hint,
  tone = 'default',
}: {
  label: string;
  value: ReactNode;
  hint?: string;
  tone?: 'default' | 'positive' | 'negative';
}) {
  const toneClass =
    tone === 'positive'
      ? 'text-emerald-600 dark:text-emerald-400'
      : tone === 'negative'
        ? 'text-red-600 dark:text-red-400'
        : 'text-navy-900 dark:text-white';

  return (
    <div className="card">
      <p className="text-xs font-semibold uppercase tracking-wide text-navy-500 dark:text-navy-400">
        {label}
      </p>
      <p className={`tabular mt-2 text-2xl font-bold ${toneClass}`}>{value}</p>
      {hint && <p className="mt-1 text-xs text-navy-500 dark:text-navy-400">{hint}</p>}
    </div>
  );
}

/** Risk band pill. */
export function RiskBadge({ level, score }: { level: string | null; score?: number | null }) {
  if (!level) return null;
  return (
    <span className={`badge ${riskClasses(level)}`}>
      {level}
      {score !== null && score !== undefined && <span className="tabular opacity-70">{score}</span>}
    </span>
  );
}

/** Inline status message. */
export function Banner({
  tone,
  title,
  children,
}: {
  tone: 'info' | 'success' | 'warning' | 'error';
  title?: string;
  children: ReactNode;
}) {
  const config = {
    info: { cls: 'border-navy-200 bg-navy-50 text-navy-800 dark:border-navy-700 dark:bg-navy-900 dark:text-navy-100', Icon: Info },
    success: { cls: 'border-emerald-200 bg-emerald-50 text-emerald-900 dark:border-emerald-900 dark:bg-emerald-950/50 dark:text-emerald-200', Icon: CheckCircle2 },
    warning: { cls: 'border-amber-200 bg-amber-50 text-amber-900 dark:border-amber-900 dark:bg-amber-950/50 dark:text-amber-200', Icon: AlertTriangle },
    error: { cls: 'border-red-200 bg-red-50 text-red-900 dark:border-red-900 dark:bg-red-950/50 dark:text-red-200', Icon: AlertTriangle },
  }[tone];

  return (
    <div className={`flex gap-3 rounded-xl border p-3.5 text-sm ${config.cls}`} role="alert">
      <config.Icon className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
      <div>
        {title && <p className="font-semibold">{title}</p>}
        <div className={title ? 'mt-0.5' : ''}>{children}</div>
      </div>
    </div>
  );
}

/** Loading placeholder matching the shape of the content it replaces. */
export function SkeletonCard({ lines = 3 }: { lines?: number }) {
  return (
    <div className="card">
      <div className="skeleton mb-3 h-3 w-28" />
      {Array.from({ length: lines }).map((_, index) => (
        <div key={index} className="skeleton mb-2 h-4" style={{ width: `${90 - index * 15}%` }} />
      ))}
    </div>
  );
}

export function SkeletonGrid({ count = 4 }: { count?: number }) {
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {Array.from({ length: count }).map((_, index) => (
        <SkeletonCard key={index} lines={2} />
      ))}
    </div>
  );
}

/** Shown when a list has no rows, rather than an empty box. */
export function EmptyState({
  title,
  message,
  action,
}: {
  title: string;
  message: string;
  action?: ReactNode;
}) {
  return (
    <div className="card flex flex-col items-center py-12 text-center">
      <Inbox className="mb-3 h-8 w-8 text-navy-300 dark:text-navy-600" aria-hidden />
      <p className="font-semibold text-navy-800 dark:text-navy-100">{title}</p>
      <p className="mt-1 max-w-sm text-sm text-navy-500 dark:text-navy-400">{message}</p>
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function Spinner({ label }: { label?: string }) {
  return (
    <span className="inline-flex items-center gap-2 text-sm text-navy-500 dark:text-navy-400">
      <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
      {label ?? 'Loading'}
    </span>
  );
}

/** Confirmation dialog for anything that moves money or cannot be undone. */
export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = 'Confirm',
  tone = 'primary',
  busy,
  onConfirm,
  onCancel,
}: {
  open: boolean;
  title: string;
  message: ReactNode;
  confirmLabel?: string;
  tone?: 'primary' | 'danger';
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-navy-950/50 p-4"
      role="dialog"
      aria-modal="true"
      aria-label={title}
    >
      <div className="w-full max-w-md animate-fade-in rounded-2xl bg-white p-6 shadow-lifted dark:bg-navy-900">
        <h2 className="text-lg font-bold text-navy-900 dark:text-white">{title}</h2>
        <div className="mt-2 text-sm text-navy-600 dark:text-navy-300">{message}</div>
        <div className="mt-6 flex justify-end gap-2">
          <button type="button" className="btn-secondary" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
          <button
            type="button"
            className={tone === 'danger' ? 'btn bg-red-600 text-white hover:bg-red-700' : 'btn-primary'}
            onClick={onConfirm}
            disabled={busy}
          >
            {busy && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

/** The simulated-environment notice. Shown wherever money appears to move. */
export function SimulationNotice() {
  return (
    <p className="text-xs text-navy-500 dark:text-navy-400">
      Simulated banking environment. No real funds, accounts or payment networks are involved.
    </p>
  );
}
