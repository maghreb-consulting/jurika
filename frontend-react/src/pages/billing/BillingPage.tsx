import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Check, Download, Loader2, Minus } from 'lucide-react';
import { billingService, enregistrerPdf } from '../../services/billing.service';
import { emitBusinessEvent } from '../../services/analytics.service';
import { useCurrentUser } from '../../store/authStore';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { ErreurFacturationBloc } from '../../components/billing/ErreurFacturation';
import { oublierForfait } from '../../lib/useForfait';
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

/**
 * Lot L7 (E5) : page Facturation du cabinet. Etat de l'abonnement (forfait, rythme, annee
 * d'abonnement, periode payee, prochaine echeance, changement a venir), usage du forfait (dossiers
 * du mois d'abonnement, acces employes, stockage), fonctions du forfait, paiements et factures.
 * L'employe voit l'abonnement en lecture seule ; paiements et factures sont reserves au superviseur.
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
      <div className="flex h-64 items-center justify-center" data-testid="billing-loading">
        <Loader2 className="h-6 w-6 animate-spin text-accent" aria-label="Chargement" />
      </div>
    );
  }

  const actif = abonnement?.statut === 'ACTIF';
  const e = abonnement?.prochaineEcheance ?? null;

  return (
    <div className="space-y-6" data-testid="billing-page">
      <header className="rounded-2xl bg-bg-raised p-6 shadow-sm">
        <h1 className="font-heading text-2xl font-semibold text-fg">Abonnement et facturation</h1>
        <p className="mt-1 text-sm text-fg-muted">
          Votre forfait, l’usage du mois, les échéances, les paiements et les factures du cabinet.
        </p>
      </header>

      <TexteAide cle="facturation-l7" titre="Que faire sur cet écran ?">
        <p>
          {!abonnement || abonnement.statut === 'AUCUN'
            ? 'Votre cabinet n’a pas encore souscrit de forfait : « Souscrire un forfait » vous fait choisir le forfait, le rythme de paiement puis le mode de paiement.'
            : abonnement.statut === 'ARCHIVE'
              ? 'Votre forfait est arrêté : le cabinet est archivé, rien n’a été supprimé. Souscrivez de nouveau pour retrouver votre travail dans l’état exact où vous l’avez laissé.'
              : 'Réglez la prochaine échéance avant sa date : sans paiement, le cabinet est archivé (rien n’est supprimé). « Changer de forfait » montre l’effet et le montant avant toute confirmation. Les factures se téléchargent en PDF.'}
          {!superviseur && ' Seul le superviseur du cabinet règle l’abonnement et consulte les factures.'}
        </p>
      </TexteAide>

      <ErreurFacturationBloc erreur={erreur} />

      {abonnement && (
        <section className="rounded-2xl bg-bg-raised p-6 shadow-sm" data-testid="billing-abonnement">
          <h2 className="mb-3 flex items-center gap-1 text-base font-semibold text-fg">
            Abonnement
            <InfoBulle texte="Le forfait est souscrit pour un an. La période payée court jusqu’à la prochaine échéance ; au-delà, sans paiement, le cabinet est archivé." />
          </h2>
          {abonnement.statut === 'AUCUN' && (
            <p className="text-sm text-fg-muted" data-testid="billing-aucun">Aucun forfait souscrit pour l’instant.</p>
          )}
          {abonnement.statut === 'ARCHIVE' && (
            <p className="text-sm text-fg-muted" data-testid="billing-archive">
              Cabinet archivé
              {abonnement.archiveMotif ? ` (${LIBELLE_MOTIF_ARCHIVAGE[abonnement.archiveMotif] ?? abonnement.archiveMotif})` : ''}.
              Dernier forfait : {abonnement.forfaitLibelle ?? '—'}. Rien n’a été supprimé.
            </p>
          )}
          {actif && (
            <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
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
                {e.forfaitLibelle}{e.rythme ? `, ${LIBELLE_RYTHME[e.rythme].toLowerCase()}` : ''}
                {e.fin ? `, période du ${formatDateIso(e.debut)} au ${formatFinIncluse(e.fin)}` : ''}
              </p>
              {e.montants ? (
                <p className="mt-1 text-fg">
                  {formatMadCents(e.montants.htCentimes)} HT + TVA {String(e.montants.tauxTva).replace('.', ',')} %
                  {' '}({formatMadCents(e.montants.tvaCentimes)}) = <strong>{formatMadCents(e.montants.ttcCentimes)} TTC</strong>
                </p>
              ) : (
                <p className="mt-1 text-warning">{e.motif}</p>
              )}
            </div>
          )}
          {actif && abonnement.changementAVenir && (
            <div className="mt-3 flex flex-wrap items-center justify-between gap-2 rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm" data-testid="billing-changement-a-venir">
              <span>
                À la prochaine échéance ({formatDateIso(abonnement.changementAVenir.effectifLe)}) : forfait{' '}
                <strong>{abonnement.changementAVenir.forfaitLibelle}</strong>
                {abonnement.changementAVenir.rythme ? `, ${LIBELLE_RYTHME[abonnement.changementAVenir.rythme].toLowerCase()}` : ''}.
              </span>
              {superviseur && (
                <button type="button" onClick={() => void annulerChangement()} disabled={annulation}
                  className="rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-fg hover:bg-bg-overlay disabled:opacity-50">
                  Annuler ce changement
                </button>
              )}
            </div>
          )}
          {superviseur && (
            <div className="mt-4 flex flex-wrap gap-3">
              {actif ? (
                <>
                  <Link to="/app/billing/payment?objet=ECHEANCE" data-testid="billing-payer-echeance"
                    className="rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-bg hover:bg-accent-hover">
                    Payer l’échéance
                  </Link>
                  <Link to="/app/billing/change-plan" className="rounded-lg border border-border px-4 py-2 text-sm font-semibold text-fg hover:bg-bg-overlay">
                    Changer de forfait ou de rythme
                  </Link>
                </>
              ) : (
                <Link to="/app/billing/payment?objet=SOUSCRIPTION" data-testid="billing-souscrire"
                  className="rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-bg hover:bg-accent-hover">
                  {abonnement.statut === 'ARCHIVE' ? 'Souscrire de nouveau' : 'Souscrire un forfait'}
                </Link>
              )}
            </div>
          )}
          {abonnement.paiementsEnAttente > 0 && (
            <p className="mt-3 text-xs text-fg-subtle">
              {abonnement.paiementsEnAttente} paiement(s) en attente : un virement, un chèque ou des espèces sont pris en
              compte quand JURIKA enregistre la réception ; une carte, à la confirmation du CMI.
            </p>
          )}
        </section>
      )}

      {usage && (
        <section className="rounded-2xl bg-bg-raised p-6 shadow-sm" data-testid="billing-usage">
          <h2 className="mb-3 flex items-center gap-1 text-base font-semibold text-fg">
            Usage du forfait
            <InfoBulle texte="Un dossier est compté dès qu’un ticket y est ouvert dans le mois d’abonnement, une seule fois. À 100 %, l’ouverture d’un dossier de plus et l’ajout de documents sont bloqués ; la consultation reste possible." />
          </h2>
          {usage.forfaitDefini === false && (
            <p className="mb-2 text-sm text-warning">Aucun forfait enregistré pour ce cabinet : les limites du forfait Essentiel s’appliquent.</p>
          )}
          <ul className="space-y-1 text-sm text-fg">
            <li>
              Dossiers ouverts ce mois d’abonnement
              {usage.periodeDebut ? ` (du ${formatDateIso(usage.periodeDebut)} au ${formatFinIncluse(usage.periodeFin)})` : ''} :{' '}
              <strong>{usage.dossiers}</strong>{usage.dossiersUnlimited ? '' : ` sur ${usage.maxDossiers}`}
            </li>
            <li>Accès employés (en plus du superviseur) : <strong>{usage.users}</strong>{usage.usersUnlimited ? '' : ` sur ${usage.maxUsers}`}</li>
            <li>
              Stockage :{' '}
              {usage.storageMesure === false
                ? 'non mesuré'
                : <><strong>{(usage.storageBytes / 1024 ** 3).toFixed(2).replace('.', ',')} Go</strong>{usage.storageUnlimited ? '' : ` sur ${usage.maxStorageGb} Go`}</>}
            </li>
          </ul>
        </section>
      )}

      {abonnement && abonnement.fonctions.length > 0 && (
        <section className="rounded-2xl bg-bg-raised p-6 shadow-sm" data-testid="billing-fonctions">
          <h2 className="mb-3 text-base font-semibold text-fg">Fonctions du forfait</h2>
          <ul className="grid gap-1 text-sm sm:grid-cols-2">
            {abonnement.fonctions.map((f) => (
              <li key={f.code} className="flex items-start gap-2">
                {f.incluse
                  ? <Check className="mt-0.5 h-4 w-4 flex-shrink-0 text-success" aria-hidden="true" />
                  : <Minus className="mt-0.5 h-4 w-4 flex-shrink-0 text-fg-subtle" aria-hidden="true" />}
                <span className={f.incluse ? 'text-fg' : 'text-fg-subtle'}>
                  {f.libelle}{f.incluse ? '' : ` : disponible dès le forfait ${f.disponibleDes}`}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {superviseur && (
        <section className="rounded-2xl bg-bg-raised p-6 shadow-sm" data-testid="billing-paiements">
          <h2 className="mb-3 text-base font-semibold text-fg">Paiements</h2>
          {paiements.length === 0 ? (
            <p className="text-sm text-fg-muted">Aucun paiement pour l’instant.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="text-xs uppercase text-fg-subtle">
                  <tr><th className="py-2 pr-3">Date</th><th className="pr-3">Objet</th><th className="pr-3">Mode</th><th className="pr-3">Montant TTC</th><th className="pr-3">État</th><th>Facture</th></tr>
                </thead>
                <tbody>
                  {paiements.map((p) => (
                    <tr key={p.id} className="border-t border-border">
                      <td className="py-2 pr-3">{formatDateIso(p.creeLe)}</td>
                      <td className="pr-3">{p.objet ? LIBELLE_OBJET[p.objet] : '—'} · {p.forfaitLibelle}</td>
                      <td className="pr-3">{LIBELLE_MODE[p.mode] ?? p.mode}</td>
                      <td className="pr-3">{p.montants ? formatMadCents(p.montants.ttcCentimes) : '—'}</td>
                      <td className="pr-3">{LIBELLE_STATUT_PAIEMENT[p.statut] ?? p.statut}</td>
                      <td>
                        {p.factureNumero ?? (p.factureRefus
                          ? <span className="text-warning">Facture en attente : {p.factureRefus}</span>
                          : '—')}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}

      {superviseur && (
        <section className="rounded-2xl bg-bg-raised p-6 shadow-sm" data-testid="billing-factures">
          <h2 className="mb-3 text-base font-semibold text-fg">Factures</h2>
          {factures.length === 0 ? (
            <p className="text-sm text-fg-muted">Aucune facture. Une facture est émise à chaque paiement acquis.</p>
          ) : (
            <ul className="divide-y divide-border text-sm">
              {factures.map((f) => (
                <li key={f.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                  <span>
                    <strong>{f.numero}</strong> du {formatDateIso(f.dateEmission)} · {f.designation} ·{' '}
                    {formatMadCents(f.montants.ttcCentimes)} TTC
                  </span>
                  <button type="button" onClick={() => void telecharger(f)}
                    className="inline-flex items-center gap-1 rounded-lg border border-border px-3 py-1 text-xs font-medium text-fg hover:bg-bg-overlay">
                    <Download className="h-3.5 w-3.5" aria-hidden="true" /> PDF
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
    </div>
  );
}

function Ligne({ libelle, valeur }: { libelle: string; valeur: string }) {
  return (
    <div>
      <dt className="text-xs text-fg-subtle">{libelle}</dt>
      <dd className="text-fg">{valeur}</dd>
    </div>
  );
}
