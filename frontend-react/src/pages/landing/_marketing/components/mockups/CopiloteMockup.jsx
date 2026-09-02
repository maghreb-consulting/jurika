import { BrowserFrame } from './DeviceFrame.jsx';
import { AppChrome } from './AppChrome.jsx';
import { Icon } from './icons.jsx';

/**
 * Mockup Pilier 3 — IA : chatbot RAG + copilote. Reproduit l'écran réel
 * ChatbotPage (assistant juridique, réponse sourcée avec badge Confiance +
 * Citations) et, à droite, le vrai CopilotePanel (violet) qui PROPOSE des
 * actions à valider — jamais d'action automatique.
 */
const SUGGESTIONS = [
  { urg: 'HAUTE', action: 'Déposer le PV d’AGO — SAFI HOLDING', why: 'Échéance légale dans 6 jours' },
  { urg: 'MOYENNE', action: 'Relancer le client CASA NÉGOCE', why: 'Sans réponse depuis 4 jours' },
];
const ECHEANCES = [
  { intitule: 'TVA mensuelle — ATLAS NÉGOCE', j: 'J-3', tone: 'danger' },
  { intitule: 'IS acompte T1 — SAFI HOLDING', j: 'J-12', tone: 'warn' },
];

export function CopiloteMockup() {
  return (
    <BrowserFrame url="app.jurika.ma/chatbot" tilt>
      <div className="mk">
        <AppChrome active="chatbot" />
        <div className="mk-page">
          <div className="mk-chat-head">
            <span className="mk-chat-head__logo"><Icon name="sparkles" size={18} /></span>
            <div>
              <h3 className="mk-h1">Assistant juridique</h3>
              <p className="mk-sub">Pose une question sur le droit des sociétés au Maroc — réponses sourcées.</p>
            </div>
          </div>

          <div className="mk-ia-grid">
            <div className="mk-thread">
              <div className="mk-msg mk-msg--user">
                <span className="mk-bubble mk-bubble--user">Quel est le capital minimum d’une SARL au Maroc ?</span>
                <span className="mk-msg__avatar mk-msg__avatar--user"><Icon name="user" size={14} /></span>
              </div>
              <div className="mk-msg mk-msg--bot">
                <span className="mk-msg__avatar mk-msg__avatar--bot"><Icon name="bot" size={14} /></span>
                <div className="mk-bubble mk-bubble--bot">
                  <p>Aucun capital minimum n’est imposé pour une SARL : il est librement fixé par les statuts. Les parts doivent être intégralement souscrites et libérées d’au moins <strong>le quart</strong>.</p>
                  <div className="mk-msg__meta">
                    <span className="mk-pill mk-pill--success"><Icon name="gauge" size={11} /> Confiance : Élevé (92%)</span>
                    <span className="mk-pill mk-pill--accent"><Icon name="sparkles" size={11} /> Réponse IA</span>
                    <span className="mk-sub">2 source(s)</span>
                  </div>
                  <div className="mk-cites">
                    <p className="mk-cites__label"><Icon name="book" size={12} /> Citations</p>
                    <div className="mk-cite"><span className="mk-cite__ref">Loi 5-96</span> Art. 46 <span className="mk-sub">— souscription et libération des parts</span></div>
                    <div className="mk-cite"><span className="mk-cite__ref">Code de commerce</span> Livre V</div>
                  </div>
                </div>
              </div>
              <div className="mk-chat-input">
                <span className="mk-chat-input__box">Pose une question juridique…</span>
                <span className="mk-btn mk-btn--accent"><Icon name="send" size={13} /> Envoyer</span>
              </div>
            </div>

            <aside className="mk-copilote">
              <div className="mk-copilote__head">
                <span className="mk-copilote__logo"><Icon name="sparkles" size={15} /></span>
                <div>
                  <strong>Copilote</strong>
                  <span className="mk-sub">Suggestions à valider</span>
                </div>
                <span className="mk-copilote__refresh"><Icon name="refresh" size={12} /></span>
              </div>
              <p className="mk-copilote__section">À faire en priorité</p>
              <ol className="mk-copilote__list">
                {SUGGESTIONS.map((s, i) => (
                  <li key={s.action}>
                    <span className="mk-copilote__num">{i + 1}</span>
                    <div>
                      <p className="mk-copilote__action">{s.action}</p>
                      <p className="mk-sub">{s.why}</p>
                    </div>
                    <span className={`mk-urg mk-urg--${s.urg.toLowerCase()}`}>{s.urg}</span>
                  </li>
                ))}
              </ol>
              <p className="mk-copilote__section"><Icon name="calendar" size={12} /> Échéances à ne pas manquer</p>
              <ul className="mk-copilote__ech">
                {ECHEANCES.map((e) => (
                  <li key={e.intitule}>
                    <span>{e.intitule}</span>
                    <span className={`mk-jbadge mk-jbadge--${e.tone}`}>{e.j}</span>
                  </li>
                ))}
              </ul>
            </aside>
          </div>
        </div>
      </div>
    </BrowserFrame>
  );
}
