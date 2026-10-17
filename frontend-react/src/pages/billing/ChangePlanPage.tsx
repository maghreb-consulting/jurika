import { useEffect, useState } from 'react';
import { ArrowLeft } from 'lucide-react';
import { billingService } from '../../services/billing.service';
import { useCurrentUser } from '../../store/authStore';
import { Page } from '../../components/layout/Page';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { EmptyState } from '../../components/ui/EmptyState';
import { Lien } from '../../components/ui/Lien';
import { CardSkeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { Message } from '../../components/ui/Message';
import { PlanPicker } from '../../components/billing/PlanPicker';
import { ChoixModePaiement } from '../../components/billing/ChoixModePaiement';
import { SuitePaiement } from '../../components/billing/SuitePaiement';
import { ErreurFacturationBloc } from '../../components/billing/ErreurFacturation';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { oublierForfait } from '../../lib/useForfait';
import { messageEchecDevis } from '../../lib/demandeDevis';
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
  const role = useCurrentUser()?.role;
  // Meme regle que le serveur (apercu et changement : superviseur ; super-admin par la hierarchie).
  const superviseur = role === 'SUPERVISEUR' || role === 'SUPER_ADMIN';
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
        setErreur(lireErreurFacturation(err, messageEchecDevis()));
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

  // L6b (E8) : fil d'Ariane explicite (ecran hors menu) : Facturation > Changer de forfait.
  const fil = [
    { libelle: 'Accueil', to: '/dashboard' },
    { libelle: 'Facturation', to: '/app/billing' },
    { libelle: 'Changer de forfait' },
  ];
  const titre = 'Changer de forfait ou de rythme';

  if (chargement) {
    return (
      <Page titre={titre} fil={fil}>
        <SqueletteZone libelle="Chargement de l’abonnement">
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {Array.from({ length: 4 }).map((_, i) => (
              <CardSkeleton key={i} className="h-96" />
            ))}
          </div>
        </SqueletteZone>
      </Page>
    );
  }

  // Le changement de forfait est une operation du superviseur (le serveur la refuse a l'employe).
  if (!superviseur) {
    return (
      <Page titre={titre} fil={fil}>
        <div data-testid="change-plan-page">
          <Message
            ton="info"
            titre="Réservé au superviseur du cabinet"
            action={
              <Lien to="/app/billing" commeBouton={{ variant: 'secondary', size: 'sm' }}>
                Retour à la facturation
              </Lien>
            }
          >
            Seul le superviseur peut changer le forfait ou le rythme de paiement. Prévenez-le si le forfait actuel ne
            suffit plus au cabinet.
          </Message>
        </div>
      </Page>
    );
  }

  const actif = abonnement?.statut === 'ACTIF';

  return (
    <Page
      titre={titre}
      fil={fil}
      description={
        actif ? (
          <>
            Forfait actuel : <strong className="text-fg">{abonnement?.forfaitLibelle}</strong>
            {abonnement?.rythme ? `, ${LIBELLE_RYTHME[abonnement.rythme].toLowerCase()}` : ''}.
          </>
        ) : undefined
      }
      actions={
        <Lien to="/app/billing" commeBouton={{ variant: 'secondary' }}>
          <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Retour à la facturation
        </Lien>
      }
      aide={
        <TexteAide cle="changement-forfait" titre="Comment se passe un changement ?">
          <p>
            Forfait supérieur : effectif tout de suite ; en paiement annuel, vous réglez la différence au prorata des mois
            restants, et les nouveaux quotas s’appliquent dès la confirmation du paiement ; en paiement mensuel, la mensualité
            suivante est celle du nouveau forfait. Forfait inférieur : effectif à la prochaine échéance, sans remboursement,
            et refusé si l’usage du cabinet dépasse le forfait visé. Rythme : changé à la prochaine échéance. Chaque changement
            donne lieu à de nouvelles conditions particulières à accepter.
          </p>
        </TexteAide>
      }
    >
      <div className="space-y-6" data-testid="change-plan-page">
        <ErreurFacturationBloc erreur={erreur} />
        {resultat && (
          <div data-testid="change-plan-resultat">
            <Message ton="succes">{resultat}</Message>
          </div>
        )}
        {devis && <Message ton="info">{devis}</Message>}

        {!actif ? (
          <EmptyState
            title="Aucun abonnement actif"
            description="Un changement de forfait suppose un abonnement en cours. Souscrivez d’abord un forfait."
            cta={
              <Lien to="/app/billing/payment?objet=SOUSCRIPTION" commeBouton={{ variant: 'primary' }}>
                Souscrire un forfait
              </Lien>
            }
          />
        ) : preparation ? (
          <SuitePaiement preparation={preparation} />
        ) : (
          <>
            <PlanPicker
              period={rythme}
              forfaitActuel={abonnement?.forfait}
              onPeriodChange={(r) => {
                setRythme(r);
                setApercu(null);
                const t = vise ?? abonnement?.forfait;
                if (t) void choisir({ code: t as PlanCode, quoteOnly: false } as PricingTier, r);
              }}
              testIdPrefix="change-plan"
              libelleAction={(t) => (t.quoteOnly && abonnement?.forfait !== 'sur_devis' ? 'Demander un devis' : 'Voir l’effet')}
              onChoisir={(t) => void choisir(t)}
              desactive={envoi}
            />

            {apercu && (
              <Card
                accent
                data-testid="change-plan-apercu"
                title={
                  <span className="flex items-center gap-1">
                    Aperçu : {apercu.versLibelle}, {LIBELLE_RYTHME[apercu.rythmeVise].toLowerCase()}
                    <InfoBulle
                      libelle="Aide : aperçu du changement"
                      texte="Calculé par le serveur à partir de votre abonnement et de l’usage du cabinet. Rien n’est enregistré avant la confirmation."
                    />
                  </span>
                }
              >
                <div className="space-y-3 text-sm">
                  <p className="text-fg">{apercu.message}</p>
                  {apercu.effet && (
                    <p className="text-fg-muted">
                      {LIBELLE_EFFET[apercu.effet]}
                      {apercu.effectifLe ? ` (le ${formatDateIso(apercu.effectifLe)})` : ''}.
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
                      {apercu.depassements.map((d) => (
                        <li key={d}>{d}</li>
                      ))}
                    </ul>
                  )}
                  {apercu.accepte && apercu.effet === 'APRES_PAIEMENT' && (
                    <ChoixModePaiement
                      valeur={mode}
                      onChange={setMode}
                      carteDisponible={abonnement?.carteDisponible ?? false}
                      carteMotif={abonnement?.carteIndisponibleMotif}
                    />
                  )}
                  {apercu.accepte && (
                    <div className="flex justify-end">
                      <Button type="button" onClick={() => void confirmer()} loading={envoi} data-testid="change-plan-confirmer">
                        {apercu.effet === 'APRES_PAIEMENT' ? 'Confirmer et payer la différence' : 'Confirmer le changement'}
                      </Button>
                    </div>
                  )}
                </div>
              </Card>
            )}
          </>
        )}
      </div>
    </Page>
  );
}
