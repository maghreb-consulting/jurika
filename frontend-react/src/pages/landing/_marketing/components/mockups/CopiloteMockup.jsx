import { BrowserFrame } from './DeviceFrame.jsx';
import { AppChrome } from './AppChrome.jsx';
import { Icon } from './icons.jsx';

/**
 * Ecran d'illustration du chatbot juridique (lot L7 E3), aligne sur le cahier des
 * charges 12.1 : une question qui contient une donnee personnelle est bloquee avant
 * l'envoi (RG-COM-04) et un avertissement permanent rappelle de ne pas saisir de
 * donnees de clients (RG-COM-05). Aucune reponse juridique n'y est affirmee.
 */
const DONNEES_BLOQUEES = [
  'numéro de CIN', 'ICE', 'RC', 'identifiant fiscal', 'téléphone',
  'adresse électronique', 'RIB', 'date de naissance',
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
              <h3 className="mk-h1">Chatbot juridique</h3>
              <p className="mk-sub">Une question sur le droit des sociétés au Maroc — réponses sourcées.</p>
            </div>
          </div>

          <div className="mk-ia-grid">
            <div className="mk-thread">
              <div className="mk-msg mk-msg--user">
                <span className="mk-bubble mk-bubble--user">Le gérant, CIN BK483921, peut-il céder ses parts ?</span>
                <span className="mk-msg__avatar mk-msg__avatar--user"><Icon name="user" size={14} /></span>
              </div>
              <div className="mk-msg mk-msg--bot">
                <span className="mk-msg__avatar mk-msg__avatar--bot"><Icon name="bot" size={14} /></span>
                <div className="mk-bubble mk-bubble--bot">
                  <p>Envoi bloqué : votre question contient un numéro de CIN. Reformulez-la sans cette donnée.</p>
                  <div className="mk-msg__meta">
                    <span className="mk-pill mk-pill--warning"><Icon name="lock" size={11} /> Non envoyée</span>
                  </div>
                </div>
              </div>
              <div className="mk-chat-input">
                <span className="mk-chat-input__box">Posez une question juridique…</span>
                <span className="mk-btn mk-btn--accent"><Icon name="send" size={13} /> Envoyer</span>
              </div>
            </div>

            <aside className="mk-copilote">
              <div className="mk-copilote__head">
                <span className="mk-copilote__logo"><Icon name="lock" size={15} /></span>
                <div>
                  <strong>Rappel</strong>
                  <span className="mk-sub">Ne saisissez aucune donnée de client.</span>
                </div>
              </div>
              <p className="mk-copilote__section">Bloqués avant l’envoi</p>
              <ul className="mk-copilote__ech">
                {DONNEES_BLOQUEES.map((d) => (
                  <li key={d}><span>{d}</span></li>
                ))}
              </ul>
            </aside>
          </div>
        </div>
      </div>
    </BrowserFrame>
  );
}
