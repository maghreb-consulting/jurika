import { TexteAide } from '../../components/ui/Aide';
import type { EtapeParcours } from './aidesParcours';

const MANQUANTS_AFFICHES = 5;

/**
 * Aide en tete d'etape d'un parcours (CDC 15.4) : ce qu'il faut faire, ce qui manque,
 * ce qui vient ensuite. Masquable, puis reaffichable (TexteAide).
 *
 * `manquants` : donnees encore manquantes, calculees par la page ; quand la liste est
 * fournie et non vide, elle remplace le texte general de l'aide.
 */
export function AideEtape({
  parcours,
  etapes,
  etape,
  manquants,
}: {
  parcours: string;
  etapes: EtapeParcours[];
  etape: number;
  manquants?: string[];
}) {
  const definition = etapes.find((e) => e.number === etape);
  if (!definition) return null;
  const { label: libelle, aide } = definition;
  const liste = (manquants ?? []).filter((m) => m.trim().length > 0);
  return (
    <div className="mb-4" data-testid={`aide-etape-${parcours}`}>
      <TexteAide cle={`parcours.${parcours}.etape${etape}`} titre={`Aide de l’étape « ${libelle} »`}>
        <ul className="space-y-1">
          <li>
            <span className="font-medium text-fg">À faire : </span>
            {aide.faire}
          </li>
          <li>
            <span className="font-medium text-fg">Ce qui manque : </span>
            {liste.length > 0 ? (
              <>
                {liste.length === 1 ? 'il reste une donnée à compléter.' : `il reste ${liste.length} données à compléter.`}
                <ul className="ml-4 mt-0.5 list-disc">
                  {liste.slice(0, MANQUANTS_AFFICHES).map((m) => (
                    <li key={m}>{m}</li>
                  ))}
                  {liste.length > MANQUANTS_AFFICHES && (
                    <li>et {liste.length - MANQUANTS_AFFICHES} autre{liste.length - MANQUANTS_AFFICHES > 1 ? 's' : ''}.</li>
                  )}
                </ul>
              </>
            ) : (
              aide.manque
            )}
          </li>
          <li>
            <span className="font-medium text-fg">Ensuite : </span>
            {aide.ensuite}
          </li>
        </ul>
      </TexteAide>
    </div>
  );
}
