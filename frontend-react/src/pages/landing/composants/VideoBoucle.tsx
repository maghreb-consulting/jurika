import { useEffect, useRef, useState } from 'react';
import { Pause, Play } from 'lucide-react';
import { cn } from '../../../lib/utils';
import { useMouvementReduit } from '../../../lib/mouvement';
import { DIMENSIONS_VIDEO, VIDEOS, cheminVideo, type NomVideo } from '../videos';

/**
 * Lot L6b (E10, V4, demande du 2026-10-16) : courte video en boucle de la vitrine, dans un
 * cadre d'ecran (meme cadre que les captures).
 *
 * - Deux couches dans la meme case : l'image fixe (poster WebP, <img> aux dimensions
 *   declarees) et, par-dessus, la <video> muette, en boucle, `playsinline`, `preload="none"`.
 *   La place est reservee avant tout chargement (aucun decalage de la mise en page) ;
 *   l'image fixe est l'element affiche au chargement (LCP de l'accroche), la video la
 *   recouvre en fondu (opacite) quand sa lecture a commence.
 * - Lecture seulement quand la video est a l'ecran (IntersectionObserver), pause hors ecran ;
 *   rien n'est telecharge avant (aucun `autoplay` tant qu'elle n'est pas visible) ; dans
 *   l'accroche (`apresChargement`), pas avant la fin du chargement de la page. Sans
 *   IntersectionObserver : aucune lecture automatique (image fixe et bouton).
 * - Bouton de pause et de lecture toujours present, dans la barre du cadre (WCAG 2.2.2 : un
 *   mouvement automatique de plus de 5 secondes doit pouvoir etre arrete). Un arret demande
 *   par le visiteur est respecte : la video ne repart pas d'elle-meme.
 * - "Reduire les animations" : l'image fixe seule, avec un bouton "Lire la video" ; la video
 *   ne demarre qu'a la demande.
 * - Equivalent textuel : aria-label de la video et de l'image (videos.ts), legende visible.
 */
export function VideoBoucle({
  nom,
  apresChargement = false,
  prioritaire = false,
  actif = true,
  className,
}: {
  nom: NomVideo;
  /** Recit : seul l'ecran actif de la pile lit sa video (les autres sont caches). */
  actif?: boolean;
  /** Accroche : la lecture attend la fin du chargement de la page. */
  apresChargement?: boolean;
  /** Image fixe chargee d'emblee, priorite haute (accroche). */
  prioritaire?: boolean;
  className?: string;
}) {
  const video = VIDEOS[nom];
  const reduit = useMouvementReduit();
  const boite = useRef<HTMLDivElement>(null);
  const lecteur = useRef<HTMLVideoElement>(null);
  const [visible, setVisible] = useState(false);
  const [pret, setPret] = useState(() => !apresChargement || document.readyState === 'complete');
  // null : pas de choix du visiteur ; true : lecture demandee ; false : pause demandee.
  const [choix, setChoix] = useState<boolean | null>(null);
  const [enLecture, setEnLecture] = useState(false);

  useEffect(() => {
    if (pret) return undefined;
    const surCharge = () => setPret(true);
    // Chargement termine entre le rendu et cet effet : l'evenement est deja passe.
    if (document.readyState === 'complete') {
      const t = window.setTimeout(surCharge, 0);
      return () => window.clearTimeout(t);
    }
    window.addEventListener('load', surCharge, { once: true });
    return () => window.removeEventListener('load', surCharge);
  }, [pret]);

  useEffect(() => {
    const el = boite.current;
    if (!el || reduit) return undefined;
    // Sans IntersectionObserver (vieux navigateur), aucune lecture automatique : image fixe et
    // bouton "Lire la video".
    if (typeof window.IntersectionObserver !== 'function') return undefined;
    const obs = new window.IntersectionObserver((entrees) => setVisible(entrees.some((e) => e.isIntersecting)), { threshold: 0.25 });
    obs.observe(el);
    return () => obs.disconnect();
  }, [reduit]);

  // Lecture voulue : jamais apres une pause demandee ; apres une lecture demandee, tant que la
  // video est a l'ecran (toujours si les animations sont reduites : aucun observateur) ;
  // sinon, mouvement permis, des qu'elle est a l'ecran.
  const voulue = !actif || choix === false ? false : choix === true ? reduit || visible : !reduit && visible && pret;
  const montee = voulue || enLecture || choix !== null;

  useEffect(() => {
    const v = lecteur.current;
    if (!v) return;
    if (voulue) {
      try {
        const p = v.play?.();
        if (p && typeof p.catch === 'function') p.catch(() => setEnLecture(false));
      } catch {
        // Lecture impossible (navigateur sans video) : l'image fixe reste.
      }
    } else {
      try {
        v.pause?.();
      } catch {
        // Sans objet.
      }
    }
  }, [voulue, montee]);

  const libelleBouton = enLecture ? 'Mettre la vidéo en pause' : 'Lire la vidéo';
  const invitation = reduit && choix === null && !enLecture;
  // Le grand bouton disparait au clic : le focus passe au bouton de la barre (jamais perdu).
  const boutonBarre = useRef<HTMLButtonElement>(null);
  const focusApres = useRef(false);
  useEffect(() => {
    if (invitation || !focusApres.current) return;
    focusApres.current = false;
    boutonBarre.current?.focus();
  }, [invitation]);

  return (
    <figure className={cn('v-video', className)} data-video={nom} data-lecture={enLecture ? 'oui' : 'non'}>
      <div className="v-cadre overflow-hidden rounded-xl">
        <div className="v-cadre-barre flex h-9 items-center gap-1.5 pl-4 pr-1.5">
          <span className="flex min-w-0 flex-1 items-center gap-1.5" aria-hidden="true">
            <span className="v-point v-point-1" />
            <span className="v-point v-point-2" />
            <span className="v-point v-point-3" />
            <span className="ml-3 truncate text-xs font-medium text-fg-subtle">{video.ecran}</span>
          </span>
          {/* Pause et lecture, dans la barre du cadre : rien de l'ecran filme n'est masque ;
              taille fixe (aucun decalage quand le bouton change d'etat). */}
          {!invitation && (
            <button
              ref={boutonBarre}
              type="button"
              onClick={() => setChoix(!enLecture)}
              aria-label={`${libelleBouton} : ${video.legende}`}
              title={libelleBouton}
              className="v-video-bouton inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-fg-muted hover:bg-bg-raised hover:text-fg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus"
            >
              {enLecture ? <Pause className="h-4 w-4" aria-hidden="true" /> : <Play className="h-4 w-4" aria-hidden="true" />}
            </button>
          )}
        </div>
        <div ref={boite} className="v-video-pile relative">
          <img
            src={cheminVideo(nom, 'webp')}
            width={DIMENSIONS_VIDEO.largeur}
            height={DIMENSIONS_VIDEO.hauteur}
            loading={prioritaire ? 'eager' : 'lazy'}
            fetchPriority={prioritaire ? 'high' : undefined}
            decoding="async"
            alt={video.alt}
            className="v-video-media"
            draggable={false}
          />
          {montee && (
            <video
              ref={lecteur}
              className="v-video-media v-video-lecteur"
              data-actif={enLecture ? '' : undefined}
              width={DIMENSIONS_VIDEO.largeur}
              height={DIMENSIONS_VIDEO.hauteur}
              poster={cheminVideo(nom, 'webp')}
              muted
              loop
              playsInline
              autoPlay={voulue}
              preload="none"
              aria-label={video.alt}
              onPlaying={() => setEnLecture(true)}
              onPause={() => setEnLecture(false)}
            >
              <source src={cheminVideo(nom, 'mp4')} type="video/mp4" />
              <source src={cheminVideo(nom, 'webm')} type="video/webm" />
            </video>
          )}
          {/* Animations reduites, video jamais lancee : grand bouton au centre de l'image fixe. */}
          {invitation && (
            <button
              type="button"
              onClick={() => {
                focusApres.current = true;
                setChoix(true);
              }}
              aria-label={`${libelleBouton} : ${video.legende}`}
              title={libelleBouton}
              className="v-video-bouton v-video-bouton-lire absolute left-1/2 top-1/2 inline-flex h-12 -translate-x-1/2 -translate-y-1/2 items-center justify-center gap-2 rounded-full border border-border bg-bg-raised px-5 text-sm font-semibold text-fg shadow-card focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus"
            >
              <Play className="h-4 w-4" aria-hidden="true" />
              {libelleBouton}
            </button>
          )}
        </div>
      </div>
      <figcaption className="mt-2 text-center text-sm text-fg-subtle">{video.legende}</figcaption>
    </figure>
  );
}
