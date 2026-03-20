import React, { useState } from "react";
import {
  Shield,
  Check,
  RotateCcw,
  Fingerprint,
  Zap,
  ChevronRight,
} from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { Card, CardHeader } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { HelpTooltip } from "@/components/ui/HelpTooltip";
import { EmptyState } from "@/components/ui/EmptyState";
import { Loader } from "@/components/ui/Loader";
import {
  useFlashCredits,
  useRequestFlashCredit,
  useRetractCredit,
} from "@/hooks/useFlashCredit";
import { format, parseISO } from "date-fns";
import { fr } from "date-fns/locale";

const fmt = (n: number) =>
  new Intl.NumberFormat("fr-FR", { style: "currency", currency: "EUR" }).format(
    n,
  );

const FEE_RATE = 0.015; // 1,5% → always shown in €

// ─── Step 1: Proposition ──────────────────────────────────────────────────────
const StepProposition: React.FC<{
  amount: number;
  purpose: string;
  onAmountChange: (v: number) => void;
  onPurposeChange: (v: string) => void;
  onNext: () => void;
}> = ({ amount, purpose, onAmountChange, onPurposeChange, onNext }) => {
  const fee = amount * FEE_RATE;
  const total = amount + fee;

  return (
    <div className="space-y-6 animate-slide-up">
      {/* Benefits banner */}
      <div className="grid grid-cols-3 gap-3">
        {[
          {
            icon: "⚡",
            title: "Réponse en 5 min",
            desc: "Décision automatique",
          },
          { icon: "🔒", title: "Sans garantie", desc: "Basé sur votre flux" },
          { icon: "↩️", title: "14j pour rétracter", desc: "Art. L312-19" },
        ].map((f) => (
          <Card key={f.title} padding="sm" className="text-center">
            <p className="text-xl mb-1">{f.icon}</p>
            <p className="text-white text-xs font-semibold">{f.title}</p>
            <p className="text-text-muted text-[11px] mt-0.5">{f.desc}</p>
          </Card>
        ))}
      </div>

      <Card>
        <CardHeader title="Configurer votre Réserve" action={<HelpTooltip text="Définissez le montant et l'objet de votre financement instantané. Réponse automatique en moins de 5 minutes." />} />
        <div className="mt-5 space-y-6">
          {/* Amount slider */}
          <div>
            <div className="flex items-center justify-between mb-3">
              <label
                htmlFor="reserve-amount"
                className="text-sm font-medium text-text-secondary"
              >
                Montant souhaité
              </label>
              <span className="font-numeric text-primary text-xl font-bold">
                {fmt(amount)}
              </span>
            </div>
            <input
              id="reserve-amount"
              type="range"
              min={500}
              max={10000}
              step={250}
              value={amount}
              onChange={(e) => onAmountChange(Number(e.target.value))}
              className="w-full accent-primary h-1.5 rounded-full"
            />
            <div className="flex justify-between text-text-muted text-xs mt-1.5">
              <span>500 €</span>
              <span>5 000 €</span>
              <span>10 000 €</span>
            </div>
          </div>

          {/* Purpose */}
          <div>
            <label
              htmlFor="reserve-purpose"
              className="block text-sm font-medium text-text-secondary mb-1.5"
            >
              Objet du financement
            </label>
            <input
              id="reserve-purpose"
              type="text"
              value={purpose}
              onChange={(e) => onPurposeChange(e.target.value)}
              placeholder="Ex. : salaires, fournisseurs, TVA…"
              className="w-full bg-white/[0.04] border border-white/10 rounded-xl px-3.5 py-2.5 text-white text-sm placeholder:text-text-muted focus:outline-none focus:border-primary/40 focus:ring-2 focus:ring-primary/15 transition"
            />
          </div>

          {/* Cost summary — commission always in € */}
          <div className="bg-primary/[0.06] border border-primary/15 rounded-xl p-4 space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-text-secondary">Montant demandé</span>
              <span className="font-numeric text-white font-medium">
                {fmt(amount)}
              </span>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-text-secondary flex items-center gap-1.5">
                Commission
                <HelpTooltip text="Frais de service FlowGuard : 1,5% du montant emprunté, prélevés à l'octroi. Toujours affichés en euros." />
              </span>
              {/* ABSOLUTE RULE: always in €, never as % */}
              <span className="font-numeric text-warning font-medium">
                {fmt(fee)}
              </span>
            </div>
            <div className="flex justify-between border-t border-white/10 pt-2 font-semibold">
              <span className="text-white flex items-center gap-1">Total à rembourser <HelpTooltip text="Montant emprunté + commission, à rembourser à l'échéance indiquée dans le contrat." /></span>
              <span className="font-numeric text-primary">{fmt(total)}</span>
            </div>
          </div>

          <p className="text-text-muted text-xs leading-relaxed">
            En poursuivant, vous acceptez les conditions générales de la Réserve
            de Trésorerie FlowGuard. Vous disposez de 14 jours pour exercer
            votre droit de rétractation (Art. L312-19 Code de la consommation).
          </p>

          <Button
            variant="gradient"
            size="lg"
            fullWidth
            rightIcon={<ChevronRight size={16} />}
            disabled={!purpose.trim()}
            onClick={onNext}
          >
            Continuer
          </Button>
        </div>
      </Card>
    </div>
  );
};

// ─── Step 2: Biometric confirmation ───────────────────────────────────────────
const StepConfirm: React.FC<{
  amount: number;
  purpose: string;
  fee: number;
  isLoading: boolean;
  onConfirm: () => void;
  onBack: () => void;
}> = ({ amount, purpose, fee, isLoading, onConfirm, onBack }) => (
  <div className="max-w-md mx-auto space-y-6 animate-slide-up">
    <Card className="text-center">
      <CardHeader title="Confirmer votre demande" action={<HelpTooltip text="Récapitulatif de votre demande avant envoi. Confirmez avec votre empreinte ou un double-clic sur le bouton." />} />
      <div className="mt-6 space-y-4">
        <div className="grid grid-cols-2 gap-3 text-left">
          <div className="bg-white/[0.03] rounded-xl p-3">
            <p className="text-text-muted text-xs mb-1">Montant</p>
            <p className="font-numeric text-white font-bold">{fmt(amount)}</p>
          </div>
          <div className="bg-white/[0.03] rounded-xl p-3">
            <p className="text-text-muted text-xs mb-1">Commission</p>
            <p className="font-numeric text-warning font-bold">{fmt(fee)}</p>
          </div>
          <div className="col-span-2 bg-white/[0.03] rounded-xl p-3">
            <p className="text-text-muted text-xs mb-1">Objet</p>
            <p className="text-white text-sm">{purpose}</p>
          </div>
        </div>

        {/* Biometric prompt */}
        <div className="flex flex-col items-center gap-3 py-4">
          <div className="w-16 h-16 rounded-full bg-primary/15 border-2 border-primary/30 flex items-center justify-center">
            <Fingerprint size={28} className="text-primary" />
          </div>
          <p className="text-text-secondary text-sm">
            Appuyez pour confirmer avec votre empreinte
          </p>
        </div>

        <div className="flex gap-3">
          <Button
            variant="ghost"
            size="lg"
            fullWidth
            onClick={onBack}
            disabled={isLoading}
          >
            Retour
          </Button>
          <Button
            variant="gradient"
            size="lg"
            fullWidth
            isLoading={isLoading}
            leftIcon={<Shield size={15} />}
            confirmDelay
            onClick={onConfirm}
          >
            Valider
          </Button>
        </div>
      </div>
    </Card>
  </div>
);

// ─── Step 3: Success ───────────────────────────────────────────────────────────
const StepSuccess: React.FC<{ amount: number; onReset: () => void }> = ({
  amount,
  onReset,
}) => (
  <div className="max-w-sm mx-auto text-center space-y-6 animate-slide-up py-8">
    <div className="relative w-20 h-20 mx-auto">
      <div className="w-20 h-20 rounded-full bg-success/20 border-2 border-success/40 flex items-center justify-center">
        <Check
          size={36}
          className="text-success animate-draw-check"
          strokeWidth={2.5}
        />
      </div>
    </div>
    <div>
      <h2
        className="text-2xl font-bold text-white"
        style={{ fontFamily: "var(--font-display)" }}
      >
        Demande soumise !
      </h2>
      <p className="text-text-secondary mt-2">
        Votre demande de{" "}
        <span className="font-numeric text-white font-semibold">
          {fmt(amount)}
        </span>{" "}
        est en cours de traitement. Réponse sous 5 minutes.
      </p>
    </div>
    <div className="bg-success/[0.06] border border-success/15 rounded-xl p-4 text-sm text-text-secondary">
      Vous recevrez une notification dès que les fonds sont disponibles sur
      votre compte.
    </div>
    <Button variant="outline" size="lg" fullWidth onClick={onReset}>
      Retour à mes crédits
    </Button>
  </div>
);

// ─── Main page ─────────────────────────────────────────────────────────────────
const FlashCreditPage: React.FC = () => {
  const [step, setStep] = useState<"list" | "propose" | "confirm" | "success">(
    "list",
  );
  const [amount, setAmount] = useState(2000);
  const [purpose, setPurpose] = useState("");
  const [error, setError] = useState<string | null>(null);

  const { data: credits, isLoading } = useFlashCredits();
  const request = useRequestFlashCredit();
  const retract = useRetractCredit();

  const fee = amount * FEE_RATE;

  const handleConfirm = async () => {
    setError(null);
    try {
      await request.mutateAsync({ amount, purpose });
      setStep("success");
    } catch {
      setError("Erreur lors de la demande. Veuillez réessayer.");
      setStep("propose");
    }
  };

  if (step === "propose") {
    return (
      <Layout title="Réserve de Trésorerie">
        <div className="max-w-2xl mx-auto space-y-5">
          <div className="flex items-center gap-3">
            <button
              onClick={() => setStep("list")}
              className="text-text-muted hover:text-white transition text-sm"
            >
              ← Retour
            </button>
            <div className="flex-1" />
            {/* Step indicator */}
            <div className="flex items-center gap-2 text-xs text-text-muted">
              <span className="w-5 h-5 rounded-full bg-primary text-white flex items-center justify-center font-bold">
                1
              </span>
              <span className="w-8 h-px bg-white/10" />
              <span className="w-5 h-5 rounded-full bg-white/10 text-text-muted flex items-center justify-center font-bold">
                2
              </span>
              <span className="w-8 h-px bg-white/10" />
              <span className="w-5 h-5 rounded-full bg-white/10 text-text-muted flex items-center justify-center font-bold">
                3
              </span>
            </div>
          </div>
          <StepProposition
            amount={amount}
            purpose={purpose}
            onAmountChange={setAmount}
            onPurposeChange={setPurpose}
            onNext={() => setStep("confirm")}
          />
        </div>
      </Layout>
    );
  }

  if (step === "confirm") {
    return (
      <Layout title="Réserve de Trésorerie">
        <div className="max-w-2xl mx-auto space-y-5">
          <div className="flex items-center gap-3">
            <button
              onClick={() => setStep("propose")}
              className="text-text-muted hover:text-white transition text-sm"
            >
              ← Retour
            </button>
            <div className="flex-1" />
            <div className="flex items-center gap-2 text-xs text-text-muted">
              <span className="w-5 h-5 rounded-full bg-success/30 text-success flex items-center justify-center font-bold">
                ✓
              </span>
              <span className="w-8 h-px bg-white/10" />
              <span className="w-5 h-5 rounded-full bg-primary text-white flex items-center justify-center font-bold">
                2
              </span>
              <span className="w-8 h-px bg-white/10" />
              <span className="w-5 h-5 rounded-full bg-white/10 text-text-muted flex items-center justify-center font-bold">
                3
              </span>
            </div>
          </div>
          <StepConfirm
            amount={amount}
            purpose={purpose}
            fee={fee}
            isLoading={request.isPending}
            onConfirm={handleConfirm}
            onBack={() => setStep("propose")}
          />
        </div>
      </Layout>
    );
  }

  if (step === "success") {
    return (
      <Layout title="Réserve de Trésorerie">
        <StepSuccess
          amount={amount}
          onReset={() => {
            setStep("list");
            setAmount(2000);
            setPurpose("");
          }}
        />
      </Layout>
    );
  }

  // ─── Step 0: Credits list ────────────────────────────────────────────────────
  return (
    <Layout title="Réserve de Trésorerie">
      <div className="max-w-4xl mx-auto space-y-6 animate-slide-up">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="page-title">Réserve de Trésorerie</h1>
            <p className="page-subtitle">
              Financement instantané jusqu'à 10 000 €
            </p>
          </div>
          <Button
            variant="gradient"
            leftIcon={<Zap size={15} />}
            onClick={() => setStep("propose")}
          >
            Activer la Réserve
          </Button>
        </div>

        {error && (
          <div className="flex items-center gap-3 px-4 py-3 bg-danger/[0.06] border border-danger/20 rounded-xl text-sm text-danger">
            {error}
          </div>
        )}

        {/* Credits list */}
        <Card>
          <CardHeader title="Mes financements" action={<HelpTooltip text="Historique de vos réserves de trésorerie FlowGuard actives et passées avec statut et options de rétractation." />} />
          {isLoading ? (
            <Loader />
          ) : !credits?.length ? (
            <EmptyState
              title="Aucun financement actif"
              description="Faites votre première demande de Réserve de Trésorerie"
              action={
                <Button
                  variant="gradient"
                  leftIcon={<Zap size={15} />}
                  onClick={() => setStep("propose")}
                >
                  Activer la Réserve
                </Button>
              }
            />
          ) : (
            <div className="space-y-3 mt-3">
              {credits.map((c) => (
                <div
                  key={c.id}
                  className="flex items-center gap-4 p-4 bg-white/[0.02] border border-white/[0.05] rounded-xl"
                >
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1 flex-wrap">
                      <span className="font-numeric text-white font-semibold">
                        {fmt(c.amount)}
                      </span>
                      <span
                        className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                          c.status === "APPROVED" || c.status === "DISBURSED"
                            ? "bg-success/20 text-success"
                            : c.status === "PENDING"
                              ? "bg-warning/20 text-warning"
                              : "bg-white/10 text-text-muted"
                        }`}
                      >
                        {c.status}
                      </span>
                    </div>
                    <p className="text-text-secondary text-sm truncate">
                      {c.purpose}
                    </p>
                    <div className="flex gap-4 mt-1.5 text-xs text-text-muted flex-wrap">
                      {c.dueDate && (
                        <span>
                          Échéance :{" "}
                          {format(parseISO(c.dueDate), "d MMM yyyy", {
                            locale: fr,
                          })}
                        </span>
                      )}
                      {/* Commission in € — ABSOLUTE RULE */}
                      {Boolean(c.fee) && (
                        <span className="font-numeric">
                          Commission : {fmt(c.fee)}
                        </span>
                      )}
                    </div>
                  </div>
                  {c.status === "DISBURSED" &&
                    !c.retractionExercised &&
                    c.retractionDeadline && (
                      <Button
                        variant="ghost"
                        size="sm"
                        leftIcon={<RotateCcw size={13} />}
                        isLoading={retract.isPending}
                        onClick={() => retract.mutate(c.id)}
                      >
                        Rétracter
                      </Button>
                    )}
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>
    </Layout>
  );
};

export default FlashCreditPage;
