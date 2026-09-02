/**
 * Derivations PURES pour les graphiques du tableau de bord CLIENT.
 *
 * Ces fonctions ne font AUCUN appel reseau : elles transforment les donnees
 * DEJA agregees et scopees aux dossiers du client (ses demandes, les requetes
 * de son conseiller) en series pretes pour Recharts. Aucune donnee factice :
 * une entree vide produit un tableau vide -> l'UI affiche « Aucune donnee ».
 */
import type { DemandeSummary, RequeteStatut, RequeteSummary } from '../types/dataroom';

export interface Slice {
  /** Cle brute (statut/groupe) — pour le mapping couleur. */
  key: string;
  /** Libelle affiche (FR). */
  label: string;
  count: number;
}

/** Ordre canonique des statuts de demande pour un rendu stable du donut. */
const DEMANDE_ORDER: { key: string; label: string }[] = [
  { key: 'NON_TRAITEE', label: 'Non traitée' },
  { key: 'EN_COURS', label: 'En cours' },
  { key: 'TRAITEE', label: 'Traitée' },
];

/**
 * Repartition de MES demandes (client -> conseiller) par statut. Ordre
 * canonique, statuts a 0 exclus (un donut n'affiche pas de tranche vide).
 * Retour [] si aucune demande.
 */
export function demandesByStatut(demandes: DemandeSummary[]): Slice[] {
  if (!demandes || demandes.length === 0) return [];
  const counts = new Map<string, number>();
  for (const d of demandes) counts.set(d.statut, (counts.get(d.statut) ?? 0) + 1);
  return DEMANDE_ORDER.filter((o) => (counts.get(o.key) ?? 0) > 0).map((o) => ({
    key: o.key,
    label: o.label,
    count: counts.get(o.key) ?? 0,
  }));
}

/**
 * Regroupement des statuts de requete (conseiller -> client) tel que vu par le
 * client sur la page « Demandes de mon conseiller » : « À faire » (OUVERTE +
 * A_COMPLETER) / « En attente de validation » (REPONDUE) / « Terminé »
 * (CLOTUREE).
 */
const REQUETE_GROUPS: { key: string; label: string; match: RequeteStatut[] }[] = [
  { key: 'A_FAIRE', label: 'À faire', match: ['OUVERTE', 'A_COMPLETER'] },
  { key: 'ATTENTE', label: 'En attente de validation', match: ['REPONDUE'] },
  { key: 'TERMINE', label: 'Terminé', match: ['CLOTUREE'] },
];

/** Statuts de requete « actionnables » par le client (a traiter). */
export const REQUETE_A_FAIRE: RequeteStatut[] = ['OUVERTE', 'A_COMPLETER'];

/**
 * Repartition des requetes de mon conseiller par groupe de statut. Groupes a 0
 * exclus. Retour [] si aucune requete.
 */
export function requetesByStatut(requetes: RequeteSummary[]): Slice[] {
  if (!requetes || requetes.length === 0) return [];
  return REQUETE_GROUPS.map((g) => ({
    key: g.key,
    label: g.label,
    count: requetes.filter((r) => g.match.includes(r.statut)).length,
  })).filter((s) => s.count > 0);
}
