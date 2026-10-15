import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Menu as IconeMenu, Monitor, Moon, Sun, X } from 'lucide-react';
import { BrandLogo } from '../../../components/ui/BrandLogo';
import { Lien } from '../../../components/ui/Lien';
import { Menu, MenuChoix } from '../../../components/ui/Menu';
import { LIBELLES_THEME, useThemeStore } from '../../../store/themeStore';
import { LiensSections } from './LiensSections';

/**
 * Lot L6b (E10) : en-tete de la vitrine (accueil et mentions legales).
 *
 * Hauteur fixe (4 rem) et logo dans une boite aux dimensions du logo : rien ne bouge au
 * chargement. Sous 1280 px, les liens des sections passent dans un panneau ouvert par le
 * bouton "Menu" (aria-expanded, Echap ferme). Le theme se choisit pour ce navigateur
 * (visiteur sans compte : aucun appel au serveur).
 */
function ChoixThemeVitrine() {
  const choix = useThemeStore((s) => s.choix);
  const mode = useThemeStore((s) => s.mode);
  const appliquerChoix = useThemeStore((s) => s.appliquerChoix);
  const Icone = choix === 'SYSTEME' ? Monitor : mode === 'dark' ? Moon : Sun;
  const libelle = `Thème : ${LIBELLES_THEME[choix].toLowerCase()}`;
  return (
    <Menu
      libelle={libelle}
      titre={libelle}
      alignement="fin"
      classeBouton="h-9 w-9 justify-center text-fg-muted hover:text-fg"
      bouton={<Icone className="h-5 w-5" aria-hidden="true" />}
    >
      <p className="px-4 pb-1 pt-2 text-xs font-semibold uppercase tracking-wide text-fg-subtle">Thème</p>
      <MenuChoix icone={<Monitor />} coche={choix === 'SYSTEME'} onSelect={() => appliquerChoix('SYSTEME')}>
        {LIBELLES_THEME.SYSTEME}
      </MenuChoix>
      <MenuChoix icone={<Sun />} coche={choix === 'CLAIR'} onSelect={() => appliquerChoix('CLAIR')}>
        {LIBELLES_THEME.CLAIR}
      </MenuChoix>
      <MenuChoix icone={<Moon />} coche={choix === 'SOMBRE'} onSelect={() => appliquerChoix('SOMBRE')}>
        {LIBELLES_THEME.SOMBRE}
      </MenuChoix>
    </Menu>
  );
}

export function EnTeteVitrine({ surAccueil }: { surAccueil: boolean }) {
  const [ouvert, setOuvert] = useState(false);

  useEffect(() => {
    if (!ouvert) return undefined;
    const surTouche = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOuvert(false);
    };
    window.addEventListener('keydown', surTouche);
    return () => window.removeEventListener('keydown', surTouche);
  }, [ouvert]);

  const logo = (
    <span className="block h-9 w-[142px]">
      <BrandLogo variant="full" size={36} />
    </span>
  );

  return (
    <header className="sticky top-0 z-40 border-b border-border bg-bg-raised/90 backdrop-blur-md">
      <div className="mx-auto flex h-16 max-w-6xl items-center gap-6 px-4 sm:px-6">
        {surAccueil ? (
          <a href="#haut" className="shrink-0 rounded-sm" aria-label="JURIKA, haut de la page">
            {logo}
          </a>
        ) : (
          <Link to="/" className="shrink-0 rounded-sm" aria-label="JURIKA, accueil">
            {logo}
          </Link>
        )}

        <nav aria-label="Sections de la vitrine" className="hidden flex-1 items-center gap-1 xl:flex">
          <LiensSections surAccueil={surAccueil} classeLien="rounded-md px-3 py-2 text-sm font-medium text-fg-muted hover:bg-bg-overlay hover:text-fg" />
        </nav>

        <div className="ml-auto flex items-center gap-2">
          <ChoixThemeVitrine />
          <Lien to="/login" commeBouton={{ variant: 'secondary', size: 'md' }} className="hidden sm:inline-flex" data-testid="navbar-login-cta">
            Se connecter
          </Lien>
          <Lien to="/essai" commeBouton={{ variant: 'primary', size: 'md' }} className="hidden sm:inline-flex" data-testid="navbar-essai-cta">
            Essai gratuit
          </Lien>
          <button
            type="button"
            className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-fg hover:bg-bg-overlay xl:hidden"
            aria-expanded={ouvert}
            aria-controls="menu-vitrine"
            aria-label={ouvert ? 'Fermer le menu' : 'Ouvrir le menu'}
            onClick={() => setOuvert((o) => !o)}
          >
            {ouvert ? <X className="h-5 w-5" aria-hidden="true" /> : <IconeMenu className="h-5 w-5" aria-hidden="true" />}
          </button>
        </div>
      </div>

      {ouvert && (
        <nav
          id="menu-vitrine"
          aria-label="Sections de la vitrine"
          className="border-t border-border bg-bg-raised px-4 pb-4 pt-2 shadow-card-lifted sm:px-6 xl:hidden"
        >
          <div className="mx-auto flex max-w-6xl flex-col">
            <LiensSections
              surAccueil={surAccueil}
              classeLien="rounded-md px-3 py-2.5 text-base font-medium text-fg hover:bg-bg-overlay"
              onChoisir={() => setOuvert(false)}
            />
            <div className="mt-3 flex flex-col gap-2 sm:hidden">
              <Lien to="/essai" commeBouton={{ variant: 'primary', size: 'lg' }}>
                Essai gratuit
              </Lien>
              <Lien to="/login" commeBouton={{ variant: 'secondary', size: 'lg' }}>
                Se connecter
              </Lien>
            </div>
          </div>
        </nav>
      )}
    </header>
  );
}
