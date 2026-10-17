import { useRef, useState, type ComponentType } from 'react';
import { Pause, Play } from 'lucide-react';
import { Badge } from '../../../components/ui/Badge';
import { cn } from '../../../lib/utils';
import { DESSIN_SCENE, SCENES, type NomScene } from './scenes';
import { SceneCreation, SceneFrise, type ProprietesScene } from './ScenesTicket';
import { SceneJuri, SceneStatuts, SceneTheme } from './ScenesEcrans';
import { useDeroule, useEchelle, useLecture, useProche } from './useLecture';

/**
 * Lot L6c (E1, demande du 2026-10-17) : demonstration animee de la vitrine, dans un cadre
 * d'ecran (meme cadre que les captures).
 *
 * - La scene (scenes.ts) est dessinee a taille fixe puis mise a l'echelle du cadre (ratio
 *   16:10 reserve : aucun decalage de la mise en page).
 * - Elle joue quand elle est a l'ecran et l'onglet visible (useLecture), en boucle ; elle se
 *   met en pause hors ecran ; dans la pile du recit, seul l'ecran actif joue (`actif`).
 * - "Reduire les animations" (ou navigateur sans IntersectionObserver) : etat final, fixe.
 * - Non interactive : role="img" et la description de la scene ; contenu aria-hidden et
 *   inert (aucun focus possible, aucun clic) ; legende visible sous le cadre.
 * - Bouton de pause et de lecture dans la barre du cadre, hors de la scene (WCAG 2.2.2 : un
 *   mouvement automatique de plus de 5 secondes doit pouvoir etre arrete) ; absent quand la
 *   scene est fixe. Une pause demandee est respectee.
 * - Montage differe : la scene n'est construite qu'a l'approche de l'ecran (useProche) ;
 *   avant, seul le cadre (place reservee) est rendu.
 * - Aucune requete reseau, aucun magasin de l'application : donnees fixes (donnees.ts).
 */

const CONTENUS: Record<NomScene, ComponentType<ProprietesScene>> = {
  'creation-ticket': SceneCreation,
  'frise-demarches': SceneFrise,
  'statuts-apercu': SceneStatuts,
  'juri-aide': SceneJuri,
  'theme-clair-sombre': SceneTheme,
};

export function DemonstrationAnimee({ nom, actif = true, className }: { nom: NomScene; actif?: boolean; className?: string }) {
  const scene = SCENES[nom];
  const boite = useRef<HTMLDivElement>(null);
  useEchelle(boite, DESSIN_SCENE.largeur);
  const lecture = useLecture(boite);
  const proche = useProche(boite);
  const [pauseDemandee, setPauseDemandee] = useState(false);
  const joue = lecture.joue && actif && !pauseDemandee;
  const etape = useDeroule(scene.durees, joue, lecture.statique, scene.etapeFinale);
  const Contenu = CONTENUS[nom];
  const libelle = pauseDemandee ? 'Reprendre la démonstration' : 'Mettre la démonstration en pause';

  return (
    <figure className={cn('v-demo', className)} data-demo={nom} data-lecture={joue ? 'oui' : 'non'} data-etape-scene={etape} data-montee={proche ? 'oui' : 'non'}>
      <div className="v-cadre overflow-hidden rounded-xl">
        <div className="v-cadre-barre flex h-9 items-center gap-1.5 pl-4 pr-1.5">
          <span className="flex min-w-0 flex-1 items-center gap-1.5" aria-hidden="true">
            <span className="v-point v-point-1" />
            <span className="v-point v-point-2" />
            <span className="v-point v-point-3" />
            <span className="ml-3 truncate text-xs font-medium text-fg-subtle">{scene.ecran}</span>
          </span>
          <Badge variant="neutral" className="shrink-0">
            Données fictives
          </Badge>
          {!lecture.statique && (
            <button
              type="button"
              onClick={() => setPauseDemandee((p) => !p)}
              aria-label={`${libelle} : ${scene.legende}`}
              aria-pressed={pauseDemandee}
              title={libelle}
              className="v-demo-bouton inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-fg-muted hover:bg-bg-raised hover:text-fg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus"
            >
              {pauseDemandee ? <Play className="h-4 w-4" aria-hidden="true" /> : <Pause className="h-4 w-4" aria-hidden="true" />}
            </button>
          )}
        </div>
        <div ref={boite} className="v-demo-boite" role="img" aria-label={scene.description}>
          <div className="v-demo-toile" aria-hidden="true" inert data-pause={joue ? undefined : ''}>
            {proche && <Contenu etape={etape} joue={joue} statique={lecture.statique} />}
          </div>
        </div>
      </div>
      <figcaption className="mt-2 text-center text-sm text-fg-subtle">{scene.legende}</figcaption>
    </figure>
  );
}
