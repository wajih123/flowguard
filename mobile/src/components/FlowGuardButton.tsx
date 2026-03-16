import React from 'react';
import {
  TouchableOpacity,
  Text,
  ActivityIndicator,
  StyleSheet,
  type ViewStyle,
  type StyleProp,
} from 'react-native';
import { colors, spacing, typography } from '../theme';

interface FlowGuardButtonProps {
  /** Display text */
  label?: string
  /** Alias for label */
  title?: string
  onPress: () => void
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost' | 'outline'
  loading?: boolean
  disabled?: boolean
  icon?: string
  style?: StyleProp<ViewStyle>
  fullWidth?: boolean
}

export const FlowGuardButton: React.FC<FlowGuardButtonProps> = ({
  label,
  title,
  onPress,
  variant = 'primary',
  loading = false,
  disabled = false,
  icon,
  style,
  fullWidth = true,
}) => {
  const isDisabled = disabled || loading;
  const displayText = label ?? title ?? '';

  const buttonStyles = [
    styles.base,
    fullWidth && styles.fullWidth,
    variant === 'primary' && styles.primary,
    variant === 'secondary' && styles.secondary,
    variant === 'danger' && styles.danger,
    variant === 'ghost' && styles.ghost,
    variant === 'outline' && styles.outline,
    isDisabled && styles.disabled,
    style,
  ];

  const textStyles = [
    styles.text,
    variant === 'secondary' && styles.secondaryText,
    variant === 'ghost' && styles.ghostText,
    variant === 'outline' && styles.outlineText,
    variant === 'danger' && styles.dangerText,
  ];

  return (
    <TouchableOpacity
      style={buttonStyles}
      onPress={onPress}
      disabled={isDisabled}
      activeOpacity={0.7}
    >
      {loading ? (
        <ActivityIndicator
          color={
            variant === 'secondary' || variant === 'ghost' || variant === 'outline'
              ? colors.primary
              : colors.textPrimary
          }
          size="small"
        />
      ) : (
        <Text style={textStyles}>{icon ? `${icon}  ${displayText}` : displayText}</Text>
      )}
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  base: {
    paddingVertical: spacing.sm + 4,
    paddingHorizontal: spacing.lg,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
    minHeight: 48,
  },
  fullWidth: {
    alignSelf: 'stretch',
  },
  primary: {
    backgroundColor: colors.primary,
  },
  secondary: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.cardBorder,
  },
  danger: {
    backgroundColor: colors.danger,
  },
  ghost: {
    backgroundColor: 'transparent',
  },
  outline: {
    backgroundColor: 'transparent',
    borderWidth: 1.5,
    borderColor: colors.primary,
  },
  disabled: {
    opacity: 0.5,
  },
  text: {
    color: colors.textPrimary,
    fontSize: typography.body.fontSize,
    fontWeight: '700',
    letterSpacing: 0.2,
  },
  secondaryText: {
    color: colors.textPrimary,
  },
  ghostText: {
    color: colors.primary,
  },
  outlineText: {
    color: colors.primary,
  },
  dangerText: {
    color: colors.textPrimary,
  },
});
