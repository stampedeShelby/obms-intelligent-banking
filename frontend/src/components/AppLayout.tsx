import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import {
  Bell,
  Bot,
  CreditCard,
  FileText,
  Gauge,
  LayoutDashboard,
  LogOut,
  Menu,
  Moon,
  PiggyBank,
  Receipt,
  Send,
  ShieldAlert,
  Sun,
  Target,
  TrendingUp,
  Users,
  Wallet,
  X,
} from 'lucide-react';

import { notifications as notificationsApi } from '../api/endpoints';
import { useAuth } from '../context/useAuth';
import { useTheme } from '../context/useTheme';

interface NavItem {
  to: string;
  label: string;
  Icon: typeof LayoutDashboard;
  roles?: string[];
}

const CUSTOMER_NAV: NavItem[] = [
  { to: '/app', label: 'Dashboard', Icon: LayoutDashboard },
  { to: '/app/accounts', label: 'Accounts', Icon: Wallet },
  { to: '/app/transfer', label: 'Transfer', Icon: Send },
  { to: '/app/beneficiaries', label: 'Beneficiaries', Icon: Users },
  { to: '/app/bills', label: 'Bill payments', Icon: Receipt },
  { to: '/app/statements', label: 'Statements', Icon: FileText },
];

const INTELLIGENCE_NAV: NavItem[] = [
  { to: '/app/copilot', label: 'Financial copilot', Icon: Bot },
  { to: '/app/analytics', label: 'Spending analytics', Icon: TrendingUp },
  { to: '/app/health', label: 'Financial health', Icon: Gauge },
  { to: '/app/simulator', label: 'What-if simulator', Icon: CreditCard },
  { to: '/app/goals', label: 'Savings goals', Icon: Target },
  { to: '/app/alerts', label: 'Fraud alerts', Icon: ShieldAlert },
];

const STAFF_NAV: NavItem[] = [
  { to: '/app/admin', label: 'Admin dashboard', Icon: Gauge, roles: ['ADMIN', 'BANK_STAFF', 'FRAUD_ANALYST'] },
  { to: '/app/admin/kyc', label: 'KYC verification', Icon: Users, roles: ['ADMIN', 'BANK_STAFF'] },
  { to: '/app/admin/fraud', label: 'Fraud investigation', Icon: ShieldAlert, roles: ['ADMIN', 'FRAUD_ANALYST'] },
  { to: '/app/admin/audit', label: 'Audit log', Icon: FileText, roles: ['ADMIN'] },
];

/**
 * The signed-in shell: responsive sidebar, top bar and the routed page.
 *
 * Navigation is filtered by role, but that is presentation only - the backend
 * refuses an unauthorised call regardless of what the sidebar shows.
 */
export default function AppLayout() {
  const { session, signOut, isStaff } = useAuth();
  const { theme, toggle } = useTheme();
  const navigate = useNavigate();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [unread, setUnread] = useState(0);

  useEffect(() => {
    let cancelled = false;
    notificationsApi
      .unreadCount()
      .then((count) => {
        if (!cancelled) setUnread(count);
      })
      .catch(() => {
        // A failed badge count must not interrupt the page.
      });
    return () => {
      cancelled = true;
    };
  }, []);

  function handleSignOut() {
    signOut();
    navigate('/login', { replace: true });
  }

  function visible(items: NavItem[]) {
    return items.filter(
      (item) => !item.roles || item.roles.some((role) => session?.roles.includes(role)),
    );
  }

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    [
      'flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition',
      isActive
        ? 'bg-navy-900 text-white dark:bg-teal-500 dark:text-navy-950'
        : 'text-navy-600 hover:bg-navy-100 dark:text-navy-300 dark:hover:bg-navy-800',
    ].join(' ');

  return (
    <div className="min-h-screen lg:flex">
      {/* Mobile overlay */}
      {sidebarOpen && (
        <button
          type="button"
          aria-label="Close navigation"
          className="fixed inset-0 z-30 bg-navy-950/50 lg:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      <aside
        className={[
          'fixed inset-y-0 left-0 z-40 w-72 shrink-0 overflow-y-auto border-r border-navy-100 bg-white px-4 py-5 transition-transform',
          'dark:border-navy-800 dark:bg-navy-900',
          'lg:static lg:translate-x-0',
          sidebarOpen ? 'translate-x-0' : '-translate-x-full',
        ].join(' ')}
      >
        <div className="mb-6 flex items-center justify-between">
          <Link to="/app" className="flex items-center gap-2.5">
            <Logo />
            <span className="text-lg font-bold tracking-tight text-navy-900 dark:text-white">
              OBMS
            </span>
          </Link>
          <button
            type="button"
            className="btn-ghost p-2 lg:hidden"
            onClick={() => setSidebarOpen(false)}
            aria-label="Close navigation"
          >
            <X className="h-5 w-5" aria-hidden />
          </button>
        </div>

        <NavGroup label="Banking" items={visible(CUSTOMER_NAV)} linkClass={linkClass} onNavigate={() => setSidebarOpen(false)} />
        <NavGroup label="Intelligence" items={visible(INTELLIGENCE_NAV)} linkClass={linkClass} onNavigate={() => setSidebarOpen(false)} />
        {isStaff && (
          <NavGroup label="Administration" items={visible(STAFF_NAV)} linkClass={linkClass} onNavigate={() => setSidebarOpen(false)} />
        )}

        <div className="mt-6 rounded-xl bg-navy-50 p-3 text-xs text-navy-600 dark:bg-navy-950 dark:text-navy-400">
          Simulated banking environment. No real funds are involved.
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 flex items-center justify-between gap-3 border-b border-navy-100 bg-white/90 px-4 py-3 backdrop-blur dark:border-navy-800 dark:bg-navy-900/90 lg:px-8">
          <button
            type="button"
            className="btn-ghost p-2 lg:hidden"
            onClick={() => setSidebarOpen(true)}
            aria-label="Open navigation"
          >
            <Menu className="h-5 w-5" aria-hidden />
          </button>

          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold text-navy-900 dark:text-white">
              {session?.displayName}
            </p>
            <p className="truncate text-xs text-navy-500 dark:text-navy-400">
              {session?.roles.join(' · ')}
            </p>
          </div>

          <div className="flex items-center gap-1">
            <button
              type="button"
              className="btn-ghost p-2"
              onClick={toggle}
              aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
            >
              {theme === 'dark' ? <Sun className="h-5 w-5" aria-hidden /> : <Moon className="h-5 w-5" aria-hidden />}
            </button>

            <Link to="/app/notifications" className="btn-ghost relative p-2" aria-label="Notifications">
              <Bell className="h-5 w-5" aria-hidden />
              {unread > 0 && (
                <span className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-teal-600 px-1 text-[10px] font-bold text-white">
                  {unread > 9 ? '9+' : unread}
                </span>
              )}
            </Link>

            <Link to="/app/profile" className="btn-ghost p-2" aria-label="Profile and settings">
              <PiggyBank className="h-5 w-5" aria-hidden />
            </Link>

            <button type="button" className="btn-ghost p-2" onClick={handleSignOut} aria-label="Sign out">
              <LogOut className="h-5 w-5" aria-hidden />
            </button>
          </div>
        </header>

        <main className="flex-1 px-4 py-6 lg:px-8 lg:py-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

function NavGroup({
  label,
  items,
  linkClass,
  onNavigate,
}: {
  label: string;
  items: NavItem[];
  linkClass: (state: { isActive: boolean }) => string;
  onNavigate: () => void;
}) {
  if (items.length === 0) return null;

  return (
    <nav className="mb-5">
      <p className="mb-2 px-3 text-[11px] font-bold uppercase tracking-wider text-navy-400 dark:text-navy-500">
        {label}
      </p>
      <ul className="space-y-1">
        {items.map(({ to, label: itemLabel, Icon }) => (
          <li key={to}>
            <NavLink to={to} end={to === '/app'} className={linkClass} onClick={onNavigate}>
              <Icon className="h-4.5 w-4.5 shrink-0" aria-hidden />
              <span className="truncate">{itemLabel}</span>
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}

/** The OBMS mark: a shield outline around a rising bar motif. */
export function Logo({ className = 'h-8 w-8' }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" className={className} role="img" aria-label="OBMS">
      <defs>
        <linearGradient id="obms-mark" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#0e234c" />
          <stop offset="100%" stopColor="#04c8b1" />
        </linearGradient>
      </defs>
      <path
        d="M16 2.5 4.5 6.4v9.2c0 6.6 4.6 12.1 11.5 13.9 6.9-1.8 11.5-7.3 11.5-13.9V6.4Z"
        fill="url(#obms-mark)"
      />
      <rect x="10" y="17" width="2.8" height="5.5" rx="1" fill="#fff" opacity="0.95" />
      <rect x="14.6" y="13.5" width="2.8" height="9" rx="1" fill="#fff" opacity="0.95" />
      <rect x="19.2" y="10" width="2.8" height="12.5" rx="1" fill="#fff" opacity="0.95" />
    </svg>
  );
}
