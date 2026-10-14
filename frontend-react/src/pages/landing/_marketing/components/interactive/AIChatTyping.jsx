import { useEffect, useState } from 'react';
import { useReducedMotion } from '../../hooks/useReducedMotion.js';

/**
 * Conversation d'illustration du chatbot, dont la reponse se tape progressivement.
 * Lot L7 E3 : la reponse rappelle la regle P1 (aucune IA ne redige un acte) au lieu
 * de montrer un chatbot qui genere des statuts ; aucun contenu juridique n'y est affirme.
 */
const FULL_REPLY = "Je ne rédige pas d'acte : les statuts sont générés par le parcours de création, à partir des modèles validés. Je peux répondre à vos questions juridiques générales, en citant mes sources.";

export function AIChatTyping({ delayMs = 1800 }) {
  const reduced = useReducedMotion();
  const [typed, setTyped] = useState(reduced ? FULL_REPLY : '');
  const [showSources, setShowSources] = useState(reduced);

  useEffect(() => {
    if (reduced) return undefined;
    let timer2;
    let intervalId;
    let i = 0;
    const timer1 = setTimeout(() => {
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
          <strong>Chatbot juridique</strong>
          <span>cite ses sources</span>
        </div>
      </div>
      <div className="ai-msg user">
        <p>Peux-tu rédiger les statuts de ma SARL à associé unique ?</p>
      </div>
      <div className="ai-msg bot">
        {typed.length < FULL_REPLY.length && (
          <div className="bot-typing" aria-hidden="true">
            <span /><span /><span />
          </div>
        )}
        <p>{typed}{typed.length < FULL_REPLY.length && <span className="typewriter-caret" aria-hidden="true">|</span>}</p>
        {showSources && (
          <div className="bot-cite">
            Ne saisissez aucune donnée de client dans le chatbot.
          </div>
        )}
      </div>
    </div>
  );
}
