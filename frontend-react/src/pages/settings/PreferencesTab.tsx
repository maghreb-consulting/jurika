import { Globe, Monitor, Moon, Sun } from 'lucide-react';
import { LIBELLES_THEME, useThemeStore, type ChoixTheme } from '../../store/themeStore';
import { usePreferencesStore } from '../../store/preferencesStore';
import { Button } from '../../components/ui/Button';
import { TexteAide } from '../../components/ui/Aide';

/**
 * BUG 11 (2026-06-08) — Onglet Preferences dans /settings.
 *  - Theme (lot L6b, E3) : selon l'ordinateur (defaut), clair ou sombre ; applique tout de
 *    suite et memorise SUR LE COMPTE (store/preferencesStore, PUT /users/me/preferences).
 *    Un echec d'enregistrement s'affiche dans la coquille (bandeau "Preferences d'affichage").
 *  - Langue : FR uniquement en V1.
 */
const CHOIX: { choix: ChoixTheme; icone: typeof Sun; testId: string }[] = [
  { choix: 'SYSTEME', icone: Monitor, testId: 'pref-theme-systeme' },
  { choix: 'CLAIR', icone: Sun, testId: 'pref-theme-light' },
  { choix: 'SOMBRE', icone: Moon, testId: 'pref-theme-dark' },
];

export function PreferencesTab() {
  const choix = useThemeStore((s) => s.choix);
  const choisirTheme = usePreferencesStore((s) => s.choisirTheme);

  return (
    <div className="space-y-4">
      <div>
        <h2 className="flex items-center gap-2 text-lg font-semibold text-fg">
          <Globe className="h-5 w-5 text-accent" aria-hidden="true" /> Préférences
        </h2>
        <p className="mt-1 text-sm text-fg-muted">
          Personnalisez l’apparence et la langue de votre espace JURIKA.
        </p>
      </div>

      <TexteAide cle="preferences" titre="Apparence et langue">
        <p>
          Choisissez « Selon l’ordinateur » pour suivre le réglage clair ou sombre de votre
          ordinateur, ou imposez « Clair » ou « Sombre ». Le changement est immédiat et suit
          votre compte sur tous vos appareils ; rien d’autre à enregistrer.
        </p>
      </TexteAide>

      {/* Theme */}
      <section className="rounded-xl border border-border bg-bg-raised p-5">
        <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-fg-subtle">
          Apparence
        </h3>
        <div className="flex flex-wrap gap-2" role="group" aria-label="Thème">
          {CHOIX.map(({ choix: c, icone: Icone, testId }) => (
            <Button
              key={c}
              variant={choix === c ? 'primary' : 'secondary'}
              aria-pressed={choix === c}
              onClick={() => void choisirTheme(c)}
              data-testid={testId}
              icon={<Icone />}
            >
              {LIBELLES_THEME[c]}
            </Button>
          ))}
        </div>
        <p className="mt-3 text-xs text-fg-muted">
          Le thème s’applique immédiatement et reste mémorisé sur votre compte.
        </p>
      </section>

      {/* Langue */}
      <section className="rounded-xl border border-border bg-bg-raised p-5">
        <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-fg-subtle">
          Langue
        </h3>
        <div className="flex items-center gap-2">
          <Button variant="primary" disabled>
            Français (FR)
          </Button>
          <Button variant="secondary" disabled title="Pas encore disponible">
            العربية (AR)
          </Button>
          <Button variant="secondary" disabled title="Pas encore disponible">
            English (EN)
          </Button>
        </div>
        <p className="mt-3 text-xs text-fg-muted">
          L’interface est en français. L’arabe et l’anglais ne sont pas encore disponibles.
        </p>
      </section>
    </div>
  );
}
