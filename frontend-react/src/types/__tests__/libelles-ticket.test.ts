import { describe, expect, it } from 'vitest';
import {
  FORME_JURIDIQUE_LABELS,
  PRIORITE_LABELS,
  STATUT_LABELS,
  STATUT_LABELS_COURTS,
  TICKET_TYPE_LABELS,
} from '../ticket';

/**
 * Lot L6b (E7, P11) : les libelles affiches des tickets sont en francais correct, accents
 * compris ("Creation" sans accent etait affiche sur chaque carte de ticket).
 */
describe('Libelles affiches des tickets', () => {
  it('types d’operation accentues', () => {
    expect(TICKET_TYPE_LABELS.CREATION).toBe('Création');
    expect(TICKET_TYPE_LABELS.SUCCURSALE_ETR).toBe('Succursale étrangère');
    expect(TICKET_TYPE_LABELS.FERMETURE_SUCCURSALE).toBe('Fermeture de succursale');
    expect(TICKET_TYPE_LABELS.IMPORT).toBe('Import de dossier');
    expect(FORME_JURIDIQUE_LABELS.SARL_AU).toBe('SARL à associé unique (SARL AU)');
  });

  it('aucun libelle ne garde une forme non accentuee connue', () => {
    const tous = [
      ...Object.values(TICKET_TYPE_LABELS),
      ...Object.values(STATUT_LABELS),
      ...Object.values(STATUT_LABELS_COURTS),
      ...Object.values(PRIORITE_LABELS),
      ...Object.values(FORME_JURIDIQUE_LABELS),
    ];
    const fautes = /\b(Creation|Cloture|Generation|Deroulement|demarche|etrangere|associe|Annule)\b/;
    expect(tous.filter((l) => fautes.test(l))).toEqual([]);
  });
});
