import React from 'react'
import { View, ActivityIndicator, StyleSheet } from 'react-native'
import { useAuthStore } from '../store/authStore'
import { AuthNavigator } from './AuthNavigator'
import { UserNavigator } from './UserNavigator'
import { BusinessNavigator } from './BusinessNavigator'
import { AdminNavigator } from './AdminNavigator'
import { DemoBanner } from '../components/DemoBanner'
import { OfflineBanner } from '../components/OfflineBanner'
import { colors } from '../theme'

export const RootNavigator: React.FC = () => {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const isLoading = useAuthStore((s) => s.isLoading)
  const user = useAuthStore((s) => s.user)

  if (isLoading) {
    return (
      <View style={styles.loading}>
        <ActivityIndicator size="large" color={colors.primary} />
      </View>
    )
  }

  if (!isAuthenticated || !user) {
    return <AuthNavigator />
  }

  const Navigator = (() => {
    switch (user.role) {
      case 'ROLE_BUSINESS':
        return <BusinessNavigator />
      case 'ROLE_ADMIN':
      case 'ROLE_SUPER_ADMIN':
        return <AdminNavigator />
      case 'ROLE_USER':
      default:
        return <UserNavigator />
    }
  })()

  return (
    <View style={{ flex: 1 }}>
      <DemoBanner />
      {Navigator}
      <OfflineBanner />
    </View>
  )
}

const styles = StyleSheet.create({
  loading: {
    flex: 1,
    backgroundColor: colors.background,
    justifyContent: 'center',
    alignItems: 'center',
  },
})
