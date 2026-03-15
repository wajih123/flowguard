import React, { useState, useCallback } from 'react'
import { View, Text, FlatList, TouchableOpacity, StyleSheet, RefreshControl } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { useAlerts, useMarkAlertAsRead } from '../../hooks/useAlerts'
import { AlertCard } from './components/AlertCard'
import { FlowGuardLoader } from '../../components/FlowGuardLoader'
import { ErrorScreen } from '../../components/ErrorScreen'
import { EmptyState } from '../../components/EmptyState'
import type { Alert } from '../../domain/Alert'
import { useAccountStore } from '../../store/accountStore'
import { colors, typography, spacing } from '../../theme'

type TabKey = 'all' | 'unread' | 'critical'

const TABS: { key: TabKey; label: string }[] = [
  { key: 'all', label: 'Toutes' },
  { key: 'unread', label: 'Non lues' },
  { key: 'critical', label: 'Critiques' },
]

export const AlertsScreen: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabKey>('all')
  const account = useAccountStore((s) => s.account)
  const { alerts, isLoading, isError, refetch } = useAlerts(account?.id)
  const { mutate: markRead } = useMarkAlertAsRead()

  const filteredAlerts = useCallback((): Alert[] => {
    if (!alerts) return []
    switch (activeTab) {
      case 'unread':
        return alerts.filter((a) => !a.isRead)
      case 'critical':
        return alerts.filter((a) => a.severity === 'CRITICAL')
      default:
        return alerts
    }
  }, [alerts, activeTab])

  const handleMarkRead = useCallback(
    (alertId: string) => {
      markRead(alertId)
    },
    [markRead],
  )

  if (isLoading) return <FlowGuardLoader />
  if (isError) return <ErrorScreen message="Impossible de charger les alertes" onRetry={refetch} />

  const data = filteredAlerts()

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <Text style={styles.title}>Alertes</Text>

      <View style={styles.tabBar}>
        {TABS.map((tab) => {
          const isActive = activeTab === tab.key
          return (
            <TouchableOpacity
              key={tab.key}
              onPress={() => setActiveTab(tab.key)}
              style={[styles.tab, isActive && styles.tabActive]}
            >
              <Text style={[styles.tabText, isActive && styles.tabTextActive]}>{tab.label}</Text>
              {tab.key === 'unread' && alerts && (
                <View style={styles.badge}>
                  <Text style={styles.badgeText}>{alerts.filter((a) => !a.isRead).length}</Text>
                </View>
              )}
            </TouchableOpacity>
          )
        })}
      </View>

      {data.length === 0 ? (
        <EmptyState
          icon="bell-off-outline"
          title="Aucune alerte"
          subtitle={
            activeTab === 'unread'
              ? 'Toutes les alertes ont été lues'
              : activeTab === 'critical'
                ? 'Aucune alerte critique en cours'
                : "Pas d'alertes pour le moment"
          }
        />
      ) : (
        <FlatList<Alert>
          data={data}
          keyExtractor={(item) => item.id}
          renderItem={({ item }) => (
            <AlertCard alert={item} onMarkRead={() => handleMarkRead(item.id)} />
          )}
          contentContainerStyle={styles.list}
          showsVerticalScrollIndicator={false}
          refreshControl={
            <RefreshControl
              refreshing={false}
              onRefresh={() => refetch()}
              tintColor={colors.primary}
            />
          }
        />
      )}
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  title: {
    ...typography.h1,
    color: colors.textPrimary,
    paddingHorizontal: spacing.md,
    paddingTop: spacing.md,
    marginBottom: spacing.md,
  },
  tabBar: {
    flexDirection: 'row',
    paddingHorizontal: spacing.md,
    gap: spacing.sm,
    marginBottom: spacing.md,
  },
  tab: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    borderRadius: 20,
    backgroundColor: colors.surface,
    gap: spacing.xs,
  },
  tabActive: {
    backgroundColor: colors.primary + '30',
  },
  tabText: {
    color: colors.textSecondary,
    fontSize: typography.caption.fontSize,
    fontWeight: '600',
  },
  tabTextActive: {
    color: colors.primary,
  },
  badge: {
    backgroundColor: colors.danger,
    borderRadius: 10,
    minWidth: 20,
    height: 20,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 6,
  },
  badgeText: {
    color: colors.textPrimary,
    fontSize: 11,
    fontWeight: '700',
  },
  list: {
    paddingHorizontal: spacing.md,
    paddingBottom: spacing.xxl,
  },
})
