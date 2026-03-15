import React, { lazy, Suspense, useEffect } from "react";
import { Routes, Route, Navigate } from "react-router-dom";
import { Loader } from "@/components/ui/Loader";
import { useAuthStore } from "@/store/authStore";

const AdminLoginPage = lazy(() => import("./pages/auth/AdminLoginPage"));
const DashboardPage = lazy(() => import("./pages/DashboardPage"));
const UsersPage = lazy(() => import("./pages/UsersPage"));
const UserDetailPage = lazy(() => import("./pages/UserDetailPage"));
const FlashCreditsPage = lazy(() => import("./pages/FlashCreditsPage"));
const AlertsPage = lazy(() => import("./pages/AlertsPage"));
const KpisPage = lazy(() => import("./pages/KpisPage"));
const MLStatsPage = lazy(() => import("./pages/MLStatsPage"));
const AdminFlagsPage = lazy(() => import("./pages/AdminFlagsPage"));
const AdminConfigPage = lazy(() => import("./pages/AdminConfigPage"));
const AdminManagePage = lazy(() => import("./pages/AdminManagePage"));
const AuditLogPage = lazy(() => import("./pages/AuditLogPage"));

/** Requires any admin (ROLE_ADMIN or ROLE_SUPER_ADMIN). */
const AdminPrivateRoute: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const { isAuthenticated, isAdmin, isLoading } = useAuthStore();
  if (isLoading) return <Loader fullScreen />;
  if (!isAuthenticated || !isAdmin)
    return <Navigate to="/admin/login" replace />;
  return <>{children}</>;
};

const AdminApp: React.FC = () => {
  const hydrate = useAuthStore((s) => s.hydrate);
  useEffect(() => {
    hydrate();
  }, [hydrate]);

  return (
    <Suspense fallback={<Loader fullScreen />}>
      <Routes>
        <Route path="login" element={<AdminLoginPage />} />
        <Route
          path="*"
          element={
            <AdminPrivateRoute>
              <Routes>
                <Route path="/" element={<Navigate to="dashboard" replace />} />
                <Route path="dashboard" element={<DashboardPage />} />
                <Route path="users" element={<UsersPage />} />
                <Route path="users/:userId" element={<UserDetailPage />} />
                <Route path="credits" element={<FlashCreditsPage />} />
                <Route path="alerts" element={<AlertsPage />} />
                <Route path="kpis" element={<KpisPage />} />
                <Route path="ml" element={<MLStatsPage />} />
                {/* Super-admin routes — visible but locked for ROLE_ADMIN */}
                <Route path="flags" element={<AdminFlagsPage />} />
                <Route path="config" element={<AdminConfigPage />} />
                <Route path="admins" element={<AdminManagePage />} />
                <Route path="audit" element={<AuditLogPage />} />
              </Routes>
            </AdminPrivateRoute>
          }
        />
      </Routes>
    </Suspense>
  );
};

export default AdminApp;
