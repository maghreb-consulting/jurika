import { Check, CheckCircle, ChevronDown, ChevronRight, Info, Paperclip, Plus, Save, X } from 'lucide-react';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { Select } from '../../../components/ui/Select';
import { TextField, ZoneTexte } from '../../../components/ui/TextField';
import { FriseDemarches } from '../../../components/tickets/FriseDemarches';
import { TicketCard } from '../../../components/tickets/TicketCard';
import { ChoixVueTickets } from '../../../components/tickets/TicketsHeader';
import { TicketsFilters } from '../../../components/tickets/TicketsFilters';
import { resumeTickets } from '../../../components/tickets/libellesTickets';
import { FriseEtapes } from '../../../components/workflow/FriseEtapes';
import { EtatTicketParcours, MetaTicketParcours } from '../../../components/workflow/EnTeteParcours';
import { AvancementPanel } from '../../tickets/AvancementPanel';
import { COLUMNS, PISTES_KANBAN } from '../../tickets/kanbanColonnes';
import { ETAPES_CREATION } from '../../workflow/aidesParcours';
import { FORME_JURIDIQUE_LABELS, STATUT_LABELS, TICKET_TYPE_LABELS } from '../../../types/ticket';
import { DOCUMENT_TYPE_LABELS } from '../../../types/dataroom';
import { cn } from '../../../lib/utils';
import { ChampFixe, ChampFrappe, Coquille, Curseur, EncadreAide, PageEnTeteDemo } from './Briques';
import { LIBELLE_COCHEE_DEMO, ORDRE_COCHEE_DEMO, SOCIETE_DEMO, TICKETS_DEMO, TICKET_OASIS, TITRE_TICKETS_DEMO, vueDemarchesDemo } from './donnees';

/**
 * Lot L6c : scenes "creation d'un ticket" (accroche) et "frise des demarches".
 * Etapes : voir scenes.ts (durees) ; `etape` est l'etape en cours, `joue` dit si la scene
 * avance (saisie lettre a lettre).
 *
 * L6c E4 : ecrans reels a 1280 x 800 (Briques.tsx, Coquille) : page "Mes tickets"
 * (TicketsPage : en-tete, aide, filtres, colonnes par statut avec les vraies cartes de ticket),
 * tiroir "Nouveau ticket" (NewTicketDrawer, largeur md), page du parcours a l'etape 1
 * (en-tete du parcours, frise des etapes, aide de l'etape, certificat negatif), page du ticket
 * (avancement, frise des demarches, liste des demarches et bouton "Cocher").
 */

export interface ProprietesScene {
  etape: number;
  joue: boolean;
  statique: boolean;
}

const ETAPES_FRISE = ETAPES_CREATION.map((e) => ({ numero: e.number, libelle: e.label }));
const AIDE_DENOMINATION = ETAPES_CREATION[0];
const OPTIONS_TYPE = [{ value: 'CREATION', label: TICKET_TYPE_LABELS.CREATION }];
const OPTIONS_FORME = [{ value: 'SARL', label: FORME_JURIDIQUE_LABELS.SARL }];
const OPTIONS_PRIORITE = [{ value: 'NORMALE', label: 'Normale' }];
const rien = () => undefined;

/* ------------------------------------------------- Creation d'un ticket ---- */
/* 0 : liste, curseur au repos ; 1 : curseur vers "Nouveau ticket" ; 2 : clic, tiroir ;
 * 3 : saisie du titre ; 4 : clic dans "Raison sociale" ; 5 : saisie ; 6 : clic sur
 * "Creer et ouvrir le parcours" (chargement) ; 7 : parcours, etape 1 et son aide (etat
 * final) ; 8 : fondu de sortie. */

const POSITIONS_CREATION: [number, number][] = [
  [700, 520],
  [1110, 126],
  [1110, 126],
  [1050, 422],
  [1050, 630],
  [1050, 630],
  [1170, 768],
  [1170, 768],
  [700, 520],
];

function PageMesTickets() {
  return (
    <>
      <PageEnTeteDemo
        fil={['Accueil', TITRE_TICKETS_DEMO]}
        titre={TITRE_TICKETS_DEMO}
        description={
          <>
            Les opérations juridiques des dossiers dont vous êtes responsable, rangées par statut.
            <span className="mt-0.5 block text-xs text-fg-subtle">{resumeTickets(4, 3, 1)}</span>
          </>
        }
        actions={
          <>
            <ChoixVueTickets view="kanban" onViewChange={rien} />
            <Button type="button" icon={<Plus />} tabIndex={-1}>
              Nouveau ticket
            </Button>
          </>
        }
      />
      <EncadreAide titre="Comment suivre les tickets ?" className="mb-6">
        <p>
          Chaque ticket suit une opération en quatre statuts : création, génération des documents, déroulement de la démarche, clôture.
          Cliquez sur un ticket pour voir ce qu’il faut faire et ce qui manque ; « Ouvrir la page du ticket » réunit tout. Pour une
          nouvelle opération, cliquez sur « Nouveau ticket ».
        </p>
      </EncadreAide>
      <div className="space-y-4">
        <TicketsFilters
          query=""
          onQueryChange={rien}
          filterType=""
          onFilterTypeChange={rien}
          filterPriorite=""
          onFilterPrioriteChange={rien}
          filterStatut=""
          onFilterStatutChange={rien}
        />
        <div className="grid gap-3" style={{ gridTemplateColumns: PISTES_KANBAN }}>
          {COLUMNS.map((col) => {
            const tickets = TICKETS_DEMO.filter((t) => t.statut === col.statut);
            return (
              <section key={col.statut} className={cn('flex min-w-0 flex-col gap-2', col.sortie && 'ml-2 border-l border-border pl-4')}>
                <div className={cn('flex items-center justify-between gap-2 rounded-r-lg border-l-4 bg-bg-raised px-3 py-2 shadow-card', col.accent)}>
                  <h2 className={cn('text-sm font-semibold leading-tight', col.sortie ? 'text-fg-muted' : 'text-heading')}>{STATUT_LABELS[col.statut]}</h2>
                  <span className="shrink-0 rounded-full bg-bg-overlay px-2 py-0.5 text-xs font-semibold tabular-nums text-fg-muted">{tickets.length}</span>
                </div>
                {col.sortie && (
                  <p className="px-1 text-xs leading-snug text-fg-subtle">
                    Sortie du parcours, atteignable depuis les quatre autres statuts — ce n’est pas une cinquième étape.
                  </p>
                )}
                <div className={cn('flex min-h-[120px] flex-col gap-2 rounded-lg border border-dashed p-1.5', col.sortie ? 'border-border bg-bg-overlay/40' : 'border-border-hi')}>
                  {tickets.map((t) => (
                    <TicketCard key={t.id} ticket={t} onClick={rien} />
                  ))}
                  {tickets.length === 0 && <p className="px-2 py-6 text-center text-xs text-fg-subtle">Aucun ticket</p>}
                </div>
              </section>
            );
          })}
        </div>
      </div>
    </>
  );
}

function PageParcoursEtape1() {
  return (
    <>
      <PageEnTeteDemo
        fil={['Accueil', 'Tickets', SOCIETE_DEMO.reference, 'Parcours']}
        titre={SOCIETE_DEMO.titreTicket}
        etat={<EtatTicketParcours ticket={TICKET_OASIS} forme="SARL à plusieurs associés" />}
        description={<MetaTicketParcours ticket={TICKET_OASIS} />}
        actions={
          <>
            <span className="flex items-center gap-1 text-xs text-fg-muted">
              Enregistrement automatique de la saisie
              <Info className="h-4 w-4" aria-hidden="true" />
            </span>
            <Button type="button" variant="secondary" icon={<Save />} tabIndex={-1}>
              Enregistrer et quitter
            </Button>
            <Button type="button" variant="danger" icon={<X />} tabIndex={-1}>
              Annuler le ticket
            </Button>
          </>
        }
      />
      <FriseEtapes etapes={ETAPES_FRISE} plusAvancee={1} className="mb-4" />
      <EncadreAide titre={`Aide de l’étape « ${AIDE_DENOMINATION.label} »`} className="mb-4">
        <ul className="space-y-1">
          <li>
            <span className="font-medium text-fg">À faire : </span>
            {AIDE_DENOMINATION.aide.faire}
          </li>
          <li>
            <span className="font-medium text-fg">Ce qui manque : </span>
            {AIDE_DENOMINATION.aide.manque}
          </li>
          <li>
            <span className="font-medium text-fg">Ensuite : </span>
            {AIDE_DENOMINATION.aide.ensuite}
          </li>
        </ul>
      </EncadreAide>
      <section className="rounded-xl border border-border bg-bg-raised p-6 shadow-card">
        <div className="mb-1 flex items-center gap-2">
          <h3 className="text-lg font-bold text-fg">Certificat négatif</h3>
          <span className="rounded bg-info-soft px-2 py-0.5 text-xs font-medium text-info">Lecture automatique proposée</span>
        </div>
        <p className="mb-4 text-xs text-fg-subtle">
          Saisissez les informations du certificat négatif. Vous pouvez aussi importer le certificat (PDF ou image) : ses informations sont
          lues et proposées dans les champs ci-dessous. La lecture reste une aide : la saisie manuelle est toujours possible.
        </p>
        <div className="mb-4 flex items-center gap-3 rounded-lg border border-success/30 bg-success-soft p-3">
          <CheckCircle className="h-4 w-4 text-success" aria-hidden="true" />
          <div className="flex-1">
            <p className="text-xs font-semibold uppercase text-fg-subtle">Forme juridique</p>
            <p className="text-sm font-medium text-fg">SARL (deux associés ou plus)</p>
          </div>
          <span className="rounded-full bg-info-soft px-2 py-0.5 text-[10px] font-semibold uppercase text-info">Choisie à l’ouverture du ticket</span>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <ChampFixe label="ICE" placeholder="000 000 000 000 000" value="" required />
          <ChampFixe label="Dénomination (raison sociale)" value={SOCIETE_DEMO.nom} required />
        </div>
      </section>
    </>
  );
}

export function SceneCreation({ etape, joue, statique }: ProprietesScene) {
  const tiroir = etape >= 2 && etape <= 6;
  const parcours = etape >= 7;
  const [cx, cy] = POSITIONS_CREATION[etape] ?? [700, 520];
  return (
    <div className={cn('v-demo-plan', etape === 8 && 'v-demo-sortie')}>
      <div className={cn('v-demo-vue absolute inset-0', parcours && 'v-demo-cachee')}>
        <Coquille actif={TITRE_TICKETS_DEMO}>
          <PageMesTickets />
        </Coquille>
      </div>
      <div className={cn('v-demo-vue absolute inset-0', !parcours && 'v-demo-cachee')}>
        <Coquille actif={TITRE_TICKETS_DEMO}>
          <PageParcoursEtape1 />
        </Coquille>
      </div>

      <div className={cn('v-demo-voile absolute inset-0 bg-scrim/50', !tiroir && 'v-demo-cachee')} />
      {/* Tiroir "Nouveau ticket" (NewTicketDrawer : Drawer largeur md, toute la hauteur) */}
      <div className={cn('v-demo-tiroir absolute inset-y-0 right-0 flex w-full max-w-md flex-col border-l border-border bg-bg-raised text-fg shadow-card-lifted', !tiroir && 'v-demo-tiroir-ferme')}>
        <div className="flex items-start justify-between gap-4 border-b border-border px-6 py-4">
          <div className="min-w-0">
            <p className="text-lg font-semibold text-heading">Nouveau ticket</p>
            <p className="text-sm text-fg-muted">Ouvrez le ticket d’une opération juridique : le parcours correspondant démarre aussitôt.</p>
          </div>
          <span className="-mr-2 inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-fg-subtle">
            <X className="h-4 w-4" aria-hidden="true" />
          </span>
        </div>
        <div className="min-h-0 flex-1 space-y-4 overflow-hidden px-6 py-5">
          <EncadreAide titre="Ouvrir un ticket">
            <p>
              Un ticket suit une opération du début à la fin : création du ticket, génération des documents, déroulement de la démarche,
              clôture. Donnez-lui un titre parlant et choisissez le type d’opération. Pour une création ou un import, la société et sa
              Data Room sont créées en même temps. Vous arrivez ensuite sur le parcours, où vous saisissez les données.
            </p>
          </EncadreAide>
          {etape === 3 ? (
            <ChampFrappe label="Titre" texte={SOCIETE_DEMO.titreTicket} joue={joue} required />
          ) : (
            <ChampFixe label="Titre" value={etape >= 4 ? SOCIETE_DEMO.titreTicket : ''} placeholder="Ex : Constitution Atlas Trading" required />
          )}
          <Select label="Type d’opération" value="CREATION" onChange={rien} options={OPTIONS_TYPE} tabIndex={-1} />
          <fieldset className="space-y-3 rounded-lg border border-border bg-bg-overlay p-3">
            <legend className="px-1 text-sm font-semibold text-heading">Société à créer</legend>
            {etape === 5 ? (
              <ChampFrappe label="Raison sociale" texte={SOCIETE_DEMO.denomination} joue={joue} required />
            ) : (
              <ChampFixe label="Raison sociale" value={etape >= 6 ? SOCIETE_DEMO.denomination : ''} placeholder="Ex : ATLAS TRADING SARL" required />
            )}
            <Select label="Forme juridique" value="SARL" onChange={rien} options={OPTIONS_FORME} tabIndex={-1} />
            <p className="text-xs text-fg-muted">Le dossier de la société et sa Data Room seront créés automatiquement.</p>
          </fieldset>
          <Select label="Priorité" value="NORMALE" onChange={rien} options={OPTIONS_PRIORITE} tabIndex={-1} />
          <TextField label="Échéance (facultatif)" type="text" value="" readOnly tabIndex={-1} />
          <ZoneTexte label="Description (facultatif)" value="" rows={3} readOnly tabIndex={-1} />
        </div>
        <div className="flex justify-end gap-2 border-t border-border bg-bg-overlay px-6 py-3">
          <Button type="button" variant="secondary" tabIndex={-1}>
            Annuler
          </Button>
          <Button type="button" loading={etape === 6} tabIndex={-1}>
            Créer et ouvrir le parcours
          </Button>
        </div>
      </div>
      <Curseur x={cx} y={cy} clic={[2, 4, 6].includes(etape) ? etape : 0} visible={!statique && etape >= 1 && etape <= 6} />
    </div>
  );
}

/* ---------------------------------------------------- Frise des demarches ---- */
/* Page du ticket (TicketPage : avancement, frise, demarches). La page defile comme dans le
 * navigateur : 0 : frise, la demarche 19 "Depassee" ; 1 : defilement jusqu'a la liste des
 * demarches ; 2 : curseur sur le justificatif attendu ; 3 : clic, piece choisie ; 4 : curseur
 * sur "Cocher" ; 5 : clic (chargement) ; 6 : demarche cochee ; 7 : retour a la frise, la
 * demarche y est "Accomplie" (etat final) ; 8 : fondu de sortie. */

const HAUT_FRISE = 560;
const HAUT_DEMARCHES = 780;
const DEFILEMENT_FRISE = [HAUT_FRISE, HAUT_DEMARCHES, HAUT_DEMARCHES, HAUT_DEMARCHES, HAUT_DEMARCHES, HAUT_DEMARCHES, HAUT_DEMARCHES, HAUT_FRISE, HAUT_FRISE];
const POSITIONS_FRISE: [number, number][] = [
  [700, 520],
  [700, 520],
  [404, 618],
  [404, 618],
  [530, 658],
  [530, 658],
  [530, 658],
  [700, 520],
  [700, 520],
];

export function SceneFrise({ etape, statique }: ProprietesScene) {
  const cochee = etape >= 6;
  const piece = etape >= 4;
  const vue = vueDemarchesDemo(cochee);
  const defilement = statique ? HAUT_FRISE : (DEFILEMENT_FRISE[etape] ?? HAUT_FRISE);
  const [cx, cy] = POSITIONS_FRISE[etape] ?? [700, 520];
  const [d14, d19] = vue.phases[0].demarches;
  return (
    <div className={cn('v-demo-plan', etape === 8 && 'v-demo-sortie')}>
      <Coquille actif={TITRE_TICKETS_DEMO} defilement={defilement}>
        <PageEnTeteDemo
          fil={['Tickets', SOCIETE_DEMO.reference]}
          titre={SOCIETE_DEMO.titreTicket}
          description={
            <>
              <Badge variant="info">{STATUT_LABELS.DEROULEMENT_DEMARCHE}</Badge>
              <p className="mt-2">
                <span className="font-mono">{SOCIETE_DEMO.reference}</span> — Création. Où en est l’opération, ce qu’il reste à faire et tout
                ce que le ticket contient.
              </p>
            </>
          }
          actions={
            <Button type="button" variant="secondary" tabIndex={-1}>
              Ouvrir le parcours (saisie et génération des actes)
            </Button>
          }
        />
        <div className="space-y-4">
          <AvancementPanel avancement={vue.avancement} phases={vue.phases} />
          <FriseDemarches vue={vue} />
          <Card padding={false}>
            <header className="border-b border-border px-5 py-3">
              <h3 className="text-base font-semibold text-heading">Démarches</h3>
              <p className="mt-0.5 text-sm text-fg-muted">Dans l’ordre du guide. Seules les démarches du statut courant sont actionnables.</p>
            </header>
            <div className="flex items-center gap-2 border-b border-border bg-bg-overlay px-5 py-2.5">
              <ChevronDown className="h-4 w-4 text-fg-subtle" aria-hidden="true" />
              <span className="text-sm font-semibold text-fg">{vue.phases[0].libelle}</span>
              <span className="ml-auto text-xs text-fg-muted">
                {vue.phases[0].traitees} / {vue.phases[0].total}
              </span>
            </div>
            <ul className="divide-y divide-border">
              {/* 14 : accomplie */}
              <li className="flex gap-3 px-5 py-3">
                <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded border border-success bg-success text-bg-raised">
                  <Check className="h-3.5 w-3.5" aria-hidden="true" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="text-sm text-fg">
                    <span className="font-semibold">{d14.ordre}.</span> {d14.libelle}
                  </p>
                  <p className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-fg-muted">
                    <Badge variant="info">Obligatoire</Badge>
                  </p>
                  <p className="mt-1 text-xs text-success">Accomplie le 04/03/2026</p>
                </div>
              </li>
              {/* 19 : a cocher, justificatif attendu (referentiel V24) */}
              <li className="flex gap-3 px-5 py-3">
                <span className={cn('mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded border', cochee ? 'border-success bg-success text-bg-raised' : 'border-border-input')}>
                  {cochee && <Check className="h-3.5 w-3.5" aria-hidden="true" />}
                </span>
                <div className="min-w-0 flex-1">
                  <p className="text-sm text-fg">
                    <span className="font-semibold">{ORDRE_COCHEE_DEMO}.</span> {LIBELLE_COCHEE_DEMO}
                  </p>
                  <p className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-fg-muted">
                    <Badge variant="info">Obligatoire</Badge>
                  </p>
                  {!cochee && (
                    <>
                      <p className="mt-1 text-xs text-fg-subtle">
                        <span className="font-medium text-fg">Délai : </span>
                        {d19.delai}
                      </p>
                      <div className="mt-2 space-y-2">
                        <p className="text-xs text-fg-subtle">
                          <span className="font-medium text-fg">Justificatif attendu : </span>
                          {d19.justificatifsTexte}
                        </p>
                        <div className="flex flex-wrap items-center gap-2">
                          <span
                            className={cn(
                              'inline-flex h-8 items-center gap-1.5 rounded-lg border px-2.5 text-xs font-medium',
                              piece ? 'border-success/40 bg-success-soft text-success' : 'border-border-input bg-bg-raised text-fg',
                            )}
                          >
                            <Paperclip className="h-3.5 w-3.5" aria-hidden="true" />
                            {DOCUMENT_TYPE_LABELS.RECEPISSE_DEPOT}
                          </span>
                          {piece && <span className="truncate text-xs text-success">recepisse-enregistrement-oasis-exemple.pdf</span>}
                        </div>
                      </div>
                      {!piece && (
                        <p className="mt-2 text-xs text-warning">
                          Cochage impossible tant que ces justificatifs ne sont pas fournis : {DOCUMENT_TYPE_LABELS.RECEPISSE_DEPOT}.
                        </p>
                      )}
                      <div className="mt-2 flex flex-wrap gap-2">
                        <span className="mr-2 flex items-center gap-2 self-center text-xs text-fg-muted">
                          <span className="h-4 w-4 rounded-sm border border-border-input" />
                          Visible pour le client
                        </span>
                        <Button type="button" size="sm" variant="secondary" icon={<Check />} loading={etape === 5} disabled={!piece} tabIndex={-1}>
                          Cocher
                        </Button>
                        <span className="self-center text-xs text-fg-muted">Obligatoire dans tous les dossiers : elle ne peut pas être écartée.</span>
                      </div>
                    </>
                  )}
                  {cochee && <p className="mt-1 text-xs text-success">Accomplie le 07/04/2026 · 1 justificatif</p>}
                </div>
              </li>
            </ul>
            <div className="flex items-center gap-2 border-t border-border bg-bg-overlay px-5 py-2.5">
              <ChevronRight className="h-4 w-4 text-fg-subtle" aria-hidden="true" />
              <span className="text-sm font-semibold text-fg">{STATUT_LABELS.CLOTURE_DOSSIER}</span>
              <span className="ml-auto text-xs text-fg-muted">0 / 2</span>
            </div>
          </Card>
        </div>
      </Coquille>
      <Curseur x={cx} y={cy} clic={[3, 5].includes(etape) ? etape : 0} visible={!statique && etape >= 2 && etape <= 5} />
    </div>
  );
}
