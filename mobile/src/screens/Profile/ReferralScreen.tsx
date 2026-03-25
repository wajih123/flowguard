import React, { useCallback, useState } from 'react'
import { View, Text, StyleSheet, ScrollView, Share, TouchableOpacity } from 'react-native'
import * as Clipboard from 'expo-clipboard'
import { SafeAreaView } from 'react-native-safe-area-context'
import { FlowGuardCard } from '../../components/FlowGuardCard'
import { FlowGuardButton } from '../../components/FlowGuardButton'
import { useAuthStore } from '../../store/authStore'
import { colors, typography, spacing } from '../../theme'

const REFERRAL_REWARD = '1 mois offert'
const REFEREE_REWARD = '14 jours offerts'

export const ReferralScreen: React.FC = () => {
  const user = useAuthStore((s) => s.user)
  const [copied, setCopied] = useState(false)

  // Deterministic code derived from userId — server validates it on registration
  const referralCode = user ? `FG-${user.id.slice(0, 6).toUpperCase()}` : 'FG-XXXXXX'
  const referralLink = `https://flowguard.fr/inscription?ref=${referralCode}`

  const handleCopy = useCallback(async () => {
    await Clipboard.setStringAsync(referralCode)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }, [referralCode])

  const handleShare = useCallback(async () => {
    try {
      await Share.share({
        message:
          `Essaie FlowGuard, l'app qui prédit ta trésorerie grâce à l'IA 🚀\n\n` +
          `Utilise mon code parrain ${referralCode} et obtiens ${REFEREE_REWARD} gratuits !\n\n` +
          referralLink,
      })
    } catch {
      // User cancelled share sheet — silent ignore
    }
  }, [referralCode, referralLink])

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView showsVerticalScrollIndicator={false}>
        <Text style={styles.title}>Parrainage</Text>
        <Text style={styles.subtitle}>Invitez vos amis et profitez ensemble</Text>

        {/* Hero card */}
        <FlowGuardCard style={styles.heroCard}>
          <Text style={styles.heroIcon}>🎁</Text>
          <Text style={styles.heroTitle}>Parrainez & profitez</Text>
          <View style={styles.rewardRow}>
            <View style={styles.rewardBox}>
              <Text style={styles.rewardValue}>{REFERRAL_REWARD}</Text>
              <Text style={styles.rewardLabel}>Pour vous</Text>
            </View>
            <View style={styles.rewardDivider} />
            <View style={styles.rewardBox}>
              <Text style={styles.rewardValue}>{REFEREE_REWARD}</Text>
              <Text style={styles.rewardLabel}>Pour votre filleul</Text>
            </View>
          </View>
        </FlowGuardCard>

        {/* Referral code */}
        <Text style={styles.sectionTitle}>Votre code parrain</Text>
        <TouchableOpacity
          onPress={handleCopy}
          style={styles.codeBox}
          activeOpacity={0.7}
          accessibilityLabel="Copier le code parrain"
        >
          <Text style={styles.code}>{referralCode}</Text>
          <Text style={styles.copyHint}>{copied ? '✅ Copié !' : '📋 Appuyer pour copier'}</Text>
        </TouchableOpacity>

        <View style={styles.btnWrapper}>
          <FlowGuardButton title="📣 Partager mon code" onPress={handleShare} variant="primary" />
        </View>

        {/* Steps */}
        <Text style={styles.sectionTitle}>Comment ça marche ?</Text>
        <FlowGuardCard>
          {[
            { step: '1', text: 'Partagez votre code unique à un ami ou collègue' },
            { step: '2', text: "Votre ami s'inscrit avec votre code parrain" },
            { step: '3', text: 'Votre ami connecte sa banque et active son abonnement' },
            { step: '4', text: 'Vous recevez tous les deux votre récompense automatiquement' },
          ].map((item) => (
            <View key={item.step} style={styles.stepRow}>
              <View style={styles.stepBadge}>
                <Text style={styles.stepNum}>{item.step}</Text>
              </View>
              <Text style={styles.stepText}>{item.text}</Text>
            </View>
          ))}
        </FlowGuardCard>

        {/* Stats */}
        <Text style={styles.sectionTitle}>Mes parrainages</Text>
        <FlowGuardCard>
          <View style={styles.statsRow}>
            {[
              { value: '0', label: 'Invités' },
              { value: '0', label: 'Convertis' },
              { value: '0 mois', label: 'Gagnés' },
            ].map((s) => (
              <View key={s.label} style={styles.statBox}>
                <Text style={styles.statValue}>{s.value}</Text>
                <Text style={styles.statLabel}>{s.label}</Text>
              </View>
            ))}
          </View>
        </FlowGuardCard>

        <View style={styles.bottomSpacer} />
      </ScrollView>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  title: {
    ...typography.h1,
    color: colors.textPrimary,
    paddingHorizontal: spacing.md,
    paddingTop: spacing.md,
  },
  subtitle: {
    color: colors.textSecondary,
    fontSize: typography.body.fontSize,
    paddingHorizontal: spacing.md,
    marginBottom: spacing.lg,
  },
  heroCard: {
    marginHorizontal: spacing.md,
    alignItems: 'center' as const,
    paddingVertical: spacing.xl,
  },
  heroIcon: { fontSize: 48, marginBottom: spacing.md },
  heroTitle: { ...typography.h2, color: colors.textPrimary, marginBottom: spacing.lg },
  rewardRow: { flexDirection: 'row' as const, alignItems: 'center' as const, gap: spacing.md },
  rewardBox: { flex: 1, alignItems: 'center' as const },
  rewardValue: { ...typography.h2, color: colors.primary, fontWeight: '800' as const },
  rewardLabel: { color: colors.textSecondary, fontSize: 13, marginTop: 4 },
  rewardDivider: { width: 1, height: 40, backgroundColor: colors.border },
  sectionTitle: {
    ...typography.h3,
    color: colors.textPrimary,
    paddingHorizontal: spacing.md,
    marginTop: spacing.xl,
    marginBottom: spacing.sm,
  },
  codeBox: {
    marginHorizontal: spacing.md,
    backgroundColor: colors.surface,
    borderRadius: 16,
    padding: spacing.lg,
    alignItems: 'center' as const,
    borderWidth: 2,
    borderColor: colors.primary + '40',
    borderStyle: 'dashed' as const,
  },
  code: {
    ...typography.h1,
    color: colors.primary,
    letterSpacing: 4,
    fontWeight: '800' as const,
  },
  copyHint: { color: colors.textSecondary, fontSize: 13, marginTop: spacing.xs },
  btnWrapper: { paddingHorizontal: spacing.md, marginTop: spacing.md },
  stepRow: {
    flexDirection: 'row' as const,
    alignItems: 'flex-start' as const,
    marginBottom: spacing.md,
    gap: spacing.sm,
  },
  stepBadge: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: colors.primary,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
    flexShrink: 0,
  },
  stepNum: { color: '#fff', fontWeight: '700', fontSize: 13 },
  stepText: {
    flex: 1,
    color: colors.textSecondary,
    fontSize: typography.body.fontSize,
    lineHeight: 22,
    paddingTop: 3,
  },
  statsRow: { flexDirection: 'row' as const, justifyContent: 'space-around' as const },
  statBox: { alignItems: 'center' as const },
  statValue: { ...typography.h2, color: colors.textPrimary, fontWeight: '800' as const },
  statLabel: { color: colors.textSecondary, fontSize: 13, marginTop: 4 },
  bottomSpacer: { height: spacing.xxl },
})
