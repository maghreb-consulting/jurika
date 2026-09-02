import { api } from '../lib/api';

/**
 * Sprint 11 TASK 6 — Mini client analytics best-effort vers
 * POST /api/v1/public/events (rate limite 100/min/IP cote gateway,
 * rewrite vers /internal/events sur supervision-service).
 *
 * Best-effort : un appel qui echoue NE casse JAMAIS l'UX (les events
 * sont du nice-to-have observabilite, pas du critique).
 *
 * Implementation (hardening 2026-06-02) :
 *  1. Priorite a {@code navigator.sendBeacon} — la request part en background,
 *     ne pollue PAS la console DevTools en rouge meme si le serveur 500,
 *     survit a un unload (ideal pour SIGNUP_COMPLETED juste avant redirect).
 *  2. Fallback axios silencieux si sendBeacon indispo (vieux navigateurs) ou
 *     si on a besoin du retour serveur (jamais pour analytics).
 */
interface EmitOpts {
  workspaceId?: string;
  properties?: Record<string, unknown>;
}

// Base URL de l'API (memes regles que axios) — sendBeacon ne traverse PAS les
// interceptors axios, donc on construit l'URL absolue.
const API_BASE = (import.meta.env.VITE_API_URL as string | undefined) || 'http://localhost:8080/api/v1';

export function emitBusinessEvent(eventType: string, opts: EmitOpts = {}): void {
  const payload = {
    eventType,
    workspaceId: opts.workspaceId,
    properties: opts.properties || {},
    source: 'spa',
    occurredAt: new Date().toISOString(),
  };

  // Tente sendBeacon d'abord : background fetch silencieux, pas de console error
  // visible meme en cas de 500/503/timeout cote serveur.
  if (typeof navigator !== 'undefined' && typeof navigator.sendBeacon === 'function') {
    try {
      const blob = new Blob([JSON.stringify(payload)], { type: 'application/json' });
      const ok = navigator.sendBeacon(`${API_BASE}/public/events`, blob);
      if (ok) return;
    } catch {
      // tombera dans le fallback axios ci-dessous
    }
  }

  // Fallback : axios silent (try/catch swallow). Visible en console MAIS rare
  // (uniquement navigateurs vetustes ou Blob/sendBeacon bloque par CSP stricte).
  api.post('/public/events', payload).catch(() => {
    /* analytics non-critique : on absorbe */
  });
}
