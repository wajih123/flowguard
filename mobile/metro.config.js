const { getDefaultConfig } = require('expo/metro-config')

const config = getDefaultConfig(__dirname)

config.transformer = {
  ...config.transformer,
  unstable_allowRequireContext: true,
  // Use the Hermes-optimised transform pipeline.
  // Produces bytecode-friendly output that the Hermes engine parses faster,
  // improving cold-start time on both Android and iOS.
  unstable_transformProfile: 'hermes-stable',
}

config.resolver = {
  ...config.resolver,
  sourceExts: [...config.resolver.sourceExts, 'mjs'],
}

module.exports = config
