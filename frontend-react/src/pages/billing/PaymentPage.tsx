import { useEffect, useState } from 'react';
import { demanderDevis } from '../../lib/demandeDevis';
import { useSearchParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { billingService } from '../../services/billing.service';
import { useCurrentUser } from '../../store/authStore';
import { Page } from '../../components/layout/Page';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Lien } from '../../components/ui/Lien';
import { CardSkeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { Message } from '../../components/ui/Message';
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
  const role = useCurrentUser()?.role;
  // Meme regle que le serveur (preparation d'un paiement : superviseur ; super-admin par la hierarchie).
  const superviseur = role === 'SUPERVISEUR' || role === 'SUPER_ADMIN';
  const [params] = useSearchParams();
  const objet = params.get('objet') === 'ECHEANCE' ? 'ECHEANCE' : 'SOUSCRIPTION';
  const planParam = params.get('plan') as CheckoutPlanCode | null;
  const [abonnement, setAbonnement] = useState<AbonnementVue | null>(null);
  const [chargement, setChargement] = useState(true);
  const [noteDevis, setNoteDevis] = useState<string | null>(null);
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

  const titre =
    objet === 'ECHEANCE' ? 'Payer l’échéance' : abonnement?.statut === 'ARCHIVE' ? 'Souscrire de nouveau' : 'Souscrire un forfait';
  // L6b (E8) : fil d'Ariane explicite (ecran hors menu) : Facturation > paiement.
  const fil = [
    { libelle: 'Accueil', to: '/dashboard' },
    { libelle: 'Facturation', to: '/app/billing' },
    { libelle: objet === 'ECHEANCE' ? 'Payer l’échéance' : 'Souscrire' },
  ];

  if (chargement) {
    return (
      <Page titre={objet === 'ECHEANCE' ? 'Payer l’échéance' : 'Souscrire un forfait'} fil={fil}>
        <SqueletteZone libelle="Chargement de l’abonnement">
          <div className="space-y-6">
            <CardSkeleton className="h-40" />
            <CardSkeleton className="h-72" />
          </div>
        </SqueletteZone>
      </Page>
    );
  }

  // Le paiement est une operation du superviseur (le serveur la refuse a l'employe).
  if (!superviseur) {
    return (
      <Page titre={titre} fil={fil}>
        <div data-testid="payment-page">
          <Message
            ton="info"
            titre="Réservé au superviseur du cabinet"
            action={
              <Lien to="/app/billing" commeBouton={{ variant: 'secondary', size: 'sm' }}>
                Retour à la facturation
              </Lien>
            }
          >
            Seul le superviseur règle l’abonnement du cabinet. Prévenez-le si une échéance est à payer.
          </Message>
        </div>
      </Page>
    );
  }

  const e = abonnement?.prochaineEcheance ?? null;

  return (
    <Page
      titre={titre}
      fil={fil}
      actions={
        <Lien to="/app/billing" commeBouton={{ variant: 'secondary' }}>
          <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Retour à la facturation
        </Lien>
      }
      aide={
        <TexteAide cle="paiement-cabinet" titre="Comment payer ?">
          <p>
            {objet === 'SOUSCRIPTION' ? 'Choisissez le forfait et le rythme, puis le mode de paiement. ' : ''}
            Carte : vous êtes redirigé vers la page sécurisée du CMI ; le paiement est acquis à la confirmation du CMI.
            Virement, chèque ou espèces : les instructions s’affichent ; JURIKA enregistre la réception, puis votre
            abonnement est activé ou prolongé et la facture est émise.
          </p>
        </TexteAide>
      }
    >
      <div className="space-y-6" data-testid="payment-page">
        <ErreurFacturationBloc erreur={erreur} />

        {preparation ? (
          <SuitePaiement preparation={preparation} />
        ) : (
          <>
            {objet === 'SOUSCRIPTION' && (
              <section aria-labelledby="titre-forfait-rythme" className="space-y-4">
                <h2 id="titre-forfait-rythme" className="text-base font-semibold text-heading">
                  Forfait et rythme
                </h2>
                {noteDevis && <Message ton="info">{noteDevis}</Message>}
                <PlanPicker
                  period={rythme}
                  onPeriodChange={setRythme}
                  forfaitActuel={forfait}
                  testIdPrefix="payment-plan"
                  libelleAction={(t) => (t.quoteOnly ? 'Demander un devis' : forfait === t.code ? 'Forfait choisi' : `Choisir ${t.label}`)}
                  onChoisir={(t) => {
                    if (t.quoteOnly) {
                      setNoteDevis(demanderDevis());
                      return;
                    }
                    setForfait(t.code as CheckoutPlanCode);
                  }}
                />
              </section>
            )}
            {objet === 'ECHEANCE' && (
              <Card data-testid="payment-echeance" title="Échéance à régler">
                <div className="text-sm">
                  {e ? (
                    <>
                      <p className="font-medium text-fg">
                        {e.forfaitLibelle}
                        {e.rythme ? `, ${LIBELLE_RYTHME[e.rythme].toLowerCase()}` : ''} : période du {formatDateIso(e.debut)} au{' '}
                        {formatFinIncluse(e.fin)}
                      </p>
                      {e.montants ? (
                        <p className="mt-1 text-fg">
                          Montant : <strong>{formatMadCents(e.montants.ttcCentimes)} TTC</strong> ({formatMadCents(e.montants.htCentimes)} HT)
                        </p>
                      ) : (
                        <p className="mt-1 text-warning">{e.motif}</p>
                      )}
                    </>
                  ) : (
                    <p className="text-fg-muted">Aucune échéance à régler : votre cabinet n’a pas d’abonnement actif.</p>
                  )}
                </div>
              </Card>
            )}
            <Card className="p-5">
              <ChoixModePaiement
                valeur={mode}
                onChange={setMode}
                carteDisponible={abonnement?.carteDisponible ?? false}
                carteMotif={abonnement?.carteIndisponibleMotif}
              />
              <div className="mt-5 flex justify-end">
                <Button
                  type="button"
                  onClick={() => void payer()}
                  loading={envoi}
                  disabled={!mode || (objet === 'ECHEANCE' && !e?.montants)}
                  data-testid="payment-submit"
                >
                  {mode === 'CARD' ? 'Payer par carte' : 'Confirmer le mode de paiement'}
                </Button>
              </div>
            </Card>
          </>
        )}
      </div>
    </Page>
  );
}
