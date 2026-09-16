import { get, post, put, del } from './http';
import {
  type ContainerResponse,
  type ContainersPage,
  type ContainersQuery,
  type EventsPage,
  type EventsQuery,
  type MovementResponse,
  type MovementsPage,
  type MovementsQuery,
  type RegisterContainerRequest,
  type RegisterMovementRequest,
  type SearchContainersRequest,
  type StatusChangeRequest,
  type StatusChangeResponse,
  type StatusHistoryResponse,
  type UpdateContainerRequest,
} from '../types/container';

function toListParams(query: ContainersQuery): Record<string, unknown> {
  return {
    limit: query.limit,
    cursor: query.cursor,
    sort: query.sort,
    'filter[status]': query.status,
    'filter[type]': query.type,
    'filter[owner]': query.owner,
    'filter[hasDangerousGoods]': query.hasDangerousGoods,
    'filter[location]': query.location,
    'filter[createdAfter]': query.createdAfter,
    'filter[createdBefore]': query.createdBefore,
  };
}

function toMovementsParams(query: MovementsQuery): Record<string, unknown> {
  return {
    limit: query.limit,
    cursor: query.cursor,
    'filter[from]': query.from,
    'filter[to]': query.to,
    'filter[type]': query.type,
  };
}

function toEventsParams(query: EventsQuery): Record<string, unknown> {
  return {
    limit: query.limit,
    cursor: query.cursor,
    'filter[eventType]': query.eventType,
  };
}

export function list(query: ContainersQuery = {}): Promise<ContainersPage> {
  return get<ContainersPage>('/v1/containers', toListParams(query));
}

export function search(request: SearchContainersRequest): Promise<ContainersPage> {
  return post<ContainersPage>('/v1/containers/search', request);
}

export function getByNumber(containerNumber: string): Promise<ContainerResponse> {
  return get<ContainerResponse>(`/v1/containers/number/${encodeURIComponent(containerNumber)}`);
}

export function getById(id: string): Promise<ContainerResponse> {
  return get<ContainerResponse>(`/v1/containers/${encodeURIComponent(id)}`);
}

export function register(request: RegisterContainerRequest): Promise<ContainerResponse> {
  return post<ContainerResponse>('/v1/containers', request);
}

export function update(id: string, request: UpdateContainerRequest): Promise<ContainerResponse> {
  return put<ContainerResponse>(`/v1/containers/${encodeURIComponent(id)}`, request);
}

export function remove(id: string): Promise<void> {
  return del<void>(`/v1/containers/${encodeURIComponent(id)}`);
}

export function recordMovement(id: string, request: RegisterMovementRequest): Promise<MovementResponse> {
  return post<MovementResponse>(`/v1/containers/${encodeURIComponent(id)}/movements`, request);
}

export function movements(id: string, query: MovementsQuery = {}): Promise<MovementsPage> {
  return get<MovementsPage>(`/v1/containers/${encodeURIComponent(id)}/movements`, toMovementsParams(query));
}

export function events(id: string, query: EventsQuery = {}): Promise<EventsPage> {
  return get<EventsPage>(`/v1/containers/${encodeURIComponent(id)}/events`, toEventsParams(query));
}

export function statusHistory(id: string): Promise<StatusHistoryResponse[]> {
  return get<StatusHistoryResponse[]>(`/v1/containers/${encodeURIComponent(id)}/status-history`);
}

export function changeStatus(id: string, request: StatusChangeRequest): Promise<StatusChangeResponse> {
  return put<StatusChangeResponse>(`/v1/containers/${encodeURIComponent(id)}/status`, request);
}