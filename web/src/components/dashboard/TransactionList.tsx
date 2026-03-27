import React from "react";
import { Link } from "react-router-dom";
import {
  Home,
  TrendingUp,
  Smartphone,
  ShoppingCart,
  Cloud,
  Repeat,
  ArrowRightLeft,
} from "lucide-react";
import { Card } from "@/components/ui/Card";
import { AmountDisplay } from "@/components/ui/AmountDisplay";
import { SkeletonRow } from "@/components/ui/SkeletonCard";
import type { Transaction } from "@/types";
import { formatDateShort } from "@/utils/format";

interface TransactionListProps {
  transactions?: Transaction[];
  isLoading: boolean;
}

const categoryIcons: Record<string, React.ElementType> = {
  LOGEMENT: Home,
  REVENU: TrendingUp,
  TELECOM: Smartphone,
  ALIMENTATION: ShoppingCart,
  TECH: Cloud,
};

const categoryColors: Record<string, string> = {
  LOGEMENT: "bg-primary/10 text-primary",
  REVENU: "bg-success/10 text-success",
  TELECOM: "bg-purple/10 text-purple",
  ALIMENTATION: "bg-warning/10 text-warning",
  TECH: "bg-info/10 text-info",
};

export const TransactionList: React.FC<TransactionListProps> = ({
  transactions,
  isLoading,
}) => {
  return (
    <Card padding="none" className="overflow-hidden">
      <div className="px-5 pt-5 pb-3 flex items-center justify-between">
        <p
          className="text-white font-semibold text-sm"
          style={{ fontFamily: "var(--font-display)" }}
        >
          Dernières transactions
        </p>
      </div>

      <div className="divide-y divide-white/[0.04]">
        {isLoading ? (
          <>
            <SkeletonRow />
            <SkeletonRow />
            <SkeletonRow />
            <SkeletonRow />
            <SkeletonRow />
          </>
        ) : !transactions?.length ? (
          <div className="py-10 text-center">
            <ArrowRightLeft
              size={28}
              className="text-text-muted mx-auto mb-2"
            />
            <p className="text-text-secondary text-sm">
              Aucune transaction récente
            </p>
          </div>
        ) : (
          transactions.map((tx) => {
            const Icon = categoryIcons[tx.category] ?? ArrowRightLeft;
            const iconClass =
              categoryColors[tx.category] ??
              "bg-white/[0.06] text-text-secondary";

            return (
              <div
                key={tx.id}
                className="flex items-center gap-3 px-5 py-3.5 hover:bg-white/[0.03] transition-colors"
              >
                {/* Icon */}
                <div
                  className={`w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ${iconClass}`}
                >
                  <Icon size={16} />
                </div>

                {/* Label + date */}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-1.5 flex-wrap">
                    <p className="text-white text-sm font-medium truncate">
                      {tx.label}
                    </p>
                    {tx.isRecurring && (
                      <span className="flex items-center gap-0.5 text-2xs text-primary bg-primary/10 border border-primary/20 rounded-full px-1.5 py-0.5 flex-shrink-0">
                        <Repeat size={9} />
                        récurrent
                      </span>
                    )}
                  </div>
                  <p className="text-text-muted text-xs mt-0.5">
                    {formatDateShort(tx.transactionDate)}
                  </p>
                </div>

                {/* Amount */}
                <AmountDisplay
                  amount={tx.amount}
                  size="sm"
                  showSign
                  className="flex-shrink-0"
                />
              </div>
            );
          })
        )}
      </div>

      {/* Footer link */}
      {!isLoading && (transactions?.length ?? 0) > 0 && (
        <div className="px-5 py-3 border-t border-white/[0.04]">
          <Link
            to="/transactions"
            className="text-primary text-xs font-medium hover:text-white transition-colors"
          >
            Voir toutes les transactions →
          </Link>
        </div>
      )}
    </Card>
  );
};
