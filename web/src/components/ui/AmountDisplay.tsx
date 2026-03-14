import React from "react";
import { formatAmount } from "@/utils/format";

type AmountSize = "sm" | "md" | "lg" | "xl";

interface AmountDisplayProps {
  amount: number;
  currency?: string;
  size?: AmountSize;
  showSign?: boolean;
  /** Override color: default auto-colors positive/negative */
  colorOverride?: "white" | "success" | "danger" | "warning" | "muted";
  className?: string;
}

const sizeClasses: Record<AmountSize, string> = {
  sm: "text-sm",
  md: "text-base",
  lg: "text-xl",
  xl: "text-3xl font-bold",
};

const getColor = (
  amount: number,
  override?: AmountDisplayProps["colorOverride"],
) => {
  if (override === "white") return "text-white";
  if (override === "success") return "text-success";
  if (override === "danger") return "text-danger";
  if (override === "warning") return "text-warning";
  if (override === "muted") return "text-text-secondary";
  if (amount > 0) return "text-success";
  if (amount < 0) return "text-danger";
  return "text-white";
};

/**
 * Displays a financial amount in DM Mono with French formatting.
 * Always use this component for any monetary value.
 */
export const AmountDisplay: React.FC<AmountDisplayProps> = ({
  amount,
  currency = "EUR",
  size = "md",
  showSign = false,
  colorOverride,
  className = "",
}) => {
  const formatted = formatAmount(Math.abs(amount), currency);
  const sign = showSign && amount > 0 ? "+" : amount < 0 ? "-" : "";
  const display = sign
    ? `${sign}${formatted}`
    : amount < 0
      ? `-${formatted}`
      : formatted;

  return (
    <span
      className={`font-numeric tabular-nums ${sizeClasses[size]} ${getColor(amount, colorOverride)} ${className}`}
    >
      {display}
    </span>
  );
};
