import React from 'react';
import { Text, StyleSheet } from 'react-native';
import { FlowGuardCard } from '../../../components/FlowGuardCard';
import { colors, typography, spacing } from '../../../theme';

interface BalanceCardProps {
  balance: number
  healthScore: number
  confidence: number
}

export const BalanceCard: React.FC<BalanceCardProps> = ({
  balance,
  healthScore,
  confidence,
}) => {
  const isPositive = balance >= 0;
  const formattedBalance = new Intl.NumberFormat('fr-FR', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 2,
  }).format(balance);

  return (
    <FlowGuardCard style={styles.card}>
      <Text style={styles.label}>SOLDE ACTUEL</Text>
      <Text style={[styles.balance, isPositive ? styles.positive : styles.negative]}>
        {formattedBalance}
      </Text>
      <Text style={styles.info}>
        Score santé: {healthScore}/100 · IA: {Math.round(confidence * 100)}%
      </Text>
    </FlowGuardCard>
  );
};

const styles = StyleSheet.create({
  card: {
    marginBottom: spacing.md,
  },
  label: {
    color: colors.textMuted,
    fontSize: typography.label.fontSize,
    fontWeight: typography.label.fontWeight,
    letterSpacing: typography.label.letterSpacing,
    marginBottom: spacing.xs,
  },
  balance: {
    fontSize: typography.hero.fontSize,
    fontWeight: typography.hero.fontWeight,
    letterSpacing: typography.hero.letterSpacing,
    marginBottom: spacing.sm,
  },
  positive: {
    color: colors.success,
  },
  negative: {
    color: colors.danger,
  },
  info: {
    color: colors.textSecondary,
    fontSize: typography.caption.fontSize,
  },
});
