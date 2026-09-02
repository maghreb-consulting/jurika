/**
 * TOTP RFC 6238 (HMAC-SHA1, 6 chiffres, pas de 30 s) — sans dependance.
 * Utilise pour rejouer la 2FA du compte de demonstration dans le script de
 * captation video : le code saisi a l'ecran est un VRAI code, calcule au
 * moment de la frappe, pas une valeur figee.
 */
import { createHmac } from 'node:crypto';

const B32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

export function base32Decode(input) {
    const clean = input.replace(/=+$/, '').toUpperCase().replace(/\s+/g, '');
    let bits = 0;
    let value = 0;
    const out = [];
    for (const ch of clean) {
        const idx = B32.indexOf(ch);
        if (idx === -1) throw new Error(`Caractere base32 invalide : ${ch}`);
        value = (value << 5) | idx;
        bits += 5;
        if (bits >= 8) {
            bits -= 8;
            out.push((value >>> bits) & 0xff);
        }
    }
    return Buffer.from(out);
}

export function totp(secretBase32, atMs = Date.now(), step = 30, digits = 6) {
    const counter = Math.floor(atMs / 1000 / step);
    const buf = Buffer.alloc(8);
    buf.writeBigUInt64BE(BigInt(counter));
    const hmac = createHmac('sha1', base32Decode(secretBase32)).update(buf).digest();
    const offset = hmac[hmac.length - 1] & 0x0f;
    const code = ((hmac[offset] & 0x7f) << 24)
        | ((hmac[offset + 1] & 0xff) << 16)
        | ((hmac[offset + 2] & 0xff) << 8)
        | (hmac[offset + 3] & 0xff);
    return String(code % 10 ** digits).padStart(digits, '0');
}

/** Millisecondes restantes dans la fenetre TOTP courante. */
export function msLeftInWindow(atMs = Date.now(), step = 30) {
    return step * 1000 - (atMs % (step * 1000));
}
