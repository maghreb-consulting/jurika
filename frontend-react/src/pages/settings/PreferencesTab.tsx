import { Globe, Monitor, Moon, Sun } from 'lucide-react';
import { LIBELLES_THEME, useThemeStore, type ChoixTheme } from '../../store/themeStore';
import { usePreferencesStore } from '../../store/preferencesStore';
import { Button } from '../../components/ui/Button';
import { TexteAide } from '../../components/ui/Aide';
import { Badge } from '../../components/ui/Badge';
import { Card } from '../../components/ui/Card';
import { SectionOnglet } from './EcranOuOnglet';

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
    <SectionOnglet
      titre="Préférences"
      description="Personnalisez l’apparence et la langue de votre espace JURIKA."
      aide={
        <TexteAide cle="preferences" titre="Apparence et langue">
          <p>
            Choisissez « Selon l’ordinateur » pour suivre le réglage clair ou sombre de votre
            ordinateur, ou imposez « Clair » ou « Sombre ». Le changement est immédiat et suit
            votre compte sur tous vos appareils ; rien d’autre à enregistrer.
          </p>
        </TexteAide>
      }
    >
      <div className="space-y-4">
        <Card title="Apparence" description="Le thème s’applique immédiatement et reste mémorisé sur votre compte.">
          <div className="flex flex-wrap gap-2" role="group" aria-label="Thème">
            {CHOIX.map(({ choix: c, icone: Icone, testId }) => (
              <Button
                key={c}
                type="button"
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
        </Card>

        <Card title="Langue" description="L’interface est en français. L’arabe et l’anglais ne sont pas encore disponibles.">
          <ul className="flex flex-wrap items-center gap-2" aria-label="Langues de l’interface">
            <li>
              <Badge variant="default" dot>
                <Globe className="h-3.5 w-3.5" aria-hidden="true" /> Français (FR), en usage
              </Badge>
            </li>
            <li>
              <Badge variant="neutral" title="Pas encore disponible">
                <span lang="ar">العربية</span> (AR), pas encore disponible
              </Badge>
            </li>
            <li>
              <Badge variant="neutral" title="Pas encore disponible">
                English (EN), pas encore disponible
              </Badge>
            </li>
          </ul>
        </Card>
      </div>
    </SectionOnglet>
  );
}
