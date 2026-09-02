import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { initSentry, Sentry } from './lib/sentry'
import { SentryErrorFallback } from './components/misc/SentryErrorFallback'

// Sprint 2 / TASK 5 — init Sentry avant tout rendu (sinon les erreurs initiales sont perdues).
initSentry();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Sentry.ErrorBoundary fallback={({ resetError }) => <SentryErrorFallback resetError={resetError} />}>
      <App />
    </Sentry.ErrorBoundary>
  </StrictMode>,
)
