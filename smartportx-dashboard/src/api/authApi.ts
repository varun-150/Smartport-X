// Auth API client — paths verified against auth-service controllers.

import { del, get, patch, post, put } from '../lib/http'
import type {
  ChangePasswordRequest,
  CreateUserRequest,
  LoginRequest,
  LoginResponse,
  RefreshResponse,
  UpdateUserRequest,
  UserResponse,
  UsersPage,
  UsersQuery,
} from '../types'

export const authApi = {
  login: (req: LoginRequest) => post<LoginResponse>('/v1/auth/login', req),

  refresh: (refreshToken: string) =>
    post<RefreshResponse>('/v1/auth/refresh', { refreshToken }),

  changePassword: (req: ChangePasswordRequest) =>
    put<void>('/v1/auth/password', req),
}

export const usersApi = {
  list: (query: UsersQuery = {}) =>
    get<UsersPage>('/v1/users', {
      ...(query.role ? { 'filter[role]': query.role } : {}),
      ...(query.status ? { 'filter[status]': query.status } : {}),
      ...(query.search ? { 'filter[search]': query.search } : {}),
      ...(query.page != null ? { page: query.page } : {}),
      ...(query.size != null ? { size: query.size } : {}),
    }),

  get: (id: string) => get<UserResponse>(`/v1/users/${id}`),

  create: (req: CreateUserRequest) => post<UserResponse>('/v1/users', req),

  update: (id: string, req: UpdateUserRequest) => put<UserResponse>(`/v1/users/${id}`, req),

  disable: (id: string) => patch<UserResponse>(`/v1/users/${id}/disable`),

  remove: (id: string) => del<void>(`/v1/users/${id}`),
}