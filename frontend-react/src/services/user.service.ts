import { api } from '../lib/api';
import type { ThemeMode } from '../store/themeStore';

/**
 * Sprint 12.5 T2 — User preferences service.
 *
 * POST /api/v1/users/me/theme : persiste le thème préféré côté serveur
 * (workspace.preferred_theme, migration V22 attendue côté backend).
 * Si le backend n'est pas encore prêt (404/501), on log doucement sans casser l'UX
 * — le localStorage côté front prend le relais en attendant.
 */
export interface ThemePayload {
  theme: ThemeMode;
}

export const userService = {
  async setPreferredTheme(theme: ThemeMode): Promise<void> {
    try {
      await api.post<void>('/users/me/theme', { theme } satisfies ThemePayload);
    } catch (err: unknown) {
      // Backend V22 pas encore appliqué -> degrade gracieux, on ne lève pas.
      // L'utilisateur reste sur sa préférence locale (localStorage).
      if (import.meta.env.DEV) {
        // eslint-disable-next-line no-console
        console.warn('[userService] setPreferredTheme deferred (backend V22 pending):', err);
      }
    }
  },
};
