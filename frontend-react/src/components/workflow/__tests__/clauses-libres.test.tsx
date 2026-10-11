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

  // Lot L4d (E10), decision du directeur du 11 octobre 2026 : l'emplacement est choisi.
  it('statuts : la clause se place apres l article choisi, sans quoi elle n est pas enregistree', async () => {
    ai.emplacementClausesLibres.mockResolvedValue({ possible: true, nature: 'ARTICLE', libelle: 'Après l’article choisi',
      articles: [{ numero: 1, titre: 'FORME' }, { numero: 2, titre: 'DÉNOMINATION SOCIALE' }] });
    wf.clausesLibres.mockResolvedValue([]);
    wf.remplacerClausesLibres.mockImplementation(async (_t: string, c: unknown[]) => c);
    render(<ClausesLibresPanel ticketId="t-1" workflowCode="CREATION_SARL" templateCode="STATUTS_SARL" />);
    fireEvent.click(await screen.findByRole('button', { name: 'Ajouter une clause' }));
    expect(screen.queryByLabelText('Résultat du vote')).toBeNull();
    fireEvent.change(screen.getByLabelText('Titre de la clause'), { target: { value: 'Non-concurrence' } });
    fireEvent.change(screen.getByLabelText('Texte de la clause'), { target: { value: 'Les associés s’interdisent…' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer les clauses' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Choisissez l’article après lequel placer la clause n° 1');
    expect(wf.remplacerClausesLibres).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText('Emplacement de la clause 1'), { target: { value: 'APRES_ARTICLE:2' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer les clauses' }));
    await waitFor(() => expect(wf.remplacerClausesLibres).toHaveBeenCalled());
    expect(wf.remplacerClausesLibres.mock.calls[0][1]).toEqual([
      expect.objectContaining({ document: 'STATUTS_SARL', emplacement: 'APRES_ARTICLE:2', titre: 'Non-concurrence' }),
    ]);
  });

  it('proces-verbal : la clause peut se placer avant une resolution du modele', async () => {
    ai.emplacementClausesLibres.mockResolvedValue({ possible: true, nature: 'RESOLUTION', emplacement: 'A_LA_SUITE_DES_RESOLUTIONS' });
    wf.clausesLibres.mockResolvedValue([]);
    wf.remplacerClausesLibres.mockImplementation(async (_t: string, c: unknown[]) => c);
    render(<ClausesLibresPanel ticketId="t-1" workflowCode="PV_AGO" templateCode="PV_APPROBATION_COMPTES_SARL" />);
    fireEvent.click(await screen.findByRole('button', { name: 'Ajouter une clause' }));
    fireEvent.change(screen.getByLabelText('Emplacement de la clause 1'), { target: { value: 'AVANT' } });
    fireEvent.change(screen.getByLabelText('Rang de la résolution 1'), { target: { value: '2' } });
    fireEvent.change(screen.getByLabelText('Titre de la clause'), { target: { value: 'Pouvoirs' } });
    fireEvent.change(screen.getByLabelText('Texte de la clause'), { target: { value: 'Tous pouvoirs.' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer les clauses' }));
    await waitFor(() => expect(wf.remplacerClausesLibres).toHaveBeenCalled());
    expect(wf.remplacerClausesLibres.mock.calls[0][1]).toEqual([
      expect.objectContaining({ emplacement: 'AVANT_RESOLUTION:2' }),
    ]);
  });

  it('statuts sans articles numerotes : la raison est dite', async () => {
    ai.emplacementClausesLibres.mockResolvedValue({ possible: false, motif: 'Ce modèle de statuts n’a pas d’articles numérotés.' });
    wf.clausesLibres.mockResolvedValue([]);
    render(<ClausesLibresPanel ticketId="t-1" workflowCode="CREATION_SARL" templateCode="STATUTS_SARL" />);
    expect(await screen.findByText(/pas d’articles numérotés/)).toBeInTheDocument();
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
