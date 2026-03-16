import React, { useCallback } from 'react';
import { View, Text, ScrollView, StyleSheet, Alert } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { StackScreenProps } from '@react-navigation/stack';
import { FlowGuardCard } from '../../components/FlowGuardCard';
import { FlowGuardButton } from '../../components/FlowGuardButton';
import { useAuthStore } from '../../store/authStore';
import { Routes } from '../../navigation/routes';
import { colors, typography, spacing } from '../../theme';
import type { Plan } from '../../domain/User';

type Props = StackScreenProps<Record<string, undefined>, typeof Routes.Subscription>

interface PlanInfo {
  plan: Plan
  label: string
  price: string
  features: string[]
  highlight?: boolean
}

const PLANS: PlanInfo[] = [
  {
    plan: 'FREE',
    label: 'Gratuit',
    price: '0 €/mois',
    features: [
      'Connexion 1 compte bancaire',
      'Prévisions 30 jours',
      'Alertes basiques',
      'Réserve FlowGuard (commission 1,5 %)',
    ],
  },
  {
    plan: 'PRO',
    label: 'Pro',
    price: '49 €/mois',
    highlight: true,
    features: [
      "Connexion jusqu'à 5 comptes",
      'Prévisions 90 jours',
      'Alertes avancées + seuils personnalisés',
      'Scénarios de simulation',
      'Export CSV/Excel',
      'Réserve FlowGuard (commission 1,5 %)',
    ],
  },
  {
    plan: 'SCALE',
    label: 'Scale',
    price: '99 €/mois',
    features: [
      'Connexion illimitée de comptes',
      'Prévisions 180 jours',
      'Multi-entités',
      'API access',
      'Support dédié',
      'Tout ce qui est inclus dans Pro',
    ],
  },
];

export const SubscriptionScreen: React.FC<Props> = ({ navigation: _navigation }) => {
  const user = useAuthStore((s) => s.user);
  const currentPlan = user?.plan ?? 'FREE';

  const handleSelect = useCallback(
    (plan: Plan) => {
      if (plan === currentPlan) {return;}
      if (plan === 'FREE') {
        Alert.alert(
          'Rétrograder',
          'Vous allez passer au plan Gratuit. Certaines fonctionnalités seront désactivées.',
          [
            { text: 'Annuler', style: 'cancel' },
            {
              text: 'Confirmer',
              style: 'destructive',
              onPress: () =>
                Alert.alert(
                  'Contact',
                  'Contactez support@flowguard.fr pour modifier votre abonnement.',
                ),
            },
          ],
        );
      } else {
        Alert.alert(
          'Mise à niveau',
          `Vous allez passer au plan ${plan}. Vous serez redirigé vers le paiement.`,
          [
            { text: 'Annuler', style: 'cancel' },
            {
              text: 'Continuer',
              onPress: () =>
                Alert.alert(
                  'Bientôt disponible',
                  'Le paiement en ligne sera disponible prochainement.',
                ),
            },
          ],
        );
      }
    },
    [currentPlan],
  );

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        <Text style={styles.title}>Abonnement</Text>
        <Text style={styles.subtitle}>Choisissez le plan adapté à votre activité.</Text>

        {PLANS.map((p) => {
          const isCurrent = p.plan === currentPlan;
          return (
            <FlowGuardCard
              key={p.plan}
              style={[
                styles.planCard,
                p.highlight && styles.planCardHighlight,
                isCurrent && styles.planCardCurrent,
              ]}
              elevated={p.highlight}
            >
              {p.highlight && (
                <View style={styles.popularBadge}>
                  <Text style={styles.popularText}>Le plus populaire</Text>
                </View>
              )}
              {isCurrent && (
                <View style={styles.currentBadge}>
                  <Text style={styles.currentText}>Plan actuel</Text>
                </View>
              )}

              <Text style={styles.planLabel}>{p.label}</Text>
              <Text style={styles.planPrice}>{p.price}</Text>

              <View style={styles.features}>
                {p.features.map((f) => (
                  <View key={f} style={styles.featureRow}>
                    <Text style={styles.checkmark}>✓</Text>
                    <Text style={styles.featureText}>{f}</Text>
                  </View>
                ))}
              </View>

              <FlowGuardButton
                label={isCurrent ? 'Plan actuel' : `Choisir ${p.label}`}
                onPress={() => handleSelect(p.plan)}
                variant={isCurrent ? 'outline' : 'primary'}
                style={styles.selectBtn}
              />
            </FlowGuardCard>
          );
        })}

        <Text style={styles.footer}>
          Sans engagement. Résiliez à tout moment depuis les paramètres de votre compte.
        </Text>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  scroll: { padding: spacing.md, paddingBottom: spacing.xxl },
  title: { ...typography.h2, color: colors.textPrimary, marginBottom: spacing.xs },
  subtitle: { color: colors.textSecondary, ...typography.body, marginBottom: spacing.lg },
  planCard: { marginBottom: spacing.md, position: 'relative', paddingTop: spacing.xl },
  planCardHighlight: { borderColor: colors.primary, borderWidth: 1.5 },
  planCardCurrent: { borderColor: colors.success, borderWidth: 1.5 },
  popularBadge: {
    position: 'absolute',
    top: -10,
    left: spacing.md,
    backgroundColor: colors.primary,
    borderRadius: 8,
    paddingHorizontal: spacing.sm,
    paddingVertical: 3,
  },
  popularText: { color: colors.background, ...typography.caption, fontWeight: '700' },
  currentBadge: {
    position: 'absolute',
    top: -10,
    right: spacing.md,
    backgroundColor: colors.success,
    borderRadius: 8,
    paddingHorizontal: spacing.sm,
    paddingVertical: 3,
  },
  currentText: { color: colors.background, ...typography.caption, fontWeight: '700' },
  planLabel: { ...typography.h3, color: colors.textPrimary, marginBottom: spacing.xs },
  planPrice: { ...typography.h1, color: colors.primary, marginBottom: spacing.md },
  features: { marginBottom: spacing.md },
  featureRow: { flexDirection: 'row', alignItems: 'flex-start', marginBottom: 6 },
  checkmark: { color: colors.success, marginRight: spacing.sm, ...typography.body },
  featureText: { color: colors.textSecondary, ...typography.body, flex: 1 },
  selectBtn: { marginTop: spacing.xs },
  footer: {
    color: colors.textMuted,
    ...typography.caption,
    textAlign: 'center',
    marginTop: spacing.sm,
  },
});
