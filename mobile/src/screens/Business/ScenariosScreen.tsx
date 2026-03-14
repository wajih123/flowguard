import React, { useState, useCallback } from 'react'
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  StyleSheet,
  TextInput,
  KeyboardAvoidingView,
  Platform,
} from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import type { StackScreenProps } from '@react-navigation/stack'
import { FlowGuardCard } from '../../components/FlowGuardCard'
import { FlowGuardButton } from '../../components/FlowGuardButton'
import { useScenario } from '../../hooks/useScenario'
import { useAccountStore } from '../../store/accountStore'
import { Routes } from '../../navigation/routes'
import { colors, typography, spacing } from '../../theme'
import type { ScenarioType } from '../../domain/Scenario'

type Props = StackScreenProps<Record<string, undefined>, typeof Routes.Scenarios>

interface ScenarioConfig {
  type: ScenarioType
  label: string
  icon: string
  description: string
  amountLabel: string
  delayLabel: string
}

const SCENARIOS: ScenarioConfig[] = [
  {
    type: 'LATE_PAYMENT',
    label: 'Retard de paiement',
    icon: '⏰',
    description: "Simuler le retard d'un paiement client",
    amountLabel: 'Montant du paiement (€)',
    delayLabel: 'Retard (jours)',
  },
  {
    type: 'EXTRA_EXPENSE',
    label: 'Dépense imprévue',
    icon: '💸',
    description: 'Simuler une dépense exceptionnelle',
    amountLabel: 'Montant de la dépense (€)',
    delayLabel: 'Délai avant la dépense (jours)',
  },
  {
    type: 'EARLY_INVOICE',
    label: 'Facture anticipée',
    icon: '📄',
    description: "Simuler l'encaissement anticipé d'une facture",
    amountLabel: 'Montant de la facture (€)',
    delayLabel: 'Anticipation (jours)',
  },
]

const fmtEur = (val: number) =>
  new Intl.NumberFormat('fr-FR', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 0,
  }).format(val)

export const ScenariosScreen: React.FC<Props> = () => {
  const account = useAccountStore((s) => s.account)
  const [selectedType, setSelectedType] = useState<ScenarioType>('LATE_PAYMENT')
  const [amount, setAmount] = useState('')
  const [delayDays, setDelayDays] = useState('')
  const { runScenario, isLoading, result, reset } = useScenario()

  const selectedConfig = SCENARIOS.find((s) => s.type === selectedType)!

  const handleRun = useCallback(() => {
    const parsedAmount = parseFloat(amount.replace(',', '.'))
    const parsedDelay = parseInt(delayDays, 10)
    if (
      !account?.id ||
      isNaN(parsedAmount) ||
      isNaN(parsedDelay) ||
      parsedAmount <= 0 ||
      parsedDelay < 0
    )
      return
    runScenario({
      accountId: account.id,
      type: selectedType,
      amount: parsedAmount,
      delayDays: parsedDelay,
    })
  }, [account, selectedType, amount, delayDays, runScenario])

  const handleReset = useCallback(() => {
    reset()
    setAmount('')
    setDelayDays('')
  }, [reset])

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        keyboardVerticalOffset={100}
      >
        <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
          <Text style={styles.title}>Scénarios</Text>
          <Text style={styles.subtitle}>
            Simulez l'impact de différents événements sur votre trésorerie.
          </Text>

          {/* Scenario type picker */}
          <View style={styles.scenarioRow}>
            {SCENARIOS.map((s) => (
              <TouchableOpacity
                key={s.type}
                onPress={() => {
                  setSelectedType(s.type)
                  reset()
                }}
                style={[styles.scenarioChip, selectedType === s.type && styles.scenarioChipActive]}
              >
                <Text style={styles.scenarioIcon}>{s.icon}</Text>
                <Text
                  style={[
                    styles.scenarioLabel,
                    selectedType === s.type && styles.scenarioLabelActive,
                  ]}
                >
                  {s.label}
                </Text>
              </TouchableOpacity>
            ))}
          </View>

          <FlowGuardCard variant="info" style={styles.descCard}>
            <Text style={styles.descText}>{selectedConfig.description}</Text>
          </FlowGuardCard>

          {/* Inputs */}
          <Text style={styles.inputLabel}>{selectedConfig.amountLabel}</Text>
          <TextInput
            style={styles.input}
            value={amount}
            onChangeText={setAmount}
            keyboardType="decimal-pad"
            placeholder="ex : 5000"
            placeholderTextColor={colors.textMuted}
          />

          <Text style={styles.inputLabel}>{selectedConfig.delayLabel}</Text>
          <TextInput
            style={styles.input}
            value={delayDays}
            onChangeText={setDelayDays}
            keyboardType="number-pad"
            placeholder="ex : 15"
            placeholderTextColor={colors.textMuted}
          />

          <FlowGuardButton
            label="Simuler"
            onPress={handleRun}
            variant="primary"
            loading={isLoading}
            style={styles.runBtn}
          />

          {/* Result */}
          {result && (
            <>
              <Text style={styles.sectionTitle}>Résultat de la simulation</Text>
              <FlowGuardCard
                variant={result.worstDeficit < 0 ? 'alert' : 'success'}
                style={styles.resultCard}
              >
                <Text style={styles.resultDesc}>{result.description}</Text>
                <View style={styles.resultRow}>
                  <Text style={styles.resultLabel}>Impact maximal</Text>
                  <Text
                    style={[
                      styles.resultVal,
                      { color: result.worstDeficit < 0 ? colors.danger : colors.success },
                    ]}
                  >
                    {fmtEur(result.worstDeficit)}
                  </Text>
                </View>
                <View style={styles.resultRow}>
                  <Text style={styles.resultLabel}>Délai d'impact</Text>
                  <Text style={styles.resultVal}>{result.daysUntilImpact} jour(s)</Text>
                </View>
              </FlowGuardCard>

              <FlowGuardCard variant="info" style={styles.recCard}>
                <Text style={styles.recLabel}>Recommandation</Text>
                <Text style={styles.recText}>{result.recommendedAction}</Text>
              </FlowGuardCard>

              <FlowGuardButton
                label="Nouvelle simulation"
                onPress={handleReset}
                variant="outline"
                style={styles.resetBtn}
              />
            </>
          )}
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  flex: { flex: 1 },
  scroll: { padding: spacing.md, paddingBottom: spacing.xxl },
  title: { ...typography.h2, color: colors.textPrimary, marginBottom: spacing.xs },
  subtitle: { color: colors.textSecondary, ...typography.body, marginBottom: spacing.md },
  scenarioRow: { flexDirection: 'row', gap: spacing.sm, marginBottom: spacing.md },
  scenarioChip: {
    flex: 1,
    backgroundColor: colors.surface,
    borderRadius: 12,
    padding: spacing.sm,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: colors.cardBorder,
  },
  scenarioChipActive: { borderColor: colors.primary, backgroundColor: colors.primary + '22' },
  scenarioIcon: { fontSize: 24, marginBottom: 4 },
  scenarioLabel: { ...typography.caption, color: colors.textSecondary, textAlign: 'center' },
  scenarioLabelActive: { color: colors.primary, fontWeight: '700' },
  descCard: { marginBottom: spacing.md },
  descText: { color: colors.textPrimary, ...typography.body },
  inputLabel: {
    color: colors.textSecondary,
    ...typography.caption,
    marginBottom: 4,
    marginTop: spacing.sm,
  },
  input: {
    backgroundColor: colors.surface,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    color: colors.textPrimary,
    ...typography.body,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    marginBottom: spacing.xs,
  },
  runBtn: { marginTop: spacing.md },
  sectionTitle: {
    ...typography.h3,
    color: colors.textPrimary,
    marginTop: spacing.xl,
    marginBottom: spacing.sm,
  },
  resultCard: { marginBottom: spacing.sm },
  resultDesc: { color: colors.textPrimary, ...typography.body, marginBottom: spacing.sm },
  resultRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 4 },
  resultLabel: { color: colors.textSecondary, ...typography.body },
  resultVal: { color: colors.textPrimary, ...typography.body, fontWeight: '700' },
  recCard: { marginBottom: spacing.md },
  recLabel: { ...typography.caption, color: colors.textSecondary, marginBottom: 4 },
  recText: { color: colors.textPrimary, ...typography.body },
  resetBtn: {},
})
