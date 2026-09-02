/**
 * v2 Flagship — 3 quotes émergent avec gold underline qui se trace.
 *
 * Pour TASK 2 : structure HTML statique. Animation gold underline +
 * fade-up scroll-driven TASK 8.
 */
const TESTIMONIALS = [
  {
    quote: "Le chatbot RAG juridique a réduit de 70 % notre temps de recherche documentaire. La génération auto des PV est un gain de productivité bluffant.",
    author: 'Mehdi Bensaid',
    title: 'Associé gérant',
    cabinet: 'Cabinet Atlas Consulting · Casablanca',
  },
  {
    quote: "Pour la première fois, mes clients voient en temps réel l'avancement de leur création de société. Le suivi visuel a transformé notre relation.",
    author: 'Salma El Mansouri',
    title: 'Expert-comptable agréée',
    cabinet: 'Cabinet El Mansouri · Rabat',
  },
  {
    quote: "L'hébergement Maroc et la conformité CNDP ont été décisifs. Mes clients institutionnels ne valident plus que des solutions souveraines.",
    author: 'Karim Tazi',
    title: 'Notaire',
    cabinet: 'Étude Tazi & Associés · Marrakech',
  },
];

import { useRef } from 'react';
import { useScrollReveal } from '../../hooks/useScrollReveal.js';

export function Testimonials() {
  const ref = useRef(null);
  useScrollReveal({ scope: ref });
  return (
    <section className="section section-testimonials" id="testimonials" aria-label="Témoignages" ref={ref}>
      <div className="container">
        <div className="section-head">
          <span className="eyebrow" data-reveal>Ils signent avec JURIKA</span>
          <h2 className="section-title" data-reveal>La parole des cabinets</h2>
        </div>
        <div className="testimonials-grid">
          {TESTIMONIALS.map((t, i) => (
            <figure
              className="testimonial"
              key={t.author}
              data-reveal
              style={{ transitionDelay: `${i * 120}ms` }}
            >
              <svg className="testimonial__mark" viewBox="0 0 32 32" aria-hidden="true">
                <path d="M9 7c-3 0-5 2.5-5 6v8h8v-8H8c0-2 1-3 3-3V7zm12 0c-3 0-5 2.5-5 6v8h8v-8h-4c0-2 1-3 3-3V7z" fill="currentColor" />
              </svg>
              <blockquote className="testimonial__quote">
                <p>{t.quote}</p>
              </blockquote>
              <figcaption className="testimonial__author">
                <strong>{t.author}</strong>
                <span>{t.title}</span>
                <span className="testimonial__cabinet">{t.cabinet}</span>
              </figcaption>
              <span className="testimonial__underline" aria-hidden="true" />
            </figure>
          ))}
        </div>
      </div>
    </section>
  );
}
