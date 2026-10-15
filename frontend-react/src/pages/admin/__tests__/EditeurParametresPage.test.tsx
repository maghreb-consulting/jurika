import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ToastProvider } from '../../../components/ui/Toast';

/** Lot L7 (E6) : parametres de l'editeur : ce qui manque, ou c'est utilise, rien d'invente. */

const editeur = vi.fn();
const modifier = vi.fn();
const historique = vi.fn();
vi.mock('../../../services/contrat.service', () => ({
  contratService: {
    editeur: () => editeur(),
    modifierEditeur: (v: Record<string, string | null>, t: number) => modifier(v, t),
    historiqueEditeur: () => historique(),
  },
}));

import { EditeurParametresPage } from '../EditeurParametresPage';

const VIDE = {
  valeurs: {},
  tauxTvaPourcent: 20,
  manquants: [
    { champ: 'ice', libelle: 'ICE', usages: ['FACTURES', 'CONTRAT'] },
    { champ: 'taxeProfessionnelle', libelle: 'Taxe professionnelle (TP)', usages: ['FACTURES'] },
  ],
  modifieLe: null,
  modifiePar: null,
};

describe('EditeurParametresPage', () => {
  beforeEach(() => {
    editeur.mockReset().mockResolvedValue(VIDE);
    modifier.mockReset().mockResolvedValue({ ...VIDE, manquants: [] });
    historique.mockReset().mockResolvedValue([]);
  });

  it('liste les mentions manquantes et où elles sont imprimées, champs vides', async () => {
    render(<ToastProvider><EditeurParametresPage /></ToastProvider>);
    expect(await screen.findByText(/ICE — utilisée sur : factures et contrat d’abonnement/)).toBeInTheDocument();
    expect(screen.getByText(/Taxe professionnelle \(TP\) — utilisée sur : factures/)).toBeInTheDocument();
    expect(screen.getByLabelText('Raison sociale')).toHaveValue('');
    expect(screen.getByText('Aucune modification enregistrée.')).toBeInTheDocument();
    expect(screen.getByRole('note', { name: 'Saisir l’identité de l’éditeur' })).toBeInTheDocument();
  });

  it('enregistre les champs saisis et envoie null pour un champ vide', async () => {
    render(<ToastProvider><EditeurParametresPage /></ToastProvider>);
    await screen.findByLabelText('Raison sociale');
    fireEvent.change(screen.getByLabelText('Raison sociale'), { target: { value: ' Maghreb Consulting ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));
    await waitFor(() => expect(modifier).toHaveBeenCalledTimes(1));
    const [valeurs, taux] = modifier.mock.calls[0];
    expect(valeurs.raisonSociale).toBe('Maghreb Consulting');
    expect(valeurs.ice).toBeNull();
    expect(taux).toBe(20);
  });
});
