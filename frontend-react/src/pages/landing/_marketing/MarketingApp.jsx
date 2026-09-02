import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import './App.css';

// Hooks
import { useReducedMotion } from './hooks/useReducedMotion.js';
import { useLenis } from './hooks/useLenis.js';
import { useScrollTrigger } from './hooks/useScrollTrigger.js';
import { useEasterEgg } from './hooks/useEasterEgg.js';

// Layout
import { Navbar } from './components/layout/Navbar.jsx';
import { Footer } from './components/layout/Footer.jsx';
import { GrainOverlay } from './components/layout/GrainOverlay.jsx';

// Sections
import { Hero } from './components/sections/Hero.jsx';
import { TrustMarquee } from './components/sections/TrustMarquee.jsx';
import { PillarsShowcase } from './components/sections/PillarsShowcase.jsx';
import { AISplitScroll } from './components/sections/AISplitScroll.jsx';
import { PilotageShowcase } from './components/sections/PilotageShowcase.jsx';
import { AudiencesCarousel } from './components/sections/AudiencesCarousel.jsx';
import { Pricing } from './components/sections/Pricing.jsx';
import { Testimonials } from './components/sections/Testimonials.jsx';
import { FAQ } from './components/sections/FAQ.jsx';
import { CTAFinal } from './components/sections/CTAFinal.jsx';

// Interactive (Easter egg conserve, DemoModal supprime Sprint 12.6)
import { NotarySealRain } from './components/interactive/NotarySealRain.jsx';
import { ScrollProgress } from './components/interactive/ScrollProgress.jsx';

/**
 * v2 Flagship — orchestrateur landing publique JURIKA.
 *
 * Sprint 12.6 -- Toutes les references "demo" supprimees : pas de
 * DemoModal, pas de "Demander une demo". Tous les CTAs pointent
 * directement vers /signup via React Router navigate (nav SPA fluide).
 * Analytics emitEvent retire (le port SPA double-prefixait /api/v1).
 */
export function MarketingApp() {
  const reduced = useReducedMotion();
  const navigate = useNavigate();
  const [easterToast, setEasterToast] = useState(false);
  const eggFired = useEasterEgg();

  // Smooth scroll global (Lenis) + ScrollTrigger wiring
  useLenis({ enabled: !reduced });
  useScrollTrigger({ enabled: !reduced });

  // Easter egg : pluie de sceaux + logo pulse + toast 4s
  useEffect(() => {
    if (!eggFired) return undefined;
    setEasterToast(true);
    const t = setTimeout(() => setEasterToast(false), 4000);
    return () => clearTimeout(t);
  }, [eggFired]);

  // CTA universel landing -> inscription (plus aucune demo en Sprint 12.6)
  const goSignup = () => navigate('/signup');

  return (
    <>
      <a href="#main-content" className="skip-link">Aller au contenu principal</a>
      <GrainOverlay />
      <ScrollProgress />
      <Navbar pulsing={eggFired} />
      <main id="main-content" tabIndex={-1}>
        <Hero onDemoClick={goSignup} />
        <TrustMarquee />
        <PillarsShowcase />
        <AISplitScroll />
        <PilotageShowcase />
        <AudiencesCarousel />
        <Pricing onDemoClick={goSignup} />
        <Testimonials />
        <FAQ />
        <CTAFinal onDemoClick={goSignup} />
      </main>
      <Footer />
      <NotarySealRain active={eggFired} />
      {easterToast && (
        <div className="easter-toast" role="status" data-testid="easter-toast">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden="true">
            <path d="M14 2l-1 3-3 1 3 1 1 3 1-3 3-1-3-1zM6 8l-1 2-2 1 2 1 1 2 1-2 2-1-2-1zM18 14l-1 2-2 1 2 1 1 2 1-2 2-1-2-1z" />
          </svg>
          <span>Bienvenue au cabinet. Vous avez trouvé le sceau du notaire.</span>
        </div>
      )}
    </>
  );
}
