import React, { useState } from 'react'
import { ScrollView, View, Text, StyleSheet } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { HorizonSelector } from './components/HorizonSelector'
import { ConfidenceBadge } from './components/ConfidenceBadge'
import { ForecastChart } from './components/ForecastChart'
import { FlowGuardCard } from '../../components/FlowGuardCard'
import { FlowGuardLoader } from '../../components/FlowGuardLoader'
import { ErrorScreen } from '../../components/ErrorScreen'
import { useForecast } from '../../hooks/useForecast'
import { useAccountStore } from '../../store/accountStore'
import { colors, typography, spacing } from '../../theme'
import { format, parseISO } from 'date-fns'
import { fr } from 'date-fns/locale'

export const ForecastScreen: React.FC = () => {
  const account = useAccountStore((s) => s.account)
  const [horizon, setHorizon] = useState(30)

  const { forecast, isLoading, isError, refetch } = useForecast(account?.id, horizon)

  if (isLoading) {
    return <FlowGuardLoader message="Calcul des prévisions en cours..." />
  }

  if (isError) {
    return (
      <ErrorScreen
        message="Impossible de charger les prévisions"
        onRetry={() => refetch()}
      />
    )
  }

  if (!forecast) {
    return <FlowGuardLoader message="Chargement..." />
  }

  const formatAmount = (val: number) =>
    new Intl.NumberFormat('fr-FR', {
      style: 'currency',
      currency: 'EUR',
      minimumFractionDigits: 0,
    }).format(val)

  return (
    <SafeAreaView style={styles.safeArea}>
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        <Text style={styles.title}>Prévisions de trésorerie</Text>

        <HorizonSelector selected={horizon} onSelect={setHorizon} />
        <ConfidenceBadge confidence={forecast.confidence} />

        <ForecastChart
          predictions={forecast.predictions}
          criticalPoints={forecast.criticalPoints}
        />

        {forecast.criticalPoints.length > 0 && (
          <View style={styles.criticalSection}>
            <Text style={styles.sectionTitle}>POINTS CRITIQUES</Text>
            {forecast.criticalPoints.map((cp) => {
              let dateLabel = cp.date
              try {
                dateLabel = format(parseISO(cp.date), 'dd MMMM yyyy', { locale: fr })
              } catch {
                dateLabel = cp.date
              }

              return (
                <FlowGuardCard key={cp.date} style={styles.criticalCard}>
                  <Text style={styles.criticalDate}>{dateLabel}</Text>
                  <Text style={styles.criticalDeficit}>
                    Déficit prévu: {formatAmount(cp.projectedBalance)}
                  </Text>
                  <View style={styles.badges}>
                    <View
                      style={[
                        styles.urgencyBadge,
                        {
                          backgroundColor:
                            cp.urgency === 'IMMINENT'
                              ? colors.danger + '30'
                              : colors.warning + '30',
                        },
                      ]}
                    >
                      <Text
                        style={[
                          styles.urgencyText,
                          {
                            color:
                              cp.urgency === 'IMMINENT' ? colors.danger : colors.warning,
                          },
                        ]}
                      >
                        {cp.urgency === 'IMMINENT' ? 'IMMINENT' : 'À VENIR'}
                      </Text>
                    </View>
                    <Text style={styles.daysUntil}>
                      Dans {cp.daysUntil} jours
                    </Text>
                  </View>
                </FlowGuardCard>
              )
            })}
          </View>
        )}
      </ScrollView>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: colors.background,
  },
  container: {
    flex: 1,
  },
  content: {
    padding: spacing.md,
    paddingBottom: spacing.xxl,
  },
  title: {
    color: colors.textPrimary,
    fontSize: typography.h2.fontSize,
    fontWeight: typography.h2.fontWeight,
    marginBottom: spacing.lg,
  },
  criticalSection: {
    marginTop: spacing.md,
  },
  sectionTitle: {
    color: colors.textMuted,
    fontSize: typography.label.fontSize,
    fontWeight: typography.label.fontWeight,
    letterSpacing: typography.label.letterSpacing,
    marginBottom: spacing.sm,
  },
  criticalCard: {
    marginBottom: spacing.sm,
  },
  criticalDate: {
    color: colors.textPrimary,
    fontSize: typography.h3.fontSize,
    fontWeight: typography.h3.fontWeight,
    marginBottom: spacing.xs,
  },
  criticalDeficit: {
    color: colors.danger,
    fontSize: typography.body.fontSize,
    fontWeight: '600',
    marginBottom: spacing.sm,
  },
  badges: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
  },
  urgencyBadge: {
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
    borderRadius: 8,
  },
  urgencyText: {
    fontSize: typography.caption.fontSize,
    fontWeight: '700',
  },
  daysUntil: {
    color: colors.textSecondary,
    fontSize: typography.caption.fontSize,
  },
})
