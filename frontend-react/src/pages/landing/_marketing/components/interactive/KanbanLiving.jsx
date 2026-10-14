import { useEffect, useState } from 'react';
import { useReducedMotion } from '../../hooks/useReducedMotion.js';
import { HERO_TIMING } from '../../config/motion.js';

/**
 * Kanban anime : la carte "Creation ATLAS TECH" passe de la collecte d'information au
 * deroulement de la demarche, puis a la cloture du dossier (statuts RG-TKT-01, lot L7 E3).
 * Boucle de 8 s. Si reduced-motion : carte figee a l'etat final.
 */
export function KanbanLiving() {
  const reduced = useReducedMotion();
  const [stage, setStage] = useState(reduced ? 2 : 0); // 0=collecte, 1=demarche, 2=cloture

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
    <div className="kanban-live" data-testid="kanban-living" aria-label="Illustration : un ticket passe de la collecte d’information à la clôture du dossier">
      <div className="kanban-col">
        <div className="kanban-title"><span className="status new" /> Collecte d’information</div>
        <div className={`kanban-card live ${stage === 0 ? 'is-here' : 'is-gone'}`}>
          <div className="kanban-tag tag-blue">SARL AU</div>
          <div className="kanban-name">Création — ATLAS TECH</div>
          <div className="kanban-meta">Saisie des associés</div>
        </div>
        <div className="kanban-card">
          <div className="kanban-tag tag-purple">Modification</div>
          <div className="kanban-name">Cession de parts — BMH</div>
        </div>
      </div>
      <div className="kanban-col">
        <div className="kanban-title"><span className="status progress" /> Déroulement de la démarche</div>
        <div className={`kanban-card live ${stage === 1 ? 'is-here' : 'is-gone'}`}>
          <div className="kanban-tag tag-blue">SARL AU</div>
          <div className="kanban-name">Création — ATLAS TECH</div>
          <div className="kanban-meta">Dépôt au greffe à cocher</div>
        </div>
        <div className="kanban-card">
          <div className="kanban-tag tag-amber">Dissolution</div>
          <div className="kanban-name">SARL CASA NÉGOCE</div>
        </div>
      </div>
      <div className="kanban-col">
        <div className="kanban-title"><span className="status done" /> Clôture du dossier</div>
        <div className={`kanban-card live ${stage === 2 ? 'is-here' : 'is-gone'}`}>
          <div className="kanban-tag tag-green">Terminé</div>
          <div className="kanban-name">Création — ATLAS TECH</div>
          <div className="kanban-meta">Démarches accomplies</div>
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
