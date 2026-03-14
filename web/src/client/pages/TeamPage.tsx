import React, { useState } from "react";
import { UserPlus, Trash2, Mail, Crown, Check } from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card, CardHeader } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { ConfirmModal } from "@/components/ui/ConfirmModal";
import { useAuthStore } from "@/store/authStore";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import apiClient from "@/api/client";

interface TeamMember {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: "OWNER" | "ADMIN" | "MEMBER";
  joinedAt: string;
}

const ROLE_CONFIG: Record<
  TeamMember["role"],
  { label: string; color: string; bg: string }
> = {
  OWNER: { label: "Propriétaire", color: "text-warning", bg: "bg-warning/15" },
  ADMIN: {
    label: "Administrateur",
    color: "text-primary",
    bg: "bg-primary/15",
  },
  MEMBER: {
    label: "Membre",
    color: "text-text-secondary",
    bg: "bg-white/[0.06]",
  },
};

const TeamPage: React.FC = () => {
  const { user } = useAuthStore();
  const qc = useQueryClient();
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteError, setInviteError] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<TeamMember | null>(null);

  const { data: members = [], isLoading } = useQuery<TeamMember[]>({
    queryKey: ["team"],
    queryFn: () => apiClient.get("/api/team").then((r) => r.data),
  });

  const inviteMutation = useMutation({
    mutationFn: (email: string) =>
      apiClient.post("/api/team/invite", { email }),
    onSuccess: () => {
      setInviteEmail("");
      qc.invalidateQueries({ queryKey: ["team"] });
    },
    onError: () => setInviteError("Impossible d'inviter cet utilisateur."),
  });

  const removeMutation = useMutation({
    mutationFn: (memberId: string) => apiClient.delete(`/api/team/${memberId}`),
    onSuccess: () => {
      setDeleteTarget(null);
      qc.invalidateQueries({ queryKey: ["team"] });
    },
  });

  const handleInvite = () => {
    setInviteError(null);
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(inviteEmail)) {
      setInviteError("Adresse email invalide");
      return;
    }
    inviteMutation.mutate(inviteEmail);
  };

  return (
    <Layout title="Équipe">
      <div className="max-w-3xl mx-auto space-y-6 animate-slide-up">
        <div>
          <h1 className="page-title">Gestion de l'équipe</h1>
          <p className="page-subtitle">
            Invitez des collaborateurs à accéder à votre espace FlowGuard
          </p>
        </div>

        {/* Invite */}
        <Card>
          <CardHeader title="Inviter un membre" icon={<UserPlus size={16} />} />
          <div className="mt-4 flex gap-3">
            <div className="flex-1 relative">
              <Mail
                size={14}
                className="absolute left-3.5 top-1/2 -translate-y-1/2 text-text-muted pointer-events-none"
              />
              <input
                type="email"
                placeholder="collaborateur@masociete.fr"
                value={inviteEmail}
                onChange={(e) => {
                  setInviteEmail(e.target.value);
                  setInviteError(null);
                }}
                onKeyDown={(e) => e.key === "Enter" && handleInvite()}
                className="w-full bg-white/[0.04] border border-white/10 rounded-xl pl-9 pr-3.5 py-2.5 text-white text-sm placeholder:text-text-muted focus:outline-none focus:border-primary/40 focus:ring-2 focus:ring-primary/15 transition"
              />
              {inviteError && (
                <p className="text-danger text-xs mt-1.5">{inviteError}</p>
              )}
            </div>
            <Button
              onClick={handleInvite}
              isLoading={inviteMutation.isPending}
              disabled={!inviteEmail}
              leftIcon={<UserPlus size={14} />}
            >
              Inviter
            </Button>
          </div>
          {inviteMutation.isSuccess && (
            <div className="flex items-center gap-2 mt-3 text-success text-sm">
              <Check size={14} />
              Invitation envoyée à {inviteEmail || "votre collaborateur"}
            </div>
          )}
        </Card>

        {/* Members list */}
        <Card>
          <CardHeader
            title="Membres"
            action={
              <span className="px-2 py-0.5 rounded-full text-xs bg-white/[0.06] text-text-muted font-medium">
                {members.length} membre{members.length > 1 ? "s" : ""}
              </span>
            }
          />
          <div className="mt-4 space-y-1">
            {isLoading
              ? Array.from({ length: 3 }).map((_, i) => (
                  <div
                    key={i}
                    className="flex items-center gap-3 py-3 animate-pulse"
                  >
                    <div className="w-9 h-9 rounded-full bg-white/10" />
                    <div className="flex-1 space-y-1.5">
                      <div className="h-3.5 bg-white/10 rounded w-32" />
                      <div className="h-3 bg-white/10 rounded w-44" />
                    </div>
                    <div className="h-5 bg-white/10 rounded w-20" />
                  </div>
                ))
              : members.map((member) => {
                  const isMe = member.email === user?.email;
                  const cfg = ROLE_CONFIG[member.role];
                  return (
                    <div
                      key={member.id}
                      className="flex items-center gap-3 py-3 border-b border-white/[0.04] last:border-0"
                    >
                      {/* Avatar */}
                      <div className="w-9 h-9 rounded-full bg-primary/20 flex items-center justify-center text-primary text-sm font-bold shrink-0">
                        {member.firstName[0]}
                        {member.lastName[0]}
                      </div>
                      {/* Info */}
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          <p className="text-sm font-medium text-white truncate">
                            {member.firstName} {member.lastName}
                          </p>
                          {isMe && (
                            <span className="text-xs text-text-muted">
                              (vous)
                            </span>
                          )}
                          {member.role === "OWNER" && (
                            <Crown
                              size={11}
                              className="text-warning shrink-0"
                            />
                          )}
                        </div>
                        <p className="text-xs text-text-muted truncate">
                          {member.email}
                        </p>
                      </div>
                      {/* Role badge */}
                      <span
                        className={`px-2 py-0.5 rounded-full text-xs font-semibold ${cfg.color} ${cfg.bg}`}
                      >
                        {cfg.label}
                      </span>
                      {/* Remove */}
                      {!isMe && member.role !== "OWNER" && (
                        <button
                          onClick={() => setDeleteTarget(member)}
                          className="p-1.5 text-text-muted hover:text-danger transition-colors rounded-lg hover:bg-danger/10"
                          aria-label="Retirer le membre"
                        >
                          <Trash2 size={13} />
                        </button>
                      )}
                    </div>
                  );
                })}
          </div>
        </Card>
      </div>

      <ConfirmModal
        isOpen={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => {
          if (deleteTarget) removeMutation.mutate(deleteTarget.id);
        }}
        title="Retirer le membre"
        description={`Êtes-vous sûr de vouloir retirer ${deleteTarget?.firstName} ${deleteTarget?.lastName} de l'équipe ?`}
        confirmLabel="Retirer"
        cancelLabel="Annuler"
        danger
        isLoading={removeMutation.isPending}
      />
    </Layout>
  );
};

export default TeamPage;
