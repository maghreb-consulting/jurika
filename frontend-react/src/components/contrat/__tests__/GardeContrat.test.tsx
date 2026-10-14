import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';

/** Lot L7 (E6, RG-CTR-03) : garde de l'ecran pour un contrat en attente d'acceptation. */

const etat = vi.fn();
vi.mock('../../../services/contrat.service', async (orig) => {
  const vrai = await orig<typeof import('../../../services/contrat.service')>();
  return { ...vrai, contratService: { etat: () => etat() } };
});

const auth: { user: { userId: string; role: string } | null } = { user: null };
vi.mock('../../../store/authStore', () => ({
  useAuthStore: <T,>(selector: (s: typeof auth) => T) => selector(auth),
}));

import { useGardeContrat } from '../useGardeContrat';
import { useContratStore } from '../../../store/contratStore';

function Sonde() {
  const decision = useGardeContrat();
  const { pathname } = useLocation();
  return <p data-testid="sonde">{`${decision} ${pathname}`}</p>;
}

function afficher(chemin: string) {
  render(
    <MemoryRouter initialEntries={[chemin]}>
      <Routes>
        <Route path="*" element={<Sonde />} />
      </Routes>
    </MemoryRouter>,
  );
}

const BLOQUE = {
  statut: 'EN_ATTENTE', acceptationRequise: true, objetAAccepter: 'CONDITIONS_PARTICULIERES', enAttente: null,
  courant: null, derniereAcceptation: null, resiliation: null, versionEnVigueur: null, versionAVenir: null,
  joursAvantEcheance: null, avertissementEcheance: false, reprise: null,
};

describe('useGardeContrat', () => {
  beforeEach(() => {
    etat.mockReset();
    useContratStore.getState().vider();
    sessionStorage.clear();
  });

  it('superviseur d’un cabinet en attente : redirigé hors des pages permises', async () => {
    auth.user = { userId: 'sup', role: 'SUPERVISEUR' };
    etat.mockResolvedValue(BLOQUE);
    afficher('/tickets');
    await waitFor(() => expect(screen.getByTestId('sonde')).toHaveTextContent('REDIRIGER /tickets'));
  });

  it('superviseur sur l’écran du contrat ou l’identité du cabinet : mise en page restreinte', async () => {
    auth.user = { userId: 'sup2', role: 'SUPERVISEUR' };
    etat.mockResolvedValue(BLOQUE);
    afficher('/settings/cabinet');
    await waitFor(() => expect(screen.getByTestId('sonde')).toHaveTextContent('RESTREINT'));
  });

  it('employé d’un cabinet en attente : message d’attente', async () => {
    auth.user = { userId: 'emp', role: 'EMPLOYE' };
    etat.mockResolvedValue({ statut: 'EN_ATTENTE', acceptationRequise: true });
    afficher('/tickets');
    await waitFor(() => expect(screen.getByTestId('sonde')).toHaveTextContent('ATTENTE_EMPLOYE'));
  });

  it('contrat accepté : libre ; super-admin jamais concerné', async () => {
    auth.user = { userId: 'sup3', role: 'SUPERVISEUR' };
    etat.mockResolvedValue({ ...BLOQUE, statut: 'ACCEPTE', acceptationRequise: false, objetAAccepter: null });
    afficher('/tickets');
    await waitFor(() => expect(screen.getByTestId('sonde')).toHaveTextContent('LIBRE /tickets'));

    auth.user = { userId: 'admin', role: 'SUPER_ADMIN' };
    etat.mockClear();
    afficher('/admin/workspaces');
    expect(screen.getAllByTestId('sonde').at(-1)).toHaveTextContent('LIBRE');
    expect(etat).not.toHaveBeenCalled();
  });

  it('nouvelle version des conditions générales : conduit une fois au contrat, sans bloquer', async () => {
    auth.user = { userId: 'sup4', role: 'SUPERVISEUR' };
    etat.mockResolvedValue({ ...BLOQUE, statut: 'ACCEPTE', objetAAccepter: 'NOUVELLE_VERSION' });
    afficher('/tickets');
    await waitFor(() => expect(screen.getByTestId('sonde')).toHaveTextContent('LIBRE /app/contrat'));
  });
});
