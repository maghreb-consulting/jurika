import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

/**
 * Lot L7 (E4) : inscription a l'essai gratuit. RG-ESS-01 (cinq informations, aucun
 * ICE), RG-CTR-10 (case non pre-cochee, refus sans acceptation), RG-ESS-02 (codes).
 */

const service = vi.hoisted(() => ({
  conditions: vi.fn(),
  demander: vi.fn(),
  etatDemande: vi.fn(),
  verifierTelephone: vi.fn(),
  renvoyerCode: vi.fn(),
}));
vi.mock('../../../services/essai.service', () => ({ essaiService: service }));

import { EssaiGratuitPage } from '../EssaiGratuitPage';

const CONDITIONS = { version: 'D-PROJET-2026-10-12', empreinte: 'a'.repeat(64), texte: '1. L’essai est gratuit pendant 7 jours.' };

function ouvrir() {
  return render(
    <MemoryRouter initialEntries={['/essai']}>
      <EssaiGratuitPage />
    </MemoryRouter>,
  );
}

function remplir() {
  fireEvent.change(screen.getByLabelText('Prénom'), { target: { value: 'Karim' } });
  fireEvent.change(screen.getByLabelText('Nom'), { target: { value: 'Benali' } });
  fireEvent.change(screen.getByLabelText('Organisation (nom du cabinet)'), { target: { value: 'Cabinet Benali' } });
  fireEvent.change(screen.getByTestId('essai-telephone'), { target: { value: '612345678' } });
  fireEvent.change(screen.getByLabelText('Adresse électronique'), { target: { value: 'karim@cabinet.ma' } });
}

describe('EssaiGratuitPage', () => {
  beforeEach(() => {
    Object.values(service).forEach((f) => f.mockReset());
    service.conditions.mockResolvedValue(CONDITIONS);
  });

  it('demande cinq informations seulement, sans ICE, et affiche les conditions', async () => {
    ouvrir();
    expect(await screen.findByText(CONDITIONS.texte)).toBeInTheDocument();
    expect(screen.getAllByRole('textbox')).toHaveLength(5);
    expect(screen.queryByText(/ICE/)).not.toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: /J’ai lu et j’accepte les conditions/ })).not.toBeChecked();
  });

  it('refuse l’envoi tant que les conditions ne sont pas acceptees', async () => {
    ouvrir();
    await screen.findByText(CONDITIONS.texte);
    remplir();
    fireEvent.click(screen.getByRole('button', { name: 'Recevoir mes codes de vérification' }));
    expect(await screen.findByText(/Cochez la case pour accepter/)).toBeInTheDocument();
    expect(service.demander).not.toHaveBeenCalled();
  });

  it('envoie la demande avec la version acceptee puis demande le code SMS', async () => {
    service.demander.mockResolvedValue({
      demandeId: 'd-1', telephoneMasque: '********5678', email: 'karim@cabinet.ma',
      smsEnvoye: true, emailEnvoye: true, expireLe: '2026-10-15T10:00:00Z',
    });
    ouvrir();
    await screen.findByText(CONDITIONS.texte);
    remplir();
    fireEvent.click(screen.getByRole('checkbox', { name: /J’ai lu et j’accepte/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Recevoir mes codes de vérification' }));
    await waitFor(() => expect(service.demander).toHaveBeenCalledTimes(1));
    expect(service.demander).toHaveBeenCalledWith({
      nom: 'Benali', prenom: 'Karim', organisation: 'Cabinet Benali', telephone: '+212612345678',
      email: 'karim@cabinet.ma', conditionsAcceptees: true, conditionsVersion: 'D-PROJET-2026-10-12',
    });
    expect(await screen.findByLabelText('Code reçu par SMS au ********5678')).toBeInTheDocument();
    expect(screen.getByText(/Ouvrez le courriel envoyé à karim@cabinet.ma/)).toBeInTheDocument();
  });

  it('affiche le refus du serveur (adresse deja utilisee)', async () => {
    service.demander.mockRejectedValue({
      isAxiosError: true,
      response: { status: 409, data: { code: 'CONFLICT', message: 'Cette adresse électronique a déjà servi à un essai ou à un compte JURIKA : connectez-vous, ou contactez JURIKA.' } },
    });
    ouvrir();
    await screen.findByText(CONDITIONS.texte);
    remplir();
    fireEvent.click(screen.getByRole('checkbox', { name: /J’ai lu et j’accepte/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Recevoir mes codes de vérification' }));
    expect(await screen.findByText(/connectez-vous, ou contactez JURIKA/)).toBeInTheDocument();
  });

  it('conditions introuvables : inscription impossible et dite', async () => {
    service.conditions.mockRejectedValue(new Error('panne'));
    ouvrir();
    expect(await screen.findByText(/conditions de l’essai n’ont pas pu être chargées/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Recevoir mes codes de vérification' })).toBeDisabled();
  });
});
