/** Shapes returned by the OBMS backend. Kept in step with the Java DTOs. */

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type AccountType = 'SAVINGS' | 'CURRENT';
export type AccountStatus = 'ACTIVE' | 'DORMANT' | 'FROZEN' | 'CLOSED';
export type Direction = 'DEBIT' | 'CREDIT';

export interface Session {
  accessToken: string;
  expiresInSeconds: number;
  username: string;
  displayName: string;
  roles: string[];
  customerId: number | null;
  kycStatus: string | null;
}

export interface LoginResponse {
  otpRequired: boolean;
  preAuthToken: string | null;
  accessToken: string | null;
  expiresInSeconds: number | null;
  maskedDestination: string | null;
  message: string;
}

export interface AccountSummary {
  id: number;
  accountNumber: string;
  ifscCode: string;
  nickname: string | null;
  accountType: AccountType;
  status: AccountStatus;
  balance: number;
  availableBalance: number;
  dailyTransferLimit: number;
  currency: string;
}

export interface TransactionView {
  reference: string;
  transferGroupRef: string | null;
  direction: Direction;
  transactionType: string;
  status: string;
  amount: number;
  balanceAfter: number | null;
  description: string | null;
  counterpartyName: string | null;
  category: string | null;
  categoryUserSet: boolean;
  bookedAt: string;
  riskScore: number | null;
  riskLevel: RiskLevel | null;
  accountNumber: string | null;
}

export interface CategorySlice {
  category: string;
  total: number;
  share: number;
  transactions: number;
}

export interface MonthlyTotals {
  period: string;
  income: number;
  expense: number;
  net: number;
}

export interface Insight {
  title: string;
  detail: string;
  severity: 'INFO' | 'SUGGESTION' | 'WARNING';
}

export interface SpendingAnalytics {
  period: string;
  totalSpend: number;
  totalIncome: number;
  categories: CategorySlice[];
  trend: MonthlyTotals[];
  monthOverMonth: number;
  recurring: { label: string; occurrences: number; typicalAmount: number; category: string }[];
  insights: Insight[];
  uncategorisedCount: number;
}

export interface IndicatorScore {
  indicator: string;
  label: string;
  observed: number;
  unit: string;
  weight: number;
  points: number;
  explanation: string;
}

export interface FinancialHealth {
  period: string;
  score: number;
  band: string;
  interpretation: string;
  indicators: IndicatorScore[];
  suggestions: { action: string; rationale: string; pointsAtStake: number }[];
  history: { period: string; score: number }[];
  formulaVersion: string;
  dataSufficient: boolean;
}

export interface AlertTile {
  id: number;
  riskLevel: RiskLevel;
  amount: number;
  reason: string;
  raisedAt: string;
}

export interface GoalView {
  id: number;
  name: string;
  targetAmount: number;
  savedAmount: number;
  remainingAmount: number;
  progressPercent: number;
  targetDate: string;
  status: string;
  icon: string | null;
  requiredMonthly: number;
  recommendedMonthly: number;
  onTrack: boolean;
  monthsRemaining: number;
  advice: string;
}

export interface BillItem {
  reference: string;
  billerName: string;
  category: string;
  consumerIdentifier: string;
  amount: number;
  status: string;
  dueDate: string | null;
  paidAt: string | null;
}

export interface Dashboard {
  displayName: string;
  totalBalance: number;
  accounts: AccountSummary[];
  recentTransactions: TransactionView[];
  monthIncome: number;
  monthExpense: number;
  topCategories: CategorySlice[];
  healthScore: number | null;
  healthBand: string | null;
  activeAlerts: AlertTile[];
  goals: GoalView[];
  upcomingBills: BillItem[];
  unreadNotifications: number;
  kycStatus: string;
}

export interface BeneficiaryView {
  id: number;
  beneficiaryName: string;
  nickname: string | null;
  maskedAccountNumber: string;
  ifscCode: string;
  bankName: string | null;
  status: string;
  payable: boolean;
  verifiedAt: string | null;
}

export interface Biller {
  id: number;
  code: string;
  name: string;
  category: string;
  identifierLabel: string;
  minAmount: number;
  maxAmount: number;
}

export interface RiskFactor {
  feature: string;
  value: string;
  contribution: number;
  explanation: string;
}

export type TransferOutcome = 'COMPLETED' | 'VERIFICATION_REQUIRED' | 'HELD_FOR_REVIEW';

export interface TransferResult {
  outcome: TransferOutcome;
  reference: string | null;
  pendingRef: string | null;
  amount: number;
  balanceAfter: number | null;
  riskScore: number | null;
  riskLevel: RiskLevel | null;
  message: string;
  riskFactors: RiskFactor[];
}

export interface CopilotAnswer {
  conversationId: number;
  answer: string;
  intent: string;
  figures: Record<string, string>;
  chart: { type: string; title: string; labels: string[]; values: number[] } | null;
  generator: string;
  dataAvailable: boolean;
  askedAt: string;
}

export interface SimulationResult {
  months: number;
  current: ScenarioSide;
  proposed: ScenarioSide;
  difference: number;
  monthlyAccumulation: number;
  series: { period: string; current: number; projected: number }[];
  assumptions: string[];
  verdict: string;
  dataSufficient: boolean;
}

export interface ScenarioSide {
  label: string;
  monthlyIncome: number;
  monthlyExpense: number;
  monthlyNet: number;
  endBalance: number;
}

export interface NotificationItem {
  id: number;
  type: string;
  title: string;
  body: string;
  linkTarget: string | null;
  read: boolean;
  dispatched: boolean;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface ApiError {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  fieldErrors?: Record<string, string>;
}
