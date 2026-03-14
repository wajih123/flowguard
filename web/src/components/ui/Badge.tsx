import React from "react";

type BadgeVariant =
  | "default"
  | "primary"
  | "success"
  | "warning"
  | "danger"
  | "purple"
  | "muted";

const variantClasses: Record<BadgeVariant, string> = {
  default: "bg-white/10 text-white",
  primary: "bg-primary/15 text-primary",
  success: "bg-success/15 text-success",
  warning: "bg-warning/15 text-warning",
  danger: "bg-danger/15 text-danger",
  purple: "bg-purple/15 text-purple",
  muted: "bg-white/[0.06] text-text-secondary",
};

interface BadgeProps {
  variant?: BadgeVariant;
  children: React.ReactNode;
  className?: string;
  dot?: boolean;
}

export const Badge: React.FC<BadgeProps> = ({
  variant = "default",
  children,
  className = "",
  dot = false,
}) => (
  <span
    className={`
      inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium
      ${variantClasses[variant]} ${className}
    `}
  >
    {dot && <span className="w-1.5 h-1.5 rounded-full bg-current" />}
    {children}
  </span>
);

// Helper maps for domain enums
export const severityBadge = (severity: string): BadgeVariant => {
  const map: Record<string, BadgeVariant> = {
    LOW: "primary",
    MEDIUM: "warning",
    HIGH: "danger",
    CRITICAL: "danger",
  };
  return map[severity] ?? "muted";
};

export const creditStatusBadge = (status: string): BadgeVariant => {
  const map: Record<string, BadgeVariant> = {
    PENDING: "warning",
    APPROVED: "primary",
    DISBURSED: "success",
    REPAID: "muted",
    OVERDUE: "danger",
    REJECTED: "danger",
    RETRACTED: "muted",
  };
  return map[status] ?? "muted";
};

export const kycStatusBadge = (status: string): BadgeVariant => {
  const map: Record<string, BadgeVariant> = {
    PENDING: "warning",
    IN_PROGRESS: "primary",
    APPROVED: "success",
    REJECTED: "danger",
  };
  return map[status] ?? "muted";
};
