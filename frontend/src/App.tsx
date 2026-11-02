import { Navigate, Route, Routes } from 'react-router-dom';

import AppLayout from './components/AppLayout';
import { useAuth } from './context/useAuth';
import AccountsPage from './pages/AccountsPage';
import AdminAuditPage from './pages/AdminAuditPage';
import AdminDashboardPage from './pages/AdminDashboardPage';
import AdminFraudPage from './pages/AdminFraudPage';
import AdminKycPage from './pages/AdminKycPage';
import AlertsPage from './pages/AlertsPage';
import AnalyticsPage from './pages/AnalyticsPage';
import BeneficiariesPage from './pages/BeneficiariesPage';
import BillsPage from './pages/BillsPage';
import CopilotPage from './pages/CopilotPage';
import DashboardPage from './pages/DashboardPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import GoalsPage from './pages/GoalsPage';
import HealthPage from './pages/HealthPage';
import LandingPage from './pages/LandingPage';
import LoginPage from './pages/LoginPage';
import NotFoundPage from './pages/NotFoundPage';
import NotificationsPage from './pages/NotificationsPage';
import ProfilePage from './pages/ProfilePage';
import RegisterPage from './pages/RegisterPage';
import SimulatorPage from './pages/SimulatorPage';
import StatementsPage from './pages/StatementsPage';
import TransferPage from './pages/TransferPage';

/**
 * Application routes.
 *
 * `RequireAuth` keeps unauthenticated users out of the application shell. It
 * is a navigation convenience, not a security control: every protected
 * endpoint is enforced server-side, so a user who edits the URL gets a 401
 * from the API rather than data.
 */
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />

      <Route
        path="/app"
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route path="accounts" element={<AccountsPage />} />
        <Route path="transfer" element={<TransferPage />} />
        <Route path="beneficiaries" element={<BeneficiariesPage />} />
        <Route path="bills" element={<BillsPage />} />
        <Route path="statements" element={<StatementsPage />} />
        <Route path="notifications" element={<NotificationsPage />} />
        <Route path="profile" element={<ProfilePage />} />

        <Route path="copilot" element={<CopilotPage />} />
        <Route path="analytics" element={<AnalyticsPage />} />
        <Route path="health" element={<HealthPage />} />
        <Route path="simulator" element={<SimulatorPage />} />
        <Route path="goals" element={<GoalsPage />} />
        <Route path="alerts" element={<AlertsPage />} />

        <Route path="admin" element={<AdminDashboardPage />} />
        <Route path="admin/kyc" element={<AdminKycPage />} />
        <Route path="admin/fraud" element={<AdminFraudPage />} />
        <Route path="admin/audit" element={<AdminAuditPage />} />
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}

function RequireAuth({ children }: { children: React.ReactNode }) {
  const { session } = useAuth();
  if (!session) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}
