import React from "react";
import { Card } from "@/components/ui/Card";
import { HealthGauge } from "@/components/ui/HealthGauge";
import { SkeletonCard } from "@/components/ui/SkeletonCard";

interface HealthScoreCardProps {
  score?: number;
  label?: string;
  isLoading: boolean;
}

export const HealthScoreCard: React.FC<HealthScoreCardProps> = ({
  score,
  label,
  isLoading,
}) => {
  if (isLoading) return <SkeletonCard lines={3} />;

  return (
    <Card padding="md" className="flex flex-col">
      <p className="text-text-secondary text-xs uppercase tracking-widest font-medium mb-3">
        Santé financière
      </p>

      {score !== undefined ? (
        <div className="flex-1 flex items-center justify-center py-2">
          <HealthGauge score={score} size={160} />
        </div>
      ) : (
        <div className="flex-1 flex flex-col items-center justify-center py-6 gap-2">
          <div className="w-16 h-16 rounded-full bg-white/[0.06] animate-pulse" />
          <p className="text-text-secondary text-sm text-center">
            Connectez votre banque pour voir votre score
          </p>
        </div>
      )}
    </Card>
  );
};
