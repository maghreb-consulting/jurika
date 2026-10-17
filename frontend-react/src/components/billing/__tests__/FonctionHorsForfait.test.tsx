import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

/**
 * Lot L6b (E8) : le changement de forfait est reserve au superviseur (billing-service :
 * apercu et changement en hasRole('SUPERVISEUR')). L'explication d'une fonction hors forfait
 * n'offre le lien qu'a lui ; l'employe lit qui peut le faire.
 */
const role = vi.hoisted(() => ({ courant: 'EMPLOYE' }));
vi.mock('../../../store/authStore', () => ({
  useCurrentUser: () => ({ userId: 'u1', role: role.courant }),
}));

import { FonctionHorsForfait } from '../FonctionHorsForfait';

function rendre() {
  return render(
    <MemoryRouter>
      <FonctionHorsForfait fonction="Messagerie interne" disponibleDes="Professionnel" />
    </MemoryRouter>,
  );
}

describe('FonctionHorsForfait', () => {
  it('employe : explique sans lien vers un changement que le serveur lui refuse', () => {
    role.courant = 'EMPLOYE';
    rendre();
    expect(screen.getByTestId('fonction-hors-forfait')).toHaveTextContent('incluse dès le forfait Professionnel');
    expect(screen.getByTestId('fonction-hors-forfait')).toHaveTextContent('Le superviseur du cabinet peut changer de forfait');
    expect(screen.queryByRole('link')).toBeNull();
  });

  it('superviseur : lien vers le changement de forfait', () => {
    role.courant = 'SUPERVISEUR';
    rendre();
    expect(screen.getByRole('link', { name: 'Facturation, Changer de forfait' })).toHaveAttribute('href', '/app/billing/change-plan');
  });
});
