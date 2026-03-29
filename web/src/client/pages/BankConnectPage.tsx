import React from "react";
import {
  Building2,
  Shield,
  CheckCircle,
  AlertCircle,
  RefreshCw,
  Plus,
  Wifi,
} from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { HelpTooltip } from "@/components/ui/HelpTooltip";
import {
  useBankAccounts,
  useStartConnect,
  useSyncAccounts,
} from "@/hooks/useBanking";

const fmt = (n: number) =>
  new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR" }).format(
    n,
  );

const statusColor: Record<string, string> = {
  OK: "text-success",
  SYNCING: "text-warning",
  ERROR: "text-danger",
  PENDING: "text-text-muted",
};

const BankConnectPage: React.FC = () => {
  const { data: accounts, isLoading } = useBankAccounts();
  const startConnect = useStartConnect();
  const syncAccounts = useSyncAccounts();

  const hasAccounts = accounts && accounts.length > 0;

  return (
    <Layout title="Connexion bancaire">
      <div className="max-w-3xl mx-auto space-y-6 animate-slide-up">
        <div>
          <h1 className="page-title">Connexion bancaire</h1>
          <p className="page-subtitle">
            Connexion sécurisée · Aucun mot de passe partagé
          </p>
        </div>

        {/* Error */}
        {(startConnect.isError || syncAccounts.isError) && (
          <div className="bg-danger/10 border border-danger/30 rounded-xl p-4 flex items-center gap-3 text-danger text-sm">
            <AlertCircle size={16} />
            {startConnect.isError
              ? "Impossible de lancer la connexion. Réessayez."
              : "Synchronisation échouée."}
          </div>
        )}

        {/* Security banner */}
        <div className="flex items-center gap-4 px-4 py-3 bg-success/[0.06] border border-success/15 rounded-xl">
          <div className="w-8 h-8 rounded-xl bg-success/15 flex items-center justify-center text-success flex-shrink-0">
            <Shield size={16} />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-white text-sm font-semibold">
              Connexion 100 % sécurisée
            </p>
            <p className="text-text-secondary text-xs mt-0.5 leading-relaxed">
              Connexion bancaire certifiée · FlowGuard n&apos;accède jamais à
              vos identifiants · Chiffrement bout en bout
            </p>
          </div>
          <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-success/20 text-success">
            DSP2
          </span>
        </div>

        {/* Connected accounts or empty state */}
        {isLoading ? (
          <Card>
            <div className="h-32 animate-pulse bg-white/5 rounded-xl" />
          </Card>
        ) : hasAccounts ? (
          <Card>
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2 text-white font-semibold">
                <CheckCircle size={18} className="text-success" />
                Comptes connectés
              </div>
              <div className="flex gap-2">
                <Button
                  variant="ghost"
                  size="sm"
                  leftIcon={<RefreshCw size={14} />}
                  isLoading={syncAccounts.isPending}
                  onClick={() => syncAccounts.mutate()}
                >
                  Synchroniser
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  leftIcon={<Plus size={14} />}
                  isLoading={startConnect.isPending}
                  onClick={() => startConnect.mutate()}
                >
                  Ajouter
                </Button>
              </div>
            </div>
            <div className="space-y-3">
              {accounts.map((acc) => (
                <div
                  key={acc.id}
                  className="flex items-center gap-4 p-4 bg-white/[0.02] border border-white/[0.06] rounded-xl"
                >
                  <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center text-primary font-bold text-sm flex-shrink-0">
                    {acc.bankName?.[0] ?? "B"}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-white font-medium">{acc.bankName}</p>
                    <p className="text-text-muted text-xs truncate">
                      {acc.accountName}
                    </p>
                    {acc.ibanMasked && (
                      <p className="font-mono text-text-muted text-xs">
                        {acc.ibanMasked}
                      </p>
                    )}
                  </div>
                  <div className="text-right">
                    <p className="font-mono text-white font-semibold">
                      {fmt(acc.balance)}
                    </p>
                    <p
                      className={`text-xs font-medium ${statusColor[acc.syncStatus] ?? "text-text-muted"}`}
                    >
                      {acc.syncStatus === "OK" ? "Synchronisé" : acc.syncStatus}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </Card>
        ) : (
          <Card>
            <div className="text-center py-10">
              <div className="w-16 h-16 rounded-2xl bg-primary/10 flex items-center justify-center mx-auto mb-4">
                <Building2 size={28} className="text-primary" />
              </div>
              <h3 className="text-white font-semibold mb-2">
                Aucun compte connecté
              </h3>
              <p className="text-text-secondary text-sm mb-6 max-w-sm mx-auto">
                Connectez votre compte bancaire (Boursorama, BNP, CA…) pour
                activer les prévisions IA, alertes et analyses.
              </p>
              <Button
                variant="gradient"
                size="lg"
                isLoading={startConnect.isPending}
                leftIcon={<Wifi size={16} />}
                onClick={() => startConnect.mutate()}
              >
                Connecter ma banque
              </Button>
            </div>
          </Card>
        )}

        {/* How it works */}
        <Card>
          <p className="text-white font-semibold mb-4 flex items-center gap-1.5">
            Comment ça marche ?
            <HelpTooltip text="Connexion sécurisée en 4 étapes via les APIs Open Banking DSP2. FlowGuard n'a jamais accès à vos identifiants bancaires." />
          </p>
          <div className="space-y-4">
            {[
              {
                step: "1",
                title: "Sélectionnez votre banque",
                desc: "Boursorama, BNP, Crédit Agricole, et 2 000+ établissements",
              },
              {
                step: "2",
                title: "Authentification sécurisée",
                desc: "Via le portail officiel de votre banque (Open Banking DSP2)",
              },
              {
                step: "3",
                title: "Synchronisation automatique",
                desc: "FlowGuard importe vos derniers mois de transactions",
              },
              {
                step: "4",
                title: "IA activée",
                desc: "Prévisions, alertes et analyses disponibles immédiatement",
              },
            ].map((item) => (
              <div key={item.step} className="flex items-start gap-4">
                <div className="w-8 h-8 rounded-full bg-primary/20 flex items-center justify-center text-primary text-sm font-bold flex-shrink-0">
                  {item.step}
                </div>
                <div>
                  <p className="text-white font-medium text-sm">{item.title}</p>
                  <p className="text-text-muted text-xs mt-0.5">{item.desc}</p>
                </div>
              </div>
            ))}
          </div>
        </Card>
      </div>
    </Layout>
  );
};

export default BankConnectPage;
