import '@testing-library/jest-dom/vitest';
import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { FriseDemarches } from '../FriseDemarches';
import { etatDelai, JOURS_ECHEANCE_PROCHE } from '../friseDemarches';
import type { LigneDemarche, PointAttention, VueDemarches } from '../../../types/demarche';

/**
 * Lot L6b (E7, point 8) : la frise des demarches. La couleur du delai vient des points
 * d'attention du SERVEUR (DEPASSE / CRITIQUE J-3 / APPROCHE J-15) ; elle n'est jamais seule
 * (texte et icone).
 */
function ligne(ordre: number, p: Partial<LigneDemarche> = {}): LigneDemarche {
  return {
    demarcheId: `d${ordre}`, ordre, phaseCode: 'P1', phaseLibelle: 'Dépôts', libelle: `Démarche ${ordre}`,
    statutTicket: 'DEROULEMENT_DEMARCHE', acteur: null, organisme: null, obligatoire: true, conditionApplication: null,
    piecesEntrantes: null, documentProduit: null, justificatifsTexte: null, modeleJurika: null, delai: null,
    coutIndicatif: null, variablesAlimentees: null, delaiCalculable: true, delaiDepartManquant: null,
    justificatifsAttendus: [], etat: 'A_FAIRE', motif: null, cocheAt: null, documentsDeposes: [],
    actionnableMaintenant: true, horsSequence: false, formaliteCode: null, formaliteVolet: null, depotOrdre: null,
    deposeLe: null, journal: [], ...p,
  };
}

function point(ordre: number, severite: PointAttention['severite'], joursRestants: number, echeance: string): PointAttention {
  return { ordre, libelle: `Démarche ${ordre}`, delai: null, echeance, joursRestants, severite };
}

const vue: VueDemarches = {
  workflowType: 'CREATION',
  statutCourant: 'DEROULEMENT_DEMARCHE',
  phases: [{
    code: 'P1', libelle: 'Dépôts', traitees: 2, total: 6,
    demarches: [
      ligne(1, { etat: 'COCHEE', cocheAt: '2026-10-01T10:00:00Z' }),
      ligne(2),
      ligne(3),
      ligne(4, { echeance: '2026-12-20' }),
      ligne(5),
      ligne(6, { etat: 'NON_APPLICABLE', obligatoire: false }),
    ],
  }],
  avancement: {
    statutCourant: 'DEROULEMENT_DEMARCHE', position: 3, totalStatuts: 4, traitees: 2, applicables: 5,
    prochainOrdre: 2, prochainLibelle: 'Démarche 2',
    pointsAttention: [point(2, 'DEPASSE', -4, '2026-10-11'), point(3, 'APPROCHE', 9, '2026-10-24')],
  },
};

describe('Frise des démarches', () => {
  it('donne a chaque demarche la couleur de son delai, avec son etat en toutes lettres', () => {
    render(<FriseDemarches vue={vue} />);
    const attendu: [number, string, RegExp][] = [
      [1, 'accomplie', /Accomplie/],
      [2, 'rouge', /Dépassée.*dépassée de 4 jours/],
      [3, 'orange', /Échéance proche.*24\/10\/2026, dans 9 jours/],
      [4, 'vert', /Dans les temps.*échéance le 20\/12\/2026/],
      [5, 'sans', /Sans échéance/],
      [6, 'ecartee', /Écartée/],
    ];
    for (const [ordre, ton, texte] of attendu) {
      const etape = screen.getByTestId(`frise-${ordre}`);
      expect(etape).toHaveAttribute('data-ton', ton);
      expect(etape.textContent).toMatch(texte);
      expect(within(etape).getByText(`Démarche ${ordre}`, { exact: false })).toBeInTheDocument();
    }
    expect(screen.getByText(/1 dépassée, 1 à échéance proche/)).toBeInTheDocument();
  });

  it('CRITIQUE (J-3) est une echeance proche, pas une echeance depassee', () => {
    expect(etatDelai(ligne(7), point(7, 'CRITIQUE', 2, '2026-10-17')).ton).toBe('orange');
    expect(etatDelai(ligne(7), point(7, 'CRITIQUE', 0, '2026-10-15')).detail).toMatch(/aujourd’hui/);
  });

  it('reprend le seuil « proche » du serveur (15 jours) et l’annonce dans l’aide', () => {
    expect(JOURS_ECHEANCE_PROCHE).toBe(15);
    render(<FriseDemarches vue={vue} />);
    expect(screen.getByRole('button', { name: 'Comment lire la frise ?' })).toBeInTheDocument();
  });

  it('ne rend rien sans demarche', () => {
    const { container } = render(<FriseDemarches vue={{ ...vue, phases: [] }} />);
    expect(container).toBeEmptyDOMElement();
  });
});
