/**
 * Lot L6b : identifiant unique (UUID v4) utilisable partout, y compris hors contexte securise.
 *
 * crypto.randomUUID n'existe que dans un contexte securise (HTTPS ou localhost) ; la plateforme
 * servie en HTTP sur le reseau local (Z440) le voit absent et l'etape 5 de la creation plantait.
 * crypto.getRandomValues, lui, est disponible partout.
 */
export function identifiantUnique(): string {
  const c = globalThis.crypto as Crypto | undefined;
  if (c && typeof c.randomUUID === 'function') return c.randomUUID();
  const o = new Uint8Array(16);
  c!.getRandomValues(o);
  o[6] = (o[6] & 0x0f) | 0x40; // version 4
  o[8] = (o[8] & 0x3f) | 0x80; // variante RFC 4122
  const h = Array.from(o, (x) => x.toString(16).padStart(2, '0')).join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}
