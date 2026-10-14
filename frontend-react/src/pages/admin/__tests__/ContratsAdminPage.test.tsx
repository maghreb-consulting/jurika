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
      { id: 'v1', code: '2026-10-12', entreeEnVigueur: '2026-10-14', empreinteModele: 'x', empreinteCg: 'y',
        empreinteDpa: 'z', origine: 'INITIALE', publieeLe: '2026-10-14T00:00:00Z' },
    ]);
    ouvrirConditions.mockReset().mockResolvedValue({ conditionsId: 'cp1' });
  });

  it('affiche l’état du contrat de chaque cabinet et l’aide', async () => {
    render(<ToastProvider><ContratsAdminPage /></ToastProvider>);
    const ligne = (await screen.findByText('Cabinet Atlas')).closest('tr') as HTMLElement;
    expect(within(ligne).getByText('Aucun contrat')).toBeInTheDocument();
    expect(screen.getByRole('note', { name: 'Activer un abonnement et suivre les contrats' })).toBeInTheDocument();
    expect(screen.getByText(/Version 2026-10-12/)).toBeInTheDocument();
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
