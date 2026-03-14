import React from "react";

type Severity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

const config: Record<Severity, { label: string; classes: string }> = {
  LOW: { label: "Faible", classes: "bg-success/15 text-success" },
  MEDIUM: { label: "Moyen", classes: "bg-warning/15 text-warning" },
  HIGH: { label: "Élevé", classes: "bg-orange-500/15 text-orange-400" },
  CRITICAL: { label: "Critique", classes: "bg-danger/15 text-danger" },
};

const SEVERITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"] as const;
const isSeverity = (s: string): s is Severity =>
  (SEVERITIES as readonly string[]).includes(s);

interface SeverityBadgeProps {
  severity: string;
  className?: string;
}

export const SeverityBadge: React.FC<SeverityBadgeProps> = ({
  severity,
  className = "",
}) => {
  const cfg = isSeverity(severity)
    ? config[severity]
    : { label: severity, classes: "bg-white/10 text-white" };

  const base =
    "inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium";
  return (
    <span className={[base, cfg.classes, className].join(" ")}>
      {cfg.label}
    </span>
  );
};
