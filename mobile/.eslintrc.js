module.exports = {
  root: true,
  extends: ['@react-native', 'plugin:@typescript-eslint/recommended'],
  parser: '@typescript-eslint/parser',
  plugins: ['@typescript-eslint'],
  rules: {
    'no-console': 'warn',
    'no-unused-vars': 'off',
    '@typescript-eslint/no-unused-vars': 'error',
    'react-hooks/exhaustive-deps': 'warn',
    'react-hooks/rules-of-hooks': 'error',
    'react-native/no-inline-styles': 'off',
    // Prettier owns semicolons and quotes — disable ESLint's conflicting rules
    semi: 'off',
    quotes: 'off',
    // Allow component expressions passed as props (e.g. tabBarIcon in React Navigation)
    'react/no-unstable-nested-components': ['warn', { allowAsProps: true }],
  },
}
