import { analyserContrat } from './analyseContrat';

/**
 * Lot L7 (E3, E9) : rendu du texte du contrat d'abonnement, repris TEL QUEL de la version 1
 * validee (~/docs/CONTRAT_ABONNEMENT_JURIKA_V1.md, pieces B, C et D, copiees octet pour octet
 * dans contrat-abonnement-conditions.md). Le texte n'est jamais reecrit ici : ce module ne fait
 * que le mettre en page.
 *
 * Syntaxe reconnue (celle du document source, rien de plus) :
 *  - "# " titre d'une piece, "## " titre d'un article ;
 *  - paragraphes separes par une ligne vide, lignes jointes par une espace ;
 *  - liste numerotee "1. " (lignes de suite indentees) ;
 *  - tableau "| a | b |" (premiere ligne : en-tete, deuxieme : separateur) ;
 *  - "**gras**".
 */

export function TexteEnLigne({ texte }: { texte: string }) {
  const morceaux = texte.split(/(\*\*[^*]+\*\*)/);
  return (
    <>
      {morceaux.map((m, i) =>
        /^\*\*[^*]+\*\*$/.test(m) ? <strong key={i}>{m.slice(2, -2)}</strong> : <span key={i}>{m}</span>,
      )}
    </>
  );
}

export function TexteContrat({ source }: { source: string }) {
  const blocs = analyserContrat(source);
  return (
    <>
      {blocs.map((b, i) => {
        switch (b.type) {
          case 'piece':
            return <h2 key={i}>{b.texte}</h2>;
          case 'article':
            return <h3 key={i}>{b.texte}</h3>;
          case 'paragraphe':
            return <p key={i}><TexteEnLigne texte={b.texte} /></p>;
          case 'liste':
            return (
              <ol key={i} className="my-3 list-decimal pl-6">
                {b.elements.map((e, j) => <li key={j}><TexteEnLigne texte={e} /></li>)}
              </ol>
            );
          case 'tableau':
            return (
              <div key={i} className="my-4 overflow-x-auto">
                <table className="w-full border-collapse text-left text-sm">
                  <thead>
                    <tr>
                      {b.entete.map((c, j) => (
                        <th key={j} className="border-b border-border px-2 py-1 font-semibold text-fg">
                          <TexteEnLigne texte={c} />
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {b.lignes.map((l, j) => (
                      <tr key={j}>
                        {l.map((c, k) => (
                          <td key={k} className="border-b border-border px-2 py-1 align-top">
                            <TexteEnLigne texte={c} />
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            );
          default:
            return null;
        }
      })}
    </>
  );
}
