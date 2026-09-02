import { useRef } from 'react';
import { useTilt3D } from '../../hooks/useTilt3D.js';

/**
 * v2 Flagship — Bento grid features asymétrique.
 *
 * 1 hero tile (data-viz RLS) + 2 grandes + 4 petites = 7 tuiles
 * en grille fluide à hauteur variable. Hover tilt 3D via useTilt3D.
 */
const ICON_PROPS = {
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
};

const HERO_TILE = {
  size: 'xl',
  icon: (
    <svg {...ICON_PROPS}><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>
  ),
  title: 'Architecture Multi-Tenant',
  desc: "Isolation totale via Row Level Security PostgreSQL. Chaque cabinet bénéficie d'une étanchéité niveau bancaire — au cœur du moteur de base de données.",
  badge: 'RLS PostgreSQL',
};

const BIG_TILES = [
  {
    size: 'lg',
    icon: <svg {...ICON_PROPS}><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>,
    title: 'Workflows guidés 9 étapes',
    desc: 'Création SARL/SARL AU, modifications statutaires (28 types), dissolution, liquidation, succursales — chaque étape balisée, validée, tracée.',
  },
  {
    size: 'lg',
    icon: <svg {...ICON_PROPS}><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><path d="M9 13l2 2 4-4"/></svg>,
    title: 'Génération documentaire IA',
    desc: 'Statuts, PV, actes, annonces légales, états débours — générés avec Spring AI 1.0. Validation humaine obligatoire avant publication.',
  },
];

const SMALL_TILES = [
  {
    size: 'sm',
    icon: <svg {...ICON_PROPS}><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>,
    title: 'Auth 3 étapes + 2FA TOTP',
    desc: 'Code workspace, email/mot de passe, TOTP. Chiffrement AES-256, BCrypt, anti-bruteforce. CNDP loi 09-08.',
  },
  {
    size: 'sm',
    icon: <svg {...ICON_PROPS}><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>,
    title: 'Chat + Chatbot RAG',
    desc: 'Messagerie WebSocket intra-workspace et client-employé. Chatbot juridique RAG avec pgvector cite ses sources.',
  },
  {
    size: 'sm',
    icon: <svg {...ICON_PROPS}><rect x="2" y="3" width="20" height="14" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>,
    title: 'Mobile iOS & Android',
    desc: 'React Native synchronisée. Notifications push FCM : tickets bloqués, délais, expirations CN.',
  },
  {
    size: 'sm',
    icon: <svg {...ICON_PROPS}><path d="M12 22s-8-4.5-8-11.8A8 8 0 0 1 12 2a8 8 0 0 1 8 8.2c0 7.3-8 11.8-8 11.8z"/><circle cx="12" cy="10" r="3"/></svg>,
    title: 'Hébergement souverain Maroc',
    desc: 'OVH Casablanca + plan Entreprise. Vos données restent dans le Royaume. SLA 99,9 %.',
  },
];

function BentoTile({ tile, hero = false }) {
  return (
    <article className={`bento-tile bento-${tile.size}${hero ? ' bento-hero' : ''}`} data-tilt="true">
      <div className="bento-tile__icon" aria-hidden="true">{tile.icon}</div>
      <h3>{tile.title}</h3>
      <p>{tile.desc}</p>
      {tile.badge && <span className="bento-badge">{tile.badge}</span>}
      {hero && (
        <div className="bento-viz" aria-hidden="true">
          <div className="bento-viz__col">
            <div className="bento-viz__bar" style={{ height: '78%' }} />
            <span>WS A</span>
          </div>
          <div className="bento-viz__col">
            <div className="bento-viz__bar" style={{ height: '52%' }} />
            <span>WS B</span>
          </div>
          <div className="bento-viz__col">
            <div className="bento-viz__bar" style={{ height: '88%' }} />
            <span>WS C</span>
          </div>
          <div className="bento-viz__col">
            <div className="bento-viz__bar" style={{ height: '34%' }} />
            <span>WS D</span>
          </div>
          <div className="bento-viz__divider" />
          <span className="bento-viz__legend">0 fuite inter-tenant (RLS)</span>
        </div>
      )}
    </article>
  );
}

export function BentoFeatures() {
  const gridRef = useRef(null);
  useTilt3D({ scope: gridRef, maxAngle: 4, glare: true });

  return (
    <section className="section section-bento" id="features">
      <div className="container">
        <div className="section-head">
          <span className="eyebrow">Fonctionnalités clés</span>
          <h2 className="section-title">Tout ce qu'il faut pour piloter un cabinet juridique moderne</h2>
          <p className="section-sub">
            Une plateforme intégrée pensée pour les réalités du droit marocain — du certificat négatif à l'annonce
            légale, en passant par le suivi Kanban et la Data Room sécurisée.
          </p>
        </div>
        <div className="bento-grid" ref={gridRef}>
          <BentoTile tile={HERO_TILE} hero />
          {BIG_TILES.map((t, i) => <BentoTile key={`big-${i}`} tile={t} />)}
          {SMALL_TILES.map((t, i) => <BentoTile key={`sm-${i}`} tile={t} />)}
        </div>
      </div>
    </section>
  );
}
