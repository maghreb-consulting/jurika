import { LegalPageLayout } from './LegalPageLayout';
import { TexteContrat } from './ContratTexte';
import { InfoBulle } from '../../components/ui/Aide';
import conditions from './contrat-abonnement-conditions.md?raw';

/**
 * Lot L7 (E3, E9) : conditions generales d'abonnement publiees par la vitrine.
 *
 * Le texte est celui de la version 1 du contrat d'abonnement, validee par la direction le
 * 14 octobre 2026 (~/docs/CONTRAT_ABONNEMENT_JURIKA_V1.md) : pieces B (conditions generales),
 * C (accord de traitement des donnees) et D (conditions de l'essai), reprises telles quelles :
 * contrat-abonnement-conditions.md en est la copie exacte. Les conditions particulieres
 * (piece A) sont propres a chaque cabinet et s'acceptent en ligne a la souscription : elles ne
 * figurent pas ici.
 */
const VERSION_CONDITIONS = '1';

export function CguPage() {
  return (
    <LegalPageLayout
      title="Conditions générales d’abonnement"
      subtitle="Contrat d’abonnement JURIKA : conditions générales, accord de traitement des données personnelles et conditions d’utilisation de l’essai gratuit"
      lastUpdated="14 octobre 2026"
    >
      <div className="not-prose rounded-md border border-border bg-bg-raised p-4 text-sm text-fg-muted">
        <p className="m-0" data-testid="cgu-version">
          Version {VERSION_CONDITIONS} du contrat d’abonnement, en vigueur depuis le 14 octobre 2026. Le contrat se
          compose de quatre pièces : les conditions particulières (votre cabinet, votre forfait, votre rythme de
          paiement, votre prix et vos dates), acceptées en ligne à la souscription, et les trois pièces reproduites
          ci-dessous.{' '}
          <InfoBulle
            libelle="Comment ce contrat est-il accepté ?"
            texte="Le superviseur du cabinet accepte le contrat en ligne à la souscription, après l’avoir lu en entier : les conditions particulières y sont remplies avec les données de votre abonnement. Les conditions de l’essai gratuit (pièce D) s’acceptent à l’inscription à l’essai. Une nouvelle version des conditions générales vous est notifiée au moins 30 jours avant son entrée en vigueur."
          />
        </p>
      </div>
      <TexteContrat source={conditions} />
    </LegalPageLayout>
  );
}
