import React from 'react'
import { View, Text, StyleSheet } from 'react-native'
import { colors, typography, spacing } from '../../../theme'
import type { SpendingCategory } from '../../../domain/SpendingAnalysis'

interface DonutChartProps {
  categories: SpendingCategory[]
  totalAmount: number
}

const SIZE = 200
const STROKE_WIDTH = 28
const RADIUS = (SIZE - STROKE_WIDTH) / 2
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

const CATEGORY_COLORS: string[] = [
  colors.primary,
  '#8B5CF6',
  '#F59E0B',
  '#EF4444',
  '#10B981',
  '#EC4899',
  '#6366F1',
  '#14B8A6',
]

export const DonutChart: React.FC<DonutChartProps> = ({ categories, totalAmount }) => {
  const formattedTotal = new Intl.NumberFormat('fr-FR', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 0,
  }).format(totalAmount)

  let cumulativePercent = 0

  return (
    <View style={styles.container}>
      <View style={styles.chartWrapper}>
        {/* We build a manual donut via layered Views with border arcs */}
        {categories.map((cat, index) => {
          const percent = cat.percentage / 100
          const offset = CIRCUMFERENCE * (1 - percent)
          const rotation = cumulativePercent * 360 - 90
          cumulativePercent += percent
          const color = CATEGORY_COLORS[index % CATEGORY_COLORS.length]

          return (
            <View
              key={cat.category}
              style={[
                styles.arcLayer,
                {
                  width: SIZE,
                  height: SIZE,
                  borderRadius: SIZE / 2,
                  borderWidth: STROKE_WIDTH,
                  borderColor: 'transparent',
                  borderTopColor: color,
                  borderRightColor: percent > 0.25 ? color : 'transparent',
                  borderBottomColor: percent > 0.5 ? color : 'transparent',
                  borderLeftColor: percent > 0.75 ? color : 'transparent',
                  transform: [{ rotate: `${rotation}deg` }],
                },
              ]}
            />
          )
        })}
        <View style={styles.centerLabel}>
          <Text style={styles.totalLabel}>Total</Text>
          <Text style={styles.totalAmount}>{formattedTotal}</Text>
        </View>
      </View>

      <View style={styles.legend}>
        {categories.map((cat, index) => {
          const color = CATEGORY_COLORS[index % CATEGORY_COLORS.length]
          const formattedAmount = new Intl.NumberFormat('fr-FR', {
            style: 'currency',
            currency: 'EUR',
            minimumFractionDigits: 0,
          }).format(cat.amount)

          return (
            <View key={cat.category} style={styles.legendItem}>
              <View style={[styles.legendDot, { backgroundColor: color }]} />
              <Text style={styles.legendLabel}>{cat.category}</Text>
              <Text style={styles.legendPercent}>{cat.percentage}%</Text>
              <Text style={styles.legendAmount}>{formattedAmount}</Text>
            </View>
          )
        })}
      </View>
    </View>
  )
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
  },
  chartWrapper: {
    width: SIZE,
    height: SIZE,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: spacing.lg,
  },
  arcLayer: {
    position: 'absolute',
  },
  centerLabel: {
    alignItems: 'center',
  },
  totalLabel: {
    color: colors.textMuted,
    fontSize: typography.caption.fontSize,
    marginBottom: 2,
  },
  totalAmount: {
    color: colors.textPrimary,
    fontSize: typography.h2.fontSize,
    fontWeight: '700',
  },
  legend: {
    width: '100%',
    gap: spacing.sm,
  },
  legendItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
  },
  legendDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  legendLabel: {
    flex: 1,
    color: colors.textPrimary,
    fontSize: typography.body.fontSize,
  },
  legendPercent: {
    color: colors.textSecondary,
    fontSize: typography.caption.fontSize,
    width: 40,
    textAlign: 'right',
  },
  legendAmount: {
    color: colors.textPrimary,
    fontSize: typography.body.fontSize,
    fontWeight: '600',
    width: 80,
    textAlign: 'right',
  },
})
