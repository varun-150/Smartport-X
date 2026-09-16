import type { CSSProperties, ReactNode } from 'react';
import { Skeleton, Button } from './ui';
import { EmptyState } from './feedback';

export interface Column<T> {
  key: string;
  header: ReactNode;
  render?: (row: T) => ReactNode;
  numeric?: boolean;
  className?: string;
  width?: number | string;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  rows?: T[] | null;
  getRowId: (row: T) => string;
  onRowClick?: (row: T) => void;
  loading?: boolean;
  emptyMessage?: string;
}

export function DataTable<T>({
  columns,
  rows,
  getRowId,
  onRowClick,
  loading = false,
  emptyMessage = 'No data',
}: DataTableProps<T>) {
  const isEmpty = !loading && (!rows || rows.length === 0);
  const styleFor = (col: Column<T>): CSSProperties | undefined =>
    col.width ? { width: col.width } : undefined;

  return (
    <div className="table-shell">
      <table className="data-table">
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key} className={c.numeric ? 'text-right' : ''} style={styleFor(c)}>
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {loading
            ? Array.from({ length: 5 }).map((_, i) => (
                <tr key={`skeleton-${i}`}>
                  {columns.map((c) => (
                    <td key={c.key}>
                      <Skeleton width={c.numeric ? 40 : 90} />
                    </td>
                  ))}
                </tr>
              ))
            : null}
          {!loading && rows
            ? rows.map((row) => (
                <tr
                  key={getRowId(row)}
                  className={onRowClick ? 'clickable' : undefined}
                  onClick={onRowClick ? () => onRowClick(row) : undefined}
                >
                  {columns.map((c) => (
                    <td key={c.key} className={c.numeric ? 'text-right' : c.className} style={styleFor(c)}>
                      {c.render ? c.render(row) : (row as Record<string, ReactNode>)[c.key]}
                    </td>
                  ))}
                </tr>
              ))
            : null}
        </tbody>
      </table>
      {isEmpty ? <EmptyState title={emptyMessage} /> : null}
    </div>
  );
}

interface PaginationProps {
  hasMore: boolean;
  loading: boolean;
  label?: string;
  onNext: () => void;
  onReset: () => void;
}

export function Pagination({ hasMore, loading, label, onNext, onReset }: PaginationProps) {
  return (
    <div className="pager">
      {label ? <span className="pager-meta">{label}</span> : null}
      <span className="grow" />
      <Button size="sm" variant="ghost" onClick={onReset} disabled={loading}>
        Reset
      </Button>
      <Button size="sm" onClick={onNext} disabled={!hasMore || loading} loading={loading} icon="chevron-down">
        Next
      </Button>
    </div>
  );
}