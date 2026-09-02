# Tests E2E JURIKA — Playwright

## Setup (one-time)

```bash
cd tests/e2e
npm install
npm run install:browsers
```

## Prérequis

- Backend complet UP : `.\scripts\start-all.ps1`
- Frontend Vite UP sur `http://localhost:5173`
- Comptes seed présents : `karim@jurika.ma` / `Admin@2026` / `JUR-DEMO1`

## Lancer

```bash
npm test              # headless
npm run test:headed   # visible
npm run test:ui       # UI mode (debug)
npm run report        # rapport HTML
```

## Specs

- `auth.spec.ts` — Login 3 étapes / workspace inconnu / logout
- `tickets.spec.ts` — Liste, filtre, création ticket
- `dataroom.spec.ts` — Onglets Juridique/Comptable/Demandes, Archive supprimé

## CI

```bash
E2E_BASE_URL=http://staging.jurika.ma npm test
```
