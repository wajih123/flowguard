import React from "react";
import { Check, Zap, Star, Shield, ExternalLink } from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card, CardHeader } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { useAuthStore } from "@/store/authStore";
import type { Plan } from "@/domain/User";

const plans: {
  id: Plan;
  name: string;
  price: string;
  perMonth: boolean;
  features: string[];
  icon: React.ReactNode;
  highlight?: boolean;
}[] = [
  {
    id: "FREE",
    name: "Starter",
    price: "Gratuit",
    perMonth: false,
    features: [
      "1 compte bancaire connecté",
      "Prévisions sur 30 jours",
      "Alertes automatiques basiques",
      "Dashboard trésorerie",
    ],
    icon: <Zap size={20} />,
  },
  {
    id: "PRO",
    name: "Pro",
    price: "49 €",
    perMonth: true,
    features: [
      "5 comptes bancaires connectés",
      "Prévisions sur 90 jours",
      "Scénarios illimités",
      "Flash Crédit instantané",
      "Analyses avancées",
      "Support prioritaire",
    ],
    icon: <Star size={20} />,
    highlight: true,
  },
  {
    id: "SCALE",
    name: "Scale",
    price: "149 €",
    perMonth: true,
    features: [
      "Comptes bancaires illimités",
      "Prévisions sur 180 jours",
      "Accès API",
      "Gestion d'équipe",
      "Rapports personnalisés",
      "Support dédié 24h/7j",
      "SLA garanti",
    ],
    icon: <Shield size={20} />,
  },
];

function renderPlanButton(
  isCurrent: boolean,
  plan: (typeof plans)[0],
  currentPlan: Plan,
): React.ReactNode {
  if (isCurrent) {
    return (
      <Button variant="ghost" size="sm" className="w-full" disabled>
        Offre actuelle
      </Button>
    );
  }
  if (plan.id === "FREE" && currentPlan !== "FREE") {
    return (
      <Button variant="outline" size="sm" className="w-full">
        Rétrograder
      </Button>
    );
  }
  return (
    <Button
      variant={plan.highlight ? "primary" : "secondary"}
      size="sm"
      className="w-full"
    >
      {currentPlan === "FREE" ? "Commencer" : "Changer d'offre"}
    </Button>
  );
}

const SubscriptionPage: React.FC = () => {
  const { user } = useAuthStore();
  const currentPlan = user?.plan ?? "FREE";

  return (
    <Layout title="Abonnement">
      <div className="max-w-4xl mx-auto space-y-6 animate-fade-in">
        <div className="page-header">
          <h1 className="page-title">Abonnement</h1>
          <p className="page-subtitle">Gérez votre offre FlowGuard</p>
        </div>

        {/* Current plan */}
        <Card>
          <CardHeader
            title="Votre offre actuelle"
            action={
              <Badge variant={currentPlan === "FREE" ? "muted" : "primary"}>
                {plans.find((p) => p.id === currentPlan)?.name ?? currentPlan}
              </Badge>
            }
          />
          <p className="text-text-secondary text-sm mt-2">
            {currentPlan === "FREE"
              ? "Passez à Pro pour débloquer toutes les fonctionnalités."
              : "Merci de votre confiance. Gérez votre facturation ci-dessous."}
          </p>
          {currentPlan !== "FREE" && (
            <div className="mt-4 flex gap-3">
              <Button
                variant="outline"
                size="sm"
                rightIcon={<ExternalLink size={14} />}
              >
                Gérer la facturation
              </Button>
              <Button variant="ghost" size="sm">
                Télécharger les factures
              </Button>
            </div>
          )}
        </Card>

        {/* Plans grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {plans.map((plan) => {
            const isCurrent = plan.id === currentPlan;
            return (
              <div
                key={plan.id}
                className={`relative rounded-2xl p-6 border transition-all ${
                  isCurrent
                    ? "border-primary/60 bg-primary/5"
                    : "border-white/[0.06] bg-surface"
                } ${plan.highlight ? "ring-1 ring-primary/20" : ""}`}
              >
                {plan.highlight && !isCurrent && (
                  <div className="absolute -top-3 left-1/2 -translate-x-1/2">
                    <span className="text-xs px-3 py-1 rounded-full bg-primary text-white font-medium">
                      Recommandé
                    </span>
                  </div>
                )}
                {isCurrent && (
                  <div className="absolute -top-3 left-1/2 -translate-x-1/2">
                    <span className="text-xs px-3 py-1 rounded-full bg-success text-white font-medium">
                      Offre actuelle
                    </span>
                  </div>
                )}

                <div
                  className={`inline-flex p-2.5 rounded-xl mb-4 ${isCurrent ? "bg-primary/20 text-primary" : "bg-white/10 text-text-secondary"}`}
                >
                  {plan.icon}
                </div>

                <h3 className="text-lg font-bold text-white">{plan.name}</h3>
                <div className="mt-1 mb-5">
                  <span className="text-2xl font-bold text-white">
                    {plan.price}
                  </span>
                  {plan.perMonth && (
                    <span className="text-text-secondary text-sm">/mois</span>
                  )}
                </div>

                <ul className="space-y-2 mb-6">
                  {plan.features.map((f) => (
                    <li key={f} className="flex items-start gap-2 text-sm">
                      <Check
                        size={14}
                        className="text-success mt-0.5 shrink-0"
                      />
                      <span className="text-text-secondary">{f}</span>
                    </li>
                  ))}
                </ul>

                {renderPlanButton(isCurrent, plan, currentPlan)}
              </div>
            );
          })}
        </div>

        <p className="text-xs text-text-muted text-center">
          Paiements sécurisés par Stripe. Annulation possible à tout moment. TVA
          en sus.
        </p>
      </div>
    </Layout>
  );
};

export default SubscriptionPage;
