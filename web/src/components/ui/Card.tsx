import React from "react";

interface CardProps {
  children: React.ReactNode;
  className?: string;
  onClick?: () => void;
  hover?: boolean;
  padding?: "none" | "sm" | "md" | "lg";
  /** 'gradient' uses gradient-card, 'flat' is solid surface, 'elevated' is higher elevation */
  variant?: "gradient" | "flat" | "elevated";
  /** danger ring — used for negative balance states */
  danger?: boolean;
}

const paddings = { none: "", sm: "p-4", md: "p-6", lg: "p-8" };

const variants = {
  gradient: "bg-gradient-card",
  flat: "bg-surface",
  elevated: "bg-surface-elevated",
};

export const Card: React.FC<CardProps> = ({
  children,
  className = "",
  onClick,
  hover = false,
  padding = "md",
  variant = "gradient",
  danger = false,
}) => (
  <div
    onClick={onClick}
    className={`
      rounded-2xl border transition-all duration-200
      ${variants[variant]}
      ${danger ? "border-danger/30 shadow-glow-danger" : "border-white/[0.08]"}
      ${hover ? "cursor-pointer hover:border-primary/25 hover:bg-surface-hover hover:shadow-glow" : ""}
      ${paddings[padding]}
      ${className}
    `}
  >
    {children}
  </div>
);

interface CardHeaderProps {
  title: string;
  subtitle?: string;
  action?: React.ReactNode;
  icon?: React.ReactNode;
  helpTooltip?: React.ReactNode;
}

export const CardHeader: React.FC<CardHeaderProps> = ({
  title,
  subtitle,
  action,
  icon,
  helpTooltip,
}) => (
  <div className="flex items-start justify-between mb-4">
    <div className="flex items-center gap-3">
      {icon && (
        <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center text-primary flex-shrink-0">
          {icon}
        </div>
      )}
      <div>
        <h3
          className="font-semibold text-white flex items-center gap-1.5"
          style={{ fontFamily: "var(--font-display)" }}
        >
          {title}
          {helpTooltip}
        </h3>
        {subtitle && (
          <p className="text-text-secondary text-sm mt-0.5">{subtitle}</p>
        )}
      </div>
    </div>
    {action}
  </div>
);

/** Skeleton placeholder — matches exact content shape */
export const CardSkeleton: React.FC<{ className?: string; lines?: number }> = ({
  className = "",
  lines = 2,
}) => (
  <div
    className={`rounded-2xl border border-white/[0.08] bg-gradient-card p-6 ${className}`}
  >
    <div className="skeleton h-3 w-24 mb-3" />
    <div className="skeleton h-8 w-36 mb-2" />
    {Array.from({ length: lines - 1 }).map((_, i) => (
      <div key={i} className="skeleton h-3 w-20 mt-2" />
    ))}
  </div>
);
