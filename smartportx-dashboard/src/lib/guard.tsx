import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from './authContext';
import { ROUTES } from './routes';
import { Forbidden } from '../components/Forbidden';

export function RequireAuth({ children }: { children: ReactNode }): ReactNode {
  const { authenticated } = useAuth();
  const location = useLocation();

  if (!authenticated) {
    return <Navigate to={ROUTES.login} state={{ from: location }} replace />;
  }
  return children;
}

export function RequireRoles({
  roles,
  children,
}: {
  roles: readonly string[];
  children: ReactNode;
}): ReactNode {
  const { authenticated, hasRole } = useAuth();
  const location = useLocation();

  if (!authenticated) {
    return <Navigate to={ROUTES.login} state={{ from: location }} replace />;
  }
  if (!hasRole(...roles)) {
    return <Forbidden />;
  }
  return children;
}

export function RedirectIfAuthenticated({ children }: { children: ReactNode }): ReactNode {
  const { authenticated } = useAuth();

  if (authenticated) {
    return <Navigate to={ROUTES.dashboard} replace />;
  }
  return children;
}