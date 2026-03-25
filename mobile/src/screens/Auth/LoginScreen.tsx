import React, { useState, useCallback, useRef, useEffect } from 'react'
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  Keyboard,
  TouchableOpacity,
  TextInput,
} from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { z } from 'zod'
import type { StackScreenProps } from '@react-navigation/stack'
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

type Props = StackScreenProps<Record<string, undefined>, string>

// ── OTP step ─────────────────────────────────────────────────────────────────
const OtpStep: React.FC = () => {
  const { verifyOtp, cancelMfa, isLoading, error, clearError, maskedEmail } = useAuthStore()
  const [digits, setDigits] = useState(['', '', '', '', '', ''])
  const inputRefs = useRef<(TextInput | null)[]>([])

  useEffect(() => {
    setTimeout(() => inputRefs.current[0]?.focus(), 200)
  }, [])

  const handleChange = useCallback(
    (idx: number, val: string) => {
      if (!/^\d?$/.test(val)) {
        return
      }
      const next = [...digits]
      next[idx] = val
      setDigits(next)
      if (val && idx < 5) {
        inputRefs.current[idx + 1]?.focus()
      }
      if (next.every((d) => d !== '')) {
        verifyOtp(next.join('')).catch(() => {
          setDigits(['', '', '', '', '', ''])
          setTimeout(() => inputRefs.current[0]?.focus(), 50)
        })
      }
    },
    [digits, verifyOtp],
  )

  const handleKeyPress = useCallback(
    (idx: number, key: string) => {
      if (key === 'Backspace' && !digits[idx] && idx > 0) {
        inputRefs.current[idx - 1]?.focus()
      }
    },
    [digits],
  )

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
        <TouchableOpacity onPress={cancelMfa} style={styles.backButton}>
          <Text style={styles.backButtonText}>← Retour</Text>
        </TouchableOpacity>

        <View style={styles.header}>
          <Text style={styles.logo}>🔐 Vérification</Text>
          <Text style={styles.subtitle}>
            Code envoyé à{' '}
            <Text style={{ color: colors.textPrimary, fontWeight: '700' }}>{maskedEmail}</Text>
          </Text>
          <Text style={[styles.subtitle, { marginTop: spacing.xs }]}>
            Expire dans <Text style={{ color: colors.primary }}>10 minutes</Text>.
          </Text>
        </View>

        {error && (
          <View style={styles.errorBanner}>
            <Text style={styles.errorBannerText}>{error}</Text>
            <TouchableOpacity onPress={clearError}>
              <Text style={[styles.errorBannerText, { marginLeft: spacing.sm }]}>×</Text>
            </TouchableOpacity>
          </View>
        )}

        <View style={styles.otpRow}>
          {digits.map((d, i) => (
            <TextInput
              key={i}
              ref={(el) => {
                inputRefs.current[i] = el
              }}
              style={[styles.otpBox, d ? styles.otpBoxFilled : null]}
              value={d}
              onChangeText={(v) => handleChange(i, v)}
              onKeyPress={({ nativeEvent }) => handleKeyPress(i, nativeEvent.key)}
              keyboardType="number-pad"
              maxLength={1}
              textAlign="center"
              selectTextOnFocus
            />
          ))}
        </View>

        {isLoading ? (
          <FlowGuardLoader />
        ) : (
          <FlowGuardButton
            title="Confirmer"
            onPress={() => verifyOtp(digits.join(''))}
            variant="primary"
            disabled={digits.some((d) => !d)}
          />
        )}
      </ScrollView>
    </SafeAreaView>
  )
}

// ── Login step ───────────────────────────────────────────────────────────────

export const LoginScreen: React.FC<Props> = ({ navigation }) => {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})

  const { login, loginWithBiometric, mfaPending, isLoading, error: authError } = useAuthStore()
  const { isAvailable: biometricAvailable } = useBiometric()
  const enterDemoMode = useAuthStore((s) => s.enterDemoMode)

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
    if (!validate()) {
      return
    }
    await login(email.trim(), password)
  }, [email, password, login, validate])

  const handleBiometric = useCallback(async () => {
    await loginWithBiometric()
  }, [loginWithBiometric])

  if (mfaPending) {
    return <OtpStep />
  }

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
          onPress={() => navigation.navigate(Routes.Register as never)}
          style={styles.registerLink}
        >
          <Text style={styles.registerText}>
            Pas encore de compte ? <Text style={styles.registerTextBold}>Créer un compte</Text>
          </Text>
        </TouchableOpacity>
        <TouchableOpacity onPress={() => enterDemoMode()} style={styles.demoLink}>
          <Text style={styles.demoText}>🎭 Explorer en mode démo</Text>
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
    flexDirection: 'row',
    alignItems: 'center',
  },
  errorBannerText: {
    color: colors.danger,
    fontSize: typography.body.fontSize,
    fontWeight: '600',
    flex: 1,
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
  demoLink: {
    marginTop: spacing.sm,
    alignItems: 'center',
    paddingVertical: spacing.sm,
  },
  demoText: {
    color: colors.textMuted,
    fontSize: 13,
    textDecorationLine: 'underline',
  },
  // OTP styles
  backButton: {
    marginBottom: spacing.xl,
  },
  backButtonText: {
    color: colors.textSecondary,
    fontSize: typography.body.fontSize,
  },
  otpRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: spacing.sm,
    marginBottom: spacing.xl,
  },
  otpBox: {
    width: 48,
    height: 56,
    borderRadius: 12,
    borderWidth: 1.5,
    borderColor: colors.border ?? '#2a2a3e',
    backgroundColor: colors.surface,
    color: colors.textPrimary ?? colors.primary,
    fontSize: 24,
    fontWeight: '800',
  },
  otpBoxFilled: {
    borderColor: colors.primary,
  },
})
