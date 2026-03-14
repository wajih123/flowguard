/**
 * BalanceDisplay — The most important component in FlowGuard.
 * Shows current account balance with state-driven styling:
 *   - "safe"    : balance > 500€
 *   - "warning" : 200€ < balance ≤ 500€
 *   - "danger"  : balance ≤ 200€ or deficit predicted
 */
import React, { useEffect, useRef } from "react";
import { RefreshCw, MoreHorizontal, AlertTriangle } from "lucide-react";

export type BalanceState = "safe" | "warning" | "danger" | "loading";

interface BalanceDisplayProps {
  bankName?: string;
  maskedIban?: string;
  balance: number;
  monthlyChange?: number;
  lastSync?: Date;
  state?: BalanceState;
  deficitLabel?: string;
  onRefresh?: () => void;
  onMenu?: () => void;
  ariaLabel?: string;
}

/** Format as French euro: "3 240,00 €" */
const fmtEuro = (n: number, opts?: { signed?: boolean }) => {
  const formatted = new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency: "EUR",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(Math.abs(n));
  if (opts?.signed) {
    return n >= 0 ? `+${formatted}` : `-${formatted}`;
  }
  return n < 0 ? `-${formatted}` : formatted;
};

const relativeSync = (d: Date) => {
  const diff = Math.floor((Date.now() - d.getTime()) / 1000);
  if (diff < 60) return "à l'instant";
  if (diff < 3600) return `il y a ${Math.floor(diff / 60)} min`;
  if (diff < 86400) return `il y a ${Math.floor(diff / 3600)} h`;
  return `il y a ${Math.floor(diff / 86400)} j`;
};

/** Animated counter: runs from 0 to `target` over `duration` ms */
const useCountUp = (target: number, duration = 800) => {
  const ref = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const start = performance.now();
    const startVal = 0;
    const step = (now: number) => {
      const t = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - t, 3); // ease-out cubic
      el.textContent = fmtEuro(startVal + eased * target);
      if (t < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }, [target, duration]);
  return ref;
};

export const BalanceDisplay: React.FC<BalanceDisplayProps> = ({
  bankName,
  maskedIban,
  balance,
  monthlyChange,
  lastSync,
  state = "safe",
  deficitLabel,
  onRefresh,
  onMenu,
  ariaLabel,
}) => {
  const balanceRef = useCountUp(balance);

  const isStale = lastSync
    ? Date.now() - lastSync.getTime() > 3 * 3600 * 1000
    : false;

  const cardBorder =
    state === "danger"
      ? "border-danger/35 shadow-glow-danger"
      : state === "warning"
        ? "border-warning/25"
        : "border-white/[0.08]";

  const dangerOverlay =
    state === "danger"
      ? "before:absolute before:inset-0 before:rounded-2xl before:bg-danger/[0.04] before:pointer-events-none"
      : "";

  const balanceColor =
    state === "danger"
      ? "text-danger"
      : state === "warning"
        ? "text-warning"
        : "text-white";

  return (
    <div
      className={`relative bg-gradient-card rounded-2xl border p-6 ${cardBorder} ${dangerOverlay} transition-all duration-300`}
      aria-label={ariaLabel ?? `Solde actuel : ${fmtEuro(balance)}`}
    >
      {/* Header */}
      <div className="flex items-start justify-between mb-5">
        <div>
          <p
            className="text-white font-semibold text-sm"
            style={{ fontFamily: "var(--font-display)" }}
          >
            {bankName}
          </p>
          {maskedIban && (
            <p className="text-text-muted text-xs mt-0.5 font-numeric tracking-wider">
              {maskedIban}
            </p>
          )}
        </div>
        <div className="flex items-center gap-1">
          {state === "danger" && (
            <span className="flex items-center gap-1 text-danger text-xs font-medium animate-pulse-dot mr-1">
              <AlertTriangle size={13} />
              <span>ATTENTION</span>
            </span>
          )}
          {onRefresh && (
            <button
              onClick={onRefresh}
              className={`p-2 rounded-xl hover:bg-white/10 transition-colors ${isStale ? "text-warning" : "text-text-muted hover:text-white"}`}
              aria-label="Actualiser le solde"
            >
              <RefreshCw size={14} />
            </button>
          )}
          {onMenu && (
            <button
              onClick={onMenu}
              className="p-2 rounded-xl hover:bg-white/10 transition-colors text-text-muted hover:text-white"
              aria-label="Options du compte"
            >
              <MoreHorizontal size={14} />
            </button>
          )}
        </div>
      </div>

      {/* Balance */}
      <div className="mb-4">
        <p className="text-text-secondary text-xs uppercase tracking-widest font-medium mb-2">
          Solde actuel
        </p>
        <span
          ref={balanceRef}
          className={`font-numeric text-4xl font-medium leading-none ${balanceColor}`}
          aria-label={`Solde : ${fmtEuro(balance)}`}
        >
          {fmtEuro(balance)}
        </span>
      </div>

      {/* Metadata row */}
      <div className="flex items-center justify-between">
        <div>
          {monthlyChange !== undefined && (
            <span
              className={`text-sm font-medium flex items-center gap-1 ${monthlyChange >= 0 ? "text-success" : "text-danger"}`}
            >
              {monthlyChange >= 0 ? "↗" : "↘"}
              <span className="font-numeric">
                {fmtEuro(monthlyChange, { signed: true })} ce mois
              </span>
            </span>
          )}
          {deficitLabel && state !== "safe" && (
            <p className="text-text-secondary text-xs mt-1">{deficitLabel}</p>
          )}
        </div>
        {lastSync && (
          <p className="text-text-muted text-xs">
            {isStale ? (
              <span className="text-warning">
                Mise à jour : {relativeSync(lastSync)}
              </span>
            ) : (
              <>Dernière synchro : {relativeSync(lastSync)}</>
            )}
          </p>
        )}
      </div>
    </div>
  );
};
