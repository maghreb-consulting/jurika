import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import type { Role } from '../../../types/auth';

const appels = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), delete: vi.fn() }));
vi.mock('../../../lib/api', () => ({ api: appels }));

const session = vi.hoisted(() => ({ user: null as null | { role: Role; userId: string } }));
vi.mock('../../../store/authStore', () => ({
  useAuthStore: <T,>(selector: (s: typeof session) => T) => selector(session),
}));

import { VisiteGuidee } from '../VisiteGuidee';
import { cleVisite, etapesVisite } from '../visiteEtapes';
import { useAidesStore } from '../../../store/aidesStore';

/** Lot L6 (E6, CDC 15.4) : premiere visite guidee, une par role, masquable et reaffichable. */
describe('VisiteGuidee', () => {
  beforeEach(() => {
    session.user = { role: 'EMPLOYE', userId: 'u-1' };
    useAidesStore.setState({ masquees: new Set<string>(), utilisateur: 'u-1', chargee: true });
    appels.put.mockReset().mockResolvedValue({});
    appels.delete.mockReset().mockResolvedValue({});
  });

  it('chaque role a sa visite, avec une cle distincte', () => {
    const roles: Role[] = ['EMPLOYE', 'SUPERVISEUR', 'CLIENT', 'SUPER_ADMIN'];
    for (const r of roles) expect(etapesVisite(r).length).toBeGreaterThan(2);
    expect(roles.map(cleVisite)).toEqual(['visite-employe', 'visite-superviseur', 'visite-client', 'visite-super-admin']);
  });

  it('ne s ouvre pas tant que les aides du serveur ne sont pas chargees', () => {
    useAidesStore.setState({ chargee: false });
    render(<VisiteGuidee />);
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('s ouvre a la premiere connexion, se parcourt au clavier, et Echap la masque cote serveur', () => {
    render(<VisiteGuidee />);
    const boite = screen.getByRole('dialog', { name: 'Bienvenue sur JURIKA' });
    expect(boite).toHaveAttribute('aria-modal', 'true');
    expect(boite).toHaveFocus();
    fireEvent.keyDown(boite, { key: 'ArrowRight' });
    expect(screen.getByRole('dialog', { name: 'Le menu' })).toBeInTheDocument();
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'ArrowLeft' });
    expect(screen.getByRole('dialog', { name: 'Bienvenue sur JURIKA' })).toBeInTheDocument();
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(appels.put).toHaveBeenCalledWith('/auth/me/aides/visite-employe');
  });

  it('va jusqu au bout avec Suivant puis Terminer', () => {
    render(<VisiteGuidee />);
    const n = etapesVisite('EMPLOYE').length;
    for (let i = 1; i < n; i++) fireEvent.click(screen.getByRole('button', { name: 'Suivant' }));
    expect(screen.getByText(`Visite guidée · étape ${n} sur ${n}`)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Terminer' }));
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(useAidesStore.getState().masquees.has('visite-employe')).toBe(true);
  });

  it('deja vue : ne s ouvre pas ; reaffichee : s ouvre de nouveau', () => {
    useAidesStore.setState({ masquees: new Set(['visite-employe']) });
    const { rerender } = render(<VisiteGuidee />);
    expect(screen.queryByRole('dialog')).toBeNull();
    useAidesStore.getState().reafficher('visite-employe');
    rerender(<VisiteGuidee />);
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(appels.delete).toHaveBeenCalledWith('/auth/me/aides/visite-employe');
  });

  it('la visite du super-admin parle de la plateforme', () => {
    session.user = { role: 'SUPER_ADMIN', userId: 'u-9' };
    render(<VisiteGuidee />);
    expect(screen.getByRole('dialog', { name: 'Bienvenue dans l’administration de JURIKA' })).toBeInTheDocument();
  });

  it('la tabulation reste dans la boite', () => {
    render(<VisiteGuidee />);
    const boite = screen.getByRole('dialog');
    const boutons = Array.from(boite.querySelectorAll('button:not([disabled])'));
    const dernier = boutons[boutons.length - 1] as HTMLElement;
    dernier.focus();
    fireEvent.keyDown(boite, { key: 'Tab' });
    expect(boutons[0]).toHaveFocus();
  });
});
