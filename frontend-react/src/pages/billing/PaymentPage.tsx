import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ArrowLeft, Loader2 } from 'lucide-react';
import { billingService } from '../../services/billing.service';
import { PlanPicker } from '../../components/billing/PlanPicker';
import { ChoixModePaiement } from '../../components/billing/ChoixModePaiement';
import { SuitePaiement } from '../../components/billing/SuitePaiement';
import { ErreurFacturationBloc } from '../../components/billing/ErreurFacturation';
import { TexteAide } from '../../components/ui/Aide';
import { CODES_SOUSCRIPTIBLES } from '../../lib/forfaits';
import {
  formatDateIso,
  formatFinIncluse,
  formatMadCents,
  lireErreurFacturation,
  LIBELLE_RYTHME,
  type AbonnementVue,
  type BillingPeriod,
  type CheckoutPlanCode,
  type ErreurFacturation,
  type PaymentMethod,
  type PreparationPaiementVue,
} from '../../types/billing';

/**
 * Lot L7 (E5), RG-PAY-01 a RG-PAY-04 : paiement du cabinet. Souscription (premiere ou reprise d'un
 * cabinet archive : forfait et rythme a choisir) ou echeance (montant calcule par le serveur). Carte :
 * page hebergee du CMI ; virement, cheque, especes : instructions, puis JURIKA enregistre la reception.
 */
export function PaymentPage() {
  const [params] = useSearchParams();
  const objet = params.get('objet') === 'ECHEANCE' ? 'ECHEANCE' : 'SOUSCRIPTION';
  const planParam = params.get('plan') as CheckoutPlanCode | null;
  const [abonnement, setAbonnement] = useState<AbonnementVue | null>(null);
  const [chargement, setChargement] = useState(true);
  const [forfait, setForfait] = useState<CheckoutPlanCode | null>(
    planParam && CODES_SOUSCRIPTIBLES.includes(planParam) ? planParam : null);
  const [rythme, setRythme] = useState<BillingPeriod>(params.get('period') === 'monthly' ? 'monthly' : 'yearly');
  const [mode, setMode] = useState<PaymentMethod | null>(null);
  const [envoi, setEnvoi] = useState(false);
  const [erreur, setErreur] = useState<ErreurFacturation | null>(null);
  const [preparation, setPreparation] = useState<PreparationPaiementVue | null>(null);

  useEffect(() => {
    billingService.getAbonnement()
      .then(setAbonnement)
      .catch((err) => setErreur(lireErreurFacturation(err, 'L’abonnement n’a pas pu être chargé.')))
      .finally(() => setChargement(false));
  }, []);

  async function payer() {
    if (!mode) {
      setErreur({ code: null, message: 'Choisissez un mode de paiement.', elements: [] });
      return;
    }
    if (objet === 'SOUSCRIPTION' && !forfait) {
      setErreur({ code: null, message: 'Choisissez un forfait.', elements: [] });
      return;
    }
    setErreur(null);
    setEnvoi(true);
    try {
      setPreparation(await billingService.preparerPaiement(objet === 'ECHEANCE'
        ? { objet, mode }
        : { objet, forfait: forfait ?? undefined, rythme, mode }));
    } catch (err) {
      setErreur(lireErreurFacturation(err, 'Le paiement n’a pas pu être préparé. Réessayez.'));
    } finally {
      setEnvoi(false);
    }
  }

  if (chargement) {
    return <div className="flex h-64 items-center justify-center"><Loader2 className="h-6 w-6 animate-spin text-accent" aria-label="Chargement" /></div>;
  }

  const e = abonnement?.prochaineEcheance ?? null;

  return (
    <div className="mx-auto max-w-5xl space-y-6" data-testid="payment-page">
      <Link to="/app/billing" className="inline-flex items-center gap-1 text-sm text-fg-muted hover:text-fg">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Retour à la facturation
      </Link>
      <header>
        <h1 className="font-heading text-2xl font-semibold text-fg">
          {objet === 'ECHEANCE' ? 'Payer l’échéance' : abonnement?.statut === 'ARCHIVE' ? 'Souscrire de nouveau' : 'Souscrire un forfait'}
        </h1>
      </header>
      <TexteAide cle="paiement-cabinet" titre="Comment payer ?">
        <p>
          {objet === 'SOUSCRIPTION' ? 'Choisissez le forfait et le rythme, puis le mode de paiement. ' : ''}
          Carte : vous êtes redirigé vers la page sécurisée du CMI ; le paiement est acquis à la confirmation du CMI.
          Virement, chèque ou espèces : les instructions s’affichent ; JURIKA enregistre la réception, puis votre
          abonnement est activé ou prolongé et la facture est émise.
        </p>
      </TexteAide>

      <ErreurFacturationBloc erreur={erreur} />

      {preparation ? (
        <SuitePaiement preparation={preparation} />
      ) : (
        <>
          {objet === 'SOUSCRIPTION' && (
            <section className="rounded-2xl bg-bg-raised p-5 shadow-sm">
              <h2 className="mb-4 text-base font-semibold text-fg">Forfait et rythme</h2>
              <PlanPicker period={rythme} onPeriodChange={setRythme} forfaitActuel={forfait}
                testIdPrefix="payment-plan"
                libelleAction={(t) => (t.quoteOnly ? 'Demander un devis' : forfait === t.code ? 'Forfait choisi' : `Choisir ${t.label}`)}
                onChoisir={(t) => {
                  if (t.quoteOnly) {
                    window.location.href = 'mailto:contact@jurika.ai?subject=Demande%20de%20devis%20(forfait%20Sur%20Devis)';
                    return;
                  }
                  setForfait(t.code as CheckoutPlanCode);
                }} />
            </section>
          )}
          {objet === 'ECHEANCE' && (
            <section className="rounded-2xl bg-bg-raised p-5 text-sm shadow-sm" data-testid="payment-echeance">
              {e ? (
                <>
                  <p className="font-medium text-fg">
                    {e.forfaitLibelle}{e.rythme ? `, ${LIBELLE_RYTHME[e.rythme].toLowerCase()}` : ''} : période du{' '}
                    {formatDateIso(e.debut)} au {formatFinIncluse(e.fin)}
                  </p>
                  {e.montants
                    ? <p className="mt-1">Montant : <strong>{formatMadCents(e.montants.ttcCentimes)} TTC</strong> ({formatMadCents(e.montants.htCentimes)} HT)</p>
                    : <p className="mt-1 text-warning">{e.motif}</p>}
                </>
              ) : (
                <p className="text-fg-muted">Aucune échéance à régler : votre cabinet n’a pas d’abonnement actif.</p>
              )}
            </section>
          )}
          <section className="rounded-2xl bg-bg-raised p-5 shadow-sm">
            <ChoixModePaiement valeur={mode} onChange={setMode}
              carteDisponible={abonnement?.carteDisponible ?? false} carteMotif={abonnement?.carteIndisponibleMotif} />
            <div className="mt-5 flex justify-end">
              <button type="button" onClick={() => void payer()} disabled={envoi || !mode || (objet === 'ECHEANCE' && !e?.montants)}
                data-testid="payment-submit"
                className="inline-flex items-center gap-2 rounded-lg bg-accent px-5 py-2 text-sm font-bold text-bg hover:bg-accent-hover disabled:opacity-50">
                {envoi && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
                {mode === 'CARD' ? 'Payer par carte' : 'Confirmer le mode de paiement'}
              </button>
            </div>
          </section>
        </>
      )}
    </div>
  );
}
