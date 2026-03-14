import React, { useState, useCallback, useRef } from 'react'
import { View, Text, StyleSheet, ActivityIndicator } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { WebView, type WebViewNavigation } from 'react-native-webview'
import type { NativeStackScreenProps } from '@react-navigation/native-stack'
import { FlowGuardButton } from '../../components/FlowGuardButton'
import { ErrorScreen } from '../../components/ErrorScreen'
import { useAccountStore } from '../../store/accountStore'
import { nordigenApi } from '../../api/nordigenApi'
import { colors, typography, spacing } from '../../theme'

const REDIRECT_URI = 'flowguard://bank-connect/callback'

type Props = NativeStackScreenProps<Record<string, undefined>, string>

export const BankConnectScreen: React.FC<Props> = ({ navigation }) => {
  const [step, setStep] = useState<'select' | 'webview' | 'success' | 'error'>('select')
  const [connectUrl, setConnectUrl] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const { fetchAccount } = useAccountStore()
  const webViewRef = useRef<WebView>(null)

  const handleConnect = useCallback(async () => {
    try {
      setLoading(true)
      await nordigenApi.authenticate()
      const requisition = await nordigenApi.createRequisition(REDIRECT_URI)
      setConnectUrl(requisition.link)
      setStep('webview')
    } catch {
      setStep('error')
    } finally {
      setLoading(false)
    }
  }, [])

  const handleNavigationChange = useCallback(
    (navState: WebViewNavigation) => {
      if (navState.url.startsWith(REDIRECT_URI)) {
        setStep('success')
        fetchAccount()
      }
    },
    [fetchAccount],
  )

  if (step === 'error') {
    return (
      <ErrorScreen
        message="Impossible de se connecter à la banque"
        onRetry={() => setStep('select')}
      />
    )
  }

  if (step === 'success') {
    return (
      <SafeAreaView style={styles.container} edges={['top']}>
        <View style={styles.successContainer}>
          <Text style={styles.successIcon}>🏦</Text>
          <Text style={styles.successTitle}>Compte connecté !</Text>
          <Text style={styles.successSubtitle}>
            Votre compte bancaire a été relié avec succès via Open Banking
          </Text>
          <FlowGuardButton
            title="Continuer"
            onPress={() => navigation.goBack()}
            variant="primary"
          />
        </View>
      </SafeAreaView>
    )
  }

  if (step === 'webview' && connectUrl) {
    return (
      <SafeAreaView style={styles.container} edges={['top']}>
        <View style={styles.webviewHeader}>
          <Text
            onPress={() => setStep('select')}
            style={styles.cancelText}
          >
            Annuler
          </Text>
          <Text style={styles.webviewTitle}>Connexion bancaire</Text>
          <View style={styles.headerSpacer} />
        </View>
        <WebView
          ref={webViewRef}
          source={{ uri: connectUrl }}
          onNavigationStateChange={handleNavigationChange}
          startInLoadingState
          renderLoading={() => (
            <View style={styles.webviewLoading}>
              <ActivityIndicator size="large" color={colors.primary} />
            </View>
          )}
          style={styles.webview}
        />
      </SafeAreaView>
    )
  }

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.selectContainer}>
        <Text style={styles.title}>Connecter votre banque</Text>
        <Text style={styles.subtitle}>
          Reliez votre compte bancaire via Open Banking (DSP2) pour activer les prévisions de
          trésorerie et les alertes intelligentes.
        </Text>

        <View style={styles.infoCard}>
          <Text style={styles.infoIcon}>🔒</Text>
          <View style={styles.infoContent}>
            <Text style={styles.infoTitle}>Connexion sécurisée</Text>
            <Text style={styles.infoText}>
              Nous utilisons Nordigen (GoCardless) comme intermédiaire certifié. Vos identifiants
              bancaires ne transitent jamais par nos serveurs.
            </Text>
          </View>
        </View>

        <View style={styles.features}>
          {[
            { icon: '📊', label: 'Prévisions de trésorerie IA' },
            { icon: '🔔', label: 'Alertes proactives J-7' },
            { icon: '💡', label: 'Analyse des dépenses' },
            { icon: '⚡', label: 'Accès au crédit flash' },
          ].map((feature) => (
            <View key={feature.label} style={styles.featureRow}>
              <Text style={styles.featureIcon}>{feature.icon}</Text>
              <Text style={styles.featureLabel}>{feature.label}</Text>
            </View>
          ))}
        </View>

        <View style={styles.buttonWrapper}>
          <FlowGuardButton
            title={loading ? 'Connexion...' : 'Connecter ma banque'}
            onPress={handleConnect}
            variant="primary"
            disabled={loading}
          />
        </View>
      </View>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  selectContainer: {
    flex: 1,
    paddingHorizontal: spacing.md,
    paddingTop: spacing.lg,
  },
  title: {
    ...typography.h1,
    color: colors.textPrimary,
    marginBottom: spacing.sm,
  },
  subtitle: {
    color: colors.textSecondary,
    fontSize: typography.body.fontSize,
    lineHeight: 22,
    marginBottom: spacing.lg,
  },
  infoCard: {
    flexDirection: 'row',
    backgroundColor: colors.surface,
    borderRadius: 12,
    padding: spacing.md,
    gap: spacing.sm,
    marginBottom: spacing.lg,
  },
  infoIcon: {
    fontSize: 24,
    marginTop: 2,
  },
  infoContent: {
    flex: 1,
  },
  infoTitle: {
    color: colors.textPrimary,
    fontSize: typography.body.fontSize,
    fontWeight: '700',
    marginBottom: spacing.xs,
  },
  infoText: {
    color: colors.textSecondary,
    fontSize: typography.caption.fontSize,
    lineHeight: 18,
  },
  features: {
    gap: spacing.md,
    marginBottom: spacing.xl,
  },
  featureRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
  },
  featureIcon: {
    fontSize: 20,
  },
  featureLabel: {
    color: colors.textPrimary,
    fontSize: typography.body.fontSize,
  },
  buttonWrapper: {
    marginTop: 'auto',
    paddingBottom: spacing.lg,
  },
  webviewHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  cancelText: {
    color: colors.primary,
    fontSize: typography.body.fontSize,
    fontWeight: '600',
  },
  webviewTitle: {
    color: colors.textPrimary,
    fontSize: typography.body.fontSize,
    fontWeight: '700',
  },
  headerSpacer: {
    width: 60,
  },
  webview: {
    flex: 1,
  },
  webviewLoading: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: colors.background,
  },
  successContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: spacing.lg,
  },
  successIcon: {
    fontSize: 64,
    marginBottom: spacing.lg,
  },
  successTitle: {
    ...typography.h1,
    color: colors.success,
    marginBottom: spacing.sm,
  },
  successSubtitle: {
    color: colors.textSecondary,
    fontSize: typography.body.fontSize,
    textAlign: 'center',
    marginBottom: spacing.xl,
  },
})
