import { useRef } from 'react';
import { useScrollReveal } from '../../hooks/useScrollReveal.js';
import { KanbanLiving } from '../interactive/KanbanLiving.jsx';
import { OdometerNumber } from '../interactive/OdometerNumber.jsx';
import { BrowserFrame } from '../mockups/DeviceFrame.jsx';

/**
 * Section immersive « Pilotage » — le Kanban vivant (machine à états des
 * tickets) mis en scène dans un cadre navigateur, avec une bande de KPIs
 * animés. Prolonge le pilier 4 : ici on VOIT un ticket avancer en direct.
 *
 * Fond navy pour contraster avec les piliers clairs. Le Kanban et les
 * odomètres gèrent déjà reduced-motion (état final figé).
 */
const KPIS = [
  { to: 4, label: 'états de ticket', suffix: '' },
  { to: 9, label: 'étapes guidées', suffix: '' },
  { to: 100, label: 'actions tracées', suffix: '%' },
  { to: 0, label: 'fuite inter-cabinet (RLS)', suffix: '' },
];

export function PilotageShowcase() {
  const ref = useRef(null);
  useScrollReveal({ scope: ref });

  return (
    <section className="section section-pilotage" id="pilotage-live" ref={ref}>
      <div className="container">
        <div className="section-head">
          <span className="eyebrow eyebrow-light" data-reveal>Pilotage en direct</span>
          <h2 className="section-title section-title-light" data-reveal>
            Chaque ticket avance <em className="text-gold-italic">sous vos yeux</em>
          </h2>
          <p className="section-sub section-sub-light" data-reveal>
            Nouveau → En cours → Clôturé : la machine à états déclenche le bon workflow, et le tableau de bord
            se met à jour en temps réel — le tout journalisé, cabinet par cabinet.
          </p>
        </div>

        <div className="pilotage-board" data-reveal>
          <BrowserFrame url="app.jurika.ma/tickets">
            <div className="pilotage-kanban">
              <KanbanLiving />
            </div>
          </BrowserFrame>
        </div>

        <div className="pilotage-kpis">
          {KPIS.map((k, i) => (
            <div className="pilotage-kpi" key={k.label} data-reveal style={{ transitionDelay: `${i * 90}ms` }}>
              <OdometerNumber to={k.to} suffix={k.suffix} startDelayMs={i * 120} />
              <span>{k.label}</span>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
