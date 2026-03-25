import React from 'react'
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native'
import { useAuthStore } from '../store/authStore'
import { colors, spacing } from '../theme'

export const DemoBanner: React.FC = () => {
  const isDemoMode = useAuthStore((s) => s.isDemoMode)
  const logout = useAuthStore((s) => s.logout)

  if (!isDemoMode) {
    return null
  }

  return (
    <View style={styles.banner}>
      <Text style={styles.text}>🎭 Mode Démo · Données fictives</Text>
      <TouchableOpacity
        onPress={logout}
        style={styles.exitBtn}
        accessibilityLabel="Quitter le mode démo"
      >
        <Text style={styles.exitText}>Quitter</Text>
      </TouchableOpacity>
    </View>
  )
}

const styles = StyleSheet.create({
  banner: {
    backgroundColor: colors.warning,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 6,
    paddingHorizontal: spacing.md,
  },
  text: { color: '#fff', fontSize: 12, fontWeight: '600', flex: 1 },
  exitBtn: {
    paddingVertical: 3,
    paddingHorizontal: 12,
    backgroundColor: 'rgba(0,0,0,0.2)',
    borderRadius: 8,
  },
  exitText: { color: '#fff', fontSize: 12, fontWeight: '700' },
})
