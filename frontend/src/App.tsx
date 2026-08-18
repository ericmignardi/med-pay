import axios from 'axios';
import { Activity, ShieldCheck } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, Navigate, Route, Routes } from 'react-router-dom';

interface HealthResponse {
  status: string;
}

type Probe = { state: 'loading' } | { state: 'up'; status: string } | { state: 'down' };

/**
 * Phase 0 placeholder route.
 *
 * It calls /actuator/health through the same relative path the real API client
 * will use, so rendering it green proves the whole chain the exit criterion
 * cares about: Nginx serving the SPA, Nginx proxying to the backend, and the
 * backend reaching Postgres. Phase 8 replaces this with the ten real routes.
 */
function HealthPlaceholder() {
  const [probe, setProbe] = useState<Probe>({ state: 'loading' });

  useEffect(() => {
    const controller = new AbortController();

    axios
      .get<HealthResponse>('/actuator/health', { signal: controller.signal })
      .then((response) => {
        setProbe({ state: 'up', status: response.data.status });
      })
      .catch(() => {
        setProbe({ state: 'down' });
      });

    return () => {
      controller.abort();
    };
  }, []);

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col justify-center gap-6 px-6">
      <header className="flex items-center gap-3">
        <ShieldCheck className="h-8 w-8 text-sky-700" aria-hidden="true" />
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">MedPay Ledger</h1>
          <p className="text-sm text-slate-600">Phase 0 — container baseline</p>
        </div>
      </header>

      <section
        aria-live="polite"
        className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm"
      >
        <div className="flex items-center gap-2 text-sm font-medium text-slate-700">
          <Activity className="h-4 w-4" aria-hidden="true" />
          Backend health, via the Nginx /actuator proxy
        </div>

        <p className="mt-3 font-mono text-sm">
          {probe.state === 'loading' && <span className="text-slate-500">checking…</span>}
          {probe.state === 'up' && (
            <span className="text-status-paid">&#123;&quot;status&quot;:&quot;{probe.status}&quot;&#125;</span>
          )}
          {probe.state === 'down' && (
            <span className="text-status-denied">unreachable — is the backend container healthy?</span>
          )}
        </p>
      </section>

      <p className="text-sm text-slate-500">
        Routing is live.{' '}
        <Link className="font-medium text-sky-700 underline" to="/nowhere">
          An unknown path
        </Link>{' '}
        redirects here rather than 404ing at the server, which is the SPA fallback Nginx provides.
      </p>
    </main>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HealthPlaceholder />} />
      {/* Phase 8 replaces this catch-all with the real route table and /403. */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
