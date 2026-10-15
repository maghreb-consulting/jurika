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

/**
 * Lot L6b (E5, W2) : les etapes des parcours n'emploient plus de controle brut. Un bouton
 * passe par Button (il ressemble a un bouton), un champ par TextField / ZoneTexte / Select
 * (libelle, aide et erreur relies), une case par Case / Radio / Interrupteur, un en-tete de
 * section depliable par EnTeteDepliant. Avant W2 : 293 balises brutes dans pages/workflow et
 * components/workflow.
 *
 * Exceptions nommees (fichier, balise, nombre exact, raison) : un nombre qui change, ou une
 * exception devenue sans objet, fait echouer le test (aucune porte ouverte pour rien).
 * `input[type=file]` : le selecteur natif de fichiers, sans equivalent dans la bibliotheque ;
 * il reste dans un libelle mis en forme de bouton ou de zone de depot, et doit rester
 * atteignable au clavier (sr-only, jamais `hidden` : second test).
 */
const DOSSIERS_CONTROLES = ['pages/workflow', 'components/workflow'];
const CONTROLE_BRUT = /<(button|input|select|textarea)\b/g;
const FICHIER = 'selecteur de fichiers natif, dans un libelle bouton ou une zone de depot';
const EXCEPTIONS_CONTROLES: { fichier: string; balise: string; nombre: number; raison: string }[] = [
  { fichier: 'components/workflow/FriseEtapes.tsx', balise: 'button', nombre: 1, raison: 'pastille ronde d une etape de la frise (navigation, pas une action)' },
  { fichier: 'components/workflow/PiecesJointesPanel.tsx', balise: 'input[type=file]', nombre: 1, raison: FICHIER },
  { fichier: 'components/workflow/PublicitesSociete.tsx', balise: 'input[type=file]', nombre: 1, raison: FICHIER },
  { fichier: 'components/workflow/WorkflowPiecesPanel.tsx', balise: 'input[type=file]', nombre: 1, raison: FICHIER },
  { fichier: 'pages/workflow/import-steps/Step2ImportJuridique.tsx', balise: 'input[type=file]', nombre: 1, raison: FICHIER },
  { fichier: 'pages/workflow/import-steps/StepDepotPieces.tsx', balise: 'input[type=file]', nombre: 1, raison: FICHIER },
  { fichier: 'pages/workflow/steps/Step2Siege.tsx', balise: 'input[type=file]', nombre: 3, raison: FICHIER },
  { fichier: 'pages/workflow/steps/Step5Dirigeants.tsx', balise: 'input[type=file]', nombre: 3, raison: FICHIER },
  { fichier: 'pages/workflow/steps/Step6Associes.tsx', balise: 'input[type=file]', nombre: 3, raison: FICHIER },
  { fichier: 'pages/workflow/steps/Step8PiecesJointes.tsx', balise: 'input[type=file]', nombre: 4, raison: FICHIER },
];

/**
 * Le texte sans ses commentaires, ligne par ligne : commentaires JSX et blocs ouverts en debut
 * de ligne blanchis, lignes `//` videes. (Un "/*" au milieu d'une ligne n'ouvre rien : il
 * apparait dans `accept="image/*"`.)
 */
function sansCommentaires(texte: string): string[] {
  const blanchir = (c: string) => c.replace(/[^\n]/g, ' ');
  return texte
    .replace(/\{\/\*[\s\S]*?\*\/\}/g, blanchir)
    .replace(/^[ \t]*\/\*[\s\S]*?\*\//gm, blanchir)
    .split('\n')
    .map((l) => (/^\s*\/\//.test(l) ? '' : l));
}

interface Trouve {
  fichier: string;
  balise: string;
  ligne: string;
  cache: boolean;
}

function controlesBruts(): Trouve[] {
  const trouves: Trouve[] = [];
  for (const d of DOSSIERS_CONTROLES) {
    for (const f of fichiers(join(RACINE, d))) {
      const rel = f.slice(RACINE.length + 1);
      const lignes = sansCommentaires(readFileSync(f, 'utf-8'));
      lignes.forEach((l, i) => {
        for (const m of l.matchAll(CONTROLE_BRUT)) {
          let balise: string = m[1];
          let cache = false;
          if (balise === 'input') {
            // la balise ouvrante d'un <input> se ferme par "/>" (quelques lignes au plus)
            const suite = [l.slice(m.index), ...lignes.slice(i + 1, i + 15)].join('\n');
            const ouvrante = suite.slice(0, suite.indexOf('/>'));
            if (/type="file"/.test(ouvrante)) {
              balise = 'input[type=file]';
              cache = /className="hidden"/.test(ouvrante);
            }
          }
          trouves.push({ fichier: rel, balise, ligne: `${rel}:${i + 1}: ${l.trim()}`, cache });
        }
      });
    }
  }
  return trouves;
}

describe('Parcours : aucun controle brut', () => {
  it('ni <button>, ni <input>, ni <select>, ni <textarea> hors exceptions nommees', () => {
    const parCle = new Map<string, string[]>();
    for (const t of controlesBruts()) {
      const cle = `${t.fichier}|${t.balise}`;
      parCle.set(cle, [...(parCle.get(cle) ?? []), t.ligne]);
    }
    const fautes: string[] = [];
    for (const [cle, lignes] of parCle) {
      const [fichier, balise] = cle.split('|');
      const ex = EXCEPTIONS_CONTROLES.find((e) => e.fichier === fichier && e.balise === balise);
      if (!ex || lignes.length !== ex.nombre) fautes.push(...lignes.map((l) => `<${balise}> ${l}`));
    }
    for (const ex of EXCEPTIONS_CONTROLES) {
      if (!parCle.has(`${ex.fichier}|${ex.balise}`)) fautes.push(`exception sans objet : ${ex.fichier} <${ex.balise}> (${ex.raison})`);
    }
    expect(fautes).toEqual([]);
  });

  it('un selecteur de fichiers reste atteignable au clavier (sr-only, jamais hidden)', () => {
    expect(controlesBruts().filter((t) => t.cache).map((t) => t.ligne)).toEqual([]);
  });
});
