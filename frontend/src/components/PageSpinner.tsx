import { Loader2 } from 'lucide-react';

export function PageSpinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="flex min-h-[40vh] items-center justify-center" role="status" aria-live="polite">
      <Loader2 className="h-5 w-5 animate-spin text-sky-700" aria-hidden="true" />
      <span className="ml-3 text-sm text-slate-600">{label}…</span>
    </div>
  );
}
