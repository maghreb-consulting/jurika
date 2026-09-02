/**
 * Règle des 16 jours entre la convocation et la tenue de l'assemblée — noyau PARTAGÉ
 * (Modification 2026-08-11, Dissolution 2026-08-12).
 *
 * Miroir exact de la validation backend `WorkflowSteps.convocationDelaiError`
 * (workflow-service), réutilisée par `ModificationWorkflow` et `DissolutionWorkflow`.
 *
 * Les calculs se font sur des dates ISO `AAAA-MM-JJ` interprétées en **UTC**
 * (`Date.parse` d'une date seule = minuit UTC, `Date.UTC` pour l'addition) : l'écart est
 * donc un nombre exact de **jours calendaires**, insensible au fuseau horaire et aux
 * changements d'heure (DST).
 */

/** Délai minimum entre la convocation et la tenue de l'assemblée (16 jours calendaires). */
export const CONVOCATION_DELAI_JOURS = 16;

/**
 * Ajoute n jours à une date ISO (AAAA-MM-JJ) et renvoie la date ISO résultante ('' si
 * invalide). Calcul 100% UTC (Date.UTC + setUTCDate + toISOString) pour éviter tout
 * décalage d'un jour lié au fuseau horaire / DST — garantit exactement + n jours.
 */
export function addDaysIso(dateIso: string, days: number): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec((dateIso ?? '').trim());
  if (!m) return '';
  const d = new Date(Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3])));
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/**
 * Règle DURE des 16 jours (pure, testable). Retourne un message d'erreur FR si la
 * convocation est trop tardive (ou postérieure à l'assemblée), sinon null. Convocation
 * absente (date vide) = pas de contrôle (elle est OPTIONNELLE).
 *
 * @param convocationDate date de convocation (ISO AAAA-MM-JJ, éventuellement vide)
 * @param dateAssemblee   date du PV / de l'assemblée (ISO AAAA-MM-JJ)
 */
export function convocationDelaiError(
  convocationDate: string,
  dateAssemblee: string,
): string | null {
  if (!convocationDate || !dateAssemblee) return null;
  const conv = Date.parse(convocationDate);
  const pv = Date.parse(dateAssemblee);
  if (Number.isNaN(conv) || Number.isNaN(pv)) return null;
  if (conv >= pv) {
    return "La date de convocation doit preceder la date du PV / de l'assemblee.";
  }
  const jours = Math.floor((pv - conv) / 86400000);
  if (jours < CONVOCATION_DELAI_JOURS) {
    return `Delai de convocation insuffisant : ${jours} jour(s) entre la convocation et `
      + `l'assemblee. Un minimum de ${CONVOCATION_DELAI_JOURS} jours est requis.`;
  }
  return null;
}
