import React, { useState, useCallback } from 'react'
import { View, Text, ScrollView, StyleSheet, Keyboard, TouchableOpacity } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { z } from 'zod'
import type { StackScreenProps } from '@react-navigation/stack'
import { useAuthStore } from '../../store/authStore'
import { FlowGuardButton } from '../../components/FlowGuardButton'
import { FlowGuardInput } from '../../components/FlowGuardInput'
import { FlowGuardLoader } from '../../components/FlowGuardLoader'
import { Routes } from '../../navigation/routes'
import { colors, typography, spacing } from '../../theme'

const registerSchema = z
  .object({
    firstName: z.string().min(2, 'Minimum 2 caractères'),
    lastName: z.string().min(2, 'Minimum 2 caractères'),
    email: z.string().min(1, 'Email requis').email('Email invalide'),
    password: z
      .string()
      .min(8, 'Minimum 8 caractères')
      .regex(/[A-Z]/, 'Au moins une majuscule')
      .regex(/[0-9]/, 'Au moins un chiffre')
      .regex(/[^A-Za-z0-9]/, 'Au moins un caractère spécial'),
    confirmPassword: z.string(),
    companyName: z.string().optional(),
    userType: z.enum(['FREELANCE', 'TPE', 'PME']),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'Les mots de passe ne correspondent pas',
    path: ['confirmPassword'],
  })

type UserType = 'FREELANCE' | 'TPE' | 'PME'

const USER_TYPES: { key: UserType; label: string; description: string }[] = [
  { key: 'FREELANCE', label: 'Freelance', description: 'Auto-entrepreneur, indépendant' },
  { key: 'TPE', label: 'TPE', description: '< 10 salariés' },
  { key: 'PME', label: 'PME', description: '10 à 250 salariés' },
]

type Props = StackScreenProps<Record<string, undefined>, string>

export const RegisterScreen: React.FC<Props> = ({ navigation }) => {
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [companyName, setCompanyName] = useState('')
  const [userType, setUserType] = useState<UserType>('FREELANCE')
  const [errors, setErrors] = useState<Record<string, string>>({})

  const { register, isLoading, error: authError } = useAuthStore()

  const validate = useCallback((): boolean => {
    const result = registerSchema.safeParse({
      firstName,
      lastName,
      email,
      password,
      confirmPassword,
      companyName,
      userType,
    })
    if (!result.success) {
      const fieldErrors: Record<string, string> = {}
      result.error.errors.forEach((err) => {
        const field = err.path[0]
        if (typeof field === 'string') {
          fieldErrors[field] = err.message
        }
      })
      setErrors(fieldErrors)
      return false
    }
    setErrors({})
    return true
  }, [firstName, lastName, email, password, confirmPassword, companyName, userType])

  const handleRegister = useCallback(async () => {
    Keyboard.dismiss()
    if (!validate()) {
      return
    }
    await register({
      firstName: firstName.trim(),
      lastName: lastName.trim(),
      email: email.trim(),
      password,
      companyName: companyName.trim(),
      userType,
    })
  }, [firstName, lastName, email, password, companyName, userType, register, validate])

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
        <Text style={styles.title}>Créer un compte</Text>
        <Text style={styles.subtitle}>Rejoignez FlowGuard pour piloter votre trésorerie</Text>

        {authError && (
          <View style={styles.errorBanner}>
            <Text style={styles.errorBannerText}>{authError}</Text>
          </View>
        )}

        {/* User Type Selector */}
        <Text style={styles.sectionLabel}>Type d'entreprise</Text>
        <View style={styles.typeGrid}>
          {USER_TYPES.map((type) => {
            const isActive = userType === type.key
            return (
              <TouchableOpacity
                key={type.key}
                onPress={() => setUserType(type.key)}
                style={[styles.typeCard, isActive && styles.typeCardActive]}
              >
                <Text style={[styles.typeLabel, isActive && styles.typeLabelActive]}>
                  {type.label}
                </Text>
                <Text style={styles.typeDesc}>{type.description}</Text>
              </TouchableOpacity>
            )
          })}
        </View>

        <FlowGuardInput
          label="Prénom"
          value={firstName}
          onChangeText={setFirstName}
          placeholder="Jean"
          error={errors.firstName}
        />

        <FlowGuardInput
          label="Nom"
          value={lastName}
          onChangeText={setLastName}
          placeholder="Dupont"
          error={errors.lastName}
        />

        <FlowGuardInput
          label="Email professionnel"
          value={email}
          onChangeText={setEmail}
          placeholder="jean@entreprise.com"
          keyboardType="email-address"
          autoCapitalize="none"
          autoCorrect={false}
          error={errors.email}
        />

        <FlowGuardInput
          label="Nom de l'entreprise"
          value={companyName}
          onChangeText={setCompanyName}
          placeholder="Mon Entreprise SAS"
          error={errors.companyName}
        />

        <FlowGuardInput
          label="Mot de passe"
          value={password}
          onChangeText={setPassword}
          placeholder="••••••••"
          secureTextEntry
          error={errors.password}
        />

        <FlowGuardInput
          label="Confirmer le mot de passe"
          value={confirmPassword}
          onChangeText={setConfirmPassword}
          placeholder="••••••••"
          secureTextEntry
          error={errors.confirmPassword}
        />

        {isLoading ? (
          <FlowGuardLoader />
        ) : (
          <View style={styles.buttonWrapper}>
            <FlowGuardButton title="Créer mon compte" onPress={handleRegister} variant="primary" />
          </View>
        )}

        <TouchableOpacity
          onPress={() => navigation.navigate(Routes.Login as never)}
          style={styles.loginLink}
        >
          <Text style={styles.loginText}>
            Déjà un compte ? <Text style={styles.loginTextBold}>Se connecter</Text>
          </Text>
        </TouchableOpacity>

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
  scrollContent: {
    paddingHorizontal: spacing.md,
    paddingTop: spacing.lg,
  },
  title: {
    ...typography.h1,
    color: colors.textPrimary,
    marginBottom: spacing.xs,
  },
  subtitle: {
    color: colors.textSecondary,
    fontSize: typography.body.fontSize,
    marginBottom: spacing.lg,
  },
  errorBanner: {
    backgroundColor: colors.danger + '20',
    borderRadius: 12,
    padding: spacing.md,
    marginBottom: spacing.md,
  },
  errorBannerText: {
    color: colors.danger,
    fontSize: typography.body.fontSize,
    fontWeight: '600',
  },
  sectionLabel: {
    ...typography.label,
    color: colors.textSecondary,
    marginBottom: spacing.sm,
  },
  typeGrid: {
    flexDirection: 'row',
    gap: spacing.sm,
    marginBottom: spacing.lg,
  },
  typeCard: {
    flex: 1,
    backgroundColor: colors.surface,
    borderRadius: 12,
    padding: spacing.md,
    alignItems: 'center',
    borderWidth: 2,
    borderColor: 'transparent',
  },
  typeCardActive: {
    borderColor: colors.primary,
    backgroundColor: colors.primary + '15',
  },
  typeLabel: {
    color: colors.textPrimary,
    fontSize: typography.body.fontSize,
    fontWeight: '700',
    marginBottom: spacing.xs,
  },
  typeLabelActive: {
    color: colors.primary,
  },
  typeDesc: {
    color: colors.textMuted,
    fontSize: 11,
    textAlign: 'center',
  },
  buttonWrapper: {
    marginTop: spacing.md,
  },
  loginLink: {
    marginTop: spacing.lg,
    alignItems: 'center',
    paddingVertical: spacing.md,
  },
  loginText: {
    color: colors.textSecondary,
    fontSize: typography.body.fontSize,
  },
  loginTextBold: {
    color: colors.primary,
    fontWeight: '700',
  },
  bottomSpacer: {
    height: spacing.xxl,
  },
})
