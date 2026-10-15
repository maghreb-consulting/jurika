import type { ImpayeVue } from '../types/billing';

/**
 * Lot L7 (E8), article 4.4 du contrat : textes d'une échéance impayée (bandeau de l'en-tête du
 * cabinet, écran Facturation). Sans paiement 15 jours après l'échéance et après un rappel, l'accès
 * est suspendu ; rien n'est supprimé et l'accès est rétabli dès le paiement constaté.
 */

const JOUR = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' });
const INSTANT = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'Africa/Casablanca' });

/** Date du calendrier (aaaa-mm-jj) ou instant, en toutes lettres : « 16 octobre 2026 ». */
export function jourEnLettres(iso: string | null | undefined): string {
  if (!iso) return '';
  // Un instant (rappel envoyé, suspension) se lit à l'heure de Casablanca.
  if (iso.includes('T')) {
    const d = new Date(iso);
    if (!Number.isNaN(d.getTime())) return INSTANT.format(d).replace(/^1 /, '1er ');
  }
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!m) return iso;
  return JOUR.format(new Date(Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3])))).replace(/^1 /, '1er ');
}

/** Veille d'une date du calendrier (dernier jour pour payer avant la suspension). */
export function veille(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!m) return iso;
  const d = new Date(Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3]) - 1));
  return d.toISOString().slice(0, 10);
}

export const MOYENS_DE_PAYER =
  'carte bancaire en ligne, virement, chèque ou espèces (un virement, un chèque ou des espèces comptent dès que JURIKA a constaté leur réception)';

/** Texte du bandeau et de l'encadré de la page Facturation (exporté pour les tests). */
export function messageImpaye(i: ImpayeVue | null | undefined, superviseur: boolean): { titre: string; detail: string } | null {
  if (!i) return null;
  const echeance = jourEnLettres(i.echeance);
  const suspension = jourEnLettres(i.suspensionLe);
  const qui = superviseur
    ? `Payez l’échéance depuis l’écran Facturation, bouton « Payer l’échéance » : ${MOYENS_DE_PAYER}.`
    : 'Seul le superviseur du cabinet peut régler l’échéance : prévenez-le.';
  if (i.suspendu) {
    return {
      titre: `Accès au cabinet suspendu : l’échéance du ${echeance} n’est pas réglée.`,
      detail: `${superviseur
        ? `Payez l’échéance (${MOYENS_DE_PAYER}) : l’accès est rétabli dès le paiement constaté, dans l’état exact où vous l’avez laissé.`
        : 'Seul le superviseur peut se connecter pour la régler ; l’accès sera rétabli dès le paiement constaté.'} Rien n’a été supprimé.${
        i.arretLe ? ` Une résiliation est enregistrée : sans paiement, le forfait s’arrête le ${jourEnLettres(i.arretLe)} et le cabinet sera archivé.` : ''}`,
    };
  }
  return {
    titre: `Échéance du ${echeance} non réglée : l’accès sera suspendu le ${suspension} sans paiement.`,
    detail: `Dernier jour pour que le paiement soit constaté : le ${jourEnLettres(veille(i.suspensionLe))}. ${qui} Aucune donnée n’est supprimée ; une suspension est levée dès le paiement constaté.`,
  };
}
