import { useEffect, useState } from 'react';
import { ArrowLeft } from 'lucide-react';
import { Button } from '../ui/Button';
import { billingService } from '../../services/billing.service';
import { ChoixModePaiement } from '../billing/ChoixModePaiement';
import { SuitePaiement } from '../billing/SuitePaiement';
import { ErreurFacturationBloc } from '../billing/ErreurFacturation';
import { TexteAide } from '../ui/Aide';
import { GRILLE_CDC } from '../../lib/forfaits';
import { libelleForfait } from '../../lib/adminLabels';
import {
  formatMad,
  lireErreurFacturation,
  type BillingPeriod,
  type CheckoutPlanCode,
  type ErreurFacturation,
  type PaymentMethod,
  type PreparationPaiementVue,
} from '../../types/billing';

/**
 * Etape de paiement de l'inscription payante (lot L7, E5). Le cabinet est cree, son superviseur
 * existe, les identifiants ne sont pas encore envoyes : ils partent a l'activation, quand le paiement
 * est acquis (carte : confirmation serveur du CMI ; virement, cheque, especes : reception enregistree
 * par JURIKA). Le jeton transitoire de l'inscription autorise l'appel au service de facturation.
 */
export interface SignupPaymentDraft {
  workspaceId: string;
  workspaceCode: string;
  planCode: CheckoutPlanCode;
  billingPeriod: BillingPeriod;
  contactEmail: string;
  workspaceName: string;
  /** Jeton transitoire renvoye par /public/signup/cabinet. */
  accessToken?: string;
}

export interface SignupStepPaymentProps {
  draft: SignupPaymentDraft;
  onBack: () => void;
  onPaid: (paymentId: number, status: string) => void;
}

export function SignupStepPayment({ draft, onBack, onPaid }: SignupStepPaymentProps) {
  const [mode, setMode] = useState<PaymentMethod | null>(null);
  const [carte, setCarte] = useState<{ dispo: boolean; motif: string | null }>({ dispo: false, motif: null });
  const [envoi, setEnvoi] = useState(false);
  const [erreur, setErreur] = useState<ErreurFacturation | null>(null);
  const [preparation, setPreparation] = useState<PreparationPaiementVue | null>(null);

  // Le jeton transitoire est pose avant le premier appel (l'intercepteur le lit).
  useEffect(() => {
    if (draft.accessToken) {
      try { localStorage.setItem('jurika_access_token', draft.accessToken); } catch { /* stockage indisponible */ }
    }
    let annule = false;
    billingService.getAbonnement()
      .then((a) => { if (!annule) setCarte({ dispo: a.carteDisponible, motif: a.carteIndisponibleMotif }); })
      .catch(() => { if (!annule) setCarte({ dispo: false, motif: 'État du paiement par carte inconnu : choisissez le virement, le chèque ou les espèces.' }); });
    return () => { annule = true; };
  }, [draft.accessToken]);

  const tier = GRILLE_CDC.find((t) => t.code === draft.planCode);
  const prixHt = draft.billingPeriod === 'yearly' ? tier?.yearly?.priceMad : tier?.monthly?.priceMad;

  async function payer() {
    if (!mode) {
      setErreur({ code: null, message: 'Choisissez un mode de paiement.', elements: [] });
      return;
    }
    setErreur(null);
    setEnvoi(true);
    try {
      const res = await billingService.preparerPaiement({
        objet: 'SOUSCRIPTION',
        forfait: draft.planCode,
        rythme: draft.billingPeriod,
        mode,
        contactEmail: draft.contactEmail,
        inscription: true,
      });
      setPreparation(res);
    } catch (err) {
      setErreur(lireErreurFacturation(err, 'Le paiement n’a pas pu être préparé. Réessayez.'));
    } finally {
      setEnvoi(false);
    }
  }

  if (preparation) {
    return (
      <div className="py-4" data-testid="signup-payment-suite">
        <div className="mx-auto max-w-2xl space-y-4">
          <SuitePaiement preparation={preparation} />
          {!preparation.cmi && (
            <>
              <p className="text-xs text-fg-subtle">
                Dès que JURIKA aura enregistré votre paiement, votre espace sera activé et les identifiants
                (code du cabinet et mot de passe temporaire) seront envoyés par courriel au superviseur.
              </p>
              <div className="flex justify-end">
                <Button type="button" data-testid="signup-payment-pending-continue"
                  onClick={() => onPaid(preparation.paiement.id, preparation.paiement.statut)}>
                  J’ai compris
                </Button>
              </div>
            </>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="py-4" data-testid="signup-step-payment">
      <div className="mx-auto max-w-3xl space-y-5">
        <header>
          <h2 className="text-2xl font-semibold tracking-tight text-heading">Paiement</h2>
          <p className="mt-2 text-sm text-fg-muted">
            Forfait <strong>{libelleForfait(draft.planCode)}</strong>, {draft.billingPeriod === 'yearly' ? 'paiement annuel' : 'paiement mensuel'}
            {prixHt !== undefined && <> : <strong>{formatMad(prixHt)} HT</strong> {draft.billingPeriod === 'yearly' ? 'par an' : 'par mois'}</>}
            {' '}(TVA ajoutée au taux en vigueur ; le montant TTC est indiqué à l’étape suivante).
          </p>
        </header>
        <TexteAide cle="inscription-paiement" titre="Comment régler l’abonnement ?">
          <p>
            Carte bancaire : vous êtes redirigé vers la page sécurisée du CMI ; l’abonnement est activé à la
            confirmation du CMI. Virement, chèque ou espèces : suivez les instructions affichées ; JURIKA
            enregistre la réception puis active l’abonnement. Vos identifiants arrivent par courriel après l’activation.
          </p>
        </TexteAide>
        <ErreurFacturationBloc erreur={erreur} />
        <section className="rounded-xl border border-border bg-bg-raised p-5 shadow-card">
          <ChoixModePaiement valeur={mode} onChange={setMode} carteDisponible={carte.dispo} carteMotif={carte.motif} />
          <div className="mt-5 flex flex-wrap justify-between gap-3">
            <Button type="button" variant="secondary" onClick={onBack} disabled={envoi} icon={<ArrowLeft />}>
              Étape précédente
            </Button>
            <Button type="button" onClick={() => void payer()} disabled={!mode} loading={envoi}
              data-testid="signup-payment-submit">
              {mode === 'CARD' ? 'Payer par carte' : 'Confirmer le mode de paiement'}
            </Button>
          </div>
        </section>
      </div>
    </div>
  );
}
