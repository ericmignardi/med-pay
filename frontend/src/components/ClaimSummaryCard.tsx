import { StatusBadge } from '@/components/StatusBadge';
import { formatInstant, formatServiceDate } from '@/lib/datetime';
import { formatMoney } from '@/lib/money';

import type { ClaimResponse } from '@/types/api';

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="mt-0.5 text-sm text-slate-900">{children}</dd>
    </div>
  );
}

/** The claim header + lines block, shared by the processor, reviewer and auditor detail views. */
export function ClaimSummaryCard({ claim }: { claim: ClaimResponse }) {
  return (
    <div className="space-y-4">
      <section className="rounded-lg border border-slate-200 bg-white p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="font-mono text-xs text-slate-500">{claim.claimUuid}</p>
            <h2 className="mt-1 text-lg font-semibold text-slate-900">{claim.providerName}</h2>
            <p className="text-sm text-slate-600">NPI {claim.providerNpi}</p>
          </div>
          <StatusBadge status={claim.status} />
        </div>

        <dl className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="Member reference">
            <span className="font-mono text-xs">{claim.memberReference}</span>
          </Field>
          <Field label="Service date">{formatServiceDate(claim.serviceDate)}</Field>
          <Field label="Submitted">{formatInstant(claim.submittedAt)}</Field>
          <Field label="Adjudicated">{formatInstant(claim.adjudicatedAt)}</Field>

          <Field label="Billed">
            <span className="font-mono tabular-nums">{formatMoney(claim.billedAmount)}</span>
          </Field>
          <Field label="Allowed">
            <span className="font-mono tabular-nums">{formatMoney(claim.allowedAmount)}</span>
          </Field>
          <Field label="Patient responsibility">
            <span className="font-mono tabular-nums">
              {formatMoney(claim.patientResponsibility)}
            </span>
          </Field>
          <Field label="Reviewed">{formatInstant(claim.reviewedAt)}</Field>
        </dl>

        {(claim.reviewNote !== null || claim.denialReason !== null) && (
          <div className="mt-5 rounded-md border border-slate-200 bg-slate-50 p-3">
            {claim.denialReason !== null && (
              <p className="text-sm font-medium text-status-denied">
                Denied — {claim.denialReason.replace(/_/g, ' ').toLowerCase()}
              </p>
            )}
            {claim.reviewNote !== null && (
              <p className="mt-1 text-sm text-slate-700">{claim.reviewNote}</p>
            )}
          </div>
        )}
      </section>

      <section className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <h3 className="border-b border-slate-200 px-5 py-3 text-sm font-semibold text-slate-900">
          Service lines
        </h3>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[36rem] text-sm">
            <thead className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th scope="col" className="px-5 py-2 font-medium">#</th>
                <th scope="col" className="px-5 py-2 font-medium">Service</th>
                <th scope="col" className="px-5 py-2 font-medium">Diagnosis</th>
                <th scope="col" className="px-5 py-2 text-right font-medium">Billed</th>
                <th scope="col" className="px-5 py-2 text-right font-medium">Allowed</th>
                <th scope="col" className="px-5 py-2 text-right font-medium">Patient</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {claim.lines.map((line) => (
                <tr key={line.lineNumber}>
                  <td className="px-5 py-2.5 text-slate-500">{line.lineNumber}</td>
                  <td className="px-5 py-2.5 font-mono text-xs text-slate-800">
                    {line.serviceCode}
                  </td>
                  <td className="px-5 py-2.5 font-mono text-xs text-slate-800">
                    {line.diagnosisCode}
                  </td>
                  <td className="px-5 py-2.5 text-right font-mono tabular-nums">
                    {formatMoney(line.billedAmount)}
                  </td>
                  <td className="px-5 py-2.5 text-right font-mono tabular-nums">
                    {formatMoney(line.allowedAmount)}
                  </td>
                  <td className="px-5 py-2.5 text-right font-mono tabular-nums">
                    {formatMoney(line.patientResponsibility)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
