import React from 'react'
import { View, Text, StyleSheet } from 'react-native'
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs'
import { createStackNavigator } from '@react-navigation/stack'
import { DashboardScreen } from '../screens/Dashboard/DashboardScreen'
import { ForecastScreen } from '../screens/Forecast/ForecastScreen'
import { AlertsScreen } from '../screens/Alerts/AlertsScreen'
import { AlertDetailScreen } from '../screens/Alerts/AlertDetailScreen'
import { BankAccountScreen } from '../screens/BankAccount/BankAccountScreen'
import { BankConnectScreen } from '../screens/BankConnect/BankConnectScreen'
import { ProfileScreen } from '../screens/Profile/ProfileScreen'
import { TransactionsScreen } from '../screens/Transactions/TransactionsScreen'
import { TransactionDetailScreen } from '../screens/Transactions/TransactionDetailScreen'
import { ReserveScreen } from '../screens/Reserve/ReserveScreen'
import { KycScreen } from '../screens/Kyc/KycScreen'
import { InvoicesScreen } from '../screens/Invoices/InvoicesScreen'
import { BudgetScreen } from '../screens/Budget/BudgetScreen'
import { TaxScreen } from '../screens/Tax/TaxScreen'
import { Routes } from './routes'
import { useAlertStore } from '../store/alertStore'
import { colors, spacing } from '../theme'

const Tab = createBottomTabNavigator()
const Stack = createStackNavigator()

const TabIcon: React.FC<{ emoji: string; focused: boolean; badge?: number }> = ({
  emoji,
  focused,
  badge,
}) => (
  <View style={styles.iconWrapper}>
    <Text style={[styles.icon, focused && styles.iconActive]}>{emoji}</Text>
    {badge !== undefined && badge > 0 && (
      <View style={styles.badge}>
        <Text style={styles.badgeText}>{badge > 99 ? '99+' : badge}</Text>
      </View>
    )}
  </View>
)

const DashboardStack: React.FC = () => (
  <Stack.Navigator screenOptions={{ headerShown: false }}>
    <Stack.Screen name={Routes.Dashboard} component={DashboardScreen} />
    <Stack.Screen
      name={Routes.Transactions}
      component={TransactionsScreen as React.ComponentType<object>}
    />
    <Stack.Screen
      name={Routes.TransactionDetail}
      component={TransactionDetailScreen as React.ComponentType<object>}
      options={{ presentation: 'modal' }}
    />
    <Stack.Screen name={Routes.Reserve} component={ReserveScreen as React.ComponentType<object>} />
    <Stack.Screen
      name={Routes.BankConnect}
      component={BankConnectScreen as React.ComponentType<object>}
    />
  </Stack.Navigator>
)

const AlertsStack: React.FC = () => (
  <Stack.Navigator screenOptions={{ headerShown: false }}>
    <Stack.Screen name={Routes.Alerts} component={AlertsScreen} />
    <Stack.Screen
      name={Routes.AlertDetail}
      component={AlertDetailScreen as React.ComponentType<object>}
      options={{ presentation: 'modal' }}
    />
    <Stack.Screen name={Routes.Reserve} component={ReserveScreen as React.ComponentType<object>} />
  </Stack.Navigator>
)

const BankStack: React.FC = () => (
  <Stack.Navigator screenOptions={{ headerShown: false }}>
    <Stack.Screen
      name={Routes.BankAccount}
      component={BankAccountScreen as React.ComponentType<object>}
    />
    <Stack.Screen
      name={Routes.BankConnect}
      component={BankConnectScreen as React.ComponentType<object>}
    />
  </Stack.Navigator>
)

const ProfileStack: React.FC = () => (
  <Stack.Navigator screenOptions={{ headerShown: false }}>
    <Stack.Screen name={Routes.Profile} component={ProfileScreen as React.ComponentType<object>} />
    <Stack.Screen name={Routes.Kyc} component={KycScreen} />
    <Stack.Screen name={Routes.Reserve} component={ReserveScreen as React.ComponentType<object>} />
  </Stack.Navigator>
)

const FinanceStack: React.FC = () => (
  <Stack.Navigator screenOptions={{ headerShown: false }}>
    <Stack.Screen
      name={Routes.Invoices}
      component={InvoicesScreen as React.ComponentType<object>}
    />
    <Stack.Screen name={Routes.Budget} component={BudgetScreen as React.ComponentType<object>} />
    <Stack.Screen name={Routes.Tax} component={TaxScreen as React.ComponentType<object>} />
  </Stack.Navigator>
)

export const UserNavigator: React.FC = () => {
  const unreadCount = useAlertStore((s) => s.unreadCount)

  return (
    <Tab.Navigator
      screenOptions={{
        headerShown: false,
        tabBarStyle: {
          backgroundColor: colors.surface,
          borderTopColor: colors.cardBorder,
          borderTopWidth: 1,
          paddingBottom: spacing.xs,
          paddingTop: spacing.xs,
          height: 60,
        },
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textMuted,
        tabBarLabelStyle: { fontSize: 11, fontWeight: '600' },
      }}
    >
      <Tab.Screen
        name="DashboardTab"
        component={DashboardStack}
        options={{
          tabBarLabel: 'Accueil',
          tabBarIcon: ({ focused }) => <TabIcon emoji="🏠" focused={focused} />,
        }}
      />
      <Tab.Screen
        name="PredictionsTab"
        component={ForecastScreen}
        options={{
          tabBarLabel: 'Prédictions',
          tabBarIcon: ({ focused }) => <TabIcon emoji="📈" focused={focused} />,
        }}
      />
      <Tab.Screen
        name="AlertsTab"
        component={AlertsStack}
        options={{
          tabBarLabel: 'Alertes',
          tabBarIcon: ({ focused }) => <TabIcon emoji="🔔" focused={focused} badge={unreadCount} />,
        }}
      />
      <Tab.Screen
        name="BankTab"
        component={BankStack}
        options={{
          tabBarLabel: 'Ma banque',
          tabBarIcon: ({ focused }) => <TabIcon emoji="💳" focused={focused} />,
        }}
      />
      <Tab.Screen
        name="FinanceTab"
        component={FinanceStack}
        options={{
          tabBarLabel: 'Finances',
          tabBarIcon: ({ focused }) => <TabIcon emoji="📋" focused={focused} />,
        }}
      />
      <Tab.Screen
        name="ProfileTab"
        component={ProfileStack}
        options={{
          tabBarLabel: 'Profil',
          tabBarIcon: ({ focused }) => <TabIcon emoji="👤" focused={focused} />,
        }}
      />
    </Tab.Navigator>
  )
}

const styles = StyleSheet.create({
  iconWrapper: {
    position: 'relative',
    alignItems: 'center',
    justifyContent: 'center',
  },
  icon: {
    fontSize: 22,
    opacity: 0.5,
  },
  iconActive: {
    opacity: 1,
  },
  badge: {
    position: 'absolute',
    top: -4,
    right: -8,
    backgroundColor: colors.danger,
    borderRadius: 8,
    minWidth: 16,
    height: 16,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 2,
  },
  badgeText: {
    color: '#fff',
    fontSize: 9,
    fontWeight: '700',
  },
})
