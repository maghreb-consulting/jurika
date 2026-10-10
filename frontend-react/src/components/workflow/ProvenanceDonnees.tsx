import { InfoBulle } from '../ui/Aide';
import type { VariableDuMagasin } from '../../services/workflow.service';
import { libelleProvenance, ouCorriger } from './provenanceLibelles';

/**
 * Lot L3 (RG-VAR-02, RG-VAR-03) : une donnee connue n'est jamais redemandee ; elle
 * s'affiche en lecture, avec sa provenance et l'endroit unique ou la corriger.
 */
export function ProvenanceDonnees({ variables }: { variables: VariableDuMagasin[] }) {
  const simples = variables.filter((v) => !v.boucle && v.valeur);
  if (simples.length === 0) return null;
  return (
    <section aria-labelledby="donnees-connues" className="rounded-xl border border-border bg-bg-raised p-4">
      <h4 id="donnees-connues" className="flex items-center gap-1 text-sm font-semibold text-fg">
        Données connues et leur provenance
        <InfoBulle
          libelle="D’où viennent ces données ?"
          texte="Chaque donnée est saisie une seule fois : elle est reprise partout ailleurs. Sa provenance est indiquée ; pour la corriger, faites-le à l’endroit indiqué, et la correction vaut pour toutes les générations suivantes."
        />
      </h4>
      <table className="mt-2 w-full text-xs">
        <caption className="sr-only">Données connues du ticket et leur provenance</caption>
        <thead>
          <tr className="text-left text-fg-subtle">
            <th className="py-1 pr-2 font-medium">Donnée</th>
            <th className="py-1 pr-2 font-medium">Valeur</th>
            <th className="py-1 font-medium">Provenance</th>
          </tr>
        </thead>
        <tbody>
          {simples.map((v) => (
            <tr key={v.variable} className="border-t border-border">
              <td className="py-1 pr-2 font-mono text-[11px] text-fg-muted">{v.variable}</td>
              <td className="py-1 pr-2 text-fg">{v.valeur}</td>
              <td className="py-1 text-fg-muted" title={ouCorriger(v)}>{libelleProvenance(v)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
