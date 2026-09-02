import { describe, it, expect } from 'vitest';
import { formatObjetSocial, normalizeActivites } from '../objetSocial';
import { buildPayloadCreationSarl } from '../CreationSarlWorkflowPage';

/**
 * 2026-08 — OBJET_SOCIAL multi-activités + VILLE_GREFFE (ville, jamais commune).
 */
describe('formatObjetSocial / normalizeActivites', () => {
  it('1 activité → phrase simple (pas de tiret)', () => {
    expect(formatObjetSocial(undefined, 'Le conseil en gestion')).toBe(
      'Le conseil en gestion',
    );
  });

  it('plusieurs activités (multi-lignes) → liste à tirets séparée par \\n', () => {
    const desc = 'Le conseil\nLa formation\nL’import-export';
    expect(formatObjetSocial(undefined, desc)).toBe(
      '- Le conseil\n- La formation\n- L’import-export',
    );
  });

  it('accepte un tableau `activites[]` (prioritaire sur la description)', () => {
    expect(formatObjetSocial(['A', 'B'], 'ignorée')).toBe('- A\n- B');
  });

  it('ignore les lignes vides et compacte', () => {
    expect(normalizeActivites(undefined, 'A\n\n  \nB\n')).toEqual(['A', 'B']);
    expect(formatObjetSocial(undefined, 'A\n\nB')).toBe('- A\n- B');
  });
});

describe('buildPayloadCreationSarl — VILLE_GREFFE & OBJET_SOCIAL', () => {
  const base = (step2: Record<string, unknown>, step4: Record<string, unknown>) =>
    ({
      step1: { denomination: { denomination: 'ACME', formeJuridique: 'SARL' } },
      step2: { siege: step2 },
      step3: { capital: { capitalSocialMad: 100000, nombreParts: 1000 } },
      step4: { activite: step4 },
    }) as Record<string, Record<string, unknown>>;

  it('villeGreffe = ville du greffe (Step2), JAMAIS la commune', () => {
    const s = buildPayloadCreationSarl(
      base(
        { adresse: 'x', province: 'Casablanca', commune: 'Sidi Bernoussi', villeGreffe: 'Casablanca' },
        { description: 'conseil' },
      ),
    ).societe as Record<string, unknown>;
    expect(s.villeGreffe).toBe('Casablanca');
    expect(s.villeGreffe).not.toBe('Sidi Bernoussi');
  });

  it('villeGreffe : à défaut de villeGreffe, retombe sur la province (pas la commune)', () => {
    const s = buildPayloadCreationSarl(
      base(
        { adresse: 'x', province: 'Rabat', commune: 'Agdal' },
        { description: 'conseil' },
      ),
    ).societe as Record<string, unknown>;
    expect(s.villeGreffe).toBe('Rabat');
    expect(s.villeGreffe).not.toBe('Agdal');
  });

  it('objetSocial rendu en liste à tirets pour plusieurs activités', () => {
    const s = buildPayloadCreationSarl(
      base(
        { adresse: 'x', province: 'Casablanca', villeGreffe: 'Casablanca' },
        { description: 'Le conseil\nLa formation\nL’import-export' },
      ),
    ).societe as Record<string, unknown>;
    expect(s.objetSocial).toBe('- Le conseil\n- La formation\n- L’import-export');
    // La description brute reste disponible (rétro-compat).
    expect(s.activiteSociete).toBe('Le conseil\nLa formation\nL’import-export');
  });
});
