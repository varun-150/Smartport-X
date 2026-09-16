import type { ReactNode, SVGProps } from 'react';

export type IconName =
  | 'dashboard'
  | 'box'
  | 'ship'
  | 'yard'
  | 'gate'
  | 'bell'
  | 'users'
  | 'wrench'
  | 'gauge'
  | 'logout'
  | 'refresh'
  | 'close'
  | 'chevron-down'
  | 'search'
  | 'check'
  | 'alert'
  | 'plus'
  | 'arrow-in'
  | 'arrow-out'
  | 'clock'
  | 'info'
  | 'grid';

const PATHS: Record<IconName, ReactNode> = {
  dashboard: (
    <>
      <rect x="3" y="3" width="7" height="7" rx="1" />
      <rect x="14" y="3" width="7" height="7" rx="1" />
      <rect x="3" y="14" width="7" height="7" rx="1" />
      <rect x="14" y="14" width="7" height="7" rx="1" />
    </>
  ),
  box: (
    <>
      <path d="M21 8l-9-5-9 5v8l9 5 9-5V8z" />
      <path d="M3.6 8.3L12 13l8.4-4.7" />
      <path d="M12 13v8" />
    </>
  ),
  ship: (
    <>
      <path d="M2 21h20" />
      <path d="M4 18l-1-6 3.5 1.5L12 9l5.5 4.5L21 12l-1 6H4z" />
      <path d="M12 9V5" />
      <path d="M9 5h6" />
    </>
  ),
  yard: (
    <>
      <rect x="3" y="3" width="18" height="18" rx="1.5" />
      <path d="M3 9h18M3 15h18M9 3v18M15 3v18" />
    </>
  ),
  gate: (
    <>
      <path d="M4 20V8a8 8 0 0 1 16 0v12" />
      <path d="M2 20h20" />
      <path d="M9 20v-6a3 3 0 0 1 6 0v6" />
    </>
  ),
  bell: (
    <>
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 8-3 8h18s-3-1-3-8" />
      <path d="M13.7 21a2 2 0 0 1-3.4 0" />
    </>
  ),
  users: (
    <>
      <circle cx="9" cy="8" r="3.5" />
      <path d="M2.5 20a6.5 6.5 0 0 1 13 0" />
      <path d="M16 5a3.5 3.5 0 0 1 0 6" />
      <path d="M17.5 14.5a6.5 6.5 0 0 1 4 7" />
    </>
  ),
  wrench: (
    <>
      <path d="M14.7 6.3a4.5 4.5 0 0 0-6 5.6L3 17.6V21h3.4l5.7-5.7a4.5 4.5 0 0 0 5.6-6L14.5 12 12 9.5l2.7-3.2z" />
    </>
  ),
  gauge: (
    <>
      <path d="M4.5 20a9 9 0 1 1 15 0" />
      <path d="M12 14l4-4" />
      <circle cx="12" cy="14" r="1.5" />
    </>
  ),
  logout: (
    <>
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <path d="M16 17l5-5-5-5" />
      <path d="M21 12H9" />
    </>
  ),
  refresh: (
    <>
      <path d="M21 12a9 9 0 1 1-2.6-6.4" />
      <path d="M21 3v6h-6" />
    </>
  ),
  close: <path d="M18 6L6 18M6 6l12 12" />,
  'chevron-down': <path d="M6 9l6 6 6-6" />,
  search: (
    <>
      <circle cx="11" cy="11" r="7" />
      <path d="M21 21l-4.3-4.3" />
    </>
  ),
  check: <path d="M20 6L9 17l-5-5" />,
  alert: (
    <>
      <path d="M12 9v4" />
      <path d="M12 17h.01" />
      <path d="M10.3 3.8L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.8a2 2 0 0 0-3.4 0z" />
    </>
  ),
  plus: <path d="M12 5v14M5 12h14" />,
  'arrow-in': (
    <>
      <path d="M21 12H9" />
      <path d="M13 8l-4 4 4 4" />
      <path d="M3 3v18" />
    </>
  ),
  'arrow-out': (
    <>
      <path d="M21 12H9" />
      <path d="M13 8l-4 4 4 4" />
      <path d="M21 3v18" />
    </>
  ),
  clock: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3.5 2" />
    </>
  ),
  info: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 16v-5" />
      <path d="M12 8h.01" />
    </>
  ),
  grid: (
    <>
      <rect x="3" y="3" width="7" height="7" rx="1" />
      <rect x="14" y="3" width="7" height="7" rx="1" />
      <rect x="3" y="14" width="7" height="7" rx="1" />
      <rect x="14" y="14" width="7" height="7" rx="1" />
    </>
  ),
};

interface IconProps extends SVGProps<SVGSVGElement> {
  name: IconName;
  size?: number;
}

export function Icon({ name, size = 16, ...rest }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...rest}
    >
      {PATHS[name]}
    </svg>
  );
}

export function Spinner({ size = 16 }: { size?: number }) {
  return <span className="spinner" style={{ width: size, height: size }} aria-label="Loading" role="status" />;
}