import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { ToastProvider } from '../../../components/ui/Toast';

/** Lot L7 (E6, RG-CTR-03) : etat des contrats et activation d'un abonnement par le super-admin. */

const listeAdmin = vi.fn();
const versionsAdmin = vi.fn();
const ouvrirConditions = vi.fn();
vi.mock('../../../services/contrat.service', async (orig) => {
  const vrai = await orig<typeof import('../../../services/contrat.service')>();
  return {
    ...vrai,
    contratService: {
      listeAdmin: () => listeAdmin(),
      versionsAdmin: () => versionsAdmin(),
      ouvrirConditions: (ws: string, c: unknown) => ouvrirConditions(ws, c),
    },
  };
});
const listWorkspaces = vi.fn();
vi.mock('../../../services/admin.service', () => ({ adminService: { listWorkspaces: () => listWorkspaces() } }));

import { ContratsAdminPage } from '../ContratsAdminPage';

const CABINET = {
  id: 'ws1', code: 'JUR-ATLAS', name: 'Cabinet Atlas', contactEmail: null, statut: 'ACTIVE', forfait: null,
  employes: 1, clients: 0, storageBytes: null, storageFiles: null, createdAt: '2026-10-01T00:00:00Z',
};

describe('ContratsAdminPage', () => {
  beforeEach(() => {
    listWorkspaces.mockReset().mockResolvedValue([CABINET]);
    listeAdmin.mockReset().mockResolvedValue([]);
    versionsAdmin.mockReset().mockResolvedValue([
      { id: 'v1', code: '1', entreeEnVigueur: '2026-10-14', empreinteModele: 'x', empreinteCg: 'y',
        empreinteDpa: 'z', origine: 'INITIALE', publieeLe: '2026-10-14T00:00:00Z', retiree: false },
      { id: 'v0', code: '2026-10-12', entreeEnVigueur: '2026-10-13', empreinteModele: 'x', empreinteCg: 'y',
        empreinteDpa: 'z', origine: 'INITIALE', publieeLe: '2026-10-13T00:00:00Z', retiree: true },
    ]);
    ouvrirConditions.mockReset().mockResolvedValue({ conditionsId: 'cp1' });
  });

  it('affiche l’état du contrat de chaque cabinet et l’aide', async () => {
    render(<ToastProvider><ContratsAdminPage /></ToastProvider>);
    const ligne = (await screen.findByText('Cabinet Atlas')).closest('tr') as HTMLElement;
    expect(within(ligne).getByText('Aucun contrat')).toBeInTheDocument();
    expect(screen.getByRole('note', { name: 'Activer un abonnement et suivre les contrats' })).toBeInTheDocument();
    expect(screen.getByText(/Version 1 — en vigueur le 14 octobre 2026/)).toBeInTheDocument();
    // Le projet retire reste visible du seul super-admin, marque comme tel, sans "Partir de ce texte".
    const retiree = screen.getByText(/Version 2026-10-12 — retirée : jamais présentée aux cabinets/).closest('li') as HTMLElement;
    expect(within(retiree).queryByRole('button')).toBeNull();
  });

  it('montre, par cabinet, la réponse attendue ou donnée à une nouvelle version', async () => {
    const SECOND = { ...CABINET, id: 'ws2', code: 'JUR-RIF', name: 'Cabinet Rif' };
    listWorkspaces.mockResolvedValue([CABINET, SECOND]);
    const base = { enAttente: null, courant: null, derniereAcceptation: null, resiliation: null, joursAvantEcheance: null };
    listeAdmin.mockResolvedValue([
      { ...base, workspaceId: 'ws1', statut: 'ACCEPTE', versionSansReponse: '2', derniereReponse: null },
      { ...base, workspaceId: 'ws2', statut: 'RESILIE', versionSansReponse: null,
        derniereReponse: { id: 'r1', conditionsId: 'cp2', versionCode: '2', reponse: 'REFUSEE', acceptationId: null,
          signataireCompte: 'sup@rif.ma', reponduLeCasablanca: '2026-11-21 09:00:00', adresseIp: '1.2.3.4' } },
    ]);
    render(<ToastProvider><ContratsAdminPage /></ToastProvider>);
    expect(await screen.findByTestId('sans-reponse-ws1')).toHaveTextContent('Réponse attendue à la version 2');
    expect(screen.getByTestId('reponse-ws2')).toHaveTextContent(
      'Version 2 refusée le 21 novembre 2026 à 09:00 (heure de Casablanca) — résiliation à l’échéance');
  });

  it('saisit les conditions particulières : en attente d’acceptation du superviseur', async () => {
    render(<ToastProvider><ContratsAdminPage /></ToastProvider>);
    fireEvent.click(await screen.findByRole('button', { name: 'Saisir des conditions' }));
    fireEvent.change(screen.getByLabelText('Forfait *'), { target: { value: 'PROFESSIONNEL' } });
    expect(screen.getByLabelText('Prix HT de l’échéance (DH) *')).toHaveValue('7500');
    fireEvent.change(screen.getByLabelText('Rythme de paiement *'), { target: { value: 'MENSUEL' } });
    expect(screen.getByLabelText('Prix HT de l’échéance (DH) *')).toHaveValue('750');
    fireEvent.change(screen.getByLabelText('Date de début *'), { target: { value: '2026-11-01' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer les conditions' }));
    await waitFor(() => expect(ouvrirConditions).toHaveBeenCalledTimes(1));
    expect(ouvrirConditions).toHaveBeenCalledWith('ws1', {
      forfait: 'PROFESSIONNEL', rythmePaiement: 'MENSUEL', prixHt: 750, dateDebut: '2026-11-01', modePaiement: null,
    });
  });

  it('forfait Sur Devis : aucun prix proposé, il faut saisir celui du devis', async () => {
    render(<ToastProvider><ContratsAdminPage /></ToastProvider>);
    fireEvent.click(await screen.findByRole('button', { name: 'Saisir des conditions' }));
    fireEvent.change(screen.getByLabelText('Forfait *'), { target: { value: 'SUR_DEVIS' } });
    expect(screen.getByLabelText('Prix HT de l’échéance (DH) *')).toHaveValue('');
    expect(screen.getByRole('button', { name: 'Enregistrer les conditions' })).toBeDisabled();
  });
});
