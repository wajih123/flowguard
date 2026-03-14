import React, { useState, useCallback } from 'react'
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  Alert,
  TouchableOpacity,
  TextInput,
} from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { useMutation } from '@tanstack/react-query'
import type { StackScreenProps } from '@react-navigation/stack'
import ReactNativeBiometrics from 'react-native-biometrics'
import { FlowGuardCard } from '../../components/FlowGuardCard'
import { FlowGuardButton } from '../../components/FlowGuardButton'
import { FlowGuardLoader } from '../../components/FlowGuardLoader'
import { useReserveEligibility } from '../../hooks/useReserveEligibility'
import { useAccountStore } from '../../store/accountStore'
import { Routes } from '../../navigation/routes'
import { colors, typography, spacing } from '../../theme'
import * as flowguardApi from '../../api/flowguardApi'
import type { CreditResponse } from '../../domain/Banking'

type Props = StackScreenProps<Record<string, undefined>, typeof Routes.Reserve>

const fmtEur = (val: number) =>
  new Intl.NumberFormat('fr-FR', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 0,
  }).format(val)

const COMMISSION_RATE = 0.015

const rnBiometrics = new ReactNativeBiometrics()

export const ReserveScreen: React.FC<Props> = ({ navigation }) => {
  const account = useAccountStore((s) => s.account)
  const { eligibility, isLoading, blocked } = useReserveEligibility(account?.id)
  const [amount, setAmount] = useState(0)
  const [result, setResult] = useState<CreditResponse | null>(null)

  const effectiveAmount = amount > 0 ? amount : 0
  const commission = Math.round(effectiveAmount * COMMISSION_RATE * 100) / 100
  const totalToRepay = effectiveAmount + commission

  const { mutate: activate, isPending: activating } = useMutation({
    mutationFn: () => flowguardApi.activateReserve(account!.id, amount),
    onSuccess: (res) => setResult(res as CreditResponse),
    onError: () => Alert.alert('Erreur', "Impossible d'activer la Réserve. Réessayez."),
  })

  const handleConfirm = useCallback(async () => {
    if (amount <= 0) {
      Alert.alert('Montant invalide', 'Sélectionnez un montant supérieur à 0 €.')
      return
    }

    const { available } = await rnBiometrics.isSensorAvailable()
    if (available) {
      const { success } = await rnBiometrics.simplePrompt({
        promptMessage: "Confirmer l'activation de la Réserve",
      })
      if (!success) {
        Alert.alert('Authentification échouée', 'La confirmation biométrique a échoué.')
        return
      }
    }

    activate()
  }, [amount, activate])

  // Guard: feature flag
  if (blocked === 'flag') {
    return (
      <SafeAreaView style={styles.container} edges={['top']}>
        <View style={styles.blockedContainer}>
          <Text style={styles.blockedIcon}>🔒</Text>
          <Text style={styles.blockedTitle}>Réserve non disponible</Text>
          <Text style={styles.blockedText}>
            La Réserve FlowGuard n'est pas encore disponible dans votre région.
          </Text>
          <FlowGuardButton label="Retour" onPress={() => navigation.goBack()} variant="outline" />
        </View>
      </SafeAreaView>
    )
  }

  // Guard: KYC
  if (blocked === 'kyc') {
    return (
      <SafeAreaView style={styles.container} edges={['top']}>
        <View style={styles.blockedContainer}>
          <Text style={styles.blockedIcon}>🪪</Text>
          <Text style={styles.blockedTitle}>Vérification requise</Text>
          <Text style={styles.blockedText}>
            Vous devez compléter votre KYC pour accéder à la Réserve.
          </Text>
          <FlowGuardButton
            label="Compléter le KYC"
            onPress={() => navigation.navigate(Routes.Kyc as never)}
            variant="primary"
            style={styles.blockedBtn}
          />
          <FlowGuardButton label="Retour" onPress={() => navigation.goBack()} variant="outline" />
        </View>
      </SafeAreaView>
    )
  }

  if (isLoading) return <FlowGuardLoader />

  // Guard: not eligible
  if (eligibility && !eligibility.eligible) {
    return (
      <SafeAreaView style={styles.container} edges={['top']}>
        <View style={styles.blockedContainer}>
          <Text style={styles.blockedIcon}>⚠️</Text>
          <Text style={styles.blockedTitle}>Non éligible</Text>
          <Text style={styles.blockedText}>
            {eligibility.reason ?? "Vous n'êtes pas éligible à la Réserve pour le moment."}
          </Text>
          <FlowGuardButton label="Retour" onPress={() => navigation.goBack()} variant="outline" />
        </View>
      </SafeAreaView>
    )
  }

  // Success state
  if (result) {
    return (
      <SafeAreaView style={styles.container} edges={['top']}>
        <View style={styles.blockedContainer}>
          <Text style={styles.blockedIcon}>{result.status === 'APPROVED' ? '✅' : '⏳'}</Text>
          <Text style={styles.blockedTitle}>
            {result.status === 'APPROVED' ? 'Réserve activée !' : 'Demande en cours…'}
          </Text>
          <Text style={styles.blockedText}>{result.message}</Text>

          <FlowGuardCard style={styles.resultCard}>
            <View style={styles.row}>
              <Text style={styles.rowLabel}>Montant crédité</Text>
              <Text style={styles.rowValue}>{fmtEur(effectiveAmount)}</Text>
            </View>
            <View style={styles.row}>
              <Text style={styles.rowLabel}>Commission (1,5 %)</Text>
              <Text style={styles.rowValue}>{fmtEur(result.commission)}</Text>
            </View>
            <View style={[styles.row, styles.rowLast]}>
              <Text style={styles.rowLabel}>Total à rembourser</Text>
              <Text style={[styles.rowValue, { color: colors.primary }]}>
                {fmtEur(result.totalToRepay)}
              </Text>
            </View>
          </FlowGuardCard>

          <FlowGuardButton
            label="Retour au tableau de bord"
            onPress={() => navigation.navigate(Routes.Dashboard as never)}
            variant="primary"
          />
        </View>
      </SafeAreaView>
    )
  }

  const maxAmount = eligibility?.maxAmount ?? 5000
  const suggested = eligibility?.suggestedAmount ?? 1000

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
        <Text style={styles.backText}>← Retour</Text>
      </TouchableOpacity>

      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        <Text style={styles.title}>Réserve de trésorerie</Text>
        <Text style={styles.subtitle}>
          Accédez instantanément à des liquidités. Commission unique de 1,5 % sur le montant activé.
        </Text>

        <FlowGuardCard style={styles.amountCard}>
          <Text style={styles.amountSub}>Montant souhaité (max {fmtEur(maxAmount)})</Text>
          <TextInput
            style={styles.amountInput}
            value={amount > 0 ? String(amount) : ''}
            onChangeText={(t) => {
              const parsed = parseFloat(t.replace(',', '.'))
              if (!isNaN(parsed)) setAmount(Math.min(parsed, maxAmount))
              else if (t === '') setAmount(0)
            }}
            keyboardType="decimal-pad"
            placeholder={fmtEur(suggested)}
            placeholderTextColor={colors.textMuted}
          />
          <Text style={styles.amountValue}>{fmtEur(amount > 0 ? amount : suggested)}</Text>
          <View style={styles.quickAmounts}>
            {[500, 1000, 2000, 5000]
              .filter((v) => v <= maxAmount)
              .map((v) => (
                <TouchableOpacity
                  key={v}
                  onPress={() => setAmount(v)}
                  style={[styles.quickChip, amount === v && styles.quickChipActive]}
                >
                  <Text style={[styles.quickChipText, amount === v && styles.quickChipTextActive]}>
                    {fmtEur(v)}
                  </Text>
                </TouchableOpacity>
              ))}
          </View>
        </FlowGuardCard>

        <FlowGuardCard style={styles.summaryCard}>
          <View style={styles.row}>
            <Text style={styles.rowLabel}>Montant demandé</Text>
            <Text style={styles.rowValue}>{fmtEur(amount)}</Text>
          </View>
          <View style={styles.row}>
            <Text style={styles.rowLabel}>Commission (1,5 %)</Text>
            <Text style={styles.rowValue}>{fmtEur(commission)}</Text>
          </View>
          <View style={[styles.row, styles.rowLast]}>
            <Text style={styles.rowLabel}>Total à rembourser</Text>
            <Text style={[styles.rowValue, { color: colors.primary }]}>{fmtEur(totalToRepay)}</Text>
          </View>
        </FlowGuardCard>

        <FlowGuardCard variant="info" style={styles.infoCard}>
          <Text style={styles.infoText}>
            Aucun intérêt. La commission de 1,5 % est appliquée une seule fois sur le montant
            activé. Le remboursement est automatique dès que votre solde le permet.
          </Text>
        </FlowGuardCard>

        <FlowGuardButton
          label={`Activer ${fmtEur(amount)}`}
          onPress={handleConfirm}
          variant="primary"
          loading={activating}
          style={styles.ctaBtn}
        />
      </ScrollView>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  backBtn: { paddingHorizontal: spacing.md, paddingTop: spacing.md, paddingBottom: spacing.sm },
  backText: { color: colors.primary, ...typography.body, fontWeight: '600' },
  scroll: { padding: spacing.md, paddingBottom: spacing.xxl },
  title: { ...typography.h1, color: colors.textPrimary, marginBottom: spacing.sm },
  subtitle: { color: colors.textSecondary, ...typography.body, marginBottom: spacing.lg },
  amountCard: { marginBottom: spacing.md, paddingVertical: spacing.lg },
  amountInput: {
    backgroundColor: colors.surface,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.primary,
    color: colors.textPrimary,
    ...typography.h2,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    textAlign: 'center',
    marginBottom: spacing.sm,
  },
  amountValue: {
    ...typography.hero,
    color: colors.primary,
    textAlign: 'center',
    marginBottom: spacing.sm,
  },
  amountSub: {
    color: colors.textSecondary,
    ...typography.caption,
    marginBottom: spacing.sm,
    textAlign: 'center',
  },
  quickAmounts: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
    justifyContent: 'center',
  },
  quickChip: {
    borderRadius: 20,
    paddingHorizontal: spacing.md,
    paddingVertical: 6,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.cardBorder,
  },
  quickChipActive: { borderColor: colors.primary, backgroundColor: colors.primary + '22' },
  quickChipText: { ...typography.caption, color: colors.textSecondary, fontWeight: '600' },
  quickChipTextActive: { color: colors.primary },
  summaryCard: { marginBottom: spacing.md },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: spacing.xs,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.cardBorder,
  },
  rowLast: { borderBottomWidth: 0, paddingTop: spacing.sm },
  rowLabel: { color: colors.textSecondary, ...typography.body },
  rowValue: { color: colors.textPrimary, ...typography.body, fontWeight: '700' },
  infoCard: { marginBottom: spacing.md },
  infoText: { color: colors.textPrimary, ...typography.caption },
  ctaBtn: {},
  blockedContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: spacing.xl,
  },
  blockedIcon: { fontSize: 64, marginBottom: spacing.md },
  blockedTitle: {
    ...typography.h2,
    color: colors.textPrimary,
    textAlign: 'center',
    marginBottom: spacing.sm,
  },
  blockedText: {
    color: colors.textSecondary,
    ...typography.body,
    textAlign: 'center',
    marginBottom: spacing.xl,
  },
  blockedBtn: { marginBottom: spacing.sm, width: '100%' },
  resultCard: { width: '100%', marginBottom: spacing.xl },
})
