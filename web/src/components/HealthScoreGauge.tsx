import React, { useEffect, useRef, useState } from "react";

interface HealthScoreGaugeProps {
  score: number; // 0-100
  size?: number;
  strokeWidth?: number;
  className?: string;
}

const getColor = (score: number): string => {
  if (score >= 80) return "#10b981"; // success
  if (score >= 60) return "#06B6D4"; // primary
  if (score >= 40) return "#f59e0b"; // warning
  return "#ef4444"; // danger
};

const getGradientId = (score: number): string => {
  if (score >= 80) return "gaugeGradSuccess";
  if (score >= 60) return "gaugeGradPrimary";
  if (score >= 40) return "gaugeGradWarning";
  return "gaugeGradDanger";
};

const getLabel = (score: number): string => {
  if (score >= 80) return "Bonne santé";
  if (score >= 60) return "Stable";
  if (score >= 40) return "Vigilance";
  return "Critique";
};

export const HealthScoreGauge: React.FC<HealthScoreGaugeProps> = ({
  score,
  size = 200,
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
      const eased = 1 - Math.pow(1 - t, 3);
      setAnimated(Math.round(eased * target));
      if (t < 1) rafRef.current = requestAnimationFrame(step);
    };
    rafRef.current = requestAnimationFrame(step);
    return () => {
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, [score]);

  const clamped = Math.max(0, Math.min(100, animated));
  const radius = (size - strokeWidth) / 2;
  const center = size / 2;
  const startAngle = 135;
  const maxAngle = 270;
  const angle = (clamped / 100) * maxAngle;

  const polarToCartesian = (cx: number, cy: number, r: number, deg: number) => {
    const rad = ((deg - 90) * Math.PI) / 180;
    return { x: cx + r * Math.cos(rad), y: cy + r * Math.sin(rad) };
  };

  const describeArc = (
    cx: number,
    cy: number,
    r: number,
    startDeg: number,
    endDeg: number,
  ) => {
    const start = polarToCartesian(cx, cy, r, endDeg);
    const end = polarToCartesian(cx, cy, r, startDeg);
    const largeArc = endDeg - startDeg > 180 ? 1 : 0;
    return `M ${start.x} ${start.y} A ${r} ${r} 0 ${largeArc} 0 ${end.x} ${end.y}`;
  };

  const trackPath = describeArc(
    center,
    center,
    radius,
    startAngle,
    startAngle + maxAngle,
  );
  const fillPath =
    angle > 0
      ? describeArc(center, center, radius, startAngle, startAngle + angle)
      : "";
  const color = getColor(clamped);
  const gradId = getGradientId(clamped);
  const label = getLabel(clamped);

  // The SVG viewBox height is trimmed: the bottom 25% of the circle is clipped
  const svgHeight = size * 0.82;

  return (
    <div className={`flex flex-col items-center ${className}`}>
      <svg
        width={size}
        height={svgHeight}
        viewBox={`0 0 ${size} ${svgHeight}`}
        aria-label={`Score de santé financière : ${clamped} sur 100`}
        role="img"
      >
        <defs>
          <linearGradient
            id="gaugeGradSuccess"
            x1="0%"
            y1="0%"
            x2="100%"
            y2="0%"
          >
            <stop offset="0%" stopColor="#10B981" />
            <stop offset="100%" stopColor="#06B6D4" />
          </linearGradient>
          <linearGradient
            id="gaugeGradPrimary"
            x1="0%"
            y1="0%"
            x2="100%"
            y2="0%"
          >
            <stop offset="0%" stopColor="#06B6D4" />
            <stop offset="100%" stopColor="#3B82F6" />
          </linearGradient>
          <linearGradient
            id="gaugeGradWarning"
            x1="0%"
            y1="0%"
            x2="100%"
            y2="0%"
          >
            <stop offset="0%" stopColor="#F59E0B" />
            <stop offset="100%" stopColor="#F97316" />
          </linearGradient>
          <linearGradient
            id="gaugeGradDanger"
            x1="0%"
            y1="0%"
            x2="100%"
            y2="0%"
          >
            <stop offset="0%" stopColor="#EF4444" />
            <stop offset="100%" stopColor="#F97316" />
          </linearGradient>
        </defs>

        {/* Track */}
        <path
          d={trackPath}
          fill="none"
          stroke="rgba(255,255,255,0.07)"
          strokeWidth={strokeWidth}
          strokeLinecap="round"
        />
        {/* Fill */}
        {fillPath && (
          <path
            d={fillPath}
            fill="none"
            stroke={`url(#${gradId})`}
            strokeWidth={strokeWidth}
            strokeLinecap="round"
          />
        )}
        {/* Score number */}
        <text
          x={center}
          y={center + 2}
          textAnchor="middle"
          dominantBaseline="middle"
          fill={color}
          fontSize={size * 0.22}
          fontFamily="DM Mono, monospace"
          fontWeight="500"
        >
          {clamped}
        </text>
      </svg>
      <p
        className="text-text-secondary text-sm -mt-2"
        style={{ fontFamily: "var(--font-display)" }}
      >
        {label}
      </p>
    </div>
  );
};

// (old export removed — see above for the current HealthScoreGauge)
