import type { BillingPeriod, PlanCode, PricingTier } from '../types/billing';

/**
 * Lot L7 (E5) : grille de secours, reprise du cahier des charges (paragraphes 4.3 et 4.5).
 * Utilisee seulement pendant le premier affichage ou si /public/pricing ne repond pas ; la
 * source reste le catalogue du serveur (PlanCatalog). Prix HORS TAXES.
 */
const FONCTIONS = [
  { code: 'ALERTES_DELAIS', libelle: 'Alertes de délais légaux', des: 2 },
  { code: 'MESSAGERIE_INTERNE', libelle: 'Messagerie interne', des: 2 },
  { code: 'TABLEAUX_DE_BORD_PRODUCTIVITE', libelle: 'Tableaux de bord de productivité', des: 3 },
  { code: 'EDITION_INTEGREE_ACTES', libelle: 'Édition des actes intégrée à la plateforme', des: 3 },
  { code: 'RAPPORTS_ACTIVITE', libelle: 'Rapports d’activité', des: 3 },
  { code: 'ENGAGEMENT_SERVICE', libelle: 'Engagement de service contractuel et support prioritaire', des: 4 },
];

function tier(code: PlanCode, label: string, rang: number, mensuel: number | null, annuel: number | null,
  employes: number, dossiers: number, go: number): PricingTier {
  const devis = mensuel === null;
  return {
    id: code, code, label, rang, currency: 'MAD', quoteOnly: devis,
    monthly: devis ? undefined : { priceMad: mensuel },
    yearly: devis || annuel === null ? undefined : { priceMad: annuel },
    totalAnnuelEnMensuelMad: devis ? undefined : mensuel * 12,
    quotas: {
      maxUsers: employes, maxDossiers: dossiers, maxStorageGb: go,
      usersUnlimited: employes < 0, dossiersUnlimited: dossiers < 0, storageUnlimited: go < 0,
    },
    fonctions: FONCTIONS.map((f) => ({ code: f.code, libelle: f.libelle, incluse: rang >= f.des })),
  };
}

export const GRILLE_CDC: PricingTier[] = [
  tier('essentiel', 'Essentiel', 1, 375, 3750, 1, 25, 5),
  tier('professionnel', 'Professionnel', 2, 750, 7500, 2, 50, 10),
  tier('max', 'Max', 3, 1500, 15000, 4, 100, 25),
  tier('sur_devis', 'Sur Devis', 4, null, null, -1, -1, -1),
];

/** Fonctions communes a tous les forfaits (CDC 4.5). */
export const FONCTIONS_COMMUNES = [
  'Workflows complets et génération des actes',
  'Data Room et accès client',
  'Chatbot juridique aux réponses sourcées',
  'Extraction automatique des pièces (CIN, certificat négatif)',
];

/** Lignes de quotas affichees pour un forfait. */
export function lignesQuotas(t: PricingTier): string[] {
  const q = t.quotas;
  if (t.quoteOnly) {
    return ['Plus de 100 dossiers par mois', 'Utilisateurs selon devis', 'Stockage selon devis'];
  }
  return [
    `Jusqu’à ${q.maxDossiers} dossiers par mois d’abonnement`,
    `1 superviseur + ${q.maxUsers} ${q.maxUsers > 1 ? 'accès employés' : 'accès employé'}`,
    `${q.maxStorageGb} Go de stockage`,
  ];
}

export const CODES_SOUSCRIPTIBLES: PlanCode[] = ['essentiel', 'professionnel', 'max'];

/** Cle sessionStorage : rythme choisi au premier ecran de l'inscription (repris au paiement). */
export const SIGNUP_PERIOD_STORAGE_KEY = 'jurika.signup.billingPeriod';

/** Rythme memorise a l'inscription (mensuel ou annuel), sinon null. */
export function lireRythmeInscription(): BillingPeriod | null {
  try {
    const v = sessionStorage.getItem(SIGNUP_PERIOD_STORAGE_KEY);
    return v === 'monthly' || v === 'yearly' ? v : null;
  } catch {
    return null;
  }
}
