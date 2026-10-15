import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { ChevronLeft, ChevronRight, MoveHorizontal } from 'lucide-react';
import { Button } from '../ui/Button';
import { mouvementReduit } from '../../lib/mouvement';
import { cn } from '../../lib/utils';

/**
 * Lot L6b (E7) : rangee de colonnes qui defile horizontalement QUAND elle ne tient pas.
 *
 * Le defilement ne se devine pas : des qu'une colonne depasse, une ligne le dit
 * ("Faites defiler...") et deux boutons font avancer ou reculer la rangee ; un degrade
 * marque le bord coupe. La zone de defilement est focalisable (fleches du clavier) et
 * nommee pour les lecteurs d'ecran. Quand tout tient, rien de cela ne s'affiche.
 */
export function RangeeDefilante({ libelle, children, className }: { libelle: string; children: ReactNode; className?: string }) {
  const ref = useRef<HTMLDivElement | null>(null);
  const [etat, setEtat] = useState({ deborde: false, debut: true, fin: true });

  const mesurer = useCallback(() => {
    const el = ref.current;
    if (!el) return;
    const deborde = el.scrollWidth > el.clientWidth + 1;
    const debut = el.scrollLeft <= 1;
    const fin = el.scrollLeft + el.clientWidth >= el.scrollWidth - 1;
    setEtat((e) => (e.deborde === deborde && e.debut === debut && e.fin === fin ? e : { deborde, debut, fin }));
  }, []);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    mesurer();
    if (typeof ResizeObserver === 'undefined') return;
    const obs = new ResizeObserver(mesurer);
    obs.observe(el);
    if (el.firstElementChild) obs.observe(el.firstElementChild);
    return () => obs.disconnect();
  }, [mesurer]);

  function avancer(sens: 1 | -1) {
    const el = ref.current;
    if (!el) return;
    el.scrollBy({ left: sens * Math.round(el.clientWidth * 0.8), behavior: mouvementReduit() ? 'auto' : 'smooth' });
  }

  return (
    <div className={cn('relative', className)}>
      {etat.deborde && (
        <div className="mb-2 flex items-center justify-between gap-3 text-xs text-fg-muted" data-testid="rangee-deborde">
          <p className="flex items-center gap-1.5">
            <MoveHorizontal className="h-4 w-4" aria-hidden="true" />
            Toutes les colonnes ne tiennent pas : faites défiler la rangée vers la droite (ou utilisez les flèches).
          </p>
          <div className="flex shrink-0 gap-1">
            <Button type="button" size="sm" variant="secondary" iconOnly aria-label="Colonnes précédentes" disabled={etat.debut} onClick={() => avancer(-1)}>
              <ChevronLeft className="h-4 w-4" aria-hidden="true" />
            </Button>
            <Button type="button" size="sm" variant="secondary" iconOnly aria-label="Colonnes suivantes" disabled={etat.fin} onClick={() => avancer(1)}>
              <ChevronRight className="h-4 w-4" aria-hidden="true" />
            </Button>
          </div>
        </div>
      )}
      <div
        ref={ref}
        role="region"
        aria-label={libelle}
        tabIndex={etat.deborde ? 0 : undefined}
        onScroll={mesurer}
        className="overflow-x-auto overscroll-x-contain rounded-lg pb-2"
      >
        {children}
      </div>
      {etat.deborde && !etat.fin && (
        <div aria-hidden="true" className="pointer-events-none absolute bottom-2 right-0 top-10 w-8 bg-gradient-to-l from-bg to-transparent" />
      )}
    </div>
  );
}
