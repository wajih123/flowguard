import React from 'react'
import { TouchableOpacity, View, type ViewStyle, type StyleProp } from 'react-native'
import { colors, spacing } from '../theme'

type CardVariant = 'default' | 'alert' | 'success' | 'info' | 'warning'

interface FlowGuardCardProps {
  children: React.ReactNode
  style?: StyleProp<ViewStyle>
  onPress?: () => void
  variant?: CardVariant
  elevated?: boolean
}

const VARIANT_STYLES: Record<CardVariant, { bg: string; border: string }> = {
  default: { bg: colors.card, border: colors.cardBorder },
  alert: { bg: '#FEF2F2', border: '#EF4444' },
  success: { bg: '#F0FFF4', border: '#10B981' },
  info: { bg: '#E0F7FF', border: '#06B6D4' },
  warning: { bg: '#FFFBEB', border: '#F59E0B' },
}

export const FlowGuardCard: React.FC<FlowGuardCardProps> = ({
  children,
  style,
  onPress,
  variant = 'default',
  elevated = false,
}) => {
  const { bg, border } = VARIANT_STYLES[variant]

  const cardStyle: ViewStyle = {
    backgroundColor: bg,
    borderColor: border,
    borderRadius: 16,
    borderWidth: 1,
    padding: spacing.md,
    ...(elevated
      ? {
          shadowColor: '#000',
          shadowOffset: { width: 0, height: 4 },
          shadowOpacity: 0.15,
          shadowRadius: 8,
          elevation: 4,
        }
      : {}),
  }

  if (onPress) {
    return (
      <TouchableOpacity style={[cardStyle, style]} onPress={onPress} activeOpacity={0.85}>
        {children}
      </TouchableOpacity>
    )
  }

  return <View style={[cardStyle, style]}>{children}</View>
}

export default FlowGuardCard
