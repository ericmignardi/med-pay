import { ArrowLeft } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';

import { messageOf, statusOf } from '@/api/apiClient';
import { fetchOwnClaim } from '@/api/endpoints';
import { ClaimSummaryCard } from '@/components/ClaimSummaryCard';
import { ErrorBanner, NotFoundPanel, SkeletonRows } from '@/components/Feedback';
import { JournalGroups } from '@/components/JournalGroups';
import { useAsyncData } from '@/lib/useAsyncData';

export function ClaimDetailPage() {
  const { claimUuid = '' } = useParams();
  const { data, loading, error, reload } = useAsyncData(
    () => fetchOwnClaim(claimUuid),
    [claimUuid],
  );

  return (
    <div className="space-y-4">
      <Link
        to="/claims"
        className="inline-flex items-center gap-1.5 text-sm font-medium text-sky-700 hover:underline"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        Back to your claims
      </Link>

      {loading && (
        <div className="overflow-hidden rounded-lg border border-slate-200 bg-white" aria-busy="true">
          <SkeletonRows rows={6} columns={4} />
        </div>
      )}

      {/* A claim belonging to another processor is a 404, not a 403 — existence is
          not disclosed (PRD §5.3), so the panel says the same thing either way. */}
      {!loading && statusOf(error) === 404 && (
        <NotFoundPanel
          title="Claim not found"
          description="No claim with that identifier was submitted by you."
        />
      )}

      {!loading && error !== null && statusOf(error) !== 404 && (
        <ErrorBanner message={messageOf(error, 'Could not load this claim.')} onRetry={reload} />
      )}

      {!loading && data !== null && (
        <>
          <ClaimSummaryCard claim={data} />

          <section>
            <h3 className="mb-2 text-sm font-semibold text-slate-900">Ledger entries</h3>
            <JournalGroups groups={data.journalGroups} />
          </section>
        </>
      )}
    </div>
  );
}
