import { AlertCircle, Loader2, ShieldCheck } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom';

import { messageOf, toErrorResponse } from '@/api/apiClient';
import { useAuth } from '@/auth/useAuth';
import { PageSpinner } from '@/components/PageSpinner';

import type { Role, UserProfileResponse } from '@/types/api';

/** Where a role lands when it signs in without a specific destination in mind. */
const LANDING_BY_ROLE: Record<Role, string> = {
  CLAIMS_PROCESSOR: '/claims',
  MEDICAL_REVIEWER: '/review',
  AUDITOR: '/audit/journals',
};

function landingFor(profile: UserProfileResponse): string {
  for (const role of ['CLAIMS_PROCESSOR', 'MEDICAL_REVIEWER', 'AUDITOR'] as const) {
    if (profile.roles.includes(role)) {
      return LANDING_BY_ROLE[role];
    }
  }
  return '/403';
}

interface LocationState {
  from?: { pathname?: string };
}

export function LoginPage() {
  const { user, initializing, signIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const expired = searchParams.get('expired') === '1';

  if (initializing) {
    return <PageSpinner label="Restoring your session" />;
  }

  if (user !== null) {
    return <Navigate to={landingFor(user)} replace />;
  }

  async function handleSubmit() {
    setSubmitting(true);
    setError(null);
    setFieldErrors({});

    try {
      const profile = await signIn(email, password);

      // FR-027: a deep link that bounced through /login returns the user where they aimed.
      const state = location.state as LocationState | null;
      const target = state?.from?.pathname ?? landingFor(profile);
      navigate(target, { replace: true });
    } catch (caught) {
      const envelope = toErrorResponse(caught);
      if (envelope?.fieldErrors != null) {
        setFieldErrors(
          Object.fromEntries(envelope.fieldErrors.map((detail) => [detail.field, detail.message])),
        );
      }
      setError(messageOf(caught, 'Sign-in failed. Check your email and password.'));
    } finally {
      setSubmitting(false);
    }
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void handleSubmit();
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-md flex-col justify-center gap-6 px-6 py-10">
      <header className="flex items-center gap-3">
        <ShieldCheck className="h-8 w-8 text-sky-700" aria-hidden="true" />
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900">MedPay Ledger</h1>
          <p className="text-sm text-slate-600">Sign in to continue</p>
        </div>
      </header>

      {expired && (
        <div
          role="status"
          className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900"
        >
          Your session expired. Sign in again to pick up where you left off.
        </div>
      )}

      <form
        onSubmit={onSubmit}
        noValidate
        className="space-y-4 rounded-lg border border-slate-200 bg-white p-6 shadow-sm"
      >
        <div>
          <label htmlFor="email" className="block text-sm font-medium text-slate-800">
            Email
          </label>
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="username"
            required
            value={email}
            disabled={submitting}
            onChange={(event) => {
              setEmail(event.target.value);
            }}
            aria-invalid={fieldErrors.email !== undefined}
            aria-describedby={fieldErrors.email !== undefined ? 'email-error' : undefined}
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm disabled:bg-slate-50"
          />
          {fieldErrors.email !== undefined && (
            <p id="email-error" className="mt-1 text-xs text-status-denied">
              {fieldErrors.email}
            </p>
          )}
        </div>

        <div>
          <label htmlFor="password" className="block text-sm font-medium text-slate-800">
            Password
          </label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            disabled={submitting}
            onChange={(event) => {
              setPassword(event.target.value);
            }}
            aria-invalid={fieldErrors.password !== undefined}
            aria-describedby={fieldErrors.password !== undefined ? 'password-error' : undefined}
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm disabled:bg-slate-50"
          />
          {fieldErrors.password !== undefined && (
            <p id="password-error" className="mt-1 text-xs text-status-denied">
              {fieldErrors.password}
            </p>
          )}
        </div>

        <div aria-live="assertive">
          {error !== null && (
            <p
              role="alert"
              className="flex items-start gap-2 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-900"
            >
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
              {error}
            </p>
          )}
        </div>

        <button
          type="submit"
          disabled={submitting}
          className="inline-flex w-full items-center justify-center gap-2 rounded-md bg-sky-700 px-4 py-2 text-sm font-semibold text-white hover:bg-sky-800 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {submitting && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
          {submitting ? 'Signing in' : 'Sign in'}
        </button>
      </form>
    </main>
  );
}
