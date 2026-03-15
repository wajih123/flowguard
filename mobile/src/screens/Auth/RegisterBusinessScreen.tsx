import React, { useState, useCallback } from 'react'
import { View, Text, ScrollView, StyleSheet, Keyboard, TouchableOpacity } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { z } from 'zod'
import type { StackScreenProps } from '@react-navigation/stack'
import { useAuthStore } from '../../store/authStore'
import { FlowGuardButton } from '../../components/FlowGuardButton'
import { FlowGuardInput } from '../../components/FlowGuardInput'
import { FlowGuardCard } from '../../components/FlowGuardCard'
import { Routes } from '../../navigation/routes'
import { colors, typography, spacing } from '../../theme'

const step1Schema = z
  .object({
    firstName: z.string().min(2, 'Minimum 2 caractères'),
    lastName: z.string().min(2, 'Minimum 2 caractères'),
    email: z.string().email('Email invalide'),
    password: z.string().min(8, 'Minimum 8 caractères').regex(/[0-9]/, 'Au moins un chiffre'),
    confirmPassword: z.string(),
  })
  .refine((d) => d.password === d.confirmPassword, {
    message: 'Les mots de passe ne correspondent pas',
    path: ['confirmPassword'],
  })

const step2Schema = z.object({
  companyName: z.string().min(1, "Nom de l'entreprise requis"),
  siret: z.string().regex(/^\d{14}$/, 'SIRET invalide (14 chiffres)'),
  sector: z.string().min(1, 'Secteur requis'),
  employeeCount: z.string().min(1, 'Requis'),
})

const SECTORS = [
  'Commerce',
  'BTP',
  'Restauration',
  'Santé',
  'Immobilier',
  'Transport',
  'Tech / Numérique',
  'Services aux entreprises',
  'Autre',
]

const EMPLOYEE_COUNTS = ['1', '2-5', '6-10', '11-50', '51-250', '250+']

const PLAN_OPTIONS: { key: 'PRO' | 'SCALE'; price: string; features: string[] }[] = [
  {
    key: 'PRO',
    price: '49€/mois',
    features: [
      '✅ Multi-comptes bancaires',
      '✅ Prédictions 30/60/90j',
      '✅ Scénarios "et si"',
      '✅ Export PDF',
      '✅ Réserve FlowGuard',
      "❌ Gestion d'équipe",
    ],
  },
  {
    key: 'SCALE',
    price: '99€/mois',
    features: [
      '✅ Tout PRO inclus',
      "✅ Équipe jusqu'à 5 membres",
      '✅ Accès API',
      '✅ Support prioritaire',
    ],
  },
]

type Props = StackScreenProps<Record<string, undefined>, string>

export const RegisterBusinessScreen: React.FC<Props> = ({ navigation }) => {
  const [step, setStep] = useState<1 | 2 | 3>(1)

  // Step 1 fields
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')

  // Step 2 fields
  const [companyName, setCompanyName] = useState('')
  const [siret, setSiret] = useState('')
  const [sector, setSector] = useState('')
  const [employeeCount, setEmployeeCount] = useState('')

  // Step 3 fields
  const [selectedPlan, setSelectedPlan] = useState<'PRO' | 'SCALE'>('PRO')

  const [errors, setErrors] = useState<Record<string, string>>({})

  const { registerBusiness, isLoading, error: authError } = useAuthStore()

  const handleStep1 = useCallback(() => {
    Keyboard.dismiss()
    const result = step1Schema.safeParse({
      firstName,
      lastName,
      email,
      password,
      confirmPassword,
    })
    if (!result.success) {
      const fieldErrors: Record<string, string> = {}
      result.error.errors.forEach((e) => {
        const f = e.path[0]
        if (typeof f === 'string') fieldErrors[f] = e.message
      })
      setErrors(fieldErrors)
      return
    }
    setErrors({})
    setStep(2)
  }, [firstName, lastName, email, password, confirmPassword])

  const handleStep2 = useCallback(() => {
    Keyboard.dismiss()
    const result = step2Schema.safeParse({ companyName, siret, sector, employeeCount })
    if (!result.success) {
      const fieldErrors: Record<string, string> = {}
      result.error.errors.forEach((e) => {
        const f = e.path[0]
        if (typeof f === 'string') fieldErrors[f] = e.message
      })
      setErrors(fieldErrors)
      return
    }
    setErrors({})
    setStep(3)
  }, [companyName, siret, sector, employeeCount])

  const handleSubmit = useCallback(async () => {
    await registerBusiness({
      firstName: firstName.trim(),
      lastName: lastName.trim(),
      email: email.trim(),
      password,
      companyName: companyName.trim(),
      siret: siret.trim(),
      sector,
      employeeCount,
      plan: selectedPlan,
    })
  }, [
    firstName,
    lastName,
    email,
    password,
    companyName,
    siret,
    sector,
    employeeCount,
    selectedPlan,
    registerBusiness,
  ])

  const renderStepIndicator = () => (
    <View style={styles.stepRow}>
      {[1, 2, 3].map((s) => (
        <View key={s} style={[styles.stepDot, s <= step && styles.stepDotActive]} />
      ))}
      <Text style={styles.stepLabel}>Étape {step}/3</Text>
    </View>
  )

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView
        contentContainerStyle={styles.scroll}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      >
        <TouchableOpacity
          onPress={() => (step > 1 ? setStep((s) => (s - 1) as 1 | 2 | 3) : navigation.goBack())}
          style={styles.backBtn}
        >
          <Text style={styles.backText}>← Retour</Text>
        </TouchableOpacity>

        <Text style={styles.title}>Créer un compte entreprise</Text>
        {renderStepIndicator()}

        {authError && (
          <FlowGuardCard variant="alert" style={styles.errorCard}>
            <Text style={styles.errorText}>{authError}</Text>
          </FlowGuardCard>
        )}

        {/* ── STEP 1 ── */}
        {step === 1 && (
          <View>
            <FlowGuardInput
              label="Prénom"
              value={firstName}
              onChangeText={setFirstName}
              error={errors.firstName}
            />
            <FlowGuardInput
              label="Nom"
              value={lastName}
              onChangeText={setLastName}
              error={errors.lastName}
            />
            <FlowGuardInput
              label="Email professionnel"
              value={email}
              onChangeText={setEmail}
              keyboardType="email-address"
              autoCapitalize="none"
              error={errors.email}
            />
            <FlowGuardInput
              label="Mot de passe"
              value={password}
              onChangeText={setPassword}
              secureTextEntry
              error={errors.password}
            />
            <FlowGuardInput
              label="Confirmer le mot de passe"
              value={confirmPassword}
              onChangeText={setConfirmPassword}
              secureTextEntry
              error={errors.confirmPassword}
            />
            <FlowGuardButton
              label="Continuer"
              onPress={handleStep1}
              variant="primary"
              style={styles.btn}
            />
          </View>
        )}

        {/* ── STEP 2 ── */}
        {step === 2 && (
          <View>
            <FlowGuardInput
              label="Nom de la structure"
              value={companyName}
              onChangeText={setCompanyName}
              error={errors.companyName}
            />
            <FlowGuardInput
              label="SIRET (14 chiffres)"
              value={siret}
              onChangeText={setSiret}
              keyboardType="numeric"
              error={errors.siret}
            />

            <Text style={styles.pickerLabel}>Secteur d'activité</Text>
            <View style={styles.chipsRow}>
              {SECTORS.map((s) => (
                <TouchableOpacity
                  key={s}
                  style={[styles.chip, sector === s && styles.chipActive]}
                  onPress={() => setSector(s)}
                >
                  <Text style={[styles.chipText, sector === s && styles.chipTextActive]}>{s}</Text>
                </TouchableOpacity>
              ))}
            </View>
            {errors.sector ? <Text style={styles.fieldError}>{errors.sector}</Text> : null}

            <Text style={styles.pickerLabel}>Nombre d'employés</Text>
            <View style={styles.chipsRow}>
              {EMPLOYEE_COUNTS.map((c) => (
                <TouchableOpacity
                  key={c}
                  style={[styles.chip, employeeCount === c && styles.chipActive]}
                  onPress={() => setEmployeeCount(c)}
                >
                  <Text style={[styles.chipText, employeeCount === c && styles.chipTextActive]}>
                    {c}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
            {errors.employeeCount ? (
              <Text style={styles.fieldError}>{errors.employeeCount}</Text>
            ) : null}

            <FlowGuardButton
              label="Continuer"
              onPress={handleStep2}
              variant="primary"
              style={styles.btn}
            />
          </View>
        )}

        {/* ── STEP 3 ── */}
        {step === 3 && (
          <View>
            <Text style={styles.planTitle}>Choisissez votre plan</Text>
            <Text style={styles.planSubtitle}>Essai gratuit 14 jours — aucun engagement</Text>

            {PLAN_OPTIONS.map(({ key, price, features }) => (
              <TouchableOpacity key={key} onPress={() => setSelectedPlan(key)} activeOpacity={0.8}>
                <FlowGuardCard
                  style={[styles.planCard, selectedPlan === key && styles.planCardActive]}
                >
                  <View style={styles.planHeader}>
                    <Text style={styles.planName}>{key}</Text>
                    <Text style={styles.planPrice}>{price}</Text>
                  </View>
                  {features.map((f, i) => (
                    <Text key={i} style={styles.planFeature}>
                      {f}
                    </Text>
                  ))}
                  {selectedPlan === key && <Text style={styles.selectedText}>✓ Sélectionné</Text>}
                </FlowGuardCard>
              </TouchableOpacity>
            ))}

            <FlowGuardButton
              label="Démarrer l'essai gratuit 14 jours"
              onPress={handleSubmit}
              variant="primary"
              loading={isLoading}
              style={styles.btn}
            />
          </View>
        )}

        <TouchableOpacity
          onPress={() => navigation.navigate(Routes.Login as never)}
          style={styles.loginLink}
        >
          <Text style={styles.loginText}>
            Déjà un compte ? <Text style={styles.loginBold}>Se connecter</Text>
          </Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  scroll: { padding: spacing.md, paddingBottom: spacing.xxl },
  backBtn: { marginBottom: spacing.md },
  backText: { color: colors.primary, fontSize: typography.body.fontSize, fontWeight: '600' },
  title: { ...typography.h1, color: colors.textPrimary, marginBottom: spacing.md },
  stepRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    marginBottom: spacing.lg,
  },
  stepDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: colors.cardBorder },
  stepDotActive: { backgroundColor: colors.primary },
  stepLabel: {
    color: colors.textMuted,
    fontSize: typography.caption.fontSize,
    marginLeft: spacing.xs,
  },
  errorCard: { marginBottom: spacing.md },
  errorText: { color: colors.danger, fontSize: typography.body.fontSize },
  btn: { marginTop: spacing.lg },
  pickerLabel: {
    color: colors.textMuted,
    fontSize: typography.caption.fontSize,
    fontWeight: '600',
    marginBottom: spacing.sm,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  chipsRow: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm, marginBottom: spacing.sm },
  chip: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: 20,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.cardBorder,
  },
  chipActive: { backgroundColor: `${colors.primary}20`, borderColor: colors.primary },
  chipText: { color: colors.textSecondary, fontSize: 13, fontWeight: '600' },
  chipTextActive: { color: colors.primary },
  fieldError: {
    color: colors.danger,
    fontSize: typography.caption.fontSize,
    marginBottom: spacing.sm,
  },
  planTitle: { ...typography.h2, color: colors.textPrimary, marginBottom: spacing.xs },
  planSubtitle: {
    color: colors.textSecondary,
    fontSize: typography.body.fontSize,
    marginBottom: spacing.lg,
  },
  planCard: { marginBottom: spacing.md },
  planCardActive: { borderColor: colors.primary, borderWidth: 2 },
  planHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: spacing.sm,
  },
  planName: { ...typography.h3, color: colors.textPrimary },
  planPrice: { color: colors.primary, fontSize: typography.h3.fontSize, fontWeight: '700' },
  planFeature: { color: colors.textSecondary, fontSize: 13, lineHeight: 22 },
  selectedText: { color: colors.success, fontWeight: '600', marginTop: spacing.sm, fontSize: 13 },
  loginLink: { alignItems: 'center', marginTop: spacing.xl },
  loginText: { color: colors.textSecondary, fontSize: typography.body.fontSize },
  loginBold: { color: colors.primary, fontWeight: '700' },
})
