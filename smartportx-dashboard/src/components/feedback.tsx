import type { ReactNode } from 'react';
import { Icon, type IconName } from './icons';
import { isApiError } from '../types/api';

export function errorMessage(err: unknown): string {
  if (isApiError(err)) {
    const body = err.body;
    if (body?.fieldErrors) {
      const first = Object.values(body.fieldErrors)[0];
      if (first) return first;
    }
    return err.message;
  }
  if (err instanceof Error && err.message) return err.message;
  return 'Something went wrong';
}

interface ErrorBannerProps {
  error: unknown;
  onRetry?: () => void;
}

export function ErrorBanner({ error, onRetry }: ErrorBannerProps) {
  return (
    <div className="banner banner-error" role="alert">
      <Icon name="alert" size={14} />
      <span>{errorMessage(error)}</span>
      {onRetry ? (
        <button className="btn btn-ghost btn-sm" type="button" onClick={onRetry}>
          Retry
        </button>
      ) : null}
    </div>
  );
}

interface EmptyStateProps {
  icon?: IconName;
  title?: ReactNode;
  children?: ReactNode;
}

export function EmptyState({ icon = 'box', title = 'No data', children }: EmptyStateProps) {
  return (
    <div className="empty-state">
      <Icon name={icon} size={22} />
      <div style={{ marginTop: 4 }}>{title}</div>
      {children}
    </div>
  );
}