import { readdirSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * Lot L6b (E9) : regles de la migration des ecrans du groupe (administration, tableaux de bord,
 * tracabilite, referentiel, messagerie, divers), lues dans la source.
 *
 * 1. Aucune couleur en dur (hexadecimal, rgb(), palette Tailwind) : tout passe par les jetons.
 * 2. Aucune hauteur fixe en vh ni h-screen (le defilement a la molette n'est jamais bloque) ;
 *    la messagerie se cale sur la hauteur de l'en-tete de la coquille (--hauteur-entete).
 * 3. Chaque ecran de la coquille s'enveloppe dans <Page> (titre, action, aide, fil) et n'a plus
 *    son propre h1.
 */
const RACINE = resolve(__dirname, '../../../..');
const DOSSIERS = [
  'pages/admin',
  'pages/dashboard',
  'pages/tracabilite',
  'pages/referentiel',
  'pages/misc',
  'pages/chat',
  'components/dashboard',
  'components/tracabilite',
  'components/misc',
];

function fichiers(dossier: string): string[] {
  return readdirSync(dossier, { withFileTypes: true }).flatMap((e) => {
    const chemin = join(dossier, e.name);
    if (e.isDirectory()) return e.name === '__tests__' ? [] : fichiers(chemin);
    return /\.(tsx|ts)$/.test(e.name) && !/\.test\.tsx?$/.test(e.name) ? [chemin] : [];
  });
}

const TOUS = DOSSIERS.flatMap((d) => fichiers(join(RACINE, 'src', d)));
const PALETTE =
  /\b(?:bg|text|border|ring|from|to|via|fill|stroke|outline|decoration|divide|shadow)-(?:slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)-\d{2,3}\b|\b(?:bg|text|border|ring)-(?:white|black)\b/;
const LITTERALE = /#[0-9a-f]{3,8}\b|\brgba?\(|\bhsla?\(/i;

function lignesDeCode(f: string): { n: number; code: string }[] {
  return readFileSync(f, 'utf-8')
    .split('\n')
    .map((l, i) => ({ n: i + 1, code: l.replace(/\/\/.*$/, '') }))
    .filter(({ code }) => !/^\s*\*/.test(code) && !/^\s*\/\*/.test(code));
}

describe('Ecrans du groupe E9 : jetons, defilement, structure', () => {
  it('trouve les fichiers du groupe', () => {
    expect(TOUS.length).toBeGreaterThan(30);
  });

  it('aucune couleur codee en dur', () => {
    const fautes = TOUS.flatMap((f) =>
      lignesDeCode(f)
        .filter(({ code }) => PALETTE.test(code) || LITTERALE.test(code))
        .map(({ n, code }) => `${f.slice(RACINE.length + 1)}:${n}: ${code.trim()}`),
    );
    expect(fautes).toEqual([]);
  });

  it('aucune hauteur fixe en vh ni h-screen ; la messagerie suit --hauteur-entete', () => {
    const fautes = TOUS.flatMap((f) =>
      lignesDeCode(f)
        .filter(({ code }) => /\b\d+vh\b|h-screen\b|min-h-screen\b/.test(code))
        .map(({ n, code }) => `${f.slice(RACINE.length + 1)}:${n}: ${code.trim()}`),
    );
    expect(fautes).toEqual([]);
    const chat = readFileSync(join(RACINE, 'src/pages/chat/ChatPage.tsx'), 'utf-8');
    expect(chat).toContain('var(--hauteur-entete)');
    expect(chat).not.toMatch(/overscroll-(?:contain|none)/);
  });

  it('chaque ecran de la coquille utilise <Page> et n a plus son propre h1', () => {
    const ecrans = TOUS.filter((f) => /\/pages\/(admin|dashboard|tracabilite|referentiel|chat)\/[^/]+Page\.tsx$|\/pages\/dashboard\/[^/]+Dashboard\.tsx$/.test(f));
    expect(ecrans.length).toBeGreaterThanOrEqual(17);
    const sansPage = ecrans.filter((f) => !readFileSync(f, 'utf-8').includes("from '../../components/layout/Page'"));
    const avecH1 = ecrans.filter((f) => /<h1[\s>]/.test(readFileSync(f, 'utf-8')));
    expect(sansPage.map((f) => f.slice(RACINE.length + 1))).toEqual([]);
    expect(avecH1.map((f) => f.slice(RACINE.length + 1))).toEqual([]);
  });
});
