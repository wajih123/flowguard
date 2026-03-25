import React, { useState, useCallback } from 'react'
import { View, Text, ScrollView, StyleSheet, Keyboard } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import Slider from '@react-native-community/slider'
import { useScenario } from '../../hooks/useScenario'
import { ImpactChart } from './components/ImpactChart'
import { FlowGuardButton } from '../../components/FlowGuardButton'
import { FlowGuardLoader } from '../../components/FlowGuardLoader'
import { FlowGuardInput } from '../../components/FlowGuardInput'
import { colors, typography, spacing } from '../../theme'
import type { ScenarioType } from '../../domain/Scenario'

const SCENARIO_TYPES: { key: ScenarioType; label: string; icon: string }[] = [
  { key: 'NEW_EXPENSE', label: 'Nouvelle dépense', icon: '💸' },
  { key: 'DELAYED_INCOME', label: 'Revenu retardé', icon: '⏳' },
  { key: 'LOST_CLIENT', label: 'Perte de client', icon: '👋' },
  { key: 'NEW_HIRE', label: 'Nouvel employé', icon: '👤' },
  { key: 'INVESTMENT', label: 'Investissement', icon: '📊' },
]

interface ScenarioTemplate {
  label: string
  icon: string
  type: ScenarioType
  amount: string
  delayDays: number
  description: string
}

const FREELANCE_TEMPLATES: ScenarioTemplate[] = [
  {
    label: 'Je perds mon client principal',
    icon: '👋',
    type: 'LOST_CLIENT',
    amount: '3000',
    delayDays: 30,
    description: 'Perte du client principal (~3 000 €/mois)',
  },
  {
    label: 'Congé / sabbatique 2 mois',
    icon: '🏖️',
    type: 'NEW_EXPENSE',
    amount: '4000',
    delayDays: 60,
    description: "Impact d'une pause de 2 mois sur mes revenus",
  },
  {
    label: 'Je recrute un collaborateur',
    icon: '👥',
    type: 'NEW_HIRE',
    amount: '2500',
    delayDays: 30,
    description: "Coût mensuel d'un salarié ou sous-traitant",
  },
  {
    label: 'Une facture arrive en retard',
    icon: '⏱️',
    type: 'DELAYED_INCOME',
    amount: '5000',
    delayDays: 45,
    description: 'Retard de 45 jours sur une grosse facture',
  },
]

const DELAY_OPTIONS = [
  { days: 7, label: '7 jours' },
  { days: 14, label: '14 jours' },
  { days: 30, label: '30 jours' },
  { days: 60, label: '60 jours' },
  { days: 90, label: '90 jours' },
]

export const ScenarioScreen: React.FC = () => {
  const [scenarioType, setScenarioType] = useState<ScenarioType>('NEW_EXPENSE')
  const [amount, setAmount] = useState('')
  const [delayDays, setDelayDays] = useState(30)
  const [description, setDescription] = useState('')

  const { runScenario, result, isLoading: isPending } = useScenario()

  const applyTemplate = useCallback((tpl: ScenarioTemplate) => {
    setScenarioType(tpl.type)
    setAmount(tpl.amount)
    setDelayDays(tpl.delayDays)
    setDescription(tpl.description)
  }, [])

  const handleSimulate = useCallback(() => {
    Keyboard.dismiss()
    const parsedAmount = parseFloat(amount.replace(/\s/g, '').replace(',', '.'))
    if (isNaN(parsedAmount) || parsedAmount <= 0) {
      return
    }

    runScenario({
      type: scenarioType,
      amount: parsedAmount,
      delayDays,
      description: description || undefined,
    })
  }, [scenarioType, amount, delayDays, description, runScenario])

  const isValidAmount = () => {
    const parsed = parseFloat(amount.replace(/\s/g, '').replace(',', '.'))
    return !isNaN(parsed) && parsed > 0
  }

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView showsVerticalScrollIndicator={false} keyboardShouldPersistTaps="handled">
        <Text style={styles.title}>Simulateur What-If</Text>
        <Text style={styles.subtitle}>Simulez l'impact d'un événement sur votre trésorerie</Text>

        {/* Quick templates */}
        <Text style={styles.sectionTitle}>🚀 Templates rapides</Text>
        <View style={styles.templatesGrid}>
          {FREELANCE_TEMPLATES.map((tpl) => (
            <Text key={tpl.label} onPress={() => applyTemplate(tpl)} style={styles.templateChip}>
              {tpl.icon} {tpl.label}
            </Text>
          ))}
        </View>

        {/* Scenario Type Picker */}
        <Text style={styles.sectionTitle}>Type de scénario</Text>
        <View style={styles.typeGrid}>
          {SCENARIO_TYPES.map((st) => {
            const isActive = scenarioType === st.key
            return (
              <Text
                key={st.key}
                onPress={() => setScenarioType(st.key)}
                style={[styles.typeChip, isActive && styles.typeChipActive]}
              >
                {st.icon} {st.label}
              </Text>
            )
          })}
        </View>

        {/* Amount Input */}
        <FlowGuardInput
          label="Montant (€)"
          value={amount}
          onChangeText={setAmount}
          placeholder="ex: 5000"
          keyboardType="numeric"
        />

        {/* Delay Picker */}
        <Text style={styles.sectionTitle}>Horizon d'impact</Text>
        <View style={styles.delayRow}>
          <Slider
            style={styles.slider}
            minimumValue={7}
            maximumValue={90}
            step={1}
            value={delayDays}
            onValueChange={setDelayDays}
            minimumTrackTintColor={colors.primary}
            maximumTrackTintColor={colors.border}
            thumbTintColor={colors.primary}
          />
          <Text style={styles.delayLabel}>{delayDays} jours</Text>
        </View>
        <View style={styles.delayPresets}>
          {DELAY_OPTIONS.map((opt) => (
            <Text
              key={opt.days}
              onPress={() => setDelayDays(opt.days)}
              style={[styles.delayPreset, delayDays === opt.days && styles.delayPresetActive]}
            >
              {opt.label}
            </Text>
          ))}
        </View>

        {/* Description */}
        <FlowGuardInput
          label="Description (optionnel)"
          value={description}
          onChangeText={setDescription}
          placeholder="ex: Achat d'un serveur"
        />

        {/* Run Button */}
        <View style={styles.buttonWrapper}>
          <FlowGuardButton
            title="Lancer la simulation"
            onPress={handleSimulate}
            variant="primary"
            disabled={!isValidAmount() || isPending}
          />
        </View>

        {/* Loading */}
        {isPending && <FlowGuardLoader />}

        {/* Result */}
        {result && !isPending && (
          <View style={styles.resultSection}>
            <Text style={styles.sectionTitle}>Résultat de la simulation</Text>

            {result.riskLevel && (
              <View
                style={[
                  styles.riskBadge,
                  {
                    backgroundColor:
                      result.riskLevel === 'HIGH'
                        ? colors.danger + '20'
                        : result.riskLevel === 'MEDIUM'
                          ? colors.warning + '20'
                          : colors.success + '20',
                  },
                ]}
              >
                <Text
                  style={[
                    styles.riskText,
                    {
                      color:
                        result.riskLevel === 'HIGH'
                          ? colors.danger
                          : result.riskLevel === 'MEDIUM'
                            ? colors.warning
                            : colors.success,
                    },
                  ]}
                >
                  Risque{' '}
                  {result.riskLevel === 'HIGH'
                    ? 'élevé'
                    : result.riskLevel === 'MEDIUM'
                      ? 'modéré'
                      : 'faible'}
                </Text>
              </View>
            )}

            <ImpactChart result={result} />

            {result.recommendation && (
              <View style={styles.recommendationBox}>
                <Text style={styles.recommendationTitle}>💡 Recommandation IA</Text>
                <Text style={styles.recommendationText}>{result.recommendation}</Text>
              </View>
            )}
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
  },
  subtitle: {
    color: colors.textSecondary,
    fontSize: typography.body.fontSize,
    paddingHorizontal: spacing.md,
    marginBottom: spacing.lg,
  },
  sectionTitle: {
    ...typography.h3,
    color: colors.textPrimary,
    paddingHorizontal: spacing.md,
    marginBottom: spacing.sm,
    marginTop: spacing.md,
  },
  typeGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    paddingHorizontal: spacing.md,
    gap: spacing.sm,
    marginBottom: spacing.md,
  },
  typeChip: {
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    borderRadius: 20,
    backgroundColor: colors.surface,
    color: colors.textSecondary,
    fontSize: typography.caption.fontSize,
    fontWeight: '600',
    overflow: 'hidden',
  },
  typeChipActive: {
    backgroundColor: colors.primary + '30',
    color: colors.primary,
  },
  templatesGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    paddingHorizontal: spacing.md,
    gap: spacing.sm,
    marginBottom: spacing.md,
  },
  templateChip: {
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    borderRadius: 20,
    backgroundColor: colors.primary + '15',
    color: colors.primary,
    fontSize: typography.caption.fontSize,
    fontWeight: '600',
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: colors.primary + '30',
  },
  delayRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing.md,
    gap: spacing.sm,
  },
  slider: {
    flex: 1,
    height: 40,
  },
  delayLabel: {
    color: colors.primary,
    fontSize: typography.body.fontSize,
    fontWeight: '700',
    width: 70,
    textAlign: 'right',
  },
  delayPresets: {
    flexDirection: 'row',
    paddingHorizontal: spacing.md,
    gap: spacing.xs,
    marginBottom: spacing.md,
  },
  delayPreset: {
    paddingVertical: spacing.xs,
    paddingHorizontal: spacing.sm,
    borderRadius: 14,
    backgroundColor: colors.surface,
    color: colors.textMuted,
    fontSize: 12,
    overflow: 'hidden',
  },
  delayPresetActive: {
    backgroundColor: colors.primary + '20',
    color: colors.primary,
  },
  buttonWrapper: {
    paddingHorizontal: spacing.md,
    marginTop: spacing.md,
  },
  resultSection: {
    marginTop: spacing.lg,
    paddingHorizontal: spacing.md,
  },
  riskBadge: {
    alignSelf: 'flex-start',
    paddingVertical: spacing.xs,
    paddingHorizontal: spacing.md,
    borderRadius: 16,
    marginBottom: spacing.md,
  },
  riskText: {
    fontWeight: '700',
    fontSize: typography.body.fontSize,
  },
  recommendationBox: {
    backgroundColor: colors.surface,
    borderRadius: 12,
    padding: spacing.md,
    marginTop: spacing.md,
    borderLeftWidth: 3,
    borderLeftColor: colors.primary,
  },
  recommendationTitle: {
    color: colors.textPrimary,
    fontSize: typography.body.fontSize,
    fontWeight: '700',
    marginBottom: spacing.xs,
  },
  recommendationText: {
    color: colors.textSecondary,
    fontSize: typography.body.fontSize,
    lineHeight: 22,
  },
  bottomSpacer: {
    height: spacing.xxl,
  },
})
