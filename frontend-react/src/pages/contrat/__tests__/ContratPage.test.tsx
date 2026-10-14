import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../../../components/ui/Toast';
import type { ApercuContrat, EtatContrat } from '../../../types/contrat';

/** Lot L7 (E6, RG-CTR-01) : acceptation en ligne du contrat par le superviseur. */

const etat = vi.fn();
const historique = vi.fn();
const versions = vi.fn();
const apercu = vi.fn();
const accepter = vi.fn();
vi.mock('../../../services/contrat.service', async (orig) => {
  const vrai = await orig<typeof import('../../../services/contrat.service')>();
  return {
    ...vrai,
    contratService: {
      etat: () => etat(),
      historique: () => historique(),
      versions: () => versions(),
      apercu: (s: unknown) => apercu(s),
      accepter: (s: unknown, e: string, c: boolean) => accepter(s, e, c),
    },
  };
});

const auth = { user: { userId: 'u1', role: 'SUPERVISEUR' } };
vi.mock('../../../store/authStore', () => ({
  useAuthStore: <T,>(selector: (s: typeof auth) => T) => selector(auth),
}));

import { ContratPage } from '../ContratPage';

const CONDITIONS = {
  id: 'cp1', reference: 'CP-2026-000001', forfait: 'PROFESSIONNEL', forfaitLibelle: 'Professionnel',
  rythmePaiement: 'ANNUEL', prixHt: 7500, dateDebut: '2026-11-01', dateFin: '2027-11-01',
  echeanceCourante: '2027-11-01', modePaiement: null, statut: 'EN_ATTENTE', origine: 'SUPER_ADMIN',
  creeLe: '2026-10-14T09:00:00Z',
} as const;

const EN_ATTENTE: EtatContrat = {
  statut: 'EN_ATTENTE', acceptationRequise: true, objetAAccepter: 'CONDITIONS_PARTICULIERES',
  enAttente: { ...CONDITIONS }, courant: null, derniereAcceptation: null, resiliation: null,
  versionEnVigueur: null, versionAVenir: null, joursAvantEcheance: null, avertissementEcheance: false, reprise: null,
};

const APERCU: ApercuContrat = {
  objet: 'CONDITIONS_PARTICULIERES', conditions: { ...CONDITIONS }, versionCode: '2026-10-12',
  texte: '# A. Conditions particulières\n\n| Élément | Valeur |\n|---|---|\n| Forfait | Professionnel |\n',
  empreinte: 'a'.repeat(64), manquants: ['ICE de l’Éditeur'],
};

function afficher() {
  render(
    <MemoryRouter>
      <ToastProvider>
        <ContratPage />
      </ToastProvider>
    </MemoryRouter>,
  );
}

describe('ContratPage', () => {
  beforeEach(() => {
    [etat, historique, versions, apercu, accepter].forEach((f) => f.mockReset());
    etat.mockResolvedValue(EN_ATTENTE);
    historique.mockResolvedValue({ conditions: [], acceptations: [], resiliations: [], renouvellements: [], avis: [] });
    versions.mockResolvedValue([]);
    apercu.mockResolvedValue(APERCU);
    accepter.mockResolvedValue({});
  });

  it('dit ce qu’il faut faire et exige la civilité et la qualité avant d’afficher le contrat', async () => {
    afficher();
    expect(await screen.findByText('Contrat d’abonnement à accepter')).toBeInTheDocument();
    expect(screen.getByRole('note', { name: 'Votre contrat d’abonnement' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Afficher le contrat à accepter' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Renseignez votre civilité et votre qualité');
    expect(apercu).not.toHaveBeenCalled();
  });

  it('case jamais pré-cochée, acceptation après confirmation avec l’empreinte du texte lu', async () => {
    afficher();
    await screen.findByText('Contrat d’abonnement à accepter');
    fireEvent.change(screen.getByLabelText('Civilité *'), { target: { value: 'Mme' } });
    fireEvent.change(screen.getByLabelText('Qualité au sein du cabinet *'), { target: { value: 'gérante' } });
    fireEvent.click(screen.getByRole('button', { name: 'Afficher le contrat à accepter' }));
    const caseAcceptation = await screen.findByRole('checkbox', { name: 'J’ai lu et j’accepte le contrat d’abonnement JURIKA' });
    expect(caseAcceptation).not.toBeChecked();
    expect(screen.getByText('ICE de l’Éditeur')).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Professionnel' })).toBeInTheDocument();
    const bouton = screen.getByRole('button', { name: 'Accepter le contrat' });
    expect(bouton).toBeDisabled();
    fireEvent.click(caseAcceptation);
    fireEvent.click(bouton);
    fireEvent.click(await screen.findByRole('button', { name: 'Je confirme mon acceptation' }));
    await waitFor(() => expect(accepter).toHaveBeenCalledTimes(1));
    expect(accepter).toHaveBeenCalledWith(
      expect.objectContaining({ civilite: 'Mme', qualite: 'gérante' }), 'a'.repeat(64), true);
  });

  it('modifier une mention après lecture oblige à relire le contrat', async () => {
    afficher();
    await screen.findByText('Contrat d’abonnement à accepter');
    fireEvent.change(screen.getByLabelText('Civilité *'), { target: { value: 'M.' } });
    fireEvent.change(screen.getByLabelText('Qualité au sein du cabinet *'), { target: { value: 'gérant' } });
    fireEvent.click(screen.getByRole('button', { name: 'Afficher le contrat à accepter' }));
    await screen.findByRole('checkbox');
    fireEvent.change(screen.getByLabelText('Qualité au sein du cabinet *'), { target: { value: 'associé' } });
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Afficher le contrat à accepter' })).toBeInTheDocument();
  });
});
