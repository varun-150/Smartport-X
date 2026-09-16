import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { authApi } from './authApi';
import { tokenStore } from './token';
import { setAuthLostHandler } from './http';
import { disconnectSocket } from './ws';
import type { LoginResponse, UserResponse } from '../types/auth';

interface AuthContextValue {
  user: UserResponse | null;
  authenticated: boolean;
  login: (username: string, password: string) => Promise<UserResponse>;
  logout: () => void;
  hasPermission: (permission: string) => boolean;
  hasRole: (...roles: string[]) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }): ReactNode {
  const [user, setUser] = useState<UserResponse | null>(null);

  useEffect(() => {
    setAuthLostHandler(() => {
      tokenStore.clear();
      setUser(null);
    });
    return () => {
      disconnectSocket();
    };
  }, []);

  const login = useCallback(async (username: string, password: string): Promise<UserResponse> => {
    try {
      const res = await authApi.login({ username, password });
      applySession(res);
      setUser(res.user);
      return res.user;
    } catch (err) {
      tokenStore.clear();
      setUser(null);
      throw err;
    }
  }, []);

  const logout = useCallback(() => {
    tokenStore.clear();
    setUser(null);
    disconnectSocket();
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      authenticated: user !== null,
      login,
      logout,
      hasPermission: (permission: string) => user?.permissions.includes(permission) ?? false,
      hasRole: (...roles: string[]) => (user ? roles.includes(user.role) : false),
    }),
    [user, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

function applySession(res: LoginResponse): void {
  tokenStore.set({
    accessToken: res.accessToken,
    refreshToken: res.refreshToken,
    expiresIn: res.expiresIn,
    tokenType: res.tokenType,
  });
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}