import { useEffect, useState } from 'react';
import { useReducedMotion } from '../../hooks/useReducedMotion.js';
import { HERO_TIMING } from '../../config/motion.js';

/**
 * v2 Flagship — Kanban LIVE : carte "Création ATLAS TECH" glisse
 * Nouveau → En cours (t=2.5s) puis En cours → Clôturé (t=4.0s) avec
 * sceau or "Validé" stamp bounce. Loop 8s.
 *
 * Si reduced-motion : carte fixée à l'état final "Clôturé".
 */
export function KanbanLiving() {
  const reduced = useReducedMotion();
  const [stage, setStage] = useState(reduced ? 2 : 0); // 0=Nouveau, 1=En cours, 2=Clôturé

  useEffect(() => {
    if (reduced) return undefined;
    let t1, t2, t3;
    const loop = () => {
      setStage(0);
      t1 = setTimeout(() => setStage(1), HERO_TIMING.KANBAN_FIRST_MOVE * 1000);
      t2 = setTimeout(() => setStage(2), HERO_TIMING.KANBAN_SECOND_MOVE * 1000);
      t3 = setTimeout(loop, HERO_TIMING.KANBAN_LOOP_MS);
    };
    loop();
    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
      clearTimeout(t3);
    };
  }, [reduced]);

  return (
    <div className="kanban-live" data-testid="kanban-living" aria-label="Démo : carte qui progresse Nouveau → En cours → Clôturé">
      <div className="kanban-col">
        <div className="kanban-title"><span className="status new" /> Nouveau</div>
        <div className={`kanban-card live ${stage === 0 ? 'is-here' : 'is-gone'}`}>
          <div className="kanban-tag tag-blue">SARL AU</div>
          <div className="kanban-name">Création — ATLAS TECH</div>
          <div className="kanban-meta">Étape 3/9 · IA génération</div>
        </div>
        <div className="kanban-card">
          <div className="kanban-tag tag-purple">Modification</div>
          <div className="kanban-name">Cession parts — BMH</div>
        </div>
      </div>
      <div className="kanban-col">
        <div className="kanban-title"><span className="status progress" /> En cours</div>
        <div className={`kanban-card live ${stage === 1 ? 'is-here' : 'is-gone'}`}>
          <div className="kanban-tag tag-blue">SARL AU</div>
          <div className="kanban-name">Création — ATLAS TECH</div>
          <div className="kanban-meta">Étape 7/9 · PV en attente</div>
        </div>
        <div className="kanban-card">
          <div className="kanban-tag tag-amber">Dissolution</div>
          <div className="kanban-name">SARL CASA NÉGOCE</div>
        </div>
      </div>
      <div className="kanban-col">
        <div className="kanban-title"><span className="status done" /> Clôturé</div>
        <div className={`kanban-card live ${stage === 2 ? 'is-here' : 'is-gone'}`}>
          <div className="kanban-tag tag-green">Terminé</div>
          <div className="kanban-name">Création — ATLAS TECH</div>
          <div className="kanban-meta">Validé · 9/9 étapes</div>
          {stage === 2 && <div className="seal-validated" aria-hidden="true">✓</div>}
        </div>
        <div className="kanban-card">
          <div className="kanban-tag tag-green">Terminé</div>
          <div className="kanban-name">SAFI HOLDING</div>
        </div>
      </div>
    </div>
  );
}
