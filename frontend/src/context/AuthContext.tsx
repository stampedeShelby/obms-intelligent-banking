import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';

import { setAccessToken, setUnauthorisedHandler } from '../api/client';
import type { Session } from '../api/types';
import { AuthContext, type AuthState } from './authContext';

/**
 * Holds the signed-in session.
 *
 * The token lives in memory only, so a page refresh signs the customer out.
 * That is deliberate for a banking client: a token in `localStorage` survives
 * refresh but is readable by any injected script.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);

  const signIn = useCallback((next: Session) => {
    setAccessToken(next.accessToken);
    setSession(next);
  }, []);

  const signOut = useCallback(() => {
    setAccessToken(null);
    setSession(null);
  }, []);

  // An expired or rejected token returns the customer to sign-in rather than
  // leaving the UI stuck in a half-authenticated state.
  useEffect(() => {
    setUnauthorisedHandler(() => setSession(null));
    return () => setUnauthorisedHandler(null);
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      session,
      signIn,
      signOut,
      hasRole: (role: string) => session?.roles.includes(role) ?? false,
      isStaff:
        session?.roles.some((role) =>
          ['ADMIN', 'BANK_STAFF', 'FRAUD_ANALYST'].includes(role),
        ) ?? false,
    }),
    [session, signIn, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
