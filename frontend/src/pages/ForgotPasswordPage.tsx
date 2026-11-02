import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Loader2 } from 'lucide-react';

import { errorMessage } from '../api/client';
import { auth } from '../api/endpoints';
import { Banner } from '../components/ui';
import { AuthShell } from './LoginPage';

/**
 * Password reset.
 *
 * The request stage always reports success, whether or not the address is
 * registered - otherwise this form would reveal which addresses hold accounts.
 */
export default function ForgotPasswordPage() {
  const [stage, setStage] = useState<'request' | 'reset' | 'done'>('request');
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function request(event: React.FormEvent) {
    event.preventDefault();
    setError('');
    setBusy(true);
    try {
      await auth.forgotPassword(email.trim());
      setStage('reset');
    } catch (caught) {
      setError(errorMessage(caught, 'Could not start the reset.'));
    } finally {
      setBusy(false);
    }
  }

  async function reset(event: React.FormEvent) {
    event.preventDefault();
    setError('');
    setBusy(true);
    try {
      await auth.resetPassword(email.trim(), code.trim(), newPassword);
      setStage('done');
    } catch (caught) {
      setError(errorMessage(caught, 'Could not reset the password.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell>
      {stage === 'done' ? (
        <>
          <h1 className="text-2xl font-bold text-navy-900 dark:text-white">Password changed</h1>
          <p className="mt-1.5 text-sm text-navy-600 dark:text-navy-300">
            You can now sign in with your new password.
          </p>
          <Link to="/login" className="btn-primary mt-6 w-full">
            Go to sign in
          </Link>
        </>
      ) : stage === 'request' ? (
        <>
          <h1 className="text-2xl font-bold text-navy-900 dark:text-white">Reset your password</h1>
          <p className="mt-1.5 text-sm text-navy-600 dark:text-navy-300">
            Enter your email and we will send a reset code.
          </p>

          {error && (
            <div className="mt-4">
              <Banner tone="error">{error}</Banner>
            </div>
          )}

          <form onSubmit={request} className="mt-6 space-y-4">
            <div>
              <label className="label" htmlFor="email">
                Email
              </label>
              <input
                id="email"
                type="email"
                required
                className="input"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </div>
            <button type="submit" className="btn-primary w-full" disabled={busy}>
              {busy && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
              Send reset code
            </button>
          </form>
        </>
      ) : (
        <>
          <h1 className="text-2xl font-bold text-navy-900 dark:text-white">Enter your code</h1>
          <p className="mt-1.5 text-sm text-navy-600 dark:text-navy-300">
            If an account exists for {email}, a code has been sent to it.
          </p>

          {error && (
            <div className="mt-4">
              <Banner tone="error">{error}</Banner>
            </div>
          )}

          <form onSubmit={reset} className="mt-6 space-y-4">
            <div>
              <label className="label" htmlFor="code">
                Reset code
              </label>
              <input
                id="code"
                inputMode="numeric"
                maxLength={8}
                required
                className="input tabular text-center text-xl tracking-[0.35em]"
                value={code}
                onChange={(event) => setCode(event.target.value.replace(/\D/g, ''))}
              />
            </div>
            <div>
              <label className="label" htmlFor="newPassword">
                New password
              </label>
              <input
                id="newPassword"
                type="password"
                autoComplete="new-password"
                required
                className="input"
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
              />
              <p className="mt-1.5 text-xs text-navy-500 dark:text-navy-400">
                At least 10 characters, with upper and lower case, a digit and a symbol.
              </p>
            </div>
            <button type="submit" className="btn-primary w-full" disabled={busy}>
              {busy && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
              Change password
            </button>
          </form>
        </>
      )}

      <p className="mt-5 text-center text-sm">
        <Link to="/login" className="font-medium text-navy-700 hover:underline dark:text-teal-400">
          Back to sign in
        </Link>
      </p>
    </AuthShell>
  );
}
