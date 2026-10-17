import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { useState } from 'react';

/**
 * Recette R1 (point 1) : l'onglet d'origine de la demande d'essai relit l'etat ; une fois l'essai
 * ouvert (verification faite dans un autre onglet), il affiche le code du cabinet et l'identifiant
 * rendus par le serveur, et ne pretend pas que le courriel des identifiants a echoue (resultat de
 * l'envoi inconnu a la relecture).
 */

const service = vi.hoisted(() => ({
  etatDemande: vi.fn(),
  verifierTelephone: vi.fn(),
  renvoyerCode: vi.fn(),
}));
vi.mock('../../../services/essai.service', () => ({ essaiService: service }));

import { VerificationEssai } from '../VerificationEssai';
import type { EtatDemandeEssai } from '../../../services/essai.service';

const EN_COURS: EtatDemandeEssai = {
  demandeId: 'd-1', emailVerifie: false, telephoneVerifie: true, essaiCree: false,
  workspaceCode: null, loginEmail: null,
};

function Hote({ initial }: { initial: EtatDemandeEssai }) {
  const [etat, setEtat] = useState(initial);
  return <VerificationEssai etat={etat} onEtat={setEtat} />;
}

describe('VerificationEssai', () => {
  beforeEach(() => {
    Object.values(service).forEach((f) => f.mockReset());
    vi.useFakeTimers();
  });
  afterEach(() => vi.useRealTimers());

  it('essai ouvert dans un autre onglet : code du cabinet et identifiant affiches ici', async () => {
    service.etatDemande.mockResolvedValue({
      ...EN_COURS, emailVerifie: true, essaiCree: true,
      workspaceCode: 'JUR-AB12C', loginEmail: 'karim.benali@jurika.ma',
    });
    render(<MemoryRouter><Hote initial={EN_COURS} /></MemoryRouter>);
    await act(async () => { await vi.advanceTimersByTimeAsync(5000); });
    expect(screen.getByTestId('essai-ouvert')).toBeInTheDocument();
    expect(screen.getByText('JUR-AB12C')).toBeInTheDocument();
    expect(screen.getByText('karim.benali@jurika.ma')).toBeInTheDocument();
    expect(screen.getByText(/mot de passe provisoire vous a été envoyé par courriel/)).toBeInTheDocument();
    expect(screen.queryByText(/n’a pas pu être envoyé/)).not.toBeInTheDocument();
  });

  it('echec de l envoi connu a la creation : il est dit', () => {
    render(<MemoryRouter><Hote initial={{
      ...EN_COURS, emailVerifie: true, essaiCree: true, workspaceCode: 'JUR-AB12C',
      loginEmail: 'karim.benali@jurika.ma', identifiantsEnvoyes: false,
    }} /></MemoryRouter>);
    expect(screen.getByText(/n’a pas pu être envoyé/)).toBeInTheDocument();
  });
});
