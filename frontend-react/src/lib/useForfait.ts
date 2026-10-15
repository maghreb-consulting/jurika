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

/** Oublie l'etat lu (apres un changement de forfait). */
export function oublierForfait(): void {
  enCours = null;
}

export interface EtatForfait {
  abonnement: AbonnementVue | null;
  /** Faux seulement si le serveur a dit que la fonction n'est pas incluse. */
  inclut: (code: string) => boolean;
  /** Premier forfait qui donne la fonction (pour l'explication). */
  disponibleDes: (code: string) => string | null;
}

export function useForfait(actif = true): EtatForfait {
  const [abonnement, setAbonnement] = useState<AbonnementVue | null>(null);
  useEffect(() => {
    if (!actif) return undefined;
    let annule = false;
    void charger().then((a) => { if (!annule) setAbonnement(a); });
    return () => { annule = true; };
  }, [actif]);
  const fonction = (code: string) => abonnement?.fonctions?.find((f) => f.code === code);
  return {
    abonnement,
    inclut: (code) => fonction(code)?.incluse !== false,
    disponibleDes: (code) => fonction(code)?.disponibleDes ?? null,
  };
}
