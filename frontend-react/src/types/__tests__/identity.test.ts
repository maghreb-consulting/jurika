/// <reference types="vitest" />
import { describe, it, expect } from 'vitest';
import { toIsoDate } from '../identity';

describe('toIsoDate', () => {
  it('convertit DD.MM.YYYY -> YYYY-MM-DD (format backend FR)', () => {
    expect(toIsoDate('12.05.1990')).toBe('1990-05-12');
    expect(toIsoDate('01.01.2025')).toBe('2025-01-01');
    expect(toIsoDate('31.12.2030')).toBe('2030-12-31');
  });

  it('passe les dates ISO inchangees (deja YYYY-MM-DD)', () => {
    expect(toIsoDate('1990-05-12')).toBe('1990-05-12');
    expect(toIsoDate('2025-01-01')).toBe('2025-01-01');
  });

  it('retourne chaine vide pour undefined / null / vide', () => {
    expect(toIsoDate(undefined)).toBe('');
    expect(toIsoDate('')).toBe('');
  });

  it('trim les espaces autour', () => {
    expect(toIsoDate('  12.05.1990  ')).toBe('1990-05-12');
  });

  it('convertit aussi les separateurs / et - du format FR', () => {
    // Le contrat documente de toIsoDate couvre DD.MM.YYYY, DD/MM/YYYY ET DD-MM-YYYY.
    expect(toIsoDate('12/05/1990')).toBe('1990-05-12');
    expect(toIsoDate('12-05-1990')).toBe('1990-05-12');
  });

  it("laisse intacte une valeur non reconnue (pas de perte de saisie)", () => {
    // Format non reconnu : on ne casse pas la valeur, on la passe telle quelle.
    expect(toIsoDate('1990/05/12')).toBe('1990/05/12');
    expect(toIsoDate('5.5.1990')).toBe('5.5.1990');
    expect(toIsoDate('bla')).toBe('bla');
  });
});
