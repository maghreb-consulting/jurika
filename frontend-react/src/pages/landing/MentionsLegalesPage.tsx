import { useEffect } from 'react';
import { RotateCw } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { InfoBulle } from '../../components/ui/Aide';
import { Lien } from '../../components/ui/Lien';
import { Message } from '../../components/ui/Message';
import { SqueletteZone, Skeleton } from '../../components/ui/LoadingSkeleton';
import { adresseContact } from '../legal/contact';
import { VitrineCadre } from './LandingPage';
import { CNDP_STATUT, HEBERGEMENT } from './contenu';
import {
  CLES_MENTIONS,
  LIBELLES_MENTIONS,
  MESSAGE_ERREUR_MENTIONS,
  NON_RENSEIGNE,
  useMentionsLegales,
} from './mentionsLegales';

/**
 * Lot L6b (E10) : page publique des mentions legales (/mentions-legales).
 *
 * L'identite de l'editeur vient des Parametres de l'editeur (route publique, douze cles) :
 * une cle nulle s'affiche "non renseigne", jamais une valeur supposee ; une erreur de
 * chargement est dite, avec "Reessayer". L'hebergeur est celui du contrat V1 (piece C,
 * articles 5 et 6).
 */
export function MentionsLegalesPage() {
  const { resultat, recharger } = useMentionsLegales();
  const contact = adresseContact();

  useEffect(() => {
    document.title = 'Mentions légales — JURIKA';
  }, []);

  return (
    <VitrineCadre surAccueil={false}>
      <div className="mx-auto max-w-3xl px-4 py-12 sm:px-6 lg:py-16">
        <p className="eyebrow">Informations juridiques</p>
        <h1 className="font-heading mt-2 flex items-center gap-2 text-3xl font-semibold tracking-tight">
          Mentions légales
        </h1>
        <p className="mt-4 flex items-start gap-1 text-base leading-relaxed text-fg-muted">
          <span>Identité de l’éditeur de la plateforme JURIKA, telle qu’il l’a enregistrée dans ses paramètres.</span>
          <InfoBulle
            libelle="Que signifie « non renseigné » ?"
            texte="L’éditeur n’a pas encore saisi cette information dans ses paramètres. Elle s’affichera ici dès qu’elle sera enregistrée ; aucune valeur n’est supposée à sa place."
          />
        </p>

        <section aria-labelledby="editeur-titre" className="mt-10">
          <h2 id="editeur-titre" className="text-xl font-semibold">
            Éditeur
          </h2>
          <div className="mt-4" aria-live="polite">
            {resultat.etat === 'chargement' && (
              <SqueletteZone libelle="Chargement des mentions légales">
                <div className="grid gap-px overflow-hidden rounded-xl border border-border">
                  {CLES_MENTIONS.map((c) => (
                    <div key={c} className="grid gap-2 bg-bg-raised px-5 py-3 sm:grid-cols-[16rem_1fr]">
                      <Skeleton className="h-5 w-40" />
                      <Skeleton className="h-5 w-48" />
                    </div>
                  ))}
                </div>
              </SqueletteZone>
            )}
            {resultat.etat === 'erreur' && (
              <Message
                ton="erreur"
                titre="Chargement impossible"
                action={
                  <Button type="button" variant="secondary" size="sm" icon={<RotateCw className="h-4 w-4" />} onClick={recharger}>
                    Réessayer
                  </Button>
                }
              >
                {MESSAGE_ERREUR_MENTIONS}
              </Message>
            )}
            {resultat.etat === 'pret' && (
              <dl className="divide-y divide-border overflow-hidden rounded-xl border border-border bg-bg-raised" data-testid="mentions-legales">
                {CLES_MENTIONS.map((cle) => {
                  const valeur = resultat.mentions[cle];
                  return (
                    <div key={cle} className="grid gap-1 px-5 py-3 sm:grid-cols-[16rem_1fr] sm:gap-4" data-testid={`mention-${cle}`}>
                      <dt className="text-sm text-fg-muted">{LIBELLES_MENTIONS[cle]}</dt>
                      <dd className={valeur ? 'text-sm font-medium text-fg' : 'text-sm italic text-fg-subtle'}>
                        {valeur ?? NON_RENSEIGNE}
                      </dd>
                    </div>
                  );
                })}
              </dl>
            )}
          </div>
        </section>

        <section aria-labelledby="hebergement-titre" className="mt-10">
          <h2 id="hebergement-titre" className="text-xl font-semibold">
            Hébergement
          </h2>
          <p className="mt-3 text-sm leading-relaxed text-fg-muted">{HEBERGEMENT}</p>
          <p className="mt-2 text-sm text-fg-muted">{CNDP_STATUT}.</p>
        </section>

        <section aria-labelledby="documents-titre" className="mt-10">
          <h2 id="documents-titre" className="text-xl font-semibold">
            Documents
          </h2>
          <ul className="mt-3 space-y-2 text-sm text-fg-muted">
            <li>
              <Lien to="/cgu">Conditions générales d’abonnement</Lien> (version 1 du contrat d’abonnement)
            </li>
            <li>Politique de confidentialité : à venir.</li>
            {contact && (
              <li>
                Contact :{' '}
                <Lien href={`mailto:${contact}`} data-testid="mentions-contact">
                  {contact}
                </Lien>
              </li>
            )}
          </ul>
        </section>
      </div>
    </VitrineCadre>
  );
}
