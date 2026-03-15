import React from 'react'
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs'
import { createStackNavigator } from '@react-navigation/stack'
import { View, Text, StyleSheet } from 'react-native'
import { DashboardScreen } from '../screens/Dashboard/DashboardScreen'
import { ForecastScreen } from '../screens/Forecast/ForecastScreen'
import { AlertsScreen } from '../screens/Alerts/AlertsScreen'
import { SpendingScreen } from '../screens/Spending/SpendingScreen'
import { ScenarioScreen } from '../screens/Scenario/ScenarioScreen'
import { FlashCreditScreen } from '../screens/FlashCredit/FlashCreditScreen'
import { BankConnectScreen } from '../screens/BankConnect/BankConnectScreen'
import { Routes } from './routes'
import { useAlertStore } from '../store/alertStore'
import { colors, spacing } from '../theme'

const Tab = createBottomTabNavigator()
const DashboardStack = createStackNavigator()

const DashboardStackNavigator: React.FC = () => {
  return (
    <DashboardStack.Navigator
      screenOptions={{
        headerShown: false,
        cardStyle: { backgroundColor: colors.background },
      }}
    >
      <DashboardStack.Screen name={Routes.Dashboard} component={DashboardScreen} />
      <DashboardStack.Screen name={Routes.Spending} component={SpendingScreen} />
      <DashboardStack.Screen name={Routes.Scenario} component={ScenarioScreen} />
      <DashboardStack.Screen name={Routes.FlashCredit} component={FlashCreditScreen} />
      <DashboardStack.Screen
        name={Routes.BankConnect}
        component={BankConnectScreen as React.ComponentType<object>}
      />
    </DashboardStack.Navigator>
  )
}

const ProfilePlaceholder: React.FC = () => (
  <View style={styles.placeholder}>
    <Text style={styles.placeholderText}>Profil</Text>
  </View>
)

const TabIcon: React.FC<{ label: string; focused: boolean }> = ({ label, focused }) => {
  const iconMap: Record<string, string> = {
    Accueil: '🏠',
    Prévisions: '📈',
    Alertes: '🔔',
    Profil: '👤',
  }

  return (
    <Text style={[styles.tabIcon, focused && styles.tabIconActive]}>{iconMap[label] ?? '•'}</Text>
  )
}

export const MainTabNavigator: React.FC = () => {
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
        tabBarLabelStyle: {
          fontSize: 11,
          fontWeight: '600',
        },
      }}
    >
      <Tab.Screen
        name="DashboardTab"
        component={DashboardStackNavigator}
        options={{
          tabBarLabel: 'Accueil',
          tabBarIcon: ({ focused }) => <TabIcon label="Accueil" focused={focused} />,
        }}
      />
      <Tab.Screen
        name={Routes.Forecast}
        component={ForecastScreen}
        options={{
          tabBarLabel: 'Prévisions',
          tabBarIcon: ({ focused }) => <TabIcon label="Prévisions" focused={focused} />,
        }}
      />
      <Tab.Screen
        name={Routes.Alerts}
        component={AlertsScreen}
        options={{
          tabBarLabel: 'Alertes',
          tabBarIcon: ({ focused }) => <TabIcon label="Alertes" focused={focused} />,
          tabBarBadge: unreadCount > 0 ? unreadCount : undefined,
          tabBarBadgeStyle: {
            backgroundColor: colors.danger,
            color: colors.textPrimary,
            fontSize: 10,
            minWidth: 18,
            height: 18,
            borderRadius: 9,
          },
        }}
      />
      <Tab.Screen
        name={Routes.Profile}
        component={ProfilePlaceholder}
        options={{
          tabBarLabel: 'Profil',
          tabBarIcon: ({ focused }) => <TabIcon label="Profil" focused={focused} />,
        }}
      />
    </Tab.Navigator>
  )
}

const styles = StyleSheet.create({
  placeholder: {
    flex: 1,
    backgroundColor: colors.background,
    justifyContent: 'center',
    alignItems: 'center',
  },
  placeholderText: {
    color: colors.textPrimary,
    fontSize: 18,
  },
  tabIcon: {
    fontSize: 20,
    opacity: 0.5,
  },
  tabIconActive: {
    opacity: 1,
  },
})
