import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook } from '@testing-library/react';

// Mock du module api : on isole la logique de keep-alive de la couche reseau.
vi.mock('../api', async () => {
  const actual = await vi.importActual<typeof import('../api')>('../api');
  return {
    ...actual,
    refreshSession: vi.fn(),
    handleSessionEnd: vi.fn(),
  };
});

import {
  decodeTokenTimes,
  computeProactiveDelayMs,
  computeBackoffMs,
  shouldProactivelyRefresh,
  useSessionKeepAlive,
} from '../useSessionKeepAlive';
import { STORAGE_ACCESS, refreshSession, handleSessionEnd, lastRefreshAt } from '../api';

/** Fabrique un access token JWT factice avec iat/exp (en secondes). */
function fakeToken(iatSec: number, expSec: number): string {
  const payload = btoa(JSON.stringify({ iat: iatSec, exp: expSec }))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `h.${payload}.s`;
}

describe('decodeTokenTimes', () => {
  it('decode iat/exp en ms', () => {
    const t = decodeTokenTimes(fakeToken(1000, 1900));
    expect(t).toEqual({ iatMs: 1_000_000, expMs: 1_900_000 });
  });
  it('renvoie null si token absent ou malforme', () => {
    expect(decodeTokenTimes(null)).toBeNull();
    expect(decodeTokenTimes('pas-un-jwt')).toBeNull();
    expect(decodeTokenTimes(fakeToken(NaN as unknown as number, 1))).toBeNull();
  });
});

describe('computeProactiveDelayMs', () => {
  it('vise ~80% de la duree de vie de l access, moins la marge de skew', () => {
    const times = { iatMs: 1_000_000, expMs: 1_000_000 + 900_000 }; // 15 min
    // now == iat -> 80% de 900_000 = 720_000, moins skew 30_000 = 690_000
    expect(computeProactiveDelayMs(times, 1_000_000)).toBe(690_000);
  });
  it('borne le delai a un minimum si le token est deja au-dela de 80%', () => {
    const times = { iatMs: 0, expMs: 900_000 };
    expect(computeProactiveDelayMs(times, 5_000_000)).toBe(5_000); // MIN_DELAY_MS
  });
});

describe('computeBackoffMs', () => {
  it('suit une progression 5s / 15s / 45s puis plafonne', () => {
    expect(computeBackoffMs(1)).toBe(5_000);
    expect(computeBackoffMs(2)).toBe(15_000);
    expect(computeBackoffMs(3)).toBe(45_000);
    expect(computeBackoffMs(4)).toBe(120_000); // 135_000 plafonne a BACKOFF_MAX_MS
    expect(computeBackoffMs(10)).toBe(120_000);
  });
});

describe('shouldProactivelyRefresh', () => {
  it('true si activite depuis l emission du token', () => {
    expect(shouldProactivelyRefresh(2_000, 1_000)).toBe(true);
    expect(shouldProactivelyRefresh(1_000, 1_000)).toBe(true);
  });
  it('false si aucune activite depuis l emission (inactif)', () => {
    expect(shouldProactivelyRefresh(500, 1_000)).toBe(false);
  });
});

describe('useSessionKeepAlive (integration)', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    localStorage.clear();
    vi.mocked(refreshSession).mockReset();
    vi.mocked(refreshSession).mockResolvedValue({
      accessToken: 'new', refreshToken: 'newr',
      accessExpiresAt: '', refreshExpiresAt: '', userId: 'u', workspaceId: 'w',
    });
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('rafraichit proactivement quand l utilisateur est actif', () => {
    const nowSec = 1_700_000_000;
    vi.setSystemTime(nowSec * 1000);
    // access 15 min emis maintenant ; l activite du mount (Date.now) >= iat.
    localStorage.setItem(STORAGE_ACCESS, fakeToken(nowSec, nowSec + 900));

    renderHook(() => useSessionKeepAlive());

    // Avance au-dela de 80% (12 min) + jitter max (4 s).
    vi.advanceTimersByTime(12 * 60 * 1000 + 4_000 + 100);

    expect(refreshSession).toHaveBeenCalledTimes(1);
  });

  it('ne rafraichit PAS quand aucune activite depuis l emission du token', () => {
    const nowSec = 1_700_000_000;
    vi.setSystemTime(nowSec * 1000);
    // Token initial present au mount -> un timer proactif est bien programme.
    localStorage.setItem(STORAGE_ACCESS, fakeToken(nowSec, nowSec + 900));

    renderHook(() => useSessionKeepAlive());

    // Avant que le timer ne se declenche, la session a ete rerafraichie (par un
    // autre onglet) : le token courant est emis 10 min APRES la derniere activite
    // enregistree (le mount). L utilisateur n a rien fait depuis => pas de refresh.
    localStorage.setItem(STORAGE_ACCESS, fakeToken(nowSec + 600, nowSec + 600 + 900));

    vi.advanceTimersByTime(12 * 60 * 1000 + 4_000 + 100);

    expect(refreshSession).not.toHaveBeenCalled();
  });

  it('n a pas horodate de refresh au montage (lastRefreshAt vierge)', () => {
    expect(lastRefreshAt()).toBe(0);
  });

  it('echec TRANSITOIRE (reseau) : PAS de handleSessionEnd, retry avec backoff', async () => {
    const nowSec = 1_700_000_000;
    vi.setSystemTime(nowSec * 1000);
    localStorage.setItem(STORAGE_ACCESS, fakeToken(nowSec, nowSec + 900));
    vi.mocked(handleSessionEnd).mockClear();
    // Erreur reseau : pas de reponse -> isAuthRejection=false -> transitoire.
    vi.mocked(refreshSession).mockRejectedValueOnce(
      Object.assign(new Error('Network Error'), { isAxiosError: true, response: undefined }),
    );

    renderHook(() => useSessionKeepAlive());
    // Le 1er tick proactif fire entre 690000 (skew) et 694000 (jitter max).
    // On s'arrete a 694500 : apres le 1er echec, AVANT que le backoff (>=695000) ne fire.
    await vi.advanceTimersByTimeAsync(694_500);

    expect(refreshSession).toHaveBeenCalledTimes(1);
    expect(handleSessionEnd).not.toHaveBeenCalled();

    // Le backoff (5s) reprogramme un tick ; le 2e refresh reussit (mock par defaut).
    await vi.advanceTimersByTimeAsync(5_000);
    expect(refreshSession).toHaveBeenCalledTimes(2);
    expect(handleSessionEnd).not.toHaveBeenCalled();
  });

  it('VRAI refus (SESSION_INACTIVITY) : handleSessionEnd appele', async () => {
    const nowSec = 1_700_000_000;
    vi.setSystemTime(nowSec * 1000);
    localStorage.setItem(STORAGE_ACCESS, fakeToken(nowSec, nowSec + 900));
    vi.mocked(handleSessionEnd).mockClear();
    vi.mocked(refreshSession).mockRejectedValueOnce(
      Object.assign(new Error('rejected'), {
        isAxiosError: true,
        response: { status: 401, data: { code: 'SESSION_INACTIVITY' } },
      }),
    );

    renderHook(() => useSessionKeepAlive());
    await vi.advanceTimersByTimeAsync(12 * 60 * 1000 + 4_000 + 100);

    expect(refreshSession).toHaveBeenCalledTimes(1);
    expect(handleSessionEnd).toHaveBeenCalledTimes(1);
  });
});
