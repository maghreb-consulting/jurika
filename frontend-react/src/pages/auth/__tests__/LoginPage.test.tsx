import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { LoginPage } from '../LoginPage';

// Sprint 14 bis / D2 — LoginPage parcours 3 etapes (workspace -> credentials -> twofa).
// 3 tests : rendu initial etape workspace, transition workspace -> credentials,
// erreur sur workspace-check invalide.

const checkWorkspace = vi.fn();
const login = vi.fn();

vi.mock('../../../services/auth.service', () => ({
  authService: {
    checkWorkspace: (...args: unknown[]) => checkWorkspace(...args),
    login: (...args: unknown[]) => login(...args),
    sendSmsOtp: vi.fn(),
    verify2fa: vi.fn(),
  },
}));

vi.mock('../../../store/authStore', () => ({
  useAuthStore: (selector?: (s: { setSession: () => void }) => unknown) => {
    const state = { setSession: vi.fn() };
    return selector ? selector(state) : state;
  },
}));

function renderLogin() {
  return render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>,
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    checkWorkspace.mockReset();
    login.mockReset();
  });

  /**
   * Le champ est cible par son NOM ACCESSIBLE, pas par son placeholder : celui-ci
   * a change quand « JUR- » est devenu un prefixe fixe (2026-08-15), et un test
   * qui s'y accroche casse au moindre ajustement de libelle.
   */
  const champWorkspace = () =>
    screen.getByRole('textbox', { name: /code workspace/i }) as HTMLInputElement;

  it('rend l\'etape "workspace" en premier avec un input vide', () => {
    renderLogin();

    expect(screen.getByText(/Accedez a votre espace/i)).toBeInTheDocument();
    expect(champWorkspace().value).toBe('');
  });

  it('le prefixe JUR- est fixe : on ne saisit que les 5 caracteres', async () => {
    checkWorkspace.mockResolvedValue({ workspaceId: 'ws-1', name: 'Cabinet Demo' });

    const user = userEvent.setup();
    renderLogin();

    // L'employe ne tape QUE le suffixe — « JUR- » est affiche a cote du champ.
    await user.type(champWorkspace(), 'demo1');
    expect(champWorkspace().value).toBe('DEMO1');

    await user.click(screen.getByRole('button', { name: /Continuer/i }));
    // Le code COMPLET part malgre tout au serveur.
    await waitFor(() => expect(checkWorkspace).toHaveBeenCalledWith('JUR-DEMO1'));
  });

  it('un collage du code complet est absorbe (le prefixe en trop est retire)', async () => {
    const user = userEvent.setup();
    renderLogin();

    await user.type(champWorkspace(), 'JUR-DEMO1');
    expect(champWorkspace().value).toBe('DEMO1');
  });

  it('apres workspace-check OK, passe a l\'etape credentials avec le nom du cabinet', async () => {
    checkWorkspace.mockResolvedValue({ workspaceId: 'ws-1', name: 'Cabinet Demo' });

    const user = userEvent.setup();
    renderLogin();

    await user.type(champWorkspace(), 'DEMO1');
    await user.click(screen.getByRole('button', { name: /Continuer/i }));

    await waitFor(() => {
      expect(screen.getByText(/Bienvenue dans votre espace/i)).toBeInTheDocument();
    });
    expect(screen.getByText(/JUR-DEMO1.*Cabinet Demo/i)).toBeInTheDocument();
    expect(checkWorkspace).toHaveBeenCalledWith('JUR-DEMO1');
  });

  it('identifiant : suffixe @jurika.ma fige, mais adresse libre acceptee', async () => {
    checkWorkspace.mockResolvedValue({ workspaceId: 'ws-1', name: 'Cabinet Demo' });

    const user = userEvent.setup();
    renderLogin();
    await user.type(champWorkspace(), 'DEMO1');
    await user.click(screen.getByRole('button', { name: /Continuer/i }));
    await waitFor(() => screen.getByText(/Bienvenue dans votre espace/i));

    const mail = screen.getByLabelText(/Identifiant de connexion/i) as HTMLInputElement;

    // Partie locale seule : le suffixe est affiche a cote et n'est pas saisi.
    await user.type(mail, 'karim.benali');
    expect(mail.value).toBe('karim.benali');
    // Le suffixe est le <span> décoratif accolé au champ — la note d'aide en
    // dessous mentionne le même texte, d'où le ciblage sur le frère direct.
    const suffixe = mail.parentElement?.querySelector('span[aria-hidden="true"]');
    expect(suffixe?.textContent).toBe('@jurika.ma');

    // Un « @ » bascule en adresse LIBRE : les comptes anterieurs a V28 se
    // connectent avec leur email perso, qui n'est pas en @jurika.ma.
    await user.clear(mail);
    await user.type(mail, 'legacy@cabinet.ma');
    expect(mail.value).toBe('legacy@cabinet.ma');
    // Le suffixe disparait : le champ porte alors l'adresse complete.
    expect(mail.parentElement?.querySelector('span[aria-hidden="true"]')).toBeNull();
  });

  it('submit invalide (code workspace vide) : affiche l\'erreur in-app et n\'appelle PAS l\'API', async () => {
    const user = userEvent.setup();
    renderLogin();

    // Aucune saisie → submit direct.
    await user.click(screen.getByRole('button', { name: /Continuer/i }));

    // Message in-app (charte), pas de bulle native.
    expect(await screen.findByText(/Ce champ est requis/i)).toBeInTheDocument();
    expect(checkWorkspace).not.toHaveBeenCalled();
  });

  it('les formulaires portent noValidate (pas de bulle native)', () => {
    const { container } = renderLogin();
    const form = container.querySelector('form');
    expect(form).not.toBeNull();
    expect(form).toHaveAttribute('novalidate');
  });

  it('affiche l\'erreur quand workspace-check rejette', async () => {
    // axios.isAxiosError verifie isAxiosError===true ; sans ce flag,
    // extractError renvoie son message generique "Erreur reseau".
    checkWorkspace.mockRejectedValue(
      Object.assign(new Error('Bad workspace'), {
        isAxiosError: true,
        response: { data: { message: 'Code workspace inconnu' } },
      }),
    );

    const user = userEvent.setup();
    renderLogin();

    await user.type(champWorkspace(), 'XXXXX');
    await user.click(screen.getByRole('button', { name: /Continuer/i }));

    await waitFor(() => {
      expect(screen.getByText(/Code workspace inconnu/i)).toBeInTheDocument();
    });
  });
});
