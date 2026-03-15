import React, { useCallback, useState } from 'react'
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, RefreshControl } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import type { StackScreenProps } from '@react-navigation/stack'
import { FlowGuardCard } from '../../components/FlowGuardCard'
import { FlowGuardButton } from '../../components/FlowGuardButton'
import { FlowGuardLoader } from '../../components/FlowGuardLoader'
import { ErrorScreen } from '../../components/ErrorScreen'
import { SkeletonCard } from '../../components/SkeletonCard'
import { useAccountStore } from '../../store/accountStore'
import { useBankStore } from '../../store/bankStore'
import { Routes } from '../../navigation/routes'
import { colors, typography, spacing } from '../../theme'
import * as flowguardApi from '../../api/flowguardApi'
import type { BankAccount } from '../../domain/Account'
import { format } from 'date-fns'
import { fr } from 'date-fns/locale'

type Props = StackScreenProps<Record<string, undefined>, typeof Routes.BankAccount>

const statusLabel: Record<string, string> = {
  ACTIVE: 'Actif',
  EXPIRED: 'Expiré',
  PENDING: 'En attente',
}

const statusColor: Record<string, string> = {
  ACTIVE: colors.success,
  EXPIRED: colors.danger,
  PENDING: colors.warning,
}

const fmtEur = (val: number) =>
  new Intl.NumberFormat('fr-FR', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 0,
  }).format(val)

export const BankAccountScreen: React.FC<Props> = ({ navigation }) => {
  const queryClient = useQueryClient()
  const [refreshing, setRefreshing] = useState(false)
  const setSyncing = useBankStore((s) => s.setSyncing)
  const isSyncing = useBankStore((s) => s.isSyncing)
  const lastSyncAt = useBankStore((s) => s.lastSyncAt)
  const setLastSync = useBankStore((s) => s.setLastSync)
  const accountFromStore = useAccountStore((s) => s.account)

  const {
    data: accounts,
    isLoading,
    isError,
    refetch,
  } = useQuery<BankAccount[]>({
    queryKey: ['bank-accounts'],
    queryFn: () => flowguardApi.getAccounts(),
    staleTime: 5 * 60 * 1000,
  })

  const { mutate: syncMutate } = useMutation({
    mutationFn: (accountId: string) => flowguardApi.syncBank(accountId),
    onMutate: () => setSyncing(true),
    onSettled: () => {
      setSyncing(false)
      setLastSync(new Date())
      queryClient.invalidateQueries({ queryKey: ['bank-accounts'] })
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
    },
  })

  const onRefresh = useCallback(async () => {
    setRefreshing(true)
    await refetch()
    setRefreshing(false)
  }, [refetch])

  if (isLoading) {
    return (
      <SafeAreaView style={styles.container} edges={['top']}>
        <View style={styles.header}>
          <Text style={styles.title}>Comptes bancaires</Text>
        </View>
        <View style={styles.skeletons}>
          {[0, 1, 2].map((i) => (
            <SkeletonCard key={i} height={80} />
          ))}
        </View>
      </SafeAreaView>
    )
  }

  if (isError) {
    return <ErrorScreen message="Impossible de charger vos comptes" onRetry={refetch} />
  }

  const list = accounts ?? []

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView
        contentContainerStyle={styles.scroll}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            tintColor={colors.primary}
            colors={[colors.primary]}
          />
        }
      >
        <View style={styles.header}>
          <Text style={styles.title}>Comptes bancaires</Text>
          {lastSyncAt && (
            <Text style={styles.syncDate}>
              Sync. {format(lastSyncAt, 'dd/MM HH:mm', { locale: fr })}
            </Text>
          )}
        </View>

        {list.length === 0 ? (
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyText}>Aucun compte connecté</Text>
            <FlowGuardButton
              label="Connecter une banque"
              onPress={() => navigation.navigate(Routes.BankConnect as never)}
              variant="primary"
              style={styles.connectBtn}
            />
          </View>
        ) : (
          list.map((account) => (
            <FlowGuardCard key={account.id} style={styles.accountCard}>
              <View style={styles.cardHeader}>
                <Text style={styles.bankName}>{account.bankName ?? 'Banque'}</Text>
                <View
                  style={[
                    styles.statusBadge,
                    {
                      backgroundColor:
                        (statusColor[account.connectionStatus] ?? colors.primary) + '22',
                    },
                  ]}
                >
                  <Text
                    style={[
                      styles.statusText,
                      { color: statusColor[account.connectionStatus] ?? colors.primary },
                    ]}
                  >
                    {statusLabel[account.connectionStatus] ?? account.connectionStatus}
                  </Text>
                </View>
              </View>

              <Text style={styles.iban}>{account.iban}</Text>

              <View style={styles.balanceRow}>
                <View>
                  <Text style={styles.balanceLabel}>Solde</Text>
                  <Text style={styles.balanceValue}>{fmtEur(account.balance)}</Text>
                </View>
                {account.lastSyncAt && (
                  <View style={styles.rightCol}>
                    <Text style={styles.balanceLabel}>Dernière sync.</Text>
                    <Text style={styles.balanceSub}>
                      {(() => {
                        try {
                          return format(new Date(account.lastSyncAt!), 'dd/MM à HH:mm', {
                            locale: fr,
                          })
                        } catch {
                          return '—'
                        }
                      })()}
                    </Text>
                  </View>
                )}
              </View>

              {account.connectionStatus === 'EXPIRED' && (
                <FlowGuardCard variant="alert" style={styles.expiredCard}>
                  <Text style={styles.expiredText}>
                    Connexion expirée — reconnectez votre banque.
                  </Text>
                </FlowGuardCard>
              )}

              <View style={styles.cardActions}>
                <TouchableOpacity
                  style={styles.syncBtn}
                  onPress={() => syncMutate(account.id)}
                  disabled={isSyncing}
                >
                  <Text style={styles.syncBtnText}>{isSyncing ? 'Sync…' : '↻ Synchroniser'}</Text>
                </TouchableOpacity>
                {account.connectionStatus === 'EXPIRED' && (
                  <TouchableOpacity
                    style={styles.reconnectBtn}
                    onPress={() => navigation.navigate(Routes.BankConnect as never)}
                  >
                    <Text style={styles.reconnectBtnText}>Reconnecter</Text>
                  </TouchableOpacity>
                )}
              </View>
            </FlowGuardCard>
          ))
        )}

        <FlowGuardButton
          label="Ajouter un compte"
          onPress={() => navigation.navigate(Routes.BankConnect as never)}
          variant="outline"
          style={styles.addBtn}
        />
      </ScrollView>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  scroll: { padding: spacing.md, paddingBottom: spacing.xxl },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: spacing.md,
  },
  title: { ...typography.h2, color: colors.textPrimary },
  syncDate: { ...typography.caption, color: colors.textMuted },
  skeletons: { padding: spacing.md, gap: spacing.sm },
  emptyContainer: { alignItems: 'center', paddingTop: spacing.xxl },
  emptyText: { color: colors.textSecondary, ...typography.body, marginBottom: spacing.md },
  connectBtn: { width: 220 },
  accountCard: { marginBottom: spacing.md },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: spacing.xs,
  },
  bankName: { ...typography.h3, color: colors.textPrimary },
  statusBadge: { borderRadius: 8, paddingHorizontal: spacing.sm, paddingVertical: 2 },
  statusText: { ...typography.caption, fontWeight: '700' },
  iban: {
    ...typography.caption,
    color: colors.textMuted,
    fontFamily: 'monospace',
    marginBottom: spacing.sm,
  },
  balanceRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: spacing.sm },
  balanceLabel: { ...typography.caption, color: colors.textSecondary },
  balanceValue: { ...typography.h2, color: colors.primary },
  rightCol: { alignItems: 'flex-end' },
  balanceSub: { ...typography.caption, color: colors.textSecondary },
  expiredCard: { marginBottom: spacing.sm },
  expiredText: { color: colors.danger, ...typography.caption },
  cardActions: { flexDirection: 'row', gap: spacing.sm },
  syncBtn: {
    flex: 1,
    backgroundColor: colors.primary + '22',
    borderRadius: 8,
    paddingVertical: spacing.sm,
    alignItems: 'center',
  },
  syncBtnText: { color: colors.primary, ...typography.body, fontWeight: '700' },
  reconnectBtn: {
    flex: 1,
    backgroundColor: colors.warning + '22',
    borderRadius: 8,
    paddingVertical: spacing.sm,
    alignItems: 'center',
  },
  reconnectBtnText: { color: colors.warning, ...typography.body, fontWeight: '700' },
  addBtn: { marginTop: spacing.md },
})
