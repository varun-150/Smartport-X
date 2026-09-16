import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Icon, Spinner, type IconName } from './icons';

type Variant = 'default' | 'primary' | 'ghost' | 'danger';
type Size = 'sm' | 'md';
type Tone = 'neutral' | 'ok' | 'warn' | 'danger' | 'info' | 'accent';

export type { Tone };

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  icon?: IconName;
  iconOnly?: boolean;
  loading?: boolean;
}

export function Button({
  variant = 'default',
  size = 'md',
  icon,
  iconOnly = false,
  loading = false,
  disabled,
  className = '',
  children,
  type = 'button',
  ...rest
}: ButtonProps) {
  const classes = [
    'btn',
    variant !== 'default' ? `btn-${variant}` : '',
    size === 'sm' ? 'btn-sm' : '',
    iconOnly ? 'btn-icon' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <button
      className={classes}
      disabled={disabled || loading}
      type={type}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading ? <Spinner size={size === 'sm' ? 13 : 15} /> : icon ? <Icon name={icon} /> : null}
      {!iconOnly && children ? <span>{children}</span> : null}
    </button>
  );
}

interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  icon: IconName;
  label: string;
  size?: Size;
}

export function IconButton({ icon, label, size = 'md', className = '', ...rest }: IconButtonProps) {
  return (
    <button
      className={['btn', size === 'sm' ? 'btn-sm' : '', 'btn-icon', className].join(' ')}
      aria-label={label}
      title={label}
      type="button"
      {...rest}
    >
      <Icon name={icon} />
    </button>
  );
}

interface BadgeProps {
  tone?: Tone;
  children: ReactNode;
  className?: string;
}

export function Badge({ tone = 'neutral', children, className = '' }: BadgeProps) {
  const cls = tone === 'neutral' ? 'badge' : `badge badge-${tone}`;
  return <span className={`${cls} ${className}`.trim()}>{children}</span>;
}

export function statusTone(status: string | null | undefined): Tone {
  const s = (status ?? '').toUpperCase();
  switch (s) {
    case 'ACTIVE':
    case 'AVAILABLE':
    case 'COMPLETED':
    case 'CONFIRMED':
    case 'ARRIVED':
    case 'LOADED':
    case 'DEPARTED':
    case 'DELIVERED':
    case 'OK':
      return 'ok';
    case 'PENDING':
    case 'SCHEDULED':
    case 'IN_TRANSIT':
    case 'PLANNED':
    case 'AT_GATE':
    case 'AT_GATE_OUT':
    case 'INACTIVE':
      return 'info';
    case 'WORKING':
    case 'IN_PROGRESS':
    case 'CHECKED_IN':
    case 'IN_YARD':
    case 'REGISTERED':
    case 'ASSIGNED':
      return 'accent';
    case 'HELD':
    case 'OUT_OF_SERVICE':
    case 'FAILED':
    case 'LOCKED':
    case 'NO_SHOW':
    case 'CRITICAL':
    case 'ERROR':
    case 'DANGEROUS':
      return 'danger';
    case 'CANCELLED':
    case 'EMPTY':
      return 'neutral';
    default:
      return 'info';
  }
}

interface StatusBadgeProps {
  status: string | null | undefined;
  tone?: Tone;
}

export function StatusBadge({ status, tone }: StatusBadgeProps) {
  if (!status) return <Badge tone="neutral">&#8211;</Badge>;
  const label = status.replace(/_/g, ' ');
  return <Badge tone={tone ?? statusTone(status)}>{label}</Badge>;
}

interface PanelProps {
  title?: ReactNode;
  subtitle?: ReactNode;
  actions?: ReactNode;
  flush?: boolean;
  className?: string;
  children: ReactNode;
}

export function Panel({ title, subtitle, actions, flush, className = '', children }: PanelProps) {
  return (
    <section className={`panel ${className}`.trim()}>
      {title || actions ? (
        <div className="panel-header">
          <div>
            {title ? <h3 className="panel-title">{title}</h3> : null}
            {subtitle ? <p className="panel-subtitle">{subtitle}</p> : null}
          </div>
          {actions ? <div className="panel-actions">{actions}</div> : null}
        </div>
      ) : null}
      <div className={flush ? 'panel-body flush' : 'panel-body'}>{children}</div>
    </section>
  );
}

interface PageHeaderProps {
  title: ReactNode;
  subtitle?: ReactNode;
  actions?: ReactNode;
}

export function PageHeader({ title, subtitle, actions }: PageHeaderProps) {
  return (
    <div className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle ? <p>{subtitle}</p> : null}
      </div>
      {actions ? <div className="page-actions">{actions}</div> : null}
    </div>
  );
}

export function Skeleton({ width = '100%', height = 14 }: { width?: number | string; height?: number }) {
  return (
    <span className="skeleton" style={{ width, height }} aria-hidden="true" data-testid="skeleton" />
  );
}

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  footer?: ReactNode;
  size?: 'md' | 'lg';
  children: ReactNode;
}

export function Modal({ open, onClose, title, footer, size = 'md', children }: ModalProps) {
  if (!open) return null;
  return (
    <div className="overlay" role="presentation" onMouseDown={onClose}>
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-label={typeof title === 'string' ? title : 'Dialog'}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <h3>{title}</h3>
          <IconButton icon="close" label="Close" onClick={onClose} />
        </div>
        <div className={`modal-body ${size === 'lg' ? 'modal-body-lg' : ''}`}>{children}</div>
        {footer ? <div className="modal-footer">{footer}</div> : null}
      </div>
    </div>
  );
}