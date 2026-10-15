/**
 * Lot L7 (E3) : decoupe du texte du contrat d'abonnement en blocs (voir ContratTexte.tsx).
 * Syntaxe reconnue : celle du document source, rien de plus.
 */

export type Bloc =
  | { type: 'piece'; texte: string }
  | { type: 'article'; texte: string }
  | { type: 'paragraphe'; texte: string }
  | { type: 'liste'; elements: string[] }
  | { type: 'tableau'; entete: string[]; lignes: string[][] };

function cellules(ligne: string): string[] {
  return ligne.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map((c) => c.trim());
}

export function analyserContrat(source: string): Bloc[] {
  const blocs: Bloc[] = [];
  const groupes = source.replace(/\r\n/g, '\n').split(/\n\s*\n/);
  for (const groupe of groupes) {
    const lignes = groupe.split('\n').filter((l) => l.trim() !== '');
    if (lignes.length === 0) continue;
    const premiere = lignes[0];
    if (premiere.startsWith('## ')) {
      blocs.push({ type: 'article', texte: premiere.slice(3).trim() });
    } else if (premiere.startsWith('# ')) {
      blocs.push({ type: 'piece', texte: premiere.slice(2).trim() });
    } else if (premiere.startsWith('|')) {
      const [entete, , ...corps] = lignes;
      blocs.push({ type: 'tableau', entete: cellules(entete), lignes: corps.map(cellules) });
    } else if (/^\d+\.\s/.test(premiere)) {
      const elements: string[] = [];
      for (const l of lignes) {
        if (/^\d+\.\s/.test(l)) elements.push(l.replace(/^\d+\.\s+/, '').trim());
        else elements[elements.length - 1] += ' ' + l.trim();
      }
      blocs.push({ type: 'liste', elements });
    } else {
      blocs.push({ type: 'paragraphe', texte: lignes.map((l) => l.trim()).join(' ') });
    }
  }
  return blocs;
}
