// Auth & user-management contracts (auth-service).

import type { PageResponse } from './api'

export type RoleName = 'ADMIN' | 'TERMINAL_OP' | 'YARD_MANAGER' | 'GATE_OPERATOR' | 'READONLY'

export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'LOCKED'

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: UserResponse
}

export interface RefreshRequest {
  refreshToken: string
}

export interface RefreshResponse {
  accessToken: string
  expiresIn: number
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export interface UserResponse {
  id: string
  username: string
  displayName: string
  email: string
  role: RoleName
  status: UserStatus
  permissions: string[]
  lastLoginAt: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateUserRequest {
  username: string
  displayName?: string
  email?: string
  password: string
  role: RoleName
}

export interface UpdateUserRequest {
  displayName?: string
  email?: string
  role?: RoleName
  status?: UserStatus
}
export interface UsersQuery {
  role?: RoleName
  status?: UserStatus
  search?: string
  page?: number
  size?: number
}

export type UsersPage = PageResponse<UserResponse>