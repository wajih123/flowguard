import React, { useState, useCallback } from 'react'
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Switch, Alert } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { useMutation } from '@tanstack/react-query'
import type { StackScreenProps } from '@react-navigation/stack'
import { FlowGuardCard } from '../../components/FlowGuardCard'
import { FlowGuardButton } from '../../components/FlowGuardButton'
import { useAuthStore } from '../../store/authStore'
import { Routes } from '../../navigation/routes'
import { colors, typography, spacing } from '../../theme'
import * as flowguardApi from '../../api/flowguardApi'
import ReactNativeBiometrics from 'react-native-biometrics'

type Props = StackScreenProps<Record<string, undefined>, typeof Routes.Profile>

const kycLabels: Record<string, string> = {
  PENDING: 'En attente',
  VERIFIED: 'Vérifié',
  REJECTED: 'Refusé',
}
const kycColors: Record<string, string> = {
  PENDING: colors.warning,
  VERIFIED: colors.success,
  REJECTED: colors.danger,
}
const planLabels: Record<string, string> = {
  FREE: 'Gratuit',
  PRO: 'Pro — 49 €/mois',
  SCALE: 'Scale — 99 €/mois',
}

const rnBiometrics = new ReactNativeBiometrics()

export const ProfileScreen: React.FC<Props> = ({ navigation }) => {
  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)
  const [biometricEnabled, setBiometricEnabled] = useState(false)
  const [biometricLoading, setBiometricLoading] = useState(false)

  const { mutate: exportData, isPending: exporting } = useMutation({
    mutationFn: () => flowguardApi.exportUserData(),
    onSuccess: () => Alert.alert('Export', 'Votre export de données a été envoyé par email.'),
    onError: () => Alert.alert('Erreur', "Impossible d'exporter vos données."),
  })

  const { mutate: deleteAccount, isPending: deleting } = useMutation({
    mutationFn: () => flowguardApi.deleteAccount(),
    onSuccess: () => logout(),
    onError: () => Alert.alert('Erreur', 'Impossible de supprimer votre compte.'),
  })

  const handleToggleBiometric = useCallback(async () => {
    setBiometricLoading(true)
    try {
      const { available } = await rnBiometrics.isSensorAvailable()
      if (!available) {
        Alert.alert('Non disponible', "La biométrie n'est pas disponible sur cet appareil.")
        return
      }
      setBiometricEnabled((prev) => !prev)
    } finally {
      setBiometricLoading(false)
    }
  }, [])

  const handleDeleteAccount = useCallback(() => {
    Alert.alert(
      'Supprimer le compte',
      'Cette action est irréversible. Toutes vos données seront supprimées.',
      [
        { text: 'Annuler', style: 'cancel' },
        { text: 'Supprimer', style: 'destructive', onPress: () => deleteAccount() },
      ],
    )
  }, [deleteAccount])

  const handleLogout = useCallback(() => {
    Alert.alert('Déconnexion', 'Vous allez être déconnecté.', [
      { text: 'Annuler', style: 'cancel' },
      { text: 'Déconnexion', style: 'destructive', onPress: () => logout() },
    ])
  }, [logout])

  if (!user) return null

  const kycColor = kycColors[user.kycStatus] ?? colors.textMuted
  const kycLabel = kycLabels[user.kycStatus] ?? user.kycStatus

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        <Text style={styles.screenTitle}>Mon profil</Text>

        {/* Avatar + identity */}
        <FlowGuardCard style={styles.avatarCard}>
          <View style={styles.avatarCircle}>
            <Text style={styles.avatarInitials}>
              {(user.firstName[0] ?? '') + (user.lastName[0] ?? '')}
            </Text>
          </View>
          <Text style={styles.fullName}>
            {user.firstName} {user.lastName}
          </Text>
          <Text style={styles.email}>{user.email}</Text>

          <View style={styles.badgeRow}>
            <View style={[styles.badge, { backgroundColor: colors.primary + '22' }]}>
              <Text style={[styles.badgeText, { color: colors.primary }]}>
                {planLabels[user.plan] ?? user.plan}
              </Text>
            </View>
            <View style={[styles.badge, { backgroundColor: kycColor + '22' }]}>
              <Text style={[styles.badgeText, { color: kycColor }]}>KYC : {kycLabel}</Text>
            </View>
          </View>
        </FlowGuardCard>

        {/* KYC incomplete banner */}
        {user.kycStatus !== 'VERIFIED' && (
          <FlowGuardCard
            variant={user.kycStatus === 'REJECTED' ? 'alert' : 'warning'}
            style={styles.kycCard}
          >
            <Text style={styles.kycCardText}>
              {user.kycStatus === 'PENDING'
                ? "Votre vérification d'identité est en cours. Complétez votre KYC pour accéder à toutes les fonctionnalités."
                : 'Votre KYC a été refusé. Veuillez soumettre à nouveau vos documents.'}
            </Text>
            <TouchableOpacity
              onPress={() => navigation.navigate(Routes.Kyc as never)}
              style={styles.kycBtn}
            >
              <Text style={styles.kycBtnText}>Compléter le KYC →</Text>
            </TouchableOpacity>
          </FlowGuardCard>
        )}

        {/* Subscription */}
        {user.role === 'ROLE_BUSINESS' && (
          <>
            <Text style={styles.sectionTitle}>Abonnement</Text>
            <FlowGuardCard style={styles.sectionCard}>
              <View style={styles.row}>
                <Text style={styles.rowLabel}>Plan actuel</Text>
                <Text style={styles.rowValue}>{planLabels[user.plan] ?? user.plan}</Text>
              </View>
              <FlowGuardButton
                label="Gérer l'abonnement"
                onPress={() => navigation.navigate(Routes.Subscription as never)}
                variant="outline"
                style={styles.inlineBtn}
              />
            </FlowGuardCard>
          </>
        )}

        {/* Security */}
        <Text style={styles.sectionTitle}>Sécurité</Text>
        <FlowGuardCard style={styles.sectionCard}>
          <View style={styles.row}>
            <Text style={styles.rowLabel}>Connexion biométrique</Text>
            <Switch
              value={biometricEnabled}
              onValueChange={handleToggleBiometric}
              disabled={biometricLoading}
              trackColor={{ false: colors.cardBorder, true: colors.primary }}
              thumbColor={biometricEnabled ? colors.background : colors.textSecondary}
            />
          </View>
        </FlowGuardCard>

        {/* RGPD */}
        <Text style={styles.sectionTitle}>Données personnelles</Text>
        <FlowGuardCard style={styles.sectionCard}>
          <FlowGuardButton
            label="Exporter mes données"
            onPress={() => exportData()}
            variant="outline"
            loading={exporting}
            style={styles.inlineBtn}
          />
          <FlowGuardButton
            label="Supprimer mon compte"
            onPress={handleDeleteAccount}
            variant="outline"
            loading={deleting}
            style={[styles.inlineBtn, styles.dangerBtn]}
          />
        </FlowGuardCard>

        {/* Logout */}
        <FlowGuardButton
          label="Se déconnecter"
          onPress={handleLogout}
          variant="outline"
          style={styles.logoutBtn}
        />
      </ScrollView>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  scroll: { padding: spacing.md, paddingBottom: spacing.xxl },
  screenTitle: { ...typography.h1, color: colors.textPrimary, marginBottom: spacing.md },
  avatarCard: { alignItems: 'center', paddingVertical: spacing.xl, marginBottom: spacing.md },
  avatarCircle: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: colors.primary + '33',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.sm,
  },
  avatarInitials: { ...typography.h2, color: colors.primary },
  fullName: { ...typography.h2, color: colors.textPrimary, textAlign: 'center' },
  email: {
    color: colors.textSecondary,
    ...typography.body,
    textAlign: 'center',
    marginBottom: spacing.sm,
  },
  badgeRow: { flexDirection: 'row', gap: spacing.sm },
  badge: { borderRadius: 8, paddingHorizontal: spacing.sm, paddingVertical: 3 },
  badgeText: { ...typography.caption, fontWeight: '700' },
  kycCard: { marginBottom: spacing.md },
  kycCardText: { color: colors.textPrimary, ...typography.body, marginBottom: spacing.sm },
  kycBtn: {},
  kycBtnText: { color: colors.primary, ...typography.body, fontWeight: '700' },
  sectionTitle: {
    ...typography.h3,
    color: colors.textPrimary,
    marginBottom: spacing.sm,
    marginTop: spacing.md,
  },
  sectionCard: { marginBottom: spacing.xs },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: spacing.xs,
  },
  rowLabel: { color: colors.textSecondary, ...typography.body },
  rowValue: { color: colors.textPrimary, ...typography.body, fontWeight: '700' },
  inlineBtn: { marginTop: spacing.xs },
  dangerBtn: { borderColor: colors.danger },
  logoutBtn: { marginTop: spacing.lg, borderColor: colors.danger },
})
