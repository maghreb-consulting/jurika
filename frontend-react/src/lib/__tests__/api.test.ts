import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { isAuthRejection, handleSessionEnd, NoRefreshTokenError, tokenStorage } from '../api';

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
