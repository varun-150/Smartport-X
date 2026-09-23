// Shared status presentation — tone maps, badges, and dot-cells keyed to
// the verified status enums in src/types.

import type { ReactNode } from 'react'
import type {
  AppointmentStatus,
  ContainerStatus,
  DischargeContainerStatus,
  EquipmentStatus,
  NotificationSeverity,
  ScheduleStatus,
  UserStatus,
  VesselStatus,
} from '../types'
import { StatusDot, type StatusTone } from '../ui/primitives'

export type BadgeTone = 'neutral' | 'ok' | 'warn' | 'danger' | 'info' | 'accent'

export function Badge({ tone = 'neutral', children }: { tone?: BadgeTone; children: ReactNode }) {
  return <span className={`badge badge-${tone}`}>{children}</span>
}

const CONTAINER_TONE: Record<ContainerStatus, StatusTone> = {
  REGISTERED: 'info',
  AT_GATE: 'warn',
  IN_YARD: 'info',
  LOADED: 'ok',
  DISCHARGED: 'ok',
  AT_GATE_OUT: 'warn',
  DEPARTED: 'info',
  HELD: 'danger',
}

const APPOINTMENT_TONE: Record<AppointmentStatus, StatusTone> = {
  PENDING: 'warn',
  CONFIRMED: 'info',
  CHECKED_IN: 'ok',
  IN_PROGRESS: 'ok',
  COMPLETED: 'ok',
  CANCELLED: 'info',
  NO_SHOW: 'danger',
}

const VESSEL_TONE: Record<VesselStatus, StatusTone> = {
  SCHEDULED: 'info',
  IN_TRANSIT: 'warn',
  ARRIVED: 'ok',
  WORKING: 'ok',
  DEPARTED: 'info',
  CANCELLED: 'danger',
}

const SCHEDULE_TONE: Record<ScheduleStatus, StatusTone> = {
  PLANNED: 'info',
  CALLED: 'warn',
  BERTHED: 'ok',
  DEPARTED: 'info',
  VOID: 'info',
}

const DISCHARGE_TONE: Record<DischargeContainerStatus, StatusTone> = {
  PLANNED: 'info',
  DISCHARGED: 'ok',
  IN_TRANSIT: 'warn',
  STACKED: 'info',
  MISROUTED: 'danger',
}

const EQUIPMENT_TONE: Record<EquipmentStatus, StatusTone> = {
  AVAILABLE: 'ok',
  ASSIGNED: 'warn',
  OUT_OF_SERVICE: 'danger',
}

const USER_TONE: Record<UserStatus, StatusTone> = {
  ACTIVE: 'ok',
  INACTIVE: 'info',
  LOCKED: 'danger',
}

const SEVERITY_TONE: Record<NotificationSeverity, StatusTone> = {
  INFO: 'info',
  WARNING: 'warn',
  ERROR: 'danger',
  CRITICAL: 'danger',
}

export function ContainerStatusCell({ status }: { status: ContainerStatus }) {
  return <StatusDot tone={CONTAINER_TONE[status]} label={status} />
}

export function AppointmentStatusCell({ status }: { status: AppointmentStatus }) {
  return <StatusDot tone={APPOINTMENT_TONE[status]} label={status} />
}

export function VesselStatusCell({ status }: { status: VesselStatus }) {
  return <StatusDot tone={VESSEL_TONE[status]} label={status} />
}

export function ScheduleStatusCell({ status }: { status: ScheduleStatus }) {
  return <StatusDot tone={SCHEDULE_TONE[status]} label={status} />
}

export function DischargeStatusCell({ status }: { status: DischargeContainerStatus }) {
  return <StatusDot tone={DISCHARGE_TONE[status]} label={status} />
}

export function EquipmentStatusCell({ status }: { status: EquipmentStatus }) {
  return <StatusDot tone={EQUIPMENT_TONE[status]} label={status} />
}

export function UserStatusCell({ status }: { status: UserStatus }) {
  return <StatusDot tone={USER_TONE[status]} label={status} />
}

export function SeverityCell({ severity }: { severity: NotificationSeverity }) {
  return <StatusDot tone={SEVERITY_TONE[severity]} label={severity} />
}