import { ArrowLeft, CircleDot } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';

import { messageOf, statusOf } from '@/api/apiClient';
import { fetchClaimAudit } from '@/api/endpoints';
import { ClaimSummaryCard } from '@/components/ClaimSummaryCard';
import { ErrorBanner, NotFoundPanel, SkeletonRows } from '@/components/Feedback';
import { JournalGroups } from '@/components/JournalGroups';
import { formatInstant } from '@/lib/datetime';
import { useAsyncData } from '@/lib/useAsyncData';

export function AuditClaimHistoryPage() {
  const { claimUuid = '' } = useParams();
  const { data, loading, error, reload } = useAsyncData(
    () => fetchClaimAudit(claimUuid),
    [claimUuid],
  );

  return (
    <div className="space-y-4">
      <Link
        to="/audit/journals"
        className="inline-flex items-center gap-1.5 text-sm font-medium text-sky-700 hover:underline"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        Back to the ledger
      </Link>

      {loading && (
        <div className="overflow-hidden rounded-lg border border-slate-200 bg-white" aria-busy="true">
          <SkeletonRows rows={8} columns={4} />
        </div>
      )}

      {!loading && statusOf(error) === 404 && (
        <NotFoundPanel
          title="Claim not found"
          description="No claim exists with that identifier."
        />
      )}

      {!loading && error !== null && statusOf(error) !== 404 && (
        <ErrorBanner message={messageOf(error, 'Could not load this claim history.')} onRetry={reload} />
      )}

      {!loading && data !== null && (
        <>
          <ClaimSummaryCard claim={data.claim} />

          <section>
            <h3 className="mb-2 text-sm font-semibold text-slate-900">Ledger entries</h3>
            <JournalGroups groups={data.journalGroups} />
          </section>

          <section className="overflow-hidden rounded-lg border border-slate-200 bg-white">
            <h3 className="border-b border-slate-200 px-5 py-3 text-sm font-semibold text-slate-900">
              Event stream
            </h3>
            {data.events.length === 0 ? (
              <p className="px-5 py-6 text-sm text-slate-600">No events recorded.</p>
            ) : (
              <ol className="divide-y divide-slate-100">
                {data.events.map((event) => (
                  <li key={event.eventUuid} className="flex flex-wrap items-center gap-x-4 gap-y-1 px-5 py-3 text-sm">
                    <CircleDot className="h-3.5 w-3.5 shrink-0 text-sky-700" aria-hidden="true" />
                    <span className="font-medium text-slate-900">
                      {event.eventType.replace(/_/g, ' ').toLowerCase()}
                    </span>
                    <span className="text-slate-600">{formatInstant(event.createdAt)}</span>
                    <span
                      className={`ml-auto text-xs ${
                        event.publishedAt === null ? 'text-status-flagged' : 'text-slate-500'
                      }`}
                    >
                      {event.publishedAt === null
                        ? 'pending dispatch'
                        : `dispatched ${formatInstant(event.publishedAt)}`}
                    </span>
                  </li>
                ))}
              </ol>
            )}
          </section>
        </>
      )}
    </div>
  );
}
