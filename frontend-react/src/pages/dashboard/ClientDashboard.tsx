import { useEffect, useState, type ReactNode } from 'react';
import { ArrowRight, Clock, FileText, Inbox, MessageSquare, PieChart, UploadCloud } from 'lucide-react';
import { Page } from '../../components/layout/Page';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeVariant } from '../../components/ui/Badge';
import { Lien } from '../../components/ui/Lien';
import { TexteAide } from '../../components/ui/Aide';
import { CardSkeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { ChartCard } from '../../components/dashboard/charts/ChartCard';
import { DonutChart } from '../../components/dashboard/charts/DonutChart';
import { KpiCard } from '../../components/dashboard/KpiCard';
import { dataroomService } from '../../services/dataroom.service';
import { dashboardService } from '../../services/dashboard.service';
import { statutDemandeColors, statutRequeteColors } from '../../lib/chartTheme';
import { REQUETE_A_FAIRE, demandesByStatut, requetesByStatut } from '../../lib/clientCharts';
import { TYPE_REQUETE_LABELS } from '../../types/dataroom';
import type { DemandeSummary, DepotSummary, DossierBrief, RequeteSummary } from '../../types/dataroom';
import type { DocumentLite } from '../../types/dashboard';
import { useCurrentUser } from '../../store/authStore';

const STATUT_DEMANDE: Record<string, { libelle: string; ton: BadgeVariant }> = {
  NON_TRAITEE: { libelle: 'Non traitée', ton: 'danger' },
  EN_COURS: { libelle: 'En cours', ton: 'warning' },
  TRAITEE: { libelle: 'Traitée', ton: 'success' },
};

/**
 * Dashboard CLIENT -- met en avant L'ACTION ATTENDUE du client (les requetes de
 * son conseiller a traiter) puis l'historique de ses echanges, sur donnees
 * REELLES agregees par dossier (RG-U09 : le client n'a acces qu'a son perimetre).
 *
 * Tout est deja ouvert au CLIENT par dossier :
 *  - Demandes (client -> conseiller) : GET /dataroom/dossiers/{id}/demandes
 *  - Requetes (conseiller -> client) : listRequetesByDossier (direction EMPLOYE_TO_CLIENT)
 *  - Depots                          : listDepots
 *  - Documents recents               : GET /dashboards/client
 * On agrege cote front sur les dossiers du client. Aucun backend modifie.
 *
 * Lot L6b (E9) : composants de la bibliotheque ; documents recents indisponibles => "--".
 */
export function ClientDashboard() {
  const user = useCurrentUser();
  const [demandes, setDemandes] = useState<DemandeSummary[]>([]);
  const [requetes, setRequetes] = useState<RequeteSummary[]>([]);
  const [depots, setDepots] = useState<DepotSummary[]>([]);
  const [documents, setDocuments] = useState<DocumentLite[] | null>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    (async () => {
      // 1) Les dossiers du client (endpoint ouvert au CLIENT) + ses documents
      //    recents (GET /dashboards/client).
      const [dossiers, client] = await Promise.all([
        dataroomService.listDossiers().catch(() => [] as DossierBrief[]),
        dashboardService.getClient().catch(() => null),
      ]);
      // 2) Agregation par dossier : demandes, requetes du conseiller, depots.
      //    Le CLIENT ne peut pas lister globalement, mais liste celles de SES
      //    dossiers. Chaque appel echoue en silence -> [] (etat vide propre).
      const [demandesPer, requetesPer, depotsPer] = await Promise.all([
        Promise.all(dossiers.map((d) => dataroomService.listDemandesByDossier(d.id).catch(() => [] as DemandeSummary[]))),
        Promise.all(dossiers.map((d) => dataroomService.listRequetesByDossier(d.id).catch(() => [] as RequeteSummary[]))),
        Promise.all(dossiers.map((d) => dataroomService.listDepots(d.id).catch(() => [] as DepotSummary[]))),
      ]);
      if (!mounted) return;
      setDemandes(demandesPer.flat());
      setRequetes(requetesPer.flat());
      setDepots(depotsPer.flat());
      setDocuments(client ? (client.mesDocumentsRecents ?? []) : null);
      setLoading(false);
    })();
    return () => {
      mounted = false;
    };
  }, []);

  // --- KPIs ---
  const demandesEnCours = demandes.filter((d) => d.statut === 'NON_TRAITEE' || d.statut === 'EN_COURS').length;
  const requetesAtraiter = requetes.filter((r) => REQUETE_A_FAIRE.includes(r.statut)).length;

  // --- Graphiques (derivations pures) ---
  const demandesParStatut = demandesByStatut(demandes);
  const requetesParStatut = requetesByStatut(requetes);

  // --- Listes ---
  const requetesAFaire = requetes
    .filter((r) => REQUETE_A_FAIRE.includes(r.statut))
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  const demandesRecentes = [...demandes].sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1)).slice(0, 5);
  const depotsRecents = [...depots].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()).slice(0, 5);
  const docs = documents ?? [];

  return (
    <Page
      titre="Mon espace"
      description={`Bonjour, ${user?.email.split('@')[0] ?? 'Client'}. Ce que votre conseiller attend de vous, et le suivi de vos échanges avec le cabinet.`}
      action={
        <Lien to="/data-rooms" commeBouton={{ variant: 'primary' }}>
          Ouvrir ma Data Room
        </Lien>
      }
      aide={
        <TexteAide cle="tableau-de-bord-client" titre="Votre espace client">
          <p>
            Commencez par « À faire pour mon conseiller » : ce sont les pièces ou les réponses que votre
            cabinet attend de vous. Vos demandes au cabinet et leur avancement suivent. Tous vos
            documents se trouvent dans votre Data Room.
          </p>
        </TexteAide>
      }
    >
      {loading ? (
        <SqueletteZone libelle="Chargement de votre espace…" className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <CardSkeleton key={i} className="h-[6.5rem]" />
          ))}
        </SqueletteZone>
      ) : (
        <div className="space-y-6">
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <KpiCard
              icon={<Inbox />}
              label="Requêtes à traiter"
              value={requetesAtraiter}
              variant={requetesAtraiter > 0 ? 'warning' : 'default'}
              hint="pièces ou réponses attendues par votre conseiller"
            />
            <KpiCard icon={<Clock />} label="Mes demandes en cours" value={demandesEnCours} hint="non traitées ou en cours" />
            <KpiCard
              icon={<FileText />}
              label="Documents disponibles"
              value={documents === null ? null : documents.length}
              hint={documents === null ? 'donnée indisponible pour le moment' : 'dans votre Data Room'}
            />
            <KpiCard icon={<UploadCloud />} label="Dépôts effectués" value={depots.length} hint="fichiers transmis au cabinet" />
          </div>

          {/* Liste 1 (EN TETE) : A faire pour mon conseiller */}
          <Card
            accent
            title={
              <span className="inline-flex items-center gap-2">
                À faire pour mon conseiller
                {requetesAtraiter > 0 && <Badge variant="warning">{requetesAtraiter}</Badge>}
              </span>
            }
          >
            {requetesAFaire.length === 0 ? (
              <p className="text-sm text-fg-muted">Rien à faire pour le moment : votre conseiller ne vous demande rien.</p>
            ) : (
              <ul className="space-y-2">
                {requetesAFaire.slice(0, 5).map((r) => (
                  <Ligne key={r.id}>
                    <div className="mb-1 flex flex-wrap items-center gap-2">
                      <Badge variant="neutral">{r.typeRequete ? TYPE_REQUETE_LABELS[r.typeRequete] : 'Requête'}</Badge>
                      {r.statut === 'A_COMPLETER' && <Badge variant="warning">À compléter</Badge>}
                    </div>
                    <p className="text-sm font-medium text-fg">{r.sujet}</p>
                    {r.description && <p className="mt-0.5 line-clamp-1 text-xs text-fg-muted">{r.description}</p>}
                  </Ligne>
                ))}
              </ul>
            )}
            <Lien to="/mes-requetes" commeBouton={{ variant: 'secondary' }} className="mt-4 w-full">
              <ArrowRight className="h-4 w-4" aria-hidden="true" />
              Répondre aux requêtes du cabinet
            </Lien>
          </Card>

          <div className="grid gap-4 lg:grid-cols-2">
            <ChartCard title="Requêtes du cabinet par statut" icon={<PieChart />} empty={requetesParStatut.length === 0}>
              <DonutChart data={requetesParStatut} colorByKey={statutRequeteColors} />
            </ChartCard>
            <ChartCard title="Mes demandes par statut" icon={<PieChart />} empty={demandesParStatut.length === 0}>
              <DonutChart data={demandesParStatut} colorByKey={statutDemandeColors} />
            </ChartCard>
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <Card
              title={
                <span className="inline-flex items-center gap-2">
                  <MessageSquare className="h-4 w-4 text-fg-subtle" aria-hidden="true" />
                  Mes demandes récentes
                </span>
              }
            >
              {demandesRecentes.length === 0 ? (
                <p className="text-sm text-fg-muted">Aucune demande envoyée. Pour écrire au cabinet, cliquez sur « Nouvelle demande ».</p>
              ) : (
                <ul className="space-y-2">
                  {demandesRecentes.map((dem) => {
                    const s = STATUT_DEMANDE[dem.statut] ?? { libelle: dem.statut, ton: 'neutral' as BadgeVariant };
                    return (
                      <Ligne key={dem.id}>
                        <Badge variant={s.ton} className="mb-1">
                          {s.libelle}
                        </Badge>
                        <p className="text-sm font-medium text-fg">{dem.sujet}</p>
                        {dem.description && <p className="mt-0.5 line-clamp-1 text-xs text-fg-muted">{dem.description}</p>}
                      </Ligne>
                    );
                  })}
                </ul>
              )}
              <Lien to="/data-rooms" commeBouton={{ variant: 'secondary' }} className="mt-4 w-full">
                <ArrowRight className="h-4 w-4" aria-hidden="true" />
                Nouvelle demande
              </Lien>
            </Card>

            <Card
              title={
                <span className="inline-flex items-center gap-2">
                  <FileText className="h-4 w-4 text-fg-subtle" aria-hidden="true" />
                  Documents et dépôts récents
                </span>
              }
            >
              {docs.length === 0 && depotsRecents.length === 0 ? (
                <p className="text-sm text-fg-muted">
                  Aucun document ni dépôt récent. Les documents publiés par votre cabinet et vos dépôts apparaîtront ici.
                </p>
              ) : (
                <div className="space-y-4">
                  {docs.length > 0 && <ListeFichiers titre="Documents" lignes={docs.slice(0, 5)} />}
                  {depotsRecents.length > 0 && <ListeFichiers titre="Mes dépôts" lignes={depotsRecents} />}
                </div>
              )}
              <Lien to="/data-rooms" commeBouton={{ variant: 'secondary' }} className="mt-4 w-full">
                Accéder à ma Data Room
              </Lien>
            </Card>
          </div>
        </div>
      )}
    </Page>
  );
}

function Ligne({ children }: { children: ReactNode }) {
  return <li className="rounded-lg border border-border p-3">{children}</li>;
}

function ListeFichiers({ titre, lignes }: { titre: string; lignes: { id: string; title: string; filename: string; createdAt: string }[] }) {
  return (
    <div>
      <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-fg-muted">{titre}</h3>
      <ul className="space-y-2">
        {lignes.map((f) => (
          <li key={f.id} className="flex items-center justify-between gap-3 rounded-lg border border-border p-3">
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-fg">{f.title}</p>
              <p className="truncate text-xs text-fg-muted">{f.filename}</p>
            </div>
            <span className="shrink-0 text-xs tabular-nums text-fg-subtle">
              {f.createdAt ? new Date(f.createdAt).toLocaleDateString('fr-FR') : '--'}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
