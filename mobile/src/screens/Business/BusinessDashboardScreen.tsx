import React, { useCallback, useState } from 'react'
import { ScrollView, View, Text, RefreshControl, StyleSheet, TouchableOpacity } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import type { StackScreenProps } from '@react-navigation/stack'
import { FlowGuardCard } from '../../components/FlowGuardCard'
import { FlowGuardLoader } from '../../components/FlowGuardLoader'
import { ErrorScreen } from '../../components/ErrorScreen'
import { SkeletonCard } from '../../components/SkeletonCard'
import { HealthScoreGauge } from '../../components/HealthScoreGauge'
import { SeverityBadge } from '../../components/SeverityBadge'
import { AlertCard } from '../Alerts/components/AlertCard'
import { useForecast } from '../../hooks/useForecast'
import { useAlerts } from '../../hooks/useAlerts'
import { useBankStore } from '../../store/bankStore'
import { useAccountStore } from '../../store/accountStore'
import { useAuthStore } from '../../store/authStore'
import { Routes } from '../../navigation/routes'
import { colors, typography, spacing } from '../../theme'
import type { BankAccount } from '../../domain/Account'
import { format } from 'date-fns'
import { fr } from 'date-fns/locale'

type Props = StackScreenProps<Record<string, undefined>, typeof Routes.BusinessDashboard>

const HORIZONS = [
  { label: '30j', value: 30 },
  { label: '60j', value: 60 },
  { label: '90j', value: 90 },
]

const fmtEur = (val: number) =>
  new Intl.NumberFormat('fr-FR', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 0,
  }).format(val)

export const BusinessDashboardScreen: React.FC<Props> = ({ navigation }) => {
  const account = useAccountStore((s) => s.account)
  const accounts = useBankStore((s) => s.accounts)
  const user = useAuthStore((s) => s.user)
  const [horizon, setHorizon] = useState(90)
  const [refreshing, setRefreshing] = useState(false)

  const {
    forecast,
    isLoading: forecastLoading,
    isError: forecastError,
    refetch,
  } = useForecast(account?.id, horizon)
  const { criticalAlerts, markAsRead } = useAlerts(account?.id)

  const onRefresh = useCallback(async () => {
    setRefreshing(true)
    await refetch()
    setRefreshing(false)
  }, [refetch])

  if (forecastError) {
    return <ErrorScreen message="Impossible de charger le tableau de bord" onRetry={refetch} />
  }

  const currentAccount = accounts[0] ?? account

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView
        contentContainerStyle={styles.scroll}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            tintColor={colors.primary}
            colors={[colors.primary]}
          />
        }
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.header}>
          <View>
            <Text style={styles.greeting}>Bonjour {user?.firstName ?? ''} 👋</Text>
            <Text style={styles.subGreeting}>Tableau de bord entreprise</Text>
          </View>
          <TouchableOpacity onPress={() => navigation.navigate(Routes.BankAccount as never)}>
            <Text style={styles.manageAccounts}>Comptes →</Text>
          </TouchableOpacity>
        </View>

        {/* Multi-account KPI row */}
        {accounts.length > 0 && (
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            style={styles.accountsScroll}
          >
            {accounts.map((acc: BankAccount) => (
              <FlowGuardCard
                key={acc.id}
                style={[styles.accountChip, account?.id === acc.id && styles.accountChipActive]}
              >
                <Text style={styles.accountChipBank}>{acc.bankName ?? 'Banque'}</Text>
                <Text style={styles.accountChipBalance}>{fmtEur(acc.balance)}</Text>
              </FlowGuardCard>
            ))}
          </ScrollView>
        )}

        {/* Balance + Health score */}
        <View style={styles.kpiRow}>
          <FlowGuardCard style={styles.kpiCard}>
            <Text style={styles.kpiLabel}>Solde actuel</Text>
            <Text style={styles.kpiValue}>{fmtEur(currentAccount?.balance ?? 0)}</Text>
          </FlowGuardCard>
          <FlowGuardCard style={styles.kpiCard}>
            <Text style={styles.kpiLabel}>Score santé</Text>
            <HealthScoreGauge score={forecast?.healthScore ?? 0} size={60} />
          </FlowGuardCard>
        </View>

        {/* Horizon selector */}
        <View style={styles.horizonRow}>
          {HORIZONS.map((h) => (
            <TouchableOpacity
              key={h.value}
              onPress={() => setHorizon(h.value)}
              style={[styles.horizonBtn, horizon === h.value && styles.horizonBtnActive]}
            >
              <Text style={[styles.horizonText, horizon === h.value && styles.horizonTextActive]}>
                {h.label}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        {/* Forecast summary */}
        {forecastLoading ? (
          <SkeletonCard />
        ) : forecast ? (
          <FlowGuardCard style={styles.forecastCard}>
            <Text style={styles.forecastTitle}>Prévision {horizon} jours</Text>
            <View style={styles.forecastRow}>
              <View>
                <Text style={styles.forecastLabel}>Points critiques</Text>
                <Text
                  style={[
                    styles.forecastVal,
                    { color: forecast.criticalPoints.length > 0 ? colors.danger : colors.success },
                  ]}
                >
                  {forecast.criticalPoints.length}
                </Text>
              </View>
              <View>
                <Text style={styles.forecastLabel}>Confiance IA</Text>
                <Text style={[styles.forecastVal, { color: colors.primary }]}>
                  {Math.round(forecast.confidence * 100)} %
                </Text>
              </View>
              {forecast.criticalPoints[0] && (
                <View>
                  <Text style={styles.forecastLabel}>Prochain point</Text>
                  <Text style={[styles.forecastVal, { color: colors.warning }]}>
                    {(() => {
                      try {
                        return format(new Date(forecast.criticalPoints[0].date), 'dd/MM', {
                          locale: fr,
                        })
                      } catch {
                        return '—'
                      }
                    })()}
                  </Text>
                </View>
              )}
            </View>
            <TouchableOpacity onPress={() => navigation.navigate(Routes.Predictions as never)}>
              <Text style={styles.viewDetails}>Voir les prévisions détaillées →</Text>
            </TouchableOpacity>
          </FlowGuardCard>
        ) : null}

        {/* Critical alerts */}
        {criticalAlerts.length > 0 && (
          <>
            <Text style={styles.sectionTitle}>Alertes critiques</Text>
            {criticalAlerts.slice(0, 3).map((alert) => (
              <AlertCard key={alert.id} alert={alert} onMarkRead={() => markAsRead(alert.id)} />
            ))}
          </>
        )}

        {/* Quick actions */}
        <Text style={styles.sectionTitle}>Actions rapides</Text>
        <View style={styles.quickRow}>
          <TouchableOpacity
            style={styles.quickBtn}
            onPress={() => navigation.navigate(Routes.Scenarios as never)}
          >
            <Text style={styles.quickBtnIcon}>🔀</Text>
            <Text style={styles.quickBtnLabel}>Scénarios</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.quickBtn}
            onPress={() => navigation.navigate(Routes.Transactions as never)}
          >
            <Text style={styles.quickBtnIcon}>💳</Text>
            <Text style={styles.quickBtnLabel}>Transactions</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.quickBtn}
            onPress={() => navigation.navigate(Routes.Subscription as never)}
          >
            <Text style={styles.quickBtnIcon}>⭐</Text>
            <Text style={styles.quickBtnLabel}>Abonnement</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  scroll: { padding: spacing.md, paddingBottom: spacing.xxl },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: spacing.md,
  },
  greeting: { ...typography.h2, color: colors.textPrimary },
  subGreeting: { ...typography.caption, color: colors.textSecondary },
  manageAccounts: { color: colors.primary, ...typography.body, fontWeight: '600' },
  accountsScroll: { marginBottom: spacing.md },
  accountChip: { marginRight: spacing.sm, minWidth: 120, paddingVertical: spacing.sm },
  accountChipActive: { borderColor: colors.primary, borderWidth: 1 },
  accountChipBank: { ...typography.caption, color: colors.textSecondary },
  accountChipBalance: { ...typography.h3, color: colors.textPrimary },
  kpiRow: { flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.md },
  kpiCard: { flex: 1, alignItems: 'center' },
  kpiLabel: { ...typography.caption, color: colors.textSecondary, marginBottom: spacing.xs },
  kpiValue: { ...typography.h2, color: colors.primary },
  horizonRow: { flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.md },
  horizonBtn: {
    borderRadius: 20,
    paddingHorizontal: spacing.md,
    paddingVertical: 6,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.cardBorder,
  },
  horizonBtnActive: { borderColor: colors.primary, backgroundColor: colors.primary + '22' },
  horizonText: { ...typography.caption, color: colors.textSecondary, fontWeight: '600' },
  horizonTextActive: { color: colors.primary },
  forecastCard: { marginBottom: spacing.md },
  forecastTitle: { ...typography.h3, color: colors.textPrimary, marginBottom: spacing.sm },
  forecastRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: spacing.sm },
  forecastLabel: { ...typography.caption, color: colors.textSecondary },
  forecastVal: { ...typography.h3 },
  viewDetails: { color: colors.primary, ...typography.body, fontWeight: '700' },
  sectionTitle: {
    ...typography.h3,
    color: colors.textPrimary,
    marginBottom: spacing.sm,
    marginTop: spacing.md,
  },
  quickRow: { flexDirection: 'row', gap: spacing.sm },
  quickBtn: {
    flex: 1,
    backgroundColor: colors.surface,
    borderRadius: 12,
    padding: spacing.md,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: colors.cardBorder,
  },
  quickBtnIcon: { fontSize: 28, marginBottom: 4 },
  quickBtnLabel: { ...typography.caption, color: colors.textSecondary, textAlign: 'center' },
})
