import { useEffect, useRef } from 'react';
import {
  STORAGE_ACCESS,
  tokenStorage,
  refreshSession,
  handleSessionEnd,
  isAuthRejection,
  lastRefreshAt,
  refreshLockHeldByAnotherTab,
  REFRESH_LOCK_TTL_MS,
} from './api';

/**
 * Refresh PROACTIF de la session base sur l'activite reelle de l'utilisateur.
 *
 * Probleme resolu : le refresh reactif (401/403) ne se declenche qu'a
 * l'expiration de l'access token. La fenetre glissante d'inactivite (cote
 * backend) est reamorcee a chaque rotation ; si aucune rotation n'a lieu tant
 * que l'access est valide, un utilisateur ACTIF peut se retrouver au-dela de la
 * fenetre au tout premier refresh et etre deconnecte a tort ("inactivite").
 *
 * Principe :
 *  - on suit l'activite reelle (click, clavier, souris throttlee, scroll,
 *    visibilite, navigation) dans {@link lastActivityRef} ;
 *  - on programme un refresh a ~80% de la duree de vie de l'access token
 *    (lue dans les claims iat/exp), UNIQUEMENT s'il y a eu activite depuis
 *    l'emission du token courant. Ainsi :
 *      * actif -> rotation renouvelee en continu -> issuedAt suit l'activite ->
 *        jamais deconnecte a tort ;
 *      * inactif -> pas de rotation -> l'access expire -> 401 -> deconnexion
 *        propre (SESSION_INACTIVITY) via le chemin reactif.
 *  - le plafond ABSOLU (refresh-ttl) reste applique cote backend : meme actif,
 *    la session finit par expirer (SESSION_EXPIRED).
 *
 * Multi-onglets : la rotation est deduplicee dans l'onglet via `refreshSession`,
 * et coordonnee entre onglets par un verrou leger en localStorage + reprogrammation
 * sur l'evenement `storage` (quand un autre onglet a deja rafraichi).
 */

const PROACTIVE_RATIO = 0.8;
const MIN_DELAY_MS = 5_000;
/** Marge de tolerance d'horloge (client/serveur) : on vise le refresh un peu plus
 *  tot pour ne pas rater la fenetre a cause d'un leger decalage. */
const CLOCK_SKEW_MS = 30_000;
/** Re-verification quand l'utilisateur est inactif (l'activite peut reprendre). */
const IDLE_RECHECK_MS = 60_000;
/** Etale les ticks entre onglets pour eviter des rotations simultanees. */
const JITTER_MS = 4_000;
/** Throttle des evenements haute-frequence (mousemove/scroll). */
const ACTIVITY_THROTTLE_MS = 5_000;

/** Backoff sur echec TRANSITOIRE du refresh proactif (reseau / 5xx / auth-service
 *  qui redemarre) : 5s, 15s, 45s... plafonne. On NE deconnecte PAS ; l'access
 *  token courant reste utilisable jusqu'a son expiration. */
const BACKOFF_BASE_MS = 5_000;
const BACKOFF_MAX_MS = 120_000;
/** Apres N echecs transitoires consecutifs, on cesse de spammer et on laisse le
 *  chemin reactif (intercepteur) prendre le relais au prochain appel. */
const MAX_TRANSIENT_RETRIES = 4;

const ACTIVITY_EVENTS = ['click', 'keydown', 'mousemove', 'scroll', 'touchstart'] as const;

interface TokenTimes {
  iatMs: number;
  expMs: number;
}

/** Decode les claims temporels (iat/exp) d'un JWT access token. */
export function decodeTokenTimes(token: string | null): TokenTimes | null {
  if (!token) return null;
  try {
    const [, payload] = token.split('.');
    if (!payload) return null;
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
    if (typeof json.iat !== 'number' || typeof json.exp !== 'number') return null;
    return { iatMs: json.iat * 1000, expMs: json.exp * 1000 };
  } catch {
    return null;
  }
}

/** Delai (ms) avant le refresh proactif : ~80% de la vie de l'access, moins une
 *  marge de skew d'horloge, borne bas. */
export function computeProactiveDelayMs(times: TokenTimes, now: number): number {
  const fireAt = times.iatMs + PROACTIVE_RATIO * (times.expMs - times.iatMs) - CLOCK_SKEW_MS;
  return Math.max(fireAt - now, MIN_DELAY_MS);
}

/**
 * Faut-il rafraichir proactivement ? Uniquement s'il y a eu de l'activite reelle
 * depuis l'emission du token courant (~ depuis le dernier refresh). Un utilisateur
 * inactif n'est pas rafraichi : son access expire et le chemin reactif produit une
 * deconnexion propre (SESSION_INACTIVITY).
 */
export function shouldProactivelyRefresh(lastActivityMs: number, tokenIatMs: number): boolean {
  return lastActivityMs >= tokenIatMs;
}

/** Backoff exponentiel borne pour le n-ieme (1-indexed) echec transitoire. */
export function computeBackoffMs(consecutiveFailures: number): number {
  const raw = BACKOFF_BASE_MS * 3 ** (consecutiveFailures - 1);
  return Math.min(raw, BACKOFF_MAX_MS);
}

export function useSessionKeepAlive(): void {
  const lastActivityRef = useRef<number>(Date.now());
  const lastThrottleRef = useRef<number>(0);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  /** Nb d'echecs TRANSITOIRES consecutifs du refresh proactif (reset au succes). */
  const transientFailuresRef = useRef<number>(0);

  useEffect(() => {
    let cancelled = false;

    const clearTimer = () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    };

    const schedule = () => {
      clearTimer();
      const times = decodeTokenTimes(tokenStorage.getAccessToken());
      if (!times) return; // pas de session -> le chemin reactif gerera
      const delay = computeProactiveDelayMs(times, Date.now())
        + Math.floor(Math.random() * JITTER_MS);
      timerRef.current = setTimeout(tick, delay);
    };

    const tick = async () => {
      if (cancelled) return;
      const token = tokenStorage.getAccessToken();
      const times = decodeTokenTimes(token);
      if (!times) return; // deconnecte entre-temps

      const now = Date.now();
      // Activite depuis l'emission du token courant (~ depuis le dernier refresh) ?
      const activeSinceIssue = shouldProactivelyRefresh(lastActivityRef.current, times.iatMs);
      // Un autre onglet vient-il de rafraichir ? (nouveau token a venir via `storage`)
      const refreshedRecently = now - lastRefreshAt() < REFRESH_LOCK_TTL_MS;

      if (!activeSinceIssue) {
        // Inactif : on NE rafraichit pas. L'access expirera -> 401 -> SESSION_INACTIVITY.
        // On reverifie plus tard au cas ou l'activite reprend avant l'expiration.
        timerRef.current = setTimeout(tick, IDLE_RECHECK_MS);
        return;
      }

      if (refreshedRecently || refreshLockHeldByAnotherTab(now)) {
        // Un autre onglet s'en charge : on se contente de reprogrammer. La
        // coordination fine (verrou, reuse) est geree par refreshSession lui-meme.
        schedule();
        return;
      }

      try {
        await refreshSession();
        transientFailuresRef.current = 0; // succes : on repart a zero
      } catch (err) {
        if (isAuthRejection(err)) {
          // VRAI refus (revoque / inactivite / plafond absolu) : deconnexion propre.
          handleSessionEnd(err);
          return;
        }
        // Echec TRANSITOIRE (reseau / 5xx / auth-service qui redemarre) : on NE
        // deconnecte PAS. L'access courant reste valide ; on retente avec backoff.
        transientFailuresRef.current += 1;
        if (transientFailuresRef.current >= MAX_TRANSIENT_RETRIES) {
          // On cesse de spammer : le chemin reactif prendra le relais au prochain
          // appel. On reprogramme normalement (sur le token courant, encore valide).
          transientFailuresRef.current = 0;
          if (!cancelled) schedule();
          return;
        }
        if (!cancelled) {
          timerRef.current = setTimeout(tick, computeBackoffMs(transientFailuresRef.current));
        }
        return;
      }
      if (!cancelled) schedule();
    };

    const markActivity = () => {
      lastActivityRef.current = Date.now();
    };

    const onHighFrequencyActivity = () => {
      const now = Date.now();
      if (now - lastThrottleRef.current < ACTIVITY_THROTTLE_MS) return;
      lastThrottleRef.current = now;
      lastActivityRef.current = now;
    };

    const onVisibility = () => {
      if (document.visibilityState === 'visible') {
        markActivity();
        // Les timers peuvent avoir ete throttles en arriere-plan : on reprogramme
        // en repartant du token courant (potentiellement rafraichi par un autre onglet).
        schedule();
      }
    };

    const onStorage = (e: StorageEvent) => {
      // Un autre onglet a rafraichi : reprogrammer sur le nouveau token.
      if (e.key === STORAGE_ACCESS) schedule();
    };

    for (const evt of ACTIVITY_EVENTS) {
      const handler = evt === 'mousemove' || evt === 'scroll' ? onHighFrequencyActivity : markActivity;
      window.addEventListener(evt, handler, { passive: true });
    }
    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('storage', onStorage);

    schedule();

    return () => {
      cancelled = true;
      clearTimer();
      for (const evt of ACTIVITY_EVENTS) {
        const handler = evt === 'mousemove' || evt === 'scroll' ? onHighFrequencyActivity : markActivity;
        window.removeEventListener(evt, handler);
      }
      document.removeEventListener('visibilitychange', onVisibility);
      window.removeEventListener('storage', onStorage);
    };
  }, []);
}
