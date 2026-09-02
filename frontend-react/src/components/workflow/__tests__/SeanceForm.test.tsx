import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import {
  useSeanceForm,
  serializeAssocie,
  sanitizeFilename,
  emptyAssocie,
} from '../SeanceForm';

describe('SeanceForm — contrat de séance partagé', () => {
  const societe = {
    denomination: 'PARACOSME',
    capitalChiffres: 100000,
    siegeSocial: '101 bd Zerktouni, Casablanca',
    nombreParts: 1000,
    rcNumero: '123456',
    villeGreffe: 'Casablanca',
  };

  it('produit le contrat de séance (SARL) attendu par le back', () => {
    const { result } = renderHook(() =>
      useSeanceForm({ formeJuridique: 'SARL', societe }),
    );
    const p = result.current.buildPayload() as Record<string, any>;
    expect(p.associeUnique).toBe(false);
    expect(p.formeJuridique).toBe('SARL');
    expect(p.societe.denomination).toBe('PARACOSME');
    expect(p.societe.villeGreffe).toBe('Casablanca');
    // Toujours une liste associes (jamais un objet unique) — le back lit associes[0] en AU.
    expect(Array.isArray(p.associes)).toBe(true);
    expect(Array.isArray(p.documentsJoints)).toBe(true);
    expect(Array.isArray(p.ordreDuJour)).toBe(true);
    expect(p.convocation).toHaveProperty('rang', 'première');
    expect(p.seance).toHaveProperty('type');
  });

  it('marque associeUnique=true pour une SARL AU', () => {
    const { result } = renderHook(() =>
      useSeanceForm({ formeJuridique: 'SARL_AU', societe }),
    );
    const p = result.current.buildPayload() as Record<string, any>;
    expect(p.associeUnique).toBe(true);
    expect(p.formeJuridique).toBe('SARL_AU');
  });

  it('reflète les points d’ordre du jour saisis, filtrés/trimés', () => {
    const { result } = renderHook(() =>
      useSeanceForm({ formeJuridique: 'SARL', societe }),
    );
    act(() => result.current.setOrdreDuJour(['  Approbation des comptes  ', '', 'Affectation']));
    const p = result.current.buildPayload() as Record<string, any>;
    expect(p.ordreDuJour).toEqual(['Approbation des comptes', 'Affectation']);
  });

  it('serializeAssocie ne conserve le mandataire que si représenté', () => {
    const present = serializeAssocie({ ...emptyAssocie(), nom: 'BENANI', mandataireNom: 'X', presence: 'présent' });
    expect(present.mandataireNom).toBe('');
    const repr = serializeAssocie({ ...emptyAssocie(), nom: 'IDRISSI', mandataireNom: 'M. BENANI', presence: 'représenté' });
    expect(repr.mandataireNom).toBe('M. BENANI');
  });

  it('sanitizeFilename retire les caractères interdits', () => {
    expect(sanitizeFilename('Convocation - A/B:C*?')).toBe('Convocation - A B C');
  });
});
