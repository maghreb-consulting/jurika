import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Logo } from './Logo.jsx';

/**
 * v2 Flagship — Navbar avec blur scroll + gold underline link actif.
 *
 * Sprint 12.6 -- Connexion + S'inscrire en navigation React Router
 * (<Link>) pour transition fluide SPA sans full reload.
 * Bouton "Demander une démo" devient "S'inscrire" (route /signup).
 */
export function Navbar({ pulsing = false }) {
  const [scrolled, setScrolled] = useState(false);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 20);
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  return (
    <header className={`nav ${scrolled ? 'nav--scrolled' : ''}`} id="top">
      <div className="container nav-inner">
        <Logo pulsing={pulsing} />
        <nav className={`nav-links ${open ? 'open' : ''}`} aria-label="Navigation principale">
          <a href="#fonctionnalites" onClick={() => setOpen(false)}>Fonctionnalités</a>
          <a href="#ia" onClick={() => setOpen(false)}>IA</a>
          <a href="#pilotage-live" onClick={() => setOpen(false)}>Pilotage</a>
          <a href="#cible" onClick={() => setOpen(false)}>Pour qui</a>
          <a href="#tarifs" onClick={() => setOpen(false)}>Tarifs</a>
          <a href="#faq" onClick={() => setOpen(false)}>FAQ</a>
        </nav>
        <div className="nav-cta">
          <Link to="/login" className="btn btn-ghost" data-testid="navbar-login-cta">
            Connexion
          </Link>
          <Link
            to="/signup"
            className="btn btn-primary"
            onClick={() => setOpen(false)}
            data-testid="navbar-signup-cta"
          >
            S'inscrire
          </Link>
        </div>
        <button
          className={`burger ${open ? 'open' : ''}`}
          aria-label="Menu"
          aria-expanded={open}
          onClick={() => setOpen((o) => !o)}
          type="button"
        >
          <span /><span /><span />
        </button>
      </div>
    </header>
  );
}
