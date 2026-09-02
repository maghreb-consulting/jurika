export const colors = {
  primary: "#2563EB",
  primaryDark: "#1D4ED8",
  dark: "#0F172A",
  bg: "#F8FAFC",
  surface: "#FFFFFF",
  border: "#E2E8F0",
  textMuted: "#64748B",
  textSubtle: "#94A3B8",
  success: "#10B981",
  danger: "#EF4444",
  warning: "#F59E0B",
  violet: "#7C3AED",
  amber: "#F59E0B",
  slate100: "#F1F5F9",
  slate200: "#E2E8F0",
  slate700: "#334155",
} as const;

export type ColorKey = keyof typeof colors;

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
} as const;

export const radius = {
  sm: 6,
  md: 10,
  lg: 14,
  xl: 20,
  pill: 999,
} as const;

export const typography = {
  h1: { fontSize: 24, fontWeight: "700" as const, color: colors.dark },
  h2: { fontSize: 20, fontWeight: "700" as const, color: colors.dark },
  h3: { fontSize: 16, fontWeight: "600" as const, color: colors.dark },
  body: { fontSize: 14, fontWeight: "400" as const, color: colors.slate700 },
  caption: { fontSize: 12, fontWeight: "400" as const, color: colors.textMuted },
} as const;
