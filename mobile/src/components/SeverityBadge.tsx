import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import type { AlertSeverity } from '../domain/Alert';
import { colors } from '../theme';

const SEVERITY_CONFIG: Record<AlertSeverity, { color: string; label: string }> = {
  CRITICAL: { color: colors.danger, label: 'CRITIQUE' },
  HIGH: { color: colors.severityHigh, label: 'IMPORTANT' },
  MEDIUM: { color: colors.warning, label: 'MODÉRÉ' },
  LOW: { color: colors.severityLow, label: 'INFO' },
};

interface SeverityBadgeProps {
  severity: AlertSeverity
  size?: 'sm' | 'md'
}

export const SeverityBadge: React.FC<SeverityBadgeProps> = ({ severity, size = 'md' }) => {
  const { color, label } = SEVERITY_CONFIG[severity];

  return (
    <View
      style={[
        styles.badge,
        { backgroundColor: `${color}20`, borderColor: color },
        size === 'sm' && styles.badgeSm,
      ]}
    >
      <Text style={[styles.text, { color }, size === 'sm' && styles.textSm]}>{label}</Text>
    </View>
  );
};

const styles = StyleSheet.create({
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
    borderWidth: 1,
    alignSelf: 'flex-start',
  },
  badgeSm: {
    paddingHorizontal: 6,
    paddingVertical: 2,
  },
  text: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 0.8,
  },
  textSm: {
    fontSize: 9,
  },
});
