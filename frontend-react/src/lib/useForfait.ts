import { useEffect, useState } from 'react';
import { billingService } from '../services/billing.service';
import type { AbonnementVue } from '../types/billing';

/**
 * Lot L7 (E5), CDC 4.5 : forfait du cabinet, lu une fois (GET /billing/abonnement) et partage par
 * les ecrans qui masquent une fonction hors forfait. Le serveur reste juge (P6) : tant que l'etat
 * n'est pas connu, ou si la lecture echoue, rien n'est masque a l'ecran (le serveur refusera, avec
 * son message).
 */
let enCours: Promise<AbonnementVue | null> | null = null;

function charger(): Promise<AbonnementVue | null> {
  if (!enCours) {
    enCours = billingService.getAbonnement().catch(() => {
      enCours = null;
      return null;
    });
  }
  return enCours;
}

/** Ecrans montes a prevenir quand l'etat lu est oublie (recette du 2026-10-17). */
const abonnes = new Set<() => void>();

/**
 * Oublie l'etat lu (apres un changement de forfait) et fait relire les ecrans deja montes. Avant,
 * seul le cache etait vide : un ecran affiche gardait l'etat de l'essai jusqu'au rechargement.
 */
export function oublierForfait(): void {
  enCours = null;
  abonnes.forEach((relire) => relire());
}

// Retour sur l'onglet : le forfait a pu changer ailleurs (virement enregistre par le
// super-admin, souscription dans un autre onglet) ; l'etat est relu.
if (typeof document !== 'undefined') {
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible' && abonnes.size > 0) oublierForfait();
  });
}

export interface EtatForfait {
  abonnement: AbonnementVue | null;
  /** Lot L6b : vrai une fois la lecture terminee (reussie ou non). */
  charge: boolean;
  /** Faux seulement si le serveur a dit que la fonction n'est pas incluse. */
  inclut: (code: string) => boolean;
  /** Premier forfait qui donne la fonction (pour l'explication). */
  disponibleDes: (code: string) => string | null;
}

export function useForfait(actif = true): EtatForfait {
  const [abonnement, setAbonnement] = useState<AbonnementVue | null>(null);
  const [charge, setCharge] = useState(false);
  useEffect(() => {
    if (!actif) return undefined;
    let annule = false;
    const lire = () => {
      void charger().then((a) => { if (!annule) { setAbonnement(a); setCharge(true); } });
    };
    lire();
    abonnes.add(lire);
    return () => { annule = true; abonnes.delete(lire); };
  }, [actif]);
  const fonction = (code: string) => abonnement?.fonctions?.find((f) => f.code === code);
  return {
    abonnement,
    charge,
    inclut: (code) => fonction(code)?.incluse !== false,
    disponibleDes: (code) => fonction(code)?.disponibleDes ?? null,
  };
}
