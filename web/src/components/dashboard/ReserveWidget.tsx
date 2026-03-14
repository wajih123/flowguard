import React, { useState } from "react";
import { Zap, CheckCircle2, Loader2, X, AlertTriangle } from "lucide-react";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { AmountDisplay } from "@/components/ui/AmountDisplay";
import { formatAmount, formatDate } from "@/utils/format";
import type { DashboardData } from "@/types";
import apiClient from "@/api/client";
import { addDays, format } from "date-fns";

interface ReserveWidgetProps {
  dashboard?: DashboardData;
  isOpen: boolean;
  onClose: () => void;
  onActivate: () => void;
}

type Step = "proposal" | "confirm" | "success";

const COMMISSION_RATE = 0.015; // 1.5%

export const ReserveWidget: React.FC<ReserveWidgetProps> = ({
  dashboard,
  isOpen,
  onClose,
  onActivate,
}) => {
  const [step, setStep] = useState<Step>("proposal");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const amount = dashboard?.reserveMaxAmount ?? 2000;
  const commission = Math.round(amount * COMMISSION_RATE * 100) / 100;
  const total = amount + commission;
  const repaymentDate = format(addDays(new Date(), 30), "yyyy-MM-dd");

  const handleConfirm = async () => {
    if (!dashboard?.account.id) return;
    setIsSubmitting(true);
    setError(null);
    try {
      await apiClient.post("/api/credit/activate", {
        accountId: dashboard.account.id,
        amount,
        reason: "Déficit de trésorerie prévu",
      });
      setStep("success");
    } catch {
      setError("Erreur lors de l'activation. Veuillez réessayer.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleClose = () => {
    setStep("proposal");
    setError(null);
    onClose();
  };

  // Compact widget shown on the dashboard when not in modal
  const WidgetCard = (
    <Card padding="md" className="h-full flex flex-col">
      <div className="flex items-center gap-2 mb-4">
        <div className="w-9 h-9 rounded-xl bg-primary/10 flex items-center justify-center">
          <Zap size={18} className="text-primary" />
        </div>
        <div>
          <p
            className="text-white text-sm font-semibold"
            style={{ fontFamily: "var(--font-display)" }}
          >
            Réserve FlowGuard
          </p>
          <p className="text-text-muted text-xs">Financement instantané</p>
        </div>
      </div>

      {dashboard?.hasHighAlert ? (
        <>
          <div className="flex-1 bg-danger/[0.06] border border-danger/15 rounded-xl p-3 mb-4">
            <p className="text-danger text-xs font-medium mb-1">
              Déficit détecté
            </p>
            <p className="text-text-secondary text-xs leading-snug">
              {dashboard.highAlertMessage}
            </p>
          </div>
          <div className="space-y-1.5 mb-4">
            <div className="flex justify-between text-xs">
              <span className="text-text-muted">Montant disponible</span>
              <span className="font-numeric text-white">
                {formatAmount(amount)}
              </span>
            </div>
            <div className="flex justify-between text-xs">
              <span className="text-text-muted">Frais</span>
              <span className="font-numeric text-text-secondary">
                {formatAmount(commission)}
              </span>
            </div>
          </div>
          <Button
            variant="gradient"
            size="md"
            fullWidth
            leftIcon={<Zap size={16} />}
            onClick={onActivate}
          >
            Activer la Réserve
          </Button>
        </>
      ) : (
        <div className="flex-1 flex flex-col items-center justify-center py-4 text-center gap-2">
          <CheckCircle2 size={28} className="text-success" />
          <p className="text-text-secondary text-sm">
            Votre trésorerie est saine
          </p>
          <p className="text-text-muted text-xs">
            La Réserve se déclenchera automatiquement si nécessaire.
          </p>
        </div>
      )}
    </Card>
  );

  return (
    <>
      {WidgetCard}

      {/* 3-step modal */}
      <Modal
        isOpen={isOpen}
        onClose={handleClose}
        title={
          step === "proposal"
            ? "Activer la Réserve"
            : step === "confirm"
              ? "Confirmer le virement"
              : "Virement en cours"
        }
        size="sm"
      >
        {step === "proposal" && (
          <div className="space-y-5">
            {/* Alert summary */}
            <div className="bg-danger/[0.08] border border-danger/20 rounded-xl p-4">
              <div className="flex items-start gap-2">
                <AlertTriangle
                  size={16}
                  className="text-danger flex-shrink-0 mt-0.5"
                />
                <p className="text-sm text-text-secondary leading-snug">
                  {dashboard?.highAlertMessage ??
                    "Déficit de trésorerie prévu."}
                </p>
              </div>
            </div>

            {/* Amount details */}
            <div className="space-y-3">
              <div className="flex items-center justify-between py-2 border-b border-white/[0.06]">
                <span className="text-text-secondary text-sm">
                  Montant viré
                </span>
                <AmountDisplay
                  amount={amount}
                  size="md"
                  colorOverride="white"
                />
              </div>
              <div className="flex items-center justify-between py-2 border-b border-white/[0.06]">
                <div>
                  <span className="text-text-secondary text-sm">
                    Frais de service
                  </span>
                  <p className="text-text-muted text-xs">
                    Prélevés au remboursement
                  </p>
                </div>
                <AmountDisplay
                  amount={commission}
                  size="md"
                  colorOverride="muted"
                />
              </div>
              <div className="flex items-center justify-between py-2">
                <span className="text-text-secondary text-sm">
                  Remboursement estimé
                </span>
                <span className="text-xs text-text-muted font-numeric">
                  {formatDate(repaymentDate)}
                </span>
              </div>
            </div>

            <div className="bg-white/[0.04] rounded-xl p-3 text-center">
              <p className="text-text-muted text-xs">
                Disponible sur votre compte{" "}
                <span className="text-white font-medium">sous 2h</span>
              </p>
            </div>

            <Button
              variant="gradient"
              size="lg"
              fullWidth
              leftIcon={<Zap size={18} />}
              onClick={() => setStep("confirm")}
            >
              Continuer
            </Button>
          </div>
        )}

        {step === "confirm" && (
          <div className="space-y-5">
            <div className="bg-white/[0.04] border border-white/[0.08] rounded-xl divide-y divide-white/[0.06]">
              <div className="flex justify-between px-4 py-3">
                <span className="text-text-secondary text-sm">Virement</span>
                <AmountDisplay
                  amount={amount}
                  size="md"
                  colorOverride="success"
                />
              </div>
              <div className="flex justify-between px-4 py-3">
                <span className="text-text-secondary text-sm">Frais</span>
                <AmountDisplay
                  amount={commission}
                  size="md"
                  colorOverride="muted"
                />
              </div>
              <div className="flex justify-between px-4 py-3">
                <span className="text-white text-sm font-semibold">
                  Total à rembourser
                </span>
                <AmountDisplay amount={total} size="md" colorOverride="white" />
              </div>
            </div>

            {error && (
              <p className="text-danger text-sm text-center">{error}</p>
            )}

            <div className="flex gap-3">
              <Button
                variant="ghost"
                size="md"
                fullWidth
                onClick={() => setStep("proposal")}
                disabled={isSubmitting}
              >
                Retour
              </Button>
              <Button
                variant="gradient"
                size="md"
                fullWidth
                isLoading={isSubmitting}
                onClick={handleConfirm}
                confirmDelay
              >
                Confirmer le virement
              </Button>
            </div>
          </div>
        )}

        {step === "success" && (
          <div className="flex flex-col items-center text-center gap-5 py-4">
            {/* Animated checkmark */}
            <div className="w-16 h-16 rounded-full bg-success/10 flex items-center justify-center">
              <svg
                className="animate-scale-in"
                width="40"
                height="40"
                viewBox="0 0 40 40"
              >
                <circle
                  cx="20"
                  cy="20"
                  r="18"
                  fill="none"
                  stroke="#10B981"
                  strokeWidth="2"
                />
                <path
                  d="M11 20l7 7 12-13"
                  fill="none"
                  stroke="#10B981"
                  strokeWidth="2.5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  style={{
                    strokeDasharray: 30,
                    strokeDashoffset: 0,
                    animation: "drawCheck 0.6s ease-out forwards",
                  }}
                />
              </svg>
            </div>

            <div>
              <p
                className="text-white text-xl font-bold mb-1"
                style={{ fontFamily: "var(--font-display)" }}
              >
                Virement initié !
              </p>
              <p className="text-text-secondary text-sm">
                <span className="font-numeric font-medium text-success">
                  {formatAmount(amount)}
                </span>{" "}
                en cours de virement
              </p>
            </div>

            <div className="bg-white/[0.04] rounded-xl p-3 w-full">
              <p className="text-text-secondary text-sm">
                💳 Disponible sur votre compte{" "}
                <span className="text-white font-medium">sous 2h</span>
              </p>
            </div>

            <Button
              variant="secondary"
              size="md"
              fullWidth
              onClick={handleClose}
            >
              Fermer
            </Button>
          </div>
        )}
      </Modal>
    </>
  );
};
