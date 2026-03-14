import React, { useRef, useCallback } from 'react'
import { View, Text, StyleSheet, TouchableOpacity, ActivityIndicator } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import WebView from 'react-native-webview'
import type { WebViewNavigation } from 'react-native-webview'
import type { StackScreenProps } from '@react-navigation/stack'
import * as Keychain from 'react-native-keychain'
import { Routes } from '../../navigation/routes'
import { colors, typography, spacing } from '../../theme'

type Props = StackScreenProps<Record<string, undefined>, typeof Routes.AdminWeb>

const ADMIN_WEB_URL = 'https://app.flowguard.fr/admin'

export const AdminWebScreen: React.FC<Props> = ({ navigation }) => {
  const webViewRef = useRef<WebView>(null)
  // Build injected JS to set Authorization header via meta tag + fetch override
  const buildInjectedJS = useCallback((token: string) => {
    const escaped = token.replace(/'/g, "\\'")
    return `
      (function() {
        const originalFetch = window.fetch;
        window.fetch = function(input, init) {
          init = init || {};
          init.headers = Object.assign({ 'Authorization': 'Bearer ${escaped}' }, init.headers || {});
          return originalFetch(input, init);
        };
        const originalXHROpen = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function() {
          this.addEventListener('readystatechange', function() {
            if (this.readyState === 1) {
              this.setRequestHeader('Authorization', 'Bearer ${escaped}');
            }
          });
          return originalXHROpen.apply(this, arguments);
        };
        true;
      })();
    `
  }, [])

  const [injectedJS, setInjectedJS] = React.useState<string>('')
  const [loading, setLoading] = React.useState(true)
  const [error, setError] = React.useState(false)

  React.useEffect(() => {
    ;(async () => {
      const creds = await Keychain.getGenericPassword({ service: 'fg' }).catch(() => null)
      if (!creds) return
      let token: string
      try {
        const parsed = JSON.parse(creds.password) as { accessToken?: string }
        token = parsed.accessToken ?? creds.password
      } catch {
        token = creds.password
      }
      setInjectedJS(buildInjectedJS(token))
    })()
  }, [buildInjectedJS])

  const handleNavigationChange = useCallback((navState: WebViewNavigation) => {
    // If navigated away from admin area, allow back
    if (!navState.url.startsWith('https://app.flowguard.fr')) {
      webViewRef.current?.stopLoading()
      webViewRef.current?.goBack()
    }
  }, [])

  if (!injectedJS) {
    return (
      <SafeAreaView style={styles.container} edges={['top']}>
        <ActivityIndicator color={colors.primary} style={styles.centered} />
      </SafeAreaView>
    )
  }

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <View style={styles.topBar}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
          <Text style={styles.backText}>← Retour</Text>
        </TouchableOpacity>
        <Text style={styles.topTitle}>Administration web</Text>
        <TouchableOpacity onPress={() => webViewRef.current?.reload()} style={styles.reloadBtn}>
          <Text style={styles.reloadText}>↻</Text>
        </TouchableOpacity>
      </View>

      {error ? (
        <View style={styles.errorContainer}>
          <Text style={styles.errorText}>Impossible de charger l'interface web.</Text>
          <TouchableOpacity
            onPress={() => {
              setError(false)
              webViewRef.current?.reload()
            }}
            style={styles.retryBtn}
          >
            <Text style={styles.retryText}>Réessayer</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <>
          {loading && (
            <View style={styles.overlay}>
              <ActivityIndicator color={colors.primary} size="large" />
              <Text style={styles.loadingText}>Chargement…</Text>
            </View>
          )}
          <WebView
            ref={webViewRef}
            source={{ uri: ADMIN_WEB_URL }}
            injectedJavaScriptBeforeContentLoaded={injectedJS}
            onNavigationStateChange={handleNavigationChange}
            onLoadStart={() => setLoading(true)}
            onLoadEnd={() => setLoading(false)}
            onError={() => setError(true)}
            javaScriptEnabled
            domStorageEnabled
            sharedCookiesEnabled
            style={styles.webView}
          />
        </>
      )}
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.cardBorder,
  },
  backBtn: {},
  backText: { color: colors.primary, ...typography.body, fontWeight: '600' },
  topTitle: { ...typography.body, color: colors.textPrimary, fontWeight: '700' },
  reloadBtn: {},
  reloadText: { color: colors.primary, fontSize: 20 },
  webView: { flex: 1 },
  centered: { flex: 1 },
  overlay: {
    position: 'absolute',
    top: 60,
    left: 0,
    right: 0,
    bottom: 0,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: colors.background,
    zIndex: 10,
  },
  loadingText: { color: colors.textSecondary, ...typography.body, marginTop: spacing.sm },
  errorContainer: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl },
  errorText: {
    color: colors.textSecondary,
    ...typography.body,
    textAlign: 'center',
    marginBottom: spacing.md,
  },
  retryBtn: {
    backgroundColor: colors.primary,
    borderRadius: 12,
    paddingHorizontal: spacing.xl,
    paddingVertical: spacing.sm,
  },
  retryText: { color: colors.background, ...typography.body, fontWeight: '700' },
})
