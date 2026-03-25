import React, { useCallback, useState } from 'react'
import {
  View,
  Text,
  SectionList,
  TouchableOpacity,
  StyleSheet,
  RefreshControl,
  ActivityIndicator,
  Alert,
} from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import * as DocumentPicker from 'expo-document-picker'
import type { StackScreenProps } from '@react-navigation/stack'
import { FlowGuardLoader } from '../../components/FlowGuardLoader'
import { ErrorScreen } from '../../components/ErrorScreen'
import { EmptyState } from '../../components/EmptyState'
import { useAccountStore } from '../../store/accountStore'
import { useTransactions } from '../../hooks/useTransactions'
import { importStatement } from '../../api/flowguardApi'
import { Routes } from '../../navigation/routes'
import { colors, typography, spacing } from '../../theme'
import type { Transaction } from '../../domain/Transaction'
import { format, parseISO } from 'date-fns'
import { fr } from 'date-fns/locale'

type Props = StackScreenProps<
  Record<string, { transaction: Transaction }>,
  typeof Routes.Transactions
>

type FilterType = 'all' | 'DEBIT' | 'CREDIT'

const FILTERS: { key: FilterType; label: string }[] = [
  { key: 'all', label: 'Toutes' },
  { key: 'DEBIT', label: 'Dépenses' },
  { key: 'CREDIT', label: 'Revenus' },
]

const fmtEur = (val: number) =>
  new Intl.NumberFormat('fr-FR', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 2,
  }).format(val)

function groupByMonth(transactions: Transaction[]): { title: string; data: Transaction[] }[] {
  const map = new Map<string, Transaction[]>()
  for (const tx of transactions) {
    const key = (() => {
      try {
        return format(parseISO(tx.date), 'MMMM yyyy', { locale: fr })
      } catch {
        return 'Date inconnue'
      }
    })()
    const existing = map.get(key) ?? []
    existing.push(tx)
    map.set(key, existing)
  }
  return Array.from(map.entries()).map(([title, data]) => ({ title, data }))
}

export const TransactionsScreen: React.FC<Props> = ({ navigation }) => {
  const account = useAccountStore((s) => s.account)
  const [filter, setFilter] = useState<FilterType>('all')
  const [refreshing, setRefreshing] = useState(false)
  const [importing, setImporting] = useState(false)

  const {
    transactions,
    isLoading,
    isError,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    refetch,
  } = useTransactions(account?.id)

  const filtered = filter === 'all' ? transactions : transactions.filter((t) => t.type === filter)
  const sections = groupByMonth(filtered)

  const onRefresh = useCallback(async () => {
    setRefreshing(true)
    await refetch()
    setRefreshing(false)
  }, [refetch])

  const onEndReached = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) {
      void fetchNextPage()
    }
  }, [hasNextPage, isFetchingNextPage, fetchNextPage])

  const handleImport = useCallback(async () => {
    if (!account?.id || importing) return
    try {
      const result = await DocumentPicker.getDocumentAsync({
        type: [
          'application/pdf',
          'text/csv',
          'application/vnd.ms-excel',
          'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          // OFX / QIF / MT940 / CFONB are plain-text — exposed as */*
          '*/*',
        ],
        copyToCacheDirectory: true,
        multiple: false,
      })

      if (result.canceled || !result.assets?.length) return
      const asset = result.assets[0]

      setImporting(true)
      const res = await importStatement(account.id, {
        uri: asset.uri,
        name: asset.name,
        mimeType: asset.mimeType ?? undefined,
      })
      await refetch()
      Alert.alert(
        'Import terminé',
        `${res.imported} transaction(s) importée(s), ${res.skipped} ignorée(s).\nFormat détecté\u00a0: ${res.format}`,
      )
    } catch (err) {
      Alert.alert(
        'Erreur',
        "Impossible d'importer le relevé bancaire. Vérifiez le format du fichier.",
      )
    } finally {
      setImporting(false)
    }
  }, [account?.id, importing, refetch])

  if (isLoading) {
    return <FlowGuardLoader />
  }
  if (isError) {
    return <ErrorScreen message="Impossible de charger les transactions" onRetry={refetch} />
  }

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <Text style={styles.screenTitle}>Transactions</Text>
      {/* Import bank statement button */}
      <TouchableOpacity
        onPress={() => {
          void handleImport()
        }}
        disabled={importing || !account?.id}
        style={[styles.importBtn, (importing || !account?.id) && styles.importBtnDisabled]}
      >
        {importing ? (
          <ActivityIndicator size="small" color={colors.primary} />
        ) : (
          <Text style={styles.importBtnText}>↑ Importer un relevé bancaire</Text>
        )}
      </TouchableOpacity>{' '}
      <Text style={styles.importHint}>
        PDF, OFX, QIF, MT940, CFONB, XLSX, CSV — analyse IA automatique
      </Text>
      <View style={styles.filterRow}>
        {FILTERS.map((f) => (
          <TouchableOpacity
            key={f.key}
            onPress={() => setFilter(f.key)}
            style={[styles.filterBtn, filter === f.key && styles.filterBtnActive]}
          >
            <Text style={[styles.filterText, filter === f.key && styles.filterTextActive]}>
              {f.label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>
      <SectionList
        sections={sections}
        keyExtractor={(item) => item.id}
        renderSectionHeader={({ section }) => (
          <View style={styles.sectionHeader}>
            <Text style={styles.sectionTitle}>{section.title}</Text>
          </View>
        )}
        renderItem={({ item }) => (
          <TouchableOpacity
            onPress={() =>
              // eslint-disable-next-line @typescript-eslint/no-explicit-any
              (navigation as any).navigate(Routes.TransactionDetail, { transaction: item })
            }
            style={styles.txRow}
          >
            <View style={styles.txLeft}>
              <Text style={styles.txLabel} numberOfLines={1}>
                {item.label}
              </Text>
              {item.creditorName && item.creditorName !== item.label && (
                <Text style={styles.txSub} numberOfLines={1}>
                  {item.creditorName}
                </Text>
              )}
              <Text style={styles.txDate}>
                {(() => {
                  try {
                    return format(parseISO(item.date), 'dd/MM/yyyy')
                  } catch {
                    return item.date
                  }
                })()}
                {item.status === 'PENDING' && <Text style={styles.pending}> • En attente</Text>}
              </Text>
            </View>
            <Text style={[styles.txAmount, item.type === 'CREDIT' ? styles.credit : styles.debit]}>
              {item.type === 'CREDIT' ? '+' : '-'}
              {fmtEur(Math.abs(item.amount))}
            </Text>
          </TouchableOpacity>
        )}
        ListEmptyComponent={<EmptyState message="Aucune transaction" />}
        ListFooterComponent={
          isFetchingNextPage ? (
            <ActivityIndicator color={colors.primary} style={styles.footer} />
          ) : null
        }
        onEndReached={onEndReached}
        onEndReachedThreshold={0.3}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            tintColor={colors.primary}
            colors={[colors.primary]}
          />
        }
        contentContainerStyle={styles.listContent}
        stickySectionHeadersEnabled
      />
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  screenTitle: {
    ...typography.h2,
    color: colors.textPrimary,
    padding: spacing.md,
    paddingBottom: spacing.sm,
  },
  filterRow: {
    flexDirection: 'row',
    paddingHorizontal: spacing.md,
    gap: spacing.sm,
    marginBottom: spacing.sm,
  },
  filterBtn: {
    borderRadius: 20,
    paddingHorizontal: spacing.md,
    paddingVertical: 6,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.cardBorder,
  },
  filterBtnActive: { borderColor: colors.primary, backgroundColor: colors.primary + '22' },
  filterText: { ...typography.caption, color: colors.textSecondary, fontWeight: '600' },
  filterTextActive: { color: colors.primary },
  sectionHeader: {
    backgroundColor: colors.background,
    paddingHorizontal: spacing.md,
    paddingTop: spacing.sm,
    paddingBottom: 4,
  },
  sectionTitle: { ...typography.label, color: colors.textMuted },
  listContent: { paddingBottom: spacing.xxl },
  txRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.cardBorder,
  },
  txLeft: { flex: 1, marginRight: spacing.sm },
  txLabel: { ...typography.body, color: colors.textPrimary, fontWeight: '600' },
  txSub: { ...typography.caption, color: colors.textSecondary },
  txDate: { ...typography.caption, color: colors.textMuted, marginTop: 2 },
  pending: { color: colors.warning },
  txAmount: { ...typography.body, fontWeight: '700' },
  credit: { color: colors.success },
  debit: { color: colors.textPrimary },
  footer: { paddingVertical: spacing.md },
  importBtn: {
    marginHorizontal: spacing.md,
    marginBottom: spacing.sm,
    paddingVertical: spacing.sm,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: colors.primary,
    alignItems: 'center',
    backgroundColor: colors.primary + '11',
  },
  importBtnDisabled: { opacity: 0.45 },
  importBtnText: { ...typography.label, color: colors.primary, fontWeight: '600' },
  importHint: {
    ...typography.caption,
    color: colors.textMuted,
    textAlign: 'center',
    marginHorizontal: spacing.md,
    marginBottom: spacing.sm,
  },
})
