import React, { useState } from 'react'
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  RefreshControl,
  TouchableOpacity,
  Alert,
} from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { FlowGuardCard } from '../../components/FlowGuardCard'
import { FlowGuardButton } from '../../components/FlowGuardButton'
import { FlowGuardLoader } from '../../components/FlowGuardLoader'
import { colors, typography, spacing } from '../../theme'
import * as flowguardApi from '../../api/flowguardApi'

const TAX_LABELS: Record<string, string> = {
  TVA: 'TVA collectée',
  URSSAF: 'Cotisations URSSAF',
  IS: 'Impôt sur les sociétés',
  IR: 'Impôt sur le revenu',
  CFE: 'CFE',
}

const TAX_COLORS: Record<string, string> = {
  TVA: colors.primary,
  URSSAF: colors.warning ?? '#F59E0B',
  IS: '#A78BFA',
  IR: '#FBBF24',
  CFE: '#F97316',
}

const fmtEur = (n: number) =>
  new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(n)

const fmtDate = (iso: string) =>
  new Date(iso).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' })

export const TaxScreen: React.FC = () => {
  const [showAll, setShowAll] = useState(false)
  const qc = useQueryClient()

  const {
    data: allTaxes,
    isLoading: loadingAll,
    refetch,
    isFetching,
  } = useQuery({
    queryKey: ['mobile-taxes'],
    queryFn: flowguardApi.getTaxEstimates,
  })

  const { data: upcoming, isLoading: loadingUpcoming } = useQuery({
    queryKey: ['mobile-taxes-upcoming'],
    queryFn: flowguardApi.getUpcomingTaxes,
  })

  const paidMut = useMutation({
    mutationFn: flowguardApi.markTaxPaid,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['mobile-taxes'] })
      qc.invalidateQueries({ queryKey: ['mobile-taxes-upcoming'] })
    },
    onError: () => Alert.alert('Erreur', 'Impossible de marquer cette obligation comme payée.'),
  })

  const regenMut = useMutation({
    mutationFn: flowguardApi.regenerateTaxEstimates,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['mobile-taxes'] })
      qc.invalidateQueries({ queryKey: ['mobile-taxes-upcoming'] })
    },
    onError: () => Alert.alert('Erreur', 'Impossible de recalculer les obligations fiscales.'),
  })

  const displayed = showAll ? allTaxes : upcoming
  const isLoading = showAll ? loadingAll : loadingUpcoming

  const totalUnpaid: number = (allTaxes ?? [])
    .filter((t: any) => !t.isPaid)
    .reduce((s: number, t: any) => s + t.estimatedAmount, 0)

  const nextDeadline = (upcoming ?? [])[0]

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView
        contentContainerStyle={styles.content}
        refreshControl={
          <RefreshControl refreshing={isFetching} onRefresh={refetch} tintColor={colors.primary} />
        }
      >
        {/* Header */}
        <View style={styles.headerRow}>
          <Text style={styles.title}>Calendrier fiscal</Text>
          <TouchableOpacity
            onPress={() => regenMut.mutate()}
            disabled={regenMut.isPending}
            style={styles.regenBtn}
          >
            <Text style={styles.regenBtnText}>{regenMut.isPending ? '…' : '↻'}</Text>
          </TouchableOpacity>
        </View>

        {/* KPIs */}
        <View style={styles.kpiRow}>
          <FlowGuardCard style={styles.kpiCard}>
            <Text style={styles.kpiLabel}>À payer</Text>
            <Text style={[styles.kpiValue, { color: colors.warning ?? '#F59E0B' }]}>
              {fmtEur(totalUnpaid)}
            </Text>
          </FlowGuardCard>
          <FlowGuardCard style={styles.kpiCard}>
            <Text style={styles.kpiLabel}>Prochaine</Text>
            {nextDeadline ? (
              <Text style={styles.kpiValueSm}>
                {TAX_LABELS[nextDeadline.taxType] ?? nextDeadline.taxType}
                {'\n'}
                <Text style={{ color: colors.warning ?? '#F59E0B', fontSize: 11 }}>
                  {nextDeadline.daysUntilDue != null ? `dans ${nextDeadline.daysUntilDue}j` : ''}
                </Text>
              </Text>
            ) : (
              <Text style={[styles.kpiValueSm, { color: colors.success }]}>RAS</Text>
            )}
          </FlowGuardCard>
        </View>

        {/* Toggle */}
        <View style={styles.toggleRow}>
          <TouchableOpacity
            onPress={() => setShowAll(false)}
            style={[styles.toggleBtn, !showAll && styles.toggleBtnActive]}
          >
            <Text style={[styles.toggleText, !showAll && styles.toggleTextActive]}>Prochaines</Text>
          </TouchableOpacity>
          <TouchableOpacity
            onPress={() => setShowAll(true)}
            style={[styles.toggleBtn, showAll && styles.toggleBtnActive]}
          >
            <Text style={[styles.toggleText, showAll && styles.toggleTextActive]}>Toutes</Text>
          </TouchableOpacity>
        </View>

        {isLoading ? (
          <FlowGuardLoader message="Chargement…" />
        ) : !(displayed ?? []).length ? (
          <Text style={styles.empty}>
            Aucune obligation fiscale.{'\n'}Appuyez sur ↻ pour recalculer depuis vos factures.
          </Text>
        ) : (
          (displayed as any[]).map((tax: any) => {
            const taxColor = TAX_COLORS[tax.taxType] ?? colors.primary
            const overdue = !tax.isPaid && tax.daysUntilDue != null && tax.daysUntilDue < 0
            const urgent =
              !tax.isPaid && tax.daysUntilDue != null && tax.daysUntilDue <= 7 && !overdue
            return (
              <FlowGuardCard
                key={tax.id}
                style={[styles.taxCard, { borderLeftColor: taxColor, borderLeftWidth: 3 }]}
              >
                <View style={styles.taxRow}>
                  <View style={styles.taxInfo}>
                    <Text style={[styles.taxType, { color: taxColor }]}>
                      {TAX_LABELS[tax.taxType] ?? tax.taxType}
                    </Text>
                    <Text style={styles.period}>{tax.periodLabel}</Text>
                    <Text style={styles.dueDate}>
                      Échéance&nbsp;: {fmtDate(tax.dueDate)}
                      {overdue && <Text style={{ color: colors.danger }}> · En retard</Text>}
                      {urgent && (
                        <Text style={{ color: colors.warning ?? '#F59E0B' }}> · Urgent</Text>
                      )}
                    </Text>
                  </View>
                  <View style={styles.taxRight}>
                    <Text style={styles.taxAmount}>{fmtEur(tax.estimatedAmount)}</Text>
                    {tax.isPaid ? (
                      <Text style={styles.paidBadge}>✓ Payé</Text>
                    ) : (
                      <FlowGuardButton
                        label="Marquer payé"
                        variant="secondary"
                        onPress={() => paidMut.mutate(tax.id)}
                        style={styles.paidBtn}
                      />
                    )}
                  </View>
                </View>
              </FlowGuardCard>
            )
          })
        )}
      </ScrollView>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  content: { padding: spacing.md, gap: spacing.sm },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: spacing.xs,
  },
  title: { ...typography.h2, color: colors.textPrimary },
  regenBtn: {
    backgroundColor: colors.surface,
    borderRadius: 8,
    width: 36,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: colors.cardBorder,
  },
  regenBtnText: { color: colors.primary, fontSize: 18, fontWeight: '700' },
  kpiRow: { flexDirection: 'row', gap: spacing.sm },
  kpiCard: { flex: 1, padding: spacing.md },
  kpiLabel: { ...typography.caption, color: colors.textMuted, marginBottom: 4 },
  kpiValue: { ...typography.h3, fontWeight: '700' },
  kpiValueSm: { ...typography.body, color: colors.textPrimary, fontWeight: '600', lineHeight: 18 },
  toggleRow: {
    flexDirection: 'row',
    backgroundColor: colors.surface,
    borderRadius: 8,
    padding: 3,
    borderWidth: 1,
    borderColor: colors.cardBorder,
  },
  toggleBtn: { flex: 1, paddingVertical: spacing.xs, alignItems: 'center', borderRadius: 6 },
  toggleBtnActive: { backgroundColor: colors.primary },
  toggleText: { ...typography.caption, color: colors.textMuted, fontWeight: '600' },
  toggleTextActive: { color: '#fff' },
  taxCard: { padding: spacing.md },
  taxRow: { flexDirection: 'row', justifyContent: 'space-between', gap: spacing.sm },
  taxInfo: { flex: 1, gap: 2 },
  taxType: { ...typography.body, fontWeight: '700' },
  period: { ...typography.caption, color: colors.textMuted },
  dueDate: { ...typography.caption, color: colors.textMuted },
  taxRight: { alignItems: 'flex-end', gap: spacing.xs },
  taxAmount: { ...typography.h3, color: colors.textPrimary, fontWeight: '700' },
  paidBadge: { ...typography.caption, color: colors.success, fontWeight: '700' },
  paidBtn: { marginTop: 0 },
  empty: { textAlign: 'center', color: colors.textMuted, marginTop: spacing.xl, lineHeight: 22 },
})

export default TaxScreen
