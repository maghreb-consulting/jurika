import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { billingService } from '../../services/billing.service';
import { PageAccueil, TitreAccueil } from '../../components/auth/PageAccueil';
import { TexteAide } from '../../components/ui/Aide';
import { Lien } from '../../components/ui/Lien';
import { Message } from '../../components/ui/Message';
import { formatMadCents, type PaiementVue } from '../../types/billing';

/** Intervalle d'interrogation du paiement et nombre maximal d'essais (2 minutes). */
const INTERVALLE_MS = 3000;
const ESSAIS_MAX = 40;

/**
 * Lot L7 (E5), RG-PAY-05 : retour du navigateur apres la page du CMI. Ce retour NE CONFIRME
 * JAMAIS le paiement : l'ecran interroge l'etat du paiement aupres de JURIKA, qui ne le declare
 * acquis qu'a la confirmation serveur signee du CMI.
 */
export function PaiementRetourPage() {
  const [params] = useSearchParams();
  const id = Number(params.get('paiement'));
  const issueNavigateur = params.get('issue');
  const [paiement, setPaiement] = useState<PaiementVue | null>(null);
  const [essais, setEssais] = useState(0);
  const [erreur, setErreur] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return undefined;
    if (paiement && paiement.statut !== 'PENDING') return undefined;
    if (essais >= ESSAIS_MAX) return undefined;
    const t = setTimeout(() => {
      billingService.getPaiement(id)
        .then((p) => { setPaiement(p); setErreur(null); })
        .catch(() => setErreur('L’état du paiement n’a pas pu être lu ; nouvel essai en cours.'))
        .finally(() => setEssais((n) => n + 1));
    }, essais === 0 ? 0 : INTERVALLE_MS);
    return () => clearTimeout(t);
  }, [id, essais, paiement]);

  const statut = paiement?.statut ?? 'PENDING';

  return (
    <PageAccueil titre="Suivi du paiement" largeur="normale">
      <div className="space-y-5" data-testid="paiement-retour">
        <TitreAccueil titre="Suivi du paiement" centre={false} />
        <TexteAide cle="paiement-retour" titre="Pourquoi attendre ?">
          <p>
            Le paiement par carte n’est acquis qu’à la confirmation envoyée par le CMI à JURIKA, pas au retour sur
            cette page. Cette confirmation arrive en général en quelques secondes ; l’écran se met à jour seul.
            Ensuite : votre abonnement est activé et la facture apparaît dans Facturation.
          </p>
        </TexteAide>

        {!id && <Message ton="info">Paiement non identifié. Consultez la liste de vos paiements dans Facturation.</Message>}

        {id > 0 && statut === 'PENDING' && (
          <div data-testid="paiement-retour-attente">
            <Message ton="attention" titre="En attente de la confirmation du CMI.">
              {issueNavigateur === 'echec'
                ? 'Le CMI a signalé un échec sur la page de paiement ; JURIKA attend sa confirmation pour clore ce paiement.'
                : 'Votre paiement n’est pas encore confirmé par le CMI.'}
              {essais >= ESSAIS_MAX && ' La confirmation tarde : revenez plus tard dans Facturation ; aucun paiement n’est compté deux fois.'}
            </Message>
          </div>
        )}

        {statut === 'COMPLETED' && paiement && (
          <div data-testid="paiement-retour-acquis">
            <Message ton="succes" titre="Paiement acquis, confirmé par le CMI.">
              {paiement.montants ? `${formatMadCents(paiement.montants.ttcCentimes)} TTC. ` : ''}
              {paiement.factureNumero
                ? `Facture ${paiement.factureNumero} disponible dans Facturation.`
                : 'La facture sera disponible dans Facturation dès qu’elle sera émise.'}
            </Message>
          </div>
        )}

        {(statut === 'FAILED' || statut === 'CANCELLED') && (
          <div data-testid="paiement-retour-refuse">
            <Message ton="erreur" titre="Paiement refusé">
              Aucun montant n’est dû. Vous pouvez réessayer, ou payer par virement, chèque ou espèces.
            </Message>
          </div>
        )}

        {erreur && <p className="text-xs text-fg-muted">{erreur}</p>}

        <Lien to="/app/billing" commeBouton={{ variant: statut === 'PENDING' && id > 0 ? 'secondary' : 'primary' }}>
          Aller à la facturation
        </Lien>
      </div>
    </PageAccueil>
  );
}
