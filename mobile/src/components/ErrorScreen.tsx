import React from 'react'
import { View, Text, StyleSheet } from 'react-native'
import { FlowGuardButton } from './FlowGuardButton'
import { colors, typography, spacing } from '../theme'

interface ErrorScreenProps {
  message: string
  onRetry?: () => void
}

export const ErrorScreen: React.FC<ErrorScreenProps> = ({ message, onRetry }) => {
  return (
    <View style={styles.container}>
      <Text style={styles.icon}>⚠️</Text>
      <Text style={styles.message}>{message}</Text>
      {onRetry && (
        <FlowGuardButton
          label="Réessayer"
          onPress={onRetry}
          variant="secondary"
          style={styles.button}
        />
      )}
    </View>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
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
    marginBottom: spacing.lg,
  },
  button: {
    minWidth: 160,
  },
})
