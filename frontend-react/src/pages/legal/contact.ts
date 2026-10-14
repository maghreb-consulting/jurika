/**
 * Lot L7 (E3) : adresse de contact affichee par la vitrine et les pages legales.
 *
 * L'adresse est fixee AU BUILD par la variable VITE_CONTACT_EMAIL (valeur de
 * production : contact@jurika.ma, documentee dans infrastructure/production/.env.prod.example).
 * Tant que jurika.ma n'est pas deploye, la variable reste vide : aucune adresse n'est
 * affichee (aucune valeur de repli, qui publierait une adresse non ouverte).
 */
export function adresseContact(): string | null {
  const valeur: unknown = import.meta.env.VITE_CONTACT_EMAIL;
  if (typeof valeur !== 'string') return null;
  const adresse = valeur.trim();
  // Une valeur sans "@" n'est pas une adresse : elle ne s'affiche pas.
  return adresse.includes('@') ? adresse : null;
}
