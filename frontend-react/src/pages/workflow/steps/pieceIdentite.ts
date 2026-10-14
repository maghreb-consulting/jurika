/**
 * Lot L4a (E3) : piece d'identite d'un associe ou d'un gerant -- CIN, passeport ou carte
 * de sejour. Avant, la creation n'acceptait qu'une CIN marocaine : un associe etranger ne
 * pouvait pas etre saisi.
 *
 * Le format controle n'est celui de la CIN marocaine que pour une CIN. Pour un passeport
 * ou une carte de sejour, aucun format officiel n'est fixe ici : le controle se limite a
 * la vraisemblance (lettres et chiffres), il n'invente pas de regle.
 */

/** Type absent : CIN (le champ historique est le n de CIN). */
export function estCin(type: string | undefined | null): boolean {
  return !type || type === 'CIN';
}

export function pieceInvalidMessage(type: string | undefined | null, numero: string): string | null {
  if (!numero) return null;
  const v = numero.replace(/\s+/g, '').toUpperCase();
  if (estCin(type)) {
    return /^[A-Z]{1,2}[0-9]{5,7}$/.test(v) ? null : 'Numéro de CIN invalide (ex. : X 123456).';
  }
  return /^[A-Z0-9-]{4,20}$/.test(v) ? null : 'Numéro de pièce invalide : lettres et chiffres uniquement.';
}

/** La lecture automatique ne sait lire que la CIN : les autres pièces se joignent simplement. */
export function pieceSansExtraction(type: string | undefined | null): boolean {
  return !estCin(type);
}

export function libelleNumeroPiece(type: string | undefined | null): string {
  if (estCin(type)) return 'N° CIN';
  if (type === 'passeport') return 'N° de passeport';
  return 'N° de la carte de séjour';
}
