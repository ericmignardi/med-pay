/**
 * Transcribed from PRD §5, which the backend froze at the end of Phase 7.
 *
 * Every monetary field is a `string`, never a `number`. The API emits decimal strings
 * precisely because JavaScript's `number` is an IEEE-754 double, which would reintroduce
 * the floating-point error the ledger exists to avoid. Parse them with `decimal.js`
 * (see `lib/money.ts`); the ESLint config makes `Number()`, `parseFloat` and unary `+`
 * hard errors so this cannot be violated by accident.
 */

export type Role = 'CLAIMS_PROCESSOR' | 'MEDICAL_REVIEWER' | 'AUDITOR';

export type ClaimStatus =
  | 'RECEIVED'
  | 'VALIDATED'
  | 'FLAGGED_REVIEW'
  | 'ADJUDICATED'
  | 'PAID'
  | 'DENIED'
  | 'REVERSED';

export type DenialReason =
  | 'NOT_MEDICALLY_NECESSARY'
  | 'SERVICE_NOT_COVERED'
  | 'INSUFFICIENT_DOCUMENTATION'
  | 'DUPLICATE_ENCOUNTER'
  | 'OUT_OF_NETWORK';

export type ReversalReason =
  | 'DUPLICATE_PAYMENT'
  | 'CLINICAL_DETERMINATION_OVERTURNED'
  | 'PROVIDER_REFUND';

export type LedgerAccountType = 'PAYER_CLAIMS_EXPENSE' | 'PROVIDER_PAYABLE';

export type LedgerDirection = 'DEBIT' | 'CREDIT';

/* -------------------------------------------------------------------------- §5.1 auth */

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  expiresAt: string;
  userUuid: string;
  email: string;
  fullName: string;
  roles: Role[];
}

export interface UserProfileResponse {
  userUuid: string;
  email: string;
  fullName: string;
  roles: Role[];
}

/* ----------------------------------------------------------------- §5.2 fee schedules */

export interface FeeScheduleResponse {
  serviceCode: string;
  description: string;
  contractedRate: string;
  effectiveFrom: string;
  effectiveTo: string | null;
}

/* ------------------------------------------------------------------------ §5.3 claims */

export interface ClaimLineRequest {
  serviceCode: string;
  diagnosisCode: string;
  billedAmount: string;
}

export interface ClaimSubmissionRequest {
  providerNpi: string;
  memberReference: string;
  serviceDate: string;
  billedAmount: string;
  lines: ClaimLineRequest[];
}

export interface ClaimLineResponse {
  lineNumber: number;
  serviceCode: string;
  diagnosisCode: string;
  billedAmount: string;
  allowedAmount: string | null;
  patientResponsibility: string | null;
}

export interface ClaimResponse {
  claimUuid: string;
  providerNpi: string;
  providerName: string;
  memberReference: string;
  serviceDate: string;
  billedAmount: string;
  allowedAmount: string | null;
  patientResponsibility: string | null;
  status: ClaimStatus;
  submittedAt: string;
  adjudicatedAt: string | null;
  reviewedAt: string | null;
  reviewNote: string | null;
  denialReason: DenialReason | null;
  lines: ClaimLineResponse[];
  journalGroups: JournalGroupResponse[];
}

export interface ClaimSummaryResponse {
  claimUuid: string;
  providerNpi: string;
  serviceDate: string;
  billedAmount: string;
  allowedAmount: string | null;
  status: ClaimStatus;
  submittedAt: string;
  lineCount: number;
}

export interface ReversalRequest {
  reason: ReversalReason;
  note: string;
}

/* ------------------------------------------------------------------------ §5.4 review */

export interface ReviewDecisionRequest {
  note: string;
}

export interface ReviewDenialRequest {
  reason: DenialReason;
  note: string;
}

/* ------------------------------------------------------------------------- §5.5 audit */

export interface JournalLineResponse {
  journalGroupId: string;
  claimUuid: string;
  providerNpi: string;
  accountType: LedgerAccountType;
  direction: LedgerDirection;
  amount: string;
  memo: string;
  reversesJournalGroupId: string | null;
  postedAt: string;
}

export interface JournalGroupResponse {
  journalGroupId: string;
  reversesJournalGroupId: string | null;
  postedAt: string;
  lines: JournalLineResponse[];
}

export interface ClaimEventResponse {
  eventUuid: string;
  eventType: string;
  createdAt: string;
  publishedAt: string | null;
}

export interface ClaimAuditResponse {
  claim: ClaimResponse;
  journalGroups: JournalGroupResponse[];
  events: ClaimEventResponse[];
}

export interface JournalFilters {
  providerNpi?: string;
  claimUuid?: string;
  journalGroupId?: string;
  postedFrom?: string;
  postedTo?: string;
}

/* --------------------------------------------------------------- §5.6 shared envelopes */

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface FieldErrorDetail {
  field: string;
  rejectedValue: unknown;
  message: string;
}

export interface ErrorResponse {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  fieldErrors: FieldErrorDetail[] | null;
  details: Record<string, unknown> | null;
}
