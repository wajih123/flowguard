import React, { useState, useCallback, useRef } from 'react'
import { View, Text, StyleSheet, ActivityIndicator } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { WebView, type WebViewNavigation } from 'react-native-webview'
import { useAuthStore } from '../../store/authStore'
import { flowguardApi } from '../../api/flowguardApi'
import { FlowGuardLoader } from '../../components/FlowGuardLoader'
import { ErrorScreen } from '../../components/ErrorScreen'
import { colors, typography, spacing } from '../../theme'

const KYC_REDIRECT_URI = 'flowguard://kyc/callback'

export const KycScreen: React.FC = () => {
  const [kycUrl, setKycUrl] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const webViewRef = useRef<WebView>(null)
  const { user } = useAuthStore()

  const initKyc = useCallback(async () => {
    try {
      setLoading(true)
      setError(false)
      const response = await flowguardApi.post<{ url: string }>('/kyc/init', {
        userId: user?.id,
        redirectUri: KYC_REDIRECT_URI,
      })
      setKycUrl(response.url)
    } catch {
      setError(true)
    } finally {
      setLoading(false)
    }
  }, [user?.id])

  React.useEffect(() => {
    initKyc()
  }, [initKyc])

  const handleNavigationChange = useCallback((navState: WebViewNavigation) => {
    if (navState.url.startsWith(KYC_REDIRECT_URI)) {
      const url = new URL(navState.url)
      const status = url.searchParams.get('status')
      if (status === 'success') {
        useAuthStore.getState().hydrate()
      }
    }
  }, [])

  if (error) {
    return (
      <ErrorScreen
        message="Impossible d'initialiser la vérification KYC"
        onRetry={initKyc}
      />
    )
  }

  if (loading || !kycUrl) {
    return <FlowGuardLoader />
  }

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Vérification d'identité</Text>
        <Text style={styles.headerSubtitle}>Étape obligatoire pour activer votre compte</Text>
      </View>
      <WebView
        ref={webViewRef}
        source={{ uri: kycUrl }}
        onNavigationStateChange={handleNavigationChange}
        startInLoadingState
        renderLoading={() => (
          <View style={styles.webviewLoading}>
            <ActivityIndicator size="large" color={colors.primary} />
            <Text style={styles.loadingText}>Chargement du formulaire KYC...</Text>
          </View>
        )}
        style={styles.webview}
      />
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  header: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  headerTitle: {
    ...typography.h2,
    color: colors.textPrimary,
    marginBottom: spacing.xs,
  },
  headerSubtitle: {
    color: colors.textSecondary,
    fontSize: typography.caption.fontSize,
  },
  webview: {
    flex: 1,
  },
  webviewLoading: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: colors.background,
    gap: spacing.md,
  },
  loadingText: {
    color: colors.textSecondary,
    fontSize: typography.body.fontSize,
  },
})
