import React, { useState } from "react";
import { Search, Filter, CheckCircle, XCircle, Eye } from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Link, useSearchParams } from "react-router-dom";
import { AdminLayout } from "@/components/layout/AdminLayout";
import { Card } from "@/components/ui/Card";
import { Input, Select } from "@/components/ui/Input";
import { Badge, kycStatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Loader } from "@/components/ui/Loader";
import { EmptyState } from "@/components/ui/EmptyState";
import { adminApi } from "@/api/admin";
import { format, parseISO } from "date-fns";
import { fr } from "date-fns/locale";

const USER_TYPE_LABELS: Record<string, string> = {
  INDIVIDUAL: "Particulier",
  FREELANCE: "Freelance",
  TPE: "TPE",
  PME: "PME",
  SME: "Grande ent.",
  B2C_SALARIE: "Salarié",
  B2C_FAMILLE: "Famille",
  B2C_RETRAITE: "Retraité",
  B2C_ETUDIANT: "Étudiant",
  B2C_CADRE: "Cadre",
};

const AdminUsersPage: React.FC = () => {
  const [search, setSearch] = useState("");
  const [kycFilter, setKycFilter] = useState("");
  const qc = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ["admin", "users", search, kycFilter],
    queryFn: () =>
      adminApi.listUsers({
        search: search || undefined,
        kycStatus: kycFilter || undefined,
      }),
    staleTime: 60_000,
  });

  const updateKyc = useMutation({
    mutationFn: ({ userId, status }: { userId: string; status: string }) =>
      adminApi.updateKycStatus(userId, status),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin", "users"] }),
  });

  const users = data?.content ?? [];

  return (
    <AdminLayout title="Utilisateurs">
      <div className="max-w-7xl mx-auto space-y-6 animate-fade-in">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-white">Utilisateurs</h1>
            <p className="text-text-secondary text-sm mt-1">
              {data?.totalElements ?? 0} utilisateurs enregistrés
            </p>
          </div>
        </div>

        {/* Filters */}
        <div className="flex gap-3">
          <div className="flex-1">
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Rechercher par nom, email…"
              leftIcon={<Search size={16} />}
            />
          </div>
          <div className="w-48">
            <Select
              value={kycFilter}
              onChange={(e) => setKycFilter(e.target.value)}
              options={[
                { value: "", label: "Statut KYC" },
                { value: "PENDING", label: "En attente" },
                { value: "IN_PROGRESS", label: "En cours" },
                { value: "APPROVED", label: "Approuvé" },
                { value: "REJECTED", label: "Refusé" },
              ]}
            />
          </div>
        </div>

        <Card padding="none">
          {isLoading ? (
            <Loader />
          ) : users.length === 0 ? (
            <EmptyState
              title="Aucun utilisateur trouvé"
              description="Modifiez vos filtres de recherche"
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Nom</th>
                    <th>Email</th>
                    <th>Type</th>
                    <th>KYC</th>
                    <th>Inscrit le</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((u) => (
                    <tr key={u.id}>
                      <td className="font-medium">
                        {u.firstName} {u.lastName}
                        {u.companyName && (
                          <p className="text-text-muted text-xs">
                            {u.companyName}
                          </p>
                        )}
                      </td>
                      <td className="text-text-secondary">{u.emailMasked}</td>
                      <td>
                        <Badge variant="muted">
                          {USER_TYPE_LABELS[u.userType] ?? u.userType}
                        </Badge>
                      </td>
                      <td>
                        <Badge variant={kycStatusBadge(u.kycStatus)}>
                          {u.kycStatus}
                        </Badge>
                      </td>
                      <td className="text-text-secondary text-xs">
                        {format(parseISO(u.createdAt), "d MMM yyyy", {
                          locale: fr,
                        })}
                      </td>
                      <td>
                        <div className="flex items-center gap-2">
                          <Link to={`/admin/users/${u.id}`}>
                            <Button
                              variant="ghost"
                              size="sm"
                              leftIcon={<Eye size={14} />}
                            >
                              Voir
                            </Button>
                          </Link>
                          {u.kycStatus === "PENDING" ||
                          u.kycStatus === "IN_PROGRESS" ? (
                            <>
                              <Button
                                variant="ghost"
                                size="sm"
                                leftIcon={
                                  <CheckCircle
                                    size={14}
                                    className="text-success"
                                  />
                                }
                                isLoading={updateKyc.isPending}
                                onClick={() =>
                                  updateKyc.mutate({
                                    userId: u.id,
                                    status: "APPROVED",
                                  })
                                }
                              >
                                Approuver
                              </Button>
                              <Button
                                variant="ghost"
                                size="sm"
                                leftIcon={
                                  <XCircle size={14} className="text-danger" />
                                }
                                isLoading={updateKyc.isPending}
                                onClick={() =>
                                  updateKyc.mutate({
                                    userId: u.id,
                                    status: "REJECTED",
                                  })
                                }
                              >
                                Refuser
                              </Button>
                            </>
                          ) : null}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </div>
    </AdminLayout>
  );
};

export default AdminUsersPage;
