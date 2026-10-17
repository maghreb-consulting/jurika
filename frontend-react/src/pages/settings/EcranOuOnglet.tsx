import { createContext, useContext, useId, type ReactNode } from 'react';
import { Page, type PageProps } from '../../components/layout/Page';
import { InfoBulle } from '../../components/ui/Aide';
import { cn } from '../../lib/utils';

/**
 * Lot L6b (E8) : un meme ecran (Mon compte, Securite, Equipe, Facturation) s'affiche soit
 * seul (route propre : structure unique <Page>, h1, fil d'Ariane, titre de l'onglet), soit
 * comme onglet de la page Parametres (/settings/*), qui porte deja le h1 et le fil : l'ecran
 * devient alors une section titree en h2, avec la MEME action principale au meme endroit
 * (a droite du titre) et la meme aide. Aucune logique ne change.
 */
const EnOngletContext = createContext(false);

/** Enveloppe des onglets de la page Parametres. */
export function OngletsParametres({ children }: { children: ReactNode }) {
  return <EnOngletContext.Provider value={true}>{children}</EnOngletContext.Provider>;
}

export function EcranOuOnglet(props: PageProps) {
  const enOnglet = useContext(EnOngletContext);
  return enOnglet ? <SectionOnglet {...props} /> : <Page {...props} />;
}

/**
 * Onglet de la page Parametres : section titree en h2, action principale a droite du titre,
 * aide sous le titre. Les onglets propres aux parametres (notifications, preferences,
 * cabinet, plateforme) l'utilisent directement.
 */
export function SectionOnglet({
  titre,
  description,
  action,
  actions,
  infoBulle,
  etat,
  aide,
  children,
  className,
}: Omit<PageProps, 'fil' | 'largeur'>) {
  const idTitre = useId();
  return (
    <section aria-labelledby={idTitre} className={cn('@container', className)}>
      <div className="mb-4 flex flex-col gap-3 @2xl:flex-row @2xl:items-start @2xl:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h2 id={idTitre} className="text-lg font-semibold tracking-tight text-heading">
              {titre}
            </h2>
            {infoBulle && <InfoBulle texte={infoBulle} libelle={`Aide : ${titre}`} />}
            {etat}
          </div>
          {description && <div className="mt-1 max-w-3xl text-sm text-fg-muted">{description}</div>}
        </div>
        {(action || actions) && (
          <div className="flex shrink-0 flex-wrap items-center gap-2 @2xl:justify-end" data-zone="actions-page">
            {actions}
            {action}
          </div>
        )}
      </div>
      {aide && <div className="mb-4">{aide}</div>}
      {children}
    </section>
  );
}
