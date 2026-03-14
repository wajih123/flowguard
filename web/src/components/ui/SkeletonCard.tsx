import React from "react";

interface SkeletonCardProps {
  lines?: number;
  height?: string;
  className?: string;
}

/** Skeleton pulse card for loading states */
export const SkeletonCard: React.FC<SkeletonCardProps> = ({
  lines = 3,
  height,
  className = "",
}) => (
  <div
    className={`rounded-2xl bg-surface border border-white/[0.08] p-6 animate-pulse ${className}`}
  >
    {height ? (
      <div className="rounded-xl bg-white/[0.06]" style={{ height }} />
    ) : (
      <div className="space-y-3">
        {Array.from({ length: lines }).map((_, i) => (
          <div
            key={i}
            className="h-4 rounded-lg bg-white/[0.06]"
            style={{
              width: i === 0 ? "60%" : i === lines - 1 ? "40%" : "100%",
            }}
          />
        ))}
      </div>
    )}
  </div>
);

/** Single skeleton row for lists */
export const SkeletonRow: React.FC<{ className?: string }> = ({
  className = "",
}) => (
  <div className={`flex items-center gap-3 p-4 animate-pulse ${className}`}>
    <div className="w-10 h-10 rounded-xl bg-white/[0.06] flex-shrink-0" />
    <div className="flex-1 space-y-2">
      <div className="h-3 bg-white/[0.06] rounded-lg w-3/4" />
      <div className="h-3 bg-white/[0.06] rounded-lg w-1/2" />
    </div>
    <div className="h-4 bg-white/[0.06] rounded-lg w-20" />
  </div>
);
