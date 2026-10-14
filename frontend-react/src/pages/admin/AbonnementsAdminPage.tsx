import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { adminService } from '../../services/admin.service';
import { billingAdminService } from '../../services/billing.service';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { ErreurFacturationBloc } from '../../components/billing/ErreurFacturation';
import type { AdminWorkspaceRow } from '../../types/admin';
import {
  formatDateIso,
  formatFinIncluse,
  formatMadCents,
  lireErreurFacturation,
  LIBELLE_EVENEMENT_IMPAYE,
  LIBELLE_MOTIF_ARCHIVAGE,
  LIBELLE_RYTHME,
  type AbonnementAdminVue,
  type ErreurFacturation,
  type SurDevisVue,
} from '../../types/billing';
import { jourEnLettres } from '../../lib/impaye';

/** Lot L7 (E8) : statut lisible (actif, suspendu pour impayé, archivé avec son motif). */
function libelleStatut(l: AbonnementAdminVue): string {
  if (l.statut === 'ACTIF') return l.impaye ? 'Actif, échéance impayée' : 'Actif';
  if (l.statut === 'SUSPENDU') return 'Suspendu (impayé)';
  return `Archivé${l.archiveMotif ? ` (${LIBELLE_MOTIF_ARCHIVAGE[l.archiveMotif] ?? l.archiveMotif})` : ''}`;
}

function dh(v: number | null | undefined): string {
  return v === null || v === undefined ? '—' : `${v.toFixed(2).replace('.', ',')} DH`;
}

/**
 * Lot L7 (E5) : cabinets et forfaits, vus du super-admin (abonnement, rythme, periode payee,
 * prochaine echeance, changement a venir). Sur Devis (RG-PLN-05) : montant calcule selon l'usage
 * reel du mois d'abonnement, sur un prix par dossier encore a decider par la direction.
 */
export function AbonnementsAdminPage() {
  const [lignes, setLignes] = useState<AbonnementAdminVue[]>([]);
  const [cabinets, setCabinets] = useState<AdminWorkspaceRow[]>([]);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<ErreurFacturation | null>(null);

  useEffect(() => {
    Promise.all([billingAdminService.abonnements(), adminService.listWorkspaces().catch(() => [] as AdminWorkspaceRow[])])
      .then(([a, c]) => { setLignes(a); setCabinets(c); })
      .catch((err) => setErreur(lireErreurFacturation(err, 'Les abonnements n’ont pas pu être chargés.')))
      .finally(() => setChargement(false));
  }, []);

  const nom = (id: string) => {
    const c = cabinets.find((x) => x.id === id);
    return c ? `${c.name} (${c.code})` : id;
  };
  const sansAbonnement = cabinets.filter((c) => !lignes.some((l) => l.workspaceId === c.id));

  if (chargement) {
    return <div className="flex h-64 items-center justify-center"><Loader2 className="h-6 w-6 animate-spin text-accent" aria-label="Chargement" /></div>;
  }

  return (
    <div className="space-y-6" data-testid="abonnements-admin">
      <header>
        <h1 className="font-heading text-2xl font-semibold text-fg">Abonnements des cabinets</h1>
        <p className="mt-1 text-sm text-fg-muted">Forfait, rythme, période payée et prochaine échéance de chaque cabinet.</p>
      </header>
      <TexteAide cle="admin-abonnements" titre="Lire cet écran">
        <p>
          Une échéance non réglée déclenche un rappel au cabinet le jour même ; 15 jours après l’échéance, sans paiement et
          si le rappel est parti, l’accès est <strong>suspendu</strong> (article 4.4 du contrat) : rien n’est supprimé, seul le
          superviseur se connecte, pour payer, et l’accès est rétabli dès le paiement constaté. Le cabinet n’est{' '}
          <strong>archivé</strong> qu’à l’arrêt du forfait : résiliation à sa date d’effet, par le cabinet ou par l’éditeur
          pour défaut de paiement persistant (article 12.2 : 30 jours après une mise en demeure restée sans effet, à
          enregistrer depuis l’écran Contrats). Un nouveau paiement le réactive dans l’état exact. Pour un cabinet Sur Devis, le montant est calculé sur les dossiers ouverts dans le mois d’abonnement ;
          le prix par dossier reste à fixer par la direction. Pour enregistrer un paiement reçu, passez par l’écran Paiements.
        </p>
      </TexteAide>
      <ErreurFacturationBloc erreur={erreur} />

      {lignes.length === 0 ? (
        <p className="text-sm text-fg-muted">Aucun abonnement enregistré.</p>
      ) : (
        <div className="space-y-3">
          {lignes.map((l) => (
            <article key={l.workspaceId} className="rounded-2xl bg-bg-raised p-5 text-sm shadow-sm" data-testid={`abonnement-${l.workspaceId}`}>
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <h2 className="font-semibold text-fg">{nom(l.workspaceId)}</h2>
                <span className={l.statut === 'ACTIF' && !l.impaye ? 'text-success' : 'text-warning'} data-testid="abonnement-statut">
                  {libelleStatut(l)}
                </span>
              </div>
              <p className="mt-1 text-fg-muted">
                {l.forfaitLibelle}{l.rythme ? `, ${LIBELLE_RYTHME[l.rythme].toLowerCase()}` : ', rythme inconnu'} · année du{' '}
                {formatDateIso(l.engagementDebut)} au {formatFinIncluse(l.engagementFin)} · payé jusqu’au {formatFinIncluse(l.periodeFin)}
              </p>
              {l.prochaineEcheance && (
                <p className="mt-1 text-fg">
                  Prochaine échéance le {formatDateIso(l.prochaineEcheance.debut)} : {l.prochaineEcheance.forfaitLibelle},{' '}
                  {l.prochaineEcheance.montants ? `${formatMadCents(l.prochaineEcheance.montants.ttcCentimes)} TTC` : l.prochaineEcheance.motif}
                </p>
              )}
              {l.changementAVenir && (
                <p className="mt-1 text-warning">
                  Changement à la prochaine échéance : {l.changementAVenir.forfaitLibelle}
                  {l.changementAVenir.rythme ? `, ${LIBELLE_RYTHME[l.changementAVenir.rythme].toLowerCase()}` : ''}.
                </p>
              )}
              {l.impaye && <Impaye l={l} />}
              {l.surDevis && <SurDevis s={l.surDevis} />}
            </article>
          ))}
        </div>
      )}

      {sansAbonnement.length > 0 && (
        <section className="rounded-2xl bg-bg-raised p-5 text-sm shadow-sm">
          <h2 className="mb-2 font-semibold text-fg">Cabinets sans abonnement enregistré</h2>
          <ul className="list-disc pl-6 text-fg-muted">
            {sansAbonnement.map((c) => <li key={c.id}>{c.name} ({c.code}), forfait déclaré : {c.forfait ?? 'aucun'}</li>)}
          </ul>
        </section>
      )}
    </div>
  );
}

/** Lot L7 (E8) : échéance impayée en cours et journal (rappel, suspension, rétablissement). */
function Impaye({ l }: { l: AbonnementAdminVue }) {
  const i = l.impaye;
  if (!i) return null;
  return (
    <div className="mt-3 rounded-lg border border-warning/40 bg-warning/10 p-3" data-testid="abonnement-impaye">
      <p className="flex items-center gap-1 font-medium text-fg">
        Échéance du {jourEnLettres(i.echeance)} non réglée
        <InfoBulle texte={`Rappel au cabinet dès l’échéance, puis suspension ${i.delaiJours} jours après, seulement si le rappel est parti. Un paiement enregistré (écran Paiements) ou une carte confirmée rétablit l’accès aussitôt.`} />
      </p>
      <p className="text-fg-muted">
        {i.suspendu
          ? `Accès suspendu depuis le ${jourEnLettres(i.suspenduLe)} : le superviseur peut se connecter pour payer.`
          : `Suspension prévue le ${jourEnLettres(i.suspensionLe)} sans paiement.`}{' '}
        {i.rappelEnvoyeLe
          ? `Rappel envoyé le ${jourEnLettres(i.rappelEnvoyeLe)}.`
          : 'Rappel non envoyé : vérifiez l’adresse électronique du cabinet et l’envoi des courriels ; sans rappel, l’accès n’est pas suspendu.'}
      </p>
      {i.arretLe ? (
        <p className="text-fg-muted">Résiliation enregistrée : archivage le {jourEnLettres(i.arretLe)}.</p>
      ) : i.suspendu && (
        <p className="text-fg-muted">
          Défaut de paiement persistant : après une mise en demeure restée sans effet 30 jours, la résiliation (article 12.2)
          s’enregistre depuis l’écran <Link to="/admin/contrats" className="text-accent hover:underline">Contrats</Link> ; le
          cabinet est alors archivé à sa date d’effet.
        </p>
      )}
      {l.journalImpaye.length > 0 && (
        <ul className="mt-2 space-y-0.5 text-xs text-fg-subtle" data-testid="abonnement-journal-impaye">
          {l.journalImpaye.map((e) => (
            <li key={e.id}>
              {formatDateIso(e.creeLe)} · {LIBELLE_EVENEMENT_IMPAYE[e.evenement] ?? e.evenement}
              {e.destinataire ? ` (${e.destinataire})` : ''}{e.detail ? ` : ${e.detail}` : ''}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function SurDevis({ s }: { s: SurDevisVue }) {
  return (
    <div className="mt-3 rounded-lg border border-border bg-bg-overlay p-3" data-testid="sur-devis">
      <p className="flex items-center gap-1 font-medium text-fg">
        Sur Devis : calcul selon l’usage réel
        <InfoBulle texte="Montant du mois = dossiers ouverts dans le mois d’abonnement × prix HT par dossier. Repères : le forfait Max ramené à un dossier." />
      </p>
      <p className="text-fg-muted">
        Dossiers ouverts ce mois d’abonnement{s.moisDebut ? ` (du ${formatDateIso(s.moisDebut)} au ${formatFinIncluse(s.moisFin)})` : ''} :{' '}
        <strong>{s.dossiersDuMois}</strong>
      </p>
      <p className="text-fg-muted">Prix HT par dossier : {s.prixParDossierHt === null ? 'à décider par la direction' : dh(s.prixParDossierHt)}</p>
      <p className="text-fg">{s.montantHt !== null ? <>Montant HT du mois : <strong>{dh(s.montantHt)}</strong></> : s.motif}</p>
      <p className="text-xs text-fg-subtle">
        Repères du forfait Max : {dh(s.repereMaxParDossierMensuel)} HT par dossier en paiement mensuel,{' '}
        {dh(s.repereMaxParDossierAnnuel)} HT en paiement annuel.
      </p>
    </div>
  );
}
