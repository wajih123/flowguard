import React, { useState, useCallback } from 'react'
import { View, Text, ScrollView, StyleSheet, Keyboard } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import Slider from '@react-native-community/slider'
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withSpring,
  withSequence,
  withTiming,
  runOnJS,
} from 'react-native-reanimated'
import { useFlashCredit } from '../../hooks/useFlashCredit'
import { useAccountStore } from '../../store/accountStore'
import { FlowGuardButton } from '../../components/FlowGuardButton'
import { FlowGuardCard } from '../../components/FlowGuardCard'
import { FlowGuardLoader } from '../../components/FlowGuardLoader'
import { colors, typography, spacing } from '../../theme'

const PURPOSE_OPTIONS = [
  { key: 'SALARY', label: '💼 Salaires' },
  { key: 'SUPPLIER', label: '📦 Fournisseur' },
  { key: 'TAX', label: '🏛️ Cotisations / Impôts' },
  { key: 'RENT', label: '🏢 Loyer' },
  { key: 'EMERGENCY', label: '🚨 Urgence' },
  { key: 'OTHER', label: '📋 Autre' },
]

const MIN_AMOUNT = 500
const MAX_AMOUNT = 10000
const STEP = 100

// ── Pre-qualification explainer shown before the credit form ─────────────────
const FlashCreditExplainer: React.FC<{ onContinue: () => void }> = ({ onContinue }) => (
  <SafeAreaView style={styles.container} edges={['top']}>
    <ScrollView showsVerticalScrollIndicator={false}>
      <Text style={styles.title}>Crédit Flash</Text>
      <Text style={styles.subtitle}>Quelques infos avant de commencer</Text>

      {[
        {
          icon: '⚡',
          title: 'Décision en 2 minutes',
          body: 'Notre algorithme analyse vos flux en temps réel. Aucun dossier papier, aucune attente.',
        },
        {
          icon: '🔒',
          title: "Pas d'impact sur votre score bancaire",
          body: "Nous n'effectuons pas de consultation en bureau de crédit. Votre historique bancaire externe n'est pas touché.",
        },
        {
          icon: '💰',
          title: 'Frais transparents : 1,5 % flat',
          body: 'Un seul frais fixe par opération, aucun frais caché, aucun intérêt composé. Ex : 3 000 € → 45 € de frais.',
        },
        {
          icon: '📅',
          title: 'Remboursement sous 30 jours',
          body: 'Le montant + frais est prélevé en une seule fois à J+30 sur votre compte connecté.',
        },
        {
          icon: '✅',
          title: "Critères d'éligibilité",
          body: '• Compte connecté depuis ≥ 30 jours\n• ≥ 5 transactions bancaires\n• Pas de crédit flash ouvert en cours\n• Solde prévu positif à J+7',
        },
      ].map((item) => (
        <FlowGuardCard key={item.icon} style={styles.explainerCard}>
          <View style={styles.explainerRow}>
            <Text style={styles.explainerIcon}>{item.icon}</Text>
            <View style={styles.explainerContent}>
              <Text style={styles.explainerTitle}>{item.title}</Text>
              <Text style={styles.explainerBody}>{item.body}</Text>
            </View>
          </View>
        </FlowGuardCard>
      ))}

      <View style={styles.buttonWrapper}>
        <FlowGuardButton title="Faire une demande" onPress={onContinue} variant="primary" />
      </View>
      <View style={styles.bottomSpacer} />
    </ScrollView>
  </SafeAreaView>
)

export const FlashCreditScreen: React.FC = () => {
  const [step, setStep] = useState<'EXPLAINER' | 'FORM'>('EXPLAINER')
  const [amount, setAmount] = useState(2000)
  const [purpose, setPurpose] = useState('SALARY')
  const [showSuccess, setShowSuccess] = useState(false)

  // All hooks must be called unconditionally, before any early return
  const account = useAccountStore((s) => s.account)
  const { requestCredit, isLoading: isPending } = useFlashCredit()

  const successScale = useSharedValue(0)
  const successOpacity = useSharedValue(0)

  const successAnimatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: successScale.value }],
    opacity: successOpacity.value,
  }))

  const playSuccessAnimation = useCallback(() => {
    setShowSuccess(true)
    successOpacity.value = withTiming(1, { duration: 200 })
    successScale.value = withSequence(
      withSpring(1.2, { damping: 8 }),
      withSpring(1, { damping: 12 }),
    )
  }, [successOpacity, successScale])

  const handleRequest = useCallback(() => {
    Keyboard.dismiss()
    requestCredit(
      { amount, purpose, accountId: account?.id ?? '' },
      {
        onSuccess: () => {
          runOnJS(playSuccessAnimation)()
        },
      },
    )
  }, [amount, purpose, account, requestCredit, playSuccessAnimation])

  if (step === 'EXPLAINER') {
    return <FlashCreditExplainer onContinue={() => setStep('FORM')} />
  }

  const formattedAmount = new Intl.NumberFormat('fr-FR', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 0,
  }).format(amount)

  const estimatedFee = amount * 0.015
  const formattedFee = new Intl.NumberFormat('fr-FR', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 2,
  }).format(estimatedFee)

  if (showSuccess) {
    return (
      <SafeAreaView style={styles.container} edges={['top']}>
        <View style={styles.successContainer}>
          <Animated.View style={[styles.successCircle, successAnimatedStyle]}>
            <Text style={styles.successIcon}>✅</Text>
          </Animated.View>
          <Text style={styles.successTitle}>Crédit flash accordé !</Text>
          <Text style={styles.successAmount}>{formattedAmount}</Text>
          <Text style={styles.successSubtitle}>Les fonds seront disponibles sous 2 minutes</Text>
          <View style={styles.successButtonWrapper}>
            <FlowGuardButton
              title="Retour au tableau de bord"
              onPress={() => setShowSuccess(false)}
              variant="outline"
            />
          </View>
        </View>
      </SafeAreaView>
    )
  }

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView showsVerticalScrollIndicator={false} keyboardShouldPersistTaps="handled">
        <Text style={styles.title}>Crédit Flash</Text>
        <Text style={styles.subtitle}>
          Micro-crédit instantané pour couvrir un besoin de trésorerie
        </Text>

        {/* Amount Selector */}
        <FlowGuardCard style={styles.amountCard}>
          <Text style={styles.amountLabel}>Montant souhaité</Text>
          <Text style={styles.amountValue}>{formattedAmount}</Text>
          <Slider
            style={styles.slider}
            minimumValue={MIN_AMOUNT}
            maximumValue={MAX_AMOUNT}
            step={STEP}
            value={amount}
            onValueChange={setAmount}
            minimumTrackTintColor={colors.primary}
            maximumTrackTintColor={colors.border}
            thumbTintColor={colors.primary}
          />
          <View style={styles.rangeRow}>
            <Text style={styles.rangeText}>
              {new Intl.NumberFormat('fr-FR', {
                style: 'currency',
                currency: 'EUR',
                minimumFractionDigits: 0,
              }).format(MIN_AMOUNT)}
            </Text>
            <Text style={styles.rangeText}>
              {new Intl.NumberFormat('fr-FR', {
                style: 'currency',
                currency: 'EUR',
                minimumFractionDigits: 0,
              }).format(MAX_AMOUNT)}
            </Text>
          </View>
        </FlowGuardCard>

        {/* Purpose Selector */}
        <Text style={styles.sectionTitle}>Motif du crédit</Text>
        <View style={styles.purposeGrid}>
          {PURPOSE_OPTIONS.map((opt) => {
            const isActive = purpose === opt.key
            return (
              <Text
                key={opt.key}
                onPress={() => setPurpose(opt.key)}
                style={[styles.purposeChip, isActive && styles.purposeChipActive]}
              >
                {opt.label}
              </Text>
            )
          })}
        </View>

        {/* Cost Summary */}
        <FlowGuardCard style={styles.summaryCard}>
          <View style={styles.summaryRow}>
            <Text style={styles.summaryLabel}>Montant emprunté</Text>
            <Text style={styles.summaryValue}>{formattedAmount}</Text>
          </View>
          <View style={styles.summaryRow}>
            <Text style={styles.summaryLabel}>Frais (1,5%)</Text>
            <Text style={styles.summaryValue}>{formattedFee}</Text>
          </View>
          <View style={[styles.summaryRow, styles.totalRow]}>
            <Text style={styles.totalLabel}>Total à rembourser</Text>
            <Text style={styles.totalValue}>
              {new Intl.NumberFormat('fr-FR', {
                style: 'currency',
                currency: 'EUR',
                minimumFractionDigits: 2,
              }).format(amount + estimatedFee)}
            </Text>
          </View>
          <Text style={styles.durationNote}>Remboursement sous 30 jours</Text>
        </FlowGuardCard>

        {/* Biometric Notice */}
        <View style={styles.biometricNotice}>
          <Text style={styles.biometricIcon}>🔒</Text>
          <Text style={styles.biometricText}>
            L'authentification biométrique est requise pour confirmer cette opération
          </Text>
        </View>

        {/* CTA */}
        {isPending ? (
          <FlowGuardLoader />
        ) : (
          <View style={styles.buttonWrapper}>
            <FlowGuardButton
              title="Demander le crédit flash"
              onPress={handleRequest}
              variant="primary"
            />
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
  amountCard: {
    marginHorizontal: spacing.md,
    alignItems: 'center',
  },
  amountLabel: {
    color: colors.textMuted,
    fontSize: typography.caption.fontSize,
    marginBottom: spacing.xs,
  },
  amountValue: {
    ...typography.hero,
    color: colors.primary,
    marginBottom: spacing.md,
  },
  slider: {
    width: '100%',
    height: 40,
  },
  rangeRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    width: '100%',
    marginTop: spacing.xs,
  },
  rangeText: {
    color: colors.textMuted,
    fontSize: typography.caption.fontSize,
  },
  sectionTitle: {
    ...typography.h3,
    color: colors.textPrimary,
    paddingHorizontal: spacing.md,
    marginTop: spacing.lg,
    marginBottom: spacing.sm,
  },
  purposeGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    paddingHorizontal: spacing.md,
    gap: spacing.sm,
    marginBottom: spacing.lg,
  },
  purposeChip: {
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    borderRadius: 20,
    backgroundColor: colors.surface,
    color: colors.textSecondary,
    fontSize: typography.caption.fontSize,
    fontWeight: '600',
    overflow: 'hidden',
  },
  purposeChipActive: {
    backgroundColor: colors.primary + '30',
    color: colors.primary,
  },
  summaryCard: {
    marginHorizontal: spacing.md,
    marginBottom: spacing.md,
  },
  summaryRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: spacing.sm,
  },
  summaryLabel: {
    color: colors.textSecondary,
    fontSize: typography.body.fontSize,
  },
  summaryValue: {
    color: colors.textPrimary,
    fontSize: typography.body.fontSize,
    fontWeight: '600',
  },
  totalRow: {
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.border,
    paddingTop: spacing.sm,
    marginBottom: spacing.xs,
  },
  totalLabel: {
    color: colors.textPrimary,
    fontSize: typography.body.fontSize,
    fontWeight: '700',
  },
  totalValue: {
    color: colors.primary,
    fontSize: typography.h3.fontSize,
    fontWeight: '700',
  },
  durationNote: {
    color: colors.textMuted,
    fontSize: typography.caption.fontSize,
    textAlign: 'center',
  },
  biometricNotice: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    gap: spacing.sm,
    marginHorizontal: spacing.md,
    backgroundColor: colors.warning + '15',
    borderRadius: 12,
    marginBottom: spacing.lg,
  },
  biometricIcon: {
    fontSize: 20,
  },
  biometricText: {
    flex: 1,
    color: colors.textSecondary,
    fontSize: typography.caption.fontSize,
    lineHeight: 18,
  },
  buttonWrapper: {
    paddingHorizontal: spacing.md,
  },
  successContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: spacing.lg,
  },
  successCircle: {
    width: 100,
    height: 100,
    borderRadius: 50,
    backgroundColor: colors.success + '20',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: spacing.lg,
  },
  successIcon: {
    fontSize: 48,
  },
  successTitle: {
    ...typography.h1,
    color: colors.success,
    marginBottom: spacing.sm,
  },
  successAmount: {
    ...typography.hero,
    color: colors.textPrimary,
    marginBottom: spacing.sm,
  },
  successSubtitle: {
    color: colors.textSecondary,
    fontSize: typography.body.fontSize,
    textAlign: 'center',
    marginBottom: spacing.xl,
  },
  successButtonWrapper: {
    width: '100%',
  },
  bottomSpacer: {
    height: spacing.xxl,
  },
  // Explainer step styles
  explainerCard: {
    marginHorizontal: spacing.md,
    marginBottom: spacing.sm,
  },
  explainerRow: {
    flexDirection: 'row',
    gap: spacing.md,
    alignItems: 'flex-start',
  },
  explainerIcon: { fontSize: 28, flexShrink: 0, marginTop: 2 },
  explainerContent: { flex: 1 },
  explainerTitle: {
    color: colors.textPrimary,
    fontWeight: '700',
    fontSize: typography.body.fontSize,
    marginBottom: 4,
  },
  explainerBody: {
    color: colors.textSecondary,
    fontSize: typography.caption.fontSize,
    lineHeight: 20,
  },
})
