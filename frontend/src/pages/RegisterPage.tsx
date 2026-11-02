import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { CheckCircle2, Loader2 } from 'lucide-react';

import { errorMessage } from '../api/client';
import { auth } from '../api/endpoints';
import { Banner } from '../components/ui';
import { AuthShell } from './LoginPage';

/** Registration, with the password rule shown rather than discovered. */
export default function RegisterPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    firstName: '',
    lastName: '',
    email: '',
    mobile: '',
    password: '',
    city: '',
    state: '',
    pincode: '',
    declaredMonthlyIncome: '',
  });
  const [error, setError] = useState('');
  const [done, setDone] = useState<{ accountNumber: string; customerNumber: string } | null>(null);
  const [busy, setBusy] = useState(false);

  function update(field: keyof typeof form) {
    return (event: React.ChangeEvent<HTMLInputElement>) =>
      setForm((current) => ({ ...current, [field]: event.target.value }));
  }

  const rules = [
    { label: 'At least 10 characters', met: form.password.length >= 10 },
    { label: 'An upper and a lower case letter', met: /[a-z]/.test(form.password) && /[A-Z]/.test(form.password) },
    { label: 'A digit', met: /\d/.test(form.password) },
    { label: 'A symbol', met: /[^A-Za-z0-9]/.test(form.password) },
  ];

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError('');
    setBusy(true);
    try {
      const response = await auth.register({
        ...form,
        declaredMonthlyIncome: form.declaredMonthlyIncome ? form.declaredMonthlyIncome : null,
      });
      setDone({ accountNumber: response.accountNumber, customerNumber: response.customerNumber });
    } catch (caught) {
      setError(errorMessage(caught, 'Could not complete registration.'));
    } finally {
      setBusy(false);
    }
  }

  if (done) {
    return (
      <AuthShell>
        <div className="mb-3 inline-flex rounded-xl bg-emerald-600 p-2.5">
          <CheckCircle2 className="h-5 w-5 text-white" aria-hidden />
        </div>
        <h1 className="text-2xl font-bold text-navy-900 dark:text-white">Account opened</h1>
        <p className="mt-1.5 text-sm text-navy-600 dark:text-navy-300">
          Your savings account is open. Complete KYC verification from your profile to unlock
          transfers.
        </p>

        <dl className="mt-5 space-y-3 rounded-xl bg-navy-50 p-4 text-sm dark:bg-navy-950">
          <div className="flex justify-between gap-4">
            <dt className="text-navy-500 dark:text-navy-400">Customer number</dt>
            <dd className="tabular font-semibold text-navy-900 dark:text-white">
              {done.customerNumber}
            </dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-navy-500 dark:text-navy-400">Account number</dt>
            <dd className="tabular font-semibold text-navy-900 dark:text-white">
              {done.accountNumber}
            </dd>
          </div>
        </dl>

        <button type="button" className="btn-primary mt-6 w-full" onClick={() => navigate('/login')}>
          Go to sign in
        </button>
      </AuthShell>
    );
  }

  return (
    <AuthShell>
      <h1 className="text-2xl font-bold text-navy-900 dark:text-white">Open an account</h1>
      <p className="mt-1.5 text-sm text-navy-600 dark:text-navy-300">
        A savings account is opened straight away. KYC verification unlocks transfers.
      </p>

      {error && (
        <div className="mt-4">
          <Banner tone="error">{error}</Banner>
        </div>
      )}

      <form onSubmit={submit} className="mt-6 space-y-4">
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="label" htmlFor="firstName">
              First name
            </label>
            <input id="firstName" required className="input" value={form.firstName} onChange={update('firstName')} />
          </div>
          <div>
            <label className="label" htmlFor="lastName">
              Last name
            </label>
            <input id="lastName" required className="input" value={form.lastName} onChange={update('lastName')} />
          </div>
        </div>

        <div>
          <label className="label" htmlFor="email">
            Email
          </label>
          <input id="email" type="email" required className="input" value={form.email} onChange={update('email')} />
        </div>

        <div>
          <label className="label" htmlFor="mobile">
            Mobile number
          </label>
          <input
            id="mobile"
            inputMode="numeric"
            maxLength={10}
            required
            className="input tabular"
            value={form.mobile}
            onChange={(event) =>
              setForm((current) => ({ ...current, mobile: event.target.value.replace(/\D/g, '') }))
            }
            placeholder="10 digits"
          />
        </div>

        <div>
          <label className="label" htmlFor="password">
            Password
          </label>
          <input
            id="password"
            type="password"
            autoComplete="new-password"
            required
            className="input"
            value={form.password}
            onChange={update('password')}
          />
          <ul className="mt-2 space-y-1">
            {rules.map((rule) => (
              <li
                key={rule.label}
                className={`flex items-center gap-1.5 text-xs ${
                  rule.met ? 'text-emerald-600 dark:text-emerald-400' : 'text-navy-500 dark:text-navy-400'
                }`}
              >
                <CheckCircle2 className="h-3.5 w-3.5" aria-hidden />
                {rule.label}
              </li>
            ))}
          </ul>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="label" htmlFor="city">
              City
            </label>
            <input id="city" className="input" value={form.city} onChange={update('city')} />
          </div>
          <div>
            <label className="label" htmlFor="pincode">
              PIN code
            </label>
            <input
              id="pincode"
              inputMode="numeric"
              maxLength={6}
              className="input tabular"
              value={form.pincode}
              onChange={(event) =>
                setForm((current) => ({ ...current, pincode: event.target.value.replace(/\D/g, '') }))
              }
            />
          </div>
        </div>

        <div>
          <label className="label" htmlFor="income">
            Monthly income (optional)
          </label>
          <input
            id="income"
            inputMode="decimal"
            className="input tabular"
            value={form.declaredMonthlyIncome}
            onChange={update('declaredMonthlyIncome')}
            placeholder="Used by the financial health score"
          />
        </div>

        <button type="submit" className="btn-primary w-full" disabled={busy}>
          {busy && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
          Open account
        </button>
      </form>

      <p className="mt-5 text-center text-sm text-navy-600 dark:text-navy-300">
        Already have an account?{' '}
        <Link to="/login" className="font-medium text-navy-800 hover:underline dark:text-teal-400">
          Sign in
        </Link>
      </p>
    </AuthShell>
  );
}
