import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { colors, typography, spacing } from '../../../theme';

interface QuickActionsProps {
  onFlashCredit: () => void
  onSpending: () => void
  onScenario: () => void
  onBankConnect: () => void
}

interface ActionItem {
  icon: string
  label: string
  onPress: () => void
}

export const QuickActions: React.FC<QuickActionsProps> = ({
  onFlashCredit,
  onSpending,
  onScenario,
  onBankConnect,
}) => {
  const actions: ActionItem[] = [
    { icon: '⚡', label: 'Avance flash', onPress: onFlashCredit },
    { icon: '📊', label: 'Mes dépenses', onPress: onSpending },
    { icon: '🔮', label: 'Simuler', onPress: onScenario },
    { icon: '🏦', label: 'Ma banque', onPress: onBankConnect },
  ];

  return (
    <View style={styles.container}>
      <Text style={styles.label}>ACTIONS RAPIDES</Text>
      <View style={styles.grid}>
        {actions.map((action) => (
          <TouchableOpacity
            key={action.label}
            style={styles.action}
            onPress={action.onPress}
            activeOpacity={0.7}
          >
            <Text style={styles.icon}>{action.icon}</Text>
            <Text style={styles.actionLabel}>{action.label}</Text>
          </TouchableOpacity>
        ))}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    marginBottom: spacing.md,
  },
  label: {
    color: colors.textMuted,
    fontSize: typography.label.fontSize,
    fontWeight: typography.label.fontWeight,
    letterSpacing: typography.label.letterSpacing,
    marginBottom: spacing.sm,
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
  },
  action: {
    width: '48%',
    backgroundColor: colors.card,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: 16,
    padding: spacing.md,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 80,
  },
  icon: {
    fontSize: 24,
    marginBottom: spacing.xs,
  },
  actionLabel: {
    color: colors.textPrimary,
    fontSize: typography.caption.fontSize,
    fontWeight: '600',
    textAlign: 'center',
  },
});
