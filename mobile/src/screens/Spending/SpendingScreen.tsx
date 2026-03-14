import React, { useState } from 'react'
import { View, Text, ScrollView, StyleSheet, RefreshControl } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { useSpending } from '../../hooks/useSpending'
import { DonutChart } from './components/DonutChart'
import { InsightCard } from './components/InsightCard'
import { FlowGuardLoader } from '../../components/FlowGuardLoader'
import { ErrorScreen } from '../../components/ErrorScreen'
import { EmptyState } from '../../components/EmptyState'
import { colors, typography, spacing } from '../../theme'

type Period = '7j' | '30j' | '90j'

const PERIOD_OPTIONS: { key: Period; label: string }[] = [
  { key: '7j', label: '7 jours' },
  { key: '30j', label: '30 jours' },
  { key: '90j', label: '90 jours' },
]

export const SpendingScreen: React.FC = () => {
  const [period, setPeriod] = useState<Period>('30j')
  const { data, isLoading, isError, refetch } = useSpending(period)

  if (isLoading) return <FlowGuardLoader />
  if (isError) return <ErrorScreen message="Impossible d'analyser les dépenses" onRetry={refetch} />
  if (!data) return <EmptyState icon="chart-donut" title="Aucune dépense" subtitle="Pas de transactions sur la période" />

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl refreshing={false} onRefresh={() => refetch()} tintColor={colors.primary} />
        }
      >
        <Text style={styles.title}>Analyse des dépenses</Text>

        <View style={styles.periodSelector}>
          {PERIOD_OPTIONS.map((opt) => {
            const isActive = period === opt.key
            return (
              <Text
                key={opt.key}
                onPress={() => setPeriod(opt.key)}
                style={[styles.periodOption, isActive && styles.periodOptionActive]}
              >
                {opt.label}
              </Text>
            )
          })}
        </View>

        <View style={styles.chartSection}>
          <DonutChart categories={data.categories} totalAmount={data.totalAmount} />
        </View>

        {data.aiInsights && data.aiInsights.length > 0 && (
          <View style={styles.insightsSection}>
            <Text style={styles.sectionTitle}>Insights IA</Text>
            {data.aiInsights.map((insight, index) => (
              <InsightCard
                key={index}
                icon={insight.type === 'positive' ? '📈' : insight.type === 'negative' ? '📉' : '💡'}
                text={insight.text}
                type={insight.type}
              />
            ))}
          </View>
        )}

        {data.topMerchants && data.topMerchants.length > 0 && (
          <View style={styles.merchantsSection}>
            <Text style={styles.sectionTitle}>Top commerçants</Text>
            {data.topMerchants.map((merchant) => {
              const formattedAmount = new Intl.NumberFormat('fr-FR', {
                style: 'currency',
                currency: 'EUR',
                minimumFractionDigits: 0,
              }).format(merchant.totalAmount)

              return (
                <View key={merchant.name} style={styles.merchantRow}>
                  <Text style={styles.merchantName}>{merchant.name}</Text>
                  <Text style={styles.merchantCount}>{merchant.transactionCount} opérations</Text>
                  <Text style={styles.merchantAmount}>{formattedAmount}</Text>
                </View>
              )
            })}
          </View>
        )}

        <View style={styles.bottomSpacer} />
      </ScrollView>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  title: {
    ...typography.h1,
    color: colors.textPrimary,
    paddingHorizontal: spacing.md,
    paddingTop: spacing.md,
    marginBottom: spacing.md,
  },
  periodSelector: {
    flexDirection: 'row',
    paddingHorizontal: spacing.md,
    gap: spacing.sm,
    marginBottom: spacing.lg,
  },
  periodOption: {
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    borderRadius: 20,
    backgroundColor: colors.surface,
    color: colors.textSecondary,
    fontSize: typography.caption.fontSize,
    fontWeight: '600',
    overflow: 'hidden',
  },
  periodOptionActive: {
    backgroundColor: colors.primary + '30',
    color: colors.primary,
  },
  chartSection: {
    paddingHorizontal: spacing.md,
    marginBottom: spacing.lg,
  },
  insightsSection: {
    paddingHorizontal: spacing.md,
    marginBottom: spacing.lg,
  },
  sectionTitle: {
    ...typography.h3,
    color: colors.textPrimary,
    marginBottom: spacing.sm,
  },
  merchantsSection: {
    paddingHorizontal: spacing.md,
  },
  merchantRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: spacing.sm,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  merchantName: {
    flex: 1,
    color: colors.textPrimary,
    fontSize: typography.body.fontSize,
    fontWeight: '600',
  },
  merchantCount: {
    color: colors.textMuted,
    fontSize: typography.caption.fontSize,
    marginRight: spacing.md,
  },
  merchantAmount: {
    color: colors.textPrimary,
    fontSize: typography.body.fontSize,
    fontWeight: '700',
    width: 80,
    textAlign: 'right',
  },
  bottomSpacer: {
    height: spacing.xxl,
  },
})
