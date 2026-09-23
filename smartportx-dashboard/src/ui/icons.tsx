// Inline SVG icon set (stroke-based, currentColor). No icon library dependency.

import type { SVGProps } from 'react'

type IconProps = SVGProps<SVGSVGElement> & { size?: number }

function base({ size = 16, ...props }: IconProps): SVGProps<SVGSVGElement> {
  return {
    width: size,
    height: size,
    viewBox: '0 0 16 16',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 1.5,
    strokeLinecap: 'round',
    strokeLinejoin: 'round',
    'aria-hidden': true,
    ...props,
  }
}

export function IconDashboard(props: IconProps) {
  return (
    <svg {...base(props)}>
      <rect x="1.5" y="1.5" width="5" height="5" />
      <rect x="9.5" y="1.5" width="5" height="5" />
      <rect x="1.5" y="9.5" width="5" height="5" />
      <rect x="9.5" y="9.5" width="5" height="5" />
    </svg>
  )
}

export function IconBox(props: IconProps) {
  return (
    <svg {...base(props)}>
      <path d="M2.5 5.5 8 2.6l5.5 2.9v7L8 15.4l-5.5-3z" />
      <path d="M2.5 5.5 8 8.4l5.5-2.9M8 8.4v7" />
    </svg>
  )
}

export function IconYard(props: IconProps) {
  return (
    <svg {...base(props)}>
      <path d="M2.5 12V5.5L8 2.6l5.5 2.9v6.5" />
      <path d="M2.5 8h11M2.5 12h11" />
    </svg>
  )
}

export function IconGate(props: IconProps) {
  return (
    <svg {...base(props)}>
      <path d="M3 13V4.5l5-2 5 2V13M2 13h12" />
      <path d="M7 8h2M8 8v3" />
    </svg>
  )
}

export function IconVessel(props: IconProps) {
  return (
    <svg {...base(props)}>
      <path d="M8 2.5 13 12H3z" />
      <path d="M8 5v3M2 12v1.5M14 12v1.5" />
    </svg>
  )
}

export function IconBell(props: IconProps) {
  return (
    <svg {...base(props)}>
      <path d="M8 2.5A3 3 0 0 0 5 5.5V8l-1.5 2.5h9L11 8V5.5a3 3 0 0 0-3-3z" />
      <path d="M6.5 13a1.5 1.5 0 0 0 3 0" />
    </svg>
  )
}

export function IconUsers(props: IconProps) {
  return (
    <svg {...base(props)}>
      <circle cx="5.5" cy="5.5" r="2" />
      <circle cx="11.5" cy="6" r="1.8" />
      <path d="M1.7 13a4.2 4.2 0 0 1 7.6-1.7M9.3 11.4A4.2 4.2 0 0 1 15 13" />
    </svg>
  )
}

export function IconSystem(props: IconProps) {
  return (
    <svg {...base(props)}>
      <rect x="2.5" y="2.5" width="11" height="11" />
      <path d="M5.5 6.5v3M8 5v6M10.5 6.5v3" />
    </svg>
  )
}

export function IconSignOut(props: IconProps) {
  return (
    <svg {...base(props)}>
      <path d="M9.5 4.5V2.5h-6v11h6v-2M6 8h8.5M12 5.5 14.5 8 12 10.5" />
    </svg>
  )
}

export function IconSearch(props: IconProps) {
  return (
    <svg {...base(props)}>
      <circle cx="6.5" cy="6.5" r="4" />
      <path d="m9.5 9.5 4 4" />
    </svg>
  )
}

export function IconRefresh(props: IconProps) {
  return (
    <svg {...base(props)}>
      <path d="M13 8a5 5 0 1 1-1.7-3.8M13 1.8V5h-3.2" />
    </svg>
  )
}

export function IconAlert(props: IconProps) {
  return (
    <svg {...base(props)}>
      <path d="M8 2.5 14.5 13H1.5z" />
      <path d="M8 6.5v3M8 11.5h.01" />
    </svg>
  )
}

export function IconInfo(props: IconProps) {
  return (
    <svg {...base(props)}>
      <circle cx="8" cy="8" r="6.5" />
      <path d="M8 7v4M8 5h.01" />
    </svg>
  )
}

export function IconChevron(props: IconProps) {
  return (
    <svg {...base(props)}>
      <path d="m4.5 10 3.5-3.5L11.5 10" />
    </svg>
  )
}

export function IconChevronLeft(props: IconProps) {
  return (
    <svg {...base(props)}>
      <path d="M11.5 10 8 6.5 4.5 10" />
    </svg>
  )
}

export function IconX(props: IconProps) {
  return (
    <svg {...base(props)}>
      <path d="M4 4l8 8M12 4l-8 8" />
    </svg>
  )
}

export function IconQuote(props: IconProps) {
  return (
    <svg {...base(props)}>
      <path d="M3.5 2.5h9a1.5 1.5 0 0 1 1.5 1.5v5a1.5 1.5 0 0 1-1.5 1.5H8L5.5 13V10.5H3.5A1.5 1.5 0 0 1 2 9V4a1.5 1.5 0 0 1 1.5-1.5z" />
      <path d="M5 5.5h6M5 8h4" />
    </svg>
  )
}