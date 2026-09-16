export type RoleName = 'ADMIN' | 'TERMINAL_OP' | 'YARD_MANAGER' | 'GATE_OPERATOR' | 'READONLY';

export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'LOCKED';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
  user: UserResponse;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface RefreshResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface UserResponse {
  id: string;
  username: string;
  displayName: string;
  email: string;
  role: RoleName;
  status: UserStatus;
  permissions: string[];
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateUserRequest {
  username: string;
  email: string;
  displayName: string;
  password: string;
  role: RoleName;
  status?: UserStatus;
}

export interface UpdateUserRequest {
  email?: string;
  displayName?: string;
  role?: RoleName;
  status?: UserStatus;
}

export interface UsersQuery {
  limit?: number;
  cursor?: string;
  role?: RoleName;
  status?: UserStatus;
  search?: string;
  sort?: string;
}

export interface UsersPage {
  pagination: PaginationShape;
  data: UserResponse[];
}

export interface PaginationShape {
  nextCursor: string | null;
  hasMore: boolean;
  totalCount: number;
  limit: number;
}

export const ROLE_LABELS: Record<RoleName, string> = {
  ADMIN: 'Administrator',
  TERMINAL_OP: 'Terminal Operator',
  YARD_MANAGER: 'Yard Manager',
  GATE_OPERATOR: 'Gate Operator',
  READONLY: 'Read-only',
};

/** Role -> permission set as enforced by identity RolePermissions. */
export const ROLE_PERMISSIONS: Record<RoleName, string[]> = {
  ADMIN: [
    'users.read', 'users.write', 'users.disable',
    'vessels.read', 'vessels.write',
    'yards.read', 'yards.write',
    'gates.read', 'gates.write',
    'reports.read',
  ],
  TERMINAL_OP: [
    'vessels.read', 'vessels.write',
    'yards.read',
    'gates.read',
  ],
  YARD_MANAGER: [
    'yards.read', 'yards.write',
  ],
  GATE_OPERATOR: [
    'gates.read', 'gates.write',
  ],
  READONLY: [
    'vessels.read', 'yards.read', 'gates.read', 'reports.read',
  ],
};

export const PASSWORD_PATTERN = /^(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).+$/;