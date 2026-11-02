import { useContext } from 'react';

import { AuthContext, type AuthState } from './authContext';

/**
 * Reads the signed-in session.
 *
 * @throws when called outside an AuthProvider, which is a programming error
 *         rather than a runtime condition worth handling
 */
export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider');
  }
  return context;
}
