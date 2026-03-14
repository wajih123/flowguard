import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  ScrollText,
  Lock,
  ChevronLeft,
  ChevronRight,
  Search,
  Filter,
} from "lucide-react";
import { AdminLayout } from "@/components/layout/AdminLayout";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Badge } from "@/components/ui/Badge";
import { Loader } from "@/components/ui/Loader";
import { adminApi } from "@/api/admin";
import type { AuditEntry } from "@/api/admin";
import { useAuthStore } from "@/store/authStore";
import { format, parseISO } from "date-fns";
import { fr } from "date-fns/locale";

const SuperAdminGate: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const isSuperAdmin = useAuthStore((s) => s.user?.role === "ROLE_SUPER_ADMIN");
  if (!isSuperAdmin) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center animate-fade-in">
        <Lock size={40} className="text-text-muted" />
        <h2 className="text-xl font-bold text-white">Accès réservé</h2>
        <p className="text-text-secondary max-w-sm">
          Cette section est réservée au Super Admin. Contactez le fondateur pour
          obtenir un accès élevé.
        </p>
      </div>
    );
  }
  return <>{children}</>;
};

const roleBadgeVariant = (role?: string) => {
  if (role === "ROLE_SUPER_ADMIN") return "primary" as const;
  if (role === "ROLE_ADMIN") return "purple" as const;
  return "muted" as const;
};

const PAGE_SIZE = 25;

const AuditLogPage: React.FC = () => {
  const [page, setPage] = useState(0);
  const [actionFilter, setActionFilter] = useState("");
  const [actorFilter, setActorFilter] = useState("");

  const { data, isLoading } = useQuery({
    queryKey: ["admin", "audit", page, actionFilter, actorFilter],
    queryFn: () =>
      adminApi.getAuditLog({
        page,
        size: PAGE_SIZE,
        action: actionFilter || undefined,
        actorId: actorFilter || undefined,
      }),
    staleTime: 15_000,
  });

  const entries: AuditEntry[] = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  const renderTable = () => {
    if (isLoading)
      return (
        <div className="p-10">
          <Loader />
        </div>
      );
    if (entries.length === 0) {
      return (
        <div className="flex flex-col items-center justify-center py-14 gap-3 text-center">
          <ScrollText size={32} className="text-text-muted" />
          <p className="text-text-secondary text-sm">
            Aucune entrée d&apos;audit correspondante.
          </p>
        </div>
      );
    }
    return (
      <div className="overflow-x-auto">
        <table className="w-full text-sm min-w-[800px]">
          <thead>
            <tr className="border-b border-white/[0.06] text-text-muted text-xs">
              <th className="text-left px-4 py-3 font-medium">Acteur</th>
              <th className="text-left px-4 py-3 font-medium">Rôle</th>
              <th className="text-left px-4 py-3 font-medium">Action</th>
              <th className="text-left px-4 py-3 font-medium">Cible</th>
              <th className="text-left px-4 py-3 font-medium">IP</th>
              <th className="text-left px-4 py-3 font-medium">Date</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-white/[0.04]">
            {entries.map((entry) => {
              const roleLabel = (() => {
                if (entry.actorRole === "ROLE_SUPER_ADMIN")
                  return "Super Admin";
                if (entry.actorRole === "ROLE_ADMIN") return "Admin";
                return entry.actorRole;
              })();
              return (
                <tr key={entry.id} className="hover:bg-white/[0.02]">
                  <td className="px-4 py-3">
                    <p className="text-white text-xs font-mono">
                      {entry.actorEmail ?? "—"}
                    </p>
                  </td>
                  <td className="px-4 py-3">
                    {entry.actorRole ? (
                      <Badge variant={roleBadgeVariant(entry.actorRole)}>
                        {roleLabel}
                      </Badge>
                    ) : (
                      <span className="text-text-muted">—</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <code className="text-primary text-xs bg-primary/10 px-2 py-0.5 rounded">
                      {entry.action}
                    </code>
                  </td>
                  <td className="px-4 py-3 text-text-secondary text-xs">
                    {entry.targetType && entry.targetId ? (
                      <span>
                        <span className="text-text-muted">
                          {entry.targetType}
                        </span>{" "}
                        <span className="font-mono">
                          {entry.targetId.slice(0, 8)}…
                        </span>
                      </span>
                    ) : (
                      "—"
                    )}
                  </td>
                  <td className="px-4 py-3 text-text-muted font-mono text-xs">
                    {entry.ipAddress ?? "—"}
                  </td>
                  <td className="px-4 py-3 text-text-muted text-xs whitespace-nowrap">
                    {format(parseISO(entry.createdAt), "d MMM yyyy HH:mm:ss", {
                      locale: fr,
                    })}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    );
  };

  const handleFilterApply = () => {
    setPage(0);
  };

  return (
    <AdminLayout title="Journal d'audit">
      <SuperAdminGate>
        <div className="space-y-6 animate-fade-in">
          {/* Header */}
          <div>
            <h1 className="text-2xl font-bold text-white">
              Journal d&apos;audit
            </h1>
            <p className="text-text-secondary text-sm mt-1">
              Toutes les actions administratives enregistrées (
              {totalElements.toLocaleString()} entrées).
            </p>
          </div>

          {/* Filters */}
          <Card padding="sm">
            <div className="flex items-end gap-3 flex-wrap">
              <div className="flex-1 min-w-[180px]">
                <Input
                  label="Action"
                  placeholder="ex: DISABLE_USER, KYC_UPDATE…"
                  value={actionFilter}
                  onChange={(e) => setActionFilter(e.target.value)}
                  leftIcon={<Filter size={15} />}
                  onKeyDown={(e) => e.key === "Enter" && handleFilterApply()}
                />
              </div>
              <div className="flex-1 min-w-[180px]">
                <Input
                  label="ID acteur"
                  placeholder="UUID de l'admin…"
                  value={actorFilter}
                  onChange={(e) => setActorFilter(e.target.value)}
                  leftIcon={<Search size={15} />}
                  onKeyDown={(e) => e.key === "Enter" && handleFilterApply()}
                />
              </div>
              <Button
                variant="secondary"
                onClick={handleFilterApply}
                leftIcon={<Filter size={15} />}
              >
                Filtrer
              </Button>
              {(actionFilter || actorFilter) && (
                <Button
                  variant="ghost"
                  onClick={() => {
                    setActionFilter("");
                    setActorFilter("");
                    setPage(0);
                  }}
                >
                  Effacer
                </Button>
              )}
            </div>
          </Card>

          {/* Table */}
          <Card padding="none">{renderTable()}</Card>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between">
              <p className="text-text-muted text-sm">
                Page {page + 1} sur {totalPages} &middot;{" "}
                {totalElements.toLocaleString()} entrées
              </p>
              <div className="flex items-center gap-2">
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => setPage((p) => p - 1)}
                  leftIcon={<ChevronLeft size={16} />}
                >
                  Précédent
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                  rightIcon={<ChevronRight size={16} />}
                >
                  Suivant
                </Button>
              </div>
            </div>
          )}
        </div>
      </SuperAdminGate>
    </AdminLayout>
  );
};

export default AuditLogPage;
