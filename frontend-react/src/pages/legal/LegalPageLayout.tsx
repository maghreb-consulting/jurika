import { useEffect, type ReactNode } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { BrandLogo } from '../../components/ui/BrandLogo';
import { classesBouton } from '../../components/ui/boutonClasses';
import { adresseContact } from './contact';

/**
 * Mise en page commune aux pages legales publiques (conditions generales d'abonnement,
 * politique de confidentialite), accessibles sans authentification.
 *
 * Lot L7 (E3) : l'adresse de contact vient de VITE_CONTACT_EMAIL (fixee au build) ;
 * sans valeur, aucune adresse n'est affichee.
 */
export function LegalPageLayout({
  title,
  subtitle,
  lastUpdated,
  children,
}: {
  title: string;
  subtitle?: string;
  lastUpdated: string;
  children: ReactNode;
}) {
  const contact = adresseContact();

  useEffect(() => {
    const avant = document.title;
    document.title = `${title} - JURIKA`;
    return () => {
      document.title = avant;
    };
  }, [title]);

  const lienNav =
    'rounded-sm px-1 py-0.5 text-fg-muted underline-offset-2 hover:text-fg hover:underline aria-[current=page]:font-semibold aria-[current=page]:text-fg';
  return (
    <div className="min-h-dvh bg-bg text-fg">
      <header className="border-b border-border bg-bg-raised">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-3 px-4 py-3 sm:px-6">
          <Link to="/" className="inline-flex shrink-0 rounded-md" aria-label="JURIKA, retour à l’accueil">
            <BrandLogo variant="full" size={36} />
          </Link>
          <nav aria-label="Pages légales" className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
            <NavLink to="/cgu" className={lienNav}>
              Conditions générales
            </NavLink>
            <NavLink to="/confidentialite" className={lienNav}>
              Confidentialité
            </NavLink>
            <Link to="/login" className={classesBouton('secondary', 'sm')}>
              Connexion
            </Link>
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-3xl px-4 py-10 sm:px-6 sm:py-12">
        <p className="text-xs font-medium uppercase tracking-widest text-fg-muted">Informations juridiques</p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight text-heading sm:text-4xl">{title}</h1>
        {subtitle && <p className="mt-3 text-base text-fg-muted">{subtitle}</p>}
        <p className="mt-4 text-xs text-fg-muted">
          Dernière mise à jour : <time>{lastUpdated}</time>
        </p>

        <article className="mt-10 max-w-none text-sm leading-relaxed text-fg [&_h2]:mb-3 [&_h2]:mt-10 [&_h2]:border-b [&_h2]:border-border [&_h2]:pb-2 [&_h2]:text-2xl [&_h2]:font-semibold [&_h2]:text-heading [&_h3]:mb-2 [&_h3]:mt-6 [&_h3]:font-semibold [&_h3]:text-heading [&_li]:my-1 [&_p]:my-3 [&_strong]:text-fg [&_ul]:my-3 [&_ul]:list-disc [&_ul]:pl-6">
          {children}
        </article>

        <footer className="mt-16 border-t border-border pt-6 text-xs text-fg-muted">
          <p>
            JURIKA est édité par <strong className="text-fg">Maghreb Consulting</strong>,
            Casablanca (Maroc).
            {contact && (
              <>
                {' '}Pour toute question relative à ces documents :{' '}
                <a href={`mailto:${contact}`} className="text-accent underline underline-offset-2 hover:text-accent-hover" data-testid="legal-contact">{contact}</a>.
              </>
            )}
          </p>
        </footer>
      </main>
    </div>
  );
}
