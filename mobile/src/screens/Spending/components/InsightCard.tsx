import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { FlowGuardCard } from '../../../components/FlowGuardCard';
import { colors, typography, spacing } from '../../../theme';

interface InsightCardProps {
  icon: string
  text: string
  type: 'positive' | 'negative' | 'neutral'
}

export const InsightCard: React.FC<InsightCardProps> = ({ icon, text, type }) => {
  const borderColor =
    type === 'positive'
      ? colors.success
      : type === 'negative'
        ? colors.danger
        : colors.primary;

  return (
    <FlowGuardCard style={[styles.card, { borderLeftColor: borderColor, borderLeftWidth: 3 }]}>
      <View style={styles.row}>
        <Text style={styles.icon}>{icon}</Text>
        <Text style={styles.text}>{text}</Text>
      </View>
    </FlowGuardCard>
  );
};

const styles = StyleSheet.create({
  card: {
    marginBottom: spacing.sm,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: spacing.sm,
  },
  icon: {
    fontSize: 20,
  },
  text: {
    flex: 1,
    color: colors.textPrimary,
    fontSize: typography.body.fontSize,
    lineHeight: 22,
  },
});
