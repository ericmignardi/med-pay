import { AlertCircle, Check, Loader2, Plus, Trash2 } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { detailString, errorCodeOf, messageOf, toErrorResponse } from '@/api/apiClient';
import { fetchFeeSchedules, submitClaim } from '@/api/endpoints';
import { ErrorBanner } from '@/components/Feedback';
import { todayIsoDate } from '@/lib/datetime';
import { Decimal, formatMoney, isSameToTheCent, parseMoney, sumMoney } from '@/lib/money';
import { useAsyncData } from '@/lib/useAsyncData';

import type { ClaimLineRequest } from '@/types/api';

interface LineDraft {
  key: string;
  serviceCode: string;
  diagnosisCode: string;
  billedAmount: string;
}

function blankLine(): LineDraft {
  return {
    key: crypto.randomUUID(),
    serviceCode: '',
    diagnosisCode: '',
    billedAmount: '',
  };
}

export function ClaimSubmitPage() {
  const navigate = useNavigate();

  const [providerNpi, setProviderNpi] = useState('');
  const [memberReference, setMemberReference] = useState('');
  const [serviceDate, setServiceDate] = useState(todayIsoDate());
  const [billedAmount, setBilledAmount] = useState('');
  const [lines, setLines] = useState<LineDraft[]>([blankLine()]);

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const feeSchedules = useAsyncData(() => fetchFeeSchedules(serviceDate), [serviceDate]);

  /**
   * FR-009 mirrored client-side, in `decimal.js`. This is a convenience that normally
   * prevents the round trip; the server check stays authoritative and is exercised by
   * `AdjudicationBoundaryTest`. Every value here is a decimal string — a `Number()` here
   * is exactly the bug the whole money contract exists to prevent, and ESLint blocks it.
   */
  const lineSum = sumMoney(lines.map((line) => line.billedAmount));
  const header = parseMoney(billedAmount);
  const sumMatches = header !== null && isSameToTheCent(lineSum, header);
  const difference = header === null ? new Decimal(0) : lineSum.minus(header);

  function updateLine(key: string, patch: Partial<Omit<LineDraft, 'key'>>) {
    setLines((current) =>
      current.map((line) => (line.key === key ? { ...line, ...patch } : line)),
    );
  }

  async function handleSubmit() {
    setSubmitting(true);
    setError(null);
    setFieldErrors({});

    const payloadLines: ClaimLineRequest[] = lines.map((line) => ({
      serviceCode: line.serviceCode.trim().toUpperCase(),
      diagnosisCode: line.diagnosisCode.trim().toUpperCase(),
      billedAmount: line.billedAmount.trim(),
    }));

    try {
      const claim = await submitClaim(
        {
          providerNpi: providerNpi.trim(),
          memberReference: memberReference.trim(),
          serviceDate,
          billedAmount: billedAmount.trim(),
          lines: payloadLines,
        },
        // A fresh key per attempt: retrying a failed submission is a new intent, whereas
        // a duplicate network delivery of *this* attempt must collapse to one claim.
        crypto.randomUUID(),
      );
      navigate(`/claims/${claim.claimUuid}`, { replace: true });
    } catch (caught) {
      const envelope = toErrorResponse(caught);
      if (envelope?.fieldErrors != null) {
        setFieldErrors(
          Object.fromEntries(envelope.fieldErrors.map((detail) => [detail.field, detail.message])),
        );
      }
      setError(caught);
    } finally {
      setSubmitting(false);
    }
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void handleSubmit();
  }

  const code = errorCodeOf(error);
  const duplicateUuid = detailString(error, 'existingClaimUuid');

  return (
    <div className="space-y-4">
      <header>
        <h1 className="text-xl font-semibold tracking-tight text-slate-900">Submit a claim</h1>
        <p className="text-sm text-slate-600">
          Line amounts must sum to the header amount exactly. Anything at or above $25,000.00
          is held for medical review rather than paid automatically.
        </p>
      </header>

      {error !== null && code === 'DUPLICATE_CLAIM' && (
        <ErrorBanner message="An active claim already exists for this service encounter.">
          {duplicateUuid !== null && (
            <p className="mt-1">
              <Link to={`/claims/${duplicateUuid}`} className="font-medium underline">
                Open the existing claim
              </Link>
            </p>
          )}
        </ErrorBanner>
      )}

      {error !== null && code === 'LINE_SUM_MISMATCH' && (
        <ErrorBanner message="The line amounts do not sum to the header amount.">
          <p className="mt-1">
            Header {formatMoney(detailString(error, 'headerBilledAmount'))} · lines total{' '}
            {formatMoney(detailString(error, 'computedLineSum'))} · off by{' '}
            {formatMoney(detailString(error, 'difference'))}.
          </p>
        </ErrorBanner>
      )}

      {error !== null && code === 'UNKNOWN_SERVICE_CODE' && (
        <ErrorBanner
          message={`Service code ${detailString(error, 'serviceCode') ?? ''} has no rate in force on this date of service.`}
        />
      )}

      {error !== null && code === 'UNKNOWN_PROVIDER' && (
        <ErrorBanner
          message={`No active provider account for NPI ${detailString(error, 'providerNpi') ?? ''}.`}
        />
      )}

      {error !== null &&
        code !== 'DUPLICATE_CLAIM' &&
        code !== 'LINE_SUM_MISMATCH' &&
        code !== 'UNKNOWN_SERVICE_CODE' &&
        code !== 'UNKNOWN_PROVIDER' && (
          <ErrorBanner message={messageOf(error, 'The claim could not be submitted.')} />
        )}

      <form onSubmit={onSubmit} noValidate className="space-y-4">
        <section className="rounded-lg border border-slate-200 bg-white p-5">
          <h2 className="text-sm font-semibold text-slate-900">Claim header</h2>

          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            <div>
              <label htmlFor="providerNpi" className="block text-sm font-medium text-slate-800">
                Provider NPI
              </label>
              <input
                id="providerNpi"
                inputMode="numeric"
                required
                value={providerNpi}
                disabled={submitting}
                onChange={(event) => {
                  setProviderNpi(event.target.value);
                }}
                placeholder="1000000001"
                aria-describedby={fieldErrors.providerNpi !== undefined ? 'npi-error' : undefined}
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 font-mono text-sm shadow-sm disabled:bg-slate-50"
              />
              {fieldErrors.providerNpi !== undefined && (
                <p id="npi-error" className="mt-1 text-xs text-status-denied">
                  {fieldErrors.providerNpi}
                </p>
              )}
            </div>

            <div>
              <label
                htmlFor="memberReference"
                className="block text-sm font-medium text-slate-800"
              >
                Member reference
              </label>
              <input
                id="memberReference"
                required
                value={memberReference}
                disabled={submitting}
                onChange={(event) => {
                  setMemberReference(event.target.value);
                }}
                placeholder="MBR-8F41C0DE9A22"
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 font-mono text-sm shadow-sm disabled:bg-slate-50"
              />
              <p className="mt-1 text-xs text-slate-500">
                An opaque synthetic reference — never a name, SSN, or real member ID.
              </p>
            </div>

            <div>
              <label htmlFor="serviceDate" className="block text-sm font-medium text-slate-800">
                Service date
              </label>
              <input
                id="serviceDate"
                type="date"
                required
                max={todayIsoDate()}
                value={serviceDate}
                disabled={submitting}
                onChange={(event) => {
                  setServiceDate(event.target.value);
                }}
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm shadow-sm disabled:bg-slate-50"
              />
            </div>

            <div>
              <label htmlFor="billedAmount" className="block text-sm font-medium text-slate-800">
                Header billed amount
              </label>
              <input
                id="billedAmount"
                inputMode="decimal"
                required
                value={billedAmount}
                disabled={submitting}
                onChange={(event) => {
                  setBilledAmount(event.target.value);
                }}
                placeholder="125.00"
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-right font-mono text-sm tabular-nums shadow-sm disabled:bg-slate-50"
              />
            </div>
          </div>
        </section>

        <section className="rounded-lg border border-slate-200 bg-white">
          <div className="flex items-center justify-between border-b border-slate-200 px-5 py-3">
            <h2 className="text-sm font-semibold text-slate-900">Service lines</h2>
            <button
              type="button"
              disabled={submitting || lines.length >= 20}
              onClick={() => {
                setLines((current) => [...current, blankLine()]);
              }}
              className="inline-flex items-center gap-1.5 rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-40"
            >
              <Plus className="h-4 w-4" aria-hidden="true" />
              Add line
            </button>
          </div>

          {/* NFR-015: the row grid collapses to stacked cards below md rather than
              forcing a horizontal scroll on a narrow screen. */}
          <ul className="divide-y divide-slate-100">
            {lines.map((line, index) => (
              <li key={line.key} className="grid gap-3 px-5 py-4 md:grid-cols-[8rem_8rem_1fr_auto]">
                <div>
                  <label
                    htmlFor={`serviceCode-${line.key}`}
                    className="block text-xs font-medium text-slate-700"
                  >
                    Service code
                  </label>
                  <input
                    id={`serviceCode-${line.key}`}
                    list="fee-schedule-codes"
                    required
                    value={line.serviceCode}
                    disabled={submitting}
                    onChange={(event) => {
                      updateLine(line.key, { serviceCode: event.target.value });
                    }}
                    placeholder="MP101"
                    className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 font-mono text-sm uppercase shadow-sm disabled:bg-slate-50"
                  />
                </div>

                <div>
                  <label
                    htmlFor={`diagnosisCode-${line.key}`}
                    className="block text-xs font-medium text-slate-700"
                  >
                    Diagnosis
                  </label>
                  <input
                    id={`diagnosisCode-${line.key}`}
                    required
                    value={line.diagnosisCode}
                    disabled={submitting}
                    onChange={(event) => {
                      updateLine(line.key, { diagnosisCode: event.target.value });
                    }}
                    placeholder="E1165"
                    className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 font-mono text-sm uppercase shadow-sm disabled:bg-slate-50"
                  />
                </div>

                <div>
                  <label
                    htmlFor={`lineAmount-${line.key}`}
                    className="block text-xs font-medium text-slate-700"
                  >
                    Billed amount
                  </label>
                  <input
                    id={`lineAmount-${line.key}`}
                    inputMode="decimal"
                    required
                    value={line.billedAmount}
                    disabled={submitting}
                    onChange={(event) => {
                      updateLine(line.key, { billedAmount: event.target.value });
                    }}
                    placeholder="125.00"
                    className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-right font-mono text-sm tabular-nums shadow-sm disabled:bg-slate-50"
                  />
                </div>

                <div className="flex items-end">
                  <button
                    type="button"
                    disabled={submitting || lines.length === 1}
                    onClick={() => {
                      setLines((current) => current.filter((candidate) => candidate.key !== line.key));
                    }}
                    className="inline-flex items-center gap-1.5 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-600 hover:bg-slate-50 disabled:opacity-40"
                  >
                    <Trash2 className="h-4 w-4" aria-hidden="true" />
                    <span className="md:sr-only">Remove line {index + 1}</span>
                  </button>
                </div>
              </li>
            ))}
          </ul>

          <datalist id="fee-schedule-codes">
            {(feeSchedules.data ?? []).map((rate) => (
              <option key={`${rate.serviceCode}-${rate.effectiveFrom}`} value={rate.serviceCode}>
                {rate.description} — {formatMoney(rate.contractedRate)}
              </option>
            ))}
          </datalist>

          {/* The running total. aria-live so a screen reader hears the balance change. */}
          <div
            aria-live="polite"
            className={`flex flex-wrap items-center justify-between gap-2 border-t px-5 py-3 text-sm ${
              sumMatches
                ? 'border-green-200 bg-green-50 text-status-paid'
                : 'border-amber-200 bg-amber-50 text-status-flagged'
            }`}
          >
            <span className="inline-flex items-center gap-2 font-medium">
              {sumMatches ? (
                <Check className="h-4 w-4" aria-hidden="true" />
              ) : (
                <AlertCircle className="h-4 w-4" aria-hidden="true" />
              )}
              {sumMatches
                ? 'Lines match the header amount'
                : header === null
                  ? 'Enter a header amount to compare against'
                  : `Lines are off by ${formatMoney(difference.abs())}`}
            </span>
            <span className="font-mono tabular-nums">
              Lines {formatMoney(lineSum)} · header {formatMoney(billedAmount)}
            </span>
          </div>
        </section>

        <div className="flex justify-end">
          <button
            type="submit"
            disabled={submitting || !sumMatches}
            className="inline-flex items-center justify-center gap-2 rounded-md bg-sky-700 px-4 py-2 text-sm font-semibold text-white hover:bg-sky-800 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {submitting && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
            {submitting ? 'Submitting' : 'Submit claim'}
          </button>
        </div>
      </form>
    </div>
  );
}
