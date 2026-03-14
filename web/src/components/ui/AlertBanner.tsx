import React from "react";
import { Zap, X } from "lucide-react";
import { Button } from "./Button";

export type AlertSeverityLevel = "HIGH" | "MEDIUM" | "LOW" | "INFO";

interface AlertBannerProps {
  severity?: AlertSeverityLevel;
  title: string;
  message: string;
  /** CTA button label — if provided, shows a gradient activation button */
  ctaLabel?: string;
  onCta?: () => void;
  onDismiss?: () => void;
  /** Secondary dismiss label (default: "Plus tard") */
  dismissLabel?: string;
  className?: string;
}

const severityConfig: Record<
  AlertSeverityLevel,
  { bg: string; border: string; icon: string }
> = {
  HIGH: {
    bg: "bg-gradient-to-r from-danger/12 to-orange-500/8",
    border:
      "border-l-4 border-l-danger border-t border-r border-b border-danger/20",
    icon: "🔴",
  },
  MEDIUM: {
    bg: "bg-warning/[0.08]",
    border:
      "border-l-4 border-l-warning border-t border-r border-b border-warning/15",
    icon: "🟡",
  },
  LOW: {
    bg: "bg-info/[0.08]",
    border:
      "border-l-4 border-l-info border-t border-r border-b border-info/15",
    icon: "🔵",
  },
  INFO: {
    bg: "bg-primary/[0.08]",
    border:
      "border-l-4 border-l-primary border-t border-r border-b border-primary/15",
    icon: "ℹ",
  },
};

export const AlertBanner: React.FC<AlertBannerProps> = ({
  severity = "INFO",
  title,
  message,
  ctaLabel,
  onCta,
  onDismiss,
  dismissLabel = "Plus tard",
  className = "",
}) => {
  const cfg = severityConfig[severity];
  return (
    <div
      role="alert"
      aria-live={severity === "HIGH" ? "assertive" : "polite"}
      className={`
        w-full rounded-2xl ${cfg.bg} ${cfg.border}
        p-5 animate-slide-down
        ${className}
      `}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-3 flex-1 min-w-0">
          <span
            className="text-lg leading-none mt-0.5 flex-shrink-0"
            aria-hidden
          >
            {cfg.icon}
          </span>
          <div className="flex-1 min-w-0">
            <p
              className="text-white font-semibold text-sm"
              style={{ fontFamily: "var(--font-display)" }}
            >
              {title}
            </p>
            <p className="text-text-secondary text-sm mt-1 leading-relaxed">
              {message}
            </p>
          </div>
        </div>
        {onDismiss && (
          <button
            onClick={onDismiss}
            className="text-text-muted hover:text-white transition-colors flex-shrink-0 p-1 rounded-lg hover:bg-white/10"
            aria-label="Fermer"
          >
            <X size={16} />
          </button>
        )}
      </div>

      {(ctaLabel || onDismiss) && (
        <div className="flex items-center gap-3 mt-4">
          {ctaLabel && onCta && (
            <Button
              variant="gradient"
              size="md"
              leftIcon={<Zap size={15} />}
              onClick={onCta}
            >
              {ctaLabel}
            </Button>
          )}
          {onDismiss && (
            <button
              onClick={onDismiss}
              className="text-text-secondary hover:text-white text-sm transition-colors px-1"
            >
              ✕ {dismissLabel}
            </button>
          )}
        </div>
      )}
    </div>
  );
};

/** Legacy thin variant used by existing code */
export const AlertBannerThin: React.FC<{
  message: string;
  variant?: "error" | "warning" | "info" | "success";
  onClose?: () => void;
}> = ({ message, variant = "info", onClose }) => {
  const map = {
    error: "bg-danger/10 border-danger/25 text-danger",
    warning: "bg-warning/10 border-warning/25 text-warning",
    info: "bg-primary/10 border-primary/25 text-primary",
    success: "bg-success/10 border-success/25 text-success",
  };
  return (
    <div
      className={`flex items-center gap-3 px-4 py-3 rounded-xl border text-sm ${map[variant]}`}
    >
      <span className="flex-1">{message}</span>
      {onClose && (
        <button
          onClick={onClose}
          className="opacity-60 hover:opacity-100 text-lg leading-none"
        >
          ×
        </button>
      )}
    </div>
  );
};
