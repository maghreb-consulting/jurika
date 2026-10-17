import { describe, expect, it } from 'vitest';
import { statutTicketColors } from '../chartTheme';
import { STATUT_TICKET_LABELS } from '../dashboardLabels';

/**
 * Lot L6b (E17) : les couleurs des statuts de ticket etaient indexees par NOUVEAU / EN_COURS /
 * CLOTURE, statuts que le serveur n'emet plus : aucune tranche ne recevait sa couleur (motif 9).
 * Chaque statut du guide doit avoir la sienne, et aucune cle ne doit viser un statut inconnu.
 */
describe('statutTicketColors', () => {
  it('couvre exactement les statuts de ticket du guide', () => {
    expect(Object.keys(statutTicketColors).sort()).toEqual(Object.keys(STATUT_TICKET_LABELS).sort());
  });

  it('lit les couleurs dans les jetons (clair et sombre), jamais en dur', () => {
    for (const c of Object.values(statutTicketColors)) expect(c).toMatch(/^var\(--color-[a-z-]+\)$/);
  });
});
