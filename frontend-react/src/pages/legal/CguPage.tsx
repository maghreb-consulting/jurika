import { LegalPageLayout } from './LegalPageLayout';
import { TexteContrat } from './ContratTexte';
import { InfoBulle } from '../../components/ui/Aide';
import conditions from './contrat-abonnement-conditions.md?raw';

/**
 * Lot L7 (E3) : conditions generales d'abonnement publiees par la vitrine.
 *
 * Le texte est celui du contrat d'abonnement valide (CDC 4.8), pieces B (conditions
 * generales), C (accord de traitement des donnees) et D (conditions de l'essai), repris
 * tel quel : contrat-abonnement-conditions.md en est la copie exacte. Les mentions entre
 * crochets restent a completer par la direction : elles sont affichees et signalees,
 * jamais remplacees par une valeur. Les conditions particulieres (piece A) sont propres a
 * chaque cabinet et s'acceptent en ligne a la souscription : elles ne figurent pas ici.
 */
export function CguPage() {
  return (
    <LegalPageLayout
      title="Conditions générales d’abonnement"
      subtitle="Contrat d’abonnement JURIKA : conditions générales, accord de traitement des données personnelles et conditions d’utilisation de l’essai gratuit"
      lastUpdated="12 octobre 2026"
    >
      <div className="not-prose rounded-md border border-border bg-bg-raised p-4 text-sm text-fg-muted">
        <p className="m-0">
          Le contrat d’abonnement JURIKA se compose de quatre pièces : les conditions particulières
          (votre cabinet, votre forfait, votre rythme de paiement, votre prix et vos dates), acceptées
          en ligne à la souscription, et les trois pièces reproduites ci-dessous.{' '}
          <InfoBulle
            libelle="Que signifient les mentions surlignées ?"
            texte="Les mentions entre crochets, surlignées, sont encore à compléter par la direction de Maghreb Consulting."
          />
        </p>
      </div>
      <TexteContrat source={conditions} />
    </LegalPageLayout>
  );
}
