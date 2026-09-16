import { get, patch, post, put, del } from './http';
import type {
  ChangePasswordRequest,
  LoginRequest,
  LoginResponse,
  RefreshRequest,
  RefreshResponse,
  CreateUserRequest,
  UpdateUserRequest,
  UsersPage,
  UsersQuery,
  UserResponse,
} from '../types/auth';

export const authApi = {
  async login(req: LoginRequest): Promise<LoginResponse> {
    return post<LoginResponse>('/v1/auth/login', req);
  },
  async refresh(req: RefreshRequest): Promise<RefreshResponse> {
    return post<RefreshResponse>('/v1/auth/refresh', req);
  },
  async changePassword(req: ChangePasswordRequest): Promise<void> {
    return put<void>('/v1/auth/password', req);
  },
  async listUsers(query: UsersQuery = {}): Promise<UsersPage> {
    const params: Record<string, unknown> = {
      limit: query.limit,
      cursor: query.cursor,
      sort: query.sort,
    };
    if (query.role) params['filter[role]'] = query.role;
    if (query.status) params['filter[status]'] = query.status;
    if (query.search) params['filter[search]'] = query.search;
    return get<UsersPage>('/v1/users', params);
  },
  async getUser(id: string): Promise<UserResponse> {
    return get<UserResponse>(`/v1/users/${id}`);
  },
  async createUser(req: CreateUserRequest): Promise<UserResponse> {
    return post<UserResponse>('/v1/users', req);
  },
  async updateUser(id: string, req: UpdateUserRequest): Promise<UserResponse> {
    return put<UserResponse>(`/v1/users/${id}`, req);
  },
  async disableUser(id: string): Promise<void> {
    return patch<void>(`/v1/users/${id}/disable`);
  },
  async deleteUser(id: string): Promise<void> {
    return del<void>(`/v1/users/${id}`);
  },
};