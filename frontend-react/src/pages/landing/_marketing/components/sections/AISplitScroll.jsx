import { useRef } from 'react';
import { AIChatTyping } from '../interactive/AIChatTyping.jsx';
import { useScrollReveal } from '../../hooks/useScrollReveal.js';

/**
 * Section "Intelligence artificielle" (lot L7 E3) : texte a gauche, conversation
 * d'illustration a droite. L'IA reste en peripherie (CDC P1, RG-VAR-09, 12.1) :
 * elle lit les pieces et repond aux questions ; elle ne redige aucun acte.
 */
const POINTS = [
  {
    title: 'Lecture automatique des pièces',
    body: 'La CIN et le certificat négatif sont lus automatiquement ; les valeurs lues vous sont présentées pour confirmation avant d’être retenues.',
  },
  {
    title: 'Des actes générés sans intelligence artificielle',
    body: 'Statuts, procès-verbaux et formulaires sont produits à partir de modèles validés : à données identiques, acte identique.',
  },
  {
    title: 'Un chatbot juridique aux réponses sourcées',
    body: 'Il répond à partir des sources fiables déposées par JURIKA et les cite. Il n’a accès à aucun dossier et ne reçoit aucune donnée personnelle.',
  },
];

export function AISplitScroll() {
  const sectionRef = useRef(null);
  useScrollReveal({ scope: sectionRef });

  return (
    <section className="section section-ai" id="ia" ref={sectionRef}>
      <div className="container ai-grid">
        <div className="ai-text">
          <span className="eyebrow eyebrow-light" data-reveal>Intelligence artificielle</span>
          <h2 className="section-title section-title-light" data-reveal>
            L’IA vous assiste, <em className="text-gold-italic">elle ne rédige pas vos actes</em>
          </h2>
          <p className="section-sub section-sub-light" data-reveal>
            L’intelligence artificielle intervient en appui : elle lit vos pièces et répond à vos questions
            juridiques générales. Vous gardez toujours la main sur ce qui est retenu.
          </p>
          <ul className="ai-list">
            {POINTS.map((p, i) => (
              <li
                key={p.title}
                className="ai-list__item"
                data-reveal
                style={{ transitionDelay: `${i * 100}ms` }}
              >
                <span className="check" aria-hidden="true">✓</span>
                <div>
                  <strong>{p.title}</strong>
                  <p>{p.body}</p>
                </div>
              </li>
            ))}
          </ul>
        </div>
        <div className="ai-visual ai-visual-sticky">
          <AIChatTyping delayMs={400} />
        </div>
      </div>
    </section>
  );
}
