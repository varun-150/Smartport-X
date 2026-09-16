import { Suspense, lazy } from 'react';
import { Route, Routes } from 'react-router-dom';
import { ROUTES } from './lib/routes';
import { RedirectIfAuthenticated, RequireAuth, RequireRoles } from './lib/guard';
import { AppShell } from './layouts/AppShell';
import { Spinner } from './components/icons';

const LoginPage = lazy(() => import('./pages/Login'));
const DashboardPage = lazy(() => import('./pages/Dashboard'));
const ContainersPage = lazy(() => import('./pages/Containers'));
const YardPage = lazy(() => import('./pages/Yard'));
const GatePage = lazy(() => import('./pages/Gate'));
const VesselsPage = lazy(() => import('./pages/Vessels'));
const NotificationsPage = lazy(() => import('./pages/Notifications'));
const AdminUsersPage = lazy(() => import('./pages/AdminUsers'));
const SystemPage = lazy(() => import('./pages/System'));
const NotFoundPage = lazy(() => import('./pages/NotFound'));

const ALL_ROLES = ['ADMIN', 'TERMINAL_OP', 'YARD_MANAGER', 'GATE_OPERATOR', 'READONLY'];
const OPS_ROLES = ['ADMIN', 'TERMINAL_OP', 'YARD_MANAGER', 'GATE_OPERATOR', 'READONLY'];
const VESSEL_ROLES = ['ADMIN', 'TERMINAL_OP', 'READONLY'];
const YARD_ROLES = ['ADMIN', 'TERMINAL_OP', 'YARD_MANAGER'];
const GATE_ROLES = ['ADMIN', 'GATE_OPERATOR'];
const ADMIN_ROLES = ['ADMIN'];

function LoadingScreen() {
  return (
    <div className="empty-state" style={{ paddingTop: 80 }}>
      <Spinner size={28} />
    </div>
  );
}

export default function App() {
  return (
    <Suspense fallback={<LoadingScreen />}>
      <Routes>
        <Route
          path={ROUTES.login}
          element={
            <RedirectIfAuthenticated>
              <LoginPage />
            </RedirectIfAuthenticated>
          }
        />
        <Route
          path={ROUTES.dashboard}
          element={
            <RequireAuth>
              <AppShell>
                <DashboardPage />
              </AppShell>
            </RequireAuth>
          }
        />
        <Route
          path={ROUTES.containers}
          element={
            <RequireRoles roles={ALL_ROLES}>
              <AppShell>
                <ContainersPage />
              </AppShell>
            </RequireRoles>
          }
        />
        <Route
          path={ROUTES.yard}
          element={
            <RequireRoles roles={YARD_ROLES}>
              <AppShell>
                <YardPage />
              </AppShell>
            </RequireRoles>
          }
        />
        <Route
          path={ROUTES.gate}
          element={
            <RequireRoles roles={GATE_ROLES}>
              <AppShell>
                <GatePage />
              </AppShell>
            </RequireRoles>
          }
        />
        <Route
          path={ROUTES.vessels}
          element={
            <RequireRoles roles={VESSEL_ROLES}>
              <AppShell>
                <VesselsPage />
              </AppShell>
            </RequireRoles>
          }
        />
        <Route
          path={ROUTES.notifications}
          element={
            <RequireRoles roles={OPS_ROLES}>
              <AppShell>
                <NotificationsPage />
              </AppShell>
            </RequireRoles>
          }
        />
        <Route
          path={ROUTES.adminUsers}
          element={
            <RequireRoles roles={ADMIN_ROLES}>
              <AppShell>
                <AdminUsersPage />
              </AppShell>
            </RequireRoles>
          }
        />
        <Route
          path={ROUTES.system}
          element={
            <RequireRoles roles={ADMIN_ROLES}>
              <AppShell>
                <SystemPage />
              </AppShell>
            </RequireRoles>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  );
}