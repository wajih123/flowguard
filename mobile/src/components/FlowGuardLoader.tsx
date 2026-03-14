import React from 'react'
import { View, ActivityIndicator, Text, StyleSheet } from 'react-native'
import { colors, typography, spacing } from '../theme'

interface FlowGuardLoaderProps {
  message?: string
}

export const FlowGuardLoader: React.FC<FlowGuardLoaderProps> = ({ message }) => {
  return (
    <View style={styles.container}>
      <ActivityIndicator size="large" color={colors.primary} />
      {message && <Text style={styles.message}>{message}</Text>}
    </View>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
    justifyContent: 'center',
    alignItems: 'center',
    padding: spacing.lg,
  },
  message: {
    color: colors.textSecondary,
    fontSize: typography.body.fontSize,
    marginTop: spacing.md,
    textAlign: 'center',
  },
})
