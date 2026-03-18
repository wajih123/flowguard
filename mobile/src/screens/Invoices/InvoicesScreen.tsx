import React, { useState, useCallback } from 'react'
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  Alert,
  TextInput,
  TouchableOpacity,
  RefreshControl,
} from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { FlowGuardCard } from '../../components/FlowGuardCard'
import { FlowGuardButton } from '../../components/FlowGuardButton'
import { FlowGuardLoader } from '../../components/FlowGuardLoader'
import { colors, typography, spacing } from '../../theme'
import * as flowguardApi from '../../api/flowguardApi'

const fmtEur = (n: number) =>
  new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' }).format(n)

const STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Brouillon',
  SENT: 'Envoyée',
  OVERDUE: 'En retard',
  PAID: 'Payée',
  CANCELLED: 'Annulée',
}

const STATUS_COLORS: Record<string, string> = {
  DRAFT: colors.textMuted,
  SENT: colors.primary,
  OVERDUE: colors.danger,
  PAID: colors.success,
  CANCELLED: colors.textMuted,
}

export const InvoicesScreen: React.FC = () => {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState({
    clientName: '',
    number: `FAC-${Date.now().toString().slice(-6)}`,
    amountHt: '',
    vatRate: '20',
    issueDate: new Date().toISOString().split('T')[0],
    dueDate: new Date(Date.now() + 30 * 86400000).toISOString().split('T')[0],
  })

  const {
    data: invoices,
    isLoading,
    refetch,
    isFetching,
  } = useQuery({
    queryKey: ['mobile-invoices'],
    queryFn: flowguardApi.getInvoices,
  })

  const createMut = useMutation({
    mutationFn: () =>
      flowguardApi.createInvoice({
        ...form,
        amountHt: parseFloat(form.amountHt),
        vatRate: parseFloat(form.vatRate),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['mobile-invoices'] })
      setShowForm(false)
    },
    onError: () => Alert.alert('Erreur', 'Impossible de créer la facture.'),
  })

  const sendMut = useMutation({
    mutationFn: flowguardApi.sendInvoice,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['mobile-invoices'] }),
  })

  const paidMut = useMutation({
    mutationFn: flowguardApi.markInvoicePaid,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['mobile-invoices'] }),
  })

  const outstanding = (invoices ?? [])
    .filter((i: any) => i.status === 'SENT' || i.status === 'OVERDUE')
    .reduce((s: number, i: any) => s + i.totalTtc, 0)

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView
        contentContainerStyle={styles.content}
        refreshControl={
          <RefreshControl refreshing={isFetching} onRefresh={refetch} tintColor={colors.primary} />
        }
      >
        {/* Header */}
        <View style={styles.headerRow}>
          <Text style={styles.title}>Factures</Text>
          <TouchableOpacity onPress={() => setShowForm(!showForm)} style={styles.addBtn}>
            <Text style={styles.addBtnText}>{showForm ? '✕' : '+ Nouvelle'}</Text>
          </TouchableOpacity>
        </View>

        {/* KPI */}
        <FlowGuardCard style={styles.kpiCard}>
          <Text style={styles.kpiLabel}>Encours TTC</Text>
          <Text style={styles.kpiValue}>{fmtEur(outstanding)}</Text>
        </FlowGuardCard>

        {/* Create form */}
        {showForm && (
          <FlowGuardCard>
            <Text style={styles.sectionTitle}>Nouvelle facture</Text>
            <TextInput
              style={styles.input}
              placeholder="Nom du client"
              placeholderTextColor={colors.textMuted}
              value={form.clientName}
              onChangeText={(v) => setForm({ ...form, clientName: v })}
            />
            <TextInput
              style={styles.input}
              placeholder="Numéro (ex: FAC-001)"
              placeholderTextColor={colors.textMuted}
              value={form.number}
              onChangeText={(v) => setForm({ ...form, number: v })}
            />
            <TextInput
              style={styles.input}
              placeholder="Montant HT (€)"
              placeholderTextColor={colors.textMuted}
              keyboardType="decimal-pad"
              value={form.amountHt}
              onChangeText={(v) => setForm({ ...form, amountHt: v })}
            />
            <TextInput
              style={styles.input}
              placeholder="Taux TVA (%)"
              placeholderTextColor={colors.textMuted}
              keyboardType="decimal-pad"
              value={form.vatRate}
              onChangeText={(v) => setForm({ ...form, vatRate: v })}
            />
            <FlowGuardButton
              label={createMut.isPending ? 'Création…' : 'Créer la facture'}
              onPress={() => createMut.mutate()}
              disabled={createMut.isPending || !form.clientName || !form.amountHt}
            />
          </FlowGuardCard>
        )}

        {/* Invoice list */}
        {isLoading ? (
          <FlowGuardLoader message="Chargement…" />
        ) : (
          (invoices ?? []).map((inv: any) => (
            <FlowGuardCard key={inv.id} style={styles.invoiceCard}>
              <View style={styles.invoiceRow}>
                <View style={styles.invoiceInfo}>
                  <Text style={styles.clientName}>{inv.clientName}</Text>
                  <Text style={styles.invoiceNum}>N° {inv.number}</Text>
                  <Text
                    style={[
                      styles.status,
                      { color: STATUS_COLORS[inv.status] ?? colors.textMuted },
                    ]}
                  >
                    {STATUS_LABELS[inv.status] ?? inv.status}
                    {inv.daysOverdue > 0 ? ` (${inv.daysOverdue}j)` : ''}
                  </Text>
                </View>
                <Text style={styles.amount}>{fmtEur(inv.totalTtc)}</Text>
              </View>
              {inv.status === 'DRAFT' && (
                <FlowGuardButton
                  label="Envoyer"
                  variant="secondary"
                  onPress={() => sendMut.mutate(inv.id)}
                  style={styles.actionBtn}
                />
              )}
              {(inv.status === 'SENT' || inv.status === 'OVERDUE') && (
                <FlowGuardButton
                  label="Marquer payée"
                  variant="secondary"
                  onPress={() => paidMut.mutate(inv.id)}
                  style={styles.actionBtn}
                />
              )}
            </FlowGuardCard>
          ))
        )}

        {!isLoading && !(invoices ?? []).length && (
          <Text style={styles.empty}>Aucune facture. Créez la première.</Text>
        )}
      </ScrollView>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  content: { padding: spacing.md, gap: spacing.sm },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: spacing.xs,
  },
  title: { ...typography.h2, color: colors.textPrimary },
  addBtn: {
    backgroundColor: colors.primary,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
    borderRadius: 8,
  },
  addBtnText: { color: '#fff', fontWeight: '600', fontSize: 13 },
  kpiCard: { padding: spacing.md },
  kpiLabel: { ...typography.caption, color: colors.textMuted, marginBottom: 4 },
  kpiValue: { ...typography.h2, color: colors.primary },
  sectionTitle: { ...typography.h3, color: colors.textPrimary, marginBottom: spacing.sm },
  input: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: 8,
    padding: spacing.sm,
    color: colors.textPrimary,
    marginBottom: spacing.xs,
    fontSize: 14,
  },
  invoiceCard: { padding: spacing.md },
  invoiceRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  invoiceInfo: { flex: 1, gap: 2 },
  clientName: { ...typography.body, color: colors.textPrimary, fontWeight: '600' },
  invoiceNum: { ...typography.caption, color: colors.textMuted },
  status: { ...typography.caption, fontWeight: '600' },
  amount: { ...typography.h3, color: colors.textPrimary },
  actionBtn: { marginTop: spacing.sm },
  empty: { textAlign: 'center', color: colors.textMuted, marginTop: spacing.xl },
})

export default InvoicesScreen
