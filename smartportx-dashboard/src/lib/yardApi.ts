import { get, post, put, del } from './http';
import type {
  LayoutResponse,
  Block as LayoutBlock,
  BlocksResponse,
  BlockMetricsListResponse,
  EquipmentListResponse,
  EquipmentResponse,
  UpdateEquipmentRequest,
  MetricsResponse,
  AssignContainerRequest,
  AssignResponse,
  RemoveContainerResponse,
  OptimizationRequest,
  OptimizationResponse,
  OptimizationHistoriesResponse,
} from '../types/yard';

export const yardApi = {
  async layout(includeEmpty = true, blockFilter?: string[]): Promise<LayoutResponse> {
    const params: Record<string, unknown> = { includeEmpty };
    if (blockFilter?.length) params.blockFilter = blockFilter;
    return get<LayoutResponse>('/v1/yard/layout', params);
  },
  async blocks(): Promise<BlocksResponse> {
    return get<BlocksResponse>('/v1/yard/blocks');
  },
  async blockDetail(blockId: string): Promise<LayoutBlock> {
    return get<LayoutBlock>(`/v1/yard/blocks/${blockId}`);
  },
  async metrics(period = '24h'): Promise<MetricsResponse> {
    return get<MetricsResponse>('/v1/yard/metrics', { period });
  },
  async blockMetrics(): Promise<BlockMetricsListResponse> {
    return get<BlockMetricsListResponse>('/v1/yard/metrics/blocks');
  },
  async equipment(): Promise<EquipmentListResponse> {
    return get<EquipmentListResponse>('/v1/yard/equipment');
  },
  async equipmentItem(id: string): Promise<EquipmentResponse> {
    return get<EquipmentResponse>(`/v1/yard/equipment/${id}`);
  },
  async updateEquipment(id: string, req: UpdateEquipmentRequest): Promise<EquipmentResponse> {
    return put<EquipmentResponse>(`/v1/yard/equipment/${id}`, req);
  },
  async assignContainer(
    blockId: string,
    bayId: string,
    rowId: string,
    tierId: string,
    req: AssignContainerRequest,
  ): Promise<AssignResponse> {
    return put<AssignResponse>(
      `/v1/yard/blocks/${blockId}/bays/${bayId}/rows/${rowId}/tiers/${tierId}/container`,
      req,
    );
  },
  async removeContainer(
    blockId: string,
    bayId: string,
    rowId: string,
    tierId: string,
  ): Promise<RemoveContainerResponse> {
    return del<RemoveContainerResponse>(
      `/v1/yard/blocks/${blockId}/bays/${bayId}/rows/${rowId}/tiers/${tierId}/container`,
    );
  },
  async optimize(req: OptimizationRequest): Promise<OptimizationResponse> {
    return post<OptimizationResponse>('/v1/yard/optimize', req);
  },
  async optimizationHistory(cursor?: string, limit?: number): Promise<OptimizationHistoriesResponse> {
    return get<OptimizationHistoriesResponse>('/v1/yard/optimize/history', { cursor, limit });
  },
};