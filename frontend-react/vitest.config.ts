import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// Sprint 14 D1 -- setup Vitest + React Testing Library + jsdom.
export default defineConfig({
  plugins: [react()],
  esbuild: {
    jsx: 'automatic',
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./test/setup.ts'],
    // Les specs `e2e/**` sont des scenarios PLAYWRIGHT (`npm run e2e`), pas des tests
    // Vitest : sans cette exclusion, Vitest les collecte et les compte en echec
    // ("Playwright Test did not expect test() to be called here"), ce qui noyait les
    // vrais resultats -- 14 fichiers rouges pour 0 defaut reel.
    exclude: ['**/node_modules/**', '**/dist/**', 'e2e/**'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      exclude: [
        'node_modules/',
        'dist/',
        'test/**',
        'src/**/*.d.ts',
        'src/main.tsx',
        'src/vite-env.d.ts',
      ],
      thresholds: {
        // Warn-only V1 -- gate strict en Sprint 14 bis
        lines: 0,
        branches: 0,
        functions: 0,
        statements: 0,
      },
    },
  },
});
