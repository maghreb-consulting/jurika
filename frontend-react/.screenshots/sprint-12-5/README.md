# Sprint 12.5 — Visual Regression Baseline

Screenshots avant/après refactor theme generes via `theme-visual-regression.spec.ts`.

## Layout

```
.screenshots/sprint-12-5/
├── dark/                  # mode dark (defaut)
│   ├── landing.png
│   ├── login.png
│   ├── signup.png
│   ├── dashboard.png
│   ├── tickets.png
│   ├── dataroom.png
│   ├── chat.png
│   ├── chatbot.png
│   ├── billing.png
│   └── forbidden.png
└── light/                 # mode light (toggle T2)
    └── ... (memes 10 pages)
```

## Comment generer

1. Lancer le dev server : `npm run dev` (Vite :5173)
2. Pour les pages auth-only, exporter le cookie de session :
   ```bash
   export E2E_AUTH_COOKIE=$(curl -s ... | jq -r '.cookie')
   ```
3. Lancer la suite visual :
   ```bash
   npx playwright test e2e/theme-visual-regression.spec.ts --headed
   ```

## Comparaison avant/apres

Les screenshots de référence ANTE-Sprint 12.5 sont a recuperer du tag
`v0.82.1` :

```bash
git stash
git checkout v0.82.1
npm install --legacy-peer-deps
npx playwright test e2e/theme-visual-regression.spec.ts  # echoue si spec absent
git checkout -
git stash pop
```

Alternative : comparer visuellement avec les baselines staging existantes.

## Acceptance check

- [ ] 10 screenshots dark genere
- [ ] 10 screenshots light genere
- [ ] Aucune zone vide ou contraste illisible
- [ ] Gold focus ring visible au Tab (test A11y dedie)
- [ ] Tokens CSS verifies (test @theme-tokens)

Sprint 12.5 T14 — V1 2026-06-01.
