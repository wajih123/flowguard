import React, { useState } from "react";
import { UserCheck, Shield, Trash2, Download, Link2 } from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card, CardHeader } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { HelpTooltip } from "@/components/ui/HelpTooltip";
import { Loader } from "@/components/ui/Loader";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { accountantApi } from "@/api/accountant";
import { format, parseISO, isPast } from "date-fns";
import { fr } from "date-fns/locale";

const AccountantPage: React.FC = () => {
  const [email, setEmail] = useState("");
  const [dlYear, setDlYear] = useState(new Date().getFullYear() - 1);
  const [downloading, setDownloading] = useState(false);
  const qc = useQueryClient();

  const { data: grants, isLoading } = useQuery({
    queryKey: ["accountant-grants"],
    queryFn: accountantApi.listGrants,
  });

  const grantMut = useMutation({
    mutationFn: () => accountantApi.grantAccess(email),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["accountant-grants"] });
      setEmail("");
    },
  });

  const revokeMut = useMutation({
    mutationFn: accountantApi.revokeAccess,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["accountant-grants"] }),
  });

  const handleDownloadFec = async () => {
    setDownloading(true);
    try {
      const text = await accountantApi.ownerDownloadFec(dlYear);
      const blob = new Blob([text], { type: "text/tab-separated-values" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `FEC_${dlYear}.txt`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } finally {
      setDownloading(false);
    }
  };

  const portalBase = `${window.location.origin}/accountant-portal`;

  return (
    <Layout title="Comptable">
      <div className="max-w-3xl mx-auto space-y-6 animate-slide-up">
        <div>
          <h1 className="page-title">Accès comptable</h1>
          <p className="page-subtitle">
            Partagez vos données financières en toute sécurité avec votre
            expert-comptable
          </p>
        </div>

        {/* FEC Export */}
        <Card>
          <CardHeader title="Export FEC" action={<HelpTooltip text="Fichier des Écritures Comptables au format légal (art. L47 A LPF). Obligatoire en cas de contrôle fiscal." />} />
          <p className="text-text-muted text-sm mt-3">
            Le Fichier des Écritures Comptables (FEC) est requis en cas de
            contrôle fiscal (art. L47 A LPF). Téléchargez les écritures d'une
            année fiscale.
          </p>
          <div className="mt-4 flex items-center gap-3">
            <select
              className="fg-input w-32"
              value={dlYear}
              onChange={(e) => setDlYear(Number(e.target.value))}
            >
              {Array.from(
                { length: 5 },
                (_, i) => new Date().getFullYear() - 1 - i,
              ).map((y) => (
                <option key={y} value={y}>
                  {y}
                </option>
              ))}
            </select>
            <Button
              onClick={handleDownloadFec}
              disabled={downloading}
              className="gap-2"
            >
              <Download size={15} />
              {downloading ? "Génération…" : `Télécharger FEC ${dlYear}`}
            </Button>
          </div>
        </Card>

        {/* Grant Access Form */}
        <Card>
          <CardHeader title="Inviter un comptable" action={<HelpTooltip text="Donnez un accès en lecture seule, sécurisé et temporaire (∞90 jours), à votre comptable sans partager vos identifiants." />} />
          <p className="text-text-muted text-sm mt-2 mb-4">
            Votre comptable recevra un lien sécurisé (valide 90 jours) lui
            donnant accès en lecture seule à vos factures, obligations fiscales
            et export FEC.
          </p>
          <div className="flex gap-3">
            <input
              className="fg-input flex-1"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="comptable@cabinet.fr"
            />
            <Button
              onClick={() => grantMut.mutate()}
              disabled={grantMut.isPending || !email || !email.includes("@")}
            >
              {grantMut.isPending ? "Envoi…" : "Inviter"}
            </Button>
          </div>
          {grantMut.isSuccess && (
            <p className="text-success text-sm mt-2 flex items-center gap-1">
              <UserCheck size={14} /> Invitation envoyée avec succès.
            </p>
          )}
        </Card>

        {/* Active Grants */}
        <Card>
          <CardHeader title="Accès actifs" action={<HelpTooltip text="Liste des comptables et collaborateurs ayant un accès actif à votre espace FlowGuard. Révoquez à tout moment." />} />
          {isLoading ? (
            <Loader text="Chargement…" />
          ) : !grants?.length ? (
            <div className="py-10 text-center text-text-muted">
              <Shield className="mx-auto mb-3 opacity-30" size={36} />
              <p>Aucun accès comptable actif.</p>
            </div>
          ) : (
            <div className="mt-4 space-y-3">
              {grants.map((g) => {
                const expired = isPast(parseISO(g.expiresAt));
                return (
                  <div
                    key={g.id}
                    className={`flex items-center justify-between gap-4 px-4 py-3.5 rounded-xl border transition ${
                      expired
                        ? "border-white/[0.04] bg-white/[0.01] opacity-60"
                        : "border-white/[0.08] bg-white/[0.03]"
                    }`}
                  >
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <UserCheck
                          size={15}
                          className={
                            expired ? "text-text-muted" : "text-primary"
                          }
                        />
                        <p className="font-medium text-white text-sm truncate">
                          {g.accountantEmail}
                        </p>
                        {expired && (
                          <span className="text-xs text-danger bg-danger/10 px-2 py-0.5 rounded-full">
                            Expiré
                          </span>
                        )}
                      </div>
                      <p className="text-text-muted text-xs">
                        Expires le{" "}
                        {format(parseISO(g.expiresAt), "d MMMM yyyy", {
                          locale: fr,
                        })}
                      </p>
                      {g.accessToken && !expired && (
                        <a
                          href={`${portalBase}?token=${g.accessToken}`}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="text-xs text-primary hover:underline flex items-center gap-1 mt-1"
                        >
                          <Link2 size={11} />
                          Copier le lien du portail
                        </a>
                      )}
                    </div>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-danger shrink-0 gap-1"
                      onClick={() => revokeMut.mutate(g.id)}
                    >
                      <Trash2 size={13} />
                      Révoquer
                    </Button>
                  </div>
                );
              })}
            </div>
          )}
        </Card>
      </div>
    </Layout>
  );
};

export default AccountantPage;
