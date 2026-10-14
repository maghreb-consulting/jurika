import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, Loader2 } from 'lucide-react';
import { billingService } from '../../services/billing.service';
import { PlanPicker } from '../../components/billing/PlanPicker';
import { ChoixModePaiement } from '../../components/billing/ChoixModePaiement';
import { SuitePaiement } from '../../components/billing/SuitePaiement';
import { ErreurFacturationBloc } from '../../components/billing/ErreurFacturation';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { oublierForfait } from '../../lib/useForfait';
import {
  formatDateIso,
  formatMadCents,
  lireErreurFacturation,
  LIBELLE_RYTHME,
  type AbonnementVue,
  type ApercuChangementVue,
  type BillingPeriod,
  type ErreurFacturation,
  type PaymentMethod,
  type PlanCode,
  type PreparationPaiementVue,
  type PricingTier,
} from '../../types/billing';

const LIBELLE_EFFET: Record<string, string> = {
  APRES_PAIEMENT: 'Effectif dès la confirmation du paiement de la différence',
  IMMEDIAT: 'Effectif immédiatement',
  PROCHAINE_ECHEANCE: 'Effectif à la prochaine échéance',
};

/**
 * Lot L7 (E5), RG-TAR-04 a RG-TAR-06 : changement de forfait ou de rythme. Un apercu calcule par
 * le serveur est obligatoire avant la confirmation : nature, effet, date, montant au prorata (hausse
 * en paiement annuel), ou ce qui depasse (baisse refusee). Sur Devis : demande de devis.
 */
export function ChangePlanPage() {
  const [abonnement, setAbonnement] = useState<AbonnementVue | null>(null);
  const [chargement, setChargement] = useState(true);
  const [rythme, setRythme] = useState<BillingPeriod>('yearly');
  const [vise, setVise] = useState<PlanCode | null>(null);
  const [apercu, setApercu] = useState<ApercuChangementVue | null>(null);
  const [mode, setMode] = useState<PaymentMethod | null>(null);
  const [envoi, setEnvoi] = useState(false);
  const [erreur, setErreur] = useState<ErreurFacturation | null>(null);
  const [resultat, setResultat] = useState<string | null>(null);
  const [preparation, setPreparation] = useState<PreparationPaiementVue | null>(null);
  const [devis, setDevis] = useState<string | null>(null);

  useEffect(() => {
    billingService.getAbonnement()
      .then((a) => {
        setAbonnement(a);
        if (a.rythme) setRythme(a.rythme);
      })
      .catch((err) => setErreur(lireErreurFacturation(err, 'L’abonnement n’a pas pu être chargé.')))
      .finally(() => setChargement(false));
  }, []);

  async function choisir(t: PricingTier, r: BillingPeriod = rythme) {
    setErreur(null);
    setResultat(null);
    setApercu(null);
    if (t.quoteOnly && abonnement?.forfait !== 'sur_devis') {
      setVise(null);
      try {
        const rep = await billingService.contactSales({
          workspaceName: '', contactEmail: '', contactName: '', phone: '',
          message: 'Demande de devis pour le forfait Sur Devis depuis l’écran de changement de forfait.',
        });
        setDevis(rep.message);
      } catch (err) {
        setErreur(lireErreurFacturation(err, 'La demande de devis n’a pas pu être envoyée. Écrivez à contact@jurika.ai.'));
      }
      return;
    }
    setVise(t.code);
    try {
      setApercu(await billingService.apercuChangement({ forfait: t.code, rythme: r }));
    } catch (err) {
      setErreur(lireErreurFacturation(err, 'L’aperçu du changement n’a pas pu être calculé.'));
    }
  }

  async function confirmer() {
    if (!vise || !apercu) return;
    if (apercu.effet === 'APRES_PAIEMENT' && !mode) {
      setErreur({ code: null, message: 'Choisissez un mode de paiement pour la différence.', elements: [] });
      return;
    }
    setEnvoi(true);
    setErreur(null);
    try {
      const res = await billingService.changerForfait({ forfait: vise, rythme, mode: mode ?? undefined });
      oublierForfait();
      if (res.resultat === 'PAIEMENT_REQUIS' && res.paiement) {
        setPreparation(res.paiement);
      } else {
        setResultat(res.message);
        setApercu(null);
        setAbonnement(await billingService.getAbonnement());
      }
    } catch (err) {
      setErreur(lireErreurFacturation(err, 'Le changement n’a pas pu être enregistré.'));
    } finally {
      setEnvoi(false);
    }
  }

  if (chargement) {
    return <div className="flex h-64 items-center justify-center"><Loader2 className="h-6 w-6 animate-spin text-accent" aria-label="Chargement" /></div>;
  }

  const actif = abonnement?.statut === 'ACTIF';

  return (
    <div className="mx-auto max-w-6xl space-y-6" data-testid="change-plan-page">
      <Link to="/app/billing" className="inline-flex items-center gap-1 text-sm text-fg-muted hover:text-fg">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Retour à la facturation
      </Link>
      <header>
        <h1 className="font-heading text-2xl font-semibold text-fg">Changer de forfait ou de rythme</h1>
        {actif && (
          <p className="mt-1 text-sm text-fg-muted">
            Forfait actuel : <strong>{abonnement?.forfaitLibelle}</strong>
            {abonnement?.rythme ? `, ${LIBELLE_RYTHME[abonnement.rythme].toLowerCase()}` : ''}.
          </p>
        )}
      </header>
      <TexteAide cle="changement-forfait" titre="Comment se passe un changement ?">
        <p>
          Forfait supérieur : effectif tout de suite ; en paiement annuel, vous réglez la différence au prorata des mois
          restants, et les nouveaux quotas s’appliquent dès la confirmation du paiement ; en paiement mensuel, la mensualité
          suivante est celle du nouveau forfait. Forfait inférieur : effectif à la prochaine échéance, sans remboursement,
          et refusé si l’usage du cabinet dépasse le forfait visé. Rythme : changé à la prochaine échéance. Chaque changement
          donne lieu à de nouvelles conditions particulières à accepter.
        </p>
      </TexteAide>

      <ErreurFacturationBloc erreur={erreur} />
      {resultat && <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-3 text-sm text-fg" data-testid="change-plan-resultat">{resultat}</p>}
      {devis && <p role="status" className="rounded-lg border border-border bg-bg-overlay p-3 text-sm text-fg">{devis}</p>}

      {!actif ? (
        <p className="rounded-lg border border-border bg-bg-overlay p-4 text-sm text-fg-muted">
          Aucun abonnement actif : <Link to="/app/billing/payment?objet=SOUSCRIPTION" className="text-accent underline">souscrivez un forfait</Link>.
        </p>
      ) : preparation ? (
        <SuitePaiement preparation={preparation} />
      ) : (
        <>
          <PlanPicker period={rythme} forfaitActuel={abonnement?.forfait}
            onPeriodChange={(r) => {
              setRythme(r);
              setApercu(null);
              const t = vise ?? abonnement?.forfait;
              if (t) void choisir({ code: t as PlanCode, quoteOnly: false } as PricingTier, r);
            }}
            testIdPrefix="change-plan"
            libelleAction={(t) => (t.quoteOnly && abonnement?.forfait !== 'sur_devis' ? 'Demander un devis' : 'Voir l’effet')}
            onChoisir={(t) => void choisir(t)} desactive={envoi} />

          {apercu && (
            <section className="space-y-3 rounded-2xl bg-bg-raised p-5 text-sm shadow-sm" data-testid="change-plan-apercu">
              <h2 className="flex items-center gap-1 text-base font-semibold text-fg">
                Aperçu : {apercu.versLibelle}, {LIBELLE_RYTHME[apercu.rythmeVise].toLowerCase()}
                <InfoBulle texte="Calculé par le serveur à partir de votre abonnement et de l’usage du cabinet. Rien n’est enregistré avant la confirmation." />
              </h2>
              <p className="text-fg">{apercu.message}</p>
              {apercu.effet && (
                <p className="text-fg-muted">
                  {LIBELLE_EFFET[apercu.effet]}{apercu.effectifLe ? ` (le ${formatDateIso(apercu.effectifLe)})` : ''}.
                </p>
              )}
              {apercu.montants && (
                <p className="text-fg">
                  Différence à régler ({apercu.moisRestants} mois restants) : {formatMadCents(apercu.montants.htCentimes)} HT,{' '}
                  <strong>{formatMadCents(apercu.montants.ttcCentimes)} TTC</strong>.
                </p>
              )}
              {apercu.depassements.length > 0 && (
                <ul className="list-disc space-y-0.5 pl-6 text-danger" data-testid="change-plan-depassements">
                  {apercu.depassements.map((d) => <li key={d}>{d}</li>)}
                </ul>
              )}
              {apercu.accepte && apercu.effet === 'APRES_PAIEMENT' && (
                <ChoixModePaiement valeur={mode} onChange={setMode}
                  carteDisponible={abonnement?.carteDisponible ?? false} carteMotif={abonnement?.carteIndisponibleMotif} />
              )}
              {apercu.accepte && (
                <button type="button" onClick={() => void confirmer()} disabled={envoi}
                  data-testid="change-plan-confirmer"
                  className="inline-flex items-center gap-2 rounded-lg bg-accent px-5 py-2 text-sm font-bold text-bg hover:bg-accent-hover disabled:opacity-50">
                  {envoi && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
                  {apercu.effet === 'APRES_PAIEMENT' ? 'Confirmer et payer la différence' : 'Confirmer le changement'}
                </button>
              )}
            </section>
          )}
        </>
      )}
    </div>
  );
}
