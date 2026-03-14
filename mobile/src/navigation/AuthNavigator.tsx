import React from 'react'
import { createStackNavigator } from '@react-navigation/stack'
import { OnboardingScreen } from '../screens/Auth/OnboardingScreen'
import { LoginScreen } from '../screens/Auth/LoginScreen'
import { RegisterScreen } from '../screens/Auth/RegisterScreen'
import { RegisterBusinessScreen } from '../screens/Auth/RegisterBusinessScreen'
import { ForgotPasswordScreen } from '../screens/Auth/ForgotPasswordScreen'
import { KycScreen } from '../screens/Kyc/KycScreen'
import { Routes } from './routes'
import { colors } from '../theme'

const Stack = createStackNavigator()

export const AuthNavigator: React.FC = () => {
  return (
    <Stack.Navigator
      initialRouteName={Routes.Login}
      screenOptions={{
        headerShown: false,
        cardStyle: { backgroundColor: colors.background },
        animationEnabled: true,
      }}
    >
      <Stack.Screen name={Routes.Onboarding} component={OnboardingScreen} />
      <Stack.Screen name={Routes.Login} component={LoginScreen} />
      <Stack.Screen name={Routes.Register} component={RegisterScreen} />
      <Stack.Screen name={Routes.RegisterBusiness} component={RegisterBusinessScreen} />
      <Stack.Screen name={Routes.ForgotPassword} component={ForgotPasswordScreen} />
      <Stack.Screen name={Routes.Kyc} component={KycScreen} />
    </Stack.Navigator>
  )
}
