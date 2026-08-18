import { ArrowLeft, Ban, CheckCircle2, Loader2 } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';

import { errorCodeOf, messageOf, statusOf } from '@/api/apiClient';
import { approveClaim, denyClaim, fetchFlaggedClaim } from '@/api/endpoints';
import { ClaimSummaryCard } from '@/components/ClaimSummaryCard';
import { ErrorBanner, NotFoundPanel, SkeletonRows } from '@/components/Feedback';
import { useAsyncData } from '@/lib/useAsyncData';

import type { DenialReason } from '@/types/api';

const DENIAL_REASONS: DenialReason[] = [
  'NOT_MEDICALLY_NECESSARY',
  'SERVICE_NOT_COVERED',
  'INSUFFICIENT_DOCUMENTATION',
  'DUPLICATE_ENCOUNTER',
  'OUT_OF_NETWORK',
];

/**
 * FR-029 requires the two `409`s to read differently, because they mean opposite things:
 * self-approval is a permanent separation-of-duties refusal and retrying will never help,
 * while a concurrent modification means someone else got there first and the right move is
 * to reload.
 */
function decisionErrorMessage(error: unknown): string {
  switch (errorCodeOf(error)) {
    case 'SELF_APPROVAL_FORBIDDEN':
      return 'You submitted this claim, so you cannot review it. Separation of duties requires a different reviewer — this will not succeed on retry.';
    case 'CONCURRENT_MODIFICATION':
      return 'Another reviewer decided this claim first. Reload to see the current state.';
    case 'ILLEGAL_STATE_TRANSITION':
      return 'This claim is no longer awaiting review, so it cannot be approved or denied.';
    default:
      return messageOf(error, 'The decision could not be recorded.');
  }
}

export function ReviewDetailPage() {
  const { claimUuid = '' } = useParams();
  const navigate = useNavigate();

  const { data, loading, error, reload } = useAsyncData(
    () => fetchFlaggedClaim(claimUuid),
    [claimUuid],
  );

  const [note, setNote] = useState('');
  const [denialReason, setDenialReason] = useState<DenialReason>('NOT_MEDICALLY_NECESSARY');
  const [mode, setMode] = useState<'approve' | 'deny'>('approve');
  const [deciding, setDeciding] = useState(false);
  const [decisionError, setDecisionError] = useState<unknown>(null);

  async function decide() {
    setDeciding(true);
    setDecisionError(null);
    try {
      if (mode === 'approve') {
        await approveClaim(claimUuid, { note });
      } else {
        await denyClaim(claimUuid, { reason: denialReason, note });
      }
      navigate('/review', { replace: true });
    } catch (caught) {
      setDecisionError(caught);
    } finally {
      setDeciding(false);
    }
  }

  const denialNoteMissing = mode === 'deny' && note.trim() === '';

  return (
    <div className="space-y-4">
      <Link
        to="/review"
        className="inline-flex items-center gap-1.5 text-sm font-medium text-sky-700 hover:underline"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        Back to the queue
      </Link>

      {loading && (
        <div className="overflow-hidden rounded-lg border border-slate-200 bg-white" aria-busy="true">
          <SkeletonRows rows={6} columns={4} />
        </div>
      )}

      {!loading && statusOf(error) === 404 && (
        <NotFoundPanel
          title="Not in the review queue"
          description="This claim is not awaiting review — it may already have been decided by another reviewer."
        />
      )}

      {!loading && error !== null && statusOf(error) !== 404 && (
        <ErrorBanner message={messageOf(error, 'Could not load this claim.')} onRetry={reload} />
      )}

      {!loading && data !== null && (
        <>
          <ClaimSummaryCard claim={data} />

          <section className="rounded-lg border border-slate-200 bg-white p-5">
            <h3 className="text-sm font-semibold text-slate-900">Decision</h3>
            <p className="mt-1 text-sm text-slate-600">
              Approving posts a balanced ledger pair and pays the provider. Denying records a
              reason and posts nothing.
            </p>

            <fieldset className="mt-4" disabled={deciding}>
              <legend className="sr-only">Decision type</legend>
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  onClick={() => {
                    setMode('approve');
                  }}
                  aria-pressed={mode === 'approve'}
                  className={`inline-flex items-center gap-1.5 rounded-md border px-3 py-2 text-sm font-medium ${
                    mode === 'approve'
                      ? 'border-green-300 bg-green-50 text-status-paid'
                      : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
                  }`}
                >
                  <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
                  Approve
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setMode('deny');
                  }}
                  aria-pressed={mode === 'deny'}
                  className={`inline-flex items-center gap-1.5 rounded-md border px-3 py-2 text-sm font-medium ${
                    mode === 'deny'
                      ? 'border-red-300 bg-red-50 text-status-denied'
                      : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
                  }`}
                >
                  <Ban className="h-4 w-4" aria-hidden="true" />
                  Deny
                </button>
              </div>

              {mode === 'deny' && (
                <div className="mt-4">
                  <label
                    htmlFor="denialReason"
                    className="block text-sm font-medium text-slate-800"
                  >
                    Denial reason
                  </label>
                  <select
                    id="denialReason"
                    value={denialReason}
                    onChange={(event) => {
                      setDenialReason(event.target.value as DenialReason);
                    }}
                    className="mt-1 w-full max-w-sm rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm"
                  >
                    {DENIAL_REASONS.map((reason) => (
                      <option key={reason} value={reason}>
                        {reason.replace(/_/g, ' ').toLowerCase()}
                      </option>
                    ))}
                  </select>
                </div>
              )}

              <div className="mt-4">
                <label htmlFor="note" className="block text-sm font-medium text-slate-800">
                  Note {mode === 'deny' && <span aria-hidden="true">*</span>}
                </label>
                <textarea
                  id="note"
                  rows={3}
                  maxLength={1000}
                  required={mode === 'deny'}
                  value={note}
                  onChange={(event) => {
                    setNote(event.target.value);
                  }}
                  aria-describedby="note-help"
                  className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm disabled:bg-slate-50"
                />
                <p id="note-help" className="mt-1 text-xs text-slate-500">
                  {mode === 'deny'
                    ? 'Required — a denial must be justified.'
                    : 'Optional for an approval.'}
                </p>
              </div>
            </fieldset>

            <div aria-live="assertive" className="mt-4">
              {decisionError !== null && <ErrorBanner message={decisionErrorMessage(decisionError)} />}
            </div>

            <div className="mt-4 flex justify-end">
              <button
                type="button"
                disabled={deciding || denialNoteMissing}
                onClick={() => {
                  void decide();
                }}
                className={`inline-flex items-center gap-2 rounded-md px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60 ${
                  mode === 'approve' ? 'bg-green-700 hover:bg-green-800' : 'bg-red-700 hover:bg-red-800'
                }`}
              >
                {deciding && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
                {mode === 'approve' ? 'Approve and pay' : 'Deny claim'}
              </button>
            </div>
          </section>
        </>
      )}
    </div>
  );
}
