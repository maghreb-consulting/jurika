import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

/** Lot L3 (RG-GEN-05 a 07) : clauses libres d'un proces-verbal, a l'ecran. */

const ai = { emplacementClausesLibres: vi.fn() };
vi.mock('../../../services/workflowDocumentService', () => ({
  emplacementClausesLibres: (...a: unknown[]) => ai.emplacementClausesLibres(...a),
}));
const wf = { clausesLibres: vi.fn(), remplacerClausesLibres: vi.fn() };
vi.mock('../../../services/workflow.service', () => ({
  workflowService: new Proxy({}, { get: (_t, p: string) => (...a: unknown[]) => (wf as Record<string, (...x: unknown[]) => unknown>)[p](...a) }),
}));

import { ClausesLibresPanel } from '../ClausesLibresPanel';

const autreDocument = { document: 'PV_FERMETURE_SUCCURSALE_SARL', titre: 'Autre', texte: 'Autre texte' };

beforeEach(() => {
  window.localStorage.clear();
  Object.values(ai).forEach((f) => f.mockReset());
  Object.values(wf).forEach((f) => f.mockReset());
});

describe('Clauses libres', () => {
  it('ajoute une clause a l emplacement prevu, l enregistre au ticket sans toucher aux autres documents', async () => {
    ai.emplacementClausesLibres.mockResolvedValue({ possible: true, emplacement: 'A_LA_SUITE_DES_RESOLUTIONS', libelle: 'À la suite des résolutions' });
    wf.clausesLibres.mockResolvedValue([autreDocument]);
    wf.remplacerClausesLibres.mockImplementation(async (_t: string, c: unknown[]) =>
      (c as Record<string, unknown>[]).map((x) => ({ ...x, saisieLe: '2026-10-10T10:00:00Z' })));
    const onEnregistre = vi.fn();
    render(<ClausesLibresPanel ticketId="t-1" workflowCode="PV_AGO" templateCode="PV_APPROBATION_COMPTES_SARL" onEnregistre={onEnregistre} />);

    fireEvent.click(await screen.findByRole('button', { name: 'Ajouter une clause' }));
    expect(screen.getByLabelText('Texte de la clause')).toHaveAttribute('spellcheck', 'true');
    fireEvent.change(screen.getByLabelText('Titre de la clause'), { target: { value: 'Pouvoirs particuliers' } });
    fireEvent.change(screen.getByLabelText('Texte de la clause'), { target: { value: 'L’assemblée confère tous pouvoirs.' } });
    fireEvent.change(screen.getByLabelText('Résultat du vote'), { target: { value: 'adoptée' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer les clauses' }));

    await waitFor(() => expect(wf.remplacerClausesLibres).toHaveBeenCalled());
    const [, envoyees] = wf.remplacerClausesLibres.mock.calls[0];
    expect(envoyees).toEqual([
      autreDocument,
      expect.objectContaining({ document: 'PV_APPROBATION_COMPTES_SARL', titre: 'Pouvoirs particuliers', resultat: 'adoptée' }),
    ]);
    expect(await screen.findByRole('status')).toHaveTextContent('imprimées à chaque génération');
    expect(onEnregistre).toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Qu’est-ce qu’une clause libre ?' })).toBeInTheDocument();
  });

  it('statuts : l emplacement des clauses particulieres attend le cabinet', async () => {
    ai.emplacementClausesLibres.mockResolvedValue({ possible: false, motif: 'Les statuts n’ont pas encore d’emplacement pour les clauses particulières.' });
    wf.clausesLibres.mockResolvedValue([]);
    render(<ClausesLibresPanel ticketId="t-1" workflowCode="CREATION_SARL" templateCode="STATUTS_SARL" />);
    expect(await screen.findByText(/pas encore d’emplacement/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ajouter une clause' })).not.toBeInTheDocument();
  });

  it('un modele sans emplacement ne propose rien', async () => {
    ai.emplacementClausesLibres.mockResolvedValue({ possible: false, motif: 'Pas d’emplacement.' });
    wf.clausesLibres.mockResolvedValue([]);
    const { container } = render(<ClausesLibresPanel ticketId="t-1" workflowCode="MODIFICATION" templateCode="PV_MODIFICATION_SARL" />);
    await waitFor(() => expect(ai.emplacementClausesLibres).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });
});
