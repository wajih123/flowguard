import React from 'react'
import { View, Text, StyleSheet } from 'react-native'
import { colors, typography, spacing } from '../theme'

interface EmptyStateProps {
  message?: string
  title?: string
  subtitle?: string
  icon?: string
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  message,
  title,
  subtitle,
  icon = '📭',
}) => {
  const heading = title ?? message ?? ''
  return (
    <View style={styles.container}>
      <Text style={styles.icon}>{icon}</Text>
      {heading ? <Text style={styles.message}>{heading}</Text> : null}
      {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
    </View>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: spacing.xl,
  },
  icon: {
    fontSize: 48,
    marginBottom: spacing.md,
  },
  message: {
    color: colors.textSecondary,
    fontSize: typography.body.fontSize,
    textAlign: 'center',
    lineHeight: 22,
  },
  subtitle: {
    color: colors.textMuted,
    fontSize: typography.body.fontSize - 2,
    textAlign: 'center',
    marginTop: 4,
    lineHeight: 20,
  },
})
