import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { colors, typography, spacing } from '../../../theme';

interface HorizonSelectorProps {
  selected: number
  onSelect: (horizon: number) => void
}

const HORIZONS = [30, 60, 90] as const;

export const HorizonSelector: React.FC<HorizonSelectorProps> = ({ selected, onSelect }) => {
  return (
    <View style={styles.container}>
      {HORIZONS.map((h) => (
        <TouchableOpacity
          key={h}
          style={[styles.segment, selected === h && styles.segmentActive]}
          onPress={() => onSelect(h)}
          activeOpacity={0.7}
        >
          <Text style={[styles.label, selected === h && styles.labelActive]}>
            {h}j
          </Text>
        </TouchableOpacity>
      ))}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    backgroundColor: colors.card,
    borderRadius: 12,
    padding: 4,
    marginBottom: spacing.md,
  },
  segment: {
    flex: 1,
    paddingVertical: spacing.sm,
    borderRadius: 10,
    alignItems: 'center',
  },
  segmentActive: {
    backgroundColor: colors.primary,
  },
  label: {
    color: colors.textMuted,
    fontSize: typography.body.fontSize,
    fontWeight: '600',
  },
  labelActive: {
    color: colors.textPrimary,
  },
});
