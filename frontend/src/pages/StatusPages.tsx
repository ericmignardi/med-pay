import { Compass, ShieldAlert } from 'lucide-react';
import { Link } from 'react-router-dom';

function StatusShell({
  icon,
  code,
  title,
  description,
}: {
  icon: React.ReactNode;
  code: string;
  title: string;
  description: string;
}) {
  return (
    <main className="mx-auto flex min-h-[60vh] max-w-lg flex-col items-center justify-center gap-4 px-6 text-center">
      {icon}
      <p className="font-mono text-sm text-slate-500">{code}</p>
      <h1 className="text-2xl font-semibold tracking-tight text-slate-900">{title}</h1>
      <p className="text-sm text-slate-600">{description}</p>
      <Link
        to="/"
        className="rounded-md bg-sky-700 px-4 py-2 text-sm font-semibold text-white hover:bg-sky-800"
      >
        Back to your workspace
      </Link>
    </main>
  );
}

export function ForbiddenPage() {
  return (
    <StatusShell
      icon={<ShieldAlert className="h-10 w-10 text-status-denied" aria-hidden="true" />}
      code="403"
      title="You do not have access to that"
      description="Your account is signed in, but this area requires a role you do not hold. Nothing was changed."
    />
  );
}

export function NotFoundPage() {
  return (
    <StatusShell
      icon={<Compass className="h-10 w-10 text-slate-400" aria-hidden="true" />}
      code="404"
      title="Page not found"
      description="That address does not match any screen in MedPay Ledger."
    />
  );
}
