import React from 'react'
import { View, Text, Dimensions, StyleSheet } from 'react-native'
import { FlowGuardCard } from '../../../components/FlowGuardCard'
import type { DailyBalance, CriticalPoint } from '../../../domain/TreasuryForecast'
import { colors, typography, spacing } from '../../../theme'
import { format, parseISO } from 'date-fns'
import { fr } from 'date-fns/locale'

interface ForecastChartProps {
  predictions: DailyBalance[]
  criticalPoints: CriticalPoint[]
}

const { width: SCREEN_WIDTH } = Dimensions.get('window')
const CHART_WIDTH = SCREEN_WIDTH - spacing.md * 4
const CHART_HEIGHT = 200
const PADDING = 20

export const ForecastChart: React.FC<ForecastChartProps> = ({
  predictions,
  criticalPoints,
}) => {
  if (predictions.length === 0) {
    return null
  }

  const balances = predictions.map((p) => p.balance)
  const minVal = Math.min(...balances)
  const maxVal = Math.max(...balances)
  const range = maxVal - minVal || 1

  const criticalDates = new Set(criticalPoints.map((cp) => cp.date))

  const yToScreen = (val: number) =>
    PADDING + (CHART_HEIGHT - 2 * PADDING) * (1 - (val - minVal) / range)

  const xToScreen = (index: number) =>
    PADDING + ((CHART_WIDTH - 2 * PADDING) * index) / (predictions.length - 1)

  const zeroY = minVal >= 0 ? CHART_HEIGHT - PADDING : yToScreen(0)

  const labelIndices: number[] = []
  const step = Math.max(1, Math.floor(predictions.length / 5))
  for (let i = 0; i < predictions.length; i += step) {
    labelIndices.push(i)
  }

  const formatAmount = (val: number) =>
    new Intl.NumberFormat('fr-FR', {
      style: 'currency',
      currency: 'EUR',
      maximumFractionDigits: 0,
    }).format(val)

  return (
    <FlowGuardCard style={styles.card}>
      <View style={[styles.chart, { width: CHART_WIDTH, height: CHART_HEIGHT + 30 }]}>
        {minVal < 0 && (
          <View
            style={[
              styles.zeroLine,
              { top: zeroY, width: CHART_WIDTH - 2 * PADDING, left: PADDING },
            ]}
          />
        )}

        {predictions.map((pred, index) => {
          const x = xToScreen(index)
          const y = yToScreen(pred.balance)
          const isCritical = criticalDates.has(pred.date)
          const isNeg = pred.balance < 0

          return (
            <View
              key={pred.date}
              style={[
                styles.dot,
                {
                  left: x - (isCritical ? 4 : 2),
                  top: y - (isCritical ? 4 : 2),
                  width: isCritical ? 8 : 4,
                  height: isCritical ? 8 : 4,
                  borderRadius: isCritical ? 4 : 2,
                  backgroundColor: isCritical
                    ? colors.danger
                    : isNeg
                      ? colors.danger
                      : colors.success,
                },
              ]}
            />
          )
        })}

        {labelIndices.map((i) => {
          const x = xToScreen(i)
          const dateStr = predictions[i].date
          let label = dateStr
          try {
            label = format(parseISO(dateStr), 'dd/MM', { locale: fr })
          } catch {
            label = dateStr.slice(5)
          }

          return (
            <Text
              key={i}
              style={[
                styles.xLabel,
                { left: x - 15, top: CHART_HEIGHT },
              ]}
            >
              {label}
            </Text>
          )
        })}

        <Text style={[styles.yLabel, { top: PADDING - 8, left: 0 }]}>
          {formatAmount(maxVal)}
        </Text>
        <Text style={[styles.yLabel, { top: CHART_HEIGHT - PADDING - 8, left: 0 }]}>
          {formatAmount(minVal)}
        </Text>
      </View>
    </FlowGuardCard>
  )
}

const styles = StyleSheet.create({
  card: {
    marginBottom: spacing.md,
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
    opacity: 0.5,
  },
  dot: {
    position: 'absolute',
  },
  xLabel: {
    position: 'absolute',
    color: colors.textMuted,
    fontSize: 9,
    width: 30,
    textAlign: 'center',
  },
  yLabel: {
    position: 'absolute',
    color: colors.textMuted,
    fontSize: 9,
  },
})
