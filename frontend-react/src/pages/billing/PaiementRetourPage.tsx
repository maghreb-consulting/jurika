import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CheckCircle2, Clock, XCircle } from 'lucide-react';
import { billingService } from '../../services/billing.service';
import { TexteAide } from '../../components/ui/Aide';
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
    <div className="mx-auto max-w-2xl space-y-5 p-6" data-testid="paiement-retour">
      <h1 className="font-heading text-2xl font-semibold text-fg">Suivi du paiement</h1>
      <TexteAide cle="paiement-retour" titre="Pourquoi attendre ?">
        <p>
          Le paiement par carte n’est acquis qu’à la confirmation envoyée par le CMI à JURIKA, pas au retour sur
          cette page. Cette confirmation arrive en général en quelques secondes ; l’écran se met à jour seul.
          Ensuite : votre abonnement est activé et la facture apparaît dans Facturation.
        </p>
      </TexteAide>

      {!id && (
        <p className="rounded-lg border border-border bg-bg-overlay p-4 text-sm text-fg-muted">
          Paiement non identifié. Consultez la liste de vos paiements dans Facturation.
        </p>
      )}

      {id > 0 && statut === 'PENDING' && (
        <div className="flex items-start gap-3 rounded-lg border border-warning/40 bg-warning/10 p-4 text-sm" data-testid="paiement-retour-attente">
          <Clock className="mt-0.5 h-5 w-5 flex-shrink-0 text-warning" aria-hidden="true" />
          <div>
            <p className="font-medium text-fg">En attente de la confirmation du CMI.</p>
            <p className="mt-1 text-fg-muted">
              {issueNavigateur === 'echec'
                ? 'Le CMI a signalé un échec sur la page de paiement ; JURIKA attend sa confirmation pour clore ce paiement.'
                : 'Votre paiement n’est pas encore confirmé par le CMI.'}
              {essais >= ESSAIS_MAX && ' La confirmation tarde : revenez plus tard dans Facturation ; aucun paiement n’est compté deux fois.'}
            </p>
          </div>
        </div>
      )}

      {statut === 'COMPLETED' && paiement && (
        <div className="flex items-start gap-3 rounded-lg border border-success/40 bg-success/10 p-4 text-sm" data-testid="paiement-retour-acquis">
          <CheckCircle2 className="mt-0.5 h-5 w-5 flex-shrink-0 text-success" aria-hidden="true" />
          <div>
            <p className="font-medium text-fg">Paiement acquis, confirmé par le CMI.</p>
            <p className="mt-1 text-fg-muted">
              {paiement.montants ? `${formatMadCents(paiement.montants.ttcCentimes)} TTC. ` : ''}
              {paiement.factureNumero
                ? `Facture ${paiement.factureNumero} disponible dans Facturation.`
                : 'La facture sera disponible dans Facturation dès qu’elle sera émise.'}
            </p>
          </div>
        </div>
      )}

      {(statut === 'FAILED' || statut === 'CANCELLED') && (
        <div className="flex items-start gap-3 rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm" data-testid="paiement-retour-refuse">
          <XCircle className="mt-0.5 h-5 w-5 flex-shrink-0 text-danger" aria-hidden="true" />
          <p className="text-fg">Paiement refusé : aucun montant n’est dû. Vous pouvez réessayer, ou payer par virement, chèque ou espèces.</p>
        </div>
      )}

      {erreur && <p className="text-xs text-fg-subtle">{erreur}</p>}

      <Link to="/app/billing" className="inline-block rounded-lg border border-border px-4 py-2 text-sm font-medium text-fg hover:bg-bg-overlay">
        Aller à la facturation
      </Link>
    </div>
  );
}
