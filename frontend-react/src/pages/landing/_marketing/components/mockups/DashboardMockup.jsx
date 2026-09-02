import { BrowserFrame } from './DeviceFrame.jsx';
import { AppChrome } from './AppChrome.jsx';
import { Icon } from './icons.jsx';
import { OdometerNumber } from '../interactive/OdometerNumber.jsx';

/**
 * Mockup Pilier 4 — Pilotage. Reproduit l'écran réel SupervisorDashboard :
 * 4 cartes KPI (icône teintée + valeur), 2 cartes graphiques (donut « Tickets
 * par statut » aux couleurs réelles statutTicketColors + courbe d'activité or)
 * et le tableau « Performances par employé » avec pastilles de score.
 */
const DONUT_C = 2 * Math.PI * 42;
const SEGMENTS = [
  { label: 'Clôturés', pct: 52, color: '#10b981' },  // CLOTURE
  { label: 'En cours', pct: 33, color: '#a8853d' },  // EN_COURS
  { label: 'Nouveaux', pct: 15, color: '#1c3461' },  // NOUVEAU
];
const KPIS = [
  { icon: 'folder', tone: 'accent', label: 'Total dossiers', to: 128, tag: '112 actifs', tagTone: 'sub' },
  { icon: 'users', tone: 'accent', label: 'Employés', to: 6, tag: 'Actifs', tagTone: 'warn' },
  { icon: 'ban', tone: 'danger', label: 'Tickets en cours', to: 23, tag: '5 nouveau(x)', tagTone: 'danger' },
  { icon: 'checkCircle', tone: 'success', label: 'Clôturées', to: 94, tag: '3 annulé(s)', tagTone: 'sub' },
];
const PERF = [
  { who: 'K. Alaoui', enc: 4, clo: 22, ann: 1, score: 92, tone: 'good' },
  { who: 'S. El Mansouri', enc: 6, clo: 18, ann: 0, score: 75, tone: 'warn' },
  { who: 'Y. Benani', enc: 3, clo: 14, ann: 2, score: 82, tone: 'good' },
];

function Donut() {
  let offset = 0;
  return (
    <svg className="mk-donut" viewBox="0 0 100 100" aria-hidden="true">
      <circle className="mk-donut__track" cx="50" cy="50" r="42" />
      {SEGMENTS.map((s) => {
        const len = (s.pct / 100) * DONUT_C;
        const c = (
          <circle key={s.label} cx="50" cy="50" r="42" fill="none" stroke={s.color} strokeWidth="11"
            strokeDasharray={`${len} ${DONUT_C - len}`} strokeDashoffset={-offset} transform="rotate(-90 50 50)" />
        );
        offset += len;
        return c;
      })}
      <text className="mk-donut__center" x="50" y="49">117</text>
      <text className="mk-donut__sub" x="50" y="60">tickets</text>
    </svg>
  );
}

export function DashboardMockup() {
  return (
    <BrowserFrame url="app.jurika.ma/dashboard" tilt>
      <div className="mk">
        <AppChrome active="dashboard" />
        <div className="mk-page">
          <div className="mk-db-head">
            <h3 className="mk-h1">Tableau de bord — Superviseur</h3>
            <p className="mk-sub">Vue d’ensemble &amp; performances du cabinet</p>
          </div>

          <div className="mk-kpirow">
            {KPIS.map((k) => (
              <div className="mk-kpi" key={k.label}>
                <span className={`mk-kpi__ico tone-${k.tone}`}><Icon name={k.icon} size={20} /></span>
                <div className="mk-kpi__body">
                  <span className="mk-kpi__label">{k.label}</span>
                  <span className="mk-kpi__val"><OdometerNumber to={k.to} /></span>
                  <span className={`mk-kpi__tag tone-${k.tagTone}`}>{k.tag}</span>
                </div>
              </div>
            ))}
          </div>

          <div className="mk-charts">
            <div className="mk-card mk-chartcard">
              <div className="mk-card__head"><Icon name="trendingUp" size={14} /> Tickets par statut</div>
              <div className="mk-chartcard__donut">
                <Donut />
                <ul className="mk-legend">
                  {SEGMENTS.map((s) => (
                    <li key={s.label}><span className="mk-legend__dot" style={{ background: s.color }} />{s.label}<strong>{s.pct}%</strong></li>
                  ))}
                </ul>
              </div>
            </div>
            <div className="mk-card mk-chartcard">
              <div className="mk-card__head"><Icon name="trendingUp" size={14} /> Activité (14 derniers jours)</div>
              <svg className="mk-trend" viewBox="0 0 280 96" preserveAspectRatio="none" aria-hidden="true">
                <defs>
                  <linearGradient id="mkTrendFill2" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#a8853d" stopOpacity="0.28" />
                    <stop offset="100%" stopColor="#a8853d" stopOpacity="0" />
                  </linearGradient>
                </defs>
                <path d="M0,74 L28,62 L56,66 L84,50 L112,54 L140,38 L168,42 L196,28 L224,32 L252,18 L280,22 L280,96 L0,96 Z" fill="url(#mkTrendFill2)" />
                <path d="M0,74 L28,62 L56,66 L84,50 L112,54 L140,38 L168,42 L196,28 L224,32 L252,18 L280,22" fill="none" stroke="#a8853d" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </div>
          </div>

          <div className="mk-card mk-perf">
            <div className="mk-card__head">Performances par employé</div>
            <table className="mk-table">
              <thead>
                <tr><th>Employé</th><th>En cours</th><th>Clôturées</th><th>Annulées</th><th>Score</th></tr>
              </thead>
              <tbody>
                {PERF.map((p) => (
                  <tr key={p.who}>
                    <td><span className="mk-avatar">{p.who[0]}</span> {p.who}</td>
                    <td>{p.enc}</td>
                    <td className="mk-txt-success">{p.clo}</td>
                    <td className="mk-txt-danger">{p.ann}</td>
                    <td><span className={`mk-score mk-score--${p.tone}`}>{p.tone === 'good' && <Icon name="checkCircle" size={11} />}{p.score}%</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </BrowserFrame>
  );
}
