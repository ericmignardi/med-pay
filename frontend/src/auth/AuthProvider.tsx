import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';

import { PROFILE_KEY, TOKEN_KEY, clearSession, readToken } from '@/api/apiClient';
import { fetchProfile, login } from '@/api/endpoints';
import { AuthContext, type AuthState } from '@/auth/authContext';

import type { Role, UserProfileResponse } from '@/types/api';

/**
 * Session state lives in `sessionStorage`, not `localStorage`: a JWT that outlives the
 * browser tab is a longer-lived credential than this system wants (NFR-002).
 *
 * The cached profile is only a first paint optimisation. On every mount the provider
 * re-validates against `GET /auth/me`, so a token revoked or expired server-side cannot
 * leave a stale signed-in shell on screen.
 */

function readCachedProfile(): UserProfileResponse | null {
  const raw = sessionStorage.getItem(PROFILE_KEY);
  if (raw === null) {
    return null;
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    if (typeof parsed !== 'object' || parsed === null) {
      return null;
    }
    const candidate = parsed as Partial<UserProfileResponse>;
    return typeof candidate.userUuid === 'string' && Array.isArray(candidate.roles)
      ? (candidate as UserProfileResponse)
      : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfileResponse | null>(() => readCachedProfile());
  const [initializing, setInitializing] = useState(true);

  useEffect(() => {
    let cancelled = false;

    if (readToken() === null) {
      clearSession();
      setUser(null);
      setInitializing(false);
      return () => {
        cancelled = true;
      };
    }

    fetchProfile()
      .then((profile) => {
        if (cancelled) {
          return;
        }
        sessionStorage.setItem(PROFILE_KEY, JSON.stringify(profile));
        setUser(profile);
      })
      .catch(() => {
        // A 401 is already handled by the interceptor; anything else means we cannot
        // trust the cached profile either.
        if (!cancelled) {
          clearSession();
          setUser(null);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setInitializing(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const signIn = useCallback(async (email: string, password: string) => {
    const response = await login({ email, password });

    sessionStorage.setItem(TOKEN_KEY, response.token);
    const profile: UserProfileResponse = {
      userUuid: response.userUuid,
      email: response.email,
      fullName: response.fullName,
      roles: response.roles,
    };
    sessionStorage.setItem(PROFILE_KEY, JSON.stringify(profile));
    setUser(profile);
    return profile;
  }, []);

  const signOut = useCallback(() => {
    clearSession();
    setUser(null);
  }, []);

  const hasRole = useCallback((role: Role) => user?.roles.includes(role) ?? false, [user]);

  const value = useMemo<AuthState>(
    () => ({ user, initializing, signIn, signOut, hasRole }),
    [user, initializing, signIn, signOut, hasRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
