import { Moon, Sun } from 'lucide-react';
import { useThemeStore } from '../../store/themeStore';
import { userService } from '../../services/user.service';

/**
 * Sprint 12.5/12.6 — Theme switcher icon button.
 *
 * Default mode = light (post-pivot Strategie A). Logique d'icone identique :
 * - Mode light affiche Moon (l'utilisateur peut "couper la lumiere" -> dark)
 * - Mode dark affiche Sun (l'utilisateur peut "rallumer" -> light)
 *
 * Toggle store -> applique data-theme="dark" sur <html> ou retire l'attribut
 * (light = baseline implicite, dark = override explicite).
 * Fire-and-forget sync DB cote serveur (backend V22) ; localStorage prend
 * le relais si l'endpoint n'est pas encore disponible.
 */
export function ThemeToggle() {
  const mode = useThemeStore((s) => s.mode);
  const toggle = useThemeStore((s) => s.toggle);

  const handleClick = () => {
    const next = mode === 'dark' ? 'light' : 'dark';
    toggle();
    void userService.setPreferredTheme(next);
  };

  const isDark = mode === 'dark';
  const Icon = isDark ? Sun : Moon;
  const label = isDark ? 'Passer en mode clair' : 'Passer en mode sombre';

  return (
    <button
      type="button"
      onClick={handleClick}
      aria-label={label}
      aria-pressed={!isDark}
      title={label}
      className="p-2 text-fg-subtle transition hover:text-fg"
    >
      <Icon className="h-5 w-5" />
    </button>
  );
}
