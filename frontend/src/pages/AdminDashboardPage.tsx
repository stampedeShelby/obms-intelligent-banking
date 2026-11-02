import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';

import { errorMessage } from '../api/client';
import { admin } from '../api/endpoints';
import { Banner, PageHeader, SkeletonGrid, StatTile } from '../components/ui';

interface SystemStats {
  totalCustomers: number;
  activeAccounts: number;
  pendingKyc: number;
  transactions7d: number;
  alerts7d: number;
  suspicious7d: number;
  openInvestigations: number;
}

interface Monitoring {
  transactionsAnalysed: number;
  alertsRaised: number;
  suspiciousCount: number;
  lowCount: number;
  mediumCount: number;
  highCount: number;
  criticalCount: number;
  openInvestigations: number;
  falsePositives: number;
}

const RISK_COLOURS: Record<string, string> = {
  LOW: '#0f9d58',
  MEDIUM: '#e8a33d',
  HIGH: '#e8602c',
  CRITICAL: '#c62828',
};

/** Admin and staff landing page: system statistics and fraud monitoring. */
export default function AdminDashboardPage() {
  const [stats, setStats] = useState<SystemStats | null>(null);
  const [monitoring, setMonitoring] = useState<Monitoring | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    admin
      .stats()
      .then(setStats)
      .catch((caught) => setError(errorMessage(caught, 'Could not load system statistics.')));

    // Monitoring is analyst-only, so a 403 here is expected for branch staff
    // and must not blank the whole page.
    admin
      .monitoring(7)
      .then(setMonitoring)
      .catch(() => setMonitoring(null));
  }, []);

  if (error) {
    return (
      <>
        <PageHeader title="Admin dashboard" />
        <Banner tone="error">{error}</Banner>
      </>
    );
  }

  if (!stats) {
    return (
      <>
        <PageHeader title="Admin dashboard" />
        <SkeletonGrid count={4} />
      </>
    );
  }

  const distribution = monitoring
    ? [
        { name: 'LOW', value: monitoring.lowCount },
        { name: 'MEDIUM', value: monitoring.mediumCount },
        { name: 'HIGH', value: monitoring.highCount },
        { name: 'CRITICAL', value: monitoring.criticalCount },
      ].filter((slice) => slice.value > 0)
    : [];

  return (
    <>
      <PageHeader
        title="Admin dashboard"
        subtitle="System statistics and fraud monitoring over the last seven days."
      />

      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatTile label="Customers" value={stats.totalCustomers} />
        <StatTile label="Active accounts" value={stats.activeAccounts} />
        <StatTile
          label="KYC awaiting review"
          value={stats.pendingKyc}
          hint={stats.pendingKyc > 0 ? 'Needs attention' : 'Queue is clear'}
          tone={stats.pendingKyc > 0 ? 'negative' : 'positive'}
        />
        <StatTile
          label="Open investigations"
          value={stats.openInvestigations}
          tone={stats.openInvestigations > 0 ? 'negative' : 'positive'}
        />
      </section>

      <section className="mt-5 grid gap-4 sm:grid-cols-3">
        <StatTile label="Transactions (7d)" value={stats.transactions7d} />
        <StatTile label="Assessments (7d)" value={stats.alerts7d} />
        <StatTile
          label="Above low risk (7d)"
          value={stats.suspicious7d}
          hint={
            stats.alerts7d > 0
              ? `${((stats.suspicious7d / stats.alerts7d) * 100).toFixed(1)}% of assessments`
              : undefined
          }
        />
      </section>

      <div className="mt-5 grid gap-5 lg:grid-cols-2">
        <div className="card">
          <h2 className="mb-3 font-semibold text-navy-900 dark:text-white">Risk distribution</h2>
          {distribution.length === 0 ? (
            <p className="py-10 text-center text-sm text-navy-500 dark:text-navy-400">
              {monitoring
                ? 'No assessments recorded yet.'
                : 'Fraud monitoring is available to analysts and administrators.'}
            </p>
          ) : (
            <>
              <div className="h-60">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={distribution}
                      dataKey="value"
                      nameKey="name"
                      innerRadius={48}
                      outerRadius={84}
                      paddingAngle={2}
                    >
                      {distribution.map((slice) => (
                        <Cell key={slice.name} fill={RISK_COLOURS[slice.name]} />
                      ))}
                    </Pie>
                    <Tooltip />
                    <Legend verticalAlign="bottom" height={28} iconSize={8} />
                  </PieChart>
                </ResponsiveContainer>
              </div>
              {monitoring && (
                <dl className="mt-2 space-y-1.5 text-sm">
                  <div className="flex justify-between">
                    <dt className="text-navy-500 dark:text-navy-400">Reported false positives</dt>
                    <dd className="tabular font-semibold text-navy-900 dark:text-white">
                      {monitoring.falsePositives}
                    </dd>
                  </div>
                </dl>
              )}
            </>
          )}
        </div>

        <div className="card">
          <h2 className="mb-3 font-semibold text-navy-900 dark:text-white">Where to go next</h2>
          <ul className="space-y-2">
            <QuickLink
              to="/app/admin/kyc"
              title="KYC verification queue"
              detail={`${stats.pendingKyc} submission${stats.pendingKyc === 1 ? '' : 's'} awaiting review`}
            />
            <QuickLink
              to="/app/admin/fraud"
              title="Fraud investigation"
              detail={`${stats.openInvestigations} open alert${stats.openInvestigations === 1 ? '' : 's'}`}
            />
            <QuickLink
              to="/app/admin/audit"
              title="Audit log"
              detail="Every security and money-relevant action, read only"
            />
          </ul>

          <p className="mt-4 rounded-xl bg-navy-50 p-3 text-xs text-navy-600 dark:bg-navy-950 dark:text-navy-400">
            Access to each area is enforced on the server by role, both at the URL and on the
            service method. What this menu shows is presentation only.
          </p>
        </div>
      </div>
    </>
  );
}

function QuickLink({ to, title, detail }: { to: string; title: string; detail: string }) {
  return (
    <li>
      <Link
        to={to}
        className="block rounded-xl border border-navy-100 p-3.5 transition hover:border-teal-500 hover:bg-navy-50 dark:border-navy-800 dark:hover:bg-navy-800"
      >
        <p className="font-medium text-navy-900 dark:text-white">{title}</p>
        <p className="mt-0.5 text-sm text-navy-500 dark:text-navy-400">{detail}</p>
      </Link>
    </li>
  );
}
