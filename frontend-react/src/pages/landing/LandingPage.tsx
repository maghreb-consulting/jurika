import { useEffect, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { EnTeteVitrine } from './composants/EnTeteVitrine';
import { PiedVitrine } from './composants/PiedVitrine';
import {
  Accroche,
  Contact,
  Essai,
  Fonctionnalites,
  Parcours,
  ProblemeSolution,
  Questions,
  Securite,
  Tarifs,
} from './composants/Sections';

/**
 * Lot L6b (E10) : vitrine publique de JURIKA, entierement refaite pour le premier lancement.
 *
 * Sections, dans l'ordre : accroche ; probleme et solution ; fonctionnalites ; parcours
 * couverts ; securite et confidentialite ; tarifs (config/pricing.ts, seule source) ; essai
 * gratuit ; questions frequentes ; contact. Pied de page : conditions generales, politique de
 * confidentialite (a venir), mentions legales de l'editeur (Parametres de l'editeur).
 *
 * Remplace le portage du site marketing (Lenis, GSAP, Playfair, maquettes d'ecran a donnees
 * fictives, compteurs animes) : jetons du systeme de design, police auto-hebergee, aucune
 * animation d'entree, aucun decalage au chargement.
 */
export function VitrineCadre({ surAccueil, children }: { surAccueil: boolean; children: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col bg-bg text-fg">
      <a
        href="#contenu"
        className="sr-only z-50 rounded-md bg-bg-raised px-4 py-2 text-sm font-medium text-fg shadow-card-lifted focus:not-sr-only focus:fixed focus:left-4 focus:top-4"
      >
        Aller au contenu principal
      </a>
      <EnTeteVitrine surAccueil={surAccueil} />
      <main id="contenu" tabIndex={-1} className="flex-1 focus:outline-none">
        {children}
      </main>
      <PiedVitrine surAccueil={surAccueil} />
    </div>
  );
}

export function LandingPage() {
  const { hash } = useLocation();

  useEffect(() => {
    document.title = 'JURIKA — Gestion juridique des entreprises';
  }, []);

  // Arrivee par un lien "/#section" (depuis une autre page) : la section est montree.
  useEffect(() => {
    if (!hash) return;
    document.getElementById(decodeURIComponent(hash.slice(1)))?.scrollIntoView?.();
  }, [hash]);

  return (
    <VitrineCadre surAccueil>
      <Accroche />
      <ProblemeSolution />
      <Fonctionnalites />
      <Parcours />
      <Securite />
      <Tarifs />
      <Essai />
      <Questions />
      <Contact />
    </VitrineCadre>
  );
}
