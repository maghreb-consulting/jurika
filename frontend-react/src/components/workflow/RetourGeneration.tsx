import { InfoBulle } from '../ui/Aide';
import type { DonneeManquante, DonneeNommee } from '../../services/workflowDocumentService';

/**
 * Lot L3 (regle des variables) : ce que la generation dit a l'employe.
 *
 * - refus : une donnee INTERNE manque ; elle est nommee (libelle du champ), avec la
 *   phrase de l'acte ou elle s'imprime. Rien n'est produit.
 * - donnees a obtenir : une donnee EXTERNE (attendue d'un organisme : RC, ICE, IF...)
 *   manque ; l'acte est produit avec le marqueur « À OBTENIR », la plateforme la
 *   reclame, et l'acte se regenere quand elle arrive.
 */
export function RetourGeneration({
  erreur,
  refus = [],
  aObtenir = [],
}: {
  erreur?: string | null;
  refus?: DonneeManquante[];
  aObtenir?: DonneeNommee[];
}) {
  if (!erreur && refus.length === 0 && aObtenir.length === 0) return null;
  return (
    <div className="space-y-2">
      {refus.length > 0 ? (
        <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-fg">
          <p className="flex items-center gap-1 font-medium text-danger">
            Document non généré : des données manquent
            <InfoBulle
              libelle="Pourquoi le document n’est-il pas généré ?"
              texte="Une donnée que le cabinet doit fournir manque (une donnée « interne ») : la plateforme ne produit jamais un acte avec un blanc ou une valeur inventée. Complétez-la à l’étape indiquée, puis relancez la génération."
            />
          </p>
          <ul className="mt-1 list-disc space-y-1 pl-5">
            {refus.map((d) => (
              <li key={d.variable}>
                <strong>{d.libelle}</strong>
                {d.endroit && (
                  <span className="block text-xs text-fg-subtle">Dans la phrase : « {d.endroit} »</span>
                )}
              </li>
            ))}
          </ul>
        </div>
      ) : (
        erreur && (
          <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger">
            {erreur}
          </p>
        )
      )}
      {aObtenir.length > 0 && (
        <div role="status" className="rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm text-fg">
          <p className="flex items-center gap-1 font-medium">
            Données à obtenir d’un organisme
            <InfoBulle
              libelle="Que sont les données à obtenir ?"
              texte="Des données qu’un organisme délivre (registre du commerce, ICE, identifiant fiscal, taxe professionnelle, CNSS…). L’acte est produit avec la mention « À OBTENIR » à leur place ; la plateforme les réclame, et l’acte est régénéré quand elles sont saisies."
            />
          </p>
          <ul className="mt-1 list-disc pl-5">
            {aObtenir.map((d) => (
              <li key={d.variable}>{d.libelle}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
