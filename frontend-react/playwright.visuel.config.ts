import { defineConfig, devices } from '@playwright/test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { API_SIMULEE } from './e2e/visuel/constantes';

/**
 * Lot L6b (E4) : harnais de verification visuelle automatique.
 *
 * Distinct de playwright.config.ts (qui vise une pile reelle) : ici, AUCUN serveur
 * applicatif. Le front est construit (`vite build`) puis servi (`vite preview`) ; toutes
 * les reponses de l'API sont simulees par page.route a partir des jeux de donnees de
 * e2e/visuel/simulation. Toute requete non simulee fait echouer l'ecran en la nommant.
 *
 * Les fichiers de ce harnais se terminent par `.visuel.ts` : la configuration e2e
 * existante (motif *.spec.ts) ne les ramasse pas, et celle-ci ne ramasse qu'eux.
 *
 *   npm run test:visuel             tout (detecteurs + ecrans) ; echoue sur un defaut
 *   npm run test:visuel:captures    meme parcours, defauts consignes sans echec
 *                                   (VISUEL_NON_BLOQUANT=1) : captures et rapport complets
 *
 * Variables :
 *   VISUEL_SORTIE         dossier des captures (defaut test-results/captures)
 *   VISUEL_RAPPORT        dossier du rapport (defaut test-results/visuel-rapport)
 *   VISUEL_NON_BLOQUANT   1 : les defauts sont consignes au rapport, le test ne tombe pas
 *   VISUEL_PORT           port de vite preview (defaut 4599)
 *   VISUEL_SANS_BUILD     1 : sert le dossier deja construit (dist/visuel) sans reconstruire
 */
const ICI = path.dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.VISUEL_PORT ?? 4599);
const DIST = 'dist/visuel';

// `npm run test:visuel:captures` : mode non bloquant (meme effet sous Windows et Linux, sans
// syntaxe d'environnement propre au shell). La variable est heritee par les workers.
if (process.env.npm_lifecycle_event === 'test:visuel:captures') process.env.VISUEL_NON_BLOQUANT = '1';

process.env.VISUEL_SORTIE =path.resolve(ICI, process.env.VISUEL_SORTIE ?? 'test-results/captures');
process.env.VISUEL_RAPPORT = path.resolve(ICI, process.env.VISUEL_RAPPORT ?? 'test-results/visuel-rapport');

const construire = `npx vite build --outDir ${DIST} --emptyOutDir --logLevel warn`;
const servir = `npx vite preview --outDir ${DIST} --port ${PORT} --strictPort --host 127.0.0.1`;

export default defineConfig({
  testDir: './e2e/visuel',
  testMatch: '**/*.visuel.ts',
  outputDir: 'test-results/visuel-artefacts',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: process.env.CI ? 2 : 4,
  timeout: 90_000,
  expect: { timeout: 10_000 },
  globalSetup: './e2e/visuel/preparation.ts',
  globalTeardown: './e2e/visuel/synthese.ts',
  reporter: [
    ['list'],
    ['html', { outputFolder: path.join(process.env.VISUEL_RAPPORT, 'html'), open: 'never' }],
    ['json', { outputFile: path.join(process.env.VISUEL_RAPPORT, 'resultats.json') }],
  ],
  use: {
    ...devices['Desktop Chrome'],
    baseURL: `http://127.0.0.1:${PORT}`,
    locale: 'fr-FR',
    timezoneId: 'Africa/Casablanca',
    // Captures stables : animations reduites (le front respecte prefers-reduced-motion).
    contextOptions: { reducedMotion: 'reduce' },
    trace: 'retain-on-failure',
    screenshot: 'off',
    video: 'off',
  },
  projects: [
    { name: 'detecteurs', testMatch: 'detecteurs.visuel.ts' },
    { name: 'ecrans', testMatch: 'ecrans.visuel.ts' },
  ],
  webServer: {
    command: process.env.VISUEL_SANS_BUILD === '1' ? servir : `${construire} && ${servir}`,
    url: `http://127.0.0.1:${PORT}/`,
    reuseExistingServer: false,
    timeout: 240_000,
    env: { VITE_API_URL: API_SIMULEE, VITE_REALTIME_URL: '', VITE_SENTRY_DSN_FRONTEND: '' },
  },
});
