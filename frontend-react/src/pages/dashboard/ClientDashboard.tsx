import { useEffect, useState, type ReactNode } from 'react';
import { ArrowRight, Check, Clock, FileText, Inbox, MessageSquare, Route } from 'lucide-react';
import { Page } from '../../components/layout/Page';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeVariant } from '../../components/ui/Badge';
import { Lien } from '../../components/ui/Lien';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { Message } from '../../components/ui/Message';
import { CardSkeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { KpiCard } from '../../components/dashboard/KpiCard';
import { dataroomService } from '../../services/dataroom.service';
import { dashboardService } from '../../services/dashboard.service';
import { REQUETE_A_FAIRE } from '../../lib/clientCharts';
import { cn } from '../../lib/utils';
import { TYPE_REQUETE_LABELS } from '../../types/dataroom';
import type { DemandeSummary, DepotSummary, DossierBrief, RequeteSummary } from '../../types/dataroom';
import type { DocumentLite, TicketLite } from '../../types/dashboard';
import { useCurrentUser } from '../../store/authStore';

const STATUT_DEMANDE: Record<string, { libelle: string; ton: BadgeVariant }> = {
  NON_TRAITEE: { libelle: 'Non traitée', ton: 'danger' },
  EN_COURS: { libelle: 'En cours', ton: 'warning' },
  TRAITEE: { libelle: 'Traitée', ton: 'success' },
};

/**
 * Lot L6b (E17) : etapes d'une operation telles que le client les lit (statuts du ticket, dans
 * l'ordre du parcours). Seuls les tickets ouverts sont rendus par le serveur (mesTicketsEnCours) ;
 * la cloture figure comme derniere etape a venir.
 */
const ETAPES: Array<{ statut: string; libelle: string }> = [
  { statut: 'CREATION_TICKET', libelle: 'Collecte des informations' },
  { statut: 'GENERATION_DOCUMENTS', libelle: 'Préparation des actes' },
  { statut: 'DEROULEMENT_DEMARCHE', libelle: 'Démarches administratives' },
  { statut: 'CLOTURE_DOSSIER', libelle: 'Clôture' },
];

/**
 * Tableau de bord du CLIENT : l'action attendue de lui (requetes de son conseiller), l'avancement
 * de ses operations, puis l'historique de ses echanges, sur donnees REELLES agregees par dossier
 * (RG-U09 : le client n'a acces qu'a son perimetre).
 *
 * Sources (toutes ouvertes au CLIENT par dossier) : demandes, requetes du conseiller, depots
 * (dataroom), operations en cours et documents recents (GET /dashboards/client).
 *
 * Lot L6b (E17) : les deux anneaux (requetes et demandes par statut, sans chiffres) sont retires :
 * les compteurs et les listes disent la meme chose, lisiblement. Les compteurs "Documents
 * disponibles" (en fait les dix derniers documents) et "Depots effectues" (historique) cedent la
 * place a "Operations en cours", avec l'etape de chacune. Une source en echec est dite, jamais
 * comptee zero (motif 9).
 */
export function ClientDashboard() {
  const user = useCurrentUser();
  const [demandes, setDemandes] = useState<DemandeSummary[]>([]);
  const [requetes, setRequetes] = useState<RequeteSummary[]>([]);
  const [depots, setDepots] = useState<DepotSummary[]>([]);
  const [documents, setDocuments] = useState<DocumentLite[] | null>([]);
  const [operations, setOperations] = useState<TicketLite[] | null>([]);
  const [incomplet, setIncomplet] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    (async () => {
      let echec = false;
      const note = <T,>(repli: T) => () => {
        echec = true;
        return repli;
      };
      // 1) Les dossiers du client (endpoint ouvert au CLIENT) + ses operations et documents
      //    recents (GET /dashboards/client).
      const [dossiers, client] = await Promise.all([
        dataroomService.listDossiers().catch(note([] as DossierBrief[])),
        dashboardService.getClient().catch(() => null),
      ]);
      // 2) Agregation par dossier : demandes, requetes du conseiller, depots. Un appel en echec
      //    est signale (message), la liste correspondante reste partielle.
      const [demandesPer, requetesPer, depotsPer] = await Promise.all([
        Promise.all(dossiers.map((d) => dataroomService.listDemandesByDossier(d.id).catch(note([] as DemandeSummary[])))),
        Promise.all(dossiers.map((d) => dataroomService.listRequetesByDossier(d.id).catch(note([] as RequeteSummary[])))),
        Promise.all(dossiers.map((d) => dataroomService.listDepots(d.id).catch(note([] as DepotSummary[])))),
      ]);
      if (!mounted) return;
      setDemandes(demandesPer.flat());
      setRequetes(requetesPer.flat());
      setDepots(depotsPer.flat());
      setDocuments(client ? (client.mesDocumentsRecents ?? []) : null);
      setOperations(client ? (client.mesTicketsEnCours ?? []) : null);
      setIncomplet(echec);
      setLoading(false);
    })();
    return () => {
      mounted = false;
    };
  }, []);

  const demandesEnCours = demandes.filter((d) => d.statut === 'NON_TRAITEE' || d.statut === 'EN_COURS').length;
  const requetesAFaire = requetes
    .filter((r) => REQUETE_A_FAIRE.includes(r.statut))
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  const aFaire = requetesAFaire.length;
  const demandesRecentes = [...demandes].sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1)).slice(0, 5);
  const depotsRecents = [...depots].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()).slice(0, 5);
  const docs = documents ?? [];

  return (
    <Page
      titre="Mon espace"
      description={`Bonjour, ${user?.email.split('@')[0] ?? 'Client'}. Ce que votre conseiller attend de vous, l’avancement de vos opérations et le suivi de vos échanges avec le cabinet.`}
      action={
        <Lien to="/data-rooms" commeBouton={{ variant: 'primary' }}>
          Ouvrir ma Data Room
        </Lien>
      }
      aide={
        <TexteAide cle="tableau-de-bord-client" titre="Votre espace client">
          <p>
            Commencez par « À faire pour mon conseiller » : ce sont les pièces ou les réponses que votre cabinet attend
            de vous ; rien n’avance sans elles. « Avancement de mes opérations » montre l’étape de chaque opération en
            cours (collecte des informations, préparation des actes, démarches administratives, clôture). Pour poser
            une question au cabinet, envoyez une demande ; tous vos documents se trouvent dans votre Data Room.
          </p>
        </TexteAide>
      }
    >
      {loading ? (
        <SqueletteZone libelle="Chargement de votre espace…" className="grid gap-4 md:grid-cols-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <CardSkeleton key={i} className="h-[6.5rem]" />
          ))}
        </SqueletteZone>
      ) : (
        <div className="space-y-6">
          {incomplet && (
            <Message ton="attention" titre="Certaines informations n’ont pas pu être chargées">
              Les compteurs et les listes ci-dessous peuvent être incomplets. Rechargez la page dans quelques instants.
            </Message>
          )}
          <div className="grid gap-4 md:grid-cols-3">
            <KpiCard
              icon={<Inbox />}
              label="À faire pour mon conseiller"
              value={aFaire}
              variant={aFaire > 0 ? 'warning' : 'default'}
              hint={aFaire > 0 ? 'pièces ou réponses attendues de vous' : 'rien ne vous est demandé'}
              to="/mes-requetes"
            />
            <KpiCard
              icon={<Route />}
              label="Opérations en cours"
              value={operations === null ? null : operations.length}
              hint={operations === null ? 'donnée indisponible pour le moment' : 'menées par votre cabinet'}
            />
            <KpiCard
              icon={<Clock />}
              label="Mes demandes en cours"
              value={demandesEnCours}
              hint="envoyées au cabinet, sans réponse finale"
            />
          </div>

          {/* En tete : ce que le conseiller attend du client */}
          <Card
            accent
            title={
              <span className="inline-flex items-center gap-2">
                À faire pour mon conseiller
                {aFaire > 0 && <Badge variant="warning">{aFaire}</Badge>}
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

          <Card
            title={
              <span className="inline-flex items-center gap-2">
                <Route className="h-4 w-4 text-fg-subtle" aria-hidden="true" />
                Avancement de mes opérations
                <InfoBulle
                  libelle="Aide : avancement de mes opérations"
                  texte="Chaque opération confiée au cabinet (création, modification, dissolution…) passe par quatre étapes : collecte des informations, préparation des actes, démarches administratives auprès des organismes, puis clôture. L’étape en cours est mise en avant."
                />
              </span>
            }
          >
            {operations === null ? (
              <p className="text-sm text-fg-muted">L’avancement de vos opérations n’a pas pu être chargé. Rechargez la page dans quelques instants.</p>
            ) : operations.length === 0 ? (
              <p className="text-sm text-fg-muted">Aucune opération en cours. Quand votre cabinet ouvre une opération pour votre société, son avancement apparaît ici.</p>
            ) : (
              <ul className="divide-y divide-border">
                {operations.map((o) => (
                  <li key={o.id} className="py-3 first:pt-0 last:pb-0">
                    <p className="text-sm font-medium text-fg">
                      {o.titre}
                      {o.reference && <span className="ml-2 font-mono text-xs text-fg-subtle">{o.reference}</span>}
                    </p>
                    <Etapes statut={o.statut} />
                  </li>
                ))}
              </ul>
            )}
          </Card>

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
                  {documents === null
                    ? 'Les documents récents n’ont pas pu être chargés ; ils restent dans votre Data Room.'
                    : 'Aucun document ni dépôt récent. Les documents publiés par votre cabinet et vos dépôts apparaîtront ici.'}
                </p>
              ) : (
                <div className="space-y-4">
                  {docs.length > 0 && <ListeFichiers titre="Documents du cabinet" lignes={docs.slice(0, 5)} />}
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

/** Frise des quatre etapes d'une operation ; l'etape courante est mise en avant. */
function Etapes({ statut }: { statut: string }) {
  const courante = ETAPES.findIndex((e) => e.statut === statut);
  return (
    <ol className="mt-2 grid gap-1.5 sm:grid-cols-4 sm:gap-2" aria-label="Étapes de l’opération">
      {ETAPES.map((e, i) => {
        const faite = courante >= 0 && i < courante;
        const enCours = i === courante;
        return (
          <li
            key={e.statut}
            aria-current={enCours ? 'step' : undefined}
            className={cn(
              'flex items-center gap-2 rounded-md border px-2.5 py-1.5 text-xs',
              enCours && 'border-accent bg-accent-soft font-semibold text-heading',
              faite && 'border-border bg-bg-overlay text-fg-muted',
              !enCours && !faite && 'border-dashed border-border text-fg-subtle',
            )}
          >
            <span
              aria-hidden="true"
              className={cn(
                'flex h-4 w-4 shrink-0 items-center justify-center rounded-full text-[0.6rem] font-semibold',
                enCours && 'bg-accent text-accent-fg',
                faite && 'bg-success text-success-fg',
                !enCours && !faite && 'border border-border-hi',
              )}
            >
              {faite ? <Check className="h-3 w-3" /> : i + 1}
            </span>
            <span className="min-w-0">
              {e.libelle}
              <span className="sr-only">{enCours ? ' (étape en cours)' : faite ? ' (terminée)' : ' (à venir)'}</span>
            </span>
          </li>
        );
      })}
    </ol>
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
