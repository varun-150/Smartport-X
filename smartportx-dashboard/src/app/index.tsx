// App composition: providers + application shell routing.

import { Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { RequireAuth, RequireRoles } from '../lib/guards'
import { AppLayout } from './layout'
import { LoginPage } from '../features/auth/LoginPage'
import { DashboardPage } from '../features/dashboard/DashboardPage'
import { ContainersPage, ContainerDetailPage } from '../features/containers'
import { YardPage, YardEquipmentPage } from '../features/yard'
import { GatePage, BookAppointmentPage } from '../features/gate'
import { VesselsPage, VesselDetailPage } from '../features/vessels'
import { NotificationsPage } from '../features/notifications/NotificationsPage'
import { TestimonialsPage } from '../features/testimonials/TestimonialsPage'
import { UsersPage, CreateUserPage, EditUserPage } from '../features/admin'
import { SystemPage } from '../features/system/SystemPage'
import { NotFoundPage, ForbiddenPage } from '../features/errors'

export function App() {
  return (
    <Routes>
      {/* Public */}
      <Route path="/login" element={<LoginPage />} />

      {/* Auth-gated application shell */}
      <Route element={<RequireAuth><Outlet /></RequireAuth>}>
        <Route element={<AppLayout />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="containers" element={<ContainersPage />} />
          <Route path="containers/:containerId" element={<ContainerDetailPage />} />
          <Route path="yard" element={<YardPage />} />
          <Route path="yard/equipment" element={<YardEquipmentPage />} />
          <Route path="gate" element={<GatePage />} />
          <Route path="gate/book" element={<BookAppointmentPage />} />
          <Route path="vessels" element={<VesselsPage />} />
          <Route path="vessels/:vesselId" element={<VesselDetailPage />} />
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="testimonials" element={<TestimonialsPage />} />

          {/* Admin-only area */}
          <Route element={<RequireRoles roles={['ADMIN']}><Outlet /></RequireRoles>}>
            <Route path="admin/users" element={<UsersPage />} />
            <Route path="admin/users/new" element={<CreateUserPage />} />
            <Route path="admin/users/:userId/edit" element={<EditUserPage />} />
          </Route>

          {/* System page — service health (all roles can view read-only status) */}
          <Route path="system" element={<SystemPage />} />
          <Route path="forbidden" element={<ForbiddenPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}