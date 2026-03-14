import React, { useCallback } from 'react'
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { StackScreenProps } from '@react-navigation/stack'
import { FlowGuardCard } from '../../components/FlowGuardCard'
import { FlowGuardButton } from '../../components/FlowGuardButton'
import { SeverityBadge } from '../../components/SeverityBadge'
import { useAccountStore } from '../../store/accountStore'
import { Routes } from '../../navigation/routes'
import { colors, typography, spacing } from '../../theme'
import * as flowguardApi from '../../api/flowguardApi'
import type { Alert } from '../../domain/Alert'
import { format } from 'date-fns'
import { fr } from 'date-fns/locale'

const typeLabels: Record<string, string> = {
  CASH_SHORTAGE: 'Déficit de trésorerie',
  UNUSUAL_SPEND: 'Dépense inhabituelle',
  PAYMENT_DUE: 'Échéance à venir',
  POSITIVE_TREND: 'Tendance positive',
}

const typeActions: Record<string, string> = {
  CASH_SHORTAGE:
    'Activez la Réserve FlowGuard pour couvrir ce besoin de trésorerie à court terme. Taux commission 1,5 %.',
  UNUSUAL_SPEND:
    "Analysez vos dernières transactions pour identifier la dépense atypique. Configurez un scénario pour anticiper l'impact.",
  PAYMENT_DUE:
    "Assurez-vous que votre solde sera suffisant à la date d'échéance. Utilisez la Réserve si besoin.",
  POSITIVE_TREND:
    "Votre trésorerie est en bonne santé. Pensez à épargner l'excédent ou à anticiper des investissements.",
}

type Props = StackScreenProps<Record<string, { alert: Alert }>, typeof Routes.AlertDetail>

export const AlertDetailScreen: React.FC<Props> = ({ route, navigation }) => {
  const alert = route.params?.alert
  const account = useAccountStore((s) => s.account)
  const queryClient = useQueryClient()

  const { mutate: markRead, isPending } = useMutation({
    mutationFn: () => flowguardApi.markAlertRead(alert.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['alerts', account?.id] })
    },
  })

  const handleMarkRead = useCallback(() => {
    if (!alert.isRead) markRead()
  }, [alert.isRead, markRead])

  const formattedDate = (() => {
    try {
      return format(new Date(alert.createdAt), 'dd MMMM yyyy à HH:mm', { locale: fr })
    } catch {
      return ''
    }
  })()

  const severityVariantMap: Record<string, 'alert' | 'success' | 'info' | 'warning'> = {
    CRITICAL: 'alert',
    HIGH: 'alert',
    MEDIUM: 'warning',
    LOW: 'info',
  }
  const cardVariant = severityVariantMap[alert.severity] ?? 'info'

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
        <Text style={styles.backText}>← Retour</Text>
      </TouchableOpacity>

      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        <View style={styles.titleRow}>
          <Text style={styles.title}>{typeLabels[alert.type] ?? alert.type}</Text>
          <SeverityBadge severity={alert.severity} />
        </View>
        {formattedDate ? <Text style={styles.date}>{formattedDate}</Text> : null}

        <FlowGuardCard variant={cardVariant} style={styles.messageCard}>
          <Text style={styles.messageText}>{alert.message}</Text>
        </FlowGuardCard>

        {alert.projectedDeficit != null && (
          <FlowGuardCard style={styles.infoCard}>
            <Text style={styles.infoLabel}>Déficit projeté</Text>
            <Text style={styles.infoValue}>
              {new Intl.NumberFormat('fr-FR', {
                style: 'currency',
                currency: 'EUR',
                minimumFractionDigits: 0,
              }).format(Math.abs(alert.projectedDeficit))}
            </Text>
          </FlowGuardCard>
        )}

        {alert.triggerDate && (
          <FlowGuardCard style={styles.infoCard}>
            <Text style={styles.infoLabel}>Date de déclenchement</Text>
            <Text style={styles.infoValue}>
              {(() => {
                try {
                  return format(new Date(alert.triggerDate!), 'dd MMMM yyyy', { locale: fr })
                } catch {
                  return alert.triggerDate
                }
              })()}
            </Text>
          </FlowGuardCard>
        )}

        <Text style={styles.sectionTitle}>Que faire ?</Text>
        <FlowGuardCard variant="info" style={styles.actionCard}>
          <Text style={styles.actionText}>
            {typeActions[alert.type] ?? 'Analysez votre situation de trésorerie.'}
          </Text>
        </FlowGuardCard>

        {(alert.type === 'CASH_SHORTAGE' || alert.type === 'PAYMENT_DUE') && (
          <FlowGuardButton
            label="Activer la Réserve"
            onPress={() => navigation.navigate(Routes.Reserve as never)}
            variant="primary"
            style={styles.ctaBtn}
          />
        )}

        {!alert.isRead && (
          <FlowGuardButton
            label="Marquer comme lue"
            onPress={handleMarkRead}
            variant="outline"
            loading={isPending}
            style={styles.readBtn}
          />
        )}
      </ScrollView>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  backBtn: { paddingHorizontal: spacing.md, paddingTop: spacing.md, paddingBottom: spacing.sm },
  backText: { color: colors.primary, ...typography.body, fontWeight: '600' },
  scroll: { padding: spacing.md, paddingBottom: spacing.xxl },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: spacing.xs,
  },
  title: { ...typography.h2, color: colors.textPrimary, flex: 1, marginRight: spacing.sm },
  date: { ...typography.caption, color: colors.textMuted, marginBottom: spacing.md },
  messageCard: { marginBottom: spacing.md },
  messageText: { color: colors.textPrimary, ...typography.body },
  infoCard: {
    marginBottom: spacing.sm,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  infoLabel: { color: colors.textSecondary, ...typography.caption },
  infoValue: { color: colors.textPrimary, ...typography.h3 },
  sectionTitle: {
    ...typography.h3,
    color: colors.textPrimary,
    marginTop: spacing.md,
    marginBottom: spacing.sm,
  },
  actionCard: { marginBottom: spacing.md },
  actionText: { color: colors.textPrimary, ...typography.body },
  ctaBtn: { marginBottom: spacing.sm },
  readBtn: { marginBottom: spacing.sm },
})
