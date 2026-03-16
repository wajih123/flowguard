import React, { useEffect, useRef } from 'react';
import { View, Text, Animated, StyleSheet } from 'react-native';
import Svg, { Path } from 'react-native-svg';
import { colors } from '../theme';

interface HealthScoreGaugeProps {
  score: number // 0–100
  size?: number
}

function getColor(score: number): string {
  if (score <= 40) {return colors.danger;}
  if (score <= 70) {return colors.warning;}
  return colors.success;
}

function getLabel(score: number): string {
  if (score <= 40) {return 'Critique';}
  if (score <= 70) {return 'Attention';}
  return 'Bonne santé';
}

// Draws an arc path on a circle
function describeArc(
  cx: number,
  cy: number,
  r: number,
  startAngle: number,
  endAngle: number,
): string {
  const start = polarToCartesian(cx, cy, r, endAngle);
  const end = polarToCartesian(cx, cy, r, startAngle);
  const largeArcFlag = endAngle - startAngle <= 180 ? '0' : '1';
  return `M ${start.x} ${start.y} A ${r} ${r} 0 ${largeArcFlag} 0 ${end.x} ${end.y}`;
}

function polarToCartesian(cx: number, cy: number, r: number, angle: number) {
  const rad = ((angle - 90) * Math.PI) / 180;
  return { x: cx + r * Math.cos(rad), y: cy + r * Math.sin(rad) };
}

export const HealthScoreGauge: React.FC<HealthScoreGaugeProps> = ({ score, size = 140 }) => {
  const animatedScore = useRef(new Animated.Value(0)).current;
  const color = getColor(score);
  const label = getLabel(score);
  const cx = size / 2;
  const cy = size / 2;
  const r = size / 2 - 12;
  const strokeWidth = 14;
  const startAngle = -140;
  const endAngle = 140;
  const totalAngle = endAngle - startAngle;
  const arcAngle = startAngle + (totalAngle * Math.clamp(score, 0, 100)) / 100;

  useEffect(() => {
    Animated.spring(animatedScore, {
      toValue: score,
      useNativeDriver: false,
      tension: 50,
      friction: 8,
    }).start();
  }, [score, animatedScore]);

  const bgArc = describeArc(cx, cy, r, startAngle, endAngle);
  const fgArc = describeArc(cx, cy, r, startAngle, arcAngle);

  return (
    <View style={styles.container}>
      <Svg width={size} height={size}>
        {/* Background track */}
        <Path
          d={bgArc}
          stroke={`${colors.textMuted}30`}
          strokeWidth={strokeWidth}
          fill="none"
          strokeLinecap="round"
        />
        {/* Colored arc */}
        <Path
          d={fgArc}
          stroke={color}
          strokeWidth={strokeWidth}
          fill="none"
          strokeLinecap="round"
        />
      </Svg>
      <View style={[StyleSheet.absoluteFill, styles.center]}>
        <Text style={[styles.score, { color }]}>{Math.round(score)}</Text>
        <Text style={[styles.label, { color }]}>{label}</Text>
      </View>
    </View>
  );
};

// Polyfill Math.clamp for environments that don't have it
declare global {
  interface Math {
    clamp(value: number, min: number, max: number): number
  }
}
if (!Math.clamp) {
  Math.clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), max);
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  center: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  score: {
    fontSize: 42,
    fontWeight: '800',
  },
  label: {
    fontSize: 12,
    fontWeight: '600',
    marginTop: 2,
  },
});
