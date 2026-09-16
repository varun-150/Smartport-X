export const ROUTES = {
  login: '/login',
  dashboard: '/dashboard',
  containers: '/containers',
  yard: '/yard',
  gate: '/gate',
  vessels: '/vessels',
  notifications: '/notifications',
  adminUsers: '/admin/users',
  system: '/system',
} as const;

export type RouteKey = keyof typeof ROUTES;

export interface NavItem {
  to: string;
  label: string;
  roles: readonly string[];
  section: 'ops' | 'admin' | 'system';
}

export const NAV_ITEMS: NavItem[] = [
  {
    to: ROUTES.dashboard,
    label: 'Dashboard',
    roles: ['ADMIN', 'TERMINAL_OP', 'YARD_MANAGER', 'GATE_OPERATOR', 'READONLY'],
    section: 'ops',
  },
  {
    to: ROUTES.containers,
    label: 'Containers',
    roles: ['ADMIN', 'TERMINAL_OP', 'YARD_MANAGER', 'GATE_OPERATOR', 'READONLY'],
    section: 'ops',
  },
  {
    to: ROUTES.vessels,
    label: 'Vessels',
    roles: ['ADMIN', 'TERMINAL_OP', 'READONLY'],
    section: 'ops',
  },
  {
    to: ROUTES.yard,
    label: 'Yard',
    roles: ['ADMIN', 'TERMINAL_OP', 'YARD_MANAGER'],
    section: 'ops',
  },
  {
    to: ROUTES.gate,
    label: 'Gate',
    roles: ['ADMIN', 'GATE_OPERATOR'],
    section: 'ops',
  },
  {
    to: ROUTES.notifications,
    label: 'Alerts',
    roles: ['ADMIN', 'TERMINAL_OP', 'YARD_MANAGER', 'GATE_OPERATOR', 'READONLY'],
    section: 'ops',
  },
  {
    to: ROUTES.adminUsers,
    label: 'Users',
    roles: ['ADMIN'],
    section: 'admin',
  },
  {
    to: ROUTES.system,
    label: 'System',
    roles: ['ADMIN'],
    section: 'system',
  },
];

export function navForRoles(roles: readonly string[]): NavItem[] {
  return NAV_ITEMS.filter((item) => item.roles.some((r) => roles.includes(r)));
}