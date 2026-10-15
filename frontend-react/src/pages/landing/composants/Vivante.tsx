import { useEffect, useRef, type ReactNode, type RefObject } from 'react';
import { cn } from '../../../lib/utils';
import { useMouvementReduit } from '../../../lib/mouvement';

/**
 * Lot L6b (E10, V3) : captures "vivantes" de la vitrine.
 *
 * Au survol d'une souris (media `hover: hover` et `pointer: fine`), la capture s'incline
 * legerement en 3D (6 degres au plus), un reflet suit le pointeur et l'ombre portee se
 * deplace a l'oppose, comme sous une lumiere tenue par le pointeur. Tout passe par des
 * variables CSS lues par `transform` et `opacity` (vitrine.css) : aucune propriete de boite
 * ne change, rien ne se decale. Une mise a jour par image (requestAnimationFrame).
 * Ecran tactile, ou "reduire les animations" : rien n'est branche, la capture reste droite.
 */

const INCLINAISON_X = 8; // degres pour toute la hauteur (4 au plus de chaque cote)
const INCLINAISON_Y = 12; // degres pour toute la largeur (6 au plus de chaque cote)

function arrondi(n: number): number {
  return Number(n.toFixed(2));
}

function useInclinaison(ref: RefObject<HTMLElement | null>) {
  const reduit = useMouvementReduit();

  useEffect(() => {
    const el = ref.current;
    if (!el || reduit) return undefined;
    if (typeof window.matchMedia !== 'function' || !window.matchMedia('(hover: hover) and (pointer: fine)').matches) {
      return undefined;
    }
    const cibles = [el, el.parentElement].filter((x): x is HTMLElement => !!x);
    let image = 0;
    let point: { x: number; y: number } | null = null;

    const poser = (vars: Record<string, string>) => {
      for (const c of cibles) for (const [k, v] of Object.entries(vars)) c.style.setProperty(k, v);
    };
    const appliquer = () => {
      image = 0;
      if (!point) return;
      const r = el.getBoundingClientRect();
      if (r.width <= 0 || r.height <= 0) return;
      const px = Math.min(0.5, Math.max(-0.5, (point.x - r.left) / r.width - 0.5));
      const py = Math.min(0.5, Math.max(-0.5, (point.y - r.top) / r.height - 0.5));
      poser({
        '--rx': `${arrondi(-py * INCLINAISON_X)}deg`,
        '--ry': `${arrondi(px * INCLINAISON_Y)}deg`,
        '--px': String(arrondi(px)),
        '--py': String(arrondi(py)),
      });
    };
    const surMouvement = (e: PointerEvent) => {
      if (e.pointerType && e.pointerType !== 'mouse') return;
      point = { x: e.clientX, y: e.clientY };
      for (const c of cibles) c.setAttribute('data-survol', '');
      if (typeof window.requestAnimationFrame !== 'function') appliquer();
      else if (!image) image = window.requestAnimationFrame(appliquer);
    };
    const surSortie = () => {
      point = null;
      if (image) window.cancelAnimationFrame(image);
      image = 0;
      for (const c of cibles) c.removeAttribute('data-survol');
      poser({ '--rx': '0deg', '--ry': '0deg', '--px': '0', '--py': '0' });
    };
    el.addEventListener('pointermove', surMouvement);
    el.addEventListener('pointerleave', surSortie);
    return () => {
      el.removeEventListener('pointermove', surMouvement);
      el.removeEventListener('pointerleave', surSortie);
      surSortie();
    };
  }, [ref, reduit]);
}

/** Cadre vivant : ombre mobile (derriere), contenu incline, reflet (devant, sans pointeur). */
export function Vivante({ children, className, classeContenu }: { children: ReactNode; className?: string; classeContenu?: string }) {
  const ref = useRef<HTMLDivElement>(null);
  useInclinaison(ref);
  return (
    <div className={cn('v-vivante-cadre relative isolate', className)}>
      <span className="v-ombre" aria-hidden="true" />
      <div ref={ref} className={cn('v-vivante relative', classeContenu)}>
        {children}
        <span className="v-reflet" aria-hidden="true">
          <span className="v-reflet-tache" />
        </span>
      </div>
    </div>
  );
}
