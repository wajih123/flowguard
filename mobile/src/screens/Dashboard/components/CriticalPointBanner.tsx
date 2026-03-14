import React from 'react'
import { View, Text, StyleSheet } from 'react-native'
import { FlowGuardButton } from '../../../components/FlowGuardButton'
import type { CriticalPoint } from '../../../domain/TreasuryForecast'
import { colors, typography, spacing } from '../../../theme'

interface CriticalPointBannerProps {
  criticalPoints: CriticalPoint[]
  onActivateCredit: () => void
}

export const CriticalPointBanner: React.FC<CriticalPointBannerProps> = ({
  criticalPoints,
  onActivateCredit,
}) => {
  if (criticalPoints.length === 0) {
    return null
  }

  const nearest = criticalPoints.reduce((prev, curr) =>
    curr.daysUntil < prev.daysUntil ? curr : prev,
  )

  const formattedAmount = new Intl.NumberFormat('fr-FR', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 0,
  }).format(Math.abs(nearest.projectedBalance))

  return (
    <View style={styles.banner}>
      <Text style={styles.icon}>⚠️</Text>
      <Text style={styles.message}>
        Déficit prévu de {formattedAmount} dans {nearest.daysUntil} jours
      </Text>
      <FlowGuardButton
        label="Activer une avance"
        onPress={onActivateCredit}
        variant="danger"
        style={styles.button}
      />
    </View>
  )
}

const styles = StyleSheet.create({
  banner: {
    backgroundColor: colors.danger + '20',
    borderWidth: 1,
    borderColor: colors.danger + '40',
    borderRadius: 16,
    padding: spacing.md,
    marginBottom: spacing.md,
    alignItems: 'center',
  },
  icon: {
    fontSize: 32,
    marginBottom: spacing.sm,
  },
  message: {
    color: colors.textPrimary,
    fontSize: typography.body.fontSize,
    fontWeight: '600',
    textAlign: 'center',
    marginBottom: spacing.md,
  },
  button: {
    alignSelf: 'stretch',
  },
})
