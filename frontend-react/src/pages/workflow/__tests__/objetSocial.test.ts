import { describe, it, expect } from 'vitest';
import { formatObjetSocial, normalizeActivites } from '../objetSocial';

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

/*
 * LOT C (2026-09-23) — les trois cas qui suivaient ont été retirés.
 *
 * Ils portaient sur `buildPayloadCreationSarl`, constructeur de charge utile
 * qu'AUCUN code de production n'appelait plus : il n'était vivant que par ses
 * propres tests. Le contrat qu'ils décrivaient — VILLE_GREFFE jamais la commune,
 * OBJET_SOCIAL en liste à tirets — est désormais tenu par le constructeur unique
 * côté serveur, et vérifié mécaniquement par `ContratChargeUtileCreationTest`
 * (workflow-service), qui relève les 193 chemins sur les résolveurs eux-mêmes.
 *
 * Les quatre cas conservés ci-dessus portent sur `formatObjetSocial` /
 * `normalizeActivites`, fonctions pures toujours employées.
 */
