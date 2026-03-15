import React, { useState, useCallback } from 'react'
import { View, Text, ScrollView, StyleSheet, TouchableOpacity } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { z } from 'zod'
import type { StackScreenProps } from '@react-navigation/stack'
import { FlowGuardButton } from '../../components/FlowGuardButton'
import { FlowGuardInput } from '../../components/FlowGuardInput'
import { FlowGuardCard } from '../../components/FlowGuardCard'
import { Routes } from '../../navigation/routes'
import { colors, typography, spacing } from '../../theme'
import api from '../../api/flowguardApi'

const schema = z.object({
  email: z.string().email('Email invalide'),
})

type Props = StackScreenProps<Record<string, undefined>, string>

export const ForgotPasswordScreen: React.FC<Props> = ({ navigation }) => {
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)
  const [loading, setLoading] = useState(false)

  const handleSubmit = useCallback(async () => {
    const result = schema.safeParse({ email })
    if (!result.success) {
      setError(result.error.errors[0]?.message ?? 'Email invalide')
      return
    }
    setError(null)
    setLoading(true)
    try {
      await api.post('/api/auth/forgot-password', { email: email.trim() })
      setSuccess(true)
    } catch {
      setError("Impossible d'envoyer l'email. Vérifiez votre connexion.")
    } finally {
      setLoading(false)
    }
  }, [email])

  if (success) {
    return (
      <SafeAreaView style={styles.container} edges={['top']}>
        <View style={styles.successContainer}>
          <Text style={styles.successIcon}>📬</Text>
          <Text style={styles.successTitle}>Email envoyé !</Text>
          <Text style={styles.successText}>
            Consultez votre boîte email pour réinitialiser votre mot de passe.
          </Text>
          <FlowGuardButton
            label="Retour à la connexion"
            onPress={() => navigation.navigate(Routes.Login as never)}
            variant="primary"
          />
        </View>
      </SafeAreaView>
    )
  }

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView
        contentContainerStyle={styles.scroll}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      >
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Text style={styles.backText}>← Retour</Text>
        </TouchableOpacity>

        <Text style={styles.title}>Mot de passe oublié ?</Text>
        <Text style={styles.subtitle}>
          Entrez votre email et nous vous enverrons un lien pour réinitialiser votre mot de passe.
        </Text>

        {error && (
          <FlowGuardCard variant="alert" style={styles.errorCard}>
            <Text style={styles.errorText}>{error}</Text>
          </FlowGuardCard>
        )}

        <FlowGuardInput
          label="Email"
          value={email}
          onChangeText={setEmail}
          keyboardType="email-address"
          autoCapitalize="none"
          error={undefined}
        />

        <FlowGuardButton
          label="Envoyer le lien"
          onPress={handleSubmit}
          variant="primary"
          loading={loading}
          style={styles.btn}
        />
      </ScrollView>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  scroll: { padding: spacing.md, paddingTop: spacing.lg, paddingBottom: spacing.xxl },
  backBtn: { marginBottom: spacing.lg },
  backText: { color: colors.primary, fontSize: typography.body.fontSize, fontWeight: '600' },
  title: { ...typography.h1, color: colors.textPrimary, marginBottom: spacing.sm },
  subtitle: {
    color: colors.textSecondary,
    fontSize: typography.body.fontSize,
    lineHeight: 22,
    marginBottom: spacing.xl,
  },
  errorCard: { marginBottom: spacing.md },
  errorText: { color: colors.danger, fontSize: typography.body.fontSize },
  btn: { marginTop: spacing.md },
  successContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: spacing.xl,
  },
  successIcon: { fontSize: 64, marginBottom: spacing.lg },
  successTitle: {
    ...typography.h1,
    color: colors.textPrimary,
    marginBottom: spacing.md,
    textAlign: 'center',
  },
  successText: {
    color: colors.textSecondary,
    textAlign: 'center',
    fontSize: typography.body.fontSize,
    lineHeight: 24,
    marginBottom: spacing.xl,
  },
})
