import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { DossierFiscalTab } from '../DossierFiscalTab';
import { ToastProvider } from '../../../components/ui/Toast';
import { dataroomService } from '../../../services/dataroom.service';
import type {
  DossierComptableView,
  DossierFiscalDetailedView,
} from '../../../types/dataroom';

// 2026-06-24 — Réécriture : l'ancien test ciblait le placeholder Sprint 7
// (mock `getFiscal`, textes « Module Sprint 8 a venir ») alors que le composant
// est passé à la version Sprint 8 (`getFiscalDetail`, grille 7 CGI, échéances).
// D'où l'« Erreur réseau » pré-existante. On teste ici le composant réel + la
// nouvelle règle de conformité fiscale au comptable (RG-DF03).

vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: {
    getFiscalDetail: vi.fn(),
    listFiscalDocuments: vi.fn(),
    listEcheances: vi.fn(),
    getComptable: vi.fn(),
    openExercice: vi.fn(),
  },
}));

const DETAIL: DossierFiscalDetailedView = {
  dossierId: 'd1',
  exerciceCourant: 'ex-2025',
  exercices: [
    {
      id: 'ex-2025',
      annee: 2025,
      dateDebut: '2025-01-01',
      dateFin: '2025-12-31',
      statut: 'OUVERT',
      dateOuverture: '2025-01-01T00:00:00Z',
      dateCloture: null,
    },
    {
      id: 'ex-2024',
      annee: 2024,
      dateDebut: '2024-01-01',
      dateFin: '2024-12-31',
      statut: 'CLOTURE',
      dateOuverture: '2024-01-01T00:00:00Z',
      dateCloture: '2025-03-31T00:00:00Z',
    },
  ],
  compteurs: [],
  categoriesCgi: ['TVA', 'IS', 'IR', 'TP_TSC', 'RAS', 'ATTESTATIONS', 'CONTENTIEUX'],
};

const COMPTABLE: DossierComptableView = {
  dossierId: 'd1',
  annees: [2025, 2024],
  anneeCourante: 2025,
  totauxParCategorie: [],
};

beforeEach(() => {
  vi.mocked(dataroomService.getFiscalDetail).mockResolvedValue(DETAIL);
  vi.mocked(dataroomService.listFiscalDocuments).mockResolvedValue([]);
  vi.mocked(dataroomService.listEcheances).mockResolvedValue([]);
  vi.mocked(dataroomService.getComptable).mockResolvedValue(COMPTABLE);
  vi.mocked(dataroomService.openExercice).mockResolvedValue(DETAIL.exercices[0]);
});

afterEach(() => vi.restoreAllMocks());

describe('DossierFiscalTab', () => {
  it('charge le detail et affiche le selecteur + grille 7 CGI', async () => {
    render(
      <ToastProvider>
        <DossierFiscalTab dossierId="d1" role="EMPLOYE" />
      </ToastProvider>,
    );

    await waitFor(() =>
      expect(dataroomService.getFiscalDetail).toHaveBeenCalledWith('d1', undefined),
    );
    expect(await screen.findByText(/7 categories CGI Maroc/i)).toBeInTheDocument();
    expect(screen.getByText('Exercice 2024 (CLOTURE)')).toBeInTheDocument();
  });

  it('changer d\'exercice recharge le detail avec son id', async () => {
    const user = userEvent.setup();
    render(
      <ToastProvider>
        <DossierFiscalTab dossierId="d1" role="EMPLOYE" />
      </ToastProvider>,
    );
    await screen.findByText(/7 categories CGI Maroc/i);

    await user.selectOptions(screen.getByRole('combobox'), 'ex-2024');

    await waitFor(() =>
      expect(dataroomService.getFiscalDetail).toHaveBeenCalledWith('d1', 'ex-2024'),
    );
  });

  // Flux « nouvel exercice » via les dialogs stylés (PromptDialog/ConfirmDialog)
  // qui remplacent window.prompt/confirm.
  async function ouvrirFluxNouvelExercice(user: ReturnType<typeof userEvent.setup>, annee: string) {
    await user.click(screen.getByText('Nouvel exercice'));
    const anneeField = await screen.findByLabelText(/Annee du nouvel exercice/i);
    await user.clear(anneeField);
    await user.type(anneeField, annee);
    await user.click(screen.getByRole('button', { name: 'Continuer' }));
  }

  it('RG-DF03 : annee tenue en compta -> ouverture conforme (autoCreateComptable=false)', async () => {
    const user = userEvent.setup();
    render(
      <ToastProvider>
        <DossierFiscalTab dossierId="d1" role="EMPLOYE" />
      </ToastProvider>,
    );
    await screen.findByText(/7 categories CGI Maroc/i);

    await ouvrirFluxNouvelExercice(user, '2025');

    await waitFor(() => expect(dataroomService.getComptable).toHaveBeenCalledWith('d1'));
    // Année présente en compta -> pas de confirmation « non tenue », régime direct.
    expect(screen.queryByText(/non tenue en comptabilite/i)).toBeNull();
    await user.click(await screen.findByRole('button', { name: "Ouvrir l'exercice" }));
    await waitFor(() =>
      expect(dataroomService.openExercice).toHaveBeenCalledWith('d1', {
        annee: 2025,
        regimeTvaMensuel: true,
        autoCreateComptable: false,
      }),
    );
  });

  it('RG-DF03 : annee absente en compta -> confirmation -> autoCreateComptable=true', async () => {
    const user = userEvent.setup();
    render(
      <ToastProvider>
        <DossierFiscalTab dossierId="d1" role="EMPLOYE" />
      </ToastProvider>,
    );
    await screen.findByText(/7 categories CGI Maroc/i);

    await ouvrirFluxNouvelExercice(user, '2030');

    // Année non tenue -> ConfirmDialog stylé, on confirme la création comptable.
    await user.click(await screen.findByRole('button', { name: 'Creer et continuer' }));
    await user.click(await screen.findByRole('button', { name: "Ouvrir l'exercice" }));
    await waitFor(() =>
      expect(dataroomService.openExercice).toHaveBeenCalledWith('d1', {
        annee: 2030,
        regimeTvaMensuel: true,
        autoCreateComptable: true,
      }),
    );
  });

  it('RG-DF03 : annee absente en compta + refus -> aucune ouverture (pas de fiscal orphelin)', async () => {
    const user = userEvent.setup();
    render(
      <ToastProvider>
        <DossierFiscalTab dossierId="d1" role="EMPLOYE" />
      </ToastProvider>,
    );
    await screen.findByText(/7 categories CGI Maroc/i);

    await ouvrirFluxNouvelExercice(user, '2030');

    await waitFor(() => expect(dataroomService.getComptable).toHaveBeenCalledWith('d1'));
    // On annule la confirmation « non tenue » -> aucune ouverture.
    await user.click(await screen.findByRole('button', { name: 'Annuler' }));
    expect(dataroomService.openExercice).not.toHaveBeenCalled();
  });
});
