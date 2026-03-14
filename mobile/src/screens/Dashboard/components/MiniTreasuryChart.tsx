import React from 'react'
import { View, Text, StyleSheet, Dimensions } from 'react-native'
import { FlowGuardCard } from '../../../components/FlowGuardCard'
import type { DailyBalance } from '../../../domain/TreasuryForecast'
import { colors, typography, spacing } from '../../../theme'

interface MiniTreasuryChartProps {
  predictions: DailyBalance[]
}

const { width: SCREEN_WIDTH } = Dimensions.get('window')
const CHART_WIDTH = SCREEN_WIDTH - spacing.md * 4
const CHART_HEIGHT = 120

export const MiniTreasuryChart: React.FC<MiniTreasuryChartProps> = ({ predictions }) => {
  if (predictions.length === 0) {
    return null
  }

  const balances = predictions.map((p) => p.balance)
  const minVal = Math.min(...balances)
  const maxVal = Math.max(...balances)
  const range = maxVal - minVal || 1

  const points = balances.map((balance, index) => {
    const x = (index / (balances.length - 1)) * CHART_WIDTH
    const y = CHART_HEIGHT - ((balance - minVal) / range) * CHART_HEIGHT
    return { x, y }
  })

  const zeroY =
    minVal >= 0
      ? CHART_HEIGHT
      : CHART_HEIGHT - ((0 - minVal) / range) * CHART_HEIGHT

  const hasNegative = minVal < 0

  return (
    <FlowGuardCard style={styles.card}>
      <Text style={styles.label}>PRÉVISION 30 JOURS</Text>
      <View style={styles.chartContainer}>
        <View style={[styles.chart, { width: CHART_WIDTH, height: CHART_HEIGHT }]}>
          {hasNegative && (
            <View
              style={[
                styles.zeroLine,
                { top: zeroY, width: CHART_WIDTH },
              ]}
            />
          )}
          {points.map((point, index) => {
            if (index === 0) return null
            const prev = points[index - 1]
            const isNeg = balances[index] < 0
            return (
              <View
                key={index}
                style={[
                  styles.dot,
                  {
                    left: point.x - 2,
                    top: point.y - 2,
                    backgroundColor: isNeg ? colors.danger : colors.success,
                  },
                ]}
              />
            )
          })}
        </View>
      </View>
    </FlowGuardCard>
  )
}

const styles = StyleSheet.create({
  card: {
    marginBottom: spacing.md,
  },
  label: {
    color: colors.textMuted,
    fontSize: typography.label.fontSize,
    fontWeight: typography.label.fontWeight,
    letterSpacing: typography.label.letterSpacing,
    marginBottom: spacing.sm,
  },
  chartContainer: {
    alignItems: 'center',
  },
  chart: {
    position: 'relative',
  },
  zeroLine: {
    position: 'absolute',
    height: 1,
    borderStyle: 'dashed',
    borderWidth: 1,
    borderColor: colors.danger,
  },
  dot: {
    position: 'absolute',
    width: 4,
    height: 4,
    borderRadius: 2,
  },
})
