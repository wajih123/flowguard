import React, { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Shield, Lock, Search, UserX, UserCheck } from "lucide-react";
import { AdminLayout } from "@/components/layout/AdminLayout";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Badge } from "@/components/ui/Badge";
import { Loader } from "@/components/ui/Loader";
import { ConfirmModal } from "@/components/ui/ConfirmModal";
import { adminApi } from "@/api/admin";
import type { AdminUser, AdminUserDetail } from "@/api/admin";
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

const roleBadge = (role: string) => {
  if (role === "ROLE_SUPER_ADMIN")
    return <Badge variant="primary">Super Admin</Badge>;
  if (role === "ROLE_ADMIN") return <Badge variant="purple">Admin</Badge>;
  return <Badge variant="muted">Utilisateur</Badge>;
};

type PromoteTarget = {
  user: AdminUser;
  role: "ROLE_ADMIN" | "ROLE_SUPER_ADMIN";
} | null;

const AdminManagePage: React.FC = () => {
  const qc = useQueryClient();
  const currentUserId = useAuthStore((s) => s.user?.id);

  // Current admins list
  const { data: admins, isLoading: loadingAdmins } = useQuery({
    queryKey: ["admin", "admins"],
    queryFn: adminApi.listAdmins,
  });

  // User search for promotion
  const [search, setSearch] = useState("");
  const { data: searchResults, isFetching: searching } = useQuery({
    queryKey: ["admin", "users", "search", search],
    queryFn: () =>
      adminApi.listUsers({ search, size: 8 }).then((p) => p.content),
    enabled: search.trim().length >= 2,
    staleTime: 10_000,
  });

  // Modals
  const [promoteTarget, setPromoteTarget] = useState<PromoteTarget>(null);
  const [revokeTarget, setRevokeTarget] = useState<AdminUserDetail | null>(
    null,
  );

  const promote = useMutation({
    mutationFn: () => {
      if (!promoteTarget) return Promise.reject(new Error("No target"));
      return adminApi.promoteUser(promoteTarget.user.id, promoteTarget.role);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["admin", "admins"] });
      qc.invalidateQueries({ queryKey: ["admin", "users"] });
      setPromoteTarget(null);
    },
  });

  const revoke = useMutation({
    mutationFn: () => adminApi.revokeAdmin(revokeTarget!.id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["admin", "admins"] });
      setRevokeTarget(null);
    },
  });

  const nonAdminResults = (searchResults ?? []).filter(
    (u) => u.role !== "ROLE_ADMIN" && u.role !== "ROLE_SUPER_ADMIN",
  );

  const renderSearchResults = () => {
    if (searching)
      return (
        <div className="p-6">
          <Loader />
        </div>
      );
    if (nonAdminResults.length === 0) {
      return (
        <p className="px-5 py-4 text-text-muted text-sm">
          Aucun utilisateur non-admin trouvé.
        </p>
      );
    }
    return (
      <div className="divide-y divide-white/[0.04]">
        {nonAdminResults.map((user) => (
          <div
            key={user.id}
            className="flex items-center justify-between px-5 py-3"
          >
            <div>
              <p className="text-white text-sm font-medium">
                {user.firstName} {user.lastName}
              </p>
              <p className="text-text-muted text-xs font-mono">
                {user.emailMasked}
              </p>
            </div>
            <div className="flex items-center gap-2">
              <Button
                variant="secondary"
                size="sm"
                leftIcon={<UserCheck size={14} />}
                onClick={() => setPromoteTarget({ user, role: "ROLE_ADMIN" })}
              >
                Admin
              </Button>
              <Button
                variant="outline"
                size="sm"
                leftIcon={<Shield size={14} />}
                onClick={() =>
                  setPromoteTarget({ user, role: "ROLE_SUPER_ADMIN" })
                }
              >
                Super Admin
              </Button>
            </div>
          </div>
        ))}
      </div>
    );
  };

  return (
    <AdminLayout title="Gestion des admins">
      <SuperAdminGate>
        <div className="space-y-8 animate-fade-in">
          {/* Header */}
          <div>
            <h1 className="text-2xl font-bold text-white">
              Gestion des admins
            </h1>
            <p className="text-text-secondary text-sm mt-1">
              Gérez les rôles administrateurs de la plateforme.
            </p>
          </div>

          {/* Current admins */}
          <section className="space-y-3">
            <h2 className="text-base font-semibold text-white">
              Administrateurs actifs
            </h2>
            <Card padding="none">
              {loadingAdmins ? (
                <div className="p-8">
                  <Loader />
                </div>
              ) : (
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-white/[0.06] text-text-muted text-xs">
                      <th className="text-left px-5 py-3 font-medium">Nom</th>
                      <th className="text-left px-5 py-3 font-medium">Email</th>
                      <th className="text-left px-5 py-3 font-medium">Rôle</th>
                      <th className="text-left px-5 py-3 font-medium">
                        Depuis
                      </th>
                      <th className="px-5 py-3" />
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-white/[0.04]">
                    {(admins ?? []).map((admin) => {
                      const isMe = admin.id === currentUserId;
                      return (
                        <tr key={admin.id}>
                          <td className="px-5 py-3 font-medium text-white">
                            {admin.firstName} {admin.lastName}
                          </td>
                          <td className="px-5 py-3 text-text-secondary font-mono text-xs">
                            {admin.email}
                          </td>
                          <td className="px-5 py-3">{roleBadge(admin.role)}</td>
                          <td className="px-5 py-3 text-text-muted text-xs">
                            {format(parseISO(admin.createdAt), "d MMM yyyy", {
                              locale: fr,
                            })}
                          </td>
                          <td className="px-5 py-3 text-right">
                            {!isMe && admin.role !== "ROLE_SUPER_ADMIN" && (
                              <Button
                                variant="danger"
                                size="sm"
                                leftIcon={<UserX size={14} />}
                                onClick={() => setRevokeTarget(admin)}
                              >
                                Révoquer
                              </Button>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                    {(admins ?? []).length === 0 && (
                      <tr>
                        <td
                          colSpan={5}
                          className="px-5 py-8 text-center text-text-muted"
                        >
                          Aucun administrateur trouvé.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              )}
            </Card>
          </section>

          {/* Promote a user */}
          <section className="space-y-3">
            <h2 className="text-base font-semibold text-white">
              Promouvoir un utilisateur
            </h2>
            <p className="text-text-secondary text-xs">
              Recherchez un utilisateur existant (non admin) pour lui attribuer
              un rôle administrateur.
            </p>
            <div className="max-w-md">
              <Input
                placeholder="Rechercher par nom ou email…"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                leftIcon={<Search size={16} />}
              />
            </div>

            {search.trim().length >= 2 && (
              <Card padding="none">{renderSearchResults()}</Card>
            )}
          </section>
        </div>

        {/* Promote confirm modal */}
        <ConfirmModal
          isOpen={!!promoteTarget}
          onClose={() => setPromoteTarget(null)}
          onConfirm={() => promote.mutate()}
          title="Promouvoir l'utilisateur"
          description={(() => {
            if (!promoteTarget) return "";
            const roleLabel =
              promoteTarget.role === "ROLE_SUPER_ADMIN"
                ? "Super Admin"
                : "Admin";
            return `Accorder le rôle ${roleLabel} à ${promoteTarget.user.firstName} ${promoteTarget.user.lastName} (${promoteTarget.user.emailMasked}) ?`;
          })()}
          confirmLabel="Promouvoir"
          isLoading={promote.isPending}
        />

        {/* Revoke confirm modal */}
        <ConfirmModal
          isOpen={!!revokeTarget}
          onClose={() => setRevokeTarget(null)}
          onConfirm={() => revoke.mutate()}
          title="Révoquer l'accès admin"
          description={
            revokeTarget
              ? `Retirer les droits admin de ${revokeTarget.firstName} ${revokeTarget.lastName} (${revokeTarget.email}) ? Cette action est immédiate.`
              : ""
          }
          confirmLabel="Révoquer"
          cancelLabel="Annuler"
          danger
          isLoading={revoke.isPending}
        />
      </SuperAdminGate>
    </AdminLayout>
  );
};

export default AdminManagePage;
