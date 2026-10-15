import { useEffect, useRef, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { EnTeteVitrine } from './composants/EnTeteVitrine';
import { PiedVitrine } from './composants/PiedVitrine';
import { useApparitions } from './composants/useApparitions';
import './vitrine.css';
import {
  Accroche,
  BandeOperations,
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
 * Lot L6b (E10) : vitrine publique de JURIKA.
 *
 * Sections, dans l'ordre : accroche (avec la capture du tableau de bord) ; bande des
 * operations ; probleme et solution ; fonctionnalites (piliers alternes avec captures, bande de
 * l'IA) ; parcours couverts ; securite et confidentialite ; tarifs (config/pricing.ts, seule
 * source) ; essai gratuit ; questions frequentes ; contact. Pied de page : conditions
 * generales, politique de confidentialite (a venir), mentions legales de l'editeur.
 *
 * V2 (demande du 2026-10-15) : mise en page et style riches de la vitrine d'avant L6b
 * restaures, sur les jetons du systeme de design, avec de vraies captures de la plateforme ;
 * sans Lenis, GSAP, Playfair, maquettes a donnees fictives, compteurs ni bandeau defilant.
 * Apparitions au defilement courtes (useApparitions), coupees par "reduire les animations".
 */
export function VitrineCadre({ surAccueil, children }: { surAccueil: boolean; children: ReactNode }) {
  const racine = useRef<HTMLDivElement>(null);
  useApparitions(racine);
  return (
    <div ref={racine} className="vitrine flex min-h-screen flex-col bg-bg text-fg">
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
      <BandeOperations />
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
