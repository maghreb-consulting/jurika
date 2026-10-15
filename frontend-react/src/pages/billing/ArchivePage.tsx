import { useEffect, useState } from 'react';
import { Download } from 'lucide-react';
import { billingService } from '../../services/billing.service';
import { useCurrentUser } from '../../store/authStore';
import { Page } from '../../components/layout/Page';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Lien } from '../../components/ui/Lien';
import { Message } from '../../components/ui/Message';
import { LIBELLE_MOTIF_ARCHIVAGE, type AbonnementVue } from '../../types/billing';

/**
 * Lot L7 (E5), RG-TAR-07 a RG-TAR-09 : cabinet archive. Les acces sont fermes, rien n'est supprime ;
 * le superviseur se connecte pour souscrire de nouveau (reprise dans l'etat exact, RG-TAR-08),
 * exporter ses donnees ou demander leur suppression definitive (ecrans prevus au lot L8).
 */
export function ArchivePage() {
  const role = useCurrentUser()?.role;
  // La souscription est une operation du superviseur (le serveur la refuse aux autres).
  const superviseur = role === 'SUPERVISEUR' || role === 'SUPER_ADMIN';
  const [abonnement, setAbonnement] = useState<AbonnementVue | null>(null);

  useEffect(() => {
    billingService.getAbonnement().then(setAbonnement).catch(() => setAbonnement(null));
  }, []);

  const motif = abonnement?.archiveMotif ? LIBELLE_MOTIF_ARCHIVAGE[abonnement.archiveMotif] ?? abonnement.archiveMotif : null;

  return (
    <Page
      titre="Cabinet archivé : rien n’est supprimé"
      largeur="etroite"
      description={
        <>
          Le forfait du cabinet est arrêté{motif ? ` (${motif})` : ''}. Dossiers, tickets, actes, pièces, Data Rooms,
          comptes et historique sont conservés tels quels ; les accès des employés et des clients sont fermés.
        </>
      }
      action={
        superviseur ? (
          <Lien to="/app/billing/payment?objet=SOUSCRIPTION" data-testid="archive-souscrire" commeBouton={{ variant: 'primary' }}>
            Souscrire de nouveau
          </Lien>
        ) : undefined
      }
      actions={
        <Lien to="/app/billing" commeBouton={{ variant: 'secondary' }}>
          Factures et paiements
        </Lien>
      }
      aide={
        <TexteAide cle="cabinet-archive" titre="Que pouvez-vous faire ?">
          <p>
            Souscrire de nouveau : le cabinet est réactivé dans l’état exact où vous l’avez laissé, et chacun retrouve son
            accès. Si le volume archivé dépasse le forfait choisi, seules l’ouverture de nouveaux dossiers et l’ajout de
            documents sont bloqués tant que le forfait n’est pas adapté. Vous pourrez aussi exporter toutes vos données ou
            demander leur suppression définitive ; aucune suppression n’a lieu sans cette demande.
          </p>
        </TexteAide>
      }
    >
      <div className="space-y-4" data-testid="archive-page">
        {!superviseur && (
          <Message ton="info">Seul le superviseur du cabinet peut souscrire de nouveau : prévenez-le.</Message>
        )}
        <Card
          title={
            <span className="flex items-center gap-1">
              Vos données
              <InfoBulle
                libelle="Aide : export et suppression des données"
                texte="L’export complet et la demande de suppression définitive arrivent avec le lot « Données personnelles » (L8). En attendant, écrivez à JURIKA pour toute demande : rien ne sera supprimé sans votre demande expresse."
              />
            </span>
          }
          description="Ces deux demandes ne sont pas encore disponibles dans l’application."
        >
          <div className="flex flex-wrap items-center gap-2">
            <Button type="button" variant="secondary" disabled icon={<Download />}>
              Exporter les données
            </Button>
            <Button type="button" variant="secondary" disabled>
              Demander la suppression définitive
            </Button>
          </div>
        </Card>
      </div>
    </Page>
  );
}
