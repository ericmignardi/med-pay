import { Plus } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';

import { messageOf } from '@/api/apiClient';
import { fetchOwnClaims } from '@/api/endpoints';
import { EmptyState, ErrorBanner, SkeletonRows } from '@/components/Feedback';
import { Pagination } from '@/components/Pagination';
import { StatusBadge } from '@/components/StatusBadge';
import { formatInstant, formatServiceDate } from '@/lib/datetime';
import { formatMoney } from '@/lib/money';
import { useAsyncData } from '@/lib/useAsyncData';

import type { ClaimStatus } from '@/types/api';

const STATUS_OPTIONS: ClaimStatus[] = [
  'RECEIVED',
  'VALIDATED',
  'FLAGGED_REVIEW',
  'ADJUDICATED',
  'PAID',
  'DENIED',
  'REVERSED',
];

export function ClaimListPage() {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<ClaimStatus | ''>('');

  const { data, loading, error, reload } = useAsyncData(
    () => fetchOwnClaims(status === '' ? { page } : { page, status }),
    [page, status],
  );

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight text-slate-900">Your claims</h1>
          <p className="text-sm text-slate-600">
            Claims you submitted. Other processors&apos; claims are not visible here or through
            the API.
          </p>
        </div>

        <div className="flex items-end gap-3">
          <div>
            <label htmlFor="status-filter" className="block text-xs font-medium text-slate-700">
              Status
            </label>
            <select
              id="status-filter"
              value={status}
              onChange={(event) => {
                setStatus(event.target.value as ClaimStatus | '');
                setPage(0);
              }}
              className="mt-1 rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm shadow-sm"
            >
              <option value="">All statuses</option>
              {STATUS_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {option.replace(/_/g, ' ')}
                </option>
              ))}
            </select>
          </div>

          <Link
            to="/claims/new"
            className="inline-flex items-center gap-1.5 rounded-md bg-sky-700 px-3 py-2 text-sm font-semibold text-white hover:bg-sky-800"
          >
            <Plus className="h-4 w-4" aria-hidden="true" />
            New claim
          </Link>
        </div>
      </div>

      {error !== null && (
        <ErrorBanner message={messageOf(error, 'Could not load your claims.')} onRetry={reload} />
      )}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white" aria-busy={loading}>
        {loading && <SkeletonRows rows={5} columns={5} />}

        {!loading && error === null && data !== null && data.content.length === 0 && (
          <EmptyState
            title="No claims submitted yet"
            description="Submit your first claim to see it adjudicate against the fee schedule."
            action={
              <Link
                to="/claims/new"
                className="inline-flex items-center gap-1.5 rounded-md bg-sky-700 px-3 py-2 text-sm font-semibold text-white hover:bg-sky-800"
              >
                <Plus className="h-4 w-4" aria-hidden="true" />
                New claim
              </Link>
            }
          />
        )}

        {!loading && data !== null && data.content.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[44rem] text-sm">
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
                          to={`/claims/${claim.claimUuid}`}
                          className="font-mono text-xs font-medium text-sky-700 underline"
                        >
                          {claim.claimUuid.slice(0, 8)}
                        </Link>
                        <p className="mt-0.5 text-xs text-slate-500">
                          {claim.lineCount} {claim.lineCount === 1 ? 'line' : 'lines'} ·{' '}
                          {formatInstant(claim.submittedAt)}
                        </p>
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-slate-700">
                        {claim.providerNpi}
                      </td>
                      <td className="px-4 py-3 text-slate-700">
                        {formatServiceDate(claim.serviceDate)}
                      </td>
                      <td className="px-4 py-3 text-right font-mono tabular-nums text-slate-900">
                        {formatMoney(claim.billedAmount)}
                      </td>
                      <td className="px-4 py-3 text-right font-mono tabular-nums text-slate-900">
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
