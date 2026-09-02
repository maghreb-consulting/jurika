import { Icon } from './icons.jsx';
// Emblème officiel JURIKA (fond clair) — même source que BrandLogo de l'app.
// Le chrome mockup reproduit l'AppTopNav sur surface claire (--a-raised blanc).
import emblemClair from '../../../../../assets/brand/jurika-emblem-clair.png';

/**
 * Barre de navigation supérieure RÉELLE de l'app JURIKA (AppTopNav) :
 * emblème JURIKA + « JURIKA » Playfair + code workspace mono, items de nav
 * (item actif = teinte or + barre or à gauche), pill Superviseur, bouton
 * Upgrade, avatar. Reproduite fidèlement pour ancrer chaque mockup dans la
 * vraie app. `active` = clé de l'item mis en évidence.
 */
const NAV = [
  { key: 'dashboard', label: 'Tableau de bord', icon: 'home' },
  { key: 'tickets', label: 'Tous les Tickets', icon: 'ticket' },
  { key: 'dataroom', label: 'Data Rooms', icon: 'lock' },
  { key: 'chatbot', label: 'ChatBot IA', icon: 'bot' },
  { key: 'tracabilite', label: 'Traçabilité', icon: 'history' },
];

export function AppChrome({ active = 'dashboard' }) {
  return (
    <header className="mk-nav" aria-hidden="true">
      <div className="mk-nav__brand">
        <img className="mk-nav__logo" src={emblemClair} alt="" width={28} height={28} draggable={false} />
        <span className="mk-nav__brand-sep" />
        <span className="mk-nav__brand-txt">
          <strong>JURIKA</strong>
          <em>JUR-DEMO1</em>
        </span>
      </div>
      <nav className="mk-nav__items">
        {NAV.map((it) => (
          <span key={it.key} className={`mk-nav__item ${active === it.key ? 'is-active' : ''}`}>
            <Icon name={it.icon} size={15} />
            {it.label}
          </span>
        ))}
      </nav>
      <div className="mk-nav__right">
        <span className="mk-nav__role">Superviseur</span>
        <span className="mk-nav__upgrade"><Icon name="zap" size={13} /> Upgrade</span>
        <span className="mk-nav__avatar">K</span>
      </div>
    </header>
  );
}
