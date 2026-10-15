import { useCallback, useEffect, useState } from 'react';
import { Check, CreditCard, Download, Minus, Receipt } from 'lucide-react';
import { billingService, enregistrerPdf } from '../../services/billing.service';
import { emitBusinessEvent } from '../../services/analytics.service';
import { useCurrentUser } from '../../store/authStore';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { EmptyState } from '../../components/ui/EmptyState';
import { Lien } from '../../components/ui/Lien';
import { CardSkeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { Message } from '../../components/ui/Message';
import { Tableau, type ColonneTableau } from '../../components/ui/Tableau';
import { ErreurFacturationBloc } from '../../components/billing/ErreurFacturation';
import { EcranOuOnglet } from '../settings/EcranOuOnglet';
import { oublierForfait } from '../../lib/useForfait';
import { jourEnLettres, messageImpaye } from '../../lib/impaye';
import {
  formatDateIso,
  formatFinIncluse,
  formatMadCents,
  lireErreurFacturation,
  LIBELLE_MODE,
  LIBELLE_MOTIF_ARCHIVAGE,
  LIBELLE_OBJET,
  LIBELLE_RYTHME,
  LIBELLE_STATUT_PAIEMENT,
  type AbonnementVue,
  type ErreurFacturation,
  type FactureVue,
  type PaiementVue,
  type UsageSnapshot,
} from '../../types/billing';

const TITRE = 'Abonnement et facturation';
const DESCRIPTION = 'Votre forfait, l’usage du mois, les échéances, les paiements et les factures du cabinet.';

/**
 * Lot L7 (E5) : page Facturation du cabinet. Etat de l'abonnement (forfait, rythme, annee
 * d'abonnement, periode payee, prochaine echeance, changement a venir), usage du forfait (dossiers
 * du mois d'abonnement, acces employes, stockage), fonctions du forfait, paiements et factures.
 * L'employe voit l'abonnement en lecture seule ; paiements et factures sont reserves au superviseur.
 *
 * Lot L6b (E8) : structure unique <Page> ; l'action principale (payer l'echeance ou souscrire) et
 * le changement de forfait sont dans l'en-tete, pour le superviseur seulement (le serveur refuse
 * ces operations a l'employe : BillingController, hasRole('SUPERVISEUR')).
 */
export function BillingPage() {
  const user = useCurrentUser();
  const superviseur = user?.role === 'SUPERVISEUR' || user?.role === 'SUPER_ADMIN';
  const [abonnement, setAbonnement] = useState<AbonnementVue | null>(null);
  const [usage, setUsage] = useState<UsageSnapshot | null>(null);
  const [paiements, setPaiements] = useState<PaiementVue[]>([]);
  const [factures, setFactures] = useState<FactureVue[]>([]);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<ErreurFacturation | null>(null);
  const [annulation, setAnnulation] = useState(false);

  // Les mises a jour d'etat suivent les reponses (aucune mise a jour synchrone dans l'effet).
  const charger = useCallback(
    () =>
      Promise.all([
        billingService.getAbonnement(),
        billingService.getUsage().catch(() => null),
        superviseur ? billingService.listerPaiements().catch(() => [] as PaiementVue[]) : Promise.resolve([] as PaiementVue[]),
        superviseur ? billingService.listerFactures().catch(() => [] as FactureVue[]) : Promise.resolve([] as FactureVue[]),
      ])
        .then(([a, u, p, f]) => {
          setAbonnement(a);
          setUsage(u);
          setPaiements(p);
          setFactures(f);
        })
        .catch((err: unknown) => setErreur(lireErreurFacturation(err, 'L’abonnement n’a pas pu être chargé. Réessayez.')))
        .finally(() => setChargement(false)),
    [superviseur],
  );

  useEffect(() => {
    emitBusinessEvent('BILLING_PAGE_VIEWED');
    void charger();
  }, [charger]);

  async function annulerChangement() {
    setAnnulation(true);
    try {
      await billingService.annulerChangement();
      oublierForfait();
      await charger();
    } catch (err) {
      setErreur(lireErreurFacturation(err, 'Le changement programmé n’a pas pu être annulé.'));
    } finally {
      setAnnulation(false);
    }
  }

  async function telecharger(f: FactureVue) {
    try {
      enregistrerPdf(await billingService.telechargerFacture(f.id), `Facture-${f.numero}.pdf`);
    } catch (err) {
      setErreur(lireErreurFacturation(err, 'La facture n’a pas pu être téléchargée.'));
    }
  }

  if (chargement) {
    return (
      <EcranOuOnglet titre={TITRE} description={DESCRIPTION}>
        <div data-testid="billing-loading">
          <SqueletteZone libelle="Chargement de l’abonnement">
            <div className="space-y-6">
              <CardSkeleton className="h-52" />
              <CardSkeleton className="h-36" />
              <CardSkeleton className="h-40" />
            </div>
          </SqueletteZone>
        </div>
      </EcranOuOnglet>
    );
  }

  // Lot L7 (E8) : SUSPENDU = abonnement en cours, acces suspendu pour impaye (le forfait n'est pas arrete).
  const actif = abonnement?.statut === 'ACTIF' || abonnement?.statut === 'SUSPENDU';
  const impaye = messageImpaye(abonnement?.impaye, superviseur);
  const e = abonnement?.prochaineEcheance ?? null;

  // Action principale de l'ecran (superviseur) : payer l'echeance si l'abonnement court, sinon souscrire.
  const actionPrincipale =
    superviseur && abonnement ? (
      actif ? (
        <Lien to="/app/billing/payment?objet=ECHEANCE" data-testid="billing-payer-echeance" commeBouton={{ variant: 'primary' }}>
          <CreditCard className="h-4 w-4" aria-hidden="true" />
          Payer l’échéance
        </Lien>
      ) : (
        <Lien to="/app/billing/payment?objet=SOUSCRIPTION" data-testid="billing-souscrire" commeBouton={{ variant: 'primary' }}>
          {abonnement.statut === 'ARCHIVE' ? 'Souscrire de nouveau' : 'Souscrire un forfait'}
        </Lien>
      )
    ) : undefined;
  const actionSecondaire =
    superviseur && abonnement && actif ? (
      <Lien to="/app/billing/change-plan" data-testid="billing-changer-forfait" commeBouton={{ variant: 'secondary' }}>
        Changer de forfait ou de rythme
      </Lien>
    ) : undefined;

  const colonnesPaiements: ColonneTableau<PaiementVue>[] = [
    { cle: 'date', titre: 'Date', rendu: (p) => formatDateIso(p.creeLe) },
    { cle: 'objet', titre: 'Objet', rendu: (p) => `${p.objet ? LIBELLE_OBJET[p.objet] : '—'} · ${p.forfaitLibelle}` },
    { cle: 'mode', titre: 'Mode', rendu: (p) => LIBELLE_MODE[p.mode] ?? p.mode, masquerSous: 'md' },
    {
      cle: 'montant',
      titre: 'Montant TTC',
      alignement: 'droite',
      rendu: (p) => <span className="tabular-nums">{p.montants ? formatMadCents(p.montants.ttcCentimes) : '—'}</span>,
    },
    { cle: 'etat', titre: 'État', rendu: (p) => LIBELLE_STATUT_PAIEMENT[p.statut] ?? p.statut },
    {
      cle: 'facture',
      titre: 'Facture',
      masquerSous: 'lg',
      rendu: (p) =>
        p.factureNumero ?? (p.factureRefus ? <span className="text-warning">Facture en attente : {p.factureRefus}</span> : '—'),
    },
  ];

  return (
    <EcranOuOnglet
      titre={TITRE}
      description={DESCRIPTION}
      action={actionPrincipale}
      actions={actionSecondaire}
      aide={
        <TexteAide cle="facturation-l7" titre="Que faire sur cet écran ?">
          <p>
            {!abonnement || abonnement.statut === 'AUCUN'
              ? 'Votre cabinet n’a pas encore souscrit de forfait : « Souscrire un forfait » vous fait choisir le forfait, le rythme de paiement puis le mode de paiement.'
              : abonnement.statut === 'ARCHIVE'
                ? 'Votre forfait est arrêté : le cabinet est archivé, rien n’a été supprimé. Souscrivez de nouveau pour retrouver votre travail dans l’état exact où vous l’avez laissé.'
                : abonnement.statut === 'SUSPENDU'
                  ? 'L’accès au cabinet est suspendu : une échéance n’est pas réglée. « Payer l’échéance » la règle ; l’accès est rétabli dès le paiement constaté, dans l’état exact, et rien n’a été supprimé.'
                  : 'Réglez la prochaine échéance avant sa date. Sans paiement, un rappel vous est envoyé ; 15 jours après l’échéance, l’accès au cabinet est suspendu jusqu’au paiement (rien n’est supprimé). « Changer de forfait » montre l’effet et le montant avant toute confirmation. Les factures se téléchargent en PDF.'}
            {!superviseur && ' Seul le superviseur du cabinet règle l’abonnement et consulte les factures.'}
          </p>
        </TexteAide>
      }
    >
      <div className="space-y-6" data-testid="billing-page">
        <ErreurFacturationBloc erreur={erreur} />

        {abonnement && (
          <Card
            data-testid="billing-abonnement"
            title={
              <span className="flex items-center gap-1">
                Abonnement
                <InfoBulle
                  libelle="Aide : abonnement"
                  texte="Le forfait est souscrit pour un an. La période payée court jusqu’à la prochaine échéance. Sans paiement, un rappel est envoyé et l’accès est suspendu 15 jours après l’échéance, jusqu’au paiement (article 4.4 du contrat). Le cabinet n’est archivé qu’à l’arrêt du forfait (résiliation)."
                />
              </span>
            }
          >
            {abonnement.impaye && impaye && (
              <div className="mb-4" data-testid="billing-impaye">
                <Message ton={abonnement.impaye.suspendu ? 'erreur' : 'attention'} titre={impaye.titre}>
                  <p>{impaye.detail}</p>
                  <p className="mt-1 text-xs text-fg-muted">
                    {abonnement.impaye.rappelEnvoyeLe
                      ? `Rappel envoyé à l’adresse électronique du cabinet le ${jourEnLettres(abonnement.impaye.rappelEnvoyeLe)}.`
                      : 'Le rappel n’a pas encore pu être envoyé : JURIKA en est averti.'}
                  </p>
                </Message>
              </div>
            )}
            {abonnement.statut === 'AUCUN' && (
              <p className="text-sm text-fg-muted" data-testid="billing-aucun">
                Aucun forfait souscrit pour l’instant.
              </p>
            )}
            {abonnement.statut === 'ARCHIVE' && (
              <p className="text-sm text-fg-muted" data-testid="billing-archive">
                Cabinet archivé
                {abonnement.archiveMotif ? ` (${LIBELLE_MOTIF_ARCHIVAGE[abonnement.archiveMotif] ?? abonnement.archiveMotif})` : ''}.
                Dernier forfait : {abonnement.forfaitLibelle ?? '—'}. Rien n’a été supprimé.
              </p>
            )}
            {actif && (
              <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
                <Ligne libelle="Forfait" valeur={abonnement.forfaitLibelle ?? '—'} />
                <Ligne libelle="Rythme" valeur={abonnement.rythme ? LIBELLE_RYTHME[abonnement.rythme] : 'Inconnu (à fixer par JURIKA)'} />
                <Ligne libelle="Année d’abonnement" valeur={`du ${formatDateIso(abonnement.engagementDebut)} au ${formatFinIncluse(abonnement.engagementFin)}`} />
                <Ligne libelle="Période payée" valeur={`du ${formatDateIso(abonnement.periodeDebut)} au ${formatFinIncluse(abonnement.periodeFin)}`} />
              </dl>
            )}
            {actif && e && (
              <div className="mt-4 rounded-lg border border-border bg-bg-overlay p-4 text-sm" data-testid="billing-echeance">
                <p className="font-medium text-fg">Prochaine échéance : le {formatDateIso(e.debut)}</p>
                <p className="mt-1 text-fg-muted">
                  {e.forfaitLibelle}
                  {e.rythme ? `, ${LIBELLE_RYTHME[e.rythme].toLowerCase()}` : ''}
                  {e.fin ? `, période du ${formatDateIso(e.debut)} au ${formatFinIncluse(e.fin)}` : ''}
                </p>
                {e.montants ? (
                  <p className="mt-1 text-fg">
                    {formatMadCents(e.montants.htCentimes)} HT + TVA {String(e.montants.tauxTva).replace('.', ',')} %{' '}
                    ({formatMadCents(e.montants.tvaCentimes)}) = <strong>{formatMadCents(e.montants.ttcCentimes)} TTC</strong>
                  </p>
                ) : (
                  <p className="mt-1 text-warning">{e.motif}</p>
                )}
              </div>
            )}
            {actif && abonnement.changementAVenir && (
              <div className="mt-3" data-testid="billing-changement-a-venir">
                <Message
                  ton="info"
                  action={
                    superviseur ? (
                      <Button type="button" size="sm" variant="secondary" onClick={() => void annulerChangement()} loading={annulation}>
                        Annuler ce changement
                      </Button>
                    ) : undefined
                  }
                >
                  À la prochaine échéance ({formatDateIso(abonnement.changementAVenir.effectifLe)}) : forfait{' '}
                  <strong>{abonnement.changementAVenir.forfaitLibelle}</strong>
                  {abonnement.changementAVenir.rythme ? `, ${LIBELLE_RYTHME[abonnement.changementAVenir.rythme].toLowerCase()}` : ''}.
                </Message>
              </div>
            )}
            {abonnement.paiementsEnAttente > 0 && (
              <p className="mt-3 text-xs text-fg-muted">
                {abonnement.paiementsEnAttente} paiement(s) en attente : un virement, un chèque ou des espèces sont pris en
                compte quand JURIKA enregistre la réception ; une carte, à la confirmation du CMI.
              </p>
            )}
          </Card>
        )}

        {usage && (
          <Card
            data-testid="billing-usage"
            title={
              <span className="flex items-center gap-1">
                Usage du forfait
                <InfoBulle
                  libelle="Aide : usage du forfait"
                  texte="Un dossier est compté dès qu’un ticket y est ouvert dans le mois d’abonnement, une seule fois. À 100 %, l’ouverture d’un dossier de plus et l’ajout de documents sont bloqués ; la consultation reste possible."
                />
              </span>
            }
          >
            {usage.forfaitDefini === false && (
              <Message ton="attention" className="mb-3">
                Aucun forfait enregistré pour ce cabinet : les limites du forfait Essentiel s’appliquent.
              </Message>
            )}
            <ul className="space-y-1.5 text-sm text-fg">
              <li>
                Dossiers ouverts ce mois d’abonnement
                {usage.periodeDebut ? ` (du ${formatDateIso(usage.periodeDebut)} au ${formatFinIncluse(usage.periodeFin)})` : ''} :{' '}
                <strong>{usage.dossiers}</strong>
                {usage.dossiersUnlimited ? '' : ` sur ${usage.maxDossiers}`}
              </li>
              <li>
                Accès employés (en plus du superviseur) : <strong>{usage.users}</strong>
                {usage.usersUnlimited ? '' : ` sur ${usage.maxUsers}`}
              </li>
              <li>
                Stockage :{' '}
                {usage.storageMesure === false ? (
                  'non mesuré'
                ) : (
                  <>
                    <strong>{(usage.storageBytes / 1024 ** 3).toFixed(2).replace('.', ',')} Go</strong>
                    {usage.storageUnlimited ? '' : ` sur ${usage.maxStorageGb} Go`}
                  </>
                )}
              </li>
            </ul>
          </Card>
        )}

        {abonnement && abonnement.fonctions.length > 0 && (
          <Card data-testid="billing-fonctions" title="Fonctions du forfait">
            <ul className="grid gap-1.5 text-sm sm:grid-cols-2">
              {abonnement.fonctions.map((f) => (
                <li key={f.code} className="flex items-start gap-2">
                  {f.incluse ? (
                    <Check className="mt-0.5 h-4 w-4 shrink-0 text-success" aria-hidden="true" />
                  ) : (
                    <Minus className="mt-0.5 h-4 w-4 shrink-0 text-fg-subtle" aria-hidden="true" />
                  )}
                  <span className={f.incluse ? 'text-fg' : 'text-fg-muted'}>
                    {f.libelle}
                    {f.incluse ? '' : ` : disponible dès le forfait ${f.disponibleDes}`}
                  </span>
                </li>
              ))}
            </ul>
          </Card>
        )}

        {superviseur && (
          <section data-testid="billing-paiements" aria-labelledby="titre-paiements" className="space-y-3">
            <h2 id="titre-paiements" className="text-base font-semibold text-heading">
              Paiements
            </h2>
            <Tableau
              legende="Paiements du cabinet"
              colonnes={colonnesPaiements}
              lignes={paiements}
              cleLigne={(p) => String(p.id)}
              enTeteCollant={paiements.length > 12}
              hauteurMax="32rem"
              vide={
                <EmptyState
                  compact
                  icon={<CreditCard />}
                  title="Aucun paiement pour l’instant"
                  description="Un paiement apparaît ici dès qu’il est préparé : carte, virement, chèque ou espèces."
                />
              }
            />
          </section>
        )}

        {superviseur && (
          <Card data-testid="billing-factures" title="Factures" padding={factures.length === 0}>
            {factures.length === 0 ? (
              <EmptyState
                compact
                icon={<Receipt />}
                title="Aucune facture"
                description="Une facture est émise à chaque paiement acquis ; elle se télécharge ici en PDF."
              />
            ) : (
              <ul className="divide-y divide-border text-sm">
                {factures.map((f) => (
                  <li key={f.id} className="flex flex-wrap items-center justify-between gap-2 px-5 py-3">
                    <span className="min-w-0">
                      <strong>{f.numero}</strong> du {formatDateIso(f.dateEmission)} · {f.designation} ·{' '}
                      {formatMadCents(f.montants.ttcCentimes)} TTC
                    </span>
                    <Button
                      type="button"
                      size="sm"
                      variant="secondary"
                      onClick={() => void telecharger(f)}
                      icon={<Download />}
                      aria-label={`Télécharger la facture ${f.numero} en PDF`}
                    >
                      PDF
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        )}
      </div>
    </EcranOuOnglet>
  );
}

function Ligne({ libelle, valeur }: { libelle: string; valeur: string }) {
  return (
    <div>
      <dt className="text-xs text-fg-muted">{libelle}</dt>
      <dd className="text-fg">{valeur}</dd>
    </div>
  );
}
