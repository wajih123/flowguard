import React, { useEffect, useRef, useState } from "react";

interface HealthGaugeProps {
  score: number; // 0-100
  size?: number;
  strokeWidth?: number;
  className?: string;
}

const getArcColor = (score: number): string => {
  if (score >= 70) return "#10B981"; // success
  if (score >= 40) return "#F59E0B"; // warning
  return "#EF4444"; // danger
};

const getLabel = (score: number): string => {
  if (score >= 80) return "Excellente";
  if (score >= 60) return "Bonne santé";
  if (score >= 40) return "Vigilance";
  return "Critique";
};

/**
 * Half-circle (180°) SVG gauge for health score.
 * Animates from 0 → score in 800ms ease-out.
 */
export const HealthGauge: React.FC<HealthGaugeProps> = ({
  score,
  size = 160,
  strokeWidth = 12,
  className = "",
}) => {
  const [animated, setAnimated] = useState(0);
  const rafRef = useRef<number>();

  useEffect(() => {
    const target = Math.max(0, Math.min(100, score));
    const start = performance.now();
    const duration = 800;
    const step = (now: number) => {
      const t = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - t, 3); // ease-out cubic
      setAnimated(Math.round(eased * target));
      if (t < 1) rafRef.current = requestAnimationFrame(step);
    };
    rafRef.current = requestAnimationFrame(step);
    return () => {
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, [score]);

  const clamped = Math.max(0, Math.min(100, animated));
  const r = (size - strokeWidth) / 2;
  const cx = size / 2;
  // Half circle: center y is at bottom of the semicircle
  const cy = size * 0.55;

  // Arc from left (180°) to right (0°) across top
  // Full arc = 180°; filled = (clamped/100) * 180°
  const startX = cx - r;
  const startY = cy;
  const endX = cx + r;
  const endY = cy;

  // Active arc endpoint (going counter-clockwise from right side)
  // We draw from left to right, progress = clamped/100
  const angleRad = (clamped / 100) * Math.PI; // 0 to π
  const activeX = cx - r * Math.cos(angleRad);
  const activeY = cy - r * Math.sin(angleRad);
  const largeArc = clamped > 50 ? 1 : 0;

  const color = getArcColor(score);
  const label = getLabel(score);

  return (
    <div className={`flex flex-col items-center ${className}`}>
      <svg
        width={size}
        height={size * 0.6}
        viewBox={`0 0 ${size} ${size * 0.6}`}
        aria-label={`Score de santé : ${score} sur 100`}
      >
        {/* Background track */}
        <path
          d={`M ${startX} ${cy} A ${r} ${r} 0 0 1 ${endX} ${cy}`}
          fill="none"
          stroke="rgba(255,255,255,0.08)"
          strokeWidth={strokeWidth}
          strokeLinecap="round"
        />
        {/* Active arc */}
        {clamped > 0 && (
          <path
            d={`M ${startX} ${cy} A ${r} ${r} 0 ${largeArc} 1 ${activeX} ${activeY}`}
            fill="none"
            stroke={color}
            strokeWidth={strokeWidth}
            strokeLinecap="round"
            style={{ filter: `drop-shadow(0 0 6px ${color}60)` }}
          />
        )}
      </svg>

      {/* Score number */}
      <p
        className="text-4xl font-bold text-white leading-none -mt-2"
        style={{ fontFamily: "var(--font-display)", color }}
      >
        {clamped}
      </p>
      <p className="text-text-secondary text-xs mt-1 text-center">{label}</p>
    </div>
  );
};
