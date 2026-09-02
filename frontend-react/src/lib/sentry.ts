import * as Sentry from '@sentry/react';

/**
 * Sprint 2 / TASK 5 — Init Sentry frontend.
 *
 * RGPD by design : send-default-pii false (Sentry SDK Web n'envoie pas l'email,
 * mais on strip aussi event.user.email et event.user.ip_address par precaution
 * dans beforeSend, au cas ou un appel a Sentry.setUser({ email }) serait fait
 * par erreur ailleurs).
 *
 * Replay : maskAllText + blockAllMedia pour eviter toute fuite de contenu.
 */
export function initSentry(): void {
  const dsn = import.meta.env.VITE_SENTRY_DSN_FRONTEND as string | undefined;
  if (!dsn) {
    // Pas de DSN -> Sentry desactive (dev local).
    return;
  }

  Sentry.init({
    dsn,
    environment: import.meta.env.MODE,
    release: (import.meta.env.VITE_RELEASE as string | undefined) ?? 'dev',
    integrations: [
      Sentry.browserTracingIntegration(),
      Sentry.replayIntegration({
        maskAllText: true,
        blockAllMedia: true,
      }),
    ],
    tracesSampleRate: Number(import.meta.env.VITE_SENTRY_TRACES_SAMPLE_RATE ?? 0.1),
    replaysSessionSampleRate: Number(import.meta.env.VITE_SENTRY_REPLAYS_SESSION_RATE ?? 0.05),
    replaysOnErrorSampleRate: Number(import.meta.env.VITE_SENTRY_REPLAYS_ON_ERROR_RATE ?? 1.0),
    sendDefaultPii: false,
    beforeSend(event) {
      // RGPD : strip email + ip avant envoi.
      if (event.user) {
        delete event.user.email;
        delete event.user.ip_address;
        delete event.user.username;
      }
      if (event.request) {
        delete event.request.cookies;
      }
      return event;
    },
  });
}

export { Sentry };
