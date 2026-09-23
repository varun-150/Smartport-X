// Container API client — paths verified against container-service controllers.

import { get, post } from '../lib/http'
import type {
  ContainerMovement,
  ContainerResponse,
  ContainersPage,
  ContainersQuery,
} from '../types'

export const containerApi = {
  list: (query: ContainersQuery = {}) =>
    get<ContainersPage>('/v1/containers', {
      ...(query.status ? { 'filter[status]': query.status } : {}),
      ...(query.type ? { 'filter[type]': query.type } : {}),
      ...(query.owner ? { 'filter[owner]': query.owner } : {}),
      ...(query.hasDangerousGoods != null ? { 'filter[hasDangerousGoods]': query.hasDangerousGoods } : {}),
      ...(query.location ? { 'filter[location]': query.location } : {}),
      ...(query.createdAfter ? { 'filter[createdAfter]': query.createdAfter } : {}),
      ...(query.createdBefore ? { 'filter[createdBefore]': query.createdBefore } : {}),
      ...(query.page != null ? { page: query.page } : {}),
      ...(query.size != null ? { size: query.size } : {}),
    }),

  search: (query: ContainersQuery) =>
    post<ContainersPage>('/v1/containers/search', query),

  get: (id: string) => get<ContainerResponse>(`/v1/containers/${id}`),

  movements: (id: string) =>
    get<ContainerMovement[]>(`/v1/containers/${id}/movements`),
}