import { defineConfig, devices } from '@playwright/test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Lot L6b (E13) : parcours de bout en bout contre une PILE REELLE (aucune simulation).
 *
 * Distinct de playwright.config.ts (scenarios historiques) et de playwright.visuel.config.ts
 * (API simulee). Fichiers : e2e/parcours/**\/*.parcours.ts (la configuration historique ne les
 * ramasse pas : elle ne lit que *.spec.ts / *.test.ts).
 *
 *   npm run e2e:parcours                         toute la suite (outils, exploration, parcours)
 *   npm run e2e:parcours -- --grep exploration   l'exploration des ecrans par role seule
 *   npm run e2e:parcours -- --grep-invert @corpus-reel   ce qui tourne en CI (corpus fictif)
 *
 * Variables : E2E_BASE_URL (origine du front, defaut http://192.168.1.251), E2E_API_URL (defaut
 * http://192.168.1.251:8080/api/v1), E2E_SEED_URL (route de seed, defaut E2E_API_URL),
 * E2E_CAPTURES (captures de fin, defaut test-results/parcours), E2E_SANS_TEMPS_REEL=1 (tolere
 * l'absence du service temps reel, nommement). La pile doit activer la route de seed de test
 * (JURIKA_TEST_SEED_ENABLED=true, profil hors prod).
 *
 * Projet "outils" : tests des outils (sentinelle, controle des documents), sans pile ; il passe
 * avant les autres.
 */
const ICI = path.dirname(fileURLToPath(import.meta.url));
process.env.E2E_CAPTURES = path.resolve(ICI, process.env.E2E_CAPTURES ?? 'test-results/parcours');
const RAPPORT = path.resolve(ICI, 'test-results/parcours-rapport');

export default defineConfig({
  testDir: './e2e/parcours',
  testMatch: '**/*.parcours.ts',
  outputDir: 'test-results/parcours-artefacts',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  // Une pile reelle : pas de relance qui masquerait un 4xx intermittent.
  retries: 0,
  workers: process.env.CI ? 2 : 3,
  timeout: 180_000,
  expect: { timeout: 15_000 },
  reporter: [
    ['list'],
    ['html', { outputFolder: path.join(RAPPORT, 'html'), open: 'never' }],
    ['json', { outputFile: path.join(RAPPORT, 'resultats.json') }],
    ...(process.env.CI ? [['github'] as ['github']] : []),
  ],
  use: {
    ...devices['Desktop Chrome'],
    viewport: { width: 1440, height: 900 },
    locale: 'fr-FR',
    timezoneId: 'Africa/Casablanca',
    contextOptions: { reducedMotion: 'reduce' },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },
  projects: [
    { name: 'outils', testMatch: 'outils.parcours.ts' },
    { name: 'pile', testIgnore: 'outils.parcours.ts', dependencies: ['outils'] },
  ],
});
