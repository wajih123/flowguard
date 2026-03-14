import React, { useEffect, useRef, useState } from 'react'
import { StatusBar, View, ActivityIndicator, StyleSheet } from 'react-native'
import { NavigationContainer } from '@react-navigation/native'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { GestureHandlerRootView } from 'react-native-gesture-handler'
import { SafeAreaProvider } from 'react-native-safe-area-context'
import { PaperProvider } from 'react-native-paper'
import { RootNavigator } from './src/navigation/RootNavigator'
import { useAuthStore } from './src/store/authStore'
import { useFeatureFlagStore } from './src/store/featureFlagStore'
import { colors } from './src/theme'
import {
  requestPermission,
  registerFcmToken,
  setupForegroundHandler,
  setupBackgroundHandler,
} from './src/services/notifications'

setupBackgroundHandler()

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 2,
      refetchOnWindowFocus: false,
    },
  },
})

const App: React.FC = () => {
  const [isReady, setIsReady] = useState(false)
  const hydrate = useAuthStore((s) => s.hydrate)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const fetchFlags = useFeatureFlagStore((s) => s.fetchFlags)
  const notifCleanupRef = useRef<(() => void) | null>(null)

  useEffect(() => {
    const bootstrap = async () => {
      await hydrate()
      await fetchFlags()
      setIsReady(true)
    }
    void bootstrap()
  }, [hydrate, fetchFlags])

  useEffect(() => {
    if (!isAuthenticated) return
    const run = async () => {
      const granted = await requestPermission()
      if (granted) {
        await registerFcmToken()
        notifCleanupRef.current = setupForegroundHandler()
      }
    }
    void run()
    return () => {
      notifCleanupRef.current?.()
      notifCleanupRef.current = null
    }
  }, [isAuthenticated])

  if (!isReady) {
    return (
      <View style={styles.splash}>
        <ActivityIndicator size="large" color={colors.primary} />
      </View>
    )
  }

  return (
    <GestureHandlerRootView style={styles.root}>
      <SafeAreaProvider>
        <PaperProvider>
          <QueryClientProvider client={queryClient}>
            <NavigationContainer
              theme={{
                dark: true,
                colors: {
                  primary: colors.primary,
                  background: colors.background,
                  card: colors.surface,
                  text: colors.textPrimary,
                  border: colors.cardBorder,
                  notification: colors.danger,
                },
              }}
            >
              <StatusBar barStyle="light-content" backgroundColor={colors.background} />
              <RootNavigator />
            </NavigationContainer>
          </QueryClientProvider>
        </PaperProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  )
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: colors.background,
  },
  splash: {
    flex: 1,
    backgroundColor: colors.background,
    justifyContent: 'center',
    alignItems: 'center',
  },
})

export default App
