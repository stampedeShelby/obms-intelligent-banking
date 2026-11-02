import { Link } from 'react-router-dom';
import { ArrowRight, Bot, Gauge, LineChart, ShieldCheck, Target, Wallet } from 'lucide-react';

import { Logo } from '../components/AppLayout';

const FEATURES = [
  {
    Icon: ShieldCheck,
    title: 'Explainable fraud detection',
    body: 'Every transfer is scored before it settles. Alerts say which feature drove the score, in plain language, rather than showing a number with nothing behind it.',
  },
  {
    Icon: Bot,
    title: 'Financial copilot',
    body: 'Ask about your spending in ordinary English. Answers are computed from your own transactions, and the figures behind each answer are shown alongside it.',
  },
  {
    Icon: Gauge,
    title: 'Financial wellness score',
    body: 'Five weighted indicators out of 100, each with the arithmetic shown. A wellness measure computed from your own activity, not a credit score.',
  },
  {
    Icon: LineChart,
    title: 'What-if simulator',
    body: 'Test a decision before you make it. Projections use your real averages, assume no investment return, and state every assumption.',
  },
  {
    Icon: Target,
    title: 'Savings goals that are honest',
    body: 'Goals compare what you would need to save against what your surplus actually supports, and say so when a target is not reachable.',
  },
  {
    Icon: Wallet,
    title: 'Core banking, done properly',
    body: 'Transfers settle atomically under row locks, balances cannot go negative, and a retried request cannot debit you twice.',
  },
];

/** Public landing page. */
export default function LandingPage() {
  return (
    <div className="min-h-screen bg-white dark:bg-navy-950">
      <header className="mx-auto flex max-w-6xl items-center justify-between px-6 py-5">
        <div className="flex items-center gap-2.5">
          <Logo />
          <span className="text-lg font-bold tracking-tight text-navy-900 dark:text-white">OBMS</span>
        </div>
        <nav className="flex items-center gap-2">
          <Link to="/login" className="btn-ghost">
            Sign in
          </Link>
          <Link to="/register" className="btn-primary">
            Open an account
          </Link>
        </nav>
      </header>

      <section className="relative overflow-hidden">
        <div
          className="pointer-events-none absolute inset-x-0 -top-40 h-80 bg-gradient-to-br from-teal-200/40 via-transparent to-navy-200/40 blur-3xl dark:from-teal-900/30 dark:to-navy-800/30"
          aria-hidden
        />
        <div className="relative mx-auto max-w-4xl px-6 py-20 text-center">
          <p className="mb-4 inline-flex items-center gap-2 rounded-full border border-navy-200 px-3 py-1 text-xs font-semibold text-navy-600 dark:border-navy-700 dark:text-navy-300">
            Simulated banking environment
          </p>
          <h1 className="text-4xl font-bold tracking-tight text-navy-900 sm:text-5xl dark:text-white">
            Banking that explains itself
          </h1>
          <p className="mx-auto mt-5 max-w-2xl text-lg text-navy-600 dark:text-navy-300">
            OBMS brings together everyday banking and a layer of financial intelligence that shows
            its reasoning. Transfers, bills and statements on one side; fraud scoring, spending
            analysis and planning on the other.
          </p>
          <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
            <Link to="/register" className="btn-primary">
              Open an account
              <ArrowRight className="h-4 w-4" aria-hidden />
            </Link>
            <Link to="/login" className="btn-secondary">
              Sign in
            </Link>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-6 pb-20">
        <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map(({ Icon, title, body }) => (
            <article key={title} className="card">
              <div className="mb-3 inline-flex rounded-xl bg-navy-900 p-2.5 dark:bg-teal-500">
                <Icon className="h-5 w-5 text-white dark:text-navy-950" aria-hidden />
              </div>
              <h2 className="font-semibold text-navy-900 dark:text-white">{title}</h2>
              <p className="mt-2 text-sm leading-relaxed text-navy-600 dark:text-navy-300">{body}</p>
            </article>
          ))}
        </div>
      </section>

      <footer className="border-t border-navy-100 py-8 dark:border-navy-800">
        <div className="mx-auto max-w-6xl px-6 text-center text-sm text-navy-500 dark:text-navy-400">
          <p className="font-medium">
            OBMS is an academic project built for the Building Enterprise Applications course.
          </p>
          <p className="mt-1">
            It is a simulated environment. It connects to no real payment network, holds no real
            customer data, and moves no real funds.
          </p>
        </div>
      </footer>
    </div>
  );
}
