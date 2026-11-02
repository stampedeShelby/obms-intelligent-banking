import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, Loader2, ShieldCheck } from 'lucide-react';

import { errorMessage } from '../api/client';
import { auth } from '../api/endpoints';
import { Logo } from '../components/AppLayout';
import { Banner, SimulationNotice } from '../components/ui';
import { useAuth } from '../context/useAuth';

type Stage = 'credentials' | 'otp';

/**
 * Sign-in: password, then the one-time code.
 *
 * The two stages are separate on purpose. The first returns a pre-auth token
 * that is accepted by nothing but the verification endpoint, so this screen
 * cannot be short-circuited by a client that ignores the second stage.
 */
export default function LoginPage() {
  const navigate = useNavigate();
  const { signIn } = useAuth();

  const [stage, setStage] = useState<Stage>('credentials');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [preAuthToken, setPreAuthToken] = useState('');
  const [maskedDestination, setMaskedDestination] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);

  async function submitCredentials(event: React.FormEvent) {
    event.preventDefault();
    setError('');
    setBusy(true);
    try {
      const response = await auth.login(username.trim(), password);

      if (!response.otpRequired && response.accessToken) {
        signIn({
          accessToken: response.accessToken,
          expiresInSeconds: response.expiresInSeconds ?? 1800,
          username: username.trim(),
          displayName: username.trim(),
          roles: ['CUSTOMER'],
          customerId: null,
          kycStatus: null,
        });
        navigate('/app', { replace: true });
        return;
      }

      setPreAuthToken(response.preAuthToken ?? '');
      setMaskedDestination(response.maskedDestination ?? '');
      setNotice(response.message);
      setStage('otp');
    } catch (caught) {
      setError(errorMessage(caught, 'Could not sign in.'));
    } finally {
      setBusy(false);
    }
  }

  async function submitOtp(event: React.FormEvent) {
    event.preventDefault();
    setError('');
    setBusy(true);
    try {
      const session = await auth.verifyOtp(preAuthToken, code.trim());
      signIn(session);
      navigate('/app', { replace: true });
    } catch (caught) {
      setError(errorMessage(caught, 'That code was not accepted.'));
    } finally {
      setBusy(false);
    }
  }

  async function resend() {
    setError('');
    setNotice('');
    try {
      await auth.resendOtp(preAuthToken);
      setNotice('A new code has been sent.');
    } catch (caught) {
      setError(errorMessage(caught, 'Could not resend the code.'));
    }
  }

  return (
    <AuthShell>
      {stage === 'credentials' ? (
        <>
          <h1 className="text-2xl font-bold text-navy-900 dark:text-white">Sign in</h1>
          <p className="mt-1.5 text-sm text-navy-600 dark:text-navy-300">
            Enter your details. We will send a one-time code to finish signing in.
          </p>

          {error && (
            <div className="mt-4">
              <Banner tone="error">{error}</Banner>
            </div>
          )}

          <form onSubmit={submitCredentials} className="mt-6 space-y-4">
            <div>
              <label className="label" htmlFor="username">
                Email
              </label>
              <input
                id="username"
                type="email"
                autoComplete="username"
                required
                className="input"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                placeholder="you@example.com"
              />
            </div>

            <div>
              <label className="label" htmlFor="password">
                Password
              </label>
              <input
                id="password"
                type="password"
                autoComplete="current-password"
                required
                className="input"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="••••••••••"
              />
            </div>

            <button type="submit" className="btn-primary w-full" disabled={busy}>
              {busy && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
              Continue
            </button>
          </form>

          <div className="mt-5 flex items-center justify-between text-sm">
            <Link to="/forgot-password" className="font-medium text-navy-700 hover:underline dark:text-teal-400">
              Forgot password?
            </Link>
            <Link to="/register" className="font-medium text-navy-700 hover:underline dark:text-teal-400">
              Open an account
            </Link>
          </div>
        </>
      ) : (
        <>
          <button
            type="button"
            className="btn-ghost -ml-2 mb-2 px-2 text-sm"
            onClick={() => {
              setStage('credentials');
              setCode('');
              setError('');
            }}
          >
            <ArrowLeft className="h-4 w-4" aria-hidden />
            Back
          </button>

          <div className="mb-3 inline-flex rounded-xl bg-navy-900 p-2.5 dark:bg-teal-500">
            <ShieldCheck className="h-5 w-5 text-white dark:text-navy-950" aria-hidden />
          </div>
          <h1 className="text-2xl font-bold text-navy-900 dark:text-white">
            Enter your verification code
          </h1>
          <p className="mt-1.5 text-sm text-navy-600 dark:text-navy-300">
            {maskedDestination
              ? `We sent a 6-digit code to ${maskedDestination}. It expires in 5 minutes.`
              : 'We sent a 6-digit code. It expires in 5 minutes.'}
          </p>

          {notice && (
            <div className="mt-4">
              <Banner tone="info">{notice}</Banner>
            </div>
          )}
          {error && (
            <div className="mt-4">
              <Banner tone="error">{error}</Banner>
            </div>
          )}

          <form onSubmit={submitOtp} className="mt-6 space-y-4">
            <div>
              <label className="label" htmlFor="code">
                Verification code
              </label>
              <input
                id="code"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={8}
                required
                className="input tabular text-center text-2xl tracking-[0.4em]"
                value={code}
                onChange={(event) => setCode(event.target.value.replace(/\D/g, ''))}
                placeholder="______"
              />
            </div>

            <button type="submit" className="btn-primary w-full" disabled={busy || code.length < 4}>
              {busy && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
              Verify and sign in
            </button>
          </form>

          <button type="button" className="btn-ghost mt-4 w-full text-sm" onClick={resend}>
            Send a new code
          </button>

          <div className="mt-5 rounded-xl bg-navy-50 p-3 text-xs text-navy-600 dark:bg-navy-950 dark:text-navy-400">
            Running locally without an email server? Start the backend with
            <code className="mx-1 rounded bg-white px-1 py-0.5 font-mono dark:bg-navy-900">
              OBMS_OTP_DEBUG=true
            </code>
            and the code is written to the application log.
          </div>
        </>
      )}
    </AuthShell>
  );
}

/** Shared frame for the unauthenticated screens. */
export function AuthShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen">
      <div className="hidden w-1/2 flex-col justify-between bg-navy-900 p-10 lg:flex">
        <Link to="/" className="flex items-center gap-2.5">
          <Logo />
          <span className="text-lg font-bold tracking-tight text-white">OBMS</span>
        </Link>
        <div>
          <h2 className="max-w-md text-3xl font-bold leading-tight text-white">
            Banking that shows its reasoning.
          </h2>
          <p className="mt-4 max-w-md text-navy-200">
            Every risk score comes with the reason behind it. Every projection states its
            assumptions. Every figure traces back to a transaction you can open.
          </p>
        </div>
        <p className="text-xs text-navy-400">
          Simulated banking environment built as an academic project.
        </p>
      </div>

      <div className="flex w-full items-center justify-center px-6 py-12 lg:w-1/2">
        <div className="w-full max-w-sm">
          <Link to="/" className="mb-8 flex items-center gap-2.5 lg:hidden">
            <Logo />
            <span className="text-lg font-bold tracking-tight text-navy-900 dark:text-white">
              OBMS
            </span>
          </Link>
          {children}
          <div className="mt-8">
            <SimulationNotice />
          </div>
        </div>
      </div>
    </div>
  );
}
