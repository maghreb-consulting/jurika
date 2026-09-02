import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  Clock,
  FileText,
  Inbox,
  MessageSquare,
  PieChart,
  UploadCloud,
} from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { ChartCard } from '../../components/dashboard/charts/ChartCard';
import { DonutChart } from '../../components/dashboard/charts/DonutChart';
import { dataroomService } from '../../services/dataroom.service';
import { dashboardService } from '../../services/dashboard.service';
import { statutDemandeColors, statutRequeteColors } from '../../lib/chartTheme';
import {
  REQUETE_A_FAIRE,
  demandesByStatut,
  requetesByStatut,
} from '../../lib/clientCharts';
import { TYPE_REQUETE_LABELS } from '../../types/dataroom';
import type {
  DemandeSummary,
  DepotSummary,
  DossierBrief,
  RequeteSummary,
} from '../../types/dataroom';
import type { DocumentLite } from '../../types/dashboard';
import { useCurrentUser } from '../../store/authStore';

/**
 * Dashboard CLIENT — met en avant L'ACTION ATTENDUE du client (les requetes de
 * son conseiller a traiter) puis l'historique de ses echanges, sur donnees
 * REELLES agregees par dossier (RG-U09 : le client n'a acces qu'a son perimetre).
 *
 * Tout est deja ouvert au CLIENT par dossier :
 *  - Demandes (client -> conseiller) : GET /dataroom/dossiers/{id}/demandes
 *  - Requetes (conseiller -> client) : listRequetesByDossier (direction EMPLOYE_TO_CLIENT)
 *  - Depots                          : listDepots
 *  - Documents recents               : GET /dashboards/client
 * On agrege cote front sur les dossiers du client. Aucun backend modifie.
 */
export function ClientDashboard() {
  const user = useCurrentUser();
  const [demandes, setDemandes] = useState<DemandeSummary[]>([]);
  const [requetes, setRequetes] = useState<RequeteSummary[]>([]);
  const [depots, setDepots] = useState<DepotSummary[]>([]);
  const [documents, setDocuments] = useState<DocumentLite[]>([]);
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
        Promise.all(
          dossiers.map((d) =>
            dataroomService.listDemandesByDossier(d.id).catch(() => [] as DemandeSummary[]),
          ),
        ),
        Promise.all(
          dossiers.map((d) =>
            dataroomService.listRequetesByDossier(d.id).catch(() => [] as RequeteSummary[]),
          ),
        ),
        Promise.all(
          dossiers.map((d) =>
            dataroomService.listDepots(d.id).catch(() => [] as DepotSummary[]),
          ),
        ),
      ]);
      if (!mounted) return;
      setDemandes(demandesPer.flat());
      setRequetes(requetesPer.flat());
      setDepots(depotsPer.flat());
      setDocuments(client?.mesDocumentsRecents ?? []);
      setLoading(false);
    })();
    return () => {
      mounted = false;
    };
  }, []);

  // --- KPIs ---
  const demandesEnCours = demandes.filter(
    (d) => d.statut === 'NON_TRAITEE' || d.statut === 'EN_COURS',
  ).length;
  const requetesAtraiter = requetes.filter((r) => REQUETE_A_FAIRE.includes(r.statut)).length;

  // --- Graphiques (derivations pures) ---
  const demandesParStatut = demandesByStatut(demandes);
  const requetesParStatut = requetesByStatut(requetes);

  // --- Listes ---
  const requetesAFaire = requetes
    .filter((r) => REQUETE_A_FAIRE.includes(r.statut))
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());

  const demandesRecentes = [...demandes]
    .sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
    .slice(0, 5);

  const depotsRecents = [...depots]
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, 5);

  return (
    <div className="space-y-6 p-6">
      <header>
        <p className="text-xs text-fg-subtle">Espace client securise</p>
        <h1 className="font-heading text-2xl font-semibold text-fg">
          Bonjour, {user?.email.split('@')[0] ?? 'Client'}
        </h1>
        <p className="text-sm text-fg-subtle">
          Ce que votre conseiller attend de vous, et le suivi de vos echanges.
        </p>
      </header>

      {loading ? (
        <Card className="p-12 text-center text-fg-subtle">Chargement...</Card>
      ) : (
        <>
          {/* KPIs : rangee de 4 (action attendue mise en avant) */}
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {/* Mis en avant : les requetes du conseiller a traiter */}
            <Card className="border-accent/40 bg-accent/5 p-5">
              <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-lg bg-accent/15">
                <Inbox className="h-6 w-6 text-accent" />
              </div>
              <p className="text-sm font-medium text-fg">Requetes a traiter</p>
              <p className="my-1 text-3xl font-bold text-accent">{requetesAtraiter}</p>
              <p className="text-xs text-fg-subtle">demandes de votre conseiller</p>
            </Card>

            <Card className="p-5">
              <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-lg bg-warning/10">
                <Clock className="h-6 w-6 text-warning" />
              </div>
              <p className="text-sm text-fg-subtle">Mes demandes en cours</p>
              <p className="my-1 text-3xl font-bold text-fg">{demandesEnCours}</p>
              <p className="text-xs text-fg-subtle">non traitees ou en cours</p>
            </Card>

            <Card className="p-5">
              <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-lg bg-accent/10">
                <FileText className="h-6 w-6 text-accent" />
              </div>
              <p className="text-sm text-fg-subtle">Documents disponibles</p>
              <p className="my-1 text-3xl font-bold text-fg">{documents.length}</p>
              <p className="text-xs text-fg-subtle">dans votre Data Room</p>
            </Card>

            <Card className="p-5">
              <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-lg bg-emerald-50">
                <UploadCloud className="h-6 w-6 text-success" />
              </div>
              <p className="text-sm text-fg-subtle">Depots effectues</p>
              <p className="my-1 text-3xl font-bold text-fg">{depots.length}</p>
              <p className="text-xs text-fg-subtle">fichiers transmis au cabinet</p>
            </Card>
          </div>

          {/* Graphiques : 2 donuts sobres */}
          <div className="grid gap-4 lg:grid-cols-2">
            <ChartCard
              title="Requetes de mon conseiller par statut"
              icon={<PieChart className="h-4 w-4" />}
              empty={requetesParStatut.length === 0}
            >
              <DonutChart data={requetesParStatut} colorByKey={statutRequeteColors} />
            </ChartCard>

            <ChartCard
              title="Mes demandes par statut"
              icon={<PieChart className="h-4 w-4" />}
              empty={demandesParStatut.length === 0}
            >
              <DonutChart data={demandesParStatut} colorByKey={statutDemandeColors} />
            </ChartCard>
          </div>

          {/* Liste 1 (EN TETE) : A faire pour mon conseiller */}
          <Card className="p-6">
            <div className="mb-3 flex items-center justify-between gap-2">
              <h3 className="flex items-center gap-2 font-semibold text-fg">
                <Inbox className="h-4 w-4 text-accent" />
                A faire pour mon conseiller
              </h3>
              {requetesAtraiter > 0 && (
                <span className="rounded-full bg-accent/10 px-2 py-0.5 text-xs font-bold text-accent">
                  {requetesAtraiter}
                </span>
              )}
            </div>
            {requetesAFaire.length === 0 ? (
              <p className="text-sm text-fg-subtle">
                Rien a faire pour le moment. Votre conseiller ne vous demande rien.
              </p>
            ) : (
              <ul className="space-y-2">
                {requetesAFaire.slice(0, 5).map((r) => (
                  <li key={r.id} className="rounded-lg border border-border p-3">
                    <div className="mb-1 flex items-center gap-2">
                      <span className="rounded bg-accent/10 px-2 py-0.5 text-[10px] font-semibold text-accent">
                        {r.typeRequete ? TYPE_REQUETE_LABELS[r.typeRequete] : 'Requete'}
                      </span>
                      {r.statut === 'A_COMPLETER' && (
                        <span className="rounded-full bg-amber-50 px-2 py-0.5 text-[10px] font-bold text-amber-700">
                          A completer
                        </span>
                      )}
                    </div>
                    <p className="text-sm font-medium text-fg">{r.sujet}</p>
                    {r.description && (
                      <p className="mt-0.5 line-clamp-1 text-xs text-fg-subtle">{r.description}</p>
                    )}
                  </li>
                ))}
              </ul>
            )}
            <Link to="/mes-requetes" className="mt-4 block">
              <Button variant="secondary" className="w-full">
                <ArrowRight className="mr-2 h-4 w-4" />
                Demandes de mon conseiller
              </Button>
            </Link>
          </Card>

          <div className="grid gap-4 lg:grid-cols-2">
            {/* Liste 2 : Mes demandes recentes (conserve) */}
            <Card className="p-6">
              <h3 className="mb-3 flex items-center gap-2 font-semibold text-fg">
                <MessageSquare className="h-4 w-4 text-violet-600" />
                Mes demandes recentes
              </h3>
              {demandesRecentes.length === 0 ? (
                <p className="text-sm text-fg-subtle">Aucune demande envoyee.</p>
              ) : (
                <ul className="space-y-2">
                  {demandesRecentes.map((dem) => (
                    <li key={dem.id} className="rounded-lg border border-border p-3">
                      <div className="mb-1 flex items-center gap-2">
                        <span
                          className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${
                            dem.statut === 'TRAITEE'
                              ? 'bg-emerald-50 text-emerald-700'
                              : dem.statut === 'EN_COURS'
                                ? 'bg-accent/10 text-accent'
                                : 'bg-rose-50 text-rose-700'
                          }`}
                        >
                          {dem.statut === 'NON_TRAITEE'
                            ? 'Non traitee'
                            : dem.statut === 'EN_COURS'
                              ? 'En cours'
                              : 'Traitee'}
                        </span>
                      </div>
                      <p className="text-sm font-medium text-fg">{dem.sujet}</p>
                      {dem.description && (
                        <p className="mt-0.5 text-xs text-fg-subtle line-clamp-1">
                          {dem.description}
                        </p>
                      )}
                    </li>
                  ))}
                </ul>
              )}
              <Link to="/data-rooms" className="mt-4 block">
                <Button variant="secondary" className="w-full">
                  <ArrowRight className="mr-2 h-4 w-4" />
                  Nouvelle demande
                </Button>
              </Link>
            </Card>

            {/* Liste 3 : Documents et depots recents (pas de graphe depots) */}
            <Card className="p-6">
              <h3 className="mb-3 flex items-center gap-2 font-semibold text-fg">
                <FileText className="h-4 w-4 text-accent" />
                Documents et depots recents
              </h3>
              {documents.length === 0 && depotsRecents.length === 0 ? (
                <p className="text-sm text-fg-subtle">Aucun document ni depot recent.</p>
              ) : (
                <div className="space-y-4">
                  {documents.length > 0 && (
                    <div>
                      <p className="mb-2 text-[11px] font-semibold uppercase tracking-wide text-fg-subtle">
                        Documents
                      </p>
                      <ul className="space-y-2">
                        {documents.slice(0, 5).map((doc) => (
                          <li
                            key={doc.id}
                            className="flex items-center justify-between rounded-lg border border-border p-3"
                          >
                            <div className="min-w-0">
                              <p className="truncate text-sm font-medium text-fg">{doc.title}</p>
                              <p className="truncate text-xs text-fg-subtle">{doc.filename}</p>
                            </div>
                            <span className="ml-3 flex-shrink-0 text-xs text-fg-subtle">
                              {doc.createdAt
                                ? new Date(doc.createdAt).toLocaleDateString('fr-FR')
                                : '—'}
                            </span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                  {depotsRecents.length > 0 && (
                    <div>
                      <p className="mb-2 text-[11px] font-semibold uppercase tracking-wide text-fg-subtle">
                        Mes depots
                      </p>
                      <ul className="space-y-2">
                        {depotsRecents.map((dep) => (
                          <li
                            key={dep.id}
                            className="flex items-center justify-between rounded-lg border border-border p-3"
                          >
                            <div className="min-w-0">
                              <p className="truncate text-sm font-medium text-fg">{dep.title}</p>
                              <p className="truncate text-xs text-fg-subtle">{dep.filename}</p>
                            </div>
                            <span className="ml-3 flex-shrink-0 text-xs text-fg-subtle">
                              {dep.createdAt
                                ? new Date(dep.createdAt).toLocaleDateString('fr-FR')
                                : '—'}
                            </span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              )}
              <Link to="/data-rooms" className="mt-4 block">
                <Button variant="secondary" className="w-full">
                  Acceder a mon Data Room
                </Button>
              </Link>
            </Card>
          </div>
        </>
      )}
    </div>
  );
}
