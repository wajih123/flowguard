import React from 'react'
import { View, TextInput, Text, StyleSheet, type KeyboardTypeOptions } from 'react-native'
import { colors, spacing, typography } from '../theme'

interface FlowGuardInputProps {
  label: string
  value: string
  onChangeText: (text: string) => void
  error?: string
  secureTextEntry?: boolean
  keyboardType?: KeyboardTypeOptions
  placeholder?: string
  autoCapitalize?: 'none' | 'sentences' | 'words' | 'characters'
  autoCorrect?: boolean
}

export const FlowGuardInput: React.FC<FlowGuardInputProps> = ({
  label,
  value,
  onChangeText,
  error,
  secureTextEntry,
  keyboardType,
  placeholder,
  autoCapitalize,
  autoCorrect,
}) => {
  const [focused, setFocused] = React.useState(false)

  return (
    <View style={styles.container}>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        style={[
          styles.input,
          focused && styles.inputFocused,
          error ? styles.inputError : undefined,
        ]}
        value={value}
        onChangeText={onChangeText}
        secureTextEntry={secureTextEntry}
        keyboardType={keyboardType}
        placeholder={placeholder}
        placeholderTextColor={colors.textMuted}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        autoCapitalize={autoCapitalize ?? 'none'}
        autoCorrect={autoCorrect ?? false}
      />
      {error && <Text style={styles.error}>{error}</Text>}
    </View>
  )
}

const styles = StyleSheet.create({
  container: {
    marginBottom: spacing.md,
  },
  label: {
    color: colors.textMuted,
    fontSize: typography.caption.fontSize,
    fontWeight: '600',
    marginBottom: spacing.xs,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  input: {
    backgroundColor: colors.card,
    borderWidth: 1,
    borderColor: colors.cardBorder,
    borderRadius: 12,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm + 4,
    color: colors.textPrimary,
    fontSize: typography.body.fontSize,
  },
  inputFocused: {
    borderColor: colors.primary,
  },
  inputError: {
    borderColor: colors.danger,
  },
  error: {
    color: colors.danger,
    fontSize: typography.caption.fontSize,
    marginTop: spacing.xs,
  },
})
