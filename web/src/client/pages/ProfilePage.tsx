import React from "react";
import { User, Building2, Mail, Calendar } from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card, CardHeader } from "@/components/ui/Card";
import { Badge, kycStatusBadge } from "@/components/ui/Badge";
import { useAuthStore } from "@/store/authStore";
import { format, parseISO } from "date-fns";
import { fr } from "date-fns/locale";

const USER_TYPE_LABELS: Record<string, string> = {
  INDIVIDUAL: "Particulier",
  FREELANCE: "Freelance / Auto-entrepreneur",
  TPE: "TPE",
  PME: "PME",
  SME: "Grande entreprise",
};

const ProfilePage: React.FC = () => {
  const { user } = useAuthStore();
  if (!user) return null;

  return (
    <Layout title="Mon profil">
      <div className="max-w-2xl mx-auto space-y-6 animate-fade-in">
        <div className="page-header">
          <h1 className="page-title">Mon profil</h1>
          <p className="page-subtitle">
            Informations de votre compte FlowGuard
          </p>
        </div>

        {/* Avatar + name */}
        <Card>
          <div className="flex items-center gap-6">
            <div className="w-20 h-20 rounded-2xl bg-gradient-to-br from-primary to-purple flex items-center justify-center text-white text-2xl font-bold flex-shrink-0">
              {user.firstName[0]}
              {user.lastName[0]}
            </div>
            <div>
              <h2 className="text-xl font-bold text-white">
                {user.firstName} {user.lastName}
              </h2>
              {user.companyName && (
                <p className="text-text-secondary text-sm mt-0.5">
                  {user.companyName}
                </p>
              )}
              <div className="flex items-center gap-2 mt-2">
                <Badge variant="muted">
                  {USER_TYPE_LABELS[user.userType] ?? user.userType}
                </Badge>
                <Badge variant={kycStatusBadge(user.kycStatus)}>
                  KYC : {user.kycStatus}
                </Badge>
              </div>
            </div>
          </div>
        </Card>

        {/* Account details */}
        <Card>
          <CardHeader
            title="Informations du compte"
            icon={<User size={18} />}
          />
          <div className="space-y-4">
            {[
              {
                icon: <Mail size={16} />,
                label: "Adresse email",
                value: user.email,
              },
              {
                icon: <Building2 size={16} />,
                label: "Type de profil",
                value: USER_TYPE_LABELS[user.userType] ?? user.userType,
              },
              {
                icon: <Calendar size={16} />,
                label: "Membre depuis",
                value: format(parseISO(user.createdAt), "d MMMM yyyy", {
                  locale: fr,
                }),
              },
            ].map((row) => (
              <div
                key={row.label}
                className="flex items-center gap-4 py-3 border-b border-white/[0.04] last:border-0"
              >
                <div className="w-8 h-8 rounded-lg bg-white/[0.04] flex items-center justify-center text-text-secondary flex-shrink-0">
                  {row.icon}
                </div>
                <div>
                  <p className="text-text-muted text-xs">{row.label}</p>
                  <p className="text-white text-sm font-medium mt-0.5">
                    {row.value}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </Card>

        {/* KYC */}
        <Card>
          <CardHeader
            title="Statut KYC"
            subtitle="Vérification d'identité réglementaire"
          />
          <div
            className={`p-4 rounded-xl border ${
              user.kycStatus === "APPROVED"
                ? "bg-success/10 border-success/20"
                : user.kycStatus === "REJECTED"
                  ? "bg-danger/10 border-danger/20"
                  : "bg-warning/10 border-warning/20"
            }`}
          >
            <div className="flex items-center gap-3">
              <Badge
                variant={kycStatusBadge(user.kycStatus)}
                className="text-sm"
              >
                {user.kycStatus}
              </Badge>
              <p className="text-text-secondary text-sm">
                {user.kycStatus === "APPROVED" &&
                  "Votre identité a été vérifiée avec succès."}
                {user.kycStatus === "PENDING" &&
                  "Votre dossier KYC est en attente de traitement."}
                {user.kycStatus === "IN_PROGRESS" &&
                  "Votre vérification KYC est en cours."}
                {user.kycStatus === "REJECTED" &&
                  "Votre KYC a été refusé. Contactez le support."}
              </p>
            </div>
          </div>
        </Card>
      </div>
    </Layout>
  );
};

export default ProfilePage;
