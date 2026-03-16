import React from 'react';
import { View, Text, StyleSheet, Dimensions } from 'react-native';
import { colors, typography, spacing } from '../../../theme';
import type { ScenarioResult } from '../../../domain/Scenario';

interface ImpactChartProps {
  result: ScenarioResult
}

const CHART_WIDTH = Dimensions.get('window').width - spacing.md * 2 - spacing.md * 2;
const CHART_HEIGHT = 180;

export const ImpactChart: React.FC<ImpactChartProps> = ({ result }) => {
  const allValues = [...result.baselineForecast, ...result.impactedForecast];
  const maxVal = Math.max(...allValues, 1);
  const minVal = Math.min(...allValues, 0);
  const range = maxVal - minVal || 1;

  const getY = (value: number) => {
    return CHART_HEIGHT - ((value - minVal) / range) * CHART_HEIGHT;
  };

  const baselinePoints = result.baselineForecast.map((val, i) => {
    const x = result.baselineForecast.length > 1
      ? (i / (result.baselineForecast.length - 1)) * CHART_WIDTH
      : CHART_WIDTH / 2;
    return { x, y: getY(val) };
  });

  const impactedPoints = result.impactedForecast.map((val, i) => {
    const x = result.impactedForecast.length > 1
      ? (i / (result.impactedForecast.length - 1)) * CHART_WIDTH
      : CHART_WIDTH / 2;
    return { x, y: getY(val) };
  });

  const zeroY = getY(0);

  const formatAmount = (amount: number): string =>
    new Intl.NumberFormat('fr-FR', {
      style: 'currency',
      currency: 'EUR',
      minimumFractionDigits: 0,
    }).format(amount);

  return (
    <View style={styles.container}>
      <View style={[styles.chart, { width: CHART_WIDTH, height: CHART_HEIGHT }]}>
        {/* Zero line */}
        {minVal < 0 && maxVal > 0 && (
          <View style={[styles.zeroLine, { top: zeroY }]}>
            <Text style={styles.zeroLabel}>0 €</Text>
          </View>
        )}

        {/* Baseline dots */}
        {baselinePoints.map((point, i) => (
          <View
            key={`b-${i}`}
            style={[
              styles.dot,
              {
                left: point.x - 3,
                top: point.y - 3,
                backgroundColor: colors.primary,
              },
            ]}
          />
        ))}

        {/* Impacted dots */}
        {impactedPoints.map((point, i) => (
          <View
            key={`i-${i}`}
            style={[
              styles.dot,
              {
                left: point.x - 3,
                top: point.y - 3,
                backgroundColor: colors.danger,
              },
            ]}
          />
        ))}
      </View>

      <View style={styles.legendRow}>
        <View style={styles.legendItem}>
          <View style={[styles.legendDot, { backgroundColor: colors.primary }]} />
          <Text style={styles.legendText}>Prévision de base</Text>
        </View>
        <View style={styles.legendItem}>
          <View style={[styles.legendDot, { backgroundColor: colors.danger }]} />
          <Text style={styles.legendText}>Avec impact</Text>
        </View>
      </View>

      <View style={styles.summaryRow}>
        <View style={styles.summaryItem}>
          <Text style={styles.summaryLabel}>Impact maximal</Text>
          <Text style={[styles.summaryValue, { color: colors.danger }]}>
            {formatAmount(result.maxImpact)}
          </Text>
        </View>
        <View style={styles.summaryItem}>
          <Text style={styles.summaryLabel}>Trésorerie min.</Text>
          <Text
            style={[
              styles.summaryValue,
              { color: result.minBalance < 0 ? colors.danger : colors.success },
            ]}
          >
            {formatAmount(result.minBalance)}
          </Text>
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    marginTop: spacing.md,
  },
  chart: {
    position: 'relative',
    backgroundColor: colors.surface,
    borderRadius: 12,
    padding: spacing.sm,
    overflow: 'hidden',
  },
  zeroLine: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: StyleSheet.hairlineWidth,
    backgroundColor: colors.textMuted,
    flexDirection: 'row',
    alignItems: 'center',
  },
  zeroLabel: {
    color: colors.textMuted,
    fontSize: 10,
    position: 'absolute',
    right: 4,
    top: -8,
  },
  dot: {
    position: 'absolute',
    width: 6,
    height: 6,
    borderRadius: 3,
  },
  legendRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: spacing.lg,
    marginTop: spacing.sm,
    marginBottom: spacing.md,
  },
  legendItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs,
  },
  legendDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  legendText: {
    color: colors.textSecondary,
    fontSize: typography.caption.fontSize,
  },
  summaryRow: {
    flexDirection: 'row',
    gap: spacing.md,
  },
  summaryItem: {
    flex: 1,
    backgroundColor: colors.surface,
    borderRadius: 12,
    padding: spacing.md,
    alignItems: 'center',
  },
  summaryLabel: {
    color: colors.textMuted,
    fontSize: typography.caption.fontSize,
    marginBottom: spacing.xs,
  },
  summaryValue: {
    fontSize: typography.h3.fontSize,
    fontWeight: '700',
  },
});
