import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * Lot L6b (E1) : contraste WCAG AA des jetons de couleur, dans les deux themes.
 *
 * Le test lit la SOURCE des jetons (src/index.css) : le bloc du theme clair
 * (`:root, [data-theme="light"]`), celui du theme sombre (`:root[data-theme="dark"],
 * [data-theme="dark"]`, memes noms) et les noms Tailwind (`@theme inline`, ex.
 * `--color-bg: var(--jeton-bg)`), resolus jusqu'a la valeur.
 * Aucun navigateur : les valeurs sont resolues ici (var(), #rgb, #rrggbb, rgb()).
 *
 * Chaque paire (texte, fond) listee ci-dessous est une paire UTILISEE par la bibliotheque de
 * composants (src/components/ui) ou la coquille (src/components/layout). Seuils WCAG 2.1 :
 * 4,5:1 pour le texte normal, 3:1 pour le grand texte et les elements graphiques (anneau de
 * focus, bordure de champ). Les etats desactives ne sont pas concernes (WCAG 1.4.3).
 */

const CSS = readFileSync(resolve(__dirname, '../../index.css'), 'utf-8');

function bloc(selecteur: RegExp): string {
  const m = selecteur.exec(CSS);
  if (!m) throw new Error(`Bloc introuvable dans index.css : ${selecteur}`);
  // Accolade fermante correspondante (les blocs de jetons ne contiennent pas d'accolades).
  const debut = m.index + m[0].length;
  const fin = CSS.indexOf('}', debut);
  return CSS.slice(debut, fin);
}

function proprietes(source: string): Map<string, string> {
  const out = new Map<string, string>();
  const sansCommentaires = source.replace(/\/\*[\s\S]*?\*\//g, '');
  for (const m of sansCommentaires.matchAll(/(--[a-z0-9-]+)\s*:\s*([^;]+);/gi)) {
    out.set(m[1], m[2].trim());
  }
  return out;
}

const NOMS = proprietes(bloc(/@theme inline(?:\s+static)?\s*\{/));
const CLAIR = new Map([...NOMS, ...proprietes(bloc(/:root,\s*\[data-theme="light"\]\s*\{/))]);
const SOMBRE = new Map([...NOMS, ...proprietes(bloc(/:root\[data-theme="dark"\],\s*\[data-theme="dark"\]\s*\{/))]);
const THEMES = { clair: CLAIR, sombre: SOMBRE } as const;

type Rgb = [number, number, number];

function couleur(jetons: Map<string, string>, nom: string, profondeur = 0): Rgb {
  const brut = jetons.get(nom);
  if (brut === undefined) throw new Error(`Jeton absent : ${nom}`);
  if (profondeur > 10) throw new Error(`Reference circulaire : ${nom}`);
  const ref = /^var\((--[a-z0-9-]+)\)$/i.exec(brut);
  if (ref) return couleur(jetons, ref[1], profondeur + 1);
  const hex = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(brut);
  if (hex) {
    const h = hex[1].length === 3 ? [...hex[1]].map((c) => c + c).join('') : hex[1];
    return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16)) as Rgb;
  }
  const rgb = /^rgb\(\s*(\d+)[\s,]+(\d+)[\s,]+(\d+)\s*\)$/i.exec(brut);
  if (rgb) return [Number(rgb[1]), Number(rgb[2]), Number(rgb[3])];
  throw new Error(`Valeur non opaque ou non reconnue pour ${nom} : ${brut}`);
}

function luminance([r, g, b]: Rgb): number {
  const lin = (c: number) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
}

function contraste(a: Rgb, b: Rgb): number {
  const [l1, l2] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (l1 + 0.05) / (l2 + 0.05);
}

/**
 * Couleur d'un fond : un jeton (`--color-bg`), ou un melange `--color-danger 10% --color-bg`
 * (teinte a 10 % posee sur le fond : classes `bg-danger/10` des ecrans).
 */
function fond(jetons: Map<string, string>, spec: string): Rgb {
  const m = /^(--[a-z0-9-]+)\s+(\d+)%\s+(--[a-z0-9-]+)$/i.exec(spec);
  if (!m) return couleur(jetons, spec);
  const [haut, part, bas] = [couleur(jetons, m[1]), Number(m[2]) / 100, couleur(jetons, m[3])];
  return [0, 1, 2].map((i) => Math.round(haut[i] * part + bas[i] * (1 - part))) as Rgb;
}

/** [texte, fond, seuil, usage] */
type Paire = [string, string, number, string];

const TEXTE = 4.5;
const GRAPHIQUE = 3;
const FONDS = ['--color-bg', '--color-bg-raised', '--color-bg-overlay'];
const ETATS = ['success', 'warning', 'danger', 'info'] as const;

const PAIRES: Paire[] = [
  // Texte courant, secondaire, d'appoint et titres sur le fond et les surfaces.
  ...FONDS.flatMap((f): Paire[] => [
    ['--color-fg', f, TEXTE, 'texte'],
    ['--color-fg-muted', f, TEXTE, 'texte secondaire'],
    ['--color-fg-subtle', f, TEXTE, 'texte d appoint (aides, libelles discrets)'],
    ['--color-heading', f, TEXTE, 'titres'],
    // Or : lien, element actif ; etats en texte (messages, erreurs de champ).
    ['--color-accent', f, TEXTE, 'or en texte (lien, actif)'],
    ...ETATS.map((e): Paire => [`--color-${e}`, f, TEXTE, `texte d etat ${e}`]),
    // Elements graphiques : anneau de focus, contour des champs.
    ['--color-focus', f, GRAPHIQUE, 'anneau de focus'],
    ['--color-border-input', f, GRAPHIQUE, 'contour d un champ (WCAG 1.4.11)'],
  ]),
  // Boutons pleins : texte sur l'or, sur l'or survole, sur le rouge.
  ['--color-accent-fg', '--color-accent', TEXTE, 'bouton principal'],
  ['--color-accent-fg', '--color-accent-hover', TEXTE, 'bouton principal survole'],
  ['--color-bg', '--color-accent', TEXTE, 'ecrans existants : text-bg sur bg-accent'],
  ['--color-danger-fg', '--color-danger', TEXTE, 'bouton danger'],
  ['--color-danger-fg', '--color-danger-hover', TEXTE, 'bouton danger survole'],
  // Badges et messages : texte d'etat et texte courant sur la teinte douce de l'etat.
  ...ETATS.flatMap((e): Paire[] => [
    [`--color-${e}`, `--color-${e}-soft`, TEXTE, `badge ${e}`],
    ['--color-fg', `--color-${e}-soft`, TEXTE, `message ${e}`],
    [`--color-${e}-fg`, `--color-${e}`, TEXTE, `pastille pleine ${e}`],
    // Ecrans existants : `text-<etat>` sur `bg-<etat>/10`.
    [`--color-${e}`, `--color-${e} 10% --color-bg`, TEXTE, `ecrans existants : bg-${e}/10`],
    [`--color-${e}`, `--color-${e} 10% --color-bg-raised`, TEXTE, `ecrans existants : bg-${e}/10 sur carte`],
  ]),
  ['--color-accent', '--color-accent-soft', TEXTE, 'badge or (actif discret)'],
  ['--color-fg', '--color-accent-soft', TEXTE, 'texte sur selection'],
  ['--color-accent', '--color-accent 10% --color-bg', TEXTE, 'ecrans existants : bg-accent/10'],
  // Menu lateral marine.
  ['--color-nav-fg', '--color-nav-bg', TEXTE, 'menu : entree'],
  ['--color-nav-fg-hi', '--color-nav-bg-hover', TEXTE, 'menu : entree survolee'],
  ['--color-nav-active', '--color-nav-bg-active', TEXTE, 'menu : entree active (or)'],
  ['--color-nav-active', '--color-nav-bg', TEXTE, 'menu : titre de groupe actif'],
  ['--color-focus-inverse', '--color-nav-bg', GRAPHIQUE, 'menu : anneau de focus'],
];

describe('Contraste WCAG AA des jetons', () => {
  for (const [theme, jetons] of Object.entries(THEMES)) {
    describe(`theme ${theme}`, () => {
      it.each(PAIRES)('%s sur %s (seuil %s : %s)', (texte, arriere, seuil) => {
        const ratio = contraste(couleur(jetons, texte), fond(jetons, arriere));
        expect(ratio, `${texte} sur ${arriere} en ${theme}`).toBeGreaterThanOrEqual(seuil);
      });
    });
  }

  it('le calcul de contraste est juste (noir sur blanc = 21, blanc sur blanc = 1)', () => {
    expect(contraste([0, 0, 0], [255, 255, 255])).toBeCloseTo(21, 5);
    expect(contraste([255, 255, 255], [255, 255, 255])).toBeCloseTo(1, 5);
    expect(contraste([118, 118, 118], [255, 255, 255])).toBeGreaterThanOrEqual(4.5);
  });
});
