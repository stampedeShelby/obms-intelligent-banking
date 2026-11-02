import { createContext } from 'react';

import type { Session } from '../api/types';

export interface AuthState {
  session: Session | null;
  signIn: (session: Session) => void;
  signOut: () => void;
  hasRole: (role: string) => boolean;
  isStaff: boolean;
}

/**
 * The auth context object.
 *
 * Kept in its own module, separate from the provider component and the hook,
 * so that each file exports a single kind of thing. React Fast Refresh only
 * works on modules that export components alone.
 */
export const AuthContext = createContext<AuthState | null>(null);
