import { Globe, Moon, Sun } from 'lucide-react';
import { useThemeStore } from '../../store/themeStore';
import { Button } from '../../components/ui/Button';

/**
 * BUG 11 (2026-06-08) — Onglet Preferences dans /settings.
 *  - Theme : reutilise le themeStore (clair / sombre, persistance localStorage
 *    + sync DB via userService.setPreferredTheme dans la version branchee a
 *    AppTopNav.ThemeToggle).
 *  - Langue : FR uniquement en V1 (placeholder — la roadmap i18n est sprint 13).
 *
 * On evite de cloner ThemeToggle ici pour ne pas dupliquer la logique de sync :
 * on rend deux boutons explicites qui muent le store.
 */
export function PreferencesTab() {
  const mode = useThemeStore((s) => s.mode);
  const setMode = useThemeStore((s) => s.setMode);

  return (
    <div className="space-y-4">
      <div>
        <h2 className="flex items-center gap-2 text-lg font-semibold text-fg">
          <Globe className="h-5 w-5 text-accent" /> Preferences
        </h2>
        <p className="mt-1 text-sm text-fg-muted">
          Personnalisez l'apparence et la langue de votre espace JURIKA.
        </p>
      </div>

      {/* Theme */}
      <section className="rounded-2xl border border-border bg-bg-raised p-5">
        <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-fg-subtle">
          Apparence
        </h3>
        <div className="flex flex-wrap gap-2">
          <Button
            variant={mode === 'light' ? 'primary' : 'secondary'}
            onClick={() => setMode('light')}
            data-testid="pref-theme-light"
          >
            <Sun className="mr-1.5 h-4 w-4" /> Mode clair
          </Button>
          <Button
            variant={mode === 'dark' ? 'primary' : 'secondary'}
            onClick={() => setMode('dark')}
            data-testid="pref-theme-dark"
          >
            <Moon className="mr-1.5 h-4 w-4" /> Mode sombre
          </Button>
        </div>
        <p className="mt-3 text-xs text-fg-muted">
          Le mode s'applique immediatement et reste synchronise sur tous vos
          appareils connectes au meme compte (synchro DB).
        </p>
      </section>

      {/* Langue */}
      <section className="rounded-2xl border border-border bg-bg-raised p-5">
        <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-fg-subtle">
          Langue
        </h3>
        <div className="flex items-center gap-2">
          <Button variant="primary" disabled>
            Francais (FR)
          </Button>
          <Button variant="secondary" disabled title="Bientot — Sprint 13 i18n">
            العربية (AR)
          </Button>
          <Button variant="secondary" disabled title="Bientot — Sprint 13 i18n">
            English (EN)
          </Button>
        </div>
        <p className="mt-3 text-xs text-fg-muted">
          L'internationalisation AR/EN arrive avec la phase Sprint 13.
        </p>
      </section>
    </div>
  );
}
