import React from 'react'
import { createStackNavigator } from '@react-navigation/stack'
import { AdminOverviewScreen } from '../screens/Admin/AdminOverviewScreen'
import { AdminWebScreen } from '../screens/Admin/AdminWebScreen'
import { Routes } from './routes'
import { colors } from '../theme'

const Stack = createStackNavigator()

export const AdminNavigator: React.FC = () => (
  <Stack.Navigator
    screenOptions={{
      headerShown: false,
      cardStyle: { backgroundColor: colors.background },
    }}
  >
    <Stack.Screen
      name={Routes.AdminOverview}
      component={AdminOverviewScreen as React.ComponentType<object>}
    />
    <Stack.Screen name={Routes.AdminWeb} component={AdminWebScreen as React.ComponentType<object>} />
  </Stack.Navigator>
)
