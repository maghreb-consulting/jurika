/**
 * Lot L6b (E9) : apparence des graphiques qui suit le theme (clair ou sombre) par les variables
 * CSS des jetons (index.css). Les couleurs des SERIES restent celles de lib/chartTheme ; la grille,
 * le texte des axes, la legende et l'infobulle lisent les jetons, pour rester lisibles en sombre.
 */
export const AXES = {
  grille: 'var(--color-border)',
  texte: 'var(--color-fg-muted)',
  curseur: 'var(--color-border-hi)',
} as const;

export const INFOBULLE_GRAPHIQUE = {
  fontSize: 12,
  borderRadius: 8,
  backgroundColor: 'var(--color-bg-raised)',
  border: '1px solid var(--color-border-hi)',
  color: 'var(--color-fg)',
} as const;

export const LIBELLE_INFOBULLE = { fontWeight: 600, color: 'var(--color-heading)' } as const;

export const TEXTE_LEGENDE = { fontSize: 11, color: 'var(--color-fg-muted)' } as const;
