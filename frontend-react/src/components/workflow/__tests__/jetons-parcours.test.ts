import { readdirSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * Lot L6b (E5) : les ecrans des parcours (pages/workflow, components/workflow,
 * components/document, components/identity) ne codent aucune couleur en dur : ni classe
 * de la palette Tailwind, ni hexadecimal, ni rgb(). Tout passe par les jetons (deux
 * themes, contraste verifie). Avant E5 : 299 occurrences.
 */
const RACINE = resolve(__dirname, '../../..');
const DOSSIERS = ['pages/workflow', 'components/workflow', 'components/document', 'components/identity'];
const PALETTE =
  /\b(?:bg|text|border|ring|from|to|via|fill|stroke|outline|decoration|divide|shadow)-(?:slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)-\d{2,3}\b|\b(?:bg|text|border|ring)-(?:white|black)\b/;
const LITTERALE = /#[0-9a-f]{3,8}\b(?![\w-])|\brgba?\(|\bhsla?\(/i;

function fichiers(dossier: string): string[] {
  return readdirSync(dossier, { withFileTypes: true }).flatMap((e) => {
    const chemin = join(dossier, e.name);
    if (e.isDirectory()) return e.name === '__tests__' ? [] : fichiers(chemin);
    return /\.tsx$/.test(e.name) ? [chemin] : [];
  });
}

describe('Parcours : aucune couleur codee en dur', () => {
  it('ni palette Tailwind ni couleur litterale', () => {
    const fautes: string[] = [];
    for (const d of DOSSIERS) {
      for (const f of fichiers(join(RACINE, d))) {
        readFileSync(f, 'utf-8')
          .split('\n')
          .forEach((l, i) => {
            if (/^\s*(\*|\/\/)/.test(l)) return;
            if (PALETTE.test(l) || LITTERALE.test(l)) fautes.push(`${f.slice(RACINE.length + 1)}:${i + 1}: ${l.trim()}`);
          });
      }
    }
    expect(fautes).toEqual([]);
  });
});
