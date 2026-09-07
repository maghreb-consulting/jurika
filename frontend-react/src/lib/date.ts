/**
 * Formatage de dates robuste (Lot AC — bug "1970").
 *
 * La vraie correction du bug "1970" est cote API (serialisation ISO-8601 des
 * `Instant` Java — cf dashboard-service RedisCacheConfig). Ce helper est un
 * garde-fou defensif partage par toutes les vues qui affichent un horodatage
 * (tracabilite, activite client, ...) : si un endpoint renvoie malgre tout un
 * NOMBRE (epoch), on ne retombe plus sur `new Date(1_751_000_000)` interprete
 * en millisecondes -> 1970.
 *
 * Regles :
 * - `null` / `undefined` / valeur invalide -> `'—'` (jamais "1970").
 * - nombre (ou chaine 100% numerique) < 1e12 -> traite comme epoch SECONDES
 *   (x1000). Un timestamp ms courant (~1.75e12) est > 1e12 ; un timestamp s
 *   courant (~1.75e9) est < 1e12. Le seuil 1e12 les separe proprement.
 * - chaine ISO -> `new Date(iso)` (comportement standard).
 */
const FR_DATE_TIME = new Intl.DateTimeFormat('fr-FR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

const EPOCH_SECONDS_THRESHOLD = 1e12;

/** Convertit une valeur d'API (ISO string | epoch number) en `Date` fiable, ou `null`. */
export function toDate(value: string | number | null | undefined): Date | null {
  if (value == null) return null;

  let millis: number;
  if (typeof value === 'number' || /^\d+$/.test(value)) {
    const n = typeof value === 'number' ? value : Number(value);
    // Heuristique epoch secondes vs millisecondes (cf. commentaire en tete).
    millis = n < EPOCH_SECONDS_THRESHOLD ? n * 1000 : n;
  } else {
    millis = new Date(value).getTime();
  }

  if (!Number.isFinite(millis)) return null;
  const d = new Date(millis);
  return Number.isNaN(d.getTime()) ? null : d;
}

/** Date + heure au format fr-FR (`JJ/MM/AAAA HH:MM`), ou `'—'` si absente/invalide. */
export function formatDateTime(value: string | number | null | undefined): string {
  const d = toDate(value);
  return d ? FR_DATE_TIME.format(d) : '—';
}

const FR_DATE = new Intl.DateTimeFormat('fr-FR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});

/**
 * Date seule au format fr-FR (`JJ/MM/AAAA`), ou `'—'` si absente/invalide.
 * Les echeances legales se comptent en jours de calendrier : afficher une heure
 * y serait trompeur.
 */
export function formatDate(value: string | number | null | undefined): string {
  const d = toDate(value);
  return d ? FR_DATE.format(d) : '—';
}
