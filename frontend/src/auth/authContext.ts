import { createContext } from 'react';

import type { Role, UserProfileResponse } from '@/types/api';

export interface AuthState {
  /** null once rehydration has finished and nobody is signed in. */
  user: UserProfileResponse | null;
  /** True until the `GET /auth/me` rehydration settles, so guards do not redirect early. */
  initializing: boolean;
  signIn: (email: string, password: string) => Promise<UserProfileResponse>;
  signOut: () => void;
  hasRole: (role: Role) => boolean;
}

/**
 * Lives in its own module so `AuthProvider.tsx` exports a component and nothing else —
 * mixing a context object into a component module defeats React Fast Refresh.
 */
export const AuthContext = createContext<AuthState | null>(null);
