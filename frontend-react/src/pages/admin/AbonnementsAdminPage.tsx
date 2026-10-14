import { useEffect, useState } from 'react';
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
  LIBELLE_MOTIF_ARCHIVAGE,
  LIBELLE_RYTHME,
  type AbonnementAdminVue,
  type ErreurFacturation,
  type SurDevisVue,
} from '../../types/billing';

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
          Une échéance non réglée à sa date archive le cabinet (rien n’est supprimé) ; un nouveau paiement le réactive dans
          l’état exact. Pour un cabinet Sur Devis, le montant est calculé sur les dossiers ouverts dans le mois d’abonnement ;
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
                <span className={l.statut === 'ACTIF' ? 'text-success' : 'text-warning'}>
                  {l.statut === 'ACTIF' ? 'Actif' : `Archivé${l.archiveMotif ? ` (${LIBELLE_MOTIF_ARCHIVAGE[l.archiveMotif] ?? l.archiveMotif})` : ''}`}
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
