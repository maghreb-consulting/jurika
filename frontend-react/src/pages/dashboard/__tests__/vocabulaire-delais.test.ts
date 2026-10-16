import { readdirSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * Lot L6b (E17, demande du 2026-10-16) : dans les tableaux de bord du cabinet, le mot
 * "echeance" (accentue, donc affiche) est ambigu : la facturation l'emploie pour les paiements
 * ("Prochaine echeance", "Payer l'echeance"). On y parle de delais legaux des demarches et de
 * dates limites des tickets. Les identifiants techniques (champs du serveur) ne sont pas vises.
 */
const RACINE = resolve(__dirname, '../../..');

function sources(dossier: string): string[] {
  return readdirSync(dossier, { withFileTypes: true }).flatMap((e) => {
    const chemin = join(dossier, e.name);
    if (e.isDirectory()) return e.name === '__tests__' ? [] : sources(chemin);
    return /\.(tsx|ts)$/.test(e.name) ? [chemin] : [];
  });
}

describe('vocabulaire des tableaux de bord', () => {
  it('aucun texte affiche ne parle d echeance', () => {
    const fichiers = [...sources(join(RACINE, 'pages/dashboard')), ...sources(join(RACINE, 'components/dashboard'))];
    expect(fichiers.length).toBeGreaterThan(10);
    const fautes = fichiers.flatMap((f) =>
      readFileSync(f, 'utf-8')
        .split('\n')
        .map((l, i) => ({ n: i + 1, l }))
        .filter(({ l }) => /[ée]chéance|Échéance/.test(l))
        .map(({ n, l }) => `${f.slice(RACINE.length + 1)}:${n}: ${l.trim()}`),
    );
    expect(fautes).toEqual([]);
  });
});
