# JURIKA front-end — Theme & design tokens

> **Sprint 12.5 (v0.83.0)** — Alignement palette marketing-site Flagship (navy /
> or / emeraude + Playfair Display) en mode dark adapte, avec switcher
> light/dark optionnel.

---

## Stratégie

Le frontend-react adopte la palette marketing en mode **dark par défaut**
(confort de travail prolongé, marché juridique premium). Un toggle Sun/Moon
en navbar permet de basculer en light editorial pour les sessions courtes
ou les utilisateurs qui le préfèrent.

> **Pourquoi pas un clone littéral du marketing-site light ?** Les sessions
> juridiques durent souvent 8h/j. Le pattern Linear/Stripe/Vercel : landing
> light éditorial premium (séduction), app dashboard dark sobre (production).
>
> Détail des arbitrages : `output/PLAN_SPRINT_12-5_THEME_ALIGNMENT.md` §1.

---

## Source de vérité : tokens CSS

Tous les couleurs / typos / shadows passent par les CSS variables du `@theme`
block de `src/index.css` (Tailwind v4 syntax). **Pas** de `tailwind.config.js`.

### Palette dark (défaut)

| Token | Hex | Usage |
|---|---|---|
| `--color-bg` | `#050d1f` | Fond principal (navy-900) |
| `--color-bg-raised` | `#0a1628` | Cards, panels, navbar (navy-800) |
| `--color-bg-overlay` | `#112240` | Modals, popovers, dropdowns (navy-700) |
| `--color-border` | `#1c3461` | Bordures discrètes (navy-600) |
| `--color-border-hi` | `#2a4a85` | Bordures actives, focus (navy-500) |
| `--color-fg` | `#f8fafc` | Texte principal |
| `--color-fg-muted` | `#cbd5e1` | Texte secondaire |
| `--color-fg-subtle` | `#64748b` | Texte tertiaire / hint |
| `--color-accent` | `#c8a45c` | **Or signature** (CTAs, links actifs, focus) |
| `--color-accent-hover` | `#d4b06a` | Hover or |
| `--color-accent-soft` | `#e3c688` | Backgrounds soft accent |
| `--color-success` | `#10b981` | Émeraude (validé) |
| `--color-warning` | `#f59e0b` | Ambre |
| `--color-danger` | `#ef4444` | Rouge danger / irreversible |

### Palette light (toggle)

Activée via `<html data-theme="light">`. L'attribut est posé par
`useThemeStore.applyDomTheme()` au mount + à chaque toggle.

| Token | Hex | Usage |
|---|---|---|
| `--color-bg` | `#fafaf7` | Vélin éditorial |
| `--color-bg-raised` | `#ffffff` | Cards blanches |
| `--color-fg` | `#050d1f` | Navy-900 en texte |
| `--color-accent` | `#a8853d` | Or durci AA sur fond clair |

### Typographies

| Token | Famille | Usage |
|---|---|---|
| `--font-sans` | Inter | Body texte |
| `--font-heading` | Playfair Display | H1/H2/H3, signatures editoriales |
| `--font-mono` | JetBrains Mono | Codes, IDs, numéros workspace |

Classes utility : `.font-heading`, `.font-mono`, `.eyebrow` (uppercase mono),
`.font-tight` (compat ascendante avec composants pré-Sprint 12.5).

### Shadows

| Token | Valeur | Usage |
|---|---|---|
| `--shadow-card` | `0 8px 24px rgba(5,13,31,0.4)` | Cards repos |
| `--shadow-card-lifted` | `0 30px 80px -20px rgba(5,13,31,0.6)` | Hover lift |
| `--shadow-glow-gold` | `0 0 60px rgba(200,164,92,0.25)` | Glow CTA primaire |

---

## Theme switcher

### Frontend

- **Store** : `src/store/themeStore.ts` (Zustand persist).
  - State `{ mode: 'dark' | 'light' }`.
  - Persisté en localStorage sous la clé `jurika-theme`.
- **Composant** : `src/components/layout/ThemeToggle.tsx` (Sun/Moon Lucide).
  - Aria-label + aria-pressed + title (a11y).
- **DOM sync** : `applyDomTheme()` pose `<html data-theme="light">` (mode
  light) ou retire l'attribut (mode dark = baseline).
- **DB sync** : `src/services/user.service.ts > setPreferredTheme(mode)`
  appelle `POST /api/v1/users/me/theme`. Fire-and-forget (warn dev only si
  endpoint indisponible).

### Backend (V22)

- **Migration** :
  `backend-java/auth-service/src/main/resources/db/migration/V22__add_workspace_preferred_theme.sql`
- **Colonne** : `workspaces.preferred_theme VARCHAR(10) NOT NULL DEFAULT 'dark'`.
- **Contrainte** : `CHECK (preferred_theme IN ('dark', 'light'))`.
- **Endpoint a implementer** : `POST /api/v1/users/me/theme` payload
  `{"theme": "light"|"dark"}` + audit_log + business_event.

---

## Animations (Sprint 12.5 T13, subset marketing)

| Effet | Implémentation | Active |
|---|---|---|
| Grain overlay 3% | `src/components/layout/GrainOverlay.tsx`, fixed inset-0 z-40, mix-blend-mode overlay | Toute l'app |
| Lenis smooth scroll | `src/lib/useLenisScroll.ts`, hook appelé dans App.tsx | Toute l'app |
| Gold focus ring | `:focus-visible` dans index.css, outline 2px accent + offset 4px | Tout élément focusable |
| Hover lift card | `.lift-on-hover` utility class + Card prop `interactive` | Cards cliquables |
| Magnetic hover | Button prop `magnetic`, port marketing | CTAs primaires uniquement |

**Tous respectent `prefers-reduced-motion: reduce`** :
- Grain : Tailwind class `motion-reduce:hidden` (auto)
- Lenis : matchMedia check, pas d'instance créée si reduce
- Lift : transition + transform: none dans media query
- Magnetic : matchMedia check au mousemove

---

## Migration depuis pré-Sprint 12.5

Les composants utilisant les **anciens tokens "LOCKED"** (`#121414`,
`#E3E2E2`, `#2563EB`) ne sont **pas** automatiquement migrés -- ils doivent
être convertis ciblés pour passer aux tokens. Cf commits T3-T12 pour
l'inventaire ce qui a été refactorisé Sprint 12.5.

Composants non refactorisés (sweep T12 a converti la majorité mais pas
TOUT) : peuvent rester sur leurs classes slate-*/indigo-* hardcoded en
attendant Sprint 13 i18n + audit final. Ils rendent quand même sur navy
dark (les classes slate sont assez sombres).

### Quick-fix pattern pour migrer un composant existant

```diff
- className="bg-white border border-slate-200 text-slate-900"
+ className="bg-bg-raised border border-border text-fg"

- className="bg-indigo-600 hover:bg-indigo-700 text-white"
+ className="bg-accent hover:bg-accent-hover text-bg"

- <h1 className="text-2xl font-bold text-slate-900">
+ <h1 className="font-heading text-2xl font-semibold text-fg">
```

---

## Tests visuels

- **Spec** : `e2e/theme-visual-regression.spec.ts` (Sprint 12.5 T14).
- **Cibles** : 10 pages clés × 2 modes (dark/light) = 20 screenshots.
- **Run** :
  ```bash
  npm run dev &  # serveur Vite :5173
  npx playwright test e2e/theme-visual-regression.spec.ts
  ```
- **Sortie** : `.screenshots/sprint-12-5/{dark,light}/*.png` (gitignored).

---

## Marketing-site

**INTACT.** Aucun fichier de `marketing-site/` n'est touché par Sprint 12.5.
La cohérence brand passe par la palette + Playfair + accent or partagés,
pas par une fusion de code.

---

*Sprint 12.5 — v0.83.0 — 2026-06-01*
