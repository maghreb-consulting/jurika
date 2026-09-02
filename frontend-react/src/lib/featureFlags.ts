/**
 * Feature flags front — 2026-07-28.
 *
 * Petits interrupteurs pilotant l'exposition de fonctionnalites cote UI, pour
 * pouvoir (re)activer sans retrait en dur du code. Lus depuis `import.meta.env`
 * quand fourni (prefixe VITE_), avec un defaut sur.
 */

/** Lit une variable d'env VITE_* comme booleen ('true'/'1' => true). Defaut fourni sinon. */
function envBool(raw: string | undefined, fallback: boolean): boolean {
  if (raw == null || raw === '') return fallback;
  return raw === 'true' || raw === '1';
}

/**
 * 2FA par SMS. Desactive par defaut : aucun fournisseur SMS reel n'est branche
 * (le back loggue seulement). Tant que ce flag est faux, l'option SMS est
 * affichee « Hors service » (non selectionnable) partout ou le choix 2FA
 * apparait — TOTP reste l'option active. Reactiver via
 * VITE_SMS_2FA_ENABLED=true le jour ou un fournisseur SMS est configure.
 */
export const SMS_2FA_ENABLED: boolean = envBool(
  import.meta.env.VITE_SMS_2FA_ENABLED as string | undefined,
  false,
);
