import { describe, expect, it } from 'vitest';
import {
  CONVOCATION_DELAI_JOURS,
  addDaysIso,
  convocationDelaiError,
} from '../convocationDelai';

/**
 * Règle des 16 jours partagée MODIFICATION / DISSOLUTION — miroir exact du backend
 * `WorkflowSteps.convocationDelaiError`.
 */
describe('convocationDelai — règle des 16 jours', () => {
  it('accepte un écart de 16 jours pile', () => {
    expect(convocationDelaiError('2026-03-25', '2026-04-10')).toBeNull();
  });

  it('rejette un écart de 15 jours', () => {
    const err = convocationDelaiError('2026-03-26', '2026-04-10');
    expect(err).toContain('15 jour(s)');
    expect(err).toContain(String(CONVOCATION_DELAI_JOURS));
  });

  it('rejette une convocation postérieure ou égale à l’assemblée', () => {
    expect(convocationDelaiError('2026-04-10', '2026-04-10')).toContain('preceder');
    expect(convocationDelaiError('2026-04-11', '2026-04-10')).toContain('preceder');
  });

  it('ne contrôle rien si la convocation est absente (elle est optionnelle)', () => {
    expect(convocationDelaiError('', '2026-04-10')).toBeNull();
    expect(convocationDelaiError('2026-03-25', '')).toBeNull();
  });

  it('calcule en jours CALENDAIRES, insensibles au fuseau et au changement d’heure', () => {
    // Fenêtres chevauchant un passage à l'heure d'été (la durée réelle n'est pas un
    // multiple de 24 h) : le résultat doit rester exact.
    expect(convocationDelaiError('2026-03-14', '2026-03-30')).toBeNull(); // 16 j
    expect(convocationDelaiError('2026-03-15', '2026-03-30')).not.toBeNull(); // 15 j
    expect(convocationDelaiError('2026-10-19', '2026-11-04')).toBeNull(); // 16 j
  });
});

describe('convocationDelai — addDaysIso', () => {
  it('ajoute exactement n jours (calcul UTC, pas de décalage DST)', () => {
    expect(addDaysIso('2026-03-25', CONVOCATION_DELAI_JOURS)).toBe('2026-04-10');
    expect(addDaysIso('2026-03-14', 16)).toBe('2026-03-30');
    expect(addDaysIso('2026-10-19', 16)).toBe('2026-11-04');
  });

  it('gère les fins de mois et les années bissextiles', () => {
    expect(addDaysIso('2026-12-25', 16)).toBe('2027-01-10');
    expect(addDaysIso('2028-02-20', 16)).toBe('2028-03-07');
  });

  it('renvoie "" sur une date invalide', () => {
    expect(addDaysIso('', 16)).toBe('');
    expect(addDaysIso('25/03/2026', 16)).toBe('');
  });
});
