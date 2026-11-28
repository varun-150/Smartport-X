// Application shell: dark rail + topbar + paper canvas. Nav is role-aware.

import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { type ReactNode } from 'react'
import { useAuth } from '../lib/auth'
import { useRealtime } from '../lib/realtime'
import { tokenStore } from '../lib/token'
import {
  IconBox,
  IconSignOut,
  IconYard,
  IconGate,
  IconVessel,
  IconBell,
  IconUsers,
  IconSystem,
  IconDashboard,
  IconQuote,
} from '../ui/icons'

interface NavEntry {
  to: string
  label: string
  icon: ReactNode
  roles?: string[]
  match: (pathname: string) => boolean
}

const NAV: NavEntry[] = [
  { to: '/dashboard', label: 'Overview', icon: <IconDashboard size={16} />, match: (p) => p === '/dashboard' },
  { to: '/containers', label: 'Containers', icon: <IconBox size={16} />, match: (p) => p.startsWith('/containers') },
  { to: '/yard', label: 'Yard', icon: <IconYard size={16} />, match: (p) => p.startsWith('/yard') },
  { to: '/gate', label: 'Gate', icon: <IconGate size={16} />, match: (p) => p.startsWith('/gate') },
  { to: '/vessels', label: 'Vessels', icon: <IconVessel size={16} />, match: (p) => p.startsWith('/vessels') },
  { to: '/notifications', label: 'Alerts', icon: <IconBell size={16} />, match: (p) => p.startsWith('/notifications') },
  { to: '/testimonials', label: 'Testimonials', icon: <IconQuote size={16} />, match: (p) => p.startsWith('/testimonials') },
  { to: '/admin/users', label: 'Users', icon: <IconUsers size={16} />, roles: ['ADMIN'], match: (p) => p.startsWith('/admin') },
  { to: '/system', label: 'System', icon: <IconSystem size={16} />, match: (p) => p.startsWith('/system') },
]

const PAGE_TITLES: Array<{ prefix: string; title: string; kicker: string }> = [
  { prefix: '/dashboard', title: 'Terminal Overview', kicker: 'Dashboard' },
  { prefix: '/containers', title: 'Container Operations', kicker: 'Containers' },
  { prefix: '/yard', title: 'Yard Operations', kicker: 'Yard' },
  { prefix: '/gate', title: 'Gate & Appointments', kicker: 'Gate' },
  { prefix: '/vessels', title: 'Vessel Movements', kicker: 'Vessels' },
  { prefix: '/notifications', title: 'Notifications', kicker: 'Alerts' },
  { prefix: '/testimonials', title: 'Customer Testimonials', kicker: 'Testimonials' },
  { prefix: '/admin', title: 'User Administration', kicker: 'Admin' },
  { prefix: '/system', title: 'System Health', kicker: 'System' },
]

const ROLE_LABEL: Record<string, string> = {
  ADMIN: 'Administrator',
  TERMINAL_OP: 'Terminal Operator',
  YARD_MANAGER: 'Yard Manager',
  GATE_OPERATOR: 'Gate Operator',
  READONLY: 'Read Only',
}

function pageHeader(pathname: string): { title: string; kicker: string } {
  const hit = PAGE_TITLES.find((p) => pathname.startsWith(p.prefix))
  return hit ?? { title: 'SmartPortX', kicker: 'Operations' }
}

function ConnectionChip() {
  const { status } = useRealtime()
  const map: Record<string, { cls: string; text: string }> = {
    CONNECTED: { cls: 'on', text: 'LIVE' },
    CONNECTING: { cls: '', text: 'CONNECTING' },
    RECONNECTING: { cls: '', text: 'RECONNECTING' },
    DEGRADED: { cls: 'off', text: 'OFFLINE' },
    DISABLED: { cls: 'off', text: 'SIGNED OUT' },
  }
  const s = map[status] ?? { cls: '', text: 'UNKNOWN' }
  return (
    <span className={`conn-chip ${s.cls}`} title={`Realtime feed status: ${status}`}>
      {s.text}
    </span>
  )
}

export function AppLayout() {
  const { pathname } = useLocation()
  const { user } = useAuth()
  const header = pageHeader(pathname)
  const visibleNav = NAV.filter((n) => !n.roles || (user?.role && n.roles.includes(user.role)))

  return (
    <div className="app-frame">
      <aside className="rail">
        <div className="rail-brand">
          <span className="mark" aria-hidden />
          <div>
            <div className="rail-brand-name">SmartPortX</div>
            <div className="rail-brand-sub">Terminal Ops</div>
          </div>
        </div>
        <nav className="nav-list" aria-label="Primary">
          {visibleNav.map((n) => (
            <NavLink key={n.to} to={n.to} className={({ isActive }) => `nav-item ${isActive || n.match(pathname) ? 'active' : ''}`}>
              {n.icon}
              {n.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <header className="topbar">
        <div className="page-id">
          <span className="page-kicker">{header.kicker}</span>
          <span className="page-title">{header.title}</span>
        </div>
        <div className="spacer" />
        {user ? (
          <div className="user-chip">
            <span>{user.username}</span>
            <span className="role">{ROLE_LABEL[user.role] ?? user.role}</span>
          </div>
        ) : null}
        <ConnectionChip />
        <button
          type="button"
          className="btn btn-ghost btn-sm"
          aria-label="Sign out"
          onClick={() => {
            tokenStore.clear()
            window.location.assign('/login')
          }}
        >
          <IconSignOut size={15} />
          Sign out
        </button>
      </header>

      <main className="canvas">
        <Outlet />
      </main>
    </div>
  )
}