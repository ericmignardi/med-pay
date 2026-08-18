import { ArrowDownLeft, ArrowUpRight, Undo2 } from 'lucide-react';

import { formatInstant } from '@/lib/datetime';
import { Decimal, formatMoney, parseMoney } from '@/lib/money';

import type { JournalGroupResponse, LedgerDirection } from '@/types/api';

/**
 * Renders journal groups as balanced pairs. Each group prints its own signed total, which
 * is always `0.00` for a well-formed group — showing it makes the double-entry invariant
 * visible to an auditor rather than something they have to take on trust (FR-015).
 */

function signedTotal(group: JournalGroupResponse): Decimal {
  return group.lines.reduce<Decimal>((total, line) => {
    const amount = parseMoney(line.amount);
    if (amount === null) {
      return total;
    }
    return line.direction === 'DEBIT' ? total.plus(amount) : total.minus(amount);
  }, new Decimal(0));
}

function DirectionIcon({ direction }: { direction: LedgerDirection }) {
  return direction === 'DEBIT' ? (
    <ArrowUpRight className="h-3.5 w-3.5 text-sky-700" aria-hidden="true" />
  ) : (
    <ArrowDownLeft className="h-3.5 w-3.5 text-status-paid" aria-hidden="true" />
  );
}

export function JournalGroups({ groups }: { groups: JournalGroupResponse[] }) {
  if (groups.length === 0) {
    return (
      <p className="px-4 py-6 text-sm text-slate-600">
        No ledger entries. A claim posts to the ledger only when it is adjudicated below the
        review threshold or approved by a reviewer.
      </p>
    );
  }

  return (
    <div className="space-y-4">
      {groups.map((group) => {
        const total = signedTotal(group);
        const balanced = total.isZero();

        return (
          <section
            key={group.journalGroupId}
            className="overflow-hidden rounded-lg border border-slate-200 bg-white"
          >
            <header className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 bg-slate-50 px-4 py-2.5">
              <div className="flex items-center gap-2">
                {group.reversesJournalGroupId !== null && (
                  <span className="inline-flex items-center gap-1 rounded-full bg-violet-50 px-2 py-0.5 text-xs font-medium text-status-reversed ring-1 ring-inset ring-violet-300">
                    <Undo2 className="h-3 w-3" aria-hidden="true" />
                    Reversal
                  </span>
                )}
                <span className="font-mono text-xs text-slate-600">{group.journalGroupId}</span>
              </div>
              <span className="text-xs text-slate-600">{formatInstant(group.postedAt)}</span>
            </header>

            {group.reversesJournalGroupId !== null && (
              <p className="border-b border-slate-200 bg-violet-50/50 px-4 py-2 text-xs text-slate-700">
                Reverses group{' '}
                <span className="font-mono">{group.reversesJournalGroupId}</span>. The original
                rows are untouched — a correction is a new entry, never an edit.
              </p>
            )}

            <div className="overflow-x-auto">
              <table className="w-full min-w-[36rem] text-sm">
                <thead className="text-left text-xs uppercase tracking-wide text-slate-500">
                  <tr className="border-b border-slate-200">
                    <th scope="col" className="px-4 py-2 font-medium">Account</th>
                    <th scope="col" className="px-4 py-2 font-medium">Direction</th>
                    <th scope="col" className="px-4 py-2 text-right font-medium">Amount</th>
                    <th scope="col" className="px-4 py-2 font-medium">Memo</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {group.lines.map((line, index) => (
                    <tr key={`${line.journalGroupId}-${line.accountType}-${index}`}>
                      <td className="px-4 py-2 font-medium text-slate-800">{line.accountType}</td>
                      <td className="px-4 py-2">
                        <span className="inline-flex items-center gap-1.5 text-slate-700">
                          <DirectionIcon direction={line.direction} />
                          {line.direction}
                        </span>
                      </td>
                      <td className="px-4 py-2 text-right font-mono tabular-nums text-slate-900">
                        {formatMoney(line.amount)}
                      </td>
                      <td className="px-4 py-2 text-slate-600">{line.memo}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr className="border-t border-slate-200 bg-slate-50">
                    <td className="px-4 py-2 text-xs font-medium uppercase tracking-wide text-slate-500" colSpan={2}>
                      Group balance
                    </td>
                    <td
                      className={`px-4 py-2 text-right font-mono tabular-nums font-semibold ${
                        balanced ? 'text-status-paid' : 'text-status-denied'
                      }`}
                    >
                      {formatMoney(total)}
                    </td>
                    <td className="px-4 py-2 text-xs text-slate-600">
                      {balanced ? 'Balanced' : 'Unbalanced — this should be impossible'}
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </section>
        );
      })}
    </div>
  );
}
