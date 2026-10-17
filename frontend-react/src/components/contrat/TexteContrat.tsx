import type { JSX, ReactNode } from 'react';

/**
 * Lot L7 (E6) : affichage du texte du contrat d'abonnement (Markdown du modele valide) :
 * titres # et ##, tableaux, paragraphes (lignes jointes), gras et italique. Rendu en elements
 * React (aucun HTML injecte).
 */

function enLigne(texte: string, cle: string): ReactNode[] {
  const morceaux: ReactNode[] = [];
  const motif = /\*\*([^*]+)\*\*|\*([^*]+)\*/g;
  let dernier = 0;
  let m: RegExpExecArray | null;
  let i = 0;
  while ((m = motif.exec(texte)) !== null) {
    if (m.index > dernier) morceaux.push(texte.slice(dernier, m.index));
    if (m[1] !== undefined) morceaux.push(<strong key={`${cle}-b${i}`}>{m[1]}</strong>);
    else morceaux.push(<em key={`${cle}-i${i}`}>{m[2]}</em>);
    dernier = m.index + m[0].length;
    i += 1;
  }
  if (dernier < texte.length) morceaux.push(texte.slice(dernier));
  return morceaux;
}

function cellules(ligne: string): string[] {
  const t = ligne.trim();
  const coeur = t.slice(1, t.endsWith('|') ? -1 : undefined);
  return coeur.split('|').map((c) => c.trim());
}

export function TexteContrat({ texte }: { texte: string }) {
  const lignes = texte.split('\n');
  const blocs: JSX.Element[] = [];
  let paragraphe: string[] = [];
  let i = 0;

  const vider = () => {
    if (paragraphe.length > 0) {
      const k = `p${blocs.length}`;
      blocs.push(
        <p key={k} className="mb-3 leading-relaxed">
          {enLigne(paragraphe.join(' '), k)}
        </p>,
      );
      paragraphe = [];
    }
  };

  while (i < lignes.length) {
    const l = lignes[i];
    if (l.trim() === '') {
      vider();
      i += 1;
      continue;
    }
    if (l.startsWith('|')) {
      vider();
      const table: string[] = [];
      while (i < lignes.length && lignes[i].startsWith('|')) {
        table.push(lignes[i]);
        i += 1;
      }
      const rangees = table.filter((r) => !/^\|[\s:|-]+\|$/.test(r.trim())).map(cellules);
      const [entete, ...corps] = rangees;
      const k = `t${blocs.length}`;
      blocs.push(
        <div key={k} className="mb-4 overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr>
                {entete.map((c, j) => (
                  <th key={j} scope="col" className="border border-border bg-bg-overlay px-2 py-1 text-left font-semibold text-fg">
                    {enLigne(c, `${k}h${j}`)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {corps.map((r, n) => (
                <tr key={n}>
                  {r.map((c, j) => (
                    <td key={j} className="border border-border px-2 py-1 align-top">
                      {enLigne(c, `${k}r${n}c${j}`)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>,
      );
      continue;
    }
    if (l.startsWith('## ')) {
      vider();
      blocs.push(
        <h3 key={`h${blocs.length}`} className="mb-2 mt-4 text-base font-semibold text-heading">
          {enLigne(l.slice(3), `h${blocs.length}`)}
        </h3>,
      );
    } else if (l.startsWith('# ')) {
      vider();
      blocs.push(
        <h2 key={`h${blocs.length}`} className="mb-3 mt-6 border-b border-border pb-1 text-lg font-semibold text-heading">
          {enLigne(l.slice(2), `h${blocs.length}`)}
        </h2>,
      );
    } else {
      paragraphe.push(l.trim());
    }
    i += 1;
  }
  vider();
  return <div className="text-sm text-fg">{blocs}</div>;
}
