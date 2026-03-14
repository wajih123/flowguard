/**
 * ConfidenceBadge — Always shown with predictions.
 * Never show a prediction without a confidence level.
 * NEVER display "MAE < 150€" — always "Précis à ±150€ en moyenne"
 */
import React, { useState } from "react";

export type ConfidenceLevel = "HIGH" | "MEDIUM" | "LOW" | "INSUFFICIENT";

interface ConfidenceBadgeProps {
  level: ConfidenceLevel;
  historyDays?: number;
  className?: string;
}

const config: Record<
  ConfidenceLevel,
  {
    label: string;
    range: string;
    dot: string;
    bg: string;
    text: string;
    tooltip: (days?: number) => string;
  }
> = {
  HIGH: {
    label: "Fiable",
    range: "±150€",
    dot: "●",
    bg: "bg-success/10 border-success/20",
    text: "text-success",
    tooltip: (days) =>
      `Précis à ±150€ en moyenne.${days ? ` Basé sur ${days} jours de données.` : ""}`,
  },
  MEDIUM: {
    label: "Indicatif",
    range: "±300€",
    dot: "◐",
    bg: "bg-warning/10 border-warning/20",
    text: "text-warning",
    tooltip: (days) =>
      `Estimatif à ±300€.${days ? ` Basé sur ${days} jours de données. Plus vous utilisez FlowGuard, plus la précision augmente.` : ""}`,
  },
  LOW: {
    label: "Estimation",
    range: "±500€",
    dot: "○",
    bg: "bg-white/[0.06] border-white/10",
    text: "text-text-secondary",
    tooltip: (days) =>
      `Estimation indicative à ±500€.${days ? ` Seulement ${days} jours de données disponibles.` : " Connectez votre banque pour affiner."}`,
  },
  INSUFFICIENT: {
    label: "Insuffisant",
    range: "",
    dot: "○",
    bg: "bg-white/[0.04] border-white/[0.08]",
    text: "text-text-muted",
    tooltip: () =>
      "Données insuffisantes pour une prédiction fiable. Continuez à utiliser FlowGuard pour améliorer la précision.",
  },
};

export const ConfidenceBadge: React.FC<ConfidenceBadgeProps> = ({
  level,
  historyDays,
  className = "",
}) => {
  const [open, setOpen] = useState(false);
  const cfg = config[level];

  return (
    <div className="relative inline-flex">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        onBlur={() => setOpen(false)}
        className={`
          inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border text-xs font-medium
          transition-all duration-150 cursor-pointer hover:opacity-80
          ${cfg.bg} ${cfg.text} ${className}
        `}
        aria-label={`Niveau de confiance : ${cfg.label}${cfg.range ? ` ${cfg.range}` : ""}`}
      >
        <span>{cfg.dot}</span>
        <span>{cfg.label}</span>
        {cfg.range && (
          <span className="font-numeric opacity-70">{cfg.range}</span>
        )}
      </button>

      {open && (
        <div
          className="absolute bottom-full left-0 mb-2 w-64 bg-surface-elevated border border-white/10 rounded-xl p-3 text-xs text-text-secondary shadow-modal z-50 animate-fade-in"
          role="tooltip"
        >
          {cfg.tooltip(historyDays)}
        </div>
      )}
    </div>
  );
};
