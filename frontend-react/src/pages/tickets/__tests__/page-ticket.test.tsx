import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

/**
 * Lot L4d (E9, RG-TKT-06) : la page du ticket, commune à tous les parcours. Les obstacles au
 * passage s'affichent avant la demande ; un refus du serveur s'affiche avec sa raison.
 */
const { tickets, role } = vi.hoisted(() => ({
  tickets: {
    get: vi.fn(),
    acces: vi.fn(),
    passage: vi.fn(),
    transition: vi.fn(),
  },
  role: { value: 'EMPLOYE' },
}));
vi.mock('../../../services/ticket.service', () => ({ ticketService: tickets }));
vi.mock('../../../services/workflow.service', () => ({
  workflowService: { variables: vi.fn(async () => [
    { variable: 'DENOMINATION', boucle: null, rang: null, valeur: 'PARACOSME', origine: 'SAISIE', saisiePar: null, saisieLe: null, occasion: null },
    { variable: 'SIEGE_TITRE_OCCUPATION', boucle: null, rang: null, valeur: 'Bail commercial', origine: 'SAISIE', saisiePar: null, saisieLe: null, occasion: null },
  ]) },
}));
vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: { getJuridique: vi.fn(async () => ({
    raisonSociale: 'PARACOSME',
    dossiersParTicket: [{ ticketId: 't1', libelle: 'Modification', reference: 'T-1', type: 'MODIFICATION', statut: 'GENERATION_DOCUMENTS',
      ouvertLe: '2026-10-01', totalDocuments: 1,
      groupes: [{ code: 'ACTES', libelle: 'Actes', documents: [{ id: 'd1', documentType: 'STATUTS', title: 'Statuts mis à jour', version: 2 }] }] }],
  })) },
}));
vi.mock('../../../store/authStore', () => ({
  useCurrentUser: () => ({ role: role.value }),
  // L6b : la structure commune <Page> lit le role pour le fil d'Ariane.
  useAuthStore: (sel: (s: { user: { role: string } }) => unknown) => sel({ user: { role: role.value } }),
}));
const { stub, demarches } = vi.hoisted(() => ({
  stub: () => null,
  // Lot L6b (E14) : le panneau des demarches simule garde ses props et compte ses montages.
  demarches: { onModifie: undefined as undefined | (() => void), montages: 0 },
}));
vi.mock('../DecisionsTicketPanel', () => ({ DecisionsTicketPanel: stub }));
vi.mock('../DemarchesPanel', async () => {
  const { useEffect } = await import('react');
  return {
    DemarchesPanel: (p: { onModifie?: () => void }) => {
      demarches.onModifie = p.onModifie;
      useEffect(() => { demarches.montages += 1; }, []);
      return null;
    },
  };
});
vi.mock('../NoteTicketPanel', () => ({ NoteTicketPanel: stub }));
vi.mock('../DeboursPanel', () => ({ DeboursPanel: stub }));
vi.mock('../RecapitulatifPanel', () => ({ RecapitulatifPanel: stub }));

import { TicketPage } from '../TicketPage';

function monter() {
  render(
    <MemoryRouter initialEntries={['/tickets/t1']}>
      <Routes><Route path="/tickets/:ticketId" element={<TicketPage />} /></Routes>
    </MemoryRouter>,
  );
}

describe('Page du ticket (tous parcours)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    tickets.get.mockResolvedValue({ id: 't1', titre: 'Transfert du siège', reference: 'T-2026-00042', type: 'MODIFICATION',
      statut: 'GENERATION_DOCUMENTS', dossierId: 'dos1' });
    tickets.acces.mockResolvedValue({ enCharge: true, peutAgir: true, raison: null });
  });

  it('nomme les obstacles au passage avant toute demande, sans offrir le passage', async () => {
    role.value = 'EMPLOYE';
    tickets.passage.mockResolvedValue({ statut: 'GENERATION_DOCUMENTS', suivant: 'DEROULEMENT_DEMARCHE',
      obstacles: ['Démarche obligatoire non cochée : Génération du PV de transfert'] });
    monter();
    expect(await screen.findByTestId('obstacles')).toHaveTextContent('Génération du PV de transfert');
    expect(screen.getByRole('button', { name: /Passer à/ })).toBeDisabled();
    // Lot L6 (E9) : l'aide du statut dit quoi faire, renvoie aux obstacles, annonce la suite.
    const aide = screen.getByTestId('aide-statut-ticket');
    expect(aide).toHaveTextContent('Étape 2 sur 4');
    expect(aide).toHaveTextContent('Ouvrez le parcours');
    expect(aide).toHaveTextContent('un point à régler');
    expect(aide).toHaveTextContent('Déroulement de la démarche');
    // Données saisies avec leur provenance, documents lisibles.
    expect(await screen.findByText('PARACOSME', { selector: 'td' })).toBeInTheDocument();
    expect(await screen.findByTestId('documents-ticket')).toHaveTextContent('— PARACOSME — version 2');
    // L6b (backlog L6) : une donnee connue du dictionnaire s'affiche sous son libelle, pas sous son nom technique ;
    // une donnee absente du dictionnaire garde son nom technique (aucun libelle invente).
    const ligneSiege = screen.getByText('Bail commercial', { selector: 'td' }).closest('tr') as HTMLElement;
    expect(ligneSiege).toHaveTextContent('Siège titre occupation');
    expect(ligneSiege).not.toHaveTextContent('SIEGE_TITRE_OCCUPATION');
    expect(screen.getByText('PARACOSME', { selector: 'td' }).closest('tr')).toHaveTextContent('DENOMINATION');
    // Structure commune : le titre du ticket est le titre de la page.
    expect(screen.getByRole('heading', { level: 1, name: 'Transfert du siège' })).toBeInTheDocument();
  });

  it('affiche le refus du serveur avec sa raison', async () => {
    role.value = 'EMPLOYE';
    tickets.passage.mockResolvedValue({ statut: 'GENERATION_DOCUMENTS', suivant: 'DEROULEMENT_DEMARCHE', obstacles: [] });
    tickets.transition.mockRejectedValue({ isAxiosError: true, response: { status: 409,
      data: { message: 'Passage au statut DEROULEMENT_DEMARCHE impossible : donnée attendue manquante : $DATE_ACTE' } } });
    monter();
    expect(await screen.findByTestId('passage-possible')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Passer à/ }));
    await waitFor(() => expect(screen.getByTestId('refus-transition')).toHaveTextContent('$DATE_ACTE'));
  });

  it('L6b E14 : un cochage de demarche relit les obstacles au passage, sans recharger la page', async () => {
    role.value = 'EMPLOYE';
    tickets.passage.mockResolvedValueOnce({ statut: 'CREATION_TICKET', suivant: 'GENERATION_DOCUMENTS',
      obstacles: ['D\u00e9marche 1 (obligatoire) non coch\u00e9e : Ouverture du ticket'] });
    monter();
    expect(await screen.findByTestId('obstacles')).toHaveTextContent('Ouverture du ticket');
    tickets.passage.mockResolvedValue({ statut: 'CREATION_TICKET', suivant: 'GENERATION_DOCUMENTS', obstacles: [] });
    // Le panneau des demarches signale le cochage.
    expect(demarches.onModifie, 'la page passe un rappel au panneau des demarches').toBeTypeOf('function');
    demarches.onModifie?.();
    expect(await screen.findByTestId('passage-possible')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Passer \u00e0/ })).toBeEnabled();
  });

  it('L6b E14 : apres un passage de statut, le panneau des demarches est recharge', async () => {
    role.value = 'EMPLOYE';
    tickets.passage.mockResolvedValue({ statut: 'CREATION_TICKET', suivant: 'GENERATION_DOCUMENTS', obstacles: [] });
    tickets.transition.mockResolvedValue({});
    monter();
    expect(await screen.findByTestId('passage-possible')).toBeInTheDocument();
    const avant = demarches.montages;
    fireEvent.click(screen.getByRole('button', { name: /Passer \u00e0/ }));
    await waitFor(() => expect(tickets.transition).toHaveBeenCalled());
    await waitFor(() => expect(demarches.montages, 'demarches du nouveau statut relues').toBeGreaterThan(avant));
  });

  it('le superviseur lit les obstacles sans pouvoir agir', async () => {
    role.value = 'SUPERVISEUR';
    tickets.acces.mockResolvedValue({ enCharge: false, peutAgir: false, raison: 'Consultation : seul l’employé responsable du dossier agit sur le ticket.' });
    tickets.passage.mockResolvedValue({ statut: 'GENERATION_DOCUMENTS', suivant: 'DEROULEMENT_DEMARCHE', obstacles: ['X'] });
    monter();
    expect(await screen.findByTestId('obstacles')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Passer à/ })).toBeNull();
    expect(screen.getByTestId('aide-statut-ticket')).toHaveTextContent('Seul l’employé responsable du dossier fait avancer ce ticket');
    expect(await screen.findByTestId('acces-raison')).toHaveTextContent('Consultation');
  });

  // Lot L6b (E15, parcours de bout en bout) : apres le passage de statut, les demarches du
  // nouveau statut restaient " d'un autre statut, pas cochables " ; apres un cochage, le
  // passage restait bloque sur la demarche pourtant cochee : il fallait recharger la page.
  it('recharge les démarches après un passage de statut', async () => {
    role.value = 'EMPLOYE';
    demarches.montages = 0;
    tickets.passage.mockResolvedValue({ statut: 'GENERATION_DOCUMENTS', suivant: 'DEROULEMENT_DEMARCHE', obstacles: [] });
    tickets.transition.mockResolvedValue({});
    monter();
    expect(await screen.findByTestId('passage-possible')).toBeInTheDocument();
    const avant = demarches.montages;
    fireEvent.click(screen.getByRole('button', { name: /Passer à/ }));
    await waitFor(() => expect(tickets.transition).toHaveBeenCalled());
    await waitFor(() => expect(demarches.montages).toBeGreaterThan(avant));
  });

  it('relit le passage de statut après un cochage de démarche', async () => {
    role.value = 'EMPLOYE';
    tickets.passage.mockResolvedValue({ statut: 'GENERATION_DOCUMENTS', suivant: 'DEROULEMENT_DEMARCHE',
      obstacles: ['Démarche 110011 (obligatoire) non cochée'] });
    monter();
    expect(await screen.findByTestId('obstacles')).toBeInTheDocument();
    tickets.passage.mockResolvedValue({ statut: 'GENERATION_DOCUMENTS', suivant: 'DEROULEMENT_DEMARCHE', obstacles: [] });
    const lectures = tickets.passage.mock.calls.length;
    expect(demarches.onModifie).toBeTypeOf('function');
    demarches.onModifie?.();
    await waitFor(() => expect(tickets.passage.mock.calls.length).toBeGreaterThan(lectures));
    expect(await screen.findByTestId('passage-possible')).toBeInTheDocument();
  });
});
