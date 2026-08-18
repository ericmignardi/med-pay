import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';

import { useAuth } from '@/auth/useAuth';
import { PageSpinner } from '@/components/PageSpinner';

import type { Role } from '@/types/api';

interface ProtectedRouteProps {
  children: ReactNode;
  requiredRole?: Role;
}

/**
 * FR-027. Client-side guarding is **UX only**. Every rule here is independently enforced
 * server-side (PRD §2.3), and `route-protection.spec.ts` asserts that a direct `fetch`
 * bypassing this component is still rejected with a `403` — a suite that only checked the
 * redirect would pass against a server with no authorization at all.
 */
export function ProtectedRoute({ children, requiredRole }: ProtectedRouteProps) {
  const { user, initializing } = useAuth();
  const location = useLocation();

  // Redirecting before rehydration settles would bounce a signed-in user to /login on
  // every hard refresh.
  if (initializing) {
    return <PageSpinner label="Restoring your session" />;
  }

  if (user === null) {
    // `from` is what sends the user back where they were aiming after signing in.
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  if (requiredRole !== undefined && !user.roles.includes(requiredRole)) {
    return <Navigate to="/403" replace />;
  }

  return <>{children}</>;
}
