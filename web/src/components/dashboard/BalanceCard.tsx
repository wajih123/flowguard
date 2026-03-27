import React from "react";
import {
  RefreshCw,
  Building2,
  AlertTriangle,
  TrendingDown,
  TrendingUp,
} from "lucide-react";
import { Card } from "@/components/ui/Card";
import { AmountDisplay } from "@/components/ui/AmountDisplay";
import { SkeletonCard } from "@/components/ui/SkeletonCard";
import type { DashboardData } from "@/types";
import { formatDateShort } from "@/utils/format";

interface BalanceCardProps {
  dashboard?: DashboardData;
  isLoading: boolean;
  onRefresh?: () => void;
}

export const BalanceCard: React.FC<BalanceCardProps> = ({
  dashboard,
  isLoading,
  onRefresh,
}) => {
  if (isLoading) return <SkeletonCard lines={4} />;

  if (!dashboard || !dashboard.account) {
    return (
      <Card
        variant="flat"
        className="border-dashed border-white/10 text-center py-8"
      >
        <Building2 size={32} className="text-text-muted mx-auto mb-2" />
        <p className="text-text-secondary text-sm">Aucun compte connecté</p>
        <p className="text-text-muted text-xs mt-1">
          Connectez une banque pour commencer
        </p>
      </Card>
    );
  }

  const {
    currentBalance,
    predictedBalance30d,
    balanceTrend,
    account,
    accountCount,
  } = dashboard;

  const isMultiAccount = accountCount !== undefined && accountCount > 1;

  const balanceState =
    currentBalance <= 200
      ? "danger"
      : currentBalance <= 500
        ? "warning"
        : "safe";

  const trendIsPositive = balanceTrend >= 0;

  return (
    <Card danger={balanceState === "danger"} padding="md">
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center">
            <Building2 size={16} className="text-primary" />
          </div>
          <div>
            <p className="text-white text-xs font-medium leading-tight">
              {isMultiAccount ? "Tous comptes actifs" : account.bankName}
            </p>
            <p className="text-text-muted text-2xs font-numeric">
              {isMultiAccount
                ? `${accountCount} comptes connectés`
                : account.ibanMasked}
            </p>
          </div>
        </div>
        {onRefresh && (
          <button
            onClick={onRefresh}
            className="p-1.5 rounded-lg text-text-muted hover:text-white hover:bg-white/[0.06] transition-colors"
            aria-label="Rafraîchir"
          >
            <RefreshCw size={14} />
          </button>
        )}
      </div>

      {/* Balance */}
      <p className="text-text-secondary text-xs uppercase tracking-widest mb-1">
        Solde actuel
      </p>
      <AmountDisplay
        amount={currentBalance}
        size="xl"
        colorOverride={
          balanceState === "danger"
            ? "danger"
            : balanceState === "warning"
              ? "warning"
              : "white"
        }
      />

      {/* Trend */}
      <div className="flex items-center gap-1.5 mt-2">
        {trendIsPositive ? (
          <TrendingUp size={14} className="text-success" />
        ) : (
          <TrendingDown size={14} className="text-danger" />
        )}
        <span className="text-xs text-text-secondary">30 jours :</span>
        <AmountDisplay
          amount={balanceTrend}
          size="sm"
          showSign
          colorOverride={trendIsPositive ? "success" : "danger"}
        />
      </div>

      {/* Predicted balance */}
      <div className="mt-3 pt-3 border-t border-white/[0.06] flex items-center justify-between">
        <span className="text-text-muted text-xs">Solde dans 30 jours</span>
        <AmountDisplay
          amount={predictedBalance30d}
          size="sm"
          colorOverride={predictedBalance30d < 0 ? "danger" : "muted"}
        />
      </div>

      {/* Alert */}
      {dashboard.hasHighAlert && dashboard.highAlertDate && (
        <div className="mt-2 flex items-start gap-2 bg-danger/[0.08] border border-danger/20 rounded-xl px-3 py-2">
          <AlertTriangle
            size={14}
            className="text-danger flex-shrink-0 mt-0.5"
          />
          <p className="text-danger text-xs leading-snug">
            Déficit prévu le {formatDateShort(dashboard.highAlertDate)}
          </p>
        </div>
      )}
    </Card>
  );
};
