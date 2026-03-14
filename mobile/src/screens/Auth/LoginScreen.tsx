import React, { useState, useCallback } from 'react'
import { View, Text, ScrollView, StyleSheet, Keyboard, TouchableOpacity } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { z } from 'zod'
import type { NativeStackScreenProps } from '@react-navigation/native-stack'
import { useAuthStore } from '../../store/authStore'
import { useBiometric } from '../../hooks/useBiometric'
import { FlowGuardButton } from '../../components/FlowGuardButton'
import { FlowGuardInput } from '../../components/FlowGuardInput'
import { FlowGuardLoader } from '../../components/FlowGuardLoader'
import { Routes } from '../../navigation/routes'
import { colors, typography, spacing } from '../../theme'

const loginSchema = z.object({
  email: z.string().min(1, 'Email requis').email('Email invalide'),
  password: z.string().min(8, 'Minimum 8 caractères'),
})

type Props = NativeStackScreenProps<Record<string, undefined>, string>

export const LoginScreen: React.FC<Props> = ({ navigation }) => {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})

  const { login, loginWithBiometric, isLoading, error: authError } = useAuthStore()
  const { isAvailable: biometricAvailable } = useBiometric()

  const validate = useCallback((): boolean => {
    const result = loginSchema.safeParse({ email, password })
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
  }, [email, password])

  const handleLogin = useCallback(async () => {
    Keyboard.dismiss()
    if (!validate()) return
    await login(email.trim(), password)
  }, [email, password, login, validate])

  const handleBiometric = useCallback(async () => {
    await loginWithBiometric()
  }, [loginWithBiometric])

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
        <View style={styles.header}>
          <Text style={styles.logo}>FlowGuard</Text>
          <Text style={styles.subtitle}>Connectez-vous à votre compte</Text>
        </View>

        {authError && (
          <View style={styles.errorBanner}>
            <Text style={styles.errorBannerText}>{authError}</Text>
          </View>
        )}

        <FlowGuardInput
          label="Email"
          value={email}
          onChangeText={setEmail}
          placeholder="votre@email.com"
          keyboardType="email-address"
          autoCapitalize="none"
          autoCorrect={false}
          error={errors.email}
        />

        <FlowGuardInput
          label="Mot de passe"
          value={password}
          onChangeText={setPassword}
          placeholder="••••••••"
          secureTextEntry
          error={errors.password}
        />

        {isLoading ? (
          <FlowGuardLoader />
        ) : (
          <View style={styles.buttonGroup}>
            <FlowGuardButton title="Se connecter" onPress={handleLogin} variant="primary" />

            {biometricAvailable && (
              <FlowGuardButton
                title="🔐 Connexion biométrique"
                onPress={handleBiometric}
                variant="outline"
              />
            )}
          </View>
        )}

        <TouchableOpacity
          onPress={() => navigation.navigate(Routes.REGISTER as never)}
          style={styles.registerLink}
        >
          <Text style={styles.registerText}>
            Pas encore de compte ?{' '}
            <Text style={styles.registerTextBold}>Créer un compte</Text>
          </Text>
        </TouchableOpacity>
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
    flexGrow: 1,
    paddingHorizontal: spacing.md,
    paddingTop: spacing.xxl,
  },
  header: {
    marginBottom: spacing.xl,
  },
  logo: {
    ...typography.hero,
    color: colors.primary,
    marginBottom: spacing.xs,
  },
  subtitle: {
    color: colors.textSecondary,
    fontSize: typography.body.fontSize,
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
  buttonGroup: {
    gap: spacing.sm,
    marginTop: spacing.md,
  },
  registerLink: {
    marginTop: spacing.xl,
    alignItems: 'center',
    paddingVertical: spacing.md,
  },
  registerText: {
    color: colors.textSecondary,
    fontSize: typography.body.fontSize,
  },
  registerTextBold: {
    color: colors.primary,
    fontWeight: '700',
  },
})
