import { readdirSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * Lot L6b (E1) : regles du systeme de design, lues dans la source.
 *
 * 1. Deux themes COMPLETS : chaque jeton de couleur a une valeur claire ET une valeur sombre.
 * 2. Aucun composant de la bibliotheque (components/ui) ni de la coquille (components/layout)
 *    ne code une couleur en dur : ni hexadecimal, ni rgb(), ni classe de la palette Tailwind
 *    (bg-slate-100, text-white...). Tout passe par les jetons.
 * 3. Aucune police chargee depuis un service tiers (fonts.googleapis.com...).
 */
const RACINE = resolve(__dirname, '../..');
const CSS = readFileSync(join(RACINE, 'index.css'), 'utf-8');

function bloc(selecteur: RegExp): string {
  const m = selecteur.exec(CSS);
  if (!m) throw new Error(`Bloc introuvable : ${selecteur}`);
  const debut = m.index + m[0].length;
  return CSS.slice(debut, CSS.indexOf('}', debut));
}

function noms(source: string): Set<string> {
  return new Set([...source.replace(/\/\*[\s\S]*?\*\//g, '').matchAll(/(--[a-z0-9-]+)\s*:/gi)].map((m) => m[1]));
}

function fichiers(dossier: string): string[] {
  return readdirSync(dossier, { withFileTypes: true }).flatMap((e) => {
    const chemin = join(dossier, e.name);
    if (e.isDirectory()) return e.name === '__tests__' ? [] : fichiers(chemin);
    return /\.(tsx|ts)$/.test(e.name) ? [chemin] : [];
  });
}

const PALETTE_TAILWIND =
  /\b(?:bg|text|border|ring|from|to|via|fill|stroke|outline|decoration|divide|shadow)-(?:slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)-\d{2,3}\b|\b(?:bg|text|border|ring)-(?:white|black)\b/;
const COULEUR_LITTERALE = /#[0-9a-f]{3,8}\b|\brgba?\(|\bhsla?\(/i;

describe('Systeme de design : jetons et composants', () => {
  it('chaque jeton de couleur a une valeur dans le theme clair et dans le theme sombre', () => {
    const tailwind = noms(bloc(/@theme inline(?:\s+static)?\s*\{/));
    const references = [...bloc(/@theme inline(?:\s+static)?\s*\{/).matchAll(/var\((--jeton-[a-z0-9-]+)\)/g)].map((m) => m[1]);
    const clair = noms(bloc(/:root,\s*\[data-theme="light"\]\s*\{/));
    const sombre = noms(bloc(/:root\[data-theme="dark"\],\s*\[data-theme="dark"\]\s*\{/));
    expect(tailwind.size).toBeGreaterThan(20);
    expect(references.filter((j) => !clair.has(j)), 'jetons sans valeur claire').toEqual([]);
    expect(references.filter((j) => !sombre.has(j)), 'jetons sans valeur sombre').toEqual([]);
    expect([...clair].filter((j) => !sombre.has(j) && j.startsWith('--jeton-')), 'jetons clairs sans pendant sombre').toEqual([]);
  });

  it('aucune couleur codee en dur dans components/ui et components/layout', () => {
    const fautes: string[] = [];
    for (const dossier of ['components/ui', 'components/layout']) {
      for (const f of fichiers(join(RACINE, dossier))) {
        const lignes = readFileSync(f, 'utf-8').split('\n');
        lignes.forEach((l, i) => {
          const code = l.replace(/\/\/.*$/, '');
          if (/^\s*\*/.test(l)) return; // commentaire de bloc
          if (PALETTE_TAILWIND.test(code) || COULEUR_LITTERALE.test(code)) {
            fautes.push(`${f.slice(RACINE.length + 1)}:${i + 1}: ${l.trim()}`);
          }
        });
      }
    }
    expect(fautes).toEqual([]);
  });

  it('aucune police chargee depuis un service tiers', () => {
    const index = readFileSync(join(RACINE, '../index.html'), 'utf-8');
    for (const source of [CSS, index]) {
      expect(source).not.toMatch(/fonts\.googleapis\.com|fonts\.gstatic\.com|use\.typekit|fonts\.bunny/);
    }
    expect(CSS).toMatch(/@font-face\s*\{[^}]*font-family:\s*"Inter"[^}]*url\("\/fonts\/Inter-Regular\.ttf"\)/);
  });
});
