import { createHmac } from 'node:crypto';

/**
 * Lot L6b (E13) : code TOTP (RFC 6238, SHA-1, 6 chiffres, pas de 30 s) calcule en Node, pour
 * configurer puis passer la double authentification des comptes de test. Le secret reste en
 * memoire : jamais ecrit dans un fichier, un journal ou un rapport.
 */
function base32(secret: string): Buffer {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = '';
  for (const c of secret.replace(/=+$/, '').replace(/\s+/g, '').toUpperCase()) {
    const i = alphabet.indexOf(c);
    if (i < 0) throw new Error('secret TOTP illisible (caractere hors base 32)');
    bits += i.toString(2).padStart(5, '0');
  }
  const octets: number[] = [];
  for (let i = 0; i + 8 <= bits.length; i += 8) octets.push(parseInt(bits.slice(i, i + 8), 2));
  return Buffer.from(octets);
}

export function codeTotp(secret: string, instant = Date.now()): string {
  const compteur = Buffer.alloc(8);
  compteur.writeBigUInt64BE(BigInt(Math.floor(instant / 30_000)));
  const h = createHmac('sha1', base32(secret)).update(compteur).digest();
  const o = h[h.length - 1] & 15;
  return String((h.readUInt32BE(o) & 0x7fffffff) % 1_000_000).padStart(6, '0');
}

/** Fenetre de 30 s d'un instant (l'anti-rejeu du serveur refuse un second code de la meme fenetre). */
export function fenetreTotp(instant = Date.now()): number {
  return Math.floor(instant / 30_000);
}
