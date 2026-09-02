import { BrowserFrame } from './DeviceFrame.jsx';
import { AppChrome } from './AppChrome.jsx';
import { Icon } from './icons.jsx';

/**
 * Mockup Pilier 1 — Workflows. Reproduit fidèlement l'écran réel
 * CreationSarlWorkflowPage : en-tête (réf + titre + badge mode), roadmap
 * HORIZONTALE 9 étapes (labels réels), barre de progression, puis l'étape
 * « Dirigeants » (Step5Dirigeants) avec l'assistant d'extraction CIN
 * (IdentityExtractor OCR + KIE) qui pré-remplit le formulaire — l'extraction
 * vit dans le workflow ; la pièce est ensuite archivée dans la Data Room.
 */
const STEPS = [
  'Dénomination', 'Siège social', 'Capital', 'Activité', 'Dirigeants',
  'Associés', 'Génération IA', 'Pièces jointes', 'Synthèse',
];
const CURRENT = 4; // index 0-based → « Dirigeants » en cours (4 étapes validées)
const CIN_FIELDS = [
  { label: 'Nom', value: 'BENANI' },
  { label: 'Prénom', value: 'Youssef' },
  { label: 'N° CIN', value: 'BK483921' },
  { label: 'Date de naissance', value: '12/04/1988' },
  { label: 'CIN valable jusqu\'au', value: '30/09/2030' },
  { label: 'Sexe (M / F)', value: 'M' },
];

export function WorkflowMockup() {
  return (
    <BrowserFrame url="app.jurika.ma/workflows/creation-sarl" tilt>
      <div className="mk">
        <AppChrome active="tickets" />
        <div className="mk-page">
          <div className="mk-wf-head">
            <div>
              <p className="mk-mono mk-sub">TK-2026-0481</p>
              <h3 className="mk-h1">Création SARL — ATLAS NÉGOCE</h3>
            </div>
            <span className="mk-badge">Mode : SARL_AU (1 associé)</span>
          </div>

          <nav className="mk-card mk-roadmap" aria-hidden="true">
            <ol className="mk-roadmap__row">
              {STEPS.map((label, i) => {
                const state = i < CURRENT ? 'done' : i === CURRENT ? 'current' : 'future';
                return (
                  <li key={label} className={`mk-rstep is-${state}`}>
                    <span className="mk-rstep__dot">
                      {state === 'done' ? <Icon name="check" size={13} strokeWidth={2.4} />
                        : state === 'future' ? <Icon name="lock" size={11} />
                        : i + 1}
                    </span>
                    <span className="mk-rstep__label">{label}</span>
                    {i < STEPS.length - 1 && <span className="mk-rstep__conn" />}
                  </li>
                );
              })}
            </ol>
            <div className="mk-roadmap__progress">
              <div className="mk-progress"><span style={{ width: `${(CURRENT / STEPS.length) * 100}%` }} /></div>
              <span className="mk-mono mk-sub">{CURRENT}/{STEPS.length} étapes</span>
            </div>
          </nav>

          <section className="mk-card mk-kie">
            <div className="mk-kie__head">
              <span className="mk-kie__title"><Icon name="sparkles" size={15} /> Gérant — extraction CIN</span>
              <span className="mk-srcbadge"><Icon name="sparkles" size={11} /> Source : fusion (KIE + MRZ)</span>
            </div>
            <p className="mk-kie__hint">Champs lus depuis la CIN — à vérifier puis appliquer</p>
            <div className="mk-kie__grid">
              {CIN_FIELDS.map((f) => (
                <label className="mk-field" key={f.label}>
                  <span>{f.label}</span>
                  <span className="mk-input">{f.value}</span>
                </label>
              ))}
            </div>
            <div className="mk-kie__archived"><Icon name="checkCircle" size={13} /> Pièce archivée dans la Data Room (réf a3f1c980).</div>
            <div className="mk-kie__foot">
              <span className="mk-btn mk-btn--accent"><Icon name="checkCircle" size={13} /> Appliquer au formulaire</span>
            </div>
          </section>
        </div>
      </div>
    </BrowserFrame>
  );
}
