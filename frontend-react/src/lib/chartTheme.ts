/**
 * Sprint 12.5/12.6 — Theme centralise pour Recharts.
 *
 * Recharts (libs SVG-based) ne lit pas les CSS variables dans ses props
 * fill/stroke -- on duplique les hex de la palette LIGHT marketing ici.
 * Source de verite PRIMAIRE = index.css `@theme` (default light).
 *
 * Sprint 12.6 : aligne sur la palette light editorial (velin/navy/or durci).
 * Pour le mode dark via toggle, un useChartColors() hook lisant
 * getComputedStyle(:root) pourrait etre ajoute pour suivre data-theme=dark
 * dynamiquement -- pour l'instant l'app vit majoritairement en light.
 */
export const chartTheme = {
  accent: '#a8853d', // gold-700 durci (AA sur fond velin)
  accentSoft: '#d4b06a', // gold-400
  success: '#10b981', // emerald-500
  successSoft: '#34d399', // emerald-400
  warning: '#f59e0b',
  danger: '#ef4444',
  fg: '#050d1f', // navy-900
  fgMuted: '#1c3461', // navy-600
  fgSubtle: '#64748b', // slate-500
  border: '#e2e1da', // border subtle velin
  bgRaised: '#ffffff', // cards
} as const;

/**
 * Style commun pour les Tooltip Recharts -- adopte les tokens light
 * (fond bg-overlay velin, bordure border-hi, fg navy fonce).
 */
export const chartTooltipStyle = {
  fontSize: 12,
  borderRadius: 8,
  backgroundColor: '#f4f4ef', // bg-overlay velin
  border: '1px solid #c8c7bf', // border-hi
  color: chartTheme.fg,
} as const;

/**
 * Palette categorielle (donuts / series multiples) alignee sur la charte :
 * navy signature -> or -> emeraude -> slate -> ambre -> violet sourd -> rose.
 * Suffisamment contrastee et non criarde (tons sourds editoriaux).
 */
export const chartCategoricalPalette = [
  '#1c3461', // navy-600
  '#a8853d', // or durci
  '#10b981', // emeraude
  '#64748b', // slate-500
  '#d4b06a', // or clair
  '#7c6ba0', // violet sourd
  '#e29578', // terracotta sourd
  '#4b6584', // bleu ardoise
] as const;

/**
 * Couleurs semantiques par statut de ticket (donut employe/superviseur).
 * Cle = statut backend ; valeur = hex charte.
 */
// Lot L6b (E17) : les cles etaient NOUVEAU / EN_COURS / CLOTURE, statuts que le serveur n'emet
// plus depuis le lot 1 : aucune tranche ne prenait sa couleur (motif 9). Cles des cinq statuts
// du guide ; couleurs lues dans les jetons (clair et sombre).
export const statutTicketColors: Record<string, string> = {
  CREATION_TICKET: 'var(--color-info)', // ouverture, collecte
  GENERATION_DOCUMENTS: 'var(--color-accent)', // redaction des actes
  DEROULEMENT_DEMARCHE: 'var(--color-warning)', // demarches administratives
  CLOTURE_DOSSIER: 'var(--color-success)', // termine
  ANNULE: 'var(--color-fg-subtle)', // abandonne
};
