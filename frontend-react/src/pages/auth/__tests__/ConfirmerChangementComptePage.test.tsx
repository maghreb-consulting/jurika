import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

/**
 * Lot L5 (E10) : page publique de confirmation d'un changement d'adresse ou
 * d'identifiant (RG-CPT-02).
 */

const confirmer = vi.hoisted(() => vi.fn());
vi.mock('../../../services/auth.service', () => ({
  authService: { confirmerChangementCompte: confirmer },
}));

import { ConfirmerChangementComptePage } from '../ConfirmerChangementComptePage';

function ouvrir(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <ConfirmerChangementComptePage />
    </MemoryRouter>,
  );
}

describe('ConfirmerChangementComptePage', () => {
  beforeEach(() => {
    confirmer.mockReset();
    window.localStorage.clear();
  });

  it('confirme le jeton du lien et annonce le nouvel identifiant', async () => {
    confirmer.mockResolvedValue({ motif: 'identifiant', nouvelleValeur: 'k.benali@jurika.ma' });
    ouvrir('/confirmer-changement-compte?token=abc-123');

    expect(await screen.findByText('Identifiant de connexion modifié')).toBeInTheDocument();
    expect(screen.getByText('k.benali@jurika.ma')).toBeInTheDocument();
    expect(confirmer).toHaveBeenCalledTimes(1);
    expect(confirmer).toHaveBeenCalledWith('abc-123');
  });

  it('lien expire : explique et dit quoi faire', async () => {
    confirmer.mockRejectedValue({
      isAxiosError: true,
      response: { status: 400, data: { code: 'LIEN_EXPIRE', message: 'Ce lien a expire.' } },
    });
    ouvrir('/confirmer-changement-compte?token=vieux');

    expect(await screen.findByRole('alert')).toHaveTextContent('Ce lien a expiré');
    expect(screen.getByText(/Aucune modification n’a été faite/)).toBeInTheDocument();
  });

  it('lien sans jeton : aucun appel, message', () => {
    ouvrir('/confirmer-changement-compte');
    expect(confirmer).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('Ce lien est incomplet');
  });
});
