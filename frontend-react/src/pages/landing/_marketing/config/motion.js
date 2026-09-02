/**
 * Sprint marketing v2 Flagship — motion tokens centralisés.
 *
 * Easings signature, durations, et timing du Hero. Importés par les hooks
 * (useLenis, useScrollTrigger) + les composants animés (Hero, MagneticButton,
 * StickyShowcase, etc.).
 *
 * Règle : pas de magic number d'easing/duration dans les composants — tout
 * vient d'ici pour cohérence éditoriale.
 */

export const EASE = {
  // Signature : exponential out (Linear-style buttery)
  expoOut: (t) => Math.min(1, 1.001 - Math.pow(2, -10 * t)),
  // Bounce léger pour CTA emergence
  bounceSoft: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
  // Custom magnetic
  magnetic: 'cubic-bezier(0.22, 1, 0.36, 1)',
  // Standard out
  smoothOut: 'cubic-bezier(0.25, 0.46, 0.45, 0.94)',
};

export const DURATION = {
  fast: 0.18,
  base: 0.32,
  slow: 0.6,
  scenic: 1.2,
};

// Lenis config officiel (smooth scroll buttery)
export const LENIS_CONFIG = {
  duration: 1.2,
  easing: EASE.expoOut,
  smoothWheel: true,
  smoothTouch: false, // touch reste natif (a11y mobile)
};

// Hero timing chrono (en secondes)
export const HERO_TIMING = {
  EYEBROW_AT: 0.0,
  H1_LINE1_START: 0.2,
  H1_CHAR_INTERVAL_MS: 40,
  PAUSE_AFTER_LINE1_MS: 100,
  H1_LINE2_START: 1.1,
  H1_LINE2_REVEAL_MS: 600,
  SUBTITLE_AT: 1.6,
  TRUSTBAR_AT: 1.8,
  KANBAN_FIRST_MOVE: 2.5,
  KANBAN_SECOND_MOVE: 4.0,
  KANBAN_LOOP_MS: 8000,
};
