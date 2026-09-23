// Shared API contract types (RFC 9457, pagination envelopes).

/** Cursor-style pagination used by list endpoints that return `items`.
 *  Shown as page-sized slices; nextCursor advances (or terminates when hasMore=false).
 */
export interface Pagination {
  nextCursor: string | null
  hasMore: boolean
  totalCount: number
  limit: number
}

/** Generic cursor list envelope — used by vessels, containers, yard equipment etc. */
export interface ListResponse<T> {
  items: T[]
  nextCursor: string | null
  totalCount: number
}

/** Offset-style page envelope used by users and containers lists ({ pagination, data }). */
export interface PaginationShape {
  totalCount: number
  totalPages: number
  currentPage: number
  pageSize: number
  hasPrevious: boolean
  hasNext: boolean
}

export interface PageResponse<T> {
  pagination: PaginationShape
  data: T[]
}

/** RFC 9457 problem detail body returned by the gateway/API on failures. */
export interface Problem {
  type: string
  title: string
  status: number
  detail?: string
  instance?: string
  /** field errors present on 400 validation_failed */
  fieldErrors?: Record<string, string>
}

/** Browser-friendlier exception carrying the parsed problem envelope. */
export class ApiError extends Error {
  readonly status: number
  readonly title: string
  readonly detail?: string
  readonly fieldErrors?: Record<string, string>
  readonly isNetwork: boolean

  constructor(status: number, title: string, detail?: string, fieldErrors?: Record<string, string>, isNetwork = false) {
    super(detail ?? title)
    this.name = 'ApiError'
    this.status = status
    this.title = title
    this.detail = detail
    this.fieldErrors = fieldErrors
    this.isNetwork = isNetwork
  }
}