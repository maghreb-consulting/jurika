/**
 * Règle des 15 jours entre la **dissolution** d'une société et la **clôture de sa
 * liquidation** (RG-LI03) — lot « Liquidation 4 étapes » (2026-08-13).
 *
 * Miroir exact de la validation backend `WorkflowSteps.liquidationDelaiError`
 * (workflow-service), appliquée par `LiquidationWorkflow`. Le front prévient, le
 * backend garantit : la règle n'est pas contournable en manipulant la requête.
 *
 * Ce module corrige quatre défauts de l'implémentation précédente :
 *   1. **seuil** — 16 jours était appliqué au lieu de 15 ;
 *   2. **calcul** — l'écart était calculé en millisecondes (`Date.now() - d) / 86400000`),
 *      donc sensible au fuseau horaire et aux changements d'heure ; il est désormais
 *      exprimé en **jours calendaires** (dates ISO interprétées en UTC, comme
 *      `convocationDelai.ts`) ;
 *   3. **point de comparaison** — le délai sépare la date de dissolution et la **date de
 *      l'AGE de clôture saisie**, et non « aujourd'hui », qui n'a aucune portée juridique ;
 *   4. **portée** — la règle est **bloquante** (elle l'était seulement au front, sous forme
 *      d'avertissement).
 */

/** Délai minimum entre la dissolution et la clôture de la liquidation (jours calendaires). */
export const LIQUIDATION_DELAI_JOURS = 15;

/**
 * Convertit une date ISO stricte `AAAA-MM-JJ` en instant UTC (minuit), ou `null`.
 *
 * On n'utilise **pas** `Date.parse` directement : il accepte des formats non ISO
 * (`01/05/2026` est lu comme une date américaine, en heure **locale**) — ce qui
 * réintroduirait précisément la sensibilité au fuseau que ce module élimine, sur une
 * valeur en plus erronée. Le format est donc validé par un motif strict, puis converti
 * via `Date.UTC`.
 */
function parseIsoUtc(dateIso: string): number | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec((dateIso ?? '').trim());
  if (!m) return null;
  const [annee, mois, jour] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const ts = Date.UTC(annee, mois - 1, jour);
  const d = new Date(ts);
  // Rejette les dates inexistantes normalisées par Date.UTC (ex. 2026-02-31 → 03-03).
  if (d.getUTCFullYear() !== annee || d.getUTCMonth() !== mois - 1 || d.getUTCDate() !== jour) {
    return null;
  }
  return ts;
}

/**
 * Nombre de jours calendaires entre deux dates ISO (AAAA-MM-JJ), ou `null` si l'une des
 * deux est absente / invalide. Calcul 100 % UTC : le résultat est un nombre exact de
 * jours, insensible au fuseau horaire et aux changements d'heure.
 */
export function joursCalendairesEntre(debutIso: string, finIso: string): number | null {
  const debut = parseIsoUtc(debutIso);
  const fin = parseIsoUtc(finIso);
  if (debut === null || fin === null) return null;
  return Math.round((fin - debut) / 86400000);
}

/**
 * Règle DURE des 15 jours (pure, testable). Retourne un message d'erreur FR si la clôture
 * intervient moins de 15 jours après la dissolution (ou avant elle), sinon `null`.
 *
 * Date de dissolution inconnue (dossier ancien) = pas de contrôle : on ne peut pas opposer
 * un délai dont on ignore le point de départ.
 *
 * @param dateDissolution date d'effet de la dissolution (ISO AAAA-MM-JJ, éventuellement vide)
 * @param dateCloture     date de l'AGE de clôture de la liquidation (ISO AAAA-MM-JJ)
 */
export function liquidationDelaiError(
  dateDissolution: string,
  dateCloture: string,
): string | null {
  const jours = joursCalendairesEntre(dateDissolution, dateCloture);
  if (jours === null) return null;
  if (jours < 0) {
    return 'La date de cloture de la liquidation ne peut pas preceder la date de dissolution.';
  }
  if (jours < LIQUIDATION_DELAI_JOURS) {
    return `Delai legal insuffisant : ${jours} jour(s) entre la dissolution et la cloture. `
      + `Une societe ne peut etre liquidee que ${LIQUIDATION_DELAI_JOURS} jours au minimum `
      + 'apres sa dissolution.';
  }
  return null;
}
