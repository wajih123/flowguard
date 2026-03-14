import React, { useState } from "react";
import { ChevronUp, ChevronDown } from "lucide-react";

export interface Column<T> {
  key: keyof T | string;
  header: string;
  sortable?: boolean;
  render?: (value: unknown, row: T) => React.ReactNode;
  className?: string;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  data: T[];
  isLoading?: boolean;
  skeletonRows?: number;
  emptyMessage?: string;
  rowKey: (row: T) => string;
  pageSize?: number;
  onRowClick?: (row: T) => void;
}

type SortDir = "asc" | "desc" | null;

function safeStringify(val: unknown): string {
  if (val === null || val === undefined) return "";
  if (typeof val === "string") return val;
  if (typeof val === "number" || typeof val === "boolean") return String(val);
  return JSON.stringify(val);
}

function renderRows<T>(
  isLoading: boolean,
  pageData: T[],
  columns: Column<T>[],
  skeletonRows: number,
  emptyMessage: string,
  rowKey: (row: T) => string,
  onRowClick: ((row: T) => void) | undefined,
): React.ReactNode {
  if (isLoading) {
    return Array.from({ length: skeletonRows }, (_, i) => i).map((i) => (
      <tr key={`skel-${i}`} className="animate-pulse">
        {columns.map((col) => (
          <td key={String(col.key)} className="px-4 py-3">
            <div className="h-4 bg-white/10 rounded w-full" />
          </td>
        ))}
      </tr>
    ));
  }
  if (pageData.length === 0) {
    return (
      <tr>
        <td
          colSpan={columns.length}
          className="px-4 py-12 text-center text-text-secondary text-sm"
        >
          {emptyMessage}
        </td>
      </tr>
    );
  }
  return pageData.map((row) => (
    <tr
      key={rowKey(row)}
      className={`hover:bg-white/[0.02] transition-colors ${onRowClick ? "cursor-pointer" : ""}`}
      onClick={() => onRowClick?.(row)}
    >
      {columns.map((col) => {
        const val = (row as Record<string, unknown>)[String(col.key)];
        return (
          <td
            key={String(col.key)}
            className={`px-4 py-3 text-sm text-white ${col.className ?? ""}`}
          >
            {col.render ? col.render(val, row) : safeStringify(val)}
          </td>
        );
      })}
    </tr>
  ));
}

function DataTableInner<T>(props: Readonly<DataTableProps<T>>) {
  const {
    columns,
    data,
    isLoading = false,
    skeletonRows = 5,
    emptyMessage = "Aucune donnée",
    rowKey,
    pageSize = 10,
    onRowClick,
  } = props;

  const [sortKey, setSortKey] = useState<string | null>(null);
  const [sortDir, setSortDir] = useState<SortDir>(null);
  const [page, setPage] = useState(0);

  const handleSort = (key: string) => {
    if (sortKey !== key) {
      setSortKey(key);
      setSortDir("asc");
    } else if (sortDir === "asc") {
      setSortDir("desc");
    } else if (sortDir === "desc") {
      setSortKey(null);
      setSortDir(null);
    }
  };

  const sorted = [...data].sort((a, b) => {
    if (!sortKey || !sortDir) return 0;
    const av = (a as Record<string, unknown>)[sortKey];
    const bv = (b as Record<string, unknown>)[sortKey];
    if (av === bv) return 0;
    const cmp = av! < bv! ? -1 : 1;
    return sortDir === "asc" ? cmp : -cmp;
  });

  const totalPages = Math.ceil(sorted.length / pageSize);
  const pageData = sorted.slice(page * pageSize, (page + 1) * pageSize);

  return (
    <div className="glass-card overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b border-white/[0.06]">
              {columns.map((col) => (
                <th
                  key={String(col.key)}
                  className={`px-4 py-3 text-left text-xs font-medium text-text-secondary uppercase tracking-wider ${col.sortable ? "cursor-pointer select-none hover:text-white transition-colors" : ""} ${col.className ?? ""}`}
                  onClick={
                    col.sortable ? () => handleSort(String(col.key)) : undefined
                  }
                >
                  <div className="flex items-center gap-1">
                    {col.header}
                    {col.sortable && (
                      <span className="inline-flex flex-col">
                        <ChevronUp
                          size={10}
                          className={
                            sortKey === String(col.key) && sortDir === "asc"
                              ? "text-primary"
                              : "text-text-muted"
                          }
                        />
                        <ChevronDown
                          size={10}
                          className={
                            sortKey === String(col.key) && sortDir === "desc"
                              ? "text-primary"
                              : "text-text-muted"
                          }
                        />
                      </span>
                    )}
                  </div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-white/[0.04]">
            {renderRows(
              isLoading,
              pageData,
              columns,
              skeletonRows,
              emptyMessage,
              rowKey,
              onRowClick,
            )}
          </tbody>
        </table>
      </div>

      {!isLoading && totalPages > 1 && (
        <div className="flex items-center justify-between px-4 py-3 border-t border-white/[0.06]">
          <p className="text-xs text-text-secondary">
            {page * pageSize + 1}–
            {Math.min((page + 1) * pageSize, sorted.length)} sur {sorted.length}
          </p>
          <div className="flex gap-2">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-3 py-1 text-xs rounded-lg glass-card text-text-secondary hover:text-white disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              Précédent
            </button>
            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page === totalPages - 1}
              className="px-3 py-1 text-xs rounded-lg glass-card text-text-secondary hover:text-white disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              Suivant
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export const DataTable = DataTableInner;
