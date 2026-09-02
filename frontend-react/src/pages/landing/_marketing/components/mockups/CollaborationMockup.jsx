import { BrowserFrame } from './DeviceFrame.jsx';
import { AppChrome } from './AppChrome.jsx';
import { Icon } from './icons.jsx';

/**
 * Mockup Pilier 3 — Espace & collaboration client. Reproduit la vue Demandes
 * d'une Data Room côté cabinet : en-tête « espace client » cloisonné + ligne
 * permissions/journal d'accès, puis les deux files réelles — Requêtes du
 * cabinet (employé → client, machine OUVERTE/À COMPLÉTER/RÉPONDUE) et Demandes
 * du client (client → conseiller) — chaque ligne datée et à statut.
 * Aucune extraction CIN ici (elle vit dans le workflow, cf. WorkflowMockup).
 */
const REQUETES = [
  { title: 'Certificat négatif', meta: 'Demandé au client · 24/07 09:12', state: 'Ouverte', tone: 'warning', ico: 'fileText' },
  { title: 'CIN du gérant', meta: 'Complément demandé · 25/07 16:40', state: 'À compléter', tone: 'accent', ico: 'fileText' },
  { title: 'Justificatif de siège', meta: 'Déposé par le client · à valider', state: 'Répondue', tone: 'success', ico: 'checkCircle' },
];
const DEMANDES = [
  { title: 'Question — délai de dépôt au RC ?', meta: 'Envoyée par le client · 26/07 14:32', state: 'Ouverte', tone: 'warning', ico: 'messageSquare' },
];

export function CollaborationMockup() {
  return (
    <BrowserFrame url="app.jurika.ma/dataroom/atlas-negoce/demandes" tilt>
      <div className="mk">
        <AppChrome active="dataroom" />
        <div className="mk-page">
          <span className="mk-back"><Icon name="chevronDown" size={13} style={{ transform: 'rotate(90deg)' }} /> Retour au dossier</span>

          <div className="mk-dr-head">
            <span className="mk-iconbox"><Icon name="users" size={20} /></span>
            <div className="mk-dr-head__txt">
              <h3 className="mk-h1">Espace client — ATLAS NÉGOCE</h3>
              <p className="mk-sub">Client invité : F. El Alami · accès limité à ce seul dossier</p>
            </div>
            <span className="mk-pill mk-pill--success">Cloisonné</span>
          </div>

          <div className="mk-perm">
            <Icon name="lock" size={13} /> Permissions : voir · télécharger · imprimer — journal d'accès horodaté
          </div>

          <div className="mk-card mk-docs">
            <div className="mk-card__head"><Icon name="send" size={15} /> Requêtes du cabinet <span className="mk-count">3</span></div>
            {REQUETES.map((r) => (
              <div className="mk-docrow" key={r.title}>
                <span className="mk-docrow__ico"><Icon name={r.ico} size={15} /></span>
                <div className="mk-docrow__txt">
                  <p>{r.title}</p>
                  <span className="mk-sub">{r.meta}</span>
                </div>
                <span className={`mk-pill mk-pill--${r.tone}`}>{r.state}</span>
              </div>
            ))}
          </div>

          <div className="mk-card mk-docs">
            <div className="mk-card__head"><Icon name="messageSquare" size={15} /> Demandes du client <span className="mk-count">1</span></div>
            {DEMANDES.map((d) => (
              <div className="mk-docrow" key={d.title}>
                <span className="mk-docrow__ico"><Icon name={d.ico} size={15} /></span>
                <div className="mk-docrow__txt">
                  <p>{d.title}</p>
                  <span className="mk-sub">{d.meta}</span>
                </div>
                <span className={`mk-pill mk-pill--${d.tone}`}>{d.state}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </BrowserFrame>
  );
}
