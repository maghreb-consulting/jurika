import { useRef } from 'react';
import { AIChatTyping } from '../interactive/AIChatTyping.jsx';
import { useScrollReveal } from '../../hooks/useScrollReveal.js';

/**
 * v2 Flagship — IA split-scroll : texte gauche scroll normal,
 * droite sticky avec chat qui se tape au scroll progress.
 *
 * TASK 7 : ajout sticky right + scroll-reveal des bullet points (cascade).
 */
const POINTS = [
  {
    title: 'Extraction OCR intelligente',
    body: 'Lecture automatique des certificats négatifs PDF, CIN marocaines et CNIE pour pré-remplir les statuts.',
  },
  {
    title: 'Génération de documents conformes',
    body: 'Statuts SARL/SARL AU, PV d\'AGE, actes de cession, annonces légales — alignés sur la loi 5-96.',
  },
  {
    title: 'Chatbot RAG avec citations',
    body: 'Réponses contextuelles ancrées dans votre base documentaire et le Code de commerce.',
  },
  {
    title: 'États débours automatiques',
    body: 'Compilation et génération PDF des dépenses du dossier, prêts pour la facturation client.',
  },
];

export function AISplitScroll() {
  const sectionRef = useRef(null);
  useScrollReveal({ scope: sectionRef });

  return (
    <section className="section section-ai" id="ia" ref={sectionRef}>
      <div className="container ai-grid">
        <div className="ai-text">
          <span className="eyebrow eyebrow-light" data-reveal>Intelligence Artificielle</span>
          <h2 className="section-title section-title-light" data-reveal>
            L'IA juridique <em className="text-gold-italic">au cœur du dossier</em>
          </h2>
          <p className="section-sub section-sub-light" data-reveal>
            Spring AI 1.0, pattern RAG (Retrieval-Augmented Generation) et pgvector. JURIKA combine la rigueur du droit
            marocain à la puissance des grands modèles de langage — sans jamais retirer l'humain du circuit de validation.
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
          <div className="ai-stat ai-stat-1" data-reveal>
            <strong>82%</strong>
            <span>de temps gagné sur la rédaction</span>
          </div>
          <div className="ai-stat ai-stat-2" data-reveal>
            <strong>0</strong>
            <span>fuite inter-tenant (RLS)</span>
          </div>
        </div>
      </div>
    </section>
  );
}
