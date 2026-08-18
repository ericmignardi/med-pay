import axios, { AxiosError, type AxiosInstance } from 'axios';

import type { ErrorResponse } from '@/types/api';

export const TOKEN_KEY = 'medpay.token';
export const PROFILE_KEY = 'medpay.profile';

/**
 * FR-028. Two interceptors, and deliberately only two.
 *
 * A request interceptor attaches the bearer token from `sessionStorage`. A response
 * interceptor treats `401` as a dead session: it clears storage and sends the user to
 * `/login?expired=1`.
 *
 * `403` is **not** intercepted. A `403` means the session is perfectly valid and the
 * caller simply lacks the role — logging them out would be both wrong and confusing.
 * It surfaces to the calling page as an inline error instead.
 */
export const apiClient: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
  timeout: 20_000,
});

export function readToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY);
}

export function clearSession(): void {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(PROFILE_KEY);
}

apiClient.interceptors.request.use((config) => {
  const token = readToken();
  if (token !== null) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const onLoginPage = window.location.pathname === '/login';
      clearSession();
      if (!onLoginPage) {
        // A hard assignment rather than a router navigate: the interceptor lives
        // outside React, and any in-flight page state should be discarded anyway.
        window.location.assign('/login?expired=1');
      }
    }
    return Promise.reject(error instanceof Error ? error : new Error(String(error)));
  },
);

/** Narrows an unknown thrown value to the API's error envelope (PRD §5.6). */
export function toErrorResponse(error: unknown): ErrorResponse | null {
  if (!(error instanceof AxiosError)) {
    return null;
  }
  const body: unknown = error.response?.data;
  if (typeof body !== 'object' || body === null) {
    return null;
  }
  const candidate = body as Partial<ErrorResponse>;
  if (typeof candidate.code !== 'string' || typeof candidate.status !== 'number') {
    return null;
  }
  return candidate as ErrorResponse;
}

/** The API error code, or null when the failure never reached the API. */
export function errorCodeOf(error: unknown): string | null {
  return toErrorResponse(error)?.code ?? null;
}

export function statusOf(error: unknown): number | null {
  if (error instanceof AxiosError) {
    return error.response?.status ?? null;
  }
  return null;
}

/** A human-facing message, preferring the server's over a generic one. */
export function messageOf(error: unknown, fallback = 'Something went wrong.'): string {
  const envelope = toErrorResponse(error);
  if (envelope !== null && envelope.message.trim() !== '') {
    return envelope.message;
  }
  if (error instanceof AxiosError && error.code === 'ECONNABORTED') {
    return 'The request timed out. The service may be starting up.';
  }
  if (error instanceof AxiosError && error.response === undefined) {
    return 'Could not reach the server. Check your connection and try again.';
  }
  return fallback;
}

/** Reads a typed field out of the envelope's `details` map. */
export function detailString(error: unknown, key: string): string | null {
  const details = toErrorResponse(error)?.details;
  if (details === null || details === undefined) {
    return null;
  }
  const value = details[key];
  return typeof value === 'string' ? value : null;
}
