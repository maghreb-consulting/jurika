/**
 * Mini client HTTP pour la landing standalone JURIKA.
 *
 * Pas de dépendance externe (axios/fetch wrappers) — `fetch` natif suffit
 * pour la landing (calls publics POST simples : leads démo + events).
 *
 * Gestion :
 *  - 200/201 : retourne JSON parsed
 *  - 400/422 : throw `ApiError` avec `code`, `message`, `fieldErrors`
 *  - 429 : throw `ApiError` avec `code='RATE_LIMITED'`
 *  - autres : throw `ApiError` avec `code='SERVER_ERROR'`
 */

import { API_URL } from '../config/pricing.js';

export class ApiError extends Error {
  constructor(code, message, fieldErrors = {}) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

export async function postJson(path, body, { signal } = {}) {
  let response;
  try {
    response = await fetch(`${API_URL}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal,
    });
  } catch (err) {
    throw new ApiError('NETWORK_ERROR', 'Impossible de joindre le serveur. Réessayez dans quelques instants.');
  }

  if (response.status === 204) return null;

  let data = null;
  try {
    data = await response.json();
  } catch {
    // pas de body JSON
  }

  if (response.ok) return data;

  if (response.status === 429) {
    throw new ApiError('RATE_LIMITED', 'Trop de tentatives. Merci de réessayer dans 1 heure.');
  }
  if (response.status === 400 || response.status === 422) {
    throw new ApiError(
      data?.code || 'VALIDATION_ERROR',
      data?.message || 'Données invalides.',
      data?.fieldErrors || {},
    );
  }
  throw new ApiError('SERVER_ERROR', 'Erreur technique. Notre équipe a été notifiée.');
}

/**
 * POST lead démo. Le backend déclenche email commercial + business_event.
 */
export function submitDemoRequest(payload, opts) {
  return postJson('/api/v1/public/leads/demo-request', payload, opts);
}

/**
 * POST business_event (analytics). Best-effort, ne throw jamais (les events
 * landing ne doivent jamais casser l'UI).
 *
 * @param {string} eventType  ex: 'PAGE_VIEWED', 'DEMO_REQUESTED', 'CTA_CLICKED'
 * @param {object} properties payload arbitraire
 */
export async function emitEvent(eventType, properties = {}) {
  try {
    await postJson('/api/v1/public/events', {
      eventType,
      properties,
      source: 'marketing-site',
      occurredAt: new Date().toISOString(),
    });
  } catch (err) {
    // analytics best-effort — on log mais on n'interrompt pas l'UX
    if (typeof console !== 'undefined') console.debug('[events] emit failed:', eventType, err.code);
  }
}
