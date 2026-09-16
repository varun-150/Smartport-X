import type { Pagination } from './api';

export type BlockType = 'EXPORT' | 'IMPORT' | 'TRANSIT' | 'REFRIGERATED' | 'DANGEROUS' | 'EMPTY';

export type EquipmentType = 'RTG' | 'ASC';

export type EquipmentStatus = 'AVAILABLE' | 'ASSIGNED' | 'OUT_OF_SERVICE';

export type OptimizationStrategy = 'FIFO' | 'WEIGHT_CLUSTER' | 'DEPARTURE_CLUSTER' | 'BALANCED';

export type OptimizationStatus = 'COMPLETED' | 'FAILED' | 'IN_PROGRESS';

export interface LayoutResponse {
  yardId: string;
  yardName: string;
  totalBlocks: number;
  totalSlots: number;
  occupiedSlots: number;
  utilizationRate: number;
  lastUpdated: string;
  blocks: Block[];
}

export interface Block {
  blockId: string;
  blockCode: string;
  name: string;
  type: BlockType | null;
  totalBays: number;
  totalRows: number;
  totalTiers: number;
  totalSlots: number;
  occupiedSlots: number;
  utilizationRate: number;
  bays: Bay[];
}

export interface Bay {
  bayId: string;
  bayNumber: number;
  rows: Row[];
}

export interface Row {
  rowId: string;
  rowNumber: number;
  tiers: Tier[];
}

export interface Tier {
  tierId: string;
  tierNumber: number;
  position: string;
  status: 'OCCUPIED' | 'EMPTY';
  containerId: string | null;
  containerNumber: string | null;
  containerType: string | null;
  stackWeight: number | null;
  maxWeight: number | null;
  dangerousGoods: boolean | null;
  estimatedDeparture: string | null;
}

export interface BlockSummaryResponse {
  blockId: string;
  blockCode: string;
  name: string;
  type: string;
  totalSlots: number;
  occupiedSlots: number;
  utilizationRate: number;
  averageDwellDays: number | null;
  dangerousGoodsAllowed: boolean;
  refrigeratedSlots: number;
}

export interface BlocksResponse {
  data: BlockSummaryResponse[];
  pagination: Pagination | null;
}

export interface BaySummaryResponse {
  bayId: string;
  bayNumber: number;
  totalRows: number;
  totalTiers: number;
  totalSlots: number;
  occupiedSlots: number;
  utilizationRate: number;
  maxStackWeight: number | null;
}

export interface BayListResponse {
  data: BaySummaryResponse[];
}

export interface TierDetailResponse {
  tierId: string;
  position: string;
  status: string;
  container: ContainerDetail | null;
  stackWeight: number | null;
  maxWeight: number | null;
  stackCount: number;
  lastModified: string;
}

export interface ContainerDetail {
  id: string;
  number: string;
  type: string;
  owner: string | null;
  weight: number | null;
  status: string;
  dangerousGoods: Record<string, unknown> | null;
  receivedAt: string | null;
  estimatedDeparture: string | null;
}

export interface AssignContainerRequest {
  containerId: string;
  position: string;
}

export interface AssignResponse {
  tierId: string;
  position: string;
  status: string;
  containerId: string;
  containerNumber: string;
  containerType: string;
}

export interface RemoveContainerResponse {
  removedContainerId: string;
  position: string;
  removedAt: string;
  movedTo: string | null;
}

export interface EquipmentResponse {
  equipmentId: string;
  code: string;
  equipmentType: EquipmentType;
  assignedBlockId: string | null;
  status: EquipmentStatus;
  movesTotal: number;
  createdAt: string;
  updatedAt: string;
}

export interface EquipmentListResponse {
  data: EquipmentResponse[];
}

export interface UpdateEquipmentRequest {
  status?: EquipmentStatus;
  assignedBlockId?: string;
}

export interface MovesResponse {
  equipmentId: string;
  code: string;
  movesTotal: number;
  lastUpdated: string;
}

export interface MetricsResponse {
  yardId: string;
  period: string;
  generatedAt: string;
  totalSlots: number;
  occupiedSlots: number;
  availableSlots: number;
  utilizationRate: number;
  utilizationTrend: UtilizationTrendPoint[];
  averageDwellTimeDays: number | null;
  dwellTimeDistribution: DwellTimeDistribution[];
  reshufflingRate: number | null;
  totalMoves24h: number | null;
  averageMovesPerHour: number | null;
  containersReceived24h: number | null;
  containersDispatched24h: number | null;
  dangerousGoodsCount: number;
  refrigeratedCount: number;
  emptyContainerCount: number;
}

export interface UtilizationTrendPoint {
  timestamp: string;
  rate: number;
}

export interface DwellTimeDistribution {
  range: string;
  count: number;
  percentage: number;
}

export interface BlockMetricsListResponse {
  data: BlockMetricsData[];
}

export interface BlockMetricsData {
  blockId: string;
  blockCode: string;
  blockType: string;
  totalSlots: number;
  occupiedSlots: number;
  utilizationRate: number;
  averageDwellDays: number | null;
  reshufflingRate: number;
  dangerousGoodsSlots: number;
  refrigeratedSlots: number;
  emptySlots: number;
}

export interface OptimizationRequest {
  blockId: string;
  strategy: OptimizationStrategy;
  dryRun?: boolean;
  constraints?: OptimizationConstraints;
}

export interface OptimizationConstraints {
  maxResufflesPerMove?: number;
  preserveReeferPositions?: boolean;
  avoidDangerousMixing?: boolean;
}

export interface OptimizationResponse {
  optimizationId: string;
  blockId: string;
  strategy: OptimizationStrategy;
  dryRun: boolean;
  analysis: OptimizationAnalysis;
  recommendations: OptimizationRecommendation[];
  status: OptimizationStatus;
  executedAt: string | null;
  completedAt: string | null;
  movesCompleted: number | null;
  movesFailed: number | null;
  actualReshufflingReduction: number | null;
}

export interface OptimizationAnalysis {
  totalContainers: number;
  currentReshuffles: number;
  projectedReshuffles: number;
  estimatedReshufflingReduction: number;
  containersAffected: number;
  estimatedExecutionTimeMs: number;
}

export interface OptimizationRecommendation {
  sequence: number;
  containerId: string;
  containerNumber: string;
  currentPosition: string;
  recommendedPosition: string;
  reason: string;
  moveType: string;
  priority: string;
}

export interface OptimizationHistoryEntry {
  optimizationId: string;
  blockId: string;
  blockCode: string;
  strategy: OptimizationStrategy;
  status: OptimizationStatus;
  dryRun: boolean;
  containersAffected: number;
  reshufflingReduction: number | null;
  triggeredBy: string | null;
  executedAt: string;
  completedAt: string | null;
}

export interface OptimizationHistoriesResponse {
  data: OptimizationHistoryEntry[];
  pagination: Pagination;
}