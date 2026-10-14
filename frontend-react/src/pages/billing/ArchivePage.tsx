import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Archive } from 'lucide-react';
import { billingService } from '../../services/billing.service';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { LIBELLE_MOTIF_ARCHIVAGE, type AbonnementVue } from '../../types/billing';

/**
 * Lot L7 (E5), RG-TAR-07 a RG-TAR-09 : cabinet archive. Les acces sont fermes, rien n'est supprime ;
 * le superviseur se connecte pour souscrire de nouveau (reprise dans l'etat exact, RG-TAR-08),
 * exporter ses donnees ou demander leur suppression definitive (ecrans prevus au lot L8).
 */
export function ArchivePage() {
  const [abonnement, setAbonnement] = useState<AbonnementVue | null>(null);

  useEffect(() => {
    billingService.getAbonnement().then(setAbonnement).catch(() => setAbonnement(null));
  }, []);

  const motif = abonnement?.archiveMotif ? LIBELLE_MOTIF_ARCHIVAGE[abonnement.archiveMotif] ?? abonnement.archiveMotif : null;

  return (
    <div className="mx-auto max-w-2xl space-y-5 p-6" data-testid="archive-page">
      <header className="flex items-start gap-3">
        <Archive className="mt-1 h-7 w-7 text-accent" aria-hidden="true" />
        <div>
          <h1 className="font-heading text-2xl font-semibold text-fg">Cabinet archivé : rien n’est supprimé</h1>
          <p className="mt-1 text-sm text-fg-muted">
            Le forfait du cabinet est arrêté{motif ? ` (${motif})` : ''}. Dossiers, tickets, actes, pièces, Data Rooms,
            comptes et historique sont conservés tels quels ; les accès des employés et des clients sont fermés.
          </p>
        </div>
      </header>
      <TexteAide cle="cabinet-archive" titre="Que pouvez-vous faire ?">
        <p>
          Souscrire de nouveau : le cabinet est réactivé dans l’état exact où vous l’avez laissé, et chacun retrouve son
          accès. Si le volume archivé dépasse le forfait choisi, seules l’ouverture de nouveaux dossiers et l’ajout de
          documents sont bloqués tant que le forfait n’est pas adapté. Vous pourrez aussi exporter toutes vos données ou
          demander leur suppression définitive ; aucune suppression n’a lieu sans cette demande.
        </p>
      </TexteAide>
      <div className="flex flex-wrap items-center gap-3">
        <Link to="/app/billing/payment?objet=SOUSCRIPTION" data-testid="archive-souscrire"
          className="rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-bg hover:bg-accent-hover">
          Souscrire de nouveau
        </Link>
        <Link to="/app/billing" className="rounded-lg border border-border px-4 py-2 text-sm font-semibold text-fg hover:bg-bg-overlay">
          Factures et paiements
        </Link>
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <button type="button" disabled className="cursor-not-allowed rounded-lg border border-border px-4 py-2 text-sm text-fg-subtle">
          Exporter les données
        </button>
        <button type="button" disabled className="cursor-not-allowed rounded-lg border border-border px-4 py-2 text-sm text-fg-subtle">
          Demander la suppression définitive
        </button>
        <InfoBulle texte="L’export complet et la demande de suppression définitive arrivent avec le lot « Données personnelles » (L8). En attendant, écrivez à JURIKA pour toute demande : rien ne sera supprimé sans votre demande expresse." />
      </div>
    </div>
  );
}
