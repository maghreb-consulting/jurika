import { BrowserFrame } from './DeviceFrame.jsx';
import { AppChrome } from './AppChrome.jsx';
import { Icon } from './icons.jsx';

/**
 * Mockup Pilier 2 — Data Room multi-tenant. Reproduit l'écran réel du détail
 * dossier : en-tête (Building2 + raison sociale + ICE/ville + statut), barre
 * d'onglets segmentée (5 onglets réels, Juridique actif), documents en vigueur
 * versionnés, puis la file des dépôts client (validation en deux temps) et la
 * ligne de permissions. L'extraction CIN (OCR/KIE) vit dans le workflow de
 * saisie des dirigeants — pas ici : la Data Room archive, cloisonne et partage.
 */
const TABS = [
  { label: 'Dossier Juridique', icon: 'fileText', tone: 'accent', active: true },
  { label: 'Dossier Comptable', icon: 'calculator', tone: 'success' },
  { label: 'Dossier Fiscal', icon: 'landmark', tone: 'warning' },
  { label: 'Dépôts', icon: 'upload', tone: 'warning' },
  { label: 'Demandes', icon: 'messageSquare', tone: 'accent' },
];
const DOCS = [
  { title: 'Statuts SARL AU', meta: 'Statuts · v3 · 26/07/2026' },
  { title: 'PV Assemblée Générale Ordinaire', meta: 'PV AGO · v1 · 12/07/2026' },
];
const DEPOSITS = [
  { title: 'Kbis_Atlas_2026.pdf', meta: 'Déposé par le client · 26/07/2026', state: 'À valider', tone: 'warning', ico: 'upload' },
  { title: 'Bilan_2025.pdf', meta: 'Validé · classé au dossier comptable', state: 'Validé', tone: 'success', ico: 'fileText' },
];

export function DataRoomMockup() {
  return (
    <BrowserFrame url="app.jurika.ma/dataroom/atlas-negoce" tilt>
      <div className="mk">
        <AppChrome active="dataroom" />
        <div className="mk-page">
          <span className="mk-back"><Icon name="chevronDown" size={13} style={{ transform: 'rotate(90deg)' }} /> Retour aux Data Rooms</span>

          <div className="mk-dr-head">
            <span className="mk-iconbox"><Icon name="building" size={20} /></span>
            <div className="mk-dr-head__txt">
              <h3 className="mk-h1">ATLAS NÉGOCE</h3>
              <p className="mk-sub">SARL AU · ICE 002345678000045 · Casablanca</p>
            </div>
            <span className="mk-pill mk-pill--success">Actif</span>
          </div>

          <div className="mk-tabs">
            {TABS.map((t) => (
              <span key={t.label} className={`mk-tab ${t.active ? `is-active tone-${t.tone}` : ''}`}>
                <Icon name={t.icon} size={14} /> {t.label}
              </span>
            ))}
          </div>

          <div className="mk-card mk-docs">
            <div className="mk-card__head"><Icon name="fileText" size={15} /> Documents en vigueur <span className="mk-count">4</span></div>
            {DOCS.map((d) => (
              <div className="mk-docrow" key={d.title}>
                <span className="mk-docrow__ico"><Icon name="fileText" size={15} /></span>
                <div className="mk-docrow__txt">
                  <p>{d.title}</p>
                  <span className="mk-sub">{d.meta}</span>
                </div>
                <span className="mk-docrow__actions">
                  <Icon name="eye" size={14} /><Icon name="download" size={14} />
                </span>
              </div>
            ))}
          </div>

          <div className="mk-card mk-docs">
            <div className="mk-card__head"><Icon name="upload" size={15} /> Dépôts client <span className="mk-count">3</span></div>
            {DEPOSITS.map((d) => (
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

          <div className="mk-perm">
            <Icon name="lock" size={13} /> Permissions client : voir · télécharger · imprimer — cloisonné par cabinet (RLS)
          </div>
        </div>
      </div>
    </BrowserFrame>
  );
}
