import { AlertCircle, Inbox, RefreshCw } from 'lucide-react';
import type { ReactNode } from 'react';

/** Skeleton rows. `aria-hidden` because the live region announces "loading" instead. */
export function SkeletonRows({ rows = 5, columns = 4 }: { rows?: number; columns?: number }) {
  return (
    <div className="divide-y divide-slate-200" aria-hidden="true">
      {Array.from({ length: rows }, (_, rowIndex) => (
        <div key={rowIndex} className="flex gap-4 px-4 py-3">
          {Array.from({ length: columns }, (_, columnIndex) => (
            <div
              key={columnIndex}
              className="h-4 flex-1 animate-pulse rounded bg-slate-200"
              style={{ animationDelay: `${(rowIndex * columns + columnIndex) * 40}ms` }}
            />
          ))}
        </div>
      ))}
    </div>
  );
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center gap-3 px-6 py-14 text-center">
      <Inbox className="h-8 w-8 text-slate-400" aria-hidden="true" />
      <p className="text-sm font-medium text-slate-800">{title}</p>
      {description !== undefined && <p className="max-w-md text-sm text-slate-600">{description}</p>}
      {action}
    </div>
  );
}

/**
 * The retry banner every list route uses. `role="alert"` so a failure is announced rather
 * than silently replacing the rows.
 */
export function ErrorBanner({
  message,
  onRetry,
  children,
}: {
  message: string;
  onRetry?: () => void;
  children?: ReactNode;
}) {
  return (
    <div
      role="alert"
      className="flex flex-col gap-3 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-900"
    >
      <div className="flex items-start gap-2">
        <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-status-denied" aria-hidden="true" />
        <div className="flex-1">
          <p className="font-medium">{message}</p>
          {children}
        </div>
      </div>
      {onRetry !== undefined && (
        <button
          type="button"
          onClick={onRetry}
          className="inline-flex w-fit items-center gap-1.5 rounded-md border border-red-300 bg-white px-3 py-1.5 font-medium text-red-900 hover:bg-red-100"
        >
          <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
          Try again
        </button>
      )}
    </div>
  );
}

export function NotFoundPanel({ title, description }: { title: string; description: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-8 text-center">
      <p className="text-base font-semibold text-slate-900">{title}</p>
      <p className="mt-2 text-sm text-slate-600">{description}</p>
    </div>
  );
}
