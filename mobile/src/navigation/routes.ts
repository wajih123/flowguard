export const Routes = {
  // Auth
  Onboarding: 'Onboarding',
  Login: 'Login',
  Register: 'Register',
  RegisterBusiness: 'RegisterBusiness',
  ForgotPassword: 'ForgotPassword',
  Kyc: 'Kyc',
  // User tabs
  Dashboard: 'Dashboard',
  Predictions: 'Predictions',
  Alerts: 'Alerts',
  BankAccount: 'BankAccount',
  Profile: 'Profile',
  // User stack
  AlertDetail: 'AlertDetail',
  TransactionDetail: 'TransactionDetail',
  Transactions: 'Transactions',
  Reserve: 'Reserve',
  BankConnect: 'BankConnect',
  // Business tabs
  BusinessDashboard: 'BusinessDashboard',
  Scenarios: 'Scenarios',
  // Business stack
  Subscription: 'Subscription',
  // Admin
  AdminOverview: 'AdminOverview',
  AdminWeb: 'AdminWeb',
  // Legacy
  Forecast: 'Forecast',
  Spending: 'Spending',
  Scenario: 'Scenario',
  FlashCredit: 'FlashCredit',
} as const

export type RouteName = (typeof Routes)[keyof typeof Routes]
