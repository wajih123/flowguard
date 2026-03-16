import React from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { StackScreenProps } from '@react-navigation/stack';
import { FlowGuardCard } from '../../components/FlowGuardCard';
import { Routes } from '../../navigation/routes';
import { colors, typography, spacing } from '../../theme';
import type { Transaction } from '../../domain/Transaction';
import { format, parseISO } from 'date-fns';
import { fr } from 'date-fns/locale';

type Props = StackScreenProps<
  Record<string, { transaction: Transaction }>,
  typeof Routes.TransactionDetail
>

const categoryLabels: Record<string, string> = {
  LOYER: 'Loyer',
  SALAIRE: 'Salaire',
  ALIMENTATION: 'Alimentation',
  TRANSPORT: 'Transport',
  ABONNEMENT: 'Abonnement',
  ENERGIE: 'Énergie',
  TELECOM: 'Télécom',
  ASSURANCE: 'Assurance',
  CHARGES_FISCALES: 'Charges fiscales',
  FOURNISSEUR: 'Fournisseur',
  CLIENT_PAYMENT: 'Paiement client',
  VIREMENT: 'Virement',
  AUTRE: 'Autre',
};

const fmtEur = (val: number) =>
  new Intl.NumberFormat('fr-FR', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 2,
  }).format(val);

export const TransactionDetailScreen: React.FC<Props> = ({ route, navigation }) => {
  const tx = route.params?.transaction;

  const formattedDate = (() => {
    try {
      return format(parseISO(tx.date), 'EEEE dd MMMM yyyy', { locale: fr });
    } catch {
      return tx.date;
    }
  })();

  const formattedBooking = tx.bookingDate
    ? (() => {
        try {
          return format(parseISO(tx.bookingDate!), 'dd MMMM yyyy', { locale: fr });
        } catch {
          return tx.bookingDate;
        }
      })()
    : null;

  const isCredit = tx.type === 'CREDIT';
  const amountColor = isCredit ? colors.success : colors.textPrimary;

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
        <Text style={styles.backText}>← Retour</Text>
      </TouchableOpacity>

      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        <View style={styles.amountBlock}>
          <Text style={[styles.amount, { color: amountColor }]}>
            {isCredit ? '+' : '-'}
            {fmtEur(Math.abs(tx.amount))}
          </Text>
          <Text style={styles.amountLabel}>{tx.label}</Text>
          {tx.status === 'PENDING' && (
            <View style={styles.pendingBadge}>
              <Text style={styles.pendingText}>En attente</Text>
            </View>
          )}
        </View>

        <FlowGuardCard style={styles.detailCard}>
          <Row label="Date" value={formattedDate} />
          {formattedBooking && <Row label="Date de valeur" value={formattedBooking} />}
          {tx.creditorName && tx.creditorName !== tx.label && (
            <Row label="Contrepartie" value={tx.creditorName} />
          )}
          <Row label="Catégorie" value={categoryLabels[tx.category] ?? tx.category} />
          <Row label="Type" value={isCredit ? 'Crédit' : 'Débit'} />
          <Row label="Récurrent" value={tx.isRecurring ? 'Oui' : 'Non'} />
        </FlowGuardCard>
      </ScrollView>
    </SafeAreaView>
  );
};

const Row: React.FC<{ label: string; value: string }> = ({ label, value }) => (
  <View style={rowStyles.row}>
    <Text style={rowStyles.label}>{label}</Text>
    <Text style={rowStyles.value}>{value}</Text>
  </View>
);

const rowStyles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    paddingVertical: spacing.xs,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.cardBorder,
  },
  label: { color: colors.textSecondary, ...typography.body, flex: 1 },
  value: {
    color: colors.textPrimary,
    ...typography.body,
    fontWeight: '600',
    flex: 1,
    textAlign: 'right',
  },
});

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  backBtn: { paddingHorizontal: spacing.md, paddingTop: spacing.md, paddingBottom: spacing.sm },
  backText: { color: colors.primary, ...typography.body, fontWeight: '600' },
  scroll: { padding: spacing.md, paddingBottom: spacing.xxl },
  amountBlock: { alignItems: 'center', marginBottom: spacing.xl },
  amount: { ...typography.hero, textAlign: 'center' },
  amountLabel: {
    ...typography.h3,
    color: colors.textSecondary,
    textAlign: 'center',
    marginTop: spacing.xs,
  },
  pendingBadge: {
    backgroundColor: colors.warning + '22',
    borderRadius: 8,
    paddingHorizontal: spacing.md,
    paddingVertical: 3,
    marginTop: spacing.sm,
  },
  pendingText: { color: colors.warning, ...typography.caption, fontWeight: '700' },
  detailCard: {},
});
