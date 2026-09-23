// Form controls + buttons + table + pagination + modal.

import { useEffect, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react'
import { IconChevronLeft, IconChevron, IconX } from './icons'
import { EmptyState } from './primitives'

/* ---------- Button ---------- */
type ButtonVariant = 'default' | 'primary' | 'ghost' | 'danger'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: 'default' | 'sm'
}

export function Button({ variant = 'default', size = 'default', className, ...rest }: ButtonProps) {
  const classes = ['btn']
  if (variant !== 'default') classes.push(`btn-${variant}`)
  if (size === 'sm') classes.push('btn-sm')
  if (className) classes.push(className)
  return <button className={classes.join(' ')} {...rest} />
}

/* ---------- Field (label + control + error) ---------- */
interface FieldProps {
  label: string
  error?: string | null
  children: ReactNode
  hint?: string
}

export function Field({ label, error, hint, children }: FieldProps) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
      {hint && !error ? <span className="mono" style={{ fontSize: 11, color: 'var(--color-ink-3)' }}>{hint}</span> : null}
      {error ? <span className="field-error" role="alert">{error}</span> : null}
    </label>
  )
}

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean
}

export function Input({ invalid, className, ...rest }: InputProps) {
  return <input className={`input ${className ?? ''}`.trim()} aria-invalid={invalid || undefined} {...rest} />
}

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  invalid?: boolean
}

export function Select({ invalid, className, children, ...rest }: SelectProps) {
  return (
    <select className={`select ${className ?? ''}`.trim()} aria-invalid={invalid || undefined} {...rest}>
      {children}
    </select>
  )
}

/* ---------- Table ---------- */
export interface Column<T> {
  key: string
  header: string
  accessor: (row: T) => ReactNode
  align?: 'left' | 'right'
  className?: string
}

interface TableProps<T> {
  columns: Column<T>[]
  rows: T[]
  rowKey: (row: T) => string
  emptyMessage?: string
}

export function Table<T>({ columns, rows, rowKey, emptyMessage = 'No rows to display.' }: TableProps<T>) {
  if (rows.length === 0) {
    return (
      <div className="table-wrap">
        <EmptyState title={emptyMessage} />
      </div>
    )
  }
  return (
    <div className="table-wrap">
      <table className="data">
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key} className={c.align === 'right' ? 'num th' : ''}>
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={rowKey(row)}>
              {columns.map((c) => (
                <td key={c.key} className={c.align === 'right' ? 'num' : c.className ?? ''}>
                  {c.accessor(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/* ---------- Pagination ---------- */
interface PaginationProps {
  page: number
  pageSize: number
  totalCount: number
  totalPages: number
  onPage: (page: number) => void
}

export function Pagination({ page, pageSize, totalCount, totalPages, onPage }: PaginationProps) {
  const from = totalCount === 0 ? 0 : (page - 1) * pageSize + 1
  const to = Math.min(page * pageSize, totalCount)
  return (
    <div className="pagination">
      <span className="mono-gap">
        {from}–{to} of {totalCount}
      </span>
      <Button size="sm" disabled={page <= 1} aria-label="Previous page" onClick={() => onPage(page - 1)}>
        <IconChevronLeft size={14} />
      </Button>
      <span className="mono-gap">p.{page}/{Math.max(totalPages, 1)}</span>
      <Button size="sm" disabled={page >= totalPages || totalPages === 0} aria-label="Next page" onClick={() => onPage(page + 1)}>
        <IconChevron size={14} />
      </Button>
    </div>
  )
}

/* ---------- Modal ---------- */
interface ModalProps {
  open: boolean
  title: string
  onClose: () => void
  children: ReactNode
  footer?: ReactNode
}

export function Modal({ open, title, onClose, children, footer }: ModalProps) {
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open) return null
  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal-header">
          <h3>{title}</h3>
          <button type="button" className="btn btn-ghost btn-sm" onClick={onClose} aria-label="Close">
            <IconX size={14} />
          </button>
        </div>
        <div className="modal-body">{children}</div>
        {footer ? <div className="modal-footer">{footer}</div> : null}
      </div>
    </div>
  )
}