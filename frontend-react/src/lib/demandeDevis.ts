import { adresseContact } from '../pages/legal/contact';

/**
 * Lot L7 : demande de devis (forfait Sur Devis). Ouvre la messagerie vers l'adresse de contact de
 * JURIKA quand elle est configuree (VITE_CONTACT_EMAIL, contact@jurika.ma a la mise en production) ;
 * sinon, rend le message a afficher. Plus d'adresse codee en dur (contact@jurika.ai, domaine qui
 * n'appartient pas a JURIKA).
 *
 * @returns null si la messagerie a ete ouverte, sinon le message a afficher.
 */
export function demanderDevis(ouvrir: (url: string) => void = (u) => { window.location.href = u; }): string | null {
  const a = adresseContact();
  if (a) {
    ouvrir(`mailto:${a}?subject=${encodeURIComponent('Demande de devis (forfait Sur Devis)')}`);
    return null;
  }
  return 'Le forfait Sur Devis s’établit avec JURIKA. L’adresse de contact n’est pas encore publiée : choisissez un autre forfait pour commencer, il pourra être changé ensuite.';
}

/** Message d'erreur de la demande de devis, avec l'adresse de contact si elle est configuree. */
export function messageEchecDevis(): string {
  const a = adresseContact();
  return a
    ? `La demande de devis n’a pas pu être envoyée. Écrivez à ${a}.`
    : 'La demande de devis n’a pas pu être envoyée. Réessayez plus tard.';
}
