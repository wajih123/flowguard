import React, { useCallback } from 'react';
import { ScrollView, View, Text, RefreshControl, StyleSheet } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { StackNavigationProp } from '@react-navigation/stack';
import { SafeAreaView } from 'react-native-safe-area-context';
import { BalanceCard } from './components/BalanceCard';
import { HealthScoreMeter } from './components/HealthScoreMeter';
import { MiniTreasuryChart } from './components/MiniTreasuryChart';
import { CriticalPointBanner } from './components/CriticalPointBanner';
import { QuickActions } from './components/QuickActions';
import { SkeletonCard } from '../../components/SkeletonCard';
import { ErrorScreen } from '../../components/ErrorScreen';
import { AlertCard } from '../Alerts/components/AlertCard';
import { useForecast } from '../../hooks/useForecast';
import { useAlerts } from '../../hooks/useAlerts';
import { useAccountStore } from '../../store/accountStore';
import { useAuthStore } from '../../store/authStore';
import { useAlertStore } from '../../store/alertStore';
import { Routes } from '../../navigation/routes';
import { colors, typography, spacing } from '../../theme';

export const DashboardScreen: React.FC = () => {
  const navigation = useNavigation<StackNavigationProp<Record<string, undefined>>>();
  const account = useAccountStore((s) => s.account);
  const user = useAuthStore((s) => s.user);
  const unreadCount = useAlertStore((s) => s.unreadCount);

  const {
    forecast,
    isLoading: forecastLoading,
    isError: forecastError,
    refetch: refetchForecast,
  } = useForecast(account?.id, 30);

  const { criticalAlerts, markAsRead } = useAlerts(account?.id);

  const [refreshing, setRefreshing] = React.useState(false);

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    await refetchForecast();
    setRefreshing(false);
  }, [refetchForecast]);

  if (forecastError) {
    return (
      <ErrorScreen
        message="Impossible de charger vos données"
        onRetry={() => refetchForecast()}
      />
    );
  }

  return (
    <SafeAreaView style={styles.safeArea}>
      <ScrollView
        style={styles.container}
        contentContainerStyle={styles.content}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            tintColor={colors.primary}
            colors={[colors.primary]}
          />
        }
      >
        <View style={styles.header}>
          <Text style={styles.greeting}>
            Bonjour {user?.firstName ?? ''} 👋
          </Text>
          {unreadCount > 0 && (
            <View style={styles.badge}>
              <Text style={styles.badgeText}>{unreadCount}</Text>
            </View>
          )}
        </View>

        {forecastLoading ? (
          <View style={styles.skeletons}>
            <SkeletonCard height={140} />
            <View style={styles.spacer} />
            <SkeletonCard height={60} />
            <View style={styles.spacer} />
            <SkeletonCard height={160} />
            <View style={styles.spacer} />
            <SkeletonCard height={120} />
          </View>
        ) : forecast ? (
          <>
            <BalanceCard
              balance={account?.balance ?? 0}
              healthScore={forecast.healthScore}
              confidence={forecast.confidence}
            />

            <HealthScoreMeter score={forecast.healthScore} />

            <CriticalPointBanner
              criticalPoints={forecast.criticalPoints}
              onActivateCredit={() => navigation.navigate(Routes.FlashCredit)}
            />

            <MiniTreasuryChart predictions={forecast.predictions} />

            <QuickActions
              onFlashCredit={() => navigation.navigate(Routes.FlashCredit)}
              onSpending={() => navigation.navigate(Routes.Spending)}
              onScenario={() => navigation.navigate(Routes.Scenario)}
              onBankConnect={() => navigation.navigate(Routes.BankConnect)}
            />

            {criticalAlerts.filter((a) => !a.isRead).length > 0 && (
              <View style={styles.alertsSection}>
                <Text style={styles.sectionTitle}>ALERTES CRITIQUES</Text>
                {criticalAlerts
                  .filter((a) => !a.isRead)
                  .map((alert) => (
                    <AlertCard
                      key={alert.id}
                      alert={alert}
                      onMarkRead={() => markAsRead(alert.id)}
                    />
                  ))}
              </View>
            )}
          </>
        ) : null}
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: colors.background,
  },
  container: {
    flex: 1,
  },
  content: {
    padding: spacing.md,
    paddingBottom: spacing.xxl,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: spacing.lg,
  },
  greeting: {
    color: colors.textPrimary,
    fontSize: typography.h2.fontSize,
    fontWeight: typography.h2.fontWeight,
  },
  badge: {
    backgroundColor: colors.danger,
    borderRadius: 12,
    minWidth: 24,
    height: 24,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: spacing.sm,
  },
  badgeText: {
    color: colors.textPrimary,
    fontSize: 12,
    fontWeight: '700',
  },
  skeletons: {
    gap: spacing.md,
  },
  spacer: {
    height: spacing.sm,
  },
  alertsSection: {
    marginTop: spacing.md,
  },
  sectionTitle: {
    color: colors.textMuted,
    fontSize: typography.label.fontSize,
    fontWeight: typography.label.fontWeight,
    letterSpacing: typography.label.letterSpacing,
    marginBottom: spacing.sm,
  },
});
