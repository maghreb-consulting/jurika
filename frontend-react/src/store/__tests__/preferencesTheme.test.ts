import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * Lot L6b (E3) : theme qui suit l'ordinateur par defaut, choix memorise sur le compte
 * (GET/PUT /users/me/preferences), cache local, erreurs visibles.
 */
const api = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn() }));
vi.mock('../../lib/api', () => ({ api }));

let sombre = false;
const ecouteurs = new Set<() => void>();
function installerMatchMedia() {
  window.matchMedia = ((q: string) => ({
    get matches() {
      return q.includes('prefers-color-scheme: dark') ? sombre : false;
    },
    media: q,
    addEventListener: (_: string, f: () => void) => ecouteurs.add(f),
    removeEventListener: (_: string, f: () => void) => ecouteurs.delete(f),
  })) as unknown as typeof window.matchMedia;
}

async function charger() {
  vi.resetModules();
  const theme = await import('../themeStore');
  const prefs = await import('../preferencesStore');
  return { ...theme, ...prefs };
}

beforeEach(() => {
  sombre = false;
  ecouteurs.clear();
  installerMatchMedia();
  localStorage.clear();
  document.documentElement.removeAttribute('data-theme');
  api.get.mockReset();
  api.put.mockReset();
});

afterEach(() => {
  delete (window as { matchMedia?: unknown }).matchMedia;
});

describe('Theme', () => {
  it('par defaut, suit l ordinateur (sombre) et le suit quand il change', async () => {
    sombre = true;
    const { useThemeStore } = await charger();
    expect(useThemeStore.getState().choix).toBe('SYSTEME');
    useThemeStore.getState().applyDomTheme();
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    const arreter = useThemeStore.getState().suivreSysteme();
    sombre = false;
    ecouteurs.forEach((f) => f());
    expect(useThemeStore.getState().mode).toBe('light');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    arreter();
    expect(ecouteurs.size).toBe(0);
  });

  it('un choix explicite l emporte sur l ordinateur et se met en cache', async () => {
    sombre = true;
    const { useThemeStore, CLE_CACHE_THEME } = await charger();
    useThemeStore.getState().appliquerChoix('CLAIR');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    expect(localStorage.getItem(CLE_CACHE_THEME)).toBe('CLAIR');
    const arreter = useThemeStore.getState().suivreSysteme();
    ecouteurs.forEach((f) => f());
    expect(useThemeStore.getState().mode).toBe('light');
    arreter();
  });

  it('reprend le cache au demarrage, et l ancien magasin (jurika-theme) une fois', async () => {
    localStorage.setItem('jurika-theme', JSON.stringify({ state: { mode: 'dark' }, version: 0 }));
    const { useThemeStore } = await charger();
    expect(useThemeStore.getState().choix).toBe('SOMBRE');
    expect(useThemeStore.getState().mode).toBe('dark');
  });
});

describe('Preferences du compte', () => {
  it('charge les preferences a la connexion et applique le theme du compte', async () => {
    api.get.mockResolvedValue({ data: { theme: 'SOMBRE', juriMasque: true } });
    const { usePreferencesStore, useThemeStore } = await charger();
    await usePreferencesStore.getState().charger('u-1');
    expect(api.get).toHaveBeenCalledWith('/users/me/preferences');
    expect(usePreferencesStore.getState().preferences).toEqual({ theme: 'SOMBRE', juriMasque: true });
    expect(useThemeStore.getState().choix).toBe('SOMBRE');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(usePreferencesStore.getState().erreur).toBeNull();
  });

  it('un choix de theme s applique et n envoie que la cle modifiee', async () => {
    api.get.mockResolvedValue({ data: { theme: 'SYSTEME', juriMasque: true } });
    api.put.mockResolvedValue({ data: undefined });
    const { usePreferencesStore, useThemeStore } = await charger();
    await usePreferencesStore.getState().charger('u-1');
    const ok = await usePreferencesStore.getState().choisirTheme('SOMBRE');
    expect(ok).toBe(true);
    expect(useThemeStore.getState().mode).toBe('dark');
    expect(api.put).toHaveBeenCalledWith('/users/me/preferences', { theme: 'SOMBRE' });
    expect(usePreferencesStore.getState().preferences).toEqual({ theme: 'SOMBRE', juriMasque: true });
  });

  it('echec d enregistrement : message visible, choix garde a l ecran, Reessayer renvoie la modification', async () => {
    api.get.mockResolvedValue({ data: { theme: 'SYSTEME', juriMasque: false } });
    api.put.mockRejectedValueOnce({ response: { status: 500 } }).mockResolvedValueOnce({ data: undefined });
    const { usePreferencesStore, useThemeStore, MESSAGE_ENREGISTREMENT } = await charger();
    await usePreferencesStore.getState().charger('u-1');
    expect(await usePreferencesStore.getState().choisirTheme('CLAIR')).toBe(false);
    expect(usePreferencesStore.getState().erreur).toBe(MESSAGE_ENREGISTREMENT);
    expect(useThemeStore.getState().choix).toBe('CLAIR');
    expect(await usePreferencesStore.getState().reessayer()).toBe(true);
    expect(api.put).toHaveBeenLastCalledWith('/users/me/preferences', { theme: 'CLAIR' });
    expect(usePreferencesStore.getState().erreur).toBeNull();
  });

  it('echec de lecture : message visible (pas de repli silencieux), theme de l ordinateur garde', async () => {
    api.get.mockRejectedValue({ response: { status: 500 } });
    const { usePreferencesStore, useThemeStore, MESSAGE_LECTURE } = await charger();
    await usePreferencesStore.getState().charger('u-1');
    expect(usePreferencesStore.getState().erreur).toBe(MESSAGE_LECTURE);
    expect(useThemeStore.getState().choix).toBe('SYSTEME');
  });

  it('reponse illisible : traitee comme un echec de lecture, rien n est suppose', async () => {
    api.get.mockResolvedValue({ data: { theme: 'BLEU' } });
    const { usePreferencesStore, MESSAGE_LECTURE } = await charger();
    await usePreferencesStore.getState().charger('u-1');
    expect(usePreferencesStore.getState().preferences).toBeNull();
    expect(usePreferencesStore.getState().erreur).toBe(MESSAGE_LECTURE);
  });

  it('403 (mot de passe ou 2FA forces) : SYSTEME et JURI visible, sans erreur', async () => {
    localStorage.setItem('jurika.theme', 'SOMBRE');
    api.get.mockRejectedValue({ response: { status: 403 } });
    const { usePreferencesStore, useThemeStore } = await charger();
    await usePreferencesStore.getState().charger('u-1');
    expect(usePreferencesStore.getState().preferences).toEqual({ theme: 'SYSTEME', juriMasque: false });
    expect(usePreferencesStore.getState().erreur).toBeNull();
    expect(useThemeStore.getState().choix).toBe('SYSTEME');
  });

  it('hors session (page publique) : choix local seulement, aucun appel', async () => {
    const { usePreferencesStore, useThemeStore } = await charger();
    expect(await usePreferencesStore.getState().choisirTheme('SOMBRE')).toBe(true);
    expect(api.put).not.toHaveBeenCalled();
    expect(useThemeStore.getState().mode).toBe('dark');
  });

  it('JURI masque : enregistre la seule cle juriMasque', async () => {
    api.get.mockResolvedValue({ data: { theme: 'CLAIR', juriMasque: false } });
    api.put.mockResolvedValue({ data: undefined });
    const { usePreferencesStore } = await charger();
    await usePreferencesStore.getState().charger('u-1');
    await usePreferencesStore.getState().definirJuriMasque(true);
    expect(api.put).toHaveBeenCalledWith('/users/me/preferences', { juriMasque: true });
    expect(usePreferencesStore.getState().preferences?.juriMasque).toBe(true);
  });
});
