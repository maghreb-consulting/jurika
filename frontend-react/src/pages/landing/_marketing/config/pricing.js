/**
 * Pricing tiers — V1 publique JURIKA (landing _marketing inclus dans le SPA).
 *
 * Source unique de vérité : backend PlanCatalog (jurika-common) exposé via
 * GET /api/v1/public/pricing. Les composants landing (Pricing.jsx, FAQ.jsx)
 * doivent lire cette API en priorité ; cette grille statique sert de
 * FALLBACK lors du 1er paint ou si l'API tombe.
 *
 * Spec directeur 2026-06-02 (SPEC_TARIFS_CANONIQUE.md) :
 *  - Codes canoniques : essentiel / business / entreprise
 *  - Labels : Essentiel / Business / Entreprise
 *  - Prix : 499 / 1 199 / sur devis (MAD, mensuel HT)
 *  - Annuel : 4 999 / 11 999 (~2 mois offerts)
 *
 * ⚠️ Toute modification ici doit être reportée dans backend PlanCatalog
 * (sinon la landing dérive de l'API). Pour le marketing-site (repo séparé),
 * voir marketing-site/src/config/pricing.js.
 */

export const PRICING_TIERS = [
  {
    id: 'essentiel',
    label: 'Essentiel',
    price: 499,
    currency: 'DH',
    period: 'mois',
    annualPrice: 4999,
    target: 'Petites structures, 2 utilisateurs max',
    quotas: { maxUsers: 2, maxDossiers: 30, maxStorageGb: 5 },
    features: [
      '2 utilisateurs',
      '30 dossiers actifs',
      'Tous les workflows',
      'Data Room 5 Go',
      'Chat client-employé',
      'Génération de documents par IA',
    ],
    cta: { label: "S'inscrire", style: 'btn-outline' },
    featured: false,
  },
  {
    id: 'business',
    label: 'Business',
    price: 1199,
    currency: 'DH',
    period: 'mois',
    annualPrice: 11999,
    target: 'Cabinets en croissance, 6 utilisateurs max',
    quotas: { maxUsers: 6, maxDossiers: 100, maxStorageGb: 20 },
    features: [
      '6 utilisateurs',
      '100 dossiers actifs',
      'Tous les workflows',
      'Data Room 20 Go',
      'Chat client-employé',
      'Génération de documents par IA',
      'Chatbot RAG (assistant juridique)',
    ],
    cta: { label: 'Choisir Business', style: 'btn-primary' },
    featured: true,
    ribbon: 'Recommandé',
  },
  {
    id: 'entreprise',
    label: 'Entreprise',
    price: null,
    currency: 'DH',
    period: 'mois',
    annualPrice: null,
    target: 'Réseaux & cabinets multi-sites',
    priceLabel: 'Sur devis',
    quotas: { maxUsers: -1, maxDossiers: -1, maxStorageGb: -1 },
    features: [
      'Utilisateurs illimités',
      'Plus de 100 dossiers',
      'Tous les workflows',
      'Data Room sur mesure',
      'Chat client-employé',
      'Génération de documents par IA',
      'Chatbot RAG',
      'Accompagnement dédié',
    ],
    cta: { label: 'Contacter ventes', style: 'btn-outline' },
    featured: false,
  },
];

/**
 * URL absolue du SPA cible (env `VITE_APP_URL_ABSOLUTE`, sinon vide pour
 * laisser React Router gérer les paths internes).
 */
export const APP_URL = (typeof import.meta !== 'undefined' && import.meta.env?.VITE_APP_URL_ABSOLUTE) || '';

/**
 * URL backend public (env `VITE_API_URL`, défaut prod `api.jurika.ai`).
 */
export const API_URL = (typeof import.meta !== 'undefined' && import.meta.env?.VITE_API_URL) || 'https://api.jurika.ai';

/**
 * Format prix MAD avec séparateur de milliers FR.
 */
export function formatPrice(value) {
  return value.toLocaleString('fr-MA').replace(/ /g, ' ');
}

/**
 * Construit l'URL signup vers le SPA avec query `?plan=`.
 * Codes canoniques (spec 2026-06-02) : essentiel | business | entreprise.
 */
export function signupUrl(planId) {
  return `${APP_URL}/signup?plan=${planId}`;
}
