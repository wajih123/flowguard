import React from 'react'
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native'
import { FlowGuardCard } from '../../../components/FlowGuardCard'
import type { Alert } from '../../../domain/Alert'
import { colors, typography, spacing } from '../../../theme'
import { format } from 'date-fns'
import { fr } from 'date-fns/locale'

interface AlertCardProps {
  alert: Alert
  onMarkRead: () => void
}

const severityColorMap: Record<string, string> = {
  LOW: colors.severityLow,
  MEDIUM: colors.severityMedium,
  HIGH: colors.severityHigh,
  CRITICAL: colors.severityCritical,
}

const typeLabels: Record<string, string> = {
  CASH_SHORTAGE: 'Déficit de trésorerie',
  UNUSUAL_SPEND: 'Dépense inhabituelle',
  PAYMENT_DUE: 'Échéance à venir',
  POSITIVE_TREND: 'Tendance positive',
}

export const AlertCard: React.FC<AlertCardProps> = ({ alert, onMarkRead }) => {
  const borderColor = severityColorMap[alert.severity] ?? colors.primary

  const formattedDeficit = alert.projectedDeficit
    ? new Intl.NumberFormat('fr-FR', {
        style: 'currency',
        currency: 'EUR',
        minimumFractionDigits: 0,
      }).format(Math.abs(alert.projectedDeficit))
    : null

  let triggerLabel: string | null = null
  if (alert.triggerDate) {
    try {
      triggerLabel = format(new Date(alert.triggerDate), 'dd MMMM yyyy', { locale: fr })
    } catch {
      triggerLabel = alert.triggerDate
    }
  }

  return (
    <FlowGuardCard style={[styles.card, { borderLeftColor: borderColor }]}>
      <View style={styles.header}>
        <View style={[styles.severityBadge, { backgroundColor: borderColor + '30' }]}>
          <Text style={[styles.severityText, { color: borderColor }]}>
            {alert.severity}
          </Text>
        </View>
        <Text style={styles.typeLabel}>{typeLabels[alert.type] ?? alert.type}</Text>
      </View>

      <Text style={styles.message}>{alert.message}</Text>

      {formattedDeficit && (
        <Text style={styles.deficit}>Déficit prévu: -{formattedDeficit}</Text>
      )}

      {triggerLabel && (
        <Text style={styles.triggerDate}>Prévu le {triggerLabel}</Text>
      )}

      {!alert.isRead && (
        <TouchableOpacity onPress={onMarkRead} style={styles.markReadButton}>
          <Text style={styles.markReadText}>Marquer lue</Text>
        </TouchableOpacity>
      )}
    </FlowGuardCard>
  )
}

const styles = StyleSheet.create({
  card: {
    borderLeftWidth: 3,
    marginBottom: spacing.sm,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    marginBottom: spacing.sm,
  },
  severityBadge: {
    paddingHorizontal: spacing.sm,
    paddingVertical: 2,
    borderRadius: 6,
  },
  severityText: {
    fontSize: typography.caption.fontSize,
    fontWeight: '700',
  },
  typeLabel: {
    color: colors.textSecondary,
    fontSize: typography.caption.fontSize,
    fontWeight: '600',
  },
  message: {
    color: colors.textPrimary,
    fontSize: typography.body.fontSize,
    lineHeight: 22,
    marginBottom: spacing.sm,
  },
  deficit: {
    color: colors.danger,
    fontSize: typography.body.fontSize,
    fontWeight: '600',
    marginBottom: spacing.xs,
  },
  triggerDate: {
    color: colors.textMuted,
    fontSize: typography.caption.fontSize,
    marginBottom: spacing.sm,
  },
  markReadButton: {
    alignSelf: 'flex-start',
    paddingVertical: spacing.xs,
    paddingHorizontal: spacing.sm,
    borderRadius: 8,
    backgroundColor: colors.primary + '20',
  },
  markReadText: {
    color: colors.primary,
    fontSize: typography.caption.fontSize,
    fontWeight: '600',
  },
})
