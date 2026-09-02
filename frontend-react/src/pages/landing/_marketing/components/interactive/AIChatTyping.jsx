import { useEffect, useState } from 'react';
import { useReducedMotion } from '../../hooks/useReducedMotion.js';

/**
 * v2 Flagship — AI chat dont le bot tape la réponse progressivement.
 * Sources highlight via underline or qui se trace après la fin.
 */
const FULL_REPLY = "Statuts générés ✓ — 14 articles selon la loi 5-96. Capital de 100 000 DH (1 000 parts de 100 DH), gérant unique nommé pour une durée illimitée.";

export function AIChatTyping({ delayMs = 1800 }) {
  const reduced = useReducedMotion();
  const [typed, setTyped] = useState(reduced ? FULL_REPLY : '');
  const [showSources, setShowSources] = useState(reduced);

  useEffect(() => {
    if (reduced) return undefined;
    let timer1, intervalId, timer2;
    let i = 0;
    timer1 = setTimeout(() => {
      intervalId = setInterval(() => {
        i += 2;
        setTyped(FULL_REPLY.slice(0, i));
        if (i >= FULL_REPLY.length) {
          clearInterval(intervalId);
          timer2 = setTimeout(() => setShowSources(true), 300);
        }
      }, 25);
    }, delayMs);
    return () => {
      clearTimeout(timer1);
      clearTimeout(timer2);
      if (intervalId) clearInterval(intervalId);
    };
  }, [delayMs, reduced]);

  return (
    <div className="ai-chat" data-testid="ai-chat-typing">
      <div className="ai-chat-head">
        <div className="ai-avatar">J</div>
        <div>
          <strong>JURIKA Assistant</strong>
          <span>en ligne · cite ses sources</span>
        </div>
      </div>
      <div className="ai-msg user">
        <p>Génère les statuts d'une SARL AU pour un capital de 100 000 DH, gérant unique marocain.</p>
      </div>
      <div className="ai-msg bot">
        {typed.length < FULL_REPLY.length && (
          <div className="bot-typing" aria-hidden="true">
            <span /><span /><span />
          </div>
        )}
        <p>{typed}{typed.length < FULL_REPLY.length && <span className="typewriter-caret" aria-hidden="true">|</span>}</p>
        {showSources && (
          <>
            <div className="bot-cite">
              Source : <code className="src-underline">Code_commerce_MA_art46.pdf</code> · <code className="src-underline">modele_SARL_AU_v3.docx</code>
            </div>
            <div className="bot-actions">
              <button type="button">Télécharger .docx</button>
              <button type="button" className="ghost">Voir le diff</button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
