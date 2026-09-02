import { AppChrome } from './AppChrome.jsx';
import { Icon } from './icons.jsx';
import { OdometerNumber } from '../interactive/OdometerNumber.jsx';
import { KanbanLiving } from '../interactive/KanbanLiving.jsx';

/**
 * Aperçu d'app du Hero — reproduit l'écran réel EmployeeDashboard :
 * barre de nav supérieure réelle (AppChrome), en-tête « Bonjour … », rangée
 * de KPIs, puis « Mes tickets actifs » avec le Kanban VIVANT (carte qui
 * progresse Nouveau → En cours → Clôturé). Restylé aux tokens réels via `.mk`.
 */
const KPIS = [
  { icon: 'folder', tone: 'accent', label: 'Dossiers actifs', to: 24 },
  { icon: 'ticket', tone: 'accent', label: 'Tickets en cours', to: 8 },
  { icon: 'alertTriangle', tone: 'danger', label: 'Tâches urgentes', to: 3 },
  { icon: 'checkCircle', tone: 'success', label: 'Clôturées', to: 41 },
];

export function HeroAppPreview() {
  return (
    <div className="mk mk-hero-app">
      <AppChrome active="dashboard" />
      <div className="mk-page">
        <div className="mk-db-head">
          <h3 className="mk-h1">Bonjour, Karim</h3>
          <p className="mk-sub">Voici un résumé de votre activité.</p>
        </div>

        <div className="mk-kpirow">
          {KPIS.map((k) => (
            <div className="mk-kpi" key={k.label}>
              <span className={`mk-kpi__ico tone-${k.tone}`}><Icon name={k.icon} size={18} /></span>
              <div className="mk-kpi__body">
                <span className="mk-kpi__label">{k.label}</span>
                <span className="mk-kpi__val"><OdometerNumber to={k.to} /></span>
              </div>
            </div>
          ))}
        </div>

        <div className="mk-tickets">
          <div className="mk-tickets__head">
            <span>Mes tickets actifs</span>
            <span className="mk-tickets__link">Voir tous →</span>
          </div>
          <KanbanLiving />
        </div>
      </div>
    </div>
  );
}
