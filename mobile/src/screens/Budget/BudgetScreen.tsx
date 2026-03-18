import React, { useState } from 'react'
import { View, Text, ScrollView, StyleSheet, RefreshControl, TouchableOpacity } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { useQuery } from '@tanstack/react-query'
import { FlowGuardCard } from '../../components/FlowGuardCard'
import { FlowGuardLoader } from '../../components/FlowGuardLoader'
import { colors, typography, spacing } from '../../theme'
import * as flowguardApi from '../../api/flowguardApi'

const fmtEur = (n: number) =>
  new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(n)

const MONTHS = [
  'Janvier',
  'Février',
  'Mars',
  'Avril',
  'Mai',
  'Juin',
  'Juillet',
  'Août',
  'Septembre',
  'Octobre',
  'Novembre',
  'Décembre',
]

const STATUS_CONFIG: Record<string, { label: string; color: string }> = {
  OVER_BUDGET: { label: 'Dépassé', color: colors.danger },
  ON_TRACK: { label: 'OK', color: colors.success },
  UNDER_BUDGET: { label: 'Sous budget', color: colors.primary },
}

interface BudgetLine {
  category: string
  budgeted: number
  actual: number
  variance: number
  status: string
}

export const BudgetScreen: React.FC = () => {
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)

  const {
    data: vsActual,
    isLoading,
    refetch,
    isFetching,
  } = useQuery({
    queryKey: ['mobile-budget-vs-actual', year, month],
    queryFn: () => flowguardApi.getBudgetVsActual(year, month),
  })

  const totalBudgeted: number = ((vsActual?.lines ?? []) as BudgetLine[]).reduce(
    (s, l) => s + l.budgeted,
    0,
  )
  const totalActual: number = ((vsActual?.lines ?? []) as BudgetLine[]).reduce(
    (s, l) => s + l.actual,
    0,
  )

  const prevMonth = () => {
    if (month === 1) {
      setMonth(12)
      setYear((y) => y - 1)
    } else setMonth((m) => m - 1)
  }
  const nextMonth = () => {
    if (month === 12) {
      setMonth(1)
      setYear((y) => y + 1)
    } else setMonth((m) => m + 1)
  }

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView
        contentContainerStyle={styles.content}
        refreshControl={
          <RefreshControl refreshing={isFetching} onRefresh={refetch} tintColor={colors.primary} />
        }
      >
        {/* Header with period navigation */}
        <View style={styles.headerRow}>
          <Text style={styles.title}>Budget mensuel</Text>
        </View>
        <View style={styles.periodNav}>
          <TouchableOpacity onPress={prevMonth} style={styles.navBtn}>
            <Text style={styles.navBtnText}>‹</Text>
          </TouchableOpacity>
          <Text style={styles.periodLabel}>
            {MONTHS[month - 1]} {year}
          </Text>
          <TouchableOpacity onPress={nextMonth} style={styles.navBtn}>
            <Text style={styles.navBtnText}>›</Text>
          </TouchableOpacity>
        </View>

        {/* KPI row */}
        <View style={styles.kpiRow}>
          <FlowGuardCard style={styles.kpiCard}>
            <Text style={styles.kpiLabel}>Budgeté</Text>
            <Text style={styles.kpiValue}>{fmtEur(totalBudgeted)}</Text>
          </FlowGuardCard>
          <FlowGuardCard style={styles.kpiCard}>
            <Text style={styles.kpiLabel}>Réel</Text>
            <Text
              style={[
                styles.kpiValue,
                { color: totalActual > totalBudgeted ? colors.danger : colors.success },
              ]}
            >
              {fmtEur(totalActual)}
            </Text>
          </FlowGuardCard>
        </View>

        {isLoading ? (
          <FlowGuardLoader message="Chargement…" />
        ) : !(vsActual?.lines ?? []).length ? (
          <Text style={styles.empty}>Aucun budget défini pour cette période.</Text>
        ) : (
          (vsActual.lines as BudgetLine[]).map((line) => {
            const cfg = STATUS_CONFIG[line.status] ?? {
              label: line.status,
              color: colors.textMuted,
            }
            const pct = line.budgeted > 0 ? Math.min(1, line.actual / line.budgeted) : 0
            return (
              <FlowGuardCard key={line.category} style={styles.lineCard}>
                <View style={styles.lineHeader}>
                  <Text style={styles.category}>
                    {line.category.replace(/_/g, ' ').toLowerCase()}
                  </Text>
                  <Text style={[styles.statusBadge, { color: cfg.color }]}>{cfg.label}</Text>
                </View>
                {/* Progress bar */}
                <View style={styles.progressBg}>
                  <View
                    style={[
                      styles.progressFill,
                      {
                        width: `${Math.round(pct * 100)}%` as `${number}%`,
                        backgroundColor: cfg.color,
                      },
                    ]}
                  />
                </View>
                <View style={styles.lineFooter}>
                  <Text style={styles.actualAmt}>{fmtEur(line.actual)}</Text>
                  <Text style={styles.budgetedAmt}>/ {fmtEur(line.budgeted)}</Text>
                  <Text
                    style={[
                      styles.variance,
                      { color: line.variance > 0 ? colors.danger : colors.primary },
                    ]}
                  >
                    {line.variance > 0 ? '+' : ''}
                    {fmtEur(line.variance)}
                  </Text>
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
  headerRow: { marginBottom: spacing.xs },
  title: { ...typography.h2, color: colors.textPrimary },
  periodNav: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.md,
    marginBottom: spacing.sm,
  },
  navBtn: {
    padding: spacing.xs,
    backgroundColor: colors.surface,
    borderRadius: 8,
    width: 36,
    alignItems: 'center',
  },
  navBtnText: { color: colors.primary, fontSize: 20, fontWeight: '700' },
  periodLabel: { ...typography.h3, color: colors.textPrimary, minWidth: 160, textAlign: 'center' },
  kpiRow: { flexDirection: 'row', gap: spacing.sm },
  kpiCard: { flex: 1, padding: spacing.md },
  kpiLabel: { ...typography.caption, color: colors.textMuted, marginBottom: 4 },
  kpiValue: { ...typography.h3, color: colors.textPrimary, fontWeight: '700' },
  lineCard: { padding: spacing.md },
  lineHeader: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: spacing.xs },
  category: {
    ...typography.body,
    color: colors.textPrimary,
    fontWeight: '600',
    textTransform: 'capitalize',
    flex: 1,
  },
  statusBadge: { ...typography.caption, fontWeight: '700' },
  progressBg: {
    height: 6,
    backgroundColor: colors.cardBorder,
    borderRadius: 3,
    overflow: 'hidden',
    marginBottom: spacing.xs,
  },
  progressFill: { height: '100%', borderRadius: 3 },
  lineFooter: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs },
  actualAmt: { ...typography.body, color: colors.textPrimary, fontWeight: '600' },
  budgetedAmt: { ...typography.caption, color: colors.textMuted },
  variance: { ...typography.caption, fontWeight: '600', marginLeft: 'auto' },
  empty: { textAlign: 'center', color: colors.textMuted, marginTop: spacing.xl },
})

export default BudgetScreen
