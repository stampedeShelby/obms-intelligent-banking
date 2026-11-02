/** Typed wrappers around the OBMS endpoints. */

import { http, idempotencyKey } from './client';
import type {
  AccountSummary,
  BeneficiaryView,
  BillItem,
  Biller,
  CopilotAnswer,
  Dashboard,
  FinancialHealth,
  GoalView,
  LoginResponse,
  NotificationItem,
  Page,
  Session,
  SimulationResult,
  SpendingAnalytics,
  TransactionView,
  TransferResult,
} from './types';

// ---- auth ----------------------------------------------------------------

export const auth = {
  login: (username: string, password: string) =>
    http.post<LoginResponse>('/api/auth/login', { username, password }).then((r) => r.data),

  verifyOtp: (preAuthToken: string, code: string) =>
    http
      .post<Session>(
        '/api/auth/verify-otp',
        { code },
        { headers: { 'X-Pre-Auth-Token': preAuthToken } },
      )
      .then((r) => r.data),

  resendOtp: (preAuthToken: string) =>
    http.post<void>('/api/auth/resend-otp', {}, { headers: { 'X-Pre-Auth-Token': preAuthToken } }),

  register: (payload: Record<string, unknown>) =>
    http
      .post<{ customerNumber: string; username: string; accountNumber: string; message: string }>(
        '/api/auth/register',
        payload,
      )
      .then((r) => r.data),

  forgotPassword: (email: string) => http.post<void>('/api/auth/forgot-password', { email }),

  resetPassword: (username: string, code: string, newPassword: string) =>
    http.post<void>('/api/auth/reset-password', { username, code, newPassword }),

  profile: () => http.get('/api/auth/me').then((r) => r.data),

  updateProfile: (payload: Record<string, unknown>) =>
    http.put('/api/auth/me', payload).then((r) => r.data),

  submitKyc: (payload: Record<string, unknown>) =>
    http.post<string>('/api/auth/kyc', payload).then((r) => r.data),
};

// ---- banking -------------------------------------------------------------

export const banking = {
  dashboard: () => http.get<Dashboard>('/api/dashboard').then((r) => r.data),

  accounts: () => http.get<AccountSummary[]>('/api/accounts').then((r) => r.data),

  account: (id: number) => http.get<AccountSummary>(`/api/accounts/${id}`).then((r) => r.data),

  history: (accountId: number, page = 0, size = 20) =>
    http
      .get<Page<TransactionView>>(`/api/accounts/${accountId}/transactions`, {
        params: { page, size },
      })
      .then((r) => r.data),

  recent: () => http.get<TransactionView[]>('/api/transactions/recent').then((r) => r.data),

  recategorise: (reference: string, category: string) =>
    http
      .patch<TransactionView>(`/api/transactions/${reference}/category`, { category })
      .then((r) => r.data),

  statementUrl: (accountId: number, from: string, to: string) =>
    `/api/statements/${accountId}?from=${from}&to=${to}`,

  /** Downloads a statement PDF through the authenticated client. */
  downloadStatement: (accountId: number, from: string, to: string) =>
    http
      .get(`/api/statements/${accountId}`, { params: { from, to }, responseType: 'blob' })
      .then((r) => r.data as Blob),
};

// ---- transfers -----------------------------------------------------------

export const transfers = {
  initiate: (payload: {
    sourceAccountId: number;
    beneficiaryId?: number | null;
    destinationAccountId?: number | null;
    amount: string;
    description?: string;
  }) =>
    http
      .post<TransferResult>('/api/transfers', payload, {
        headers: { 'Idempotency-Key': idempotencyKey() },
      })
      .then((r) => r.data),

  confirm: (pendingRef: string, otpCode: string) =>
    http.post<TransferResult>('/api/transfers/confirm', { pendingRef, otpCode }).then((r) => r.data),
};

// ---- beneficiaries -------------------------------------------------------

export const beneficiaries = {
  list: () => http.get<BeneficiaryView[]>('/api/beneficiaries').then((r) => r.data),

  add: (payload: {
    beneficiaryName: string;
    accountNumber: string;
    ifscCode: string;
    nickname?: string;
  }) => http.post<BeneficiaryView>('/api/beneficiaries', payload).then((r) => r.data),

  verify: (id: number, code: string) =>
    http.post<BeneficiaryView>(`/api/beneficiaries/${id}/verify`, { code }).then((r) => r.data),

  rename: (id: number, nickname: string) =>
    http.patch<BeneficiaryView>(`/api/beneficiaries/${id}`, { nickname }).then((r) => r.data),

  remove: (id: number) => http.delete<void>(`/api/beneficiaries/${id}`),
};

// ---- bills ---------------------------------------------------------------

export const bills = {
  billers: () => http.get<Biller[]>('/api/bills/billers').then((r) => r.data),

  pay: (payload: {
    billerId: number;
    accountId: number;
    consumerIdentifier: string;
    amount: string;
  }) =>
    http
      .post('/api/bills/pay', payload, { headers: { 'Idempotency-Key': idempotencyKey() } })
      .then((r) => r.data),

  history: (page = 0, size = 20) =>
    http.get<Page<BillItem>>('/api/bills', { params: { page, size } }).then((r) => r.data),

  upcoming: (days = 14) =>
    http.get<BillItem[]>('/api/bills/upcoming', { params: { days } }).then((r) => r.data),
};

// ---- intelligence --------------------------------------------------------

export const intelligence = {
  analytics: (period?: string) =>
    http
      .get<SpendingAnalytics>('/api/analytics/spending', { params: period ? { period } : {} })
      .then((r) => r.data),

  healthScore: (period?: string) =>
    http
      .get<FinancialHealth>('/api/health-score', { params: period ? { period } : {} })
      .then((r) => r.data),

  simulate: (payload: Record<string, unknown>) =>
    http.post<SimulationResult>('/api/simulator/run', payload).then((r) => r.data),

  affordability: (amount: string) =>
    http.get('/api/simulator/affordability', { params: { amount } }).then((r) => r.data),

  goals: () => http.get<GoalView[]>('/api/savings-goals').then((r) => r.data),

  createGoal: (payload: { name: string; targetAmount: string; targetDate: string }) =>
    http.post<GoalView>('/api/savings-goals', payload).then((r) => r.data),

  contribute: (goalId: number, amount: string, accountId: number) =>
    http
      .post<GoalView>(`/api/savings-goals/${goalId}/contribute`, { amount, accountId })
      .then((r) => r.data),

  abandonGoal: (goalId: number) => http.delete<void>(`/api/savings-goals/${goalId}`),

  ask: (question: string, conversationId?: number | null) =>
    http
      .post<CopilotAnswer>('/api/copilot/ask', { question, conversationId: conversationId ?? null })
      .then((r) => r.data),

  suggestions: () => http.get<string[]>('/api/copilot/suggestions').then((r) => r.data),

  alerts: (page = 0, size = 20) =>
    http.get('/api/fraud-alerts', { params: { page, size } }).then((r) => r.data),

  markFalsePositive: (alertId: number, note?: string) =>
    http.post(`/api/fraud-alerts/${alertId}/false-positive`, { note }).then((r) => r.data),
};

// ---- notifications -------------------------------------------------------

export const notifications = {
  list: (page = 0, size = 20) =>
    http
      .get<Page<NotificationItem>>('/api/notifications', { params: { page, size } })
      .then((r) => r.data),

  unreadCount: () =>
    http.get<{ unread: number }>('/api/notifications/unread-count').then((r) => r.data.unread),

  markRead: (id: number) => http.post<void>(`/api/notifications/${id}/read`),

  markAllRead: () => http.post<{ updated: number }>('/api/notifications/read-all'),
};

// ---- admin ---------------------------------------------------------------

export const admin = {
  stats: () => http.get('/api/staff/stats').then((r) => r.data),

  customers: (query = '', page = 0, size = 20) =>
    http.get('/api/staff/customers', { params: { query, page, size } }).then((r) => r.data),

  kycQueue: (page = 0, size = 20) =>
    http.get('/api/staff/kyc', { params: { page, size } }).then((r) => r.data),

  decideKyc: (kycId: number, approve: boolean, reason?: string) =>
    http.post(`/api/staff/kyc/${kycId}/decision`, { approve, reason }).then((r) => r.data),

  setAccountStatus: (accountId: number, status: string, reason?: string) =>
    http.put<void>(`/api/admin/accounts/${accountId}/status`, { status, reason }),

  investigationQueue: (status?: string, page = 0, size = 20) =>
    http
      .get('/api/fraud/investigation/queue', { params: { status, page, size } })
      .then((r) => r.data),

  alert: (alertId: number) =>
    http.get(`/api/fraud/investigation/alerts/${alertId}`).then((r) => r.data),

  reviewAlert: (alertId: number, status: string, notes?: string) =>
    http
      .post(`/api/fraud/investigation/alerts/${alertId}/review`, { status, notes })
      .then((r) => r.data),

  monitoring: (days = 7) =>
    http.get('/api/fraud/investigation/monitoring', { params: { days } }).then((r) => r.data),

  distribution: () => http.get('/api/fraud/investigation/distribution').then((r) => r.data),

  audit: (action = '', actor = '', page = 0, size = 30) =>
    http.get('/api/admin/audit', { params: { action, actor, page, size } }).then((r) => r.data),
};
