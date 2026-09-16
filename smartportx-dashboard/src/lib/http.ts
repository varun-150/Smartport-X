import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios';
import { env } from '../config/env';
import { tokenStore } from './token';
import { ApiError, type ApiErrorBody } from '../types/api';

const apiBase = `${env.apiBaseUrl}/api`;

export const client = axios.create({
  baseURL: apiBase,
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
});

client.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenStore.get()?.accessToken;
  if (token) config.headers.set('Authorization', `Bearer ${token}`);
  return config;
});

type RetriableConfig = AxiosRequestConfig & { _retried?: boolean };

client.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiErrorBody>) => {
    const original = error.config as RetriableConfig | undefined;
    if (!original) throw error;

    const status = error.response?.status;
    const method = error.config?.method;
    const url = error.config?.url;

    const loginCall = method === 'post' && (url ?? '').includes('/auth/login');
    const refreshCall = method === 'post' && (url ?? '').includes('/auth/refresh');

    if (status === 401 && !loginCall && !refreshCall && tokenStore.has() && !original._retried) {
      original._retried = true;
      const refreshed = await tryRefresh();
      if (refreshed) {
        original.headers = original.headers ?? {};
        (original.headers as Record<string, string>)['Authorization'] =
          `Bearer ${tokenStore.get()?.accessToken}`;
        return client.request(original);
      }
    }

    throw new ApiError(
      status ?? 0,
      error.response?.data ?? {},
      error.response?.data?.message,
    );
  },
);

async function tryRefresh(): Promise<boolean> {
  const pair = tokenStore.get();
  if (!pair) return false;
  try {
    const res = await client.post<{ accessToken: string; refreshToken: string; expiresIn: number }>(
      '/auth/refresh',
      { refreshToken: pair.refreshToken },
      { _retried: true } as AxiosRequestConfig,
    );
    tokenStore.set({
      accessToken: res.data.accessToken,
      refreshToken: res.data.refreshToken,
      expiresIn: res.data.expiresIn,
      tokenType: 'Bearer',
    });
    return true;
  } catch {
    tokenStore.clear();
    return false;
  }
}

export function setAuthLostHandler(fn: () => void): void {
  client.interceptors.response.use(undefined, (error: unknown) => {
    const apiErr = error as { status?: number };
    if (apiErr && apiErr.status === 401 && !tokenStore.has()) {
      fn();
    }
    return Promise.reject(error);
  });
}

export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const res = await client.get<T>(url, { params });
  return res.data;
}

export async function post<T>(url: string, body?: unknown, params?: Record<string, unknown>): Promise<T> {
  const res = await client.post<T>(url, body, { params });
  return res.data;
}

export async function put<T>(url: string, body?: unknown): Promise<T> {
  const res = await client.put<T>(url, body);
  return res.data;
}

export async function del<T = void>(url: string): Promise<T> {
  const res = await client.delete<T>(url);
  return res.data;
}

export async function patch<T>(url: string, body?: unknown): Promise<T> {
  const res = await client.patch<T>(url, body);
  return res.data;
}