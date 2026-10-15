import { useEffect, useRef, useState } from 'react';
import { useReducedMotion } from '../../hooks/useReducedMotion.js';
import { useParallax } from '../../hooks/useParallax.js';
import { HERO_TIMING } from '../../config/motion.js';
import { TypewriterText } from '../interactive/TypewriterText.jsx';
import { OdometerNumber } from '../interactive/OdometerNumber.jsx';
import { MagneticButton } from '../interactive/MagneticButton.jsx';
import { GoldParticles } from '../interactive/GoldParticles.jsx';
import { HeroAppPreview } from '../mockups/HeroAppPreview.jsx';

/**
 * v2 Flagship — Hero "signature moment" 1.8s chrono.
 *
 *  t=0.0  eyebrow fade-up
 *  t=0.2  H1 ligne 1 typewriter (40ms/char)
 *  t=1.0  pause 100ms (souffle rédacteur)
 *  t=1.1  H1 ligne 2 italique Playfair, gradient or révélé gauche→droite
 *  t=1.6  sous-titre fade-up + CTA bounce
 *  t=1.8  trust bar — odomètres
 *
 * Layers parallax + grain : injectés via App.jsx + ScrollTrigger TASK 3-4.
 */
const LINE_1 = "La gestion juridique des entreprises,";
const LINE_1_DURATION_MS = LINE_1.length * HERO_TIMING.H1_CHAR_INTERVAL_MS;

export function Hero({ onDemoClick }) {
  const reduced = useReducedMotion();
  const heroRef = useRef(null);
  const [line1Done, setLine1Done] = useState(reduced);
  const [stage, setStage] = useState({ subtitle: reduced, trust: reduced, line2: reduced });

  // Layered scroll parallax (L1 backdrop -0.5x, L2 particules -0.3x, L4 mockup +0.15x)
  useParallax({ scope: heroRef });

  // Chronologie post-typewriter
  useEffect(() => {
    if (reduced) return undefined;
    const t1 = setTimeout(() => setStage((s) => ({ ...s, line2: true })), HERO_TIMING.H1_LINE2_START * 1000);
    const t2 = setTimeout(() => setStage((s) => ({ ...s, subtitle: true })), HERO_TIMING.SUBTITLE_AT * 1000);
    const t3 = setTimeout(() => setStage((s) => ({ ...s, trust: true })), HERO_TIMING.TRUSTBAR_AT * 1000);
    return () => { clearTimeout(t1); clearTimeout(t2); clearTimeout(t3); };
  }, [reduced]);

  return (
    <section className="hero" data-testid="hero" ref={heroRef}>
      <div className="hero-bg" aria-hidden="true">
        {/* L1 backdrop (-0.5x) */}
        <div className="orb orb-1" data-parallax="-0.5" />
        <div className="orb orb-2" data-parallax="-0.4" />
        <div className="grid-pattern" data-parallax="-0.2" />
        {/* L2 particules or (-0.3x via wrapper) */}
        <div className="hero-particles-layer" data-parallax="-0.3">
          <GoldParticles density={48} opacity={0.45} />
        </div>
      </div>
      <div className="container hero-inner">
        <span className="eyebrow hero-eyebrow">
          <span className="dot-live" /> Gestion juridique des sociétés au Maroc
        </span>
        <h1 className="hero-title">
          <span className="hero-line hero-line-1">
            <TypewriterText
              text={LINE_1}
              startDelayMs={HERO_TIMING.H1_LINE1_START * 1000}
              onDone={() => setLine1Done(true)}
            />
          </span>
          <br />
          <span className={`hero-line hero-line-2 ${stage.line2 || (reduced && line1Done) ? 'is-revealed' : ''}`}>
            <em className="hero-accent-italic">rigoureuse</em> et <em className="hero-accent-italic hero-accent-gradient">traçable</em>
          </span>
        </h1>
        <p className={`hero-sub ${stage.subtitle ? 'is-in' : ''}`}>
          JURIKA digitalise et centralise la gestion juridique des sociétés suivies par votre cabinet :
          création, modifications, dissolution, liquidation. Actes générés à partir de modèles validés, sans intelligence
          artificielle, lecture automatique des pièces, chatbot juridique qui cite ses sources et Data Room partagée avec vos clients.
        </p>
        <div className={`hero-cta ${stage.subtitle ? 'is-in' : ''}`}>
          <MagneticButton
            type="button"
            className="btn btn-primary btn-lg"
            onClick={onDemoClick}
            data-testid="hero-signup-cta"
          >
            Créer mon compte
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M5 12h14M13 5l7 7-7 7"/></svg>
          </MagneticButton>
          <a href="#fonctionnalites" className="btn btn-outline btn-lg">
            Découvrir la plateforme
          </a>
        </div>
        <div className={`hero-trust ${stage.trust ? 'is-in' : ''}`} data-testid="hero-trust">
          <div className="trust-item">
            <OdometerNumber to={222} startDelayMs={0} />
            <span>modèles d’actes</span>
          </div>
          <div className="trust-divider" />
          <div className="trust-item">
            <OdometerNumber to={12} startDelayMs={120} />
            <span>matrices de démarches</span>
          </div>
          <div className="trust-divider" />
          <div className="trust-item">
            <OdometerNumber to={7} startDelayMs={240} />
            <span>jours d’essai gratuit</span>
          </div>
        </div>
      </div>
      <div className="hero-mock" data-parallax="0.15">
        <div className="mock-window">
          <div className="mock-bar">
            <span className="dot red" />
            <span className="dot yellow" />
            <span className="dot green" />
            <span className="mock-url">app.jurika.ma / dashboard</span>
          </div>
          <HeroAppPreview />
        </div>
      </div>
    </section>
  );
}
