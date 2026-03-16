import React, { useCallback, useState } from 'react';
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, RefreshControl } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useQuery } from '@tanstack/react-query';
import type { StackScreenProps } from '@react-navigation/stack';
import { FlowGuardCard } from '../../components/FlowGuardCard';
import { FlowGuardLoader } from '../../components/FlowGuardLoader';
import { ErrorScreen } from '../../components/ErrorScreen';
import { useAlerts } from '../../hooks/useAlerts';
import { useAccountStore } from '../../store/accountStore';
import { Routes } from '../../navigation/routes';
import { colors, typography, spacing } from '../../theme';
import * as flowguardApi from '../../api/flowguardApi';

type Props = StackScreenProps<Record<string, undefined>, typeof Routes.AdminOverview>

const POLL_INTERVAL_MS = 60_000;

export const AdminOverviewScreen: React.FC<Props> = ({ navigation }) => {
  const [refreshing, setRefreshing] = useState(false);
  const account = useAccountStore((s) => s.account);

  const {
    data: kpis,
    isLoading,
    isError,
    refetch,
  } = useQuery<Record<string, number | string>>({
    queryKey: ['admin-kpis'],
    queryFn: () => flowguardApi.getAdminKpis(),
    refetchInterval: POLL_INTERVAL_MS,
    staleTime: POLL_INTERVAL_MS,
  });

  const { criticalAlerts } = useAlerts(account?.id);

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    await refetch();
    setRefreshing(false);
  }, [refetch]);

  if (isLoading) {return <FlowGuardLoader />;}
  if (isError) {return <ErrorScreen message="Impossible de charger les KPIs" onRetry={refetch} />;}

  const entries = Object.entries(kpis ?? {});

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      <ScrollView
        contentContainerStyle={styles.scroll}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            tintColor={colors.primary}
            colors={[colors.primary]}
          />
        }
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.header}>
          <Text style={styles.title}>Administration</Text>
          <TouchableOpacity
            onPress={() => navigation.navigate(Routes.AdminWeb as never)}
            style={styles.webBtn}
          >
            <Text style={styles.webBtnText}>Interface web →</Text>
          </TouchableOpacity>
        </View>

        <Text style={styles.pollNote}>Actualisation automatique toutes les 60 s</Text>

        {/* KPI grid */}
        <View style={styles.kpiGrid}>
          {entries.map(([key, value]) => (
            <FlowGuardCard key={key} style={styles.kpiCard}>
              <Text style={styles.kpiLabel}>{formatKey(key)}</Text>
              <Text style={styles.kpiValue}>{String(value)}</Text>
            </FlowGuardCard>
          ))}
        </View>

        {/* Tech alerts */}
        {criticalAlerts.length > 0 && (
          <>
            <Text style={styles.sectionTitle}>Alertes techniques</Text>
            {criticalAlerts.map((alert) => (
              <FlowGuardCard key={alert.id} variant="alert" style={styles.alertRow}>
                <View style={styles.alertHeader}>
                  <Text style={styles.alertType}>{alert.type}</Text>
                  <Text style={styles.alertSeverity}>{alert.severity}</Text>
                </View>
                <Text style={styles.alertMsg}>{alert.message}</Text>
              </FlowGuardCard>
            ))}
          </>
        )}

        <FlowGuardCard variant="info" style={styles.infoCard}>
          <Text style={styles.infoText}>
            En mode adminstrateur, toutes les actions de modification s'effectuent depuis
            l'interface web.
          </Text>
        </FlowGuardCard>

        <TouchableOpacity
          style={styles.webCtaBtn}
          onPress={() => navigation.navigate(Routes.AdminWeb as never)}
        >
          <Text style={styles.webCtaBtnText}>Ouvrir l'interface web complète</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
};

function formatKey(key: string): string {
  return key
    .replace(/_/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .toLowerCase()
    .replace(/^\w/, (c) => c.toUpperCase());
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  scroll: { padding: spacing.md, paddingBottom: spacing.xxl },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: spacing.xs,
  },
  title: { ...typography.h2, color: colors.textPrimary },
  webBtn: {
    backgroundColor: colors.primary + '22',
    borderRadius: 8,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.xs,
  },
  webBtnText: { color: colors.primary, ...typography.body, fontWeight: '700' },
  pollNote: { ...typography.caption, color: colors.textMuted, marginBottom: spacing.md },
  kpiGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm, marginBottom: spacing.md },
  kpiCard: { width: '47%', minWidth: 140 },
  kpiLabel: { ...typography.caption, color: colors.textSecondary, marginBottom: 4 },
  kpiValue: { ...typography.h3, color: colors.primary },
  sectionTitle: {
    ...typography.h3,
    color: colors.textPrimary,
    marginBottom: spacing.sm,
    marginTop: spacing.md,
  },
  alertRow: { marginBottom: spacing.sm },
  alertHeader: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 4 },
  alertType: { ...typography.caption, color: colors.danger, fontWeight: '700' },
  alertSeverity: { ...typography.caption, color: colors.danger },
  alertMsg: { color: colors.textPrimary, ...typography.body },
  infoCard: { marginTop: spacing.lg, marginBottom: spacing.sm },
  infoText: { color: colors.textPrimary, ...typography.body },
  webCtaBtn: {
    backgroundColor: colors.primary,
    borderRadius: 12,
    paddingVertical: spacing.md,
    alignItems: 'center',
    marginTop: spacing.sm,
  },
  webCtaBtnText: { color: colors.background, ...typography.body, fontWeight: '800' },
});
