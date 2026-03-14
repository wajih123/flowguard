import React, { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  User,
  ShieldCheck,
  Ban,
  CheckCircle,
  Trash2,
  KeyRound,
} from "lucide-react";
import { AdminLayout } from "@/components/layout/AdminLayout";
import { Card, CardHeader } from "@/components/ui/Card";
import { Badge, kycStatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Loader } from "@/components/ui/Loader";
import { Select } from "@/components/ui/Input";
import { ConfirmModal } from "@/components/ui/ConfirmModal";
import { adminApi } from "@/api/admin";
import { useAuthStore } from "@/store/authStore";
import { format, parseISO } from "date-fns";
import { fr } from "date-fns/locale";

const USER_TYPE_LABELS: Record<string, string> = {
  INDIVIDUAL: "Particulier",
  FREELANCE: "Freelance",
  TPE: "TPE",
  PME: "PME",
  SME: "Grande entreprise",
};

const KYC_OPTIONS = [
  { value: "PENDING", label: "En attente" },
  { value: "IN_PROGRESS", label: "En cours" },
  { value: "APPROVED", label: "Approuvé" },
  { value: "REJECTED", label: "Refusé" },
];

const UserDetailPage: React.FC = () => {
  const { userId } = useParams<{ userId: string }>();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const isSuperAdmin = useAuthStore((s) => s.user?.role === "ROLE_SUPER_ADMIN");

  const [gdprModal, setGdprModal] = useState(false);
  const [disableModal, setDisableModal] = useState(false);

  const { data: user, isLoading } = useQuery({
    queryKey: ["admin", "users", userId],
    queryFn: () => adminApi.getUser(userId!),
    enabled: !!userId,
  });

  const updateKyc = useMutation({
    mutationFn: (status: string) => adminApi.updateKycStatus(userId!, status),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: ["admin", "users", userId] }),
  });

  const toggleDisable = useMutation({
    mutationFn: () =>
      user?.disabled
        ? adminApi.enableUser(userId!)
        : adminApi.disableUser(userId!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["admin", "users", userId] });
      setDisableModal(false);
    },
  });

  const gdprDelete = useMutation({
    mutationFn: () => adminApi.gdprDeleteUser(userId!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["admin", "users"] });
      navigate("/admin/users");
    },
  });

  if (isLoading)
    return (
      <AdminLayout>
        <Loader fullScreen text="Chargement…" />
      </AdminLayout>
    );

  if (!user) return null;

  const fmt = (d?: string) =>
    d ? format(parseISO(d), "d MMMM yyyy à HH:mm", { locale: fr }) : "—";

  return (
    <AdminLayout title={`${user.firstName} ${user.lastName}`}>
      <div className="max-w-3xl mx-auto space-y-6 animate-fade-in">
        {/* Header */}
        <div className="flex items-center gap-4">
          <Button
            variant="ghost"
            size="sm"
            leftIcon={<ArrowLeft size={16} />}
            onClick={() => navigate(-1)}
          >
            Retour
          </Button>
          <div className="flex-1">
            <h1 className="text-xl font-bold text-white">
              {user.firstName} {user.lastName}
            </h1>
            <p className="text-text-muted text-sm">{user.email}</p>
          </div>
          <div className="flex items-center gap-2">
            <Badge variant={kycStatusBadge(user.kycStatus)}>
              {user.kycStatus}
            </Badge>
            {user.disabled && <Badge variant="danger">Désactivé</Badge>}
          </div>
        </div>

        {/* Informations */}
        <Card>
          <CardHeader title="Informations" icon={<User size={18} />} />
          <div className="grid grid-cols-2 gap-3">
            {[
              { label: "Email", value: user.email },
              { label: "Entreprise", value: user.companyName ?? "—" },
              {
                label: "Type",
                value: USER_TYPE_LABELS[user.userType] ?? user.userType,
              },
              { label: "Rôle", value: user.role },
              { label: "Inscrit le", value: fmt(user.createdAt) },
              { label: "Modifié le", value: fmt(user.updatedAt) },
              { label: "RGPD consent", value: fmt(user.gdprConsentAt) },
              { label: "Swan onboarding", value: user.swanOnboardingId ?? "—" },
            ].map((f) => (
              <div key={f.label} className="p-3 bg-white/[0.02] rounded-xl">
                <p className="text-text-muted text-xs mb-1">{f.label}</p>
                <p className="text-white text-sm font-medium break-all">
                  {f.value}
                </p>
              </div>
            ))}
          </div>
        </Card>

        {/* KYC */}
        <Card>
          <CardHeader title="Statut KYC" icon={<ShieldCheck size={18} />} />
          <div className="flex items-center gap-4">
            <div className="flex-1">
              <Select
                value={user.kycStatus}
                onChange={(e) => updateKyc.mutate(e.target.value)}
                options={KYC_OPTIONS}
              />
            </div>
            <Badge variant={kycStatusBadge(user.kycStatus)} className="text-sm">
              {user.kycStatus}
            </Badge>
          </div>
          {updateKyc.isPending && (
            <p className="text-text-muted text-xs mt-2">Mise à jour…</p>
          )}
        </Card>

        {/* Statut compte */}
        {user.disabled && (
          <Card>
            <CardHeader
              title="Compte désactivé"
              icon={<Ban size={18} className="text-danger" />}
            />
            <div className="space-y-2">
              <p className="text-text-secondary text-sm">
                Désactivé le :{" "}
                <span className="text-white">{fmt(user.disabledAt)}</span>
              </p>
              {user.disabledReason && (
                <p className="text-text-secondary text-sm">
                  Raison :{" "}
                  <span className="text-white">{user.disabledReason}</span>
                </p>
              )}
            </div>
          </Card>
        )}

        {/* Actions */}
        <Card>
          <CardHeader title="Actions admin" />
          <div className="flex flex-wrap gap-3">
            <Button
              variant={user.disabled ? "primary" : "outline"}
              size="sm"
              leftIcon={
                user.disabled ? <CheckCircle size={16} /> : <Ban size={16} />
              }
              onClick={() => setDisableModal(true)}
            >
              {user.disabled ? "Réactiver le compte" : "Désactiver le compte"}
            </Button>

            {isSuperAdmin && (
              <Button
                variant="danger"
                size="sm"
                leftIcon={<Trash2 size={16} />}
                onClick={() => setGdprModal(true)}
              >
                Suppression RGPD
              </Button>
            )}
          </div>
        </Card>
      </div>

      {/* Confirm: disable/enable */}
      <ConfirmModal
        isOpen={disableModal}
        title={user.disabled ? "Réactiver le compte" : "Désactiver le compte"}
        description={
          user.disabled
            ? `Réactiver le compte de ${user.firstName} ${user.lastName} ?`
            : `Désactiver le compte de ${user.firstName} ${user.lastName} ? L'utilisateur ne pourra plus se connecter.`
        }
        confirmLabel={user.disabled ? "Réactiver" : "Désactiver"}
        danger={!user.disabled}
        onConfirm={() => toggleDisable.mutate()}
        onClose={() => setDisableModal(false)}
        isLoading={toggleDisable.isPending}
      />

      {/* Confirm: GDPR delete */}
      <ConfirmModal
        isOpen={gdprModal}
        title="Suppression RGPD irréversible"
        description={`Les données personnelles de ${user.firstName} ${user.lastName} seront pseudonymisées définitivement. Cette action est irréversible.`}
        confirmLabel="Supprimer les données"
        danger
        requireTyping={user.email}
        onConfirm={() => gdprDelete.mutate()}
        onClose={() => setGdprModal(false)}
        isLoading={gdprDelete.isPending}
      />
    </AdminLayout>
  );
};

export default UserDetailPage;
