import { useEffect, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { cn } from '../../lib/utils';
import { useAuthStore } from '../../store/authStore';
import { FilAriane, type ElementFil } from '../ui/FilAriane';
import { InfoBulle } from '../ui/Aide';
import { filParDefaut } from './navigation';

/**
 * Lot L6b (E3) : structure unique d'un ecran de la coquille.
 *
 * Le titre et l'action principale sont TOUJOURS au meme endroit : fil d'Ariane au-dessus,
 * titre (h1) a gauche, action principale tout a droite (actions secondaires juste avant
 * elle), description sous le titre, aide (encadre TexteAide) sous l'en-tete.
 *
 *   <Page
 *     titre="Mes tickets"
 *     description="Les demandes de vos clients, de la saisie au dépôt."
 *     action={<Button icon={<Plus />} onClick={creer}>Nouveau ticket</Button>}
 *     actions={<Button variant="secondary">Exporter</Button>}
 *     infoBulle="Un ticket suit une demande du client."
 *     aide={<TexteAide cle="tickets" titre="Vos tickets">...</TexteAide>}
 *   >
 *     ...contenu...
 *   </Page>
 *
 * `fil` : elements du fil d'Ariane ; absent => deduit du menu (Accueil > entree > titre) ;
 * `false` => aucun fil. Le titre de l'onglet du navigateur devient "<titre> - JURIKA".
 */
export interface PageEnTeteProps {
  titre: string;
  description?: ReactNode;
  /** Action principale (un seul bouton `primary`). */
  action?: ReactNode;
  /** Actions secondaires, placees avant l'action principale. */
  actions?: ReactNode;
  fil?: ElementFil[] | false;
  /** Texte d'une infobulle d'aide placee a cote du titre. */
  infoBulle?: string;
  /** Badge ou etat place a cote du titre (ex. statut du dossier). */
  etat?: ReactNode;
  className?: string;
}

export function PageEnTete({ titre, description, action, actions, fil, infoBulle, etat, className }: PageEnTeteProps) {
  const role = useAuthStore((s) => s.user?.role);
  const { pathname } = useLocation();
  const elements = fil === false ? [] : (fil ?? (role ? filParDefaut(role, pathname, titre) : []));

  useEffect(() => {
    const avant = document.title;
    document.title = `${titre} - JURIKA`;
    return () => {
      document.title = avant;
    };
  }, [titre]);

  return (
    <header className={cn('@container mb-6 border-b border-border pb-4', className)}>
      <div className="min-h-[1.125rem]">{elements.length > 0 && <FilAriane elements={elements} />}</div>
      <div className="mt-1.5 flex flex-col gap-3 @2xl:flex-row @2xl:flex-wrap @2xl:items-start @2xl:justify-between">
        {/* Le titre garde au moins 22rem : avec plusieurs actions, elles passent a la ligne au lieu de
            reduire le titre a une colonne etroite (vu a l'ecran, Data Room, lot L6b). */}
        <div className="min-w-0 @2xl:flex-[1_1_22rem]">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-semibold tracking-tight text-heading">{titre}</h1>
            {infoBulle && <InfoBulle texte={infoBulle} libelle={`Aide : ${titre}`} />}
            {etat}
          </div>
          {description && <div className="mt-1 max-w-3xl text-sm text-fg-muted">{description}</div>}
        </div>
        {(action || actions) && (
          <div className="flex flex-wrap items-center gap-2 @2xl:flex-[0_1_auto] @2xl:justify-end" data-zone="actions-page">
            {actions}
            {action}
          </div>
        )}
      </div>
    </header>
  );
}

export interface PageProps extends PageEnTeteProps {
  children?: ReactNode;
  /** Encadre d'aide (TexteAide) sous l'en-tete. */
  aide?: ReactNode;
  /** Largeur du contenu : 'normale' (defaut, celle de la coquille), 'etroite' (formulaire). */
  largeur?: 'normale' | 'etroite';
}

export function Page({ children, aide, largeur = 'normale', className, ...entete }: PageProps) {
  return (
    <div className={cn(largeur === 'etroite' && 'mx-auto max-w-3xl', className)}>
      <PageEnTete {...entete} />
      {aide && <div className="mb-6">{aide}</div>}
      {children}
    </div>
  );
}
