import {
  AlarmClock,
  Bot,
  CalendarClock,
  CheckCircle,
  Compass,
  Download,
  Eye,
  EyeOff,
  FileText,
  Inbox,
  Info,
  Keyboard,
  Maximize2,
  Monitor,
  Moon,
  Pencil,
  RefreshCcw,
  Save,
  Search,
  Sparkles,
  Sun,
  Ticket,
  X,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { MenuChoix } from '../../../components/ui/Menu';
import { KpiCard } from '../../../components/dashboard/KpiCard';
import { etatDelai } from '../../../components/dashboard/delais';
import { JuriRobot } from '../../../components/assistant/JuriRobot';
import { FriseEtapes } from '../../../components/workflow/FriseEtapes';
import { EtatTicketParcours, MetaTicketParcours } from '../../../components/workflow/EnTeteParcours';
import { buildDocFilename } from '../../../components/workflow/workflowFilename';
import { STATUT_TICKET_LABELS } from '../../../lib/dashboardLabels';
import { aideStatutTicket } from '../../tickets/textesAideStatut';
import { ETAPES_CREATION } from '../../workflow/aidesParcours';
import { cn } from '../../../lib/utils';
import { Coquille, Curseur, EncadreAide, PageEnTeteDemo } from './Briques';
import { ACTES_DEMO, ACTIONS_DEMO, INDICATEURS_DEMO, LIBELLES_THEME_DEMO, SOCIETE_DEMO, TICKET_OASIS, TITRE_TICKETS_DEMO } from './donnees';
import type { ProprietesScene } from './ScenesTicket';

/**
 * Lot L6c : scenes "generation des statuts et apercu", "JURI" et "theme clair et sombre".
 * Etapes et durees : scenes.ts.
 *
 * L6c E4 : ecrans reels a 1280 x 800 : page du parcours a l'etape 7 (Step7Generation : cartes
 * des documents, "Generer", "Generation...", "<fichier> - genere", "Plein ecran", apercu
 * "Apercu - Statuts"), tableau de bord de l'employe (EmployeeDashboard : en-tete, aide,
 * indicateurs, carte "Prochaine action"), panneau de JURI (Juri.tsx, tiroir largeur md), choix
 * du theme dans l'en-tete (ThemeToggle).
 */

const ETAPES_FRISE = ETAPES_CREATION.map((e) => ({ numero: e.number, libelle: e.label }));
const AIDE_GENERATION = ETAPES_CREATION[6];
const ICONES_INDICATEURS = [<Ticket key="t" />, <AlarmClock key="a" />, <CalendarClock key="c" />, <Inbox key="m" />];

/* ------------------------------------------------- Generation des statuts ---- */
/* 0 : haut de la page du parcours (etape 7) ; 1 : defilement jusqu'aux documents, curseur
 * vers "Generer" (statuts) ; 2 : clic, generation en cours ; 3 : acte genere, curseur vers
 * "Plein ecran" ; 4 : clic ; 5 : apercu de l'acte (etat final) ; 6 : fondu de sortie. */

const HAUT_DOCUMENTS = 250;
const POSITIONS_STATUTS: [number, number][] = [
  [700, 520],
  [508, 540],
  [508, 540],
  [400, 586],
  [400, 586],
  [400, 586],
  [700, 520],
];

function CarteDocument({ libelle, principal, genere, enCours }: { libelle: string; principal: boolean; genere: boolean; enCours: boolean }) {
  return (
    <div className={cn('overflow-hidden rounded-xl border bg-bg-raised shadow-sm', 'border-border')}>
      <div className="flex items-start justify-between gap-2 border-b border-border bg-bg-overlay px-5 py-3">
        <div className="flex min-w-0 items-center gap-2">
          <FileText className="h-4 w-4 flex-shrink-0 text-fg-muted" aria-hidden="true" />
          <h4 className="truncate text-sm font-semibold text-fg">{libelle}</h4>
          {principal && <span className="rounded bg-danger-soft px-1.5 py-0.5 text-[10px] font-bold uppercase text-danger">Principal</span>}
        </div>
      </div>
      <div className="space-y-3 p-5">
        {genere ? (
          <>
            <div className="flex items-center gap-2 rounded-lg border border-success/30 bg-success-soft p-2 text-xs">
              <CheckCircle className="h-4 w-4 text-success" aria-hidden="true" />
              <span className="flex-1 truncate text-fg">{buildDocFilename(libelle, SOCIETE_DEMO.nom, SOCIETE_DEMO.forme, 1, '')} — généré</span>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <Button type="button" variant="secondary" size="sm" icon={<Maximize2 />} tabIndex={-1}>
                Plein écran
              </Button>
              <Button type="button" variant="secondary" size="sm" icon={<Download />} tabIndex={-1}>
                Télécharger (Word)
              </Button>
              <Button type="button" variant="secondary" size="sm" icon={<RefreshCcw />} tabIndex={-1}>
                Régénérer
              </Button>
              <Button type="button" variant="secondary" size="sm" icon={<Pencil />} tabIndex={-1}>
                Éditer
              </Button>
              <Button type="button" size="sm" icon={<CheckCircle />} className="col-span-2" tabIndex={-1}>
                Valider et déposer en Data Room
              </Button>
            </div>
          </>
        ) : (
          <Button type="button" fullWidth icon={<Sparkles />} loading={enCours} tabIndex={-1}>
            {enCours ? 'Génération…' : 'Générer'}
          </Button>
        )}
      </div>
    </div>
  );
}

export function SceneStatuts({ etape, statique }: ProprietesScene) {
  const genere = etape >= 3;
  const apercu = etape >= 5;
  const defilement = statique || etape >= 1 ? HAUT_DOCUMENTS : 0;
  const [cx, cy] = POSITIONS_STATUTS[etape] ?? [700, 520];
  const ticket = { ...TICKET_OASIS, statut: 'GENERATION_DOCUMENTS' as const };
  return (
    <div className={cn('v-demo-plan', etape === 6 && 'v-demo-sortie')}>
      <Coquille actif={TITRE_TICKETS_DEMO} defilement={defilement}>
        <PageEnTeteDemo
          fil={['Accueil', 'Tickets', SOCIETE_DEMO.reference, 'Parcours']}
          titre={SOCIETE_DEMO.titreTicket}
          etat={<EtatTicketParcours ticket={ticket} forme="SARL à plusieurs associés" />}
          description={<MetaTicketParcours ticket={ticket} />}
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
        <FriseEtapes etapes={ETAPES_FRISE} plusAvancee={7} className="mb-4" />
        <EncadreAide titre={`Aide de l’étape « ${AIDE_GENERATION.label} »`} className="mb-4">
          <ul className="space-y-1">
            <li>
              <span className="font-medium text-fg">À faire : </span>
              {AIDE_GENERATION.aide.faire}
            </li>
            <li>
              <span className="font-medium text-fg">Ce qui manque : </span>
              {AIDE_GENERATION.aide.manque}
            </li>
            <li>
              <span className="font-medium text-fg">Ensuite : </span>
              {AIDE_GENERATION.aide.ensuite}
            </li>
          </ul>
        </EncadreAide>
        <section className="space-y-6 rounded-xl border border-border bg-bg-raised p-6 shadow-card">
          <div className="rounded-xl border border-info/30 bg-info-soft p-4 text-sm text-fg">
            <div className="flex items-start gap-3">
              <Sparkles className="mt-0.5 h-5 w-5 flex-shrink-0 text-fg-muted" aria-hidden="true" />
              <div>
                <p className="font-semibold text-heading">Génération des documents — {SOCIETE_DEMO.nom}</p>
                <p className="text-xs text-fg-subtle">
                  Chaque document peut être généré indépendamment. Une fois généré : aperçu, téléchargement, modification et validation. Mise en
                  page conforme, génération automatique sans IA. Les documents validés sont automatiquement déposés dans la Data Room du dossier.
                </p>
              </div>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            {ACTES_DEMO.map((a) => (
              <CarteDocument key={a.cle} libelle={a.libelle} principal={a.principal} genere={!a.principal || genere} enCours={a.principal && etape === 2} />
            ))}
          </div>
        </section>
      </Coquille>
      {/* Apercu plein ecran (fenetre de Step7Generation) : extrait du modele STATUTS_SARL du corpus, donnees fictives. */}
      <div className={cn('v-demo-voile absolute inset-0 bg-scrim/50', !apercu && 'v-demo-cachee')} />
      <div className={cn('v-demo-feuille absolute inset-x-[40px] bottom-[40px] top-[40px] flex flex-col overflow-hidden rounded-xl bg-bg-raised shadow-xl', !apercu && 'v-demo-feuille-basse')}>
        <div className="flex items-center justify-between border-b border-border bg-bg-overlay px-4 py-3">
          <div className="flex items-center gap-2">
            <Eye className="h-4 w-4 text-fg-muted" aria-hidden="true" />
            <h3 className="text-sm font-semibold text-fg">Aperçu — Statuts</h3>
          </div>
          <span className="inline-flex h-8 w-8 items-center justify-center rounded-md text-fg-subtle">
            <X className="h-4 w-4" aria-hidden="true" />
          </span>
        </div>
        <div className="flex-1 overflow-hidden bg-bg-overlay p-4">
          <div className="mx-auto w-[620px] bg-bg-raised px-16 py-12 text-[13px] leading-relaxed text-fg shadow-card">
            <p className="text-center font-bold text-heading">{SOCIETE_DEMO.nom} — SARL</p>
            <p className="text-center uppercase">Société à responsabilité limitée</p>
            <p className="text-center uppercase">Au capital de {SOCIETE_DEMO.capital} dirhams</p>
            <p className="text-center uppercase">Siège social : {SOCIETE_DEMO.siege}</p>
            <p className="mt-5 text-center text-base font-bold tracking-[0.2em] text-heading">STATUTS</p>
            <p className="mt-5 font-semibold text-heading">ARTICLE 1 — FORME</p>
            <p className="text-justify">
              Il est formé entre les soussignés, propriétaires des parts ci-après créées et de celles qui pourraient l’être ultérieurement, une
              société à responsabilité limitée qui sera régie par les lois et règlements en vigueur […] ainsi que par les présents statuts.
            </p>
            <p className="mt-3 font-semibold text-heading">ARTICLE 2 — DÉNOMINATION SOCIALE</p>
            <p>La dénomination de la société est : « {SOCIETE_DEMO.nom} » SARL.</p>
            <p className="mt-3 font-semibold text-heading">ARTICLE 3 — OBJET SOCIAL</p>
            <p className="text-justify">
              La société a pour objet, tant au Maroc qu’à l’étranger, directement ou indirectement, pour son compte ou pour le compte de tiers :{' '}
              {SOCIETE_DEMO.objet}.
            </p>
            <p className="mt-3 font-semibold text-heading">ARTICLE 4 — SIÈGE SOCIAL</p>
            <p>Le siège social est fixé à : {SOCIETE_DEMO.siege}.</p>
          </div>
        </div>
      </div>
      <Curseur x={cx} y={cy} clic={etape === 2 ? 2 : etape === 4 ? 4 : 0} visible={!statique && etape >= 1 && etape <= 4} />
    </div>
  );
}

/* ------------------------------------------------- Tableau de bord (fond) ---- */

/** Choix du theme de l'en-tete (ThemeToggle) : bouton a icone, menu des trois choix. */
function ChoixTheme({ sombre, ouvert }: { sombre: boolean; ouvert: boolean }) {
  const Icone = sombre ? Moon : Sun;
  const icones = [<Monitor key="m" />, <Sun key="s" />, <Moon key="l" />];
  return (
    <span className="relative inline-flex">
      <span className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-fg-muted">
        <Icone className="h-5 w-5" aria-hidden="true" />
      </span>
      <span className={cn('v-demo-vue absolute right-0 top-11 z-40 w-56 rounded-xl border border-border bg-bg-raised py-1 shadow-card-lifted', !ouvert && 'v-demo-cachee')}>
        <p className="px-4 pb-1 pt-2 text-xs font-semibold uppercase tracking-wide text-fg-subtle">Thème</p>
        {LIBELLES_THEME_DEMO.map((l, i) => (
          <MenuChoix key={l} icone={icones[i]} coche={i === (sombre ? 2 : 1)} onSelect={() => undefined}>
            {l}
          </MenuChoix>
        ))}
      </span>
    </span>
  );
}

function TableauDeBordDemo({ theme, juri }: { theme?: { sombre: boolean; ouvert: boolean }; juri?: ReactNode }) {
  return (
    <Coquille actif="Tableau de bord" theme={theme ? <ChoixTheme sombre={theme.sombre} ouvert={theme.ouvert} /> : undefined} juri={juri}>
      <PageEnTeteDemo
        fil={['Accueil']}
        titre="Tableau de bord"
        description="Bonjour, nadia. Ce qui demande votre attention aujourd’hui : vos tickets en retard ou proches de leur délai, et les échanges de vos clients qui attendent une réponse."
        actions={
          <Button type="button" tabIndex={-1}>
            {TITRE_TICKETS_DEMO}
          </Button>
        }
      />
      <EncadreAide titre="Par où commencer ?" className="mb-6">
        <p>
          Les quatre indicateurs du haut disent ce qui presse : un ticket « en retard » a dépassé la date limite fixée sur le ticket ou le délai
          légal d’une démarche du dossier. Traitez d’abord les transferts de dossier en attente, puis les demandes « Non traitées » de vos
          clients et les réponses « À valider ».
        </p>
      </EncadreAide>
      <div className="mb-8 grid grid-cols-4 gap-4">
        {INDICATEURS_DEMO.map((k, i) => (
          <KpiCard key={k.label} label={k.label} value={k.valeur} variant={k.variant} hint={k.hint} icon={ICONES_INDICATEURS[i]} />
        ))}
      </div>
      <Card accent title="Prochaine action" description="Ce qu’il faut faire maintenant, ticket par ticket, le délai le plus proche en premier." className="mb-6">
        <ol className="divide-y divide-border">
          {ACTIONS_DEMO.map((a) => {
            const d = etatDelai(a.jours);
            return (
              <li key={a.reference} className="grid grid-cols-[1fr_auto] gap-3 py-3">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="rounded-sm font-semibold text-heading underline decoration-border-hi underline-offset-2">
                      {a.reference} · {a.titre}
                    </span>
                    <Badge variant="neutral">{STATUT_TICKET_LABELS[a.statut]}</Badge>
                  </div>
                  <p className="mt-1 text-sm leading-relaxed text-fg-muted">{aideStatutTicket(a.statut, 'RESPONSABLE').faire}</p>
                </div>
                <div className="flex flex-col items-end gap-1">
                  <Badge variant={d.variante}>{d.texte}</Badge>
                </div>
              </li>
            );
          })}
        </ol>
      </Card>
    </Coquille>
  );
}

/* ------------------------------------------------------------------- JURI ---- */
/* 0 : tableau de bord, JURI au bord ; 1 : curseur sur JURI, il sort et sa bulle s'affiche ;
 * 2 : clic, panneau ouvert par-dessus (etat final) ; 3 : fondu de sortie. */

export function SceneJuri({ etape, statique }: ProprietesScene) {
  const deploye = etape === 1;
  const panneau = etape === 2;
  const robot = (
    <div className={cn('v-demo-juri absolute bottom-6 right-0 z-30 mr-1 flex w-14 flex-col items-center', !deploye && 'v-demo-juri-bord')}>
      <span
        className={cn(
          'v-demo-bulle absolute bottom-full right-0 mb-1.5 w-14 rounded-lg border border-nav-active bg-marine px-1 py-1 text-center text-[11px] font-semibold leading-tight text-nav-fg-hi shadow-card-lifted',
          !deploye && 'v-demo-cachee',
        )}
      >
        Besoin d’aide&nbsp;?
      </span>
      <JuriRobot taille={56} className="drop-shadow-sm" />
    </div>
  );
  return (
    <div className={cn('v-demo-plan', etape === 3 && 'v-demo-sortie')}>
      <TableauDeBordDemo juri={robot} />
      <div className={cn('v-demo-voile absolute inset-0 bg-scrim/50', !panneau && 'v-demo-cachee')} />
      {/* Panneau de JURI (Drawer largeur md, toute la hauteur) */}
      <div className={cn('v-demo-tiroir absolute inset-y-0 right-0 flex w-full max-w-md flex-col border-l border-border bg-bg-raised text-fg shadow-card-lifted', !panneau && 'v-demo-tiroir-ferme')}>
        <div className="flex items-start justify-between gap-4 border-b border-border px-6 py-4">
          <div className="min-w-0">
            <p className="text-lg font-semibold text-heading">JURI, votre assistant</p>
            <p className="text-sm text-fg-muted">Aide de l’écran « Tableau de bord »</p>
          </div>
          <span className="-mr-2 inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-fg-subtle">
            <X className="h-4 w-4" aria-hidden="true" />
          </span>
        </div>
        <div className="min-h-0 flex-1 space-y-6 overflow-hidden px-6 py-5">
          <section>
            <h3 className="text-sm font-semibold text-heading">Sur cet écran</h3>
            <ul className="mt-2 space-y-3">
              <li className="rounded-lg border border-border bg-bg p-3">
                <p className="text-sm font-semibold text-fg">Par où commencer ?</p>
                <p className="mt-1 text-sm leading-relaxed text-fg-muted">
                  Les quatre indicateurs du haut disent ce qui presse : un ticket « en retard » a dépassé la date limite fixée sur le ticket ou le
                  délai légal d’une démarche du dossier. Traitez d’abord les transferts de dossier en attente, puis les demandes « Non traitées »
                  de vos clients et les réponses « À valider ».
                </p>
                <Button type="button" size="sm" variant="secondary" className="mt-2" tabIndex={-1}>
                  Voir sur la page
                </Button>
              </li>
              <li className="rounded-lg border border-border bg-bg p-3">
                <p className="flex items-center gap-1.5 text-sm font-semibold text-fg">
                  <Info className="h-4 w-4 text-info" aria-hidden="true" />
                  En retard
                </p>
                <p className="mt-1 text-sm leading-relaxed text-fg-muted">
                  Tickets en cours dont la date limite du ticket, ou le délai légal d’une démarche restant à accomplir sur le dossier, est dépassé.
                </p>
              </li>
            </ul>
          </section>
          <section>
            <h3 className="text-sm font-semibold text-heading">Pour aller plus loin</h3>
            <div className="mt-2 grid gap-2">
              <Button type="button" variant="secondary" fullWidth className="justify-start" icon={<Bot />} tabIndex={-1}>
                Poser une question au chatbot juridique
              </Button>
              <Button type="button" variant="secondary" fullWidth className="justify-start" icon={<Compass />} tabIndex={-1}>
                Revoir la visite guidée
              </Button>
              <Button type="button" variant="secondary" fullWidth className="justify-start" icon={<Keyboard />} tabIndex={-1}>
                Raccourcis clavier (touche ?)
              </Button>
              <Button type="button" variant="secondary" fullWidth className="justify-start" icon={<Search />} tabIndex={-1}>
                Palette de commandes (Ctrl + K)
              </Button>
            </div>
          </section>
        </div>
        <div className="flex w-full flex-wrap items-center justify-between gap-2 border-t border-border bg-bg-overlay px-6 py-3">
          <p className="text-xs text-fg-muted">Vous pourrez réafficher JURI depuis le menu de votre compte.</p>
          <Button type="button" variant="ghost" size="sm" icon={<EyeOff />} tabIndex={-1}>
            Masquer JURI
          </Button>
        </div>
      </div>
      <Curseur x={etape === 0 ? 700 : 1254} y={etape === 0 ? 520 : 742} clic={etape === 2 ? 1 : 0} visible={!statique && etape >= 1 && etape <= 2} />
    </div>
  );
}

/* ------------------------------------------------- Theme clair et sombre ---- */
/* Choix du theme dans l'en-tete, comme dans l'application : 0 : clair, curseur au repos ;
 * 1 : curseur vers le bouton du theme ; 2 : clic, menu ouvert ; 3 : curseur sur "Sombre" ;
 * 4 : clic, theme sombre ; 5 : curseur vers le bouton ; 6 : clic, menu ouvert ; 7 : curseur
 * sur "Clair" ; 8 : clic, theme clair. Animations reduites : les deux themes cote a cote
 * (moitie gauche claire, moitie droite sombre), fixes, menu ferme. */

const BOUTON_THEME: [number, number] = [1015, 30];
const CHOIX_SOMBRE: [number, number] = [880, 184];
const CHOIX_CLAIR: [number, number] = [880, 146];
const POSITIONS_THEME: [number, number][] = [[700, 520], BOUTON_THEME, BOUTON_THEME, CHOIX_SOMBRE, CHOIX_SOMBRE, BOUTON_THEME, BOUTON_THEME, CHOIX_CLAIR, CHOIX_CLAIR];

export function SceneTheme({ etape, statique }: ProprietesScene) {
  const sombre = !statique && etape >= 4 && etape <= 7;
  const ouvert = !statique && (etape === 2 || etape === 3 || etape === 6 || etape === 7);
  const [cx, cy] = POSITIONS_THEME[etape] ?? [700, 520];
  return (
    <div className="v-demo-plan" data-partage={statique ? '' : undefined}>
      <div data-theme="light" className="v-demo-couche absolute inset-0">
        <TableauDeBordDemo theme={{ sombre: false, ouvert: ouvert && !sombre }} />
      </div>
      <div data-theme="dark" className={cn('v-demo-couche v-demo-couche-sombre absolute inset-0', !sombre && !statique && 'v-demo-cachee')}>
        <TableauDeBordDemo theme={{ sombre: true, ouvert: ouvert && sombre }} />
      </div>
      {statique && (
        <>
          <span className="v-demo-separation absolute inset-y-0 left-1/2 w-0.5 bg-accent" />
          <span className="absolute bottom-4 left-[260px]">
            <Badge variant="neutral">Thème clair</Badge>
          </span>
          <span data-theme="dark" className="absolute bottom-4 right-6 rounded-full">
            <Badge variant="neutral">Thème sombre</Badge>
          </span>
        </>
      )}
      <Curseur x={cx} y={cy} clic={[2, 4, 6, 8].includes(etape) ? etape : 0} visible={!statique && etape >= 1} />
    </div>
  );
}
