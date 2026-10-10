import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

/**
 * Ecrans du lot L1 (demande du 2026-10-10, suite) :
 *  1. reaffectation d'office d'un dossier quelconque par le superviseur ;
 *  2. acces du client : permissions, suspension, lien d'acces, historique ;
 *  3. droit de suppression accorde ou retire depuis la page Equipe.
 * Chaque ecran porte ses aides.
 */

const svc = {
  historiqueResponsables: vi.fn(),
  reaffecter: vi.fn(),
  updatePermissions: vi.fn(),
  toggleSuspension: vi.fn(),
  historiqueAccesClient: vi.fn(),
};
vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: new Proxy({}, {
    get: (_t, p: string) => (...a: unknown[]) => {
      const f = (svc as Record<string, (...x: unknown[]) => unknown>)[p];
      return f ? f(...a) : Promise.resolve([]);
    },
  }),
}));
const auth = {
  listWorkspaceUsers: vi.fn(),
  listDroitsSuppressionDataroom: vi.fn(),
  setDroitSuppressionDataroom: vi.fn(),
  setUserActive: vi.fn(),
};
vi.mock('../../../services/auth.service', () => ({
  authService: new Proxy({}, {
    get: (_t, p: string) => (...a: unknown[]) => (auth as Record<string, (...x: unknown[]) => unknown>)[p](...a),
  }),
}));
vi.mock('../../../services/billing.service', () => ({
  billingService: { getUsage: async () => ({ planLabel: 'Cabinet', maxUsers: 10, usersUnlimited: false }) },
}));

import { HistoriqueResponsablesPanel } from '../components/HistoriqueResponsablesPanel';
import { AccesClientPanel } from '../components/AccesClientPanel';
import { TeamPage } from '../../team/TeamPage';
import type { DataroomSettings } from '../../../types/dataroom';

const membres = [
  { userId: 'e1', firstName: 'Karim', lastName: 'Bennani', email: 'k@x.ma', role: 'EMPLOYE', status: 'ACTIVE', lastLoginAt: null },
  { userId: 'e2', firstName: 'Salma', lastName: 'Idrissi', email: 's@x.ma', role: 'EMPLOYE', status: 'ACTIVE', lastLoginAt: null },
  { userId: 'e3', firstName: 'Ancien', lastName: 'Compte', email: 'a@x.ma', role: 'EMPLOYE', status: 'INACTIVE', lastLoginAt: null },
  { userId: 's1', firstName: 'Nadia', lastName: 'Superviseure', email: 'n@x.ma', role: 'SUPERVISEUR', status: 'ACTIVE', lastLoginAt: null },
];

const ligne = (p: Record<string, unknown> = {}) => ({
  id: 'r1', dossierId: 'd1', raisonSociale: 'ACME SARL', ancienResponsableId: null, ancienResponsableNom: null,
  nouveauResponsableId: 'e1', nouveauResponsableNom: 'Karim Bennani', nature: 'RATTRAPAGE', auteurId: null,
  auteurNom: null, motif: null, createdAt: '2026-10-09T10:00:00Z', responsableActuelId: 'e1',
  responsableActuelNom: 'Karim Bennani', verifiePar: null, verifieParNom: null, verifieLe: null, ...p,
});

const reglages = (p: Partial<DataroomSettings> = {}): DataroomSettings => ({
  dossierId: 'd1', accessStatus: 'ACTIVE', permDownload: true, permPrint: false, permDepot: false,
  clientLinkToken: 'tok', accessCount: 1, lastAccessedAt: null, permConsultation: true, permDemandes: true, ...p,
});

beforeEach(() => {
  window.localStorage.clear();
  Object.values(svc).forEach((f) => f.mockReset());
  Object.values(auth).forEach((f) => f.mockReset());
  auth.listWorkspaceUsers.mockResolvedValue(membres);
});

describe('1. Reaffectation d office depuis l historique des responsables', () => {
  it('le superviseur choisit un employe actif, saisit le motif ; le changement apparait dans l historique', async () => {
    svc.historiqueResponsables.mockResolvedValueOnce([ligne()]).mockResolvedValueOnce([
      ligne(),
      ligne({ id: 'r2', nature: 'FORCEE', ancienResponsableId: 'e1', ancienResponsableNom: 'Karim Bennani',
        nouveauResponsableId: 'e2', nouveauResponsableNom: 'Salma Idrissi', auteurNom: 'Nadia Superviseure',
        motif: 'Absence prolongée', createdAt: '2026-10-10T12:00:00Z', responsableActuelId: 'e2',
        responsableActuelNom: 'Salma Idrissi' }),
    ]);
    svc.reaffecter.mockResolvedValue(undefined);
    render(<HistoriqueResponsablesPanel dossierId="d1" raisonSociale="ACME SARL" peutReaffecter />);
    expect(await screen.findByText('Karim Bennani', { selector: 'strong' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Réaffecter le dossier' }));
    const choix = screen.getByLabelText('Nouvel employé responsable');
    await waitFor(() => expect(within(choix).getAllByRole('option')).toHaveLength(2));
    // Ni le responsable actuel, ni un compte desactive, ni le superviseur.
    expect(within(choix).getAllByRole('option').map((o) => o.textContent)).toEqual(['Choisir un employé…', 'Salma Idrissi']);

    const valider = screen.getByRole('button', { name: 'Réaffecter le dossier' });
    fireEvent.change(choix, { target: { value: 'e2' } });
    expect(valider).toBeDisabled(); // motif obligatoire
    fireEvent.change(screen.getByLabelText('Motif (obligatoire)'), { target: { value: '  Absence prolongée ' } });
    fireEvent.click(valider);

    await waitFor(() => expect(svc.reaffecter).toHaveBeenCalledWith('d1', 'e2', 'Absence prolongée'));
    expect(await screen.findByText('Dossier réaffecté à Salma Idrissi.')).toBeInTheDocument();
    // L'historique est recharge : attendre la nouvelle ligne, en tete.
    await waitFor(() => expect(screen.getAllByRole('listitem')).toHaveLength(2));
    const items = screen.getAllByRole('listitem');
    expect(items[0]).toHaveTextContent('Réaffectation par le superviseur');
    expect(items[0]).toHaveTextContent('Motif : Absence prolongée');
    expect(screen.getByText('Salma Idrissi', { selector: 'strong' })).toBeInTheDocument();
    expect(screen.getByRole('note', { name: 'Changer de responsable' })).toHaveTextContent('choisissez un employé actif');
  });

  it('un employe ne voit pas le bouton de reaffectation', async () => {
    svc.historiqueResponsables.mockResolvedValue([ligne()]);
    render(<HistoriqueResponsablesPanel dossierId="d1" />);
    await screen.findAllByRole('listitem');
    expect(screen.queryByRole('button', { name: 'Réaffecter le dossier' })).not.toBeInTheDocument();
    expect(auth.listWorkspaceUsers).not.toHaveBeenCalled();
  });

  it('un refus du serveur est affiche', async () => {
    svc.historiqueResponsables.mockResolvedValue([ligne()]);
    svc.reaffecter.mockRejectedValue(new Error('Le nouveau responsable doit être un employé actif'));
    render(<HistoriqueResponsablesPanel dossierId="d1" peutReaffecter />);
    await screen.findAllByRole('listitem');
    fireEvent.click(screen.getByRole('button', { name: 'Réaffecter le dossier' }));
    await waitFor(() => expect(within(screen.getByLabelText('Nouvel employé responsable')).getAllByRole('option')).toHaveLength(2));
    fireEvent.change(screen.getByLabelText('Nouvel employé responsable'), { target: { value: 'e2' } });
    fireEvent.change(screen.getByLabelText('Motif (obligatoire)'), { target: { value: 'Départ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Réaffecter le dossier' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});

describe('2. Acces du client', () => {
  it('le responsable regle chaque permission et la suspension ; l historique suit', async () => {
    svc.historiqueAccesClient.mockResolvedValueOnce([]).mockResolvedValue([
      { id: 'h1', nature: 'PERMISSIONS', acteurId: 'e1', acteurNom: 'Karim Bennani',
        avant: { consultation: true, telechargement: true, impression: false, depot: false, demandes: true },
        apres: { consultation: true, telechargement: true, impression: false, depot: false, demandes: false },
        createdAt: '2026-10-10T12:00:00Z' },
    ]);
    svc.updatePermissions.mockResolvedValue(reglages({ permDemandes: false }));
    const onSettings = vi.fn();
    render(<AccesClientPanel dossierId="d1" settings={reglages()} peutRegler onSettings={onSettings} />);
    expect(await screen.findByText('Aucune modification depuis l’ouverture de la Data Room.')).toBeInTheDocument();

    for (const libelle of ['Consulter les documents', 'Télécharger', 'Déposer des documents', 'Envoyer des demandes']) {
      expect(screen.getByLabelText(libelle)).toBeEnabled();
    }
    expect(screen.getByLabelText('Consulter les documents')).toBeChecked();
    expect(screen.getByLabelText('Déposer des documents')).not.toBeChecked();

    fireEvent.click(screen.getByLabelText('Envoyer des demandes'));
    await waitFor(() => expect(svc.updatePermissions).toHaveBeenCalledWith('d1', { permDemandes: false }));
    expect(onSettings).toHaveBeenCalledWith(expect.objectContaining({ permDemandes: false }));
    expect(await screen.findByText('Permissions modifiées — Envoyer des demandes : oui → non')).toBeInTheDocument();
    expect(screen.getByText(/par Karim Bennani/)).toBeInTheDocument();

    svc.toggleSuspension.mockResolvedValue(reglages({ accessStatus: 'SUSPENDED' }));
    fireEvent.click(screen.getByRole('button', { name: 'Suspendre l’accès' }));
    await waitFor(() => expect(svc.toggleSuspension).toHaveBeenCalledWith('d1', true));

    expect(screen.getByText(`${window.location.origin}/data-rooms`)).toBeInTheDocument();
    expect(screen.getByRole('note', { name: 'Régler l’accès du client' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Que règle l’accès du client ?' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'À quoi sert le lien d’accès ?' })).toBeInTheDocument();
  });

  it('un autre membre voit les reglages sans pouvoir les changer', () => {
    render(<AccesClientPanel dossierId="d1" settings={reglages({ accessStatus: 'SUSPENDED' })} peutRegler={false} onSettings={() => {}} />);
    expect(screen.getByLabelText('Envoyer des demandes')).toBeDisabled();
    expect(screen.getByText('Accès suspendu')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Réactiver l’accès' })).not.toBeInTheDocument();
    expect(screen.getByText(/Seuls l’employé responsable du dossier et le superviseur/)).toBeInTheDocument();
    expect(svc.historiqueAccesClient).not.toHaveBeenCalled();
  });
});

describe('3. Droit de suppression depuis la page Equipe', () => {
  it('affiche l etat de chaque employe ; le superviseur accorde et retire', async () => {
    auth.listDroitsSuppressionDataroom.mockResolvedValueOnce(['e2']).mockResolvedValue(['e1', 'e2']);
    auth.setDroitSuppressionDataroom.mockResolvedValue(undefined);
    render(<TeamPage />);
    const karim = (await screen.findByText(/Karim Bennani/)).closest('tr') as HTMLElement;
    const salma = screen.getByText(/Salma Idrissi/).closest('tr') as HTMLElement;
    const nadia = screen.getByText(/Nadia Superviseure/).closest('tr') as HTMLElement;
    expect(karim).toHaveTextContent('Non accordé');
    expect(salma).toHaveTextContent('Accordé');
    expect(nadia).toHaveTextContent('Sans objet');
    expect(within(salma).getByRole('button', { name: 'Retirer le droit de suppression à Salma Idrissi' })).toBeInTheDocument();

    fireEvent.click(within(karim).getByRole('button', { name: 'Accorder le droit de suppression à Karim Bennani' }));
    await waitFor(() => expect(auth.setDroitSuppressionDataroom).toHaveBeenCalledWith('e1', true));
    await waitFor(() => expect(screen.getByText(/Karim Bennani/).closest('tr')).toHaveTextContent('Accordé'));
    expect(screen.getByText(/Karim Bennani/).closest('tr')).not.toHaveTextContent('Non accordé');

    expect(screen.getByRole('note', { name: 'Le droit de supprimer des documents' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Que permet le droit de suppression ?' })).toBeInTheDocument();
  });
});
