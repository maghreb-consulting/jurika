/**
 * JURIKA Design Tokens (TypeScript mirror of src/index.css @theme).
 *
 * Reference: AGENT_BRIEF_RECONCILIATION.md section 4.3 (Killer Feature: Density UI).
 * Brief reference: AGENT_BRIEF.md section 3.1 + 3.2 + 3.3.
 *
 * Usage:
 *   - Prefer Tailwind classes referencing CSS vars (e.g. bg-[var(--color-bg)]).
 *   - Use these constants ONLY in TS logic (e.g. dynamic chart colors).
 *   - NEVER paste hex codes in components.
 */

export const colors = {
  bg: "var(--color-bg)",
  bgRaised: "var(--color-bg-raised)",
  bgOverlay: "var(--color-bg-overlay)",
  border: "var(--color-border)",
  borderHi: "var(--color-border-hi)",
  fg: "var(--color-fg)",
  fgMuted: "var(--color-fg-muted)",
  fgSubtle: "var(--color-fg-subtle)",
  accent: "var(--color-accent)",
  success: "var(--color-success)",
  warning: "var(--color-warning)",
  danger: "#ef4444",
} as const;

export const radius = {
  sm: "4px",
  md: "6px",
  lg: "8px",
  xl: "12px",
} as const;

/** Spacing scale (multiples of 4). Avoid arbitrary px values. */
export const spacing = [0, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64] as const;

/** Density-locked text sizes. Default body is 14px (Linear/Stripe Dashboard style). */
export const fontSize = {
  xs: "12px",
  sm: "13px",
  base: "14px",
  lg: "16px",
  xl: "18px",
  "2xl": "24px",
} as const;

/**
 * Density classes (always apply via cn(...) helper).
 * Buttons: h-8 (32px) default, h-9 (36px) medium, h-10 (40px) large. Never bigger.
 * Inputs: h-9 (36px) with text-sm value.
 * Tables: 40px row height.
 * Cards: p-4 (NOT p-6).
 */
export const density = {
  button: {
    sm: "h-7 px-2.5 text-xs",
    base: "h-8 px-3 text-sm",
    md: "h-9 px-4 text-sm",
    lg: "h-10 px-5 text-sm",
  },
  input: "h-9 px-3 text-sm",
  card: "rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-raised)] p-4",
  tableRow: "h-10 text-sm",
  badge: "h-5 px-2 text-[11px] rounded-full font-medium",
} as const;

/** Severity → Tailwind class map for deadlines (Killer Feature §4.2). */
export const severityClass = {
  INFO: "bg-blue-500/15 text-blue-400 border-blue-500/30",
  WARNING: "bg-amber-500/15 text-amber-400 border-amber-500/30",
  CRITICAL: "bg-red-500/15 text-red-400 border-red-500/30",
} as const;

/** Ticket status → badge class. */
export const ticketStatusClass = {
  NOUVEAU: "bg-blue-500/15 text-blue-400 border-blue-500/30",
  EN_COURS: "bg-amber-500/15 text-amber-400 border-amber-500/30",
  CLOTURE: "bg-emerald-500/15 text-emerald-400 border-emerald-500/30",
  ANNULE: "bg-gray-500/15 text-gray-400 border-gray-500/30",
} as const;
