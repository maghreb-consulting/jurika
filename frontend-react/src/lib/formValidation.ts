/**
 * Helpers de validation de formulaire (charte JURIKA).
 *
 * Objectif : remplacer la validation NATIVE du navigateur (bulles « Veuillez
 * renseigner ce champ », `type="email"`, `pattern`…) par une validation
 * JavaScript exécutée AVANT l'appel API, avec des messages français homogènes
 * affichés dans les composants stylés de l'app (prop `error` de `TextField`,
 * bandeau d'erreur, toast).
 *
 * Les attributs `required` / `type="email"` / `pattern` sont CONSERVÉS sur les
 * champs pour la sémantique et l'accessibilité, mais chaque `<form>` porte
 * `noValidate` : c'est ce module qui décide de la validité effective.
 *
 * Convention : un validateur renvoie `string | null`.
 *   - `null`  → champ valide
 *   - `string`→ message d'erreur à afficher (déjà en français, prêt à l'emploi)
 */

/** Libellés d'erreur standard — source unique de vérité pour la cohérence. */
export const validationMessages = {
  required: 'Ce champ est requis.',
  email: 'Adresse e-mail invalide.',
  phone: 'Numéro de téléphone invalide.',
  minLength: (n: number) => `Ce champ doit contenir au moins ${n} caractère${n > 1 ? 's' : ''}.`,
  maxLength: (n: number) => `Ce champ ne doit pas dépasser ${n} caractère${n > 1 ? 's' : ''}.`,
  pattern: 'Format invalide.',
  numeric: 'Veuillez saisir une valeur numérique.',
  positive: 'Veuillez saisir une valeur positive.',
  integer: 'Veuillez saisir un nombre entier.',
  mismatch: 'Les deux valeurs ne correspondent pas.',
} as const;

// Regex e-mail pragmatique (aligné sur la validation HTML5 usuelle, sans être
// trop laxiste). On évite les faux négatifs sur les adresses réelles.
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Vrai si `value` est une chaîne non vide (après trim). */
export function isFilled(value: string | null | undefined): boolean {
  return typeof value === 'string' && value.trim().length > 0;
}

/** Vrai si `value` est une adresse e-mail plausible. */
export function isEmail(value: string): boolean {
  return EMAIL_RE.test(value.trim());
}

/** Champ obligatoire. */
export function requiredMsg(
  value: string | null | undefined,
  message: string = validationMessages.required,
): string | null {
  return isFilled(value) ? null : message;
}

/** Champ e-mail obligatoire (vide → requis, mal formé → email). */
export function emailMsg(
  value: string | null | undefined,
  { required = true }: { required?: boolean } = {},
): string | null {
  if (!isFilled(value)) return required ? validationMessages.required : null;
  return isEmail(value!) ? null : validationMessages.email;
}

/** Longueur minimale (n'impose PAS le caractère obligatoire ; combiner avec `requiredMsg`). */
export function minLengthMsg(value: string | null | undefined, n: number): string | null {
  if (!isFilled(value)) return null;
  return value!.trim().length >= n ? null : validationMessages.minLength(n);
}

/** Longueur maximale. */
export function maxLengthMsg(value: string | null | undefined, n: number): string | null {
  if (value == null) return null;
  return value.length <= n ? null : validationMessages.maxLength(n);
}

/** Vérifie un `pattern` (RegExp). Champ vide → valide (combiner avec `requiredMsg`). */
export function patternMsg(
  value: string | null | undefined,
  re: RegExp,
  message: string = validationMessages.pattern,
): string | null {
  if (!isFilled(value)) return null;
  return re.test(value!) ? null : message;
}

/** Deux valeurs doivent être identiques (ex : confirmation de mot de passe). */
export function matchMsg(a: string, b: string, message: string = validationMessages.mismatch): string | null {
  return a === b ? null : message;
}

/**
 * Compose plusieurs validateurs et renvoie le PREMIER message d'erreur, ou
 * `null` si tout est valide.
 *
 *   const err = firstError(
 *     () => requiredMsg(email),
 *     () => emailMsg(email),
 *   );
 */
export function firstError(...checks: Array<() => string | null>): string | null {
  for (const check of checks) {
    const msg = check();
    if (msg) return msg;
  }
  return null;
}

/**
 * Type d'un jeu d'erreurs par champ : `{ [name]: message }`.
 * Une clé absente (ou `undefined`) = champ valide.
 */
export type FieldErrors<K extends string = string> = Partial<Record<K, string>>;

/** Vrai si l'objet d'erreurs contient au moins une erreur. */
export function hasErrors(errors: FieldErrors): boolean {
  return Object.values(errors).some((m) => typeof m === 'string' && m.length > 0);
}

/**
 * Donne le focus au premier champ en erreur (accessibilité).
 * Recherche `[name="<champ>"]` dans le `form`/document ; utilise l'ordre
 * fourni par `order` (l'ordre visuel du formulaire) pour être déterministe.
 *
 * @param errors  map champ → message
 * @param order   ordre des champs (noms) tel qu'affiché ; défaut = clés de `errors`
 * @param root    élément racine où chercher (défaut : document)
 */
export function focusFirstError(
  errors: FieldErrors,
  order?: string[],
  root: ParentNode = document,
): void {
  const names = (order ?? Object.keys(errors)).filter((n) => errors[n]);
  for (const name of names) {
    const el = root.querySelector<HTMLElement>(
      `[name="${CSS.escape(name)}"]`,
    );
    if (el) {
      el.focus();
      return;
    }
  }
}
