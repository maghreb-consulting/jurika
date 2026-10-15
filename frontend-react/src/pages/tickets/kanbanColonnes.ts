import type { TicketStatut } from '../../types/ticket';

// Sprint 12.5 T8 -- Colonnes Kanban repeintes : bandeau couleur status sur fond
// bg-overlay navy (au lieu de tints clairs hardcodes). Drag & drop preserve --
// pas de modification des handlers onDragStart/onDragOver/onDrop.
//
// Lot 2 (2026-09-07) -- `sortie: true` marque ANNULE : ce n'est pas la cinquieme
// etape d'une progression mais une SORTIE LATERALE, atteignable depuis n'importe
// lequel des quatre autres. La colonne reste dans la rangee (decision du cabinet)
// mais se lit differemment : elle est detachee par une gouttiere, grisee, et
// porte la mention explicite.
export const COLUMNS: { statut: TicketStatut; accent: string; sortie?: boolean }[] = [
  { statut: 'CREATION_TICKET', accent: 'border-info' }, // L6b : l'or est reserve a l'action principale
  { statut: 'GENERATION_DOCUMENTS', accent: 'border-warning' }, // ambre : production des actes
  { statut: 'DEROULEMENT_DEMARCHE', accent: 'border-warning' }, // ambre : demarches administratives
  { statut: 'CLOTURE_DOSSIER', accent: 'border-success' }, // emeraude pour validé
  { statut: 'ANNULE', accent: 'border-danger', sortie: true }, // rouge : sortie latérale
];

/**
 * Lot L6b (E7) : largeurs minimales des colonnes. Les cinq colonnes tiennent dans la zone
 * de contenu d'un ecran de 1 366 px (menu deplie) : a 240 px chacune, la cinquieme etait
 * coupee a droite (vu a l'ecran). La sortie laterale (ANNULE) est un peu plus etroite.
 * En dessous de la largeur minimale, la rangee defile horizontalement, et l'ecran le dit.
 */
export const LARGEUR_MIN_COLONNE = 192;
export const LARGEUR_MIN_SORTIE = 176;
/** Gouttiere entre colonnes (classe gap-3) et retrait de la sortie (ml-2 + pl-4 + filet). */
const GOUTTIERE = 12;
const RETRAIT_SORTIE = 8 + 16 + 1;

/** Pistes de la grille, derivees de COLUMNS (une piste par statut, jamais de retour a la ligne). */
export const PISTES_KANBAN = COLUMNS.map((c) =>
  c.sortie ? `minmax(${LARGEUR_MIN_SORTIE}px, 0.85fr)` : `minmax(${LARGEUR_MIN_COLONNE}px, 1fr)`,
).join(' ');

/** Largeur minimale de la rangee entiere, en pixels. */
export function largeurMinimaleKanban(): number {
  return COLUMNS.reduce(
    (somme, c) => somme + (c.sortie ? LARGEUR_MIN_SORTIE + RETRAIT_SORTIE : LARGEUR_MIN_COLONNE),
    GOUTTIERE * (COLUMNS.length - 1),
  );
}
