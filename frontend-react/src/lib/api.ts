import axios, { type AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios';
import type { ApiError } from '../types/api';
import type { AuthTokens } from '../types/auth';

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1';

export const STORAGE_ACCESS = 'jurika_access_token';
const STORAGE_REFRESH = 'jurika_refresh_token';
/** Horodatage (ms epoch) de la derniere rotation reussie du refresh token.
 *  Partage entre onglets via localStorage : sert au refresh proactif a savoir
 *  s'il y a eu de l'activite DEPUIS le dernier refresh, et a un onglet a ne pas
 *  re-rafraichir juste apres un autre. */
const STORAGE_LAST_REFRESH = 'jurika_last_refresh_at';
/** Verrou leger inter-onglets autour d'une rotation `/auth/refresh`. Empeche
 *  deux onglets de rafraichir en concurrence (l'un revoquant le refresh token
 *  que l'autre vient d'obtenir -> fausse SESSION_REVOKED). Detenu par api.ts. */
const STORAGE_REFRESH_LOCK = 'jurika_refresh_lock';
/** Duree de validite du verrou de rotation (borne aussi la fenetre de reuse). */
export const REFRESH_LOCK_TTL_MS = 15_000;
/** Si un autre onglet a rafraichi il y a moins de X, on REUTILISE ses tokens
 *  (deja ecrits en localStorage) au lieu d'en declencher une rotation concurrente. */
const CROSS_TAB_REUSE_MS = 10_000;
/** Petit pas d'attente quand un onglet pair detient le verrou de rotation. */
const PEER_WAIT_STEP_MS = 200;

export const tokenStorage = {
  setTokens(tokens: AuthTokens) {
    localStorage.setItem(STORAGE_ACCESS, tokens.accessToken);
    localStorage.setItem(STORAGE_REFRESH, tokens.refreshToken);
  },
  getAccessToken(): string | null {
    return localStorage.getItem(STORAGE_ACCESS);
  },
  getRefreshToken(): string | null {
    return localStorage.getItem(STORAGE_REFRESH);
  },
  clear() {
    localStorage.removeItem(STORAGE_ACCESS);
    localStorage.removeItem(STORAGE_REFRESH);
    localStorage.removeItem(STORAGE_LAST_REFRESH);
  },
};

/** Marque une rotation reussie (utilise par le refresh proactif + reactif). */
export function markRefreshed(): void {
  localStorage.setItem(STORAGE_LAST_REFRESH, String(Date.now()));
}

/** Instant (ms epoch) de la derniere rotation reussie, 0 si jamais. */
export function lastRefreshAt(): number {
  return Number(localStorage.getItem(STORAGE_LAST_REFRESH) ?? 0);
}

export const api = axios.create({
  baseURL: API_URL,
  headers: { 'Content-Type': 'application/json' },
});

let refreshPromise: Promise<AuthTokens> | null = null;

/**
 * Erreur TERMINALE (non transitoire) : il n'existe aucun refresh token local,
 * donc aucune session a prolonger. Traitee comme un vrai refus d'auth par
 * {@link isAuthRejection} (contrairement a une coupure reseau / 5xx).
 * Duck-typing via `name` (l'`instanceof` casse en modules isoles Vitest).
 */
export class NoRefreshTokenError extends Error {
  constructor() {
    super('No refresh token');
    this.name = 'NoRefreshTokenError';
  }
}

async function refreshTokens(): Promise<AuthTokens> {
  const refreshToken = tokenStorage.getRefreshToken();
  if (!refreshToken) {
    throw new NoRefreshTokenError();
  }
  const response = await axios.post<AuthTokens>(`${API_URL}/auth/refresh`, { refreshToken });
  return response.data;
}

/** Reconstruit un AuthTokens depuis le localStorage (rotation faite par un pair).
 *  Seuls access/refresh sont reellement persistes ; les autres champs ne sont pas
 *  consommes par les appelants de refresh (le chemin reactif n'utilise que
 *  `accessToken`, le proactif rien). */
function readStoredTokens(): AuthTokens | null {
  const accessToken = tokenStorage.getAccessToken();
  const refreshToken = tokenStorage.getRefreshToken();
  if (!accessToken || !refreshToken) return null;
  return {
    accessToken, refreshToken,
    accessExpiresAt: '', refreshExpiresAt: '', userId: '', workspaceId: '',
  };
}

/** Un AUTRE onglet detient-il le verrou de rotation ? (fraicheur de l'horodatage) */
export function refreshLockHeldByAnotherTab(now: number): boolean {
  const raw = Number(localStorage.getItem(STORAGE_REFRESH_LOCK) ?? 0);
  return raw > 0 && now - raw < REFRESH_LOCK_TTL_MS;
}

const sleep = (ms: number): Promise<void> =>
  new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Rotation COORDONNEE inter-onglets. Une rotation revoque l'ancien refresh token
 * (cf. RefreshTokenUseCase) : deux onglets qui rafraichissent en concurrence
 * feraient echouer le perdant en SESSION_REVOKED (fausse deconnexion). On evite ca :
 *  1. si un pair vient de rafraichir (< CROSS_TAB_REUSE_MS) -> on REUTILISE ses
 *     tokens deja en localStorage, sans re-rotater ;
 *  2. si un pair detient le verrou -> on attend brievement qu'il publie ses tokens,
 *     puis on les reutilise ;
 *  3. sinon on prend le verrou et on rafraichit reellement.
 */
async function coordinatedRefresh(): Promise<AuthTokens> {
  const startedAt = Date.now();

  // 1. Un pair a rafraichi tres recemment : reutiliser sa rotation.
  if (startedAt - lastRefreshAt() < CROSS_TAB_REUSE_MS) {
    const reused = readStoredTokens();
    if (reused) return reused;
  }

  // 2. Un pair est en pleine rotation : lui laisser le temps de publier.
  if (refreshLockHeldByAnotherTab(startedAt)) {
    const deadline = startedAt + REFRESH_LOCK_TTL_MS;
    while (Date.now() < deadline) {
      await sleep(PEER_WAIT_STEP_MS);
      if (Date.now() - lastRefreshAt() < CROSS_TAB_REUSE_MS) {
        const reused = readStoredTokens();
        if (reused) return reused;
      }
      if (!refreshLockHeldByAnotherTab(Date.now())) break; // pair fini (peut-etre en echec)
    }
  }

  // 3. Rotation reelle, sous verrou.
  localStorage.setItem(STORAGE_REFRESH_LOCK, String(Date.now()));
  try {
    const tokens = await refreshTokens();
    tokenStorage.setTokens(tokens);
    markRefreshed();
    return tokens;
  } finally {
    localStorage.removeItem(STORAGE_REFRESH_LOCK);
  }
}

/**
 * Rotation deduplicee du refresh token, PARTAGEE par le chemin reactif (401/403)
 * et le refresh PROACTIF base activite. Une seule rotation est en vol a la fois
 * dans l'onglet (dedup via `refreshPromise`) ; la coordination INTER-onglets est
 * assuree par {@link coordinatedRefresh}.
 */
export function refreshSession(): Promise<AuthTokens> {
  if (!refreshPromise) {
    refreshPromise = coordinatedRefresh().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

/**
 * Distingue un VRAI refus d'authentification (fin de session legitime) d'un echec
 * TRANSITOIRE de `/auth/refresh` (reseau, timeout, 5xx, 429, CORS ponctuel...).
 * Seul un vrai refus doit purger les tokens et rediriger vers /login.
 *
 * Vrai refus =
 *  - HTTP 401/403 portant un code metier de session (SESSION_REVOKED /
 *    SESSION_INACTIVITY / SESSION_EXPIRED), OU
 *  - absence totale de refresh token local ({@link NoRefreshTokenError}).
 * Tout le reste (pas de reponse, 5xx, 429, 401/403 SANS code de session) est
 * considere transitoire -> on CONSERVE la session.
 */
export function isAuthRejection(error: unknown): boolean {
  if (error instanceof Error && error.name === 'NoRefreshTokenError') return true;
  if (!axios.isAxiosError(error)) return false;
  const status = error.response?.status;
  if (status !== 401 && status !== 403) return false;
  const code = (error.response?.data as { code?: string } | undefined)?.code;
  return code === 'SESSION_REVOKED'
      || code === 'SESSION_INACTIVITY'
      || code === 'SESSION_EXPIRED';
}

/** Prefixes de pages PUBLIQUES / non authentifiees : y rediriger vers /login
 *  n'a pas de sens (on y est deja hors session). Etend l'ancien garde-fou qui
 *  ne couvrait que /login et provoquait une redirection depuis /signup. */
const PUBLIC_PAGE_PREFIXES = [
  '/login',
  '/signup',
  '/register',
  '/reset-password',
  '/verify-email',
  '/auth/recover-with-code',
  '/forbidden',
  '/cgu',
  '/conditions-generales',
  '/confidentialite',
  '/privacy',
];

function onPublicPage(pathname: string): boolean {
  if (pathname === '/') return true; // landing
  return PUBLIC_PAGE_PREFIXES.some((p) => pathname.startsWith(p));
}

/**
 * Termine la session : purge les tokens locaux et redirige vers /login avec le
 * bon motif (base sur le `code` renvoye par `/auth/refresh`). A n'appeler QUE
 * lorsque {@link isAuthRejection} est vrai. Ne redirige jamais depuis une page
 * publique (on y est deja hors session).
 */
export function handleSessionEnd(refreshError: unknown): void {
  tokenStorage.clear();
  if (typeof window === 'undefined') return;
  if (onPublicPage(window.location.pathname)) return;
  const code = axios.isAxiosError(refreshError)
    ? (refreshError.response?.data as { code?: string } | undefined)?.code
    : undefined;
  const reason =
    code === 'SESSION_REVOKED' ? 'session_revoked'
    : code === 'SESSION_INACTIVITY' ? 'inactivity'
    : 'expired';
  window.location.href = `/login?reason=${reason}`;
}

const PUBLIC_AUTH_PATHS = [
  '/auth/login',
  '/auth/register',
  '/auth/workspace-check',
  '/auth/refresh',
  '/auth/verify-2fa',
  '/auth/verify-email',
  '/auth/resend-verification',
  '/auth/password-reset/request',
  '/auth/password-reset/confirm',
];

/**
 * Endpoint API PUBLIC (aucun JWT requis). Deux familles :
 *  - `/public/**` : onboarding anonyme + analytics (signup, events, pricing...) ;
 *  - une whitelist d'endpoints `/auth/*` joignables avant authentification.
 * Un token attache a un endpoint public ferait rejeter la requete (JwtAuthFilter),
 * et un 401/403 dessus ne doit JAMAIS declencher de refresh / fin de session.
 */
function isPublicApiPath(url: string): boolean {
  if (url.startsWith('/public/')) return true;
  return PUBLIC_AUTH_PATHS.some((p) => url.startsWith(p));
}

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const url = config.url ?? '';
  if (isPublicApiPath(url)) {
    // Strip any stale Authorization to prevent the backend JwtAuthFilter
    // from rejecting a public endpoint due to an expired Bearer token.
    config.headers.delete('Authorization');
    return config;
  }
  const token = tokenStorage.getAccessToken();
  if (token && !config.headers.has('Authorization')) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

/**
 * HIGH-12 helper : distingue "token expire" (refresh-able) d'un vrai refus
 * de permission (PERMISSION_DENIED, SETUP_2FA_REQUIRED, PASSWORD_CHANGE_REQUIRED,
 * TRIAL_EXPIRED, etc.) qu'un refresh ne resoudra pas.
 */
function isPermissionDenied(body: unknown): boolean {
  if (!body || typeof body !== 'object') return false;
  const code = (body as { code?: string }).code;
  if (!code) return false;
  return code === 'PERMISSION_DENIED'
      || code === 'PASSWORD_CHANGE_REQUIRED'
      || code === 'SETUP_2FA_REQUIRED'
      || code === 'TRIAL_EXPIRED'
      || code === 'PAYMENT_REQUIRED';
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiError>) => {
    const original = error.config as (AxiosRequestConfig & { _retry?: boolean }) | undefined;
    const status = error.response?.status;
    // Un endpoint PUBLIC (/public/** ou /auth/* public) ne doit jamais declencher
    // de refresh ni de fin de session : un 401/403 y est soit attendu, soit sans
    // rapport avec une session utilisateur. (Corrige la redirection depuis /signup.)
    const skipSessionHandling = isPublicApiPath(original?.url ?? '');

    // HIGH-12 (audit 2026-06-02) : trigger refresh aussi sur 403. JwtAuthFilter de
    // jurika-common renvoie en effet 403 (Spring Security default) sur token
    // expire ou signature invalide, pas 401. Sans ce fix, le user etait
    // brutalement deconnecte au lieu d'un refresh transparent.
    // Pour distinguer un vrai refus permission d'un token expire, on regarde aussi
    // le body : si le serveur a renvoye un code metier (PERMISSION_DENIED, etc.)
    // on n'essaie pas de refresh — un refresh ne resoudra pas un manque de droits.
    const looksLikeAuthExpired = (status === 401 || status === 403)
        && !isPermissionDenied(error.response?.data);

    if (looksLikeAuthExpired && original && !original._retry && !skipSessionHandling) {
      original._retry = true;
      try {
        const tokens = await refreshSession();
        if (!original.headers) {
          original.headers = {};
        }
        (original.headers as Record<string, string>).Authorization = `Bearer ${tokens.accessToken}`;
        return api.request(original);
      } catch (refreshError) {
        // On ne termine la session QUE sur un vrai refus d'auth (401/403 + code
        // SESSION_*, ou absence de refresh token). Un echec TRANSITOIRE (reseau,
        // 5xx, 429, auth-service qui redemarre) NE deconnecte PAS : on laisse la
        // requete echouer, un prochain appel retentera le refresh.
        if (isAuthRejection(refreshError)) {
          handleSessionEnd(refreshError);
        }
        return Promise.reject(error);
      }
    }

    return Promise.reject(error);
  },
);

export function extractError(error: unknown): ApiError {
  if (axios.isAxiosError(error) && error.response?.data) {
    const data = error.response.data as ApiError;
    return {
      code: data.code ?? 'UNKNOWN',
      message: data.message ?? 'Erreur inconnue',
      errors: data.errors,
    };
  }
  return { code: 'NETWORK', message: 'Erreur reseau' };
}
