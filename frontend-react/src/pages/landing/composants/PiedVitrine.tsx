import { Link } from 'react-router-dom';
import { RotateCw } from 'lucide-react';
import { BrandLogo } from '../../../components/ui/BrandLogo';
import { Skeleton } from '../../../components/ui/LoadingSkeleton';
import { adresseContact } from '../../legal/contact';
import { LiensSections } from './LiensSections';
import {
  MESSAGE_ERREUR_MENTIONS,
  useMentionsLegales,
  valeurMention,
  type MentionsLegales,
} from '../mentionsLegales';

/**
 * Lot L6b (E10) : pied de page de la vitrine.
 *
 * - Informations juridiques : conditions generales d'abonnement (/cgu, contrat V1) ;
 *   politique de confidentialite "a venir" SANS lien (sa version complete est au lot L8) ;
 *   mentions legales (/mentions-legales).
 * - Rappel des mentions legales de l'editeur, lues dans ses Parametres (route publique) :
 *   une valeur absente s'affiche "non renseigne" ; une erreur de chargement est dite.
 * - Adresse de contact : VITE_CONTACT_EMAIL (fixee au build) ; sans valeur, aucune adresse.
 */
function RappelMentions({ mentions }: { mentions: MentionsLegales }) {
  const v = (cle: Parameters<typeof valeurMention>[1]) => valeurMention(mentions, cle);
  return (
    <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2" data-testid="pied-mentions">
      <div>
        <dt className="text-nav-fg">Éditeur</dt>
        <dd className="text-nav-fg-hi">
          {v('raisonSociale')}, {v('formeJuridique')}
        </dd>
      </div>
      <div>
        <dt className="text-nav-fg">Siège social</dt>
        <dd className="text-nav-fg-hi">
          {v('siegeSocial')}, {v('ville')}
        </dd>
      </div>
      <div>
        <dt className="text-nav-fg">Registre du commerce</dt>
        <dd className="text-nav-fg-hi">
          {v('rcNumero')} ({v('rcTribunal')})
        </dd>
      </div>
      <div>
        <dt className="text-nav-fg">ICE</dt>
        <dd className="text-nav-fg-hi">{v('ice')}</dd>
      </div>
    </dl>
  );
}

const CLASSE_LIEN_PIED =
  'rounded-sm text-sm text-nav-fg-hi underline decoration-1 underline-offset-2 hover:decoration-2';

export function PiedVitrine({ surAccueil }: { surAccueil: boolean }) {
  const contact = adresseContact();
  const { resultat, recharger } = useMentionsLegales();

  return (
    <footer className="zone-marine border-t border-nav-border bg-nav-bg text-nav-fg">
      <div className="mx-auto grid max-w-6xl gap-10 px-4 py-14 sm:px-6 lg:grid-cols-[1.2fr_1fr_1fr]">
        <div>
          <span className="block h-9 w-[142px]">
            <BrandLogo variant="full" tone="navy" size={36} />
          </span>
          <p className="mt-4 max-w-sm text-sm leading-relaxed">
            Plateforme de gestion juridique des sociétés, pour les cabinets qui accompagnent les entreprises au Maroc.
          </p>
          {contact && (
            <p className="mt-4 text-sm">
              <a className={CLASSE_LIEN_PIED} href={`mailto:${contact}`} data-testid="footer-contact">
                {contact}
              </a>
            </p>
          )}
        </div>

        <nav aria-label="Plan de la vitrine">
          <h2 className="text-sm font-semibold text-nav-fg-hi">La plateforme</h2>
          <div className="mt-3 flex flex-col items-start gap-2">
            <LiensSections surAccueil={surAccueil} classeLien={CLASSE_LIEN_PIED} />
          </div>
        </nav>

        <div>
          <h2 className="text-sm font-semibold text-nav-fg-hi">Informations juridiques</h2>
          <ul className="mt-3 flex flex-col items-start gap-2 text-sm">
            <li>
              <Link to="/cgu" className={CLASSE_LIEN_PIED} data-testid="pied-cgu">
                Conditions générales d’abonnement
              </Link>
            </li>
            <li data-testid="pied-confidentialite">
              Politique de confidentialité <span className="text-nav-fg">(à venir)</span>
            </li>
            <li>
              <Link to="/mentions-legales" className={CLASSE_LIEN_PIED} data-testid="pied-mentions-lien">
                Mentions légales
              </Link>
            </li>
          </ul>
        </div>
      </div>

      <div className="border-t border-nav-border">
        <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
          <h2 className="text-sm font-semibold text-nav-fg-hi">Mentions légales de l’éditeur</h2>
          <div className="mt-3" aria-live="polite">
            {resultat.etat === 'chargement' && (
              <div role="status" className="grid gap-x-6 gap-y-2 sm:grid-cols-2" aria-label="Chargement des mentions légales">
                <Skeleton className="h-11 opacity-40" />
                <Skeleton className="h-11 opacity-40" />
                <Skeleton className="h-11 opacity-40" />
                <Skeleton className="h-11 opacity-40" />
              </div>
            )}
            {resultat.etat === 'erreur' && (
              <div className="flex flex-wrap items-center gap-3 text-sm" role="alert" data-testid="pied-mentions-erreur">
                <p className="text-nav-fg-hi">{MESSAGE_ERREUR_MENTIONS}</p>
                <button
                  type="button"
                  onClick={recharger}
                  className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-nav-border px-3 text-sm font-medium text-nav-fg-hi hover:bg-nav-bg-hover"
                >
                  <RotateCw className="h-4 w-4" aria-hidden="true" />
                  Réessayer
                </button>
              </div>
            )}
            {resultat.etat === 'pret' && <RappelMentions mentions={resultat.mentions} />}
          </div>
        </div>
      </div>

      <div className="border-t border-nav-border">
        <p className="mx-auto max-w-6xl px-4 py-5 text-xs sm:px-6">
          © {new Date().getFullYear()} JURIKA. Tous droits réservés.
        </p>
      </div>
    </footer>
  );
}
