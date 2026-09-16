import type { Pagination } from './api';

export type ContainerStatus =
  | 'REGISTERED' | 'AT_GATE' | 'IN_YARD' | 'LOADED'
  | 'DISCHARGED' | 'AT_GATE_OUT' | 'DEPARTED' | 'HELD';

export type ContainerType =
  | '20DC' | '40DC' | '40HC' | '45HC' | '20RF' | '40RF' | '20TK' | '40TK';

export type MovementType = 'GATE_IN' | 'GATE_OUT' | 'YARD_MOVE' | 'VESSEL_LOAD' | 'VESSEL_DISCHARGE';

export interface ContainerResponse {
  id: string;
  containerNumber: string;
  containerType: string;
  status: ContainerStatus;
  owner: string;
  consignee: string | null;
  shipper: string | null;
  weight: number | null;
  tareWeight: number | null;
  maxPayload: number | null;
  sealNumber: string | null;
  billOfLading: string | null;
  originPort: string | null;
  destinationPort: string | null;
  expectedArrival: string | null;
  currentLocation: string | null;
  dangerousGoods: boolean;
  unNumber: string | null;
  properShippingName: string | null;
  hazardClass: string | null;
  packingGroup: string | null;
  flashPoint: string | null;
  emergencyContact: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ContainersPage {
  pagination: Pagination;
  data: ContainerResponse[];
}

export interface ContainersQuery {
  limit?: number;
  cursor?: string;
  sort?: string;
  status?: ContainerStatus;
  type?: ContainerType;
  owner?: string;
  hasDangerousGoods?: boolean;
  location?: string;
  createdAfter?: string;
  createdBefore?: string;
}

export interface LocationFilter {
  block: string;
  fromDate?: string;
  toDate?: string;
}

export interface WeightRange {
  min?: number;
  max?: number;
}

export interface SearchContainersRequest {
  containerNumber?: string;
  status?: ContainerStatus[];
  type?: ContainerType[];
  owner?: string;
  location?: LocationFilter;
  weight?: WeightRange;
  dangerousGoods?: boolean;
  customsStatus?: string;
  sort?: string;
  limit?: number;
}

export interface DangerousGoodsDeclaration {
  unNumber: string;
  properShippingName: string;
  hazardClass: string;
  packingGroup?: 'I' | 'II' | 'III';
  flashPoint?: number;
  emergencyContact?: string;
}

export interface RegisterContainerRequest {
  containerNumber: string;
  containerType: ContainerType;
  owner: string;
  consignee?: string;
  shipper?: string;
  weight: number;
  tareWeight?: number;
  maxPayload?: number;
  sealNumber?: string;
  billOfLading?: string;
  originPort?: string;
  destinationPort?: string;
  expectedArrival?: string;
  dangerousGoods?: DangerousGoodsDeclaration;
}

export interface UpdateContainerRequest {
  owner?: string;
  consignee?: string;
  shipper?: string;
  weight?: number;
  tareWeight?: number;
  maxPayload?: number;
  sealNumber?: string;
  billOfLading?: string;
  originPort?: string;
  destinationPort?: string;
  expectedArrival?: string;
  currentLocation?: string;
  dangerousGoods?: DangerousGoodsDeclaration;
}

export interface RegisterMovementRequest {
  movementType: MovementType;
  from?: string;
  to?: string;
  timestamp: string;
  reason?: string;
}

export interface MovementResponse {
  movementId: string;
  containerId: string;
  containerNumber: string;
  movementType: string;
  from: string | null;
  to: string | null;
  timestamp: string;
  operator: string | null;
  reason: string | null;
  createdAt: string;
}

export interface MovementsPage {
  pagination: Pagination;
  data: MovementResponse[];
}

export interface MovementsQuery {
  limit?: number;
  cursor?: string;
  from?: string;
  to?: string;
  type?: MovementType;
}

export interface EventResponse {
  eventId: string;
  eventType: string;
  containerId: string;
  containerNumber: string;
  timestamp: string;
  data: Record<string, unknown> | null;
  source: string | null;
}

export interface EventsPage {
  pagination: Pagination;
  data: EventResponse[];
}

export interface EventsQuery {
  limit?: number;
  cursor?: string;
  eventType?: string;
}

export interface StatusChangeRequest {
  status: ContainerStatus;
  location?: string;
  reason?: string;
  operator?: string;
}

export interface StatusChangeResponse {
  containerId: string;
  containerNumber: string;
  previousStatus: ContainerStatus;
  newStatus: ContainerStatus;
  transitionTime: string;
  location: string | null;
}

export interface StatusHistoryResponse {
  id: string;
  containerId: string;
  containerNumber: string;
  previousStatus: ContainerStatus;
  newStatus: ContainerStatus;
  changedBy: string | null;
  reason: string | null;
  changedAt: string;
}