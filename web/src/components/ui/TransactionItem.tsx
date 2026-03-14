/**
 * TransactionItem — Per-spec transaction display.
 * Revenues: DM Mono, success color, "+" prefix.
 * Expenses: DM Mono, text-primary, "−" prefix.
 * Future transactions: reduced opacity + italic.
 * Recurring: RÉCURRENT badge in cyan-dim.
 */
import React from "react";
import { RefreshCw } from "lucide-react";
import { format, parseISO, isPast } from "date-fns";
import { fr } from "date-fns/locale";

interface TransactionItemProps {
  id: string;
  label: string;
  amount: number;
  date: string; // ISO date string
  bankInitials?: string;
  isRecurring?: boolean;
  isPending?: boolean; // future / unconfirmed
  category?: string;
  onClick?: () => void;
}

const fmtAmount = (n: number) =>
  new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency: "EUR",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Math.abs(n));

export const TransactionItem: React.FC<TransactionItemProps> = ({
  label,
  amount,
  date,
  bankInitials = "?",
  isRecurring = false,
  isPending = false,
  category,
  onClick,
}) => {
  const isFuture = !isPast(parseISO(date));
  const isIncome = amount > 0;

  const dateLabel = isFuture
    ? `Prévu ${format(parseISO(date), "d MMMM", { locale: fr })}`
    : format(parseISO(date), "d MMM 'à' HH:mm", { locale: fr });

  return (
    <div
      onClick={onClick}
      className={`
        flex items-center gap-4 px-4 py-3.5 rounded-xl border border-white/[0.04]
        transition-all duration-150
        ${isFuture ? "opacity-70" : "opacity-100"}
        ${onClick ? "cursor-pointer hover:bg-white/[0.04] hover:border-white/[0.08]" : ""}
      `}
    >
      {/* Bank avatar */}
      <div className="w-10 h-10 rounded-xl bg-surface-elevated flex items-center justify-center flex-shrink-0 border border-white/10">
        <span
          className="text-xs font-bold text-text-secondary"
          style={{ fontFamily: "var(--font-display)" }}
        >
          {bankInitials.slice(0, 2).toUpperCase()}
        </span>
      </div>

      {/* Label + meta */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-white text-sm font-medium truncate">
            {label}
          </span>
          {isRecurring && (
            <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-primary-dim text-primary text-[10px] font-semibold uppercase tracking-wide flex-shrink-0">
              <RefreshCw size={8} />
              Récurrent
            </span>
          )}
        </div>
        <p className="text-text-muted text-xs mt-0.5">
          {dateLabel}
          {category && <span className="ml-2 opacity-70">· {category}</span>}
        </p>
      </div>

      {/* Amount */}
      <span
        className={`
          font-numeric text-sm font-medium flex-shrink-0
          ${isIncome ? "text-success" : "text-white"}
          ${isFuture ? "italic" : ""}
        `}
        aria-label={`${isIncome ? "Crédit" : "Débit"} : ${fmtAmount(amount)}`}
      >
        {isIncome ? "+" : "−"}
        {fmtAmount(amount)}
      </span>
    </div>
  );
};

export const TransactionItemSkeleton: React.FC = () => (
  <div className="flex items-center gap-4 px-4 py-3.5">
    <div className="skeleton w-10 h-10 rounded-xl flex-shrink-0" />
    <div className="flex-1 space-y-2">
      <div className="skeleton h-3 w-40" />
      <div className="skeleton h-2.5 w-24" />
    </div>
    <div className="skeleton h-4 w-20 flex-shrink-0" />
  </div>
);
