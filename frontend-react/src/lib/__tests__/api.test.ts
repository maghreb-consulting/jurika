import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import {
  isAuthRejection,
  handleSessionEnd,
  NoRefreshTokenError,
  tokenStorage,
  extractError,
  MESSAGE_ERREUR_RESEAU,
  MESSAGE_ERREUR_INCONNUE,
  redirigerSiArchive,
} from '../api';

describe('redirigerSiArchive (lot L7, RG-TAR-07)', () => {
  const realLocation = window.location;
  function mockLocation(pathname: string) {
    Object.defineProperty(window, 'location', { configurable: true, writable: true, value: { pathname, href: '' } });
  }
  afterEach(() => {
    Object.defineProperty(window, 'location', { configurable: true, writable: true, value: realLocation });
  });

  it('conduit le superviseur a la page du cabinet archive sur 403 WORKSPACE_ARCHIVE', () => {
    mockLocation('/dashboard');
    expect(redirigerSiArchive(axiosLikeError(403, 'WORKSPACE_ARCHIVE'))).toBe(true);
    expect(window.location.href).toBe('/app/archive');
  });

  it('ignore les autres refus et ne boucle pas depuis la page archive', () => {
    mockLocation('/dashboard');
    expect(redirigerSiArchive(axiosLikeError(403, 'PERMISSION_DENIED'))).toBe(false);
    mockLocation('/app/archive');
    expect(redirigerSiArchive(axiosLikeError(403, 'WORKSPACE_ARCHIVE'))).toBe(false);
  });

  it('lot L7 (E8) : cabinet suspendu pour impayé, conduit à l’écran Facturation pour payer', () => {
    mockLocation('/dashboard');
    expect(redirigerSiArchive(axiosLikeError(403, 'WORKSPACE_SUSPENDU'))).toBe(true);
    expect(window.location.href).toBe('/app/billing');
    mockLocation('/app/billing/payment');
    expect(redirigerSiArchive(axiosLikeError(403, 'WORKSPACE_SUSPENDU'))).toBe(false);
  });
});

/** Fabrique un objet ressemblant a une AxiosError (isAxiosError=true). */
function axiosLikeError(status: number | undefined, code?: string) {
  return Object.assign(new Error('http'), {
    isAxiosError: true,
    response: status === undefined ? undefined : { status, data: code ? { code } : {} },
  });
}

describe('isAuthRejection', () => {
  it('true sur 401 + code SESSION_*', () => {
    expect(isAuthRejection(axiosLikeError(401, 'SESSION_REVOKED'))).toBe(true);
    expect(isAuthRejection(axiosLikeError(401, 'SESSION_INACTIVITY'))).toBe(true);
    expect(isAuthRejection(axiosLikeError(403, 'SESSION_EXPIRED'))).toBe(true);
  });

  it('true si aucun refresh token local (NoRefreshTokenError)', () => {
    expect(isAuthRejection(new NoRefreshTokenError())).toBe(true);
  });

  it('false sur echec TRANSITOIRE (reseau, 5xx, 429, sans code)', () => {
    expect(isAuthRejection(axiosLikeError(undefined))).toBe(false); // pas de reponse (reseau)
    expect(isAuthRejection(axiosLikeError(500))).toBe(false);
    expect(isAuthRejection(axiosLikeError(503, 'SERVICE_DOWN'))).toBe(false);
    expect(isAuthRejection(axiosLikeError(429))).toBe(false);
    expect(isAuthRejection(axiosLikeError(401))).toBe(false); // 401 sans code de session
    expect(isAuthRejection(axiosLikeError(403, 'PERMISSION_DENIED'))).toBe(false);
  });

  it('false sur une erreur non-axios generique', () => {
    expect(isAuthRejection(new Error('boom'))).toBe(false);
    expect(isAuthRejection(null)).toBe(false);
  });
});

describe('handleSessionEnd', () => {
  const realLocation = window.location;

  function mockLocation(pathname: string) {
    Object.defineProperty(window, 'location', {
      configurable: true,
      writable: true,
      value: { pathname, href: '' },
    });
  }

  beforeEach(() => {
    localStorage.setItem('jurika_access_token', 'a');
    localStorage.setItem('jurika_refresh_token', 'r');
  });

  afterEach(() => {
    Object.defineProperty(window, 'location', {
      configurable: true,
      writable: true,
      value: realLocation,
    });
    localStorage.clear();
  });

  it('purge les tokens et redirige depuis une page protegee, motif mappe', () => {
    mockLocation('/dashboard');
    handleSessionEnd(axiosLikeError(401, 'SESSION_INACTIVITY'));
    expect(tokenStorage.getAccessToken()).toBeNull();
    expect(window.location.href).toBe('/login?reason=inactivity');
  });

  it('mappe SESSION_REVOKED -> session_revoked', () => {
    mockLocation('/tickets');
    handleSessionEnd(axiosLikeError(401, 'SESSION_REVOKED'));
    expect(window.location.href).toBe('/login?reason=session_revoked');
  });

  it('motif expired par defaut (aucun code)', () => {
    mockLocation('/tickets');
    handleSessionEnd(axiosLikeError(401));
    expect(window.location.href).toBe('/login?reason=expired');
  });

  it('NE redirige PAS depuis /signup (page publique) mais purge quand meme', () => {
    mockLocation('/signup');
    handleSessionEnd(axiosLikeError(401, 'SESSION_EXPIRED'));
    expect(tokenStorage.getAccessToken()).toBeNull();
    expect(window.location.href).toBe(''); // pas de redirection
  });

  it('NE redirige PAS depuis /login (evite la boucle)', () => {
    mockLocation('/login');
    handleSessionEnd(axiosLikeError(401, 'SESSION_REVOKED'));
    expect(window.location.href).toBe('');
  });

  it('NE redirige PAS depuis la landing /', () => {
    mockLocation('/');
    handleSessionEnd(axiosLikeError(401));
    expect(window.location.href).toBe('');
  });
});

describe('extractError (messages en francais correct)', () => {
  it('reprend le message du serveur quand il existe', () => {
    const err = Object.assign(new Error('http'), {
      isAxiosError: true,
      response: { status: 422, data: { code: 'X', message: 'Le capital est obligatoire.' } },
    });
    expect(extractError(err)).toEqual({ code: 'X', message: 'Le capital est obligatoire.', errors: undefined });
  });

  it('sans message du serveur : repli selon le statut', () => {
    expect(extractError(axiosLikeError(403)).message).toBe('Vous n’avez pas les droits nécessaires pour cette action.');
    expect(extractError(axiosLikeError(404)).message).toMatch(/^L’élément demandé est introuvable/);
    expect(extractError(axiosLikeError(503)).message).toMatch(/^Le service est momentanément indisponible/);
    expect(extractError(axiosLikeError(400)).message).toBe(MESSAGE_ERREUR_INCONNUE);
  });

  // Lot L6b (E13) : un 400 dit quel champ corriger, jamais seulement "Données invalides".
  it('400 de validation sans champ nommé dans le message : les champs du serveur sont ajoutés', () => {
    const err = Object.assign(new Error('http'), {
      isAxiosError: true,
      response: { status: 400, data: { code: 'VALIDATION_ERROR', message: 'Données invalides',
        errors: [{ field: 'email', message: 'must not be blank' }, { field: 'workspaceCode', message: 'Ce champ est obligatoire.' }] } },
    });
    const m = extractError(err).message;
    expect(m).toMatch(/^Données invalides/);
    expect(m).toContain('« email » (must not be blank)');
    expect(m).toContain('« workspaceCode » (Ce champ est obligatoire.)');
  });

  it('400 dont le message nomme déjà chaque champ : rien n’est répété', () => {
    const message = 'Données invalides. Corrigez le champ « code du cabinet » : ce champ est obligatoire.';
    const err = Object.assign(new Error('http'), {
      isAxiosError: true,
      response: { status: 400, data: { code: 'VALIDATION_ERROR', message,
        errors: [{ field: 'workspaceCode', message: 'Ce champ est obligatoire.' }] } },
    });
    expect(extractError(err).message).toBe(message);
  });

  it('sans reponse du serveur : message de connexion', () => {
    expect(extractError(axiosLikeError(undefined))).toEqual({ code: 'NETWORK', message: MESSAGE_ERREUR_RESEAU });
    expect(extractError(new Error('boom')).message).toBe(MESSAGE_ERREUR_RESEAU);
    expect(MESSAGE_ERREUR_RESEAU).not.toMatch(/reseau/);
  });
});

// Lot L6b (E13) : de nombreux ecrans affichent `err.message`. Pour une erreur axios, c'etait
// "Request failed with status code 400" : anglais, sans le champ a corriger. L'intercepteur
// remplace ce texte par le message francais du serveur (ou le repli selon le statut).
describe('intercepteur de reponse : message lisible sur l erreur', () => {
  it('err.message porte le message du serveur, champs compris', async () => {
    const { api } = await import('../api');
    const { AxiosError } = await import('axios');
    const adapter = async (config: Parameters<typeof api.request>[0]) => {
      throw new AxiosError('Request failed with status code 400', 'ERR_BAD_REQUEST', config as never, null, {
        status: 400, statusText: 'Bad Request', headers: {}, config: config as never,
        data: { code: 'VALIDATION_ERROR', message: 'Données invalides', errors: [{ field: 'denomination', message: 'Ce champ est obligatoire.' }] },
      });
    };
    const err = await api.post('/workflows/x/execute-step', {}, { adapter: adapter as never }).catch((e: unknown) => e);
    expect(err).toBeInstanceOf(Error);
    expect((err as Error).message).not.toMatch(/status code/);
    expect((err as Error).message).toContain('« denomination »');
  });

  it('sans corps : repli francais selon le statut', async () => {
    const { api } = await import('../api');
    const { AxiosError } = await import('axios');
    const adapter = async (config: Parameters<typeof api.request>[0]) => {
      throw new AxiosError('Request failed with status code 404', 'ERR_BAD_REQUEST', config as never, null, {
        status: 404, statusText: 'Not Found', headers: {}, config: config as never, data: '',
      });
    };
    const err = await api.get('/inconnu', { adapter: adapter as never }).catch((e: unknown) => e);
    expect((err as Error).message).toMatch(/^L’élément demandé est introuvable/);
  });
});
