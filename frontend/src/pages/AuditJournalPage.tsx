import { ArrowDownLeft, ArrowUpRight, Search, Undo2, X } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';

import { messageOf } from '@/api/apiClient';
import { fetchJournals } from '@/api/endpoints';
import { EmptyState, ErrorBanner, SkeletonRows } from '@/components/Feedback';
import { Pagination } from '@/components/Pagination';
import { formatInstant } from '@/lib/datetime';
import { formatMoney } from '@/lib/money';
import { useAsyncData } from '@/lib/useAsyncData';

import type { JournalFilters } from '@/types/api';

const NO_FILTERS: JournalFilters = {};

export function AuditJournalPage() {
  // Draft state is what the inputs bind to; `applied` is what the query uses, so typing
  // does not fire a request per keystroke.
  const [draft, setDraft] = useState({
    providerNpi: '',
    claimUuid: '',
    journalGroupId: '',
    postedFrom: '',
    postedTo: '',
  });
  const [applied, setApplied] = useState<JournalFilters>(NO_FILTERS);
  const [page, setPage] = useState(0);

  const { data, loading, error, reload } = useAsyncData(
    () => fetchJournals(applied, { page, size: 50 }),
    [applied, page],
  );

  function onApply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    // Empty strings are dropped by `definedParams`, so an unset field never becomes a filter.
    setApplied({
      providerNpi: draft.providerNpi.trim(),
      claimUuid: draft.claimUuid.trim(),
      journalGroupId: draft.journalGroupId.trim(),
      postedFrom: draft.postedFrom === '' ? '' : `${draft.postedFrom}T00:00:00Z`,
      postedTo: draft.postedTo === '' ? '' : `${draft.postedTo}T23:59:59Z`,
    });
    setPage(0);
  }

  function clearFilters() {
    setDraft({ providerNpi: '', claimUuid: '', journalGroupId: '', postedFrom: '', postedTo: '' });
    setApplied(NO_FILTERS);
    setPage(0);
  }

  return (
    <div className="space-y-4">
      <header>
        <h1 className="text-xl font-semibold tracking-tight text-slate-900">Ledger audit</h1>
        <p className="text-sm text-slate-600">
          Every journal row across all providers and claims, newest first. This is the only
          cross-tenant read in the system, and it is read-only without exception.
        </p>
      </header>

      <form
        onSubmit={onApply}
        className="grid gap-3 rounded-lg border border-slate-200 bg-white p-4 sm:grid-cols-2 lg:grid-cols-6"
      >
        <div className="lg:col-span-1">
          <label htmlFor="f-npi" className="block text-xs font-medium text-slate-700">
            Provider NPI
          </label>
          <input
            id="f-npi"
            value={draft.providerNpi}
            onChange={(event) => {
              setDraft((current) => ({ ...current, providerNpi: event.target.value }));
            }}
            className="mt-1 w-full rounded-md border border-slate-300 px-2.5 py-1.5 font-mono text-sm shadow-sm"
          />
        </div>

        <div className="lg:col-span-2">
          <label htmlFor="f-claim" className="block text-xs font-medium text-slate-700">
            Claim UUID
          </label>
          <input
            id="f-claim"
            value={draft.claimUuid}
            onChange={(event) => {
              setDraft((current) => ({ ...current, claimUuid: event.target.value }));
            }}
            className="mt-1 w-full rounded-md border border-slate-300 px-2.5 py-1.5 font-mono text-sm shadow-sm"
          />
        </div>

        <div>
          <label htmlFor="f-from" className="block text-xs font-medium text-slate-700">
            Posted from
          </label>
          <input
            id="f-from"
            type="date"
            value={draft.postedFrom}
            onChange={(event) => {
              setDraft((current) => ({ ...current, postedFrom: event.target.value }));
            }}
            className="mt-1 w-full rounded-md border border-slate-300 px-2.5 py-1.5 text-sm shadow-sm"
          />
        </div>

        <div>
          <label htmlFor="f-to" className="block text-xs font-medium text-slate-700">
            Posted to
          </label>
          <input
            id="f-to"
            type="date"
            value={draft.postedTo}
            onChange={(event) => {
              setDraft((current) => ({ ...current, postedTo: event.target.value }));
            }}
            className="mt-1 w-full rounded-md border border-slate-300 px-2.5 py-1.5 text-sm shadow-sm"
          />
        </div>

        <div className="flex items-end gap-2">
          <button
            type="submit"
            className="inline-flex items-center gap-1.5 rounded-md bg-sky-700 px-3 py-2 text-sm font-semibold text-white hover:bg-sky-800"
          >
            <Search className="h-4 w-4" aria-hidden="true" />
            Filter
          </button>
          <button
            type="button"
            onClick={clearFilters}
            className="inline-flex items-center gap-1.5 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            <X className="h-4 w-4" aria-hidden="true" />
            Clear
          </button>
        </div>
      </form>

      {error !== null && (
        <ErrorBanner message={messageOf(error, 'Could not load journal entries.')} onRetry={reload} />
      )}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white" aria-busy={loading}>
        {loading && <SkeletonRows rows={8} columns={6} />}

        {!loading && error === null && data !== null && data.content.length === 0 && (
          <EmptyState title="No journal entries match these filters" />
        )}

        {!loading && data !== null && data.content.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[52rem] text-sm">
                <thead className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th scope="col" className="px-4 py-2.5 font-medium">Posted</th>
                    <th scope="col" className="px-4 py-2.5 font-medium">Group</th>
                    <th scope="col" className="px-4 py-2.5 font-medium">Claim</th>
                    <th scope="col" className="px-4 py-2.5 font-medium">Account</th>
                    <th scope="col" className="px-4 py-2.5 font-medium">Direction</th>
                    <th scope="col" className="px-4 py-2.5 text-right font-medium">Amount</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {data.content.map((line, index) => (
                    <tr
                      key={`${line.journalGroupId}-${line.accountType}-${index}`}
                      className={line.reversesJournalGroupId !== null ? 'bg-violet-50/40' : undefined}
                    >
                      <td className="whitespace-nowrap px-4 py-2.5 text-xs text-slate-600">
                        {formatInstant(line.postedAt)}
                      </td>
                      <td className="px-4 py-2.5">
                        <span className="font-mono text-xs text-slate-700">
                          {line.journalGroupId.slice(0, 8)}
                        </span>
                        {/* A reversal is visibly linked to what it reverses (FR-021). */}
                        {line.reversesJournalGroupId !== null && (
                          <span className="ml-2 inline-flex items-center gap-1 rounded-full bg-violet-100 px-1.5 py-0.5 text-[0.65rem] font-medium text-status-reversed">
                            <Undo2 className="h-2.5 w-2.5" aria-hidden="true" />
                            reverses {line.reversesJournalGroupId.slice(0, 8)}
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-2.5">
                        <Link
                          to={`/audit/claims/${line.claimUuid}`}
                          className="font-mono text-xs text-sky-700 underline"
                        >
                          {line.claimUuid.slice(0, 8)}
                        </Link>
                        <span className="ml-2 font-mono text-xs text-slate-500">
                          {line.providerNpi}
                        </span>
                      </td>
                      <td className="px-4 py-2.5 text-slate-800">{line.accountType}</td>
                      <td className="px-4 py-2.5">
                        <span className="inline-flex items-center gap-1.5 text-slate-700">
                          {line.direction === 'DEBIT' ? (
                            <ArrowUpRight className="h-3.5 w-3.5 text-sky-700" aria-hidden="true" />
                          ) : (
                            <ArrowDownLeft
                              className="h-3.5 w-3.5 text-status-paid"
                              aria-hidden="true"
                            />
                          )}
                          {line.direction}
                        </span>
                      </td>
                      <td className="px-4 py-2.5 text-right font-mono tabular-nums text-slate-900">
                        {formatMoney(line.amount)}
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
