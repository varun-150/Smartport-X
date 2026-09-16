import { useEffect, useState, type ReactNode } from 'react';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../lib/authContext';
import { NAV_ITEMS, ROUTES } from '../lib/routes';
import { connectSocket, disconnectSocket, isSocketActive } from '../lib/ws';
import { formatClock } from '../lib/format';
import { Icon, type IconName } from '../components/icons';

const SECTION_LABELS: Record<string, string> = {
  ops: 'Operations',
  admin: 'Admin',
  system: 'System',
};

const ICONS: Record<string, IconName> = {
  '/dashboard': 'dashboard',
  '/containers': 'box',
  '/vessels': 'ship',
  '/yard': 'yard',
  '/gate': 'gate',
  '/notifications': 'bell',
  '/admin/users': 'users',
  '/system': 'wrench',
};

interface AppShellProps {
  children: ReactNode;
}

export function AppShell({ children }: AppShellProps) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const t = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(t);
  }, []);

  useEffect(() => {
    connectSocket();
    return () => {
      disconnectSocket();
    };
  }, []);

  const items = user ? NAV_ITEMS.filter((it) => it.roles.includes(user.role)) : [];
  const sections = Array.from(new Set(items.map((it) => it.section)));
  const current = items.find((it) => it.to === location.pathname);

  const handleLogout = () => {
    logout();
    navigate(ROUTES.login);
  };

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <span className="brand-mark">SPX</span>
          <span>
            <span className="brand-name">SmartPortX</span>
            <span className="brand-sub">Terminal Ops</span>
          </span>
        </div>
        <nav className="sidebar-nav">
          {sections.map((section) => (
            <div key={section}>
              {section !== 'ops' ? <div className="nav-group-title">{SECTION_LABELS[section]}</div> : null}
              {items
                .filter((it) => it.section === section)
                .map((it) => (
                  <NavLink
                    key={it.to}
                    to={it.to}
                    className={({ isActive }) => (isActive ? 'nav-item active' : 'nav-item')}
                  >
                    <span className="nav-icon">
                      <Icon name={ICONS[it.to] ?? 'gauge'} size={16} />
                    </span>
                    {it.label}
                  </NavLink>
                ))}
            </div>
          ))}
        </nav>
        <div className="sidebar-footer">SmartPortX · v0.1.0</div>
      </aside>

      <header className="topbar">
        <span className="topbar-title">{current?.label ?? 'Oversight'}</span>
        <span className="topbar-spacer" />
        <div className="topbar-meta">
          <span className="clock">{formatClock(now)}</span>
          <span
            className={isSocketActive() ? 'ws-dot on' : 'ws-dot'}
            aria-label={isSocketActive() ? 'Live updates connected' : 'Live updates disconnected'}
          />
          <span className="user-chip">
            <span className="role-tag">{user?.role ?? 'GUEST'}</span>
            <span>{user?.displayName ?? user?.username ?? 'Operator'}</span>
          </span>
          <button className="btn btn-ghost btn-sm" type="button" onClick={handleLogout}>
            <Icon name="logout" size={14} />
            <span>Sign out</span>
          </button>
        </div>
      </header>

      <main className="main">{children}</main>
    </div>
  );
}