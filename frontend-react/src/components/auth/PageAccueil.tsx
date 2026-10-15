import { useEffect, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { cn } from '../../lib/utils';
import { BrandLogo } from '../ui/BrandLogo';
import { InfoBulle } from '../ui/Aide';

/**
 * Lot L6b (E8) : mise en page d'accueil des ecrans HORS de la coquille (connexion, mot de
 * passe oublie, inscription, essai, verification d'adresse, premiere connexion).
 *
 * Sobre, a la charte : fond clair (marine en sombre), logo Moucharabieh en haut a gauche
 * (BrandLogo suit le theme : ivoire en sombre), contenu centre dans une carte, pied de page
 * discret (conditions generales, confidentialite). Aucun panneau decoratif, aucune donnee
 * inventee. La page defile normalement (aucune hauteur fixe).
 *
 *   <PageAccueil titre="Connexion" enTete={<>Pas de compte ? <Lien to="/signup">...</Lien></>}>
 *     <TitreAccueil icone={<KeyRound />} titre="Accedez a votre espace">...</TitreAccueil>
 *     ...formulaire...
 *   </PageAccueil>
 *
 * `titre` : titre de l'onglet du navigateur ("<titre> - JURIKA").
 * `largeur` : 'etroite' (formulaire court, 28 rem), 'normale' (32 rem), 'large' (parcours
 * en plusieurs etapes, 48 rem), 'pleine' (grille des forfaits, 72 rem).
 * `carte` : faux pour poser le contenu sans carte (le contenu fournit ses propres cartes).
 * `retourAccueil` : faux pour un ecran de session (premiere connexion) ou le logo ne doit pas
 * renvoyer vers la vitrine.
 */
export function PageAccueil({
  titre,
  enTete,
  largeur = 'normale',
  carte = true,
  retourAccueil = true,
  children,
  className,
}: {
  titre: string;
  enTete?: ReactNode;
  largeur?: 'etroite' | 'normale' | 'large' | 'pleine';
  carte?: boolean;
  retourAccueil?: boolean;
  children: ReactNode;
  className?: string;
}) {
  useEffect(() => {
    const avant = document.title;
    document.title = `${titre} - JURIKA`;
    return () => {
      document.title = avant;
    };
  }, [titre]);

  const logo = <BrandLogo variant="full" size={40} />;
  return (
    <div className="flex min-h-dvh flex-col bg-bg text-fg" data-mise-en-page="accueil">
      <header className="flex flex-wrap items-center justify-between gap-3 px-4 py-4 sm:px-8">
        {retourAccueil ? (
          <Link to="/" className="inline-flex shrink-0 rounded-md" aria-label="JURIKA, retour à l’accueil">
            {logo}
          </Link>
        ) : (
          <span className="inline-flex shrink-0">{logo}</span>
        )}
        {enTete && <div className="text-sm text-fg-muted">{enTete}</div>}
      </header>

      <main className="flex flex-1 justify-center px-4 pb-10 pt-2 sm:items-center sm:pt-4">
        <div
          className={cn(
            'w-full',
            { etroite: 'max-w-md', normale: 'max-w-lg', large: 'max-w-3xl', pleine: 'max-w-6xl' }[largeur],
          )}
        >
          {carte ? (
            <div
              className={cn(
                'rounded-xl border border-border border-t-2 border-t-accent bg-bg-raised p-6 shadow-card sm:p-8',
                className,
              )}
            >
              {children}
            </div>
          ) : (
            <div className={className}>{children}</div>
          )}
        </div>
      </main>

      <footer className="px-4 pb-6 text-center text-xs text-fg-subtle">
        <nav aria-label="Informations légales" className="flex flex-wrap items-center justify-center gap-x-4 gap-y-1">
          <Link to="/cgu" className="rounded-sm underline decoration-1 underline-offset-2 hover:text-fg">
            Conditions générales d’utilisation
          </Link>
          <Link to="/confidentialite" className="rounded-sm underline decoration-1 underline-offset-2 hover:text-fg">
            Confidentialité
          </Link>
        </nav>
      </footer>
    </div>
  );
}

/**
 * En-tete d'un ecran d'accueil : icone (decorative) dans une pastille, titre (h1), aide en
 * infobulle a cote du titre, texte d'explication en dessous. `centre` (defaut) : centre.
 */
export function TitreAccueil({
  icone,
  titre,
  infoBulle,
  libelleInfoBulle,
  children,
  centre = true,
  ton = 'accent',
}: {
  icone?: ReactNode;
  titre: ReactNode;
  infoBulle?: string;
  libelleInfoBulle?: string;
  children?: ReactNode;
  centre?: boolean;
  ton?: 'accent' | 'succes' | 'attention' | 'erreur';
}) {
  const teinte = {
    accent: 'bg-accent-soft text-accent',
    succes: 'bg-success-soft text-success',
    attention: 'bg-warning-soft text-warning',
    erreur: 'bg-danger-soft text-danger',
  }[ton];
  return (
    <div className={cn('mb-6', centre && 'text-center')}>
      {icone && (
        <div
          aria-hidden="true"
          className={cn(
            'mb-4 flex h-12 w-12 items-center justify-center rounded-full [&>svg]:h-6 [&>svg]:w-6',
            teinte,
            centre && 'mx-auto',
          )}
        >
          {icone}
        </div>
      )}
      <h1
        className={cn(
          'flex flex-wrap items-center gap-1.5 text-2xl font-semibold tracking-tight text-heading',
          centre && 'justify-center',
        )}
      >
        {titre}
        {infoBulle && <InfoBulle texte={infoBulle} libelle={libelleInfoBulle ?? 'Aide sur cet écran'} />}
      </h1>
      {children && <div className="mt-2 text-sm leading-relaxed text-fg-muted">{children}</div>}
    </div>
  );
}
