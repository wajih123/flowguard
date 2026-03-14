import React from 'react'
import { View, Text, StyleSheet } from 'react-native'
import { colors, typography, spacing } from '../theme'

interface EmptyStateProps {
  message: string
  icon?: string
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  message,
  icon = '📭',
}) => {
  return (
    <View style={styles.container}>
      <Text style={styles.icon}>{icon}</Text>
      <Text style={styles.message}>{message}</Text>
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
})
