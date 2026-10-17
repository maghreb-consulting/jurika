import { Monitor, Moon, Sun } from 'lucide-react';
import { LIBELLES_THEME, useThemeStore } from '../../store/themeStore';
import { usePreferencesStore } from '../../store/preferencesStore';
import { Menu, MenuChoix } from '../ui/Menu';

/**
 * Lot L6b (E3) : choix du theme dans l'en-tete : selon l'ordinateur (defaut), clair ou
 * sombre. Le choix s'applique tout de suite et s'enregistre sur le compte
 * (store/preferencesStore) ; un echec d'enregistrement s'affiche dans la coquille.
 */
export function ThemeToggle() {
  const choix = useThemeStore((s) => s.choix);
  const mode = useThemeStore((s) => s.mode);
  const choisirTheme = usePreferencesStore((s) => s.choisirTheme);
  const Icone = choix === 'SYSTEME' ? Monitor : mode === 'dark' ? Moon : Sun;
  const effectif = mode === 'dark' ? 'sombre' : 'clair';
  const libelle = `Thème : ${LIBELLES_THEME[choix].toLowerCase()}${choix === 'SYSTEME' ? ` (${effectif})` : ''}`;

  return (
    <Menu
      libelle={libelle}
      titre={libelle}
      alignement="fin"
      classeBouton="h-9 w-9 justify-center text-fg-muted hover:text-fg"
      bouton={<Icone className="h-5 w-5" aria-hidden="true" />}
    >
      <p className="px-4 pb-1 pt-2 text-xs font-semibold uppercase tracking-wide text-fg-subtle">Thème</p>
      <MenuChoix icone={<Monitor />} coche={choix === 'SYSTEME'} onSelect={() => void choisirTheme('SYSTEME')}>
        {LIBELLES_THEME.SYSTEME}
      </MenuChoix>
      <MenuChoix icone={<Sun />} coche={choix === 'CLAIR'} onSelect={() => void choisirTheme('CLAIR')}>
        {LIBELLES_THEME.CLAIR}
      </MenuChoix>
      <MenuChoix icone={<Moon />} coche={choix === 'SOMBRE'} onSelect={() => void choisirTheme('SOMBRE')}>
        {LIBELLES_THEME.SOMBRE}
      </MenuChoix>
    </Menu>
  );
}
