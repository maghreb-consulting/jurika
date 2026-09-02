/**
 * Objet social — plusieurs activités (2026-08).
 *
 * Le formulaire (Step4Activite) permet de saisir plusieurs activités : soit un
 * tableau `activites[]`, soit une zone multi-lignes `description` (une activité
 * par ligne). Ce helper normalise l'entrée en une liste d'activités et produit
 * la valeur `OBJET_SOCIAL` injectée dans les Statuts :
 *   - 1 activité  → phrase simple (comportement historique).
 *   - N activités → une activité par ligne, préfixée d'un tiret « - » (les
 *     retours à la ligne sont convertis en vrais `<w:br/>` Word par le moteur
 *     DocxTemplateEngine, sans toucher au texte du modèle directeur).
 *
 * Partagé par les deux constructeurs de payload (buildPayloadCreationSarl et
 * Step7Generation.buildPayload) pour éviter toute divergence de rendu.
 */

/** Normalise l'entrée activités en une liste propre (déduplication des vides). */
export function normalizeActivites(
  activites: unknown,
  description: unknown,
): string[] {
  const fromArray = Array.isArray(activites)
    ? (activites as unknown[]).map((a) => String(a ?? '').trim())
    : [];
  const list = fromArray.length
    ? fromArray
    : String(description ?? '')
        .split(/\r?\n/)
        .map((l) => l.trim());
  return list.filter((l) => l.length > 0);
}

/**
 * Valeur `OBJET_SOCIAL` formatée : phrase pour 1 activité, liste à tirets
 * (séparée par des retours à la ligne) pour plusieurs. Retombe sur la
 * description brute si aucune activité n'est isolable.
 */
export function formatObjetSocial(
  activites: unknown,
  description: unknown,
): string {
  const list = normalizeActivites(activites, description);
  if (list.length === 0) return String(description ?? '').trim();
  if (list.length === 1) return list[0];
  return list.map((a) => `- ${a}`).join('\n');
}
