import { AlarmClock, Bot, CheckCircle, Clock, Compass, Eye, FileText, Info, Keyboard, Maximize2, MessageSquare, Monitor, Moon, Sparkles, Sun, Ticket, X } from 'lucide-react';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { MenuChoix } from '../../../components/ui/Menu';
import { KpiCard } from '../../../components/dashboard/KpiCard';
import { etatDelai } from '../../../components/dashboard/delais';
import { JuriRobot } from '../../../components/assistant/JuriRobot';
import { FriseEtapes } from '../../../components/workflow/FriseEtapes';
import { buildDocFilename } from '../../../components/workflow/workflowFilename';
import { STATUT_TICKET_LABELS } from '../../../lib/dashboardLabels';
import { aideStatutTicket } from '../../tickets/textesAideStatut';
import { ETAPES_CREATION } from '../../workflow/aidesParcours';
import { STATUT_LABELS } from '../../../types/ticket';
import { cn } from '../../../lib/utils';
import { Coquille, Curseur } from './Briques';
import { ACTES_DEMO, ACTIONS_DEMO, INDICATEURS_DEMO, LIBELLES_THEME_DEMO, SOCIETE_DEMO, TITRE_TICKETS_DEMO } from './donnees';
import { FilDemo, type ProprietesScene } from './ScenesTicket';

/**
 * Lot L6c (E1) : scenes "generation des statuts et apercu", "JURI" et "theme clair et
 * sombre". Etapes et durees : scenes.ts.
 *
 * L6c E3 : ecrans alignes sur l'application : cartes de documents de l'etape 7
 * (Step7Generation : "Generer", "Generation...", "<fichier> - genere", "Plein ecran",
 * apercu "Apercu - Statuts"), carte "Prochaine action" (textes du statut et badges du delai
 * calcules par les fonctions reelles), choix du theme dans l'en-tete (ThemeToggle), panneau
 * de JURI ; coquille au menu reel de l'employe.
 */

const ETAPES_FRISE = ETAPES_CREATION.map((e) => ({ numero: e.number, libelle: e.label }));
const ICONES_INDICATEURS = [<Ticket key="t" />, <AlarmClock key="a" />, <Clock key="c" />, <MessageSquare key="m" />];

/* ------------------------------------------------- Generation des statuts ---- */
/* 0 : curseur au repos ; 1 : curseur vers "Generer" (statuts) ; 2 : clic, generation en
 * cours ; 3 : acte genere, curseur vers "Plein ecran" ; 4 : clic ; 5 : apercu de l'acte
 * (etat final) ; 6 : fondu de sortie. */

const POSITIONS_STATUTS: [number, number][] = [
  [420, 420],
  [330, 300],
  [330, 300],
  [272, 340],
  [272, 340],
  [272, 340],
  [420, 420],
];

export function SceneStatuts({ etape, statique }: ProprietesScene) {
  const genere = etape >= 3;
  const apercu = etape >= 5;
  const [cx, cy] = POSITIONS_STATUTS[etape] ?? [420, 420];
  return (
    <div className={cn('v-demo-plan', etape === 6 && 'v-demo-sortie')}>
      <Coquille actif={TITRE_TICKETS_DEMO}>
        <div className="absolute inset-0 space-y-3 p-4">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <FilDemo elements={['Accueil', 'Tickets', SOCIETE_DEMO.reference, 'Parcours']} />
              <h2 className="mt-0.5 truncate text-base font-semibold text-heading">{SOCIETE_DEMO.titreTicket}</h2>
            </div>
            <Badge variant="default" dot>
              {STATUT_LABELS.GENERATION_DOCUMENTS}
            </Badge>
          </div>
          <FriseEtapes etapes={ETAPES_FRISE} plusAvancee={7} />
          <div className="grid grid-cols-2 gap-3">
            {ACTES_DEMO.map((a) => {
              const fait = !a.principal || genere;
              const enCours = a.principal && etape === 2;
              return (
                <div key={a.cle} className={cn('overflow-hidden rounded-xl border bg-bg-raised shadow-sm', 'border-border')}>
                  <div className="flex items-center gap-2 border-b border-border bg-bg-overlay px-4 py-2">
                    <FileText className="h-4 w-4 shrink-0 text-fg-muted" aria-hidden="true" />
                    <span className="truncate text-sm font-semibold text-fg">{a.libelle}</span>
                    {a.principal && (
                      <span className="rounded bg-danger-soft px-1.5 py-0.5 text-[10px] font-bold uppercase text-danger">Principal</span>
                    )}
                  </div>
                  <div className="space-y-2 p-3">
                    {fait ? (
                      <>
                        <div className="flex items-center gap-2 rounded-lg border border-success/30 bg-success-soft p-2 text-xs">
                          <CheckCircle className="h-4 w-4 shrink-0 text-success" aria-hidden="true" />
                          <span className="flex-1 truncate text-fg">{buildDocFilename(a.libelle, SOCIETE_DEMO.nom, SOCIETE_DEMO.forme, 1, '')} — généré</span>
                        </div>
                        <Button type="button" size="sm" variant="secondary" icon={<Maximize2 className="h-4 w-4" />} tabIndex={-1}>
                          Plein écran
                        </Button>
                      </>
                    ) : (
                      <Button type="button" size="sm" fullWidth icon={<Sparkles className="h-4 w-4" />} loading={enCours} tabIndex={-1}>
                        {enCours ? 'Génération…' : 'Générer'}
                      </Button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </Coquille>
      {/* Apercu plein ecran : extrait du modele STATUTS_SARL du corpus, rempli des donnees fictives. */}
      <div className={cn('v-demo-voile absolute inset-0 bg-scrim/40', !apercu && 'v-demo-cachee')} />
      <div className={cn('v-demo-feuille absolute inset-x-8 bottom-4 top-4 flex flex-col overflow-hidden rounded-xl bg-bg-raised shadow-card-lifted', !apercu && 'v-demo-feuille-basse')}>
        <div className="flex items-center justify-between gap-3 border-b border-border bg-bg-overlay px-4 py-2">
          <p className="flex items-center gap-2 text-sm font-semibold text-fg">
            <Eye className="h-4 w-4 text-fg-muted" aria-hidden="true" />
            Aperçu — Statuts
          </p>
          <span className="inline-flex h-7 w-7 items-center justify-center text-fg-subtle">
            <X className="h-4 w-4" aria-hidden="true" />
          </span>
        </div>
        <div className="flex-1 overflow-hidden bg-bg-overlay px-8 pt-4">
          <div className="mx-auto max-w-[460px] rounded-sm bg-bg-raised px-8 py-6 text-[10.5px] leading-relaxed text-fg shadow-card">
            <p className="text-center font-bold text-heading">{SOCIETE_DEMO.nom} — SARL</p>
            <p className="text-center uppercase">Société à responsabilité limitée</p>
            <p className="text-center uppercase">Au capital de {SOCIETE_DEMO.capital} dirhams</p>
            <p className="text-center uppercase">Siège social : {SOCIETE_DEMO.siege}</p>
            <p className="mt-3 text-center text-sm font-bold tracking-[0.2em] text-heading">STATUTS</p>
            <p className="mt-3 font-semibold text-heading">ARTICLE 1 — FORME</p>
            <p className="text-justify">
              Il est formé entre les soussignés, propriétaires des parts ci-après créées et de celles qui pourraient l’être ultérieurement, une société à responsabilité limitée qui sera régie par les lois et règlements en vigueur […] ainsi que par les présents statuts.
            </p>
            <p className="mt-2 font-semibold text-heading">ARTICLE 2 — DÉNOMINATION SOCIALE</p>
            <p>La dénomination de la société est : « {SOCIETE_DEMO.nom} » SARL.</p>
            <p className="mt-2 font-semibold text-heading">ARTICLE 3 — OBJET SOCIAL</p>
            <p className="text-justify">
              La société a pour objet, tant au Maroc qu’à l’étranger, directement ou indirectement, pour son compte ou pour le compte de tiers : {SOCIETE_DEMO.objet}.
            </p>
            <p className="mt-2 font-semibold text-heading">ARTICLE 4 — SIÈGE SOCIAL</p>
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
      <span className="inline-flex h-7 w-7 items-center justify-center rounded-md text-fg-muted">
        <Icone className="h-4 w-4" aria-hidden="true" />
      </span>
      <span className={cn('v-demo-vue absolute right-0 top-8 z-20 w-[190px] rounded-lg border border-border bg-bg-raised py-1 shadow-card-lifted', !ouvert && 'v-demo-cachee')}>
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

function TableauDeBordDemo({ theme }: { theme?: { sombre: boolean; ouvert: boolean } }) {
  return (
    <Coquille actif="Tableau de bord" theme={theme ? <ChoixTheme sombre={theme.sombre} ouvert={theme.ouvert} /> : undefined}>
      <div className="absolute inset-0 space-y-3 p-4">
        <div>
          <h2 className="text-lg font-semibold text-heading">Tableau de bord</h2>
          <p className="mt-0.5 truncate text-xs text-fg-muted">
            Bonjour, nadia. Ce qui demande votre attention aujourd’hui : vos tickets en retard ou proches de leur délai, et les échanges de vos clients qui attendent une réponse.
          </p>
        </div>
        <div className="grid grid-cols-4 gap-2.5">
          {INDICATEURS_DEMO.map((k, i) => (
            <KpiCard key={k.label} label={k.label} value={k.valeur} variant={k.variant} icon={ICONES_INDICATEURS[i]} />
          ))}
        </div>
        <Card accent titleAs="h3" title="Prochaine action" description="Ce qu’il faut faire maintenant, ticket par ticket, le délai le plus proche en premier.">
          <ol className="divide-y divide-border">
            {ACTIONS_DEMO.map((a) => {
              const d = etatDelai(a.jours);
              return (
                <li key={a.reference} className="flex items-start justify-between gap-3 py-2">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="truncate text-xs font-semibold text-heading underline decoration-border-hi underline-offset-2">
                        {a.reference} · {a.titre}
                      </span>
                      <Badge variant="neutral" className="text-[10px]">
                        {STATUT_TICKET_LABELS[a.statut]}
                      </Badge>
                    </div>
                    <p className="mt-0.5 truncate text-[11px] text-fg-muted">{aideStatutTicket(a.statut, 'RESPONSABLE').faire}</p>
                  </div>
                  <Badge variant={d.variante}>{d.texte}</Badge>
                </li>
              );
            })}
          </ol>
        </Card>
      </div>
    </Coquille>
  );
}

/* ------------------------------------------------------------------- JURI ---- */
/* 0 : tableau de bord, JURI au bord ; 1 : curseur sur JURI, il sort et sa bulle s'affiche ;
 * 2 : clic, panneau ouvert par-dessus (etat final) ; 3 : fondu de sortie. */

export function SceneJuri({ etape, statique }: ProprietesScene) {
  const deploye = etape >= 1 && etape <= 2;
  const panneau = etape === 2;
  return (
    <div className={cn('v-demo-plan', etape === 3 && 'v-demo-sortie')}>
      <TableauDeBordDemo />
      <div className={cn('v-demo-juri absolute bottom-5 right-1 flex w-14 flex-col items-center', !deploye && 'v-demo-juri-bord')}>
        <span className={cn('v-demo-bulle mb-1.5 w-14 rounded-lg border border-nav-active bg-marine px-1 py-1 text-center text-[11px] font-semibold leading-tight text-nav-fg-hi shadow-card-lifted', etape !== 1 && 'v-demo-cachee')}>
          Besoin d’aide&nbsp;?
        </span>
        <JuriRobot taille={56} className="drop-shadow-sm" />
      </div>
      <div className={cn('v-demo-voile absolute inset-0 bg-scrim/30', !panneau && 'v-demo-cachee')} />
      <div className={cn('v-demo-tiroir absolute inset-y-0 right-0 flex w-[380px] flex-col border-l border-border bg-bg-raised shadow-card-lifted', !panneau && 'v-demo-tiroir-ferme')}>
        <div className="border-b border-border px-5 py-3">
          <p className="text-base font-semibold text-heading">JURI, votre assistant</p>
          <p className="mt-0.5 text-xs text-fg-muted">Aide de l’écran « Tableau de bord »</p>
        </div>
        <div className="flex-1 space-y-4 px-5 py-4">
          <section>
            <p className="text-sm font-semibold text-heading">Sur cet écran</p>
            <ul className="mt-2 space-y-2">
              <li className="rounded-lg border border-border bg-bg p-3">
                <p className="text-sm font-semibold text-fg">Par où commencer ?</p>
                <p className="mt-1 line-clamp-3 text-xs leading-relaxed text-fg-muted">
                  Les quatre indicateurs du haut disent ce qui presse : un ticket « en retard » a dépassé la date limite fixée sur le ticket ou le délai légal d’une démarche du dossier. Traitez d’abord les transferts de dossier en attente, puis les demandes « Non traitées » de vos clients et les réponses « À valider ».
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
                <p className="mt-1 text-xs leading-relaxed text-fg-muted">
                  Tickets en cours dont la date limite du ticket, ou le délai légal d’une démarche restant à accomplir sur le dossier, est dépassé.
                </p>
              </li>
            </ul>
          </section>
          <section>
            <p className="text-sm font-semibold text-heading">Pour aller plus loin</p>
            <div className="mt-2 grid gap-2">
              <Button type="button" size="sm" variant="secondary" fullWidth className="justify-start" icon={<Bot className="h-4 w-4" />} tabIndex={-1}>
                Poser une question au chatbot juridique
              </Button>
              <Button type="button" size="sm" variant="secondary" fullWidth className="justify-start" icon={<Compass className="h-4 w-4" />} tabIndex={-1}>
                Revoir la visite guidée
              </Button>
              <Button type="button" size="sm" variant="secondary" fullWidth className="justify-start" icon={<Keyboard className="h-4 w-4" />} tabIndex={-1}>
                Raccourcis clavier (touche ?)
              </Button>
            </div>
          </section>
        </div>
      </div>
      <Curseur x={etape === 0 ? 430 : 772} y={etape === 0 ? 300 : 452} clic={etape === 2 ? 1 : 0} visible={!statique && etape >= 1 && etape <= 2} />
    </div>
  );
}

/* ------------------------------------------------- Theme clair et sombre ---- */
/* Choix du theme dans l'en-tete, comme dans l'application : 0 : clair, curseur au repos ;
 * 1 : curseur vers le bouton du theme ; 2 : clic, menu ouvert ; 3 : curseur sur "Sombre" ;
 * 4 : clic, theme sombre ; 5 : curseur vers le bouton ; 6 : clic, menu ouvert ; 7 : curseur
 * sur "Clair" ; 8 : clic, theme clair. Animations reduites : les deux themes cote a cote
 * (moitie gauche claire, moitie droite sombre), fixes, menu ferme. */

const BOUTON_THEME: [number, number] = [664, 24];
const CHOIX_SOMBRE: [number, number] = [560, 168];
const CHOIX_CLAIR: [number, number] = [560, 130];
const POSITIONS_THEME: [number, number][] = [[430, 320], BOUTON_THEME, BOUTON_THEME, CHOIX_SOMBRE, CHOIX_SOMBRE, BOUTON_THEME, BOUTON_THEME, CHOIX_CLAIR, CHOIX_CLAIR];

export function SceneTheme({ etape, statique }: ProprietesScene) {
  const sombre = !statique && etape >= 4 && etape <= 7;
  const ouvert = !statique && (etape === 2 || etape === 3 || etape === 6 || etape === 7);
  const [cx, cy] = POSITIONS_THEME[etape] ?? [430, 320];
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
          <span className="absolute bottom-3 left-[160px]">
            <Badge variant="neutral">Thème clair</Badge>
          </span>
          <span data-theme="dark" className="absolute bottom-3 right-4 rounded-full">
            <Badge variant="neutral">Thème sombre</Badge>
          </span>
        </>
      )}
      <Curseur x={cx} y={cy} clic={[2, 4, 6, 8].includes(etape) ? etape : 0} visible={!statique && etape >= 1} />
    </div>
  );
}
