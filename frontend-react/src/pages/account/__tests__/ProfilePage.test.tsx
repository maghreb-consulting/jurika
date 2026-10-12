import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

/**
 * Lot L5 (E10) : ecran "Mon compte" (RG-CPT-01, RG-CPT-02).
 */

const svc = vi.hoisted(() => ({
  getMe: vi.fn(),
  getDemandesCompte: vi.fn(),
  modifierNom: vi.fn(),
  demanderChangementAdresse: vi.fn(),
  demanderChangementIdentifiant: vi.fn(),
  annulerDemandeCompte: vi.fn(),
  changePassword: vi.fn(),
}));

vi.mock('../../../services/auth.service', () => ({ authService: svc }));

import { ProfilePage } from '../ProfilePage';

/** Saisie par collage : rapide et independante de la charge de la machine. */
async function saisir(champ: HTMLElement, texte: string) {
  await userEvent.click(champ);
  await userEvent.paste(texte);
}

function me(role = 'EMPLOYE', contactEmail = 'karim@cabinet.ma') {
  return {
    userId: 'u1',
    workspaceId: 'w1',
    email: 'karim.benali@jurika.ma',
    loginEmail: 'karim.benali@jurika.ma',
    contactEmail,
    firstName: 'Karim',
    lastName: 'Benali',
    role,
  };
}

describe('ProfilePage (Mon compte)', { timeout: 20000 }, () => {
  beforeEach(() => {
    Object.values(svc).forEach((f) => f.mockReset());
    window.localStorage.clear();
  });

  it('affiche la demande en attente : la nouvelle adresse et la valeur en vigueur, distinctes', async () => {
    svc.getMe.mockResolvedValue(me('EMPLOYE', 'ancienne@cabinet.ma'));
    svc.getDemandesCompte.mockResolvedValue({
      adresse: { nouvelleValeur: 'nouvelle@cabinet.ma', expireLe: '2026-10-13T10:00:00Z' },
      identifiant: null,
    });
    render(<ProfilePage />);

    const attente = await screen.findByTestId('attente-adresse');
    expect(attente).toHaveTextContent('En attente de confirmation : nouvelle@cabinet.ma');
    expect(screen.getByTestId('profile-contact-email')).toHaveTextContent('ancienne@cabinet.ma');
    expect(screen.queryByTestId('attente-identifiant')).not.toBeInTheDocument();
  });

  it('une demande de changement d’adresse envoie la saisie et recharge l’etat en attente', async () => {
    svc.getMe.mockResolvedValue(me());
    svc.getDemandesCompte
      .mockResolvedValueOnce({ adresse: null, identifiant: null })
      .mockResolvedValue({
        adresse: { nouvelleValeur: 'perso@exemple.ma', expireLe: '2026-10-13T10:00:00Z' },
        identifiant: null,
      });
    svc.demanderChangementAdresse.mockResolvedValue({
      nouvelleValeur: 'perso@exemple.ma',
      expireLe: '2026-10-13T10:00:00Z',
    });
    render(<ProfilePage />);

    await saisir(await screen.findByLabelText('Nouvelle adresse électronique'), 'perso@exemple.ma');
    const boutons = screen.getAllByRole('button', { name: 'Recevoir le lien de confirmation' });
    await userEvent.click(boutons[0]);

    await waitFor(() => expect(svc.demanderChangementAdresse).toHaveBeenCalledWith('perso@exemple.ma'));
    expect(await screen.findByTestId('attente-adresse')).toHaveTextContent('perso@exemple.ma');
    expect(screen.getByTestId('profile-contact-email')).toHaveTextContent('karim@cabinet.ma');
  });

  it('identifiant deja pris : le refus 409 est explique', async () => {
    svc.getMe.mockResolvedValue(me());
    svc.getDemandesCompte.mockResolvedValue({ adresse: null, identifiant: null });
    svc.demanderChangementIdentifiant.mockRejectedValue({
      isAxiosError: true,
      response: { status: 409, data: { code: 'CONFLICT', message: 'IDENTIFIANT_DEJA_UTILISE' } },
    });
    render(<ProfilePage />);

    await saisir(await screen.findByLabelText('Nouvel identifiant de connexion'), 'salma@jurika.ma');
    const boutons = screen.getAllByRole('button', { name: 'Recevoir le lien de confirmation' });
    await userEvent.click(boutons[1]);

    expect(await screen.findByText(/déjà utilisé par un autre compte du cabinet/)).toBeInTheDocument();
  });

  it('mot de passe actuel faux : message explicite', async () => {
    svc.getMe.mockResolvedValue(me());
    svc.getDemandesCompte.mockResolvedValue({ adresse: null, identifiant: null });
    svc.changePassword.mockRejectedValue({
      isAxiosError: true,
      response: { status: 401, data: { code: 'UNAUTHORIZED', message: 'Ancien mot de passe incorrect' } },
    });
    render(<ProfilePage />);

    await saisir(await screen.findByLabelText('Mot de passe actuel'), 'Faux@Passe2026!');
    await saisir(screen.getByLabelText('Nouveau mot de passe'), 'Autre@Passe2026!');
    await saisir(screen.getByLabelText('Confirmation'), 'Autre@Passe2026!');
    await userEvent.click(screen.getByRole('button', { name: 'Changer le mot de passe' }));

    expect(await screen.findByText('Le mot de passe actuel est incorrect.')).toBeInTheDocument();
    expect(svc.changePassword).toHaveBeenCalledWith('Faux@Passe2026!', 'Autre@Passe2026!', 'Autre@Passe2026!');
  });

  it('client : consultation seule, sans formulaire de changement d’adresse ni d’identifiant', async () => {
    svc.getMe.mockResolvedValue(me('CLIENT'));
    svc.getDemandesCompte.mockResolvedValue({ adresse: null, identifiant: null });
    render(<ProfilePage />);

    expect(await screen.findByTestId('profile-contact-email')).toHaveTextContent('karim@cabinet.ma');
    expect(screen.queryByRole('button', { name: 'Recevoir le lien de confirmation' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Prénom')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Changer le mot de passe' })).toBeInTheDocument();
  });
});
