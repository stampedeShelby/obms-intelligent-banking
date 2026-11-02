/**
 * HTTP client for the OBMS API.
 *
 * Three behaviours worth noting:
 *
 * * The access token is held in memory, not in `localStorage`. A token in
 *   local storage is readable by any script that gets injected into the page,
 *   which is the usual way a session gets stolen. Keeping it in a module
 *   variable means a refresh signs the customer out, which is the right
 *   trade for a banking application.
 * * A 401 clears the session and notifies listeners, so an expired token
 *   returns the customer to sign-in rather than leaving the UI in a broken
 *   half-authenticated state.
 * * Money-moving calls send an `Idempotency-Key`, so a retry after a dropped
 *   connection cannot produce a second debit.
 */

import axios, { AxiosError, type AxiosInstance } from 'axios';

import type { ApiError } from './types';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

let accessToken: string | null = null;
let onUnauthorised: (() => void) | null = null;

export const http: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 20000,
});

http.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    if (error.response?.status === 401 && accessToken) {
      accessToken = null;
      onUnauthorised?.();
    }
    return Promise.reject(error);
  },
);

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

export function setUnauthorisedHandler(handler: (() => void) | null): void {
  onUnauthorised = handler;
}

/** A fresh idempotency key for one money-moving request. */
export function idempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  return `k-${Date.now()}-${Math.random().toString(36).slice(2, 11)}`;
}

/**
 * Turns an axios failure into a message worth showing a customer.
 *
 * Validation failures are flattened into a single readable line rather than
 * being dropped in favour of a generic message.
 */
export function errorMessage(error: unknown, fallback = 'Something went wrong.'): string {
  if (axios.isAxiosError<ApiError>(error)) {
    const body = error.response?.data;
    if (body?.fieldErrors && Object.keys(body.fieldErrors).length > 0) {
      return Object.values(body.fieldErrors).join(' ');
    }
    if (body?.message) {
      return body.message;
    }
    if (error.code === 'ECONNABORTED') {
      return 'The request timed out. Check that the backend is running.';
    }
    if (!error.response) {
      return 'Could not reach the server. Check that the backend is running on port 8080.';
    }
  }
  return fallback;
}

/** The stable error code, for branching on a specific failure. */
export function errorCode(error: unknown): string | null {
  if (axios.isAxiosError<ApiError>(error)) {
    return error.response?.data?.code ?? null;
  }
  return null;
}
