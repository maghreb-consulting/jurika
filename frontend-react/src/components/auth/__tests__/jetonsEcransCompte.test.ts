import { readdirSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * Lot L6b (E8) : les ecrans de l'authentification, du compte, des parametres, de l'equipe, du
 * contrat, de la facturation et des pages legales n'utilisent que les jetons du systeme de
 * design (classes semantiques) : ni hexadecimal, ni rgb(), ni classe de la palette Tailwind
 * (bg-emerald-50, text-white...). Une couleur en dur ne suit pas le theme sombre et echappe au
 * controle de contraste.
 *
 * Seule exception, nommee : l'apercu du papier a en-tete (CabinetTab), qui reproduit le
 * document IMPRIME (memes couleurs dans les deux themes) ; sa ligne porte la marque
 * "couleurs-imprimees".
 */
const RACINE = resolve(__dirname, '../../..');
const DOSSIERS = [
  'pages/auth',
  'pages/account',
  'pages/settings',
  'pages/team',
  'pages/contrat',
  'pages/billing',
  'pages/legal',
  'components/auth',
  'components/signup',
  'components/billing',
  'components/contrat',
  'components/essai',
];

const PALETTE_TAILWIND =
  /\b(?:bg|text|border|ring|from|to|via|fill|stroke|outline|decoration|divide|shadow|accent)-(?:slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)-\d{2,3}\b|\b(?:bg|text|border|ring)-(?:white|black)\b|\bbg-black\/|\btext-bg\b/;
const COULEUR_LITTERALE = /#[0-9a-f]{3,8}\b|\brgba?\(|\bhsla?\(/i;

function fichiers(dossier: string): string[] {
  return readdirSync(dossier, { withFileTypes: true }).flatMap((e) => {
    const chemin = join(dossier, e.name);
    if (e.isDirectory()) return e.name === '__tests__' ? [] : fichiers(chemin);
    return /\.(tsx|ts)$/.test(e.name) ? [chemin] : [];
  });
}

describe('Ecrans du compte, de la facturation et du contrat : jetons seulement', () => {
  it('aucune couleur codee en dur (hors papier imprime nomme)', () => {
    const fautes: string[] = [];
    for (const dossier of DOSSIERS) {
      for (const f of fichiers(join(RACINE, dossier))) {
        readFileSync(f, 'utf-8')
          .split('\n')
          .forEach((l, i) => {
            if (/^\s*\*/.test(l) || l.includes('couleurs-imprimees')) return;
            const code = l.replace(/\/\/.*$/, '');
            if (PALETTE_TAILWIND.test(code) || COULEUR_LITTERALE.test(code)) {
              fautes.push(`${f.slice(RACINE.length + 1)}:${i + 1}: ${l.trim()}`);
            }
          });
      }
    }
    expect(fautes).toEqual([]);
  });
});
