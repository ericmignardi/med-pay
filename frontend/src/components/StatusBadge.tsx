import {
  AlertTriangle,
  Ban,
  CheckCircle2,
  Clock,
  FileCheck2,
  Inbox,
  Undo2,
  type LucideIcon,
} from 'lucide-react';

import type { ClaimStatus } from '@/types/api';

/**
 * NFR-014. Status is never carried by colour alone: every badge pairs its tint with a
 * distinct icon and the status word itself, so it survives a monochrome display and any
 * form of colour vision deficiency.
 */
const PRESENTATION: Record<ClaimStatus, { label: string; icon: LucideIcon; className: string }> = {
  RECEIVED: {
    label: 'Received',
    icon: Inbox,
    className: 'bg-slate-100 text-slate-700 ring-slate-300',
  },
  VALIDATED: {
    label: 'Validated',
    icon: FileCheck2,
    className: 'bg-sky-50 text-sky-800 ring-sky-300',
  },
  FLAGGED_REVIEW: {
    label: 'Flagged for review',
    icon: AlertTriangle,
    className: 'bg-amber-50 text-status-flagged ring-amber-300',
  },
  ADJUDICATED: {
    label: 'Adjudicated',
    icon: Clock,
    className: 'bg-sky-50 text-sky-800 ring-sky-300',
  },
  PAID: {
    label: 'Paid',
    icon: CheckCircle2,
    className: 'bg-green-50 text-status-paid ring-green-300',
  },
  DENIED: {
    label: 'Denied',
    icon: Ban,
    className: 'bg-red-50 text-status-denied ring-red-300',
  },
  REVERSED: {
    label: 'Reversed',
    icon: Undo2,
    className: 'bg-violet-50 text-status-reversed ring-violet-300',
  },
};

export function StatusBadge({ status }: { status: ClaimStatus }) {
  const { label, icon: Icon, className } = PRESENTATION[status];

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ring-1 ring-inset ${className}`}
    >
      <Icon className="h-3.5 w-3.5" aria-hidden="true" />
      {label}
    </span>
  );
}
