// @ts-expect-error -- JSX module from marketing-site port (no .d.ts).
import { MarketingApp } from './_marketing/MarketingApp.jsx';
import './_marketing/brand.css';
import './_marketing/App.css';

/**
 * Sprint 12.5/12.6 — Landing page = port intégral marketing-site Flagship.
 *
 * L'ancienne landing 1482 lignes mono-fichier (dark Linear-style) est remplacee
 * par le marketing-site v2 (light editorial premium, navy/or/emeraude, Playfair).
 * Cf output/PLAN_SPRINT_12-5_THEME_ALIGNMENT.md "Strategie A : clone marketing"
 * apres pivot user 2026-06-01.
 *
 * Port :
 *  - src copies sous ./\_marketing/{components,hooks,lib,config,styles,assets}
 *  - MarketingApp.jsx == ex marketing-site/src/App.jsx, rendered tel quel
 *  - brand.css + App.css importes ici uniquement (scope au mount de la landing)
 *
 * NOTE : signupUrl/loginUrl/demoUrl du marketing pointent maintenant vers les
 * chemins relatifs SPA (/signup, /login, etc.) via config/pricing.js patch.
 * Les liens GET full-reload sur les routes React Router, qui interceptent et
 * naviguent vers les pages correspondantes (pas ideal SPA-wise mais fonctionnel
 * pour le port 1:1).
 */
export function LandingPage() {
  return <MarketingApp />;
}
