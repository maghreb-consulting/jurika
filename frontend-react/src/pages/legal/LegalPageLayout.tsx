import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
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
  return (
    <div className="min-h-screen bg-bg text-fg">
      <header className="border-b border-border bg-bg-raised">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-6 py-4">
          <Link to="/" className="font-heading text-xl font-semibold text-fg">
            JURIKA
          </Link>
          <nav className="text-sm text-fg-muted">
            <Link to="/cgu" className="mr-4 hover:text-fg">Conditions générales</Link>
            <Link to="/confidentialite" className="mr-4 hover:text-fg">Confidentialité</Link>
            <Link to="/login" className="hover:text-fg">Connexion</Link>
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-3xl px-6 py-12">
        <p className="text-xs uppercase tracking-widest text-fg-muted">Informations juridiques</p>
        <h1 className="font-heading mt-2 text-4xl font-semibold text-fg">{title}</h1>
        {subtitle && <p className="mt-3 text-base text-fg-muted">{subtitle}</p>}
        <p className="mt-4 text-xs text-fg-muted">
          Dernière mise à jour : <time>{lastUpdated}</time>
        </p>

        <article className="prose prose-sm mt-10 max-w-none text-fg-muted [&_h2]:font-heading [&_h2]:text-2xl [&_h2]:font-semibold [&_h2]:text-fg [&_h2]:mt-10 [&_h2]:mb-3 [&_h3]:font-semibold [&_h3]:text-fg [&_h3]:mt-6 [&_h3]:mb-2 [&_p]:my-3 [&_ul]:my-3 [&_ul]:list-disc [&_ul]:pl-6 [&_li]:my-1 [&_strong]:text-fg">
          {children}
        </article>

        <footer className="mt-16 border-t border-border pt-6 text-xs text-fg-muted">
          <p>
            JURIKA est édité par <strong className="text-fg">Maghreb Consulting</strong>,
            Casablanca (Maroc).
            {contact && (
              <>
                {' '}Pour toute question relative à ces documents :{' '}
                <a href={`mailto:${contact}`} className="underline" data-testid="legal-contact">{contact}</a>.
              </>
            )}
          </p>
        </footer>
      </main>
    </div>
  );
}
