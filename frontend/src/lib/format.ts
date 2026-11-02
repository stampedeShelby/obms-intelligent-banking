/**
 * Formatting helpers.
 *
 * Money is formatted in one place so that every screen renders an amount the
 * same way. Amounts arrive from the API as strings or numbers representing
 * rupees with two decimal places.
 */

const CURRENCY = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const COMPACT = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  notation: 'compact',
  maximumFractionDigits: 1,
});

/** Formats an amount as Indian rupees, e.g. `₹1,84,500.00`. */
export function money(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—';
  const numeric = typeof value === 'string' ? Number(value) : value;
  if (Number.isNaN(numeric)) return '—';
  return CURRENCY.format(numeric);
}

/** Formats an amount compactly for tight spaces, e.g. `₹1.8L`. */
export function moneyCompact(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—';
  const numeric = typeof value === 'string' ? Number(value) : value;
  if (Number.isNaN(numeric)) return '—';
  return COMPACT.format(numeric);
}

/** Formats a percentage with one decimal place. */
export function percent(value: number | string | null | undefined): string {
  if (value === null || value === undefined) return '—';
  const numeric = typeof value === 'string' ? Number(value) : value;
  if (Number.isNaN(numeric)) return '—';
  return `${numeric.toFixed(1)}%`;
}

/** Formats an ISO timestamp as a readable local date and time. */
export function dateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const parsed = new Date(iso);
  if (Number.isNaN(parsed.getTime())) return '—';
  return parsed.toLocaleString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** Formats an ISO timestamp or date as a readable local date. */
export function date(iso: string | null | undefined): string {
  if (!iso) return '—';
  const parsed = new Date(iso);
  if (Number.isNaN(parsed.getTime())) return '—';
  return parsed.toLocaleDateString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
}

/** Turns `NEEDS_ATTENTION` into `Needs attention`. */
export function humanise(value: string | null | undefined): string {
  if (!value) return '—';
  const lower = value.replace(/_/g, ' ').toLowerCase();
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

/** Masks all but the last four characters of an account number. */
export function maskAccount(accountNumber: string | null | undefined): string {
  if (!accountNumber) return '—';
  if (accountNumber.length <= 4) return accountNumber;
  return `•••• ${accountNumber.slice(-4)}`;
}

/** Tailwind classes for a risk band. */
export function riskClasses(level: string | null | undefined): string {
  switch (level) {
    case 'CRITICAL':
      return 'bg-red-50 text-risk-critical dark:bg-red-950/40';
    case 'HIGH':
      return 'bg-orange-50 text-risk-high dark:bg-orange-950/40';
    case 'MEDIUM':
      return 'bg-amber-50 text-risk-medium dark:bg-amber-950/40';
    case 'LOW':
      return 'bg-emerald-50 text-risk-low dark:bg-emerald-950/40';
    default:
      return 'bg-navy-100 text-navy-600 dark:bg-navy-800 dark:text-navy-300';
  }
}
