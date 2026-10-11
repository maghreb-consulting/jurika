import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

/**
 * Lot L4b (E4 à E6, RG-DEM-01, 06, 08) : hors création, l'employé choisit les décisions du
 * ticket, qui apportent ses démarches du référentiel du cabinet ; une démarche « à
 * apprécier » dit pourquoi ; un délai sans point de départ le demande sur la ligne.
 */
const get = vi.fn();
const put = vi.fn();
vi.mock('../../../lib/api', () => ({
  api: { get: (...a: unknown[]) => get(...a), put: (...a: unknown[]) => put(...a), post: vi.fn() },
  extractError: (e: unknown) => ({ message: String(e) }),
}));

import { DecisionsTicketPanel } from '../DecisionsTicketPanel';
import { DemarchesPanel } from '../DemarchesPanel';
import type { LigneDemarche, VueDemarches } from '../../../types/demarche';

const M = 'MATRICE_DECISIONS_MODIFICATIONS_SARL';

function decisions(choisies: { matrice: string; code: string }[], manques: string[]) {
  return {
    corpus: true, versionReferentiel: 'CORPUS_2026-10-03', forme: 'SARL', matrices: [M],
    proposees: [
      { matrice: M, numero: 1, section: 'A', code: 'approbation_comptes', libelle: 'Approbation des comptes annuels',
        typeDecision: 'AGO', matriceRenvoi: 'MATRICE_COMPTES_RESULTAT_SARL', numeroRenvoi: 1,
        choisie: choisies.length > 0 },
      { matrice: M, numero: 30, section: 'C', code: 'transfert_siege', libelle: 'Transfert du siège social',
        typeDecision: 'AGE', matriceRenvoi: null, numeroRenvoi: null, choisie: false },
    ],
    choisies, manques,
  };
}

function ligne(partial: Partial<LigneDemarche>): LigneDemarche {
  return {
    demarcheId: 'd-' + (partial.ordre ?? 1), ordre: partial.ordre ?? 1, phaseCode: 'S3',
    phaseLibelle: 'Déroulement de la démarche', libelle: 'Dépôt des états de synthèse — dépôt',
    statutTicket: 'DEROULEMENT_DEMARCHE', acteur: null, organisme: 'Greffe du tribunal de commerce',
    obligatoire: false, conditionApplication: '$CAC_EXISTE = « oui »', piecesEntrantes: null, documentProduit: null,
    justificatifsTexte: 'Récépissé de dépôt', modeleJurika: null,
    delai: '30 jours — texte de la matrice : dans les trente jours', coutIndicatif: null, variablesAlimentees: null,
    delaiCalculable: false, justificatifsAttendus: [], etat: 'A_FAIRE', motif: null, cocheAt: null,
    documentsDeposes: [], actionnableMaintenant: true, horsSequence: false, formaliteCode: 'R3-12',
    formaliteVolet: 'DEPOT', depotOrdre: null, deposeLe: null, journal: [], delaiDepartManquant: null,
    decision: 'approbation_comptes', applicabilite: 'A_APPRECIER',
    raisonApplicabilite: 'condition : $CAC_EXISTE non renseignée (à apprécier)',
    delaiDepart: null, echeance: null, delaiAttendu: 'DEPART', delaiDonnee: null,
    ...partial,
  };
}

function vue(l: LigneDemarche): VueDemarches {
  return {
    workflowType: 'MODIFICATION', statutCourant: 'DEROULEMENT_DEMARCHE',
    phases: [{ code: 'S3', libelle: 'Déroulement de la démarche', traitees: 0, total: 1, demarches: [l] }],
    avancement: { statutCourant: 'DEROULEMENT_DEMARCHE', position: 3, totalStatuts: 4, traitees: 0, applicables: 1,
      prochainOrdre: l.ordre, prochainLibelle: l.libelle, pointsAttention: [] },
  } as VueDemarches;
}

beforeEach(() => {
  get.mockReset();
  put.mockReset();
});

describe('Décisions du ticket', () => {
  it('nomme ce qui manque, puis enregistre les décisions choisies', async () => {
    get.mockResolvedValueOnce({ data: decisions([], ['Aucune décision choisie : choisissez la ou les décisions du ticket pour obtenir ses démarches.']) });
    put.mockResolvedValueOnce({ data: decisions([{ matrice: M, code: 'approbation_comptes' }], []) });
    const recharger = vi.fn();
    render(<DecisionsTicketPanel ticketId="t1" canAct onChanged={recharger} />);
    expect(await screen.findByTestId('decisions-manques')).toHaveTextContent('Aucune décision choisie');
    expect(screen.getByText(/Renvoi : la matrice Comptes, résultat et distributions — SARL fait foi/)).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText(/Approbation des comptes annuels/));
    fireEvent.click(screen.getByRole('button', { name: /Enregistrer les décisions/ }));
    await waitFor(() => expect(put).toHaveBeenCalledWith('/tickets/t1/decisions',
      { decisions: [{ matrice: M, code: 'approbation_comptes' }] }));
    // La reponse du serveur met l'etat a jour, puis le rendu suit : on attend l'etat final
    // (CI L4b #35 : la verification immediate tombait parfois avant le rendu).
    await waitFor(() => expect(recharger).toHaveBeenCalled());
    await waitFor(() => expect(screen.queryByTestId('decisions-manques')).toBeNull());
  });

  it('n’offre aucun choix en consultation', async () => {
    get.mockResolvedValueOnce({ data: decisions([], []) });
    render(<DecisionsTicketPanel ticketId="t1" canAct={false} />);
    await screen.findByTestId('decisions-ticket');
    expect(screen.queryByRole('button', { name: /Enregistrer les décisions/ })).toBeNull();
    expect(screen.getByLabelText(/Transfert du siège social/)).toBeDisabled();
  });
});

describe('Démarches du corpus', () => {
  it('dit pourquoi la démarche est à apprécier et demande le point de départ du délai', async () => {
    const l = ligne({ ordre: 30012 });
    get.mockResolvedValueOnce({ data: vue(l) });
    put.mockResolvedValueOnce({ data: vue(ligne({ ordre: 30012, delaiAttendu: null, delaiDepart: '2026-10-01', echeance: '2026-10-31' })) });
    render(<DemarchesPanel ticketId="t1" dossierId="d1" canAct />);
    expect(await screen.findByTestId('raison-30012')).toHaveTextContent('$CAC_EXISTE non renseignée');
    expect(screen.getByText('À apprécier')).toBeInTheDocument();
    const delai = screen.getByTestId('delai-30012');
    expect(delai).toHaveTextContent('Aucune échéance tant que le point de départ n’est pas connu');
    fireEvent.change(within(delai).getByLabelText('Point de départ du délai'), { target: { value: '2026-10-01' } });
    fireEvent.click(within(delai).getByRole('button', { name: 'Enregistrer la date' }));
    await waitFor(() => expect(put).toHaveBeenCalledWith('/tickets/t1/demarches/30012/delai', { depart: '2026-10-01' }));
    expect(await screen.findByText(/31\/10\/2026/)).toBeInTheDocument();
    expect(screen.getByTestId('delai-30012')).toHaveTextContent('point de départ le 01/10/2026');
  });
});
