import React, { useEffect } from 'react'
import { View, Text, StyleSheet } from 'react-native'
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withTiming,
  Easing,
} from 'react-native-reanimated'
import { colors, typography, spacing } from '../../../theme'

interface HealthScoreMeterProps {
  score: number
}

export const HealthScoreMeter: React.FC<HealthScoreMeterProps> = ({ score }) => {
  const width = useSharedValue(0)

  useEffect(() => {
    width.value = withTiming(score, {
      duration: 800,
      easing: Easing.out(Easing.cubic),
    })
  }, [score, width])

  const barColor =
    score >= 70 ? colors.success : score >= 40 ? colors.warning : colors.danger

  const animatedStyle = useAnimatedStyle(() => ({
    width: `${width.value}%`,
    backgroundColor: barColor,
  }))

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.label}>SCORE DE SANTÉ</Text>
        <Text style={[styles.score, { color: barColor }]}>{score}/100</Text>
      </View>
      <View style={styles.track}>
        <Animated.View style={[styles.fill, animatedStyle]} />
      </View>
    </View>
  )
}

const styles = StyleSheet.create({
  container: {
    marginBottom: spacing.md,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: spacing.sm,
  },
  label: {
    color: colors.textMuted,
    fontSize: typography.label.fontSize,
    fontWeight: typography.label.fontWeight,
    letterSpacing: typography.label.letterSpacing,
  },
  score: {
    fontSize: typography.h3.fontSize,
    fontWeight: typography.h3.fontWeight,
  },
  track: {
    height: 8,
    borderRadius: 4,
    backgroundColor: colors.card,
    overflow: 'hidden',
  },
  fill: {
    height: '100%',
    borderRadius: 4,
  },
})
