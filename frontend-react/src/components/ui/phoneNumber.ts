import { isValidPhoneNumber } from 'libphonenumber-js';
import type { Country } from '../../data/countries';

/**
 * Fonctions du champ telephone (PhoneNumberInput), hors du fichier du composant
 * pour garder le rechargement a chaud (react-refresh/only-export-components).
 */

/**
 * Recompose l'E.164 a partir du pays + du numero national saisi. Le `0` de
 * tete (prefixe national) est retire (jamais present en E.164). Renvoie '' si
 * aucun chiffre national n'est saisi (le champ est alors considere vide).
 */
export function composeE164(country: Country, national: string): string {
  const digits = national.replace(/\D/g, '').replace(/^0+/, '');
  return digits ? `+${country.dialCode}${digits}` : '';
}

/**
 * Valide un numero E.164 pour le pays deduit de l'indicatif. S'appuie sur
 * libphonenumber-js (longueur + plage nationale selon le pays).
 */
export function isValidPhone(e164: string): boolean {
  if (!e164) return false;
  try {
    return isValidPhoneNumber(e164);
  } catch {
    return false;
  }
}
