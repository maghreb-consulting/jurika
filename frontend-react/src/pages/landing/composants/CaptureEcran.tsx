import { cn } from '../../../lib/utils';
import { CAPTURES, DIMENSIONS_CAPTURE, cheminCapture, sourcesCapture, type NomCapture } from '../captures';
import { Vivante } from './Vivante';

/**
 * Lot L6b (E10, V2) : capture de la plateforme dans un cadre d'ecran.
 *
 * - <img> avec srcset (800w, 1600w) et `sizes` fourni par l'appelant : le navigateur charge
 *   la largeur utile, rien de plus ;
 * - width et height declares, et ratio fixe 16:10 (aspect-ratio, object-fit) : la place est
 *   reservee avant le chargement, aucun decalage de la mise en page ;
 * - chargement differe (`loading="lazy"`) sauf l'image de l'accroche (`prioritaire` :
 *   chargement immediat, priorite haute) ; decodage asynchrone ;
 * - texte de remplacement descriptif (captures.ts) ;
 * - L6c : <picture>, AVIF d'abord puis WebP (le navigateur prend le premier format qu'il
 *   sait lire) ; l'<img> de repli porte le WebP, les dimensions et le texte de remplacement ;
 * - V3 : capture "vivante" par defaut (inclinaison, reflet et ombre au survol, voir
 *   Vivante.tsx) ; `vivante={false}` quand le cadre vivant est porte par un parent (pile
 *   d'ecrans du recit, bascule clair / sombre).
 */
export function CaptureEcran({
  nom,
  sizes,
  prioritaire = false,
  compacte = false,
  vivante = true,
  className,
}: {
  nom: NomCapture;
  sizes: string;
  prioritaire?: boolean;
  /** Cadre plus discret (captures cote a cote). */
  compacte?: boolean;
  vivante?: boolean;
  className?: string;
}) {
  const capture = CAPTURES[nom];
  const arrondi = compacte ? 'rounded-lg' : 'rounded-xl';
  const cadre = (
    <div className={cn('v-cadre overflow-hidden', arrondi, !vivante && className)}>
      <div className={cn('v-cadre-barre flex items-center gap-1.5', compacte ? 'h-7 px-3' : 'h-9 px-4')} aria-hidden="true">
        <span className="v-point v-point-1" />
        <span className="v-point v-point-2" />
        <span className="v-point v-point-3" />
        {!compacte && <span className="ml-3 truncate text-xs font-medium text-fg-subtle">{capture.ecran}</span>}
      </div>
      <picture className="block">
        <source type="image/avif" srcSet={sourcesCapture(nom, 'avif')} sizes={sizes} />
        <source type="image/webp" srcSet={sourcesCapture(nom, 'webp')} sizes={sizes} />
        <img
          src={cheminCapture(nom, 800)}
          srcSet={sourcesCapture(nom, 'webp')}
          sizes={sizes}
          width={DIMENSIONS_CAPTURE.largeur}
          height={DIMENSIONS_CAPTURE.hauteur}
          loading={prioritaire ? 'eager' : 'lazy'}
          fetchPriority={prioritaire ? 'high' : undefined}
          decoding="async"
          alt={capture.alt}
          className="v-capture"
          draggable={false}
        />
      </picture>
    </div>
  );
  if (!vivante) return cadre;
  return (
    <Vivante className={className} classeContenu={arrondi}>
      {cadre}
    </Vivante>
  );
}
