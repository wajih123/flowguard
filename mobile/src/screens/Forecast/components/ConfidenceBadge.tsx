import React from 'react'
import { View, Text, StyleSheet } from 'react-native'
import { colors, typography, spacing } from '../../../theme'

interface ConfidenceBadgeProps {
  confidence: number
}

export const ConfidenceBadge: React.FC<ConfidenceBadgeProps> = ({ confidence }) => {
  const percent = Math.round(confidence * 100)
  const dotColor = percent >= 80 ? colors.success : percent >= 60 ? colors.warning : colors.danger

  return (
    <View style={styles.container}>
      <View style={[styles.dot, { backgroundColor: dotColor }]} />
      <Text style={styles.text}>Confiance IA: {percent}%</Text>
    </View>
  )
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: spacing.md,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: spacing.sm,
  },
  text: {
    color: colors.textSecondary,
    fontSize: typography.caption.fontSize,
    fontWeight: '600',
  },
})
