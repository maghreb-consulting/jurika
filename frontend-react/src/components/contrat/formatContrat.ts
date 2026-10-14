import type { ConditionsVue, ModePaiement, Rythme } from '../../types/contrat';

/** Lot L7 (E6) : libelles et formats du contrat d'abonnement. */

export const LIBELLE_STATUT: Record<string, string> = {
  AUCUN: 'Aucun contrat',
  EN_ATTENTE: 'En attente d’acceptation',
  ACCEPTE: 'Accepté',
  REMPLACE: 'Remplacé par de nouvelles conditions',
  ANNULE: 'Annulé avant acceptation',
  RESILIE: 'Résilié',
};

export const LIBELLE_RYTHME: Record<Rythme, string> = { MENSUEL: 'Mensuel', ANNUEL: 'Annuel' };

export const LIBELLE_MODE: Record<ModePaiement, string> = {
  CARTE: 'Carte bancaire en ligne',
  VIREMENT: 'Virement bancaire',
  CHEQUE: 'Chèque',
  ESPECES: 'Espèces',
};

/** Date du serveur "AAAA-MM-JJ" (sans fuseau) en toutes lettres. */
export function dateLongue(iso: string | null | undefined): string {
  if (!iso) return '—';
  const [a, m, j] = iso.slice(0, 10).split('-').map(Number);
  if (!a || !m || !j) return iso;
  return new Date(a, m - 1, j).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
}

/** Horodatage "AAAA-MM-JJ HH:MM:SS" (heure de Casablanca, deja converti par le serveur). */
export function horodatageCasablanca(s: string): string {
  return `${dateLongue(s.slice(0, 10))} à ${s.slice(11, 16)} (heure de Casablanca)`;
}

export function montant(n: number): string {
  return `${new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n)} DH HT`;
}

export function prix(c: ConditionsVue): string {
  return `${montant(c.prixHt)} ${c.rythmePaiement === 'MENSUEL' ? 'par mois' : 'par an'}`;
}
