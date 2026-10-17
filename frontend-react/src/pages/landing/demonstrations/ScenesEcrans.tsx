import { Bot, Compass, Eye, FileText, Info, Keyboard, Moon, Sun, Ticket, AlarmClock, Clock, MessageSquare } from 'lucide-react';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { KpiCard } from '../../../components/dashboard/KpiCard';
import { JuriRobot } from '../../../components/assistant/JuriRobot';
import { FriseEtapes } from '../../../components/workflow/FriseEtapes';
import { ETAPES_CREATION } from '../../workflow/aidesParcours';
import { cn } from '../../../lib/utils';
import { Coquille, Curseur } from './Briques';
import { ACTES_DEMO, ACTIONS_DEMO, INDICATEURS_DEMO, SOCIETE_DEMO } from './donnees';
import type { ProprietesScene } from './ScenesTicket';

/**
 * Lot L6c (E1) : scenes "generation des statuts et apercu", "JURI" et "theme clair et
 * sombre". Etapes et durees : scenes.ts.
 */

const ETAPES_FRISE = ETAPES_CREATION.map((e) => ({ numero: e.number, libelle: e.label }));
const ICONES_INDICATEURS = [<Ticket key="t" />, <AlarmClock key="a" />, <Clock key="c" />, <MessageSquare key="m" />];

/* ------------------------------------------------- Generation des statuts ---- */
/* 0 : curseur au repos ; 1 : curseur vers "Generer" (statuts) ; 2 : clic, generation en
 * cours ; 3 : acte genere, curseur vers "Apercu" ; 4 : clic ; 5 : apercu de l'acte (etat
 * final) ; 6 : fondu de sortie. */

const POSITIONS_STATUTS: [number, number][] = [
  [420, 420],
  [716, 318],
  [716, 318],
  [712, 318],
  [712, 318],
  [712, 318],
  [420, 420],
];

export function SceneStatuts({ etape, statique }: ProprietesScene) {
  const genere = etape >= 3;
  const apercu = etape >= 5;
  const [cx, cy] = POSITIONS_STATUTS[etape] ?? [420, 420];
  return (
    <div className={cn('v-demo-plan', etape === 6 && 'v-demo-sortie')}>
      <Coquille actif="Tickets">
        <div className="absolute inset-0 space-y-4 p-5">
          <div>
            <p className="text-xs text-fg-subtle">Tickets / {SOCIETE_DEMO.titreTicket}</p>
            <h2 className="text-lg font-semibold text-heading">Création de SARL : {SOCIETE_DEMO.denomination}</h2>
          </div>
          <FriseEtapes etapes={ETAPES_FRISE} plusAvancee={7} />
          <Card titleAs="h3" title="Actes de la création" description="Chaque acte est produit à partir du modèle du cabinet, puis relu et validé." padding={false}>
            <ul className="divide-y divide-border border-t border-border">
              {ACTES_DEMO.map((a) => {
                const fait = a.cle === 'souscription' || (a.cle === 'statuts' && genere);
                const enCours = a.cle === 'statuts' && etape === 2;
                return (
                  <li key={a.cle} className="flex items-center justify-between gap-3 px-5 py-2.5">
                    <span className="flex min-w-0 items-center gap-2 text-sm font-medium text-fg">
                      <FileText className="h-4 w-4 shrink-0 text-fg-subtle" aria-hidden="true" />
                      <span className="truncate">{a.libelle}</span>
                    </span>
                    <span className="flex shrink-0 items-center gap-2">
                      <Badge variant={fait ? 'success' : 'neutral'} dot>
                        {fait ? 'Généré' : enCours ? 'En cours' : 'À générer'}
                      </Badge>
                      {fait ? (
                        <Button type="button" size="sm" variant="secondary" icon={<Eye className="h-4 w-4" />} tabIndex={-1}>
                          Aperçu
                        </Button>
                      ) : (
                        <Button type="button" size="sm" variant={a.cle === 'statuts' ? 'primary' : 'secondary'} loading={enCours} tabIndex={-1}>
                          Générer
                        </Button>
                      )}
                    </span>
                  </li>
                );
              })}
            </ul>
          </Card>
        </div>
        {/* Apercu de l'acte : extrait du modele STATUTS_SARL du corpus, rempli des donnees fictives. */}
        <div className={cn('v-demo-voile absolute inset-0 bg-scrim/40', !apercu && 'v-demo-cachee')} />
        <div className={cn('v-demo-feuille absolute inset-x-10 bottom-0 top-5 flex flex-col overflow-hidden rounded-t-xl border border-border bg-bg-raised shadow-card-lifted', !apercu && 'v-demo-feuille-basse')}>
          <div className="flex items-center justify-between gap-3 border-b border-border px-4 py-2">
            <p className="text-sm font-semibold text-heading">Aperçu : Statuts</p>
            <Badge variant="info">Modèle du cabinet</Badge>
          </div>
          <div className="v-demo-page flex-1 overflow-hidden bg-bg-overlay px-8 pt-4">
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
      </Coquille>
      <Curseur x={cx} y={cy} clic={etape === 2 ? 2 : etape === 4 ? 4 : 0} visible={!statique && etape >= 1 && etape <= 4} />
    </div>
  );
}

/* ------------------------------------------------- Tableau de bord (fond) ---- */

function TableauDeBordDemo({ bascule }: { bascule?: { sombre: boolean } }) {
  return (
    <Coquille actif="Tableau de bord">
      <div className="absolute inset-0 space-y-4 p-5">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-xl font-semibold text-heading">Tableau de bord</h2>
            <p className="mt-1 max-w-[400px] text-sm text-fg-muted">Bonjour, Nadia. Ce qui demande votre attention aujourd’hui.</p>
          </div>
          {bascule && (
            <div className="flex items-center gap-1 rounded-lg border border-border bg-bg-raised p-1 shadow-card">
              {[
                { cle: 'clair', libelle: 'Clair', icone: <Sun className="h-4 w-4" />, actif: !bascule.sombre },
                { cle: 'sombre', libelle: 'Sombre', icone: <Moon className="h-4 w-4" />, actif: bascule.sombre },
              ].map((b) => (
                <Button
                  key={b.cle}
                  type="button"
                  size="sm"
                  variant="ghost"
                  icon={b.icone}
                  tabIndex={-1}
                  className={cn(b.actif && 'bg-accent-soft text-accent')}
                >
                  {b.libelle}
                </Button>
              ))}
            </div>
          )}
        </div>
        <div className="grid grid-cols-4 gap-3">
          {INDICATEURS_DEMO.map((k, i) => (
            <KpiCard key={k.label} label={k.label} value={k.valeur} variant={k.variant} icon={ICONES_INDICATEURS[i]} />
          ))}
        </div>
        <Card titleAs="h3" title="Prochaine action" description="Pour chaque ticket, ce qu’il faut faire à son étape et le délai qui court." padding={false}>
          <ul className="divide-y divide-border border-t border-border">
            {ACTIONS_DEMO.map((a) => (
              <li key={a.titre} className="flex items-center justify-between gap-3 px-5 py-2.5">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-fg">{a.action}</p>
                  <p className="truncate text-xs text-fg-muted">{a.titre}</p>
                </div>
                <Badge variant={a.ton} dot>
                  {a.echeance}
                </Badge>
              </li>
            ))}
          </ul>
        </Card>
      </div>
    </Coquille>
  );
}

/* ------------------------------------------------------------------- JURI ---- */
/* 0 : tableau de bord, JURI au bord ; 1 : curseur sur JURI, il sort ; 2 : clic, panneau
 * ouvert (etat final) ; 3 : fondu de sortie. */

export function SceneJuri({ etape, statique }: ProprietesScene) {
  const deploye = etape >= 1 && etape <= 2;
  const panneau = etape === 2;
  return (
    <div className={cn('v-demo-plan', etape === 3 && 'v-demo-sortie')}>
      <TableauDeBordDemo />
      <div className={cn('v-demo-juri absolute bottom-5 right-1 flex w-14 flex-col items-center', !deploye && 'v-demo-juri-bord')}>
        <span className={cn('v-demo-bulle mb-1.5 w-14 rounded-lg border border-nav-active bg-marine px-1 py-1 text-center text-[11px] font-semibold leading-tight text-nav-fg-hi shadow-card-lifted', !deploye && 'v-demo-cachee')}>
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
                <p className="mt-1 text-xs leading-relaxed text-fg-muted">
                  Les quatre indicateurs du haut disent ce qui presse : un ticket « en retard » a dépassé la date limite fixée sur le ticket ou le délai légal d’une démarche du dossier.
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
/* 0 : clair, curseur au repos ; 1 : curseur vers "Sombre" ; 2 : clic, theme sombre ;
 * 3 : curseur vers "Clair" ; 4 : clic, theme clair. Animations reduites : les deux themes
 * cote a cote (moitie gauche claire, moitie droite sombre), fixes. */

const BOUTON_SOMBRE: [number, number] = [730, 46];
const BOUTON_CLAIR: [number, number] = [636, 46];

export function SceneTheme({ etape, statique }: ProprietesScene) {
  const sombre = !statique && (etape === 2 || etape === 3);
  const [cx, cy] = etape === 0 ? [430, 320] : etape <= 2 ? BOUTON_SOMBRE : BOUTON_CLAIR;
  return (
    <div className="v-demo-plan" data-partage={statique ? '' : undefined}>
      <div data-theme="light" className="v-demo-couche absolute inset-0">
        <TableauDeBordDemo bascule={{ sombre: false }} />
      </div>
      <div data-theme="dark" className={cn('v-demo-couche v-demo-couche-sombre absolute inset-0', !sombre && !statique && 'v-demo-cachee')}>
        <TableauDeBordDemo bascule={{ sombre: true }} />
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
      <Curseur x={cx} y={cy} clic={etape === 2 ? 2 : etape === 4 ? 4 : 0} visible={!statique && etape >= 1} />
    </div>
  );
}
