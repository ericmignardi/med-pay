import { apiClient } from './apiClient';

import type {
  ClaimAuditResponse,
  ClaimResponse,
  ClaimSubmissionRequest,
  ClaimStatus,
  ClaimSummaryResponse,
  FeeScheduleResponse,
  JournalFilters,
  JournalLineResponse,
  LoginRequest,
  LoginResponse,
  PageResponse,
  ReversalRequest,
  ReviewDecisionRequest,
  ReviewDenialRequest,
  UserProfileResponse,
} from '@/types/api';

/**
 * One typed function per endpoint in PRD §5. Pages never touch `apiClient` directly, so
 * the request shapes stay in one place and a contract change is a single-file edit.
 */

interface PageParams {
  page?: number;
  size?: number;
}

/** Drops undefined and empty values so they never reach the query string as "undefined". */
function definedParams(source: Record<string, string | number | undefined>): Record<string, string | number> {
  const result: Record<string, string | number> = {};
  for (const [key, value] of Object.entries(source)) {
    if (value !== undefined && value !== '') {
      result[key] = value;
    }
  }
  return result;
}

/* ---------------------------------------------------------------------------- auth */

export async function login(request: LoginRequest): Promise<LoginResponse> {
  const response = await apiClient.post<LoginResponse>('/auth/login', request);
  return response.data;
}

export async function fetchProfile(): Promise<UserProfileResponse> {
  const response = await apiClient.get<UserProfileResponse>('/auth/me');
  return response.data;
}

/* -------------------------------------------------------------------- fee schedules */

export async function fetchFeeSchedules(effectiveOn?: string): Promise<FeeScheduleResponse[]> {
  const response = await apiClient.get<FeeScheduleResponse[]>('/fee-schedules', {
    params: definedParams({ effectiveOn }),
  });
  return response.data;
}

/* -------------------------------------------------------------------------- claims */

export async function submitClaim(
  request: ClaimSubmissionRequest,
  idempotencyKey: string,
): Promise<ClaimResponse> {
  const response = await apiClient.post<ClaimResponse>('/claims', request, {
    headers: { 'Idempotency-Key': idempotencyKey },
  });
  return response.data;
}

export async function fetchOwnClaims(
  params: PageParams & { status?: ClaimStatus },
): Promise<PageResponse<ClaimSummaryResponse>> {
  const response = await apiClient.get<PageResponse<ClaimSummaryResponse>>('/claims', {
    params: definedParams({ page: params.page, size: params.size, status: params.status }),
  });
  return response.data;
}

export async function fetchOwnClaim(claimUuid: string): Promise<ClaimResponse> {
  const response = await apiClient.get<ClaimResponse>(`/claims/${claimUuid}`);
  return response.data;
}

export async function reverseClaim(
  claimUuid: string,
  request: ReversalRequest,
): Promise<ClaimResponse> {
  const response = await apiClient.post<ClaimResponse>(`/claims/${claimUuid}/reversals`, request);
  return response.data;
}

/* -------------------------------------------------------------------------- review */

export async function fetchReviewQueue(
  params: PageParams,
): Promise<PageResponse<ClaimSummaryResponse>> {
  const response = await apiClient.get<PageResponse<ClaimSummaryResponse>>('/review/queue', {
    params: definedParams({ page: params.page, size: params.size }),
  });
  return response.data;
}

export async function fetchFlaggedClaim(claimUuid: string): Promise<ClaimResponse> {
  const response = await apiClient.get<ClaimResponse>(`/review/claims/${claimUuid}`);
  return response.data;
}

export async function approveClaim(
  claimUuid: string,
  request: ReviewDecisionRequest,
): Promise<ClaimResponse> {
  const response = await apiClient.post<ClaimResponse>(
    `/review/claims/${claimUuid}/approve`,
    request,
  );
  return response.data;
}

export async function denyClaim(
  claimUuid: string,
  request: ReviewDenialRequest,
): Promise<ClaimResponse> {
  const response = await apiClient.post<ClaimResponse>(`/review/claims/${claimUuid}/deny`, request);
  return response.data;
}

/* --------------------------------------------------------------------------- audit */

export async function fetchJournals(
  filters: JournalFilters,
  params: PageParams,
): Promise<PageResponse<JournalLineResponse>> {
  const response = await apiClient.get<PageResponse<JournalLineResponse>>('/audit/journals', {
    params: definedParams({
      page: params.page,
      size: params.size,
      providerNpi: filters.providerNpi,
      claimUuid: filters.claimUuid,
      journalGroupId: filters.journalGroupId,
      postedFrom: filters.postedFrom,
      postedTo: filters.postedTo,
    }),
  });
  return response.data;
}

export async function fetchClaimAudit(claimUuid: string): Promise<ClaimAuditResponse> {
  const response = await apiClient.get<ClaimAuditResponse>(`/audit/claims/${claimUuid}`);
  return response.data;
}
