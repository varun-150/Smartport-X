export interface Pagination {
  nextCursor: string | null;
  hasMore: boolean;
  totalCount: number;
  limit: number;
}

export interface ListResponse<T> {
  items: T[];
  nextCursor: string | null;
}

export interface ApiErrorBody {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
  fieldErrors?: Record<string, string>;
  code?: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly body: ApiErrorBody;

  constructor(status: number, body: ApiErrorBody, fallback?: string) {
    super(body?.message || body?.error || fallback || `Request failed (${status})`);
    this.name = 'ApiError';
    this.status = status;
    this.body = body ?? {};
  }
}

export const isApiError = (err: unknown): err is ApiError => err instanceof ApiError;