import { useState } from 'react';
import { Link } from 'react-router-dom';

import { messageOf } from '@/api/apiClient';
import { fetchReviewQueue } from '@/api/endpoints';
import { EmptyState, ErrorBanner, SkeletonRows } from '@/components/Feedback';
import { Pagination } from '@/components/Pagination';
import { StatusBadge } from '@/components/StatusBadge';
import { formatInstant, formatServiceDate } from '@/lib/datetime';
import { formatMoney } from '@/lib/money';
import { useAsyncData } from '@/lib/useAsyncData';

export function ReviewQueuePage() {
  const [page, setPage] = useState(0);
  const { data, loading, error, reload } = useAsyncData(() => fetchReviewQueue({ page }), [page]);

  return (
    <div className="space-y-4">
      <header>
        <h1 className="text-xl font-semibold tracking-tight text-slate-900">Review queue</h1>
        <p className="text-sm text-slate-600">
          Claims at or above $25,000.00, oldest first. The queue is shared — it is not assigned
          per reviewer, and two reviewers acting on the same claim are resolved by optimistic
          locking.
        </p>
      </header>

      {error !== null && (
        <ErrorBanner message={messageOf(error, 'Could not load the queue.')} onRetry={reload} />
      )}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white" aria-busy={loading}>
        {loading && <SkeletonRows rows={5} columns={5} />}

        {!loading && error === null && data !== null && data.content.length === 0 && (
          <EmptyState
            title="Queue is clear"
            description="No claims are waiting for review."
          />
        )}

        {!loading && data !== null && data.content.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[42rem] text-sm">
                <thead className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th scope="col" className="px-4 py-2.5 font-medium">Claim</th>
                    <th scope="col" className="px-4 py-2.5 font-medium">Provider</th>
                    <th scope="col" className="px-4 py-2.5 font-medium">Service date</th>
                    <th scope="col" className="px-4 py-2.5 text-right font-medium">Billed</th>
                    <th scope="col" className="px-4 py-2.5 text-right font-medium">Allowed</th>
                    <th scope="col" className="px-4 py-2.5 font-medium">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {data.content.map((claim) => (
                    <tr key={claim.claimUuid} className="hover:bg-slate-50">
                      <td className="px-4 py-3">
                        <Link
                          to={`/review/${claim.claimUuid}`}
                          className="font-mono text-xs font-medium text-sky-700 underline"
                        >
                          {claim.claimUuid.slice(0, 8)}
                        </Link>
                        <p className="mt-0.5 text-xs text-slate-500">
                          waiting since {formatInstant(claim.submittedAt)}
                        </p>
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-slate-700">
                        {claim.providerNpi}
                      </td>
                      <td className="px-4 py-3 text-slate-700">
                        {formatServiceDate(claim.serviceDate)}
                      </td>
                      <td className="px-4 py-3 text-right font-mono tabular-nums">
                        {formatMoney(claim.billedAmount)}
                      </td>
                      <td className="px-4 py-3 text-right font-mono tabular-nums">
                        {formatMoney(claim.allowedAmount)}
                      </td>
                      <td className="px-4 py-3">
                        <StatusBadge status={claim.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <Pagination
              page={data.page}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              first={data.first}
              last={data.last}
              onPageChange={setPage}
            />
          </>
        )}
      </div>
    </div>
  );
}
