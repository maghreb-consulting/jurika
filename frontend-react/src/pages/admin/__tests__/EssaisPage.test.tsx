import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../../../components/ui/Toast';

/** Lot L7 (E4) : ecran des essais du super-admin (RG-ESS-03, RG-ESS-11). */

const service = vi.hoisted(() => ({
  liste: vi.fn(),
  prolonger: vi.fn(),
  mettreFin: vi.fn(),
  signalementExamine: vi.fn(),
  suspendre: vi.fn(),
  leverSuspension: vi.fn(),
}));
vi.mock('../../../services/essai.service', () => ({ essaiService: service }));

import { EssaisPage } from '../EssaisPage';

const BASE = {
  contactEmail: 'karim@cabinet.ma', statut: 'ACTIVE', debut: '2026-10-10T10:00:00Z', fin: '2026-10-17T10:00:00Z',
  expireLe: null, avertissementLe: null, prolongeLe: null, prolongationJours: null, finMotif: null,
  abonnement: null, adresseIp: null, appareil: null, signalement: null, signalementExamineLe: null,
  creeLe: '2026-10-10T10:00:00Z', supprimeLe: null,
};

function ouvrir() {
  return render(
    <MemoryRouter>
      <ToastProvider>
        <EssaisPage />
      </ToastProvider>
    </MemoryRouter>,
  );
}

describe('EssaisPage', () => {
  beforeEach(() => {
    Object.values(service).forEach((f) => f.mockReset());
  });

  it('liste les essais, met en avant les signalements et propose les actions', async () => {
    service.liste.mockResolvedValue([
      { ...BASE, workspaceId: 'w1', code: 'JUR-AAAAA', nom: 'Cabinet Benali', statutEssai: 'TRIAL_ACTIVE' },
      { ...BASE, workspaceId: 'w2', code: 'JUR-BBBBB', nom: 'Cabinet Alami', statutEssai: 'TRIAL_ACTIVE',
        signalement: 'Même réseau (adresse IP) que 1 essai déjà ouvert.', adresseIp: '10.0.0.1' },
    ]);
    service.signalementExamine.mockResolvedValue(undefined);
    ouvrir();
    expect(await screen.findByText('Cabinet Benali')).toBeInTheDocument();
    expect(screen.getByText('1 inscription signalée reste à examiner.')).toBeInTheDocument();
    expect(screen.getByText('Même réseau (adresse IP) que 1 essai déjà ouvert.')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Prolonger' })).toHaveLength(2);
    expect(screen.getAllByRole('button', { name: 'Suspendre' })).toHaveLength(2);
    fireEvent.click(screen.getByRole('button', { name: 'Marquer examiné' }));
    await waitFor(() => expect(service.signalementExamine).toHaveBeenCalledWith('w2'));
  });

  it('un essai deja prolonge ne se prolonge plus', async () => {
    service.liste.mockResolvedValue([
      { ...BASE, workspaceId: 'w1', code: 'JUR-AAAAA', nom: 'Cabinet Benali', statutEssai: 'TRIAL_ACTIVE',
        prolongeLe: '2026-10-12T10:00:00Z', prolongationJours: 5 },
    ]);
    ouvrir();
    expect(await screen.findByText('Prolongé de 5 jour(s)')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Prolonger' })).not.toBeInTheDocument();
  });

  it('etat vide explicite', async () => {
    service.liste.mockResolvedValue([]);
    ouvrir();
    expect(await screen.findByText(/Aucun essai gratuit pour l’instant/)).toBeInTheDocument();
  });
});
