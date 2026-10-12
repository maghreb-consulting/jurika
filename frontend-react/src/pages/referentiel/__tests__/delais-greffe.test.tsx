import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

const { svc, role } = vi.hoisted(() => ({
  svc: { lister: vi.fn(), ajuster: vi.fn(async () => undefined), retirer: vi.fn(async () => undefined) },
  role: { value: 'SUPERVISEUR' },
}));
vi.mock('../../../services/delaisGreffe.service', () => ({ delaisGreffeService: svc }));
vi.mock('../../../store/authStore', () => ({ useCurrentUser: () => ({ role: role.value }) }));

import { DelaisGreffePage } from '../DelaisGreffePage';

/** Lot L4d (E7) : délais de dépôt au greffe, article cité, « à confirmer », ajustement du cabinet. */
const natures = [
  {
    matrice: 'MATRICE_DECISIONS_MODIFICATIONS_SARL', decisionCode: 'transfert_siege', decisionLibelle: 'Transfert du siège social',
    delaiTexte: '1 mois — Inscription modificative dans le mois (art. 75 du code de commerce)', delaiValeur: 1, delaiUnite: 'MOIS',
    article: 'art. 75 du code de commerce (loi n° 15-95)', statut: 'CITE_PAR_LE_CORPUS', ajustement: null,
  },
  {
    matrice: 'MATRICE_DECISIONS_MODIFICATIONS_SARL', decisionCode: 'nomination_gerant', decisionLibelle: 'Nomination d’un gérant',
    delaiTexte: '1 mois — Inscription RC dans le mois', delaiValeur: 1, delaiUnite: 'MOIS',
    article: null, statut: 'A_CONFIRMER', ajustement: null,
  },
];

describe('Délais de dépôt au greffe', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    svc.lister.mockResolvedValue(natures);
  });

  it('affiche l’article cité et signale le délai à confirmer', async () => {
    role.value = 'EMPLOYE';
    render(<DelaisGreffePage />);
    const cite = await screen.findByTestId('delai-transfert_siege');
    expect(cite).toHaveTextContent('art. 75 du code de commerce (loi n° 15-95)');
    expect(cite).toHaveTextContent('cité par le corpus');
    expect(screen.getByTestId('delai-nomination_gerant')).toHaveTextContent('à confirmer');
    expect(screen.getByTestId('delais-resume')).toHaveTextContent('1 délai à confirmer');
    // L'employé lit seulement.
    expect(screen.queryByRole('button', { name: 'Ajuster' })).toBeNull();
  });

  it('le superviseur ajuste un délai pour le cabinet', async () => {
    role.value = 'SUPERVISEUR';
    render(<DelaisGreffePage />);
    await screen.findByTestId('delai-nomination_gerant');
    fireEvent.click(screen.getAllByRole('button', { name: 'Ajuster' })[1]);
    fireEvent.change(screen.getByLabelText('Délai'), { target: { value: '30' } });
    fireEvent.change(screen.getByLabelText('Unité'), { target: { value: 'JOURS' } });
    fireEvent.change(screen.getByLabelText('Article'), { target: { value: 'art. 75 du code de commerce' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));
    await waitFor(() => expect(svc.ajuster).toHaveBeenCalledWith('MATRICE_DECISIONS_MODIFICATIONS_SARL', 'nomination_gerant',
      { delaiValeur: 30, delaiUnite: 'JOURS', article: 'art. 75 du code de commerce', note: undefined }));
  });

  it('refuse un délai vide, avec un message', async () => {
    role.value = 'SUPERVISEUR';
    render(<DelaisGreffePage />);
    await screen.findByTestId('delai-nomination_gerant');
    fireEvent.click(screen.getAllByRole('button', { name: 'Ajuster' })[0]);
    fireEvent.change(screen.getByLabelText('Délai'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Indiquez le délai ajusté');
    expect(svc.ajuster).not.toHaveBeenCalled();
  });
});
