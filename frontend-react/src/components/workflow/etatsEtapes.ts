import type { ElementFil } from '../ui/FilAriane';
import type { Ticket } from '../../types/ticket';

/** Lot L6b (E5) : etat d'une etape dans la frise d'un parcours. */
export type EtatEtape = 'validee' | 'affichee' | 'atteinte' | 'verrouillee' | 'bloquee';

export const TEXTE_ETAT: Record<EtatEtape, string> = {
  validee: 'validée',
  affichee: 'affichée',
  atteinte: 'en cours',
  verrouillee: 'verrouillée',
  bloquee: 'bloquée',
};

/**
 * Une etape est validee avant l'etape la plus avancee, atteinte quand elle l'est (et que
 * l'ecran en montre une autre), verrouillee au-dela ; l'etape affichee prime.
 */
export function etatEtape(numero: number, plusAvancee: number, affichee: number, bloquee?: boolean): EtatEtape {
  if (numero === affichee) return bloquee ? 'bloquee' : 'affichee';
  if (numero < plusAvancee) return 'validee';
  if (numero === plusAvancee) return 'atteinte';
  return 'verrouillee';
}

/** Fil d'Ariane d'un parcours : Accueil > Tickets > reference du ticket > Parcours. */
export function filDuParcours(ticket: Ticket): ElementFil[] {
  return [
    { libelle: 'Accueil', to: '/dashboard' },
    { libelle: 'Tickets', to: '/tickets' },
    { libelle: ticket.reference || 'Ticket', to: `/tickets/${ticket.id}` },
    { libelle: 'Parcours' },
  ];
}
