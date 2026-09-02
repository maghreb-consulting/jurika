import { useRef, useState } from 'react';
import { useStickyScrub } from '../../hooks/useStickyScrub.js';

/**
 * v2 Flagship — Sticky-scroll showcase "Au cœur du dossier".
 *
 * Pin 5x viewport, mockup central morphe à travers 5 états synchrones
 * avec le texte gauche : Ticket créé → Workflow 9 étapes → IA génère →
 * DataRoom → PV AGE signé.
 *
 * GSAP ScrollTrigger pin/scrub via useStickyScrub.
 */
const STEPS = [
  {
    n: '01',
    eyebrow: 'Ticket créé',
    title: 'Une demande client devient un dossier vivant',
    body: "Le client saisit son besoin depuis l'espace dédié. Un ticket est instantanément créé, qualifié et acheminé au bon employé du cabinet.",
    mockup: 'ticket',
  },
  {
    n: '02',
    eyebrow: 'Workflow 9 étapes',
    title: 'Le bon chemin, balisé par votre playbook',
    body: 'Création SARL, modification statutaire, dissolution… chaque procédure suit un workflow guidé, validé et tracé, conforme à la loi 5-96.',
    mockup: 'workflow',
  },
  {
    n: '03',
    eyebrow: 'L\'IA rédige',
    title: 'Statuts, PV, annonces : générés en deux clics',
    body: "Spring AI + RAG sur votre base documentaire. Citations sourcées. Validation humaine obligatoire avant publication. L'humain reste maître.",
    mockup: 'ai',
  },
  {
    n: '04',
    eyebrow: 'Data Room',
    title: 'Tout le dossier classé, accessible, partagé',
    body: 'Espace sécurisé client-cabinet. Permissions granulaires. Versionning. Audit log complet. Conformité CNDP.',
    mockup: 'dataroom',
  },
  {
    n: '05',
    eyebrow: 'Signé',
    title: 'PV AGE validé, dossier clôturé, client notifié',
    body: 'Le sceau or "Validé" se pose. Email automatique au client. Archive 10 ans (CGI Art. 211). Le rituel se termine.',
    mockup: 'signed',
  },
];

// Mockup visuel par stage (SVG inline, signé typo + chiffre)
function MockupFrame({ step, index, active }) {
  return (
    <div
      className={`mockup-frame mockup-${step.mockup} ${active ? 'is-active' : ''}`}
      data-frame={index}
      aria-hidden={!active}
    >
      <div className="mockup-frame__n">{step.n}</div>
      <div className="mockup-frame__label">{step.eyebrow}</div>
      <MockupArt mockup={step.mockup} />
    </div>
  );
}

function MockupArt({ mockup }) {
  if (mockup === 'ticket') {
    return (
      <div className="mockup-art ticket-art">
        <div className="ma-ticket-card">
          <div className="ma-line ma-w-50" />
          <div className="ma-line ma-w-80" />
          <div className="ma-line ma-w-30" />
          <span className="ma-tag">SARL AU</span>
        </div>
      </div>
    );
  }
  if (mockup === 'workflow') {
    return (
      <div className="mockup-art workflow-art">
        {Array.from({ length: 9 }).map((_, i) => (
          <span key={i} className={`ma-step ${i < 4 ? 'is-done' : i === 4 ? 'is-current' : ''}`}>{i + 1}</span>
        ))}
      </div>
    );
  }
  if (mockup === 'ai') {
    return (
      <div className="mockup-art ai-art">
        <div className="ma-prompt">▶ Génère les statuts SARL AU 100 000 DH</div>
        <div className="ma-typing"><span /><span /><span /></div>
        <div className="ma-output ma-w-80" />
        <div className="ma-output ma-w-60" />
      </div>
    );
  }
  if (mockup === 'dataroom') {
    return (
      <div className="mockup-art dataroom-art">
        <div className="ma-file" /><div className="ma-file" /><div className="ma-file" />
        <div className="ma-file" /><div className="ma-file" /><div className="ma-file" />
      </div>
    );
  }
  // signed
  return (
    <div className="mockup-art signed-art">
      <div className="ma-pv">PV AGE</div>
      <div className="ma-seal" aria-hidden="true">
        <svg viewBox="0 0 64 64" width="100%" height="100%">
          <circle cx="32" cy="32" r="28" fill="none" stroke="#c8a45c" strokeWidth="2" />
          <path d="M32 14 L36.6 27.4 L50.6 27.4 L39.3 35.6 L43.9 49 L32 40.8 L20.1 49 L24.7 35.6 L13.4 27.4 L27.4 27.4 Z"
                fill="none" stroke="#c8a45c" strokeWidth="1.5" />
          <text x="32" y="36" textAnchor="middle" fontSize="9" fill="#c8a45c" fontFamily="Inter, sans-serif" fontWeight="600">VALIDÉ</text>
        </svg>
      </div>
    </div>
  );
}

export function StickyShowcase() {
  const stageRef = useRef(null);
  const [stage, setStage] = useState(0);

  useStickyScrub({ scope: stageRef, stages: STEPS.length, onStageChange: setStage });

  return (
    <section className="sticky-showcase" id="au-coeur-du-dossier" aria-label="Au cœur du dossier : 5 étapes synchronisées">
      <div className="container">
        <div className="section-head">
          <span className="eyebrow">Au cœur du dossier</span>
          <h2 className="section-title">Cinq moments. Un seul flux.</h2>
          <p className="section-sub">
            Suivez un dossier de bout en bout. Du ticket entrant à la signature, JURIKA orchestre chaque étape sans rupture.
          </p>
        </div>
      </div>

      <div className="sticky-stage" ref={stageRef} data-sticky-stage>
        <div className="container sticky-grid">
          <div className="sticky-text">
            {STEPS.map((s, i) => (
              <article
                key={s.n}
                className={`sticky-step ${stage === i ? 'is-active' : stage > i ? 'is-past' : ''}`}
                data-step={i}
              >
                <span className="eyebrow">{s.eyebrow}</span>
                <h3 className="sticky-step-title">{s.title}</h3>
                <p>{s.body}</p>
              </article>
            ))}
          </div>
          <div className="sticky-mockup" aria-hidden="true">
            {STEPS.map((s, i) => (
              <MockupFrame key={s.n} step={s} index={i} active={stage === i} />
            ))}
            <div className="sticky-mockup__progress" aria-hidden="true">
              <div className="sticky-mockup__bar" style={{ width: `${((stage + 1) / STEPS.length) * 100}%` }} />
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
