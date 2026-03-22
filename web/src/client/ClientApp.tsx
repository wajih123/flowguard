import React, { useEffect } from "react";
import { Routes, Route, Navigate } from "react-router-dom";
import { useAuthStore } from "@/store/authStore";
import { Loader } from "@/components/ui/Loader";
import type { Role } from "@/domain/User";

// ─── Lazy page imports ────────────────────────────────────────────────────────
const LoginPage = React.lazy(() => import("./pages/auth/LoginPage"));
const RegisterPage = React.lazy(() => import("./pages/auth/RegisterPage"));
const RegisterBusinessPage = React.lazy(
  () => import("./pages/auth/RegisterBusinessPage"),
);
const ForgotPasswordPage = React.lazy(
  () => import("./pages/auth/ForgotPasswordPage"),
);
const DashboardPage = React.lazy(() => import("./pages/DashboardPage"));
const ForecastPage = React.lazy(() => import("./pages/ForecastPage"));
const AlertsPage = React.lazy(() => import("./pages/AlertsPage"));
const TransactionsPage = React.lazy(() => import("./pages/TransactionsPage"));
const SpendingPage = React.lazy(() => import("./pages/SpendingPage"));
const ScenarioPage = React.lazy(() => import("./pages/ScenarioPage"));
const FlashCreditPage = React.lazy(() => import("./pages/FlashCreditPage"));
const BankConnectPage = React.lazy(() => import("./pages/BankConnectPage"));
const BankCallbackPage = React.lazy(() => import("./pages/BankCallbackPage"));
const ProfilePage = React.lazy(() => import("./pages/ProfilePage"));
const SubscriptionPage = React.lazy(() => import("./pages/SubscriptionPage"));
const TeamPage = React.lazy(() => import("./pages/TeamPage"));
const InvoicesPage = React.lazy(() => import("./pages/InvoicesPage"));
const BudgetPage = React.lazy(() => import("./pages/BudgetPage"));
const TaxPage = React.lazy(() => import("./pages/TaxPage"));
const BenchmarksPage = React.lazy(() => import("./pages/BenchmarksPage"));
const ForecastAccuracyPage = React.lazy(
  () => import("./pages/ForecastAccuracyPage"),
);
const PaymentsPage = React.lazy(() => import("./pages/PaymentsPage"));
const AccountantPage = React.lazy(() => import("./pages/AccountantPage"));
const FinancialControlCenterPage = React.lazy(
  () => import("./pages/FinancialControlCenterPage"),
);
const SubscriptionsAuditPage = React.lazy(
  () => import("./pages/SubscriptionsPage"),
);

// ─── Route guards ─────────────────────────────────────────────────────────────
const PrivateRoute: React.FC<{
  children: React.ReactNode;
  roles?: Role[];
}> = ({ children, roles }) => {
  const { isAuthenticated, isLoading, user } = useAuthStore();
  if (isLoading) return <Loader fullScreen text="Chargement…" />;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (roles && user && !roles.includes(user.role)) {
    return <Navigate to="/dashboard" replace />;
  }
  return <>{children}</>;
};

const PublicRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, isLoading } = useAuthStore();
  if (isLoading) return <Loader fullScreen text="Chargement…" />;
  if (isAuthenticated) return <Navigate to="/dashboard" replace />;
  return <>{children}</>;
};

// ─── ClientApp ────────────────────────────────────────────────────────────────
const ClientApp: React.FC = () => {
  const { hydrate } = useAuthStore();

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  return (
    <React.Suspense fallback={<Loader fullScreen text="Chargement…" />}>
      <Routes>
        {/* Public auth routes */}
        <Route
          path="/login"
          element={
            <PublicRoute>
              <LoginPage />
            </PublicRoute>
          }
        />
        <Route
          path="/register"
          element={
            <PublicRoute>
              <RegisterPage />
            </PublicRoute>
          }
        />
        <Route
          path="/register-business"
          element={
            <PublicRoute>
              <RegisterBusinessPage />
            </PublicRoute>
          }
        />
        <Route
          path="/forgot-password"
          element={
            <PublicRoute>
              <ForgotPasswordPage />
            </PublicRoute>
          }
        />

        {/* Protected routes — all authenticated users */}
        <Route
          path="/dashboard"
          element={
            <PrivateRoute>
              <DashboardPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/forecast"
          element={
            <PrivateRoute>
              <ForecastPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/alerts"
          element={
            <PrivateRoute>
              <AlertsPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/transactions"
          element={
            <PrivateRoute>
              <TransactionsPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/spending"
          element={
            <PrivateRoute>
              <SpendingPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/bank-connect"
          element={
            <PrivateRoute>
              <BankConnectPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/banking/callback"
          element={
            <PrivateRoute>
              <BankCallbackPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/profile"
          element={
            <PrivateRoute>
              <ProfilePage />
            </PrivateRoute>
          }
        />
        <Route
          path="/subscription"
          element={
            <PrivateRoute>
              <SubscriptionPage />
            </PrivateRoute>
          }
        />

        {/* ROLE_USER only */}
        <Route
          path="/flash-credit"
          element={
            <PrivateRoute roles={["ROLE_USER"]}>
              <FlashCreditPage />
            </PrivateRoute>
          }
        />

        {/* ROLE_BUSINESS only */}
        <Route
          path="/scenarios"
          element={
            <PrivateRoute roles={["ROLE_BUSINESS"]}>
              <ScenarioPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/team"
          element={
            <PrivateRoute roles={["ROLE_BUSINESS"]}>
              <TeamPage />
            </PrivateRoute>
          }
        />

        {/* Financial tools — all authenticated users */}
        <Route
          path="/invoices"
          element={
            <PrivateRoute>
              <InvoicesPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/budget"
          element={
            <PrivateRoute>
              <BudgetPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/tax"
          element={
            <PrivateRoute>
              <TaxPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/benchmarks"
          element={
            <PrivateRoute>
              <BenchmarksPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/forecast-accuracy"
          element={
            <PrivateRoute>
              <ForecastAccuracyPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/payments"
          element={
            <PrivateRoute>
              <PaymentsPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/accountant"
          element={
            <PrivateRoute>
              <AccountantPage />
            </PrivateRoute>
          }
        />

        {/* Financial Decision Engine */}
        <Route
          path="/control-center"
          element={
            <PrivateRoute>
              <FinancialControlCenterPage />
            </PrivateRoute>
          }
        />

        {/* Subscription audit */}
        <Route
          path="/subscriptions"
          element={
            <PrivateRoute>
              <SubscriptionsAuditPage />
            </PrivateRoute>
          }
        />

        {/* Catch-all */}
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </React.Suspense>
  );
};

export default ClientApp;
