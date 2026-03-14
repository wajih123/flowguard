export const typography = {
  hero: { fontSize: 48, fontWeight: '900' as const, letterSpacing: -1 },
  h1: { fontSize: 32, fontWeight: '800' as const },
  h2: { fontSize: 24, fontWeight: '700' as const },
  h3: { fontSize: 18, fontWeight: '700' as const },
  body: { fontSize: 15, fontWeight: '400' as const, lineHeight: 22 },
  caption: { fontSize: 12, fontWeight: '400' as const },
  label: {
    fontSize: 11,
    fontWeight: '700' as const,
    letterSpacing: 1,
    textTransform: 'uppercase' as const,
  },
} as const
