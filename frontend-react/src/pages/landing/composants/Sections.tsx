import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import {
  Archive,
  ArrowRight,
  BotMessageSquare,
  BrainCircuit,
  Building2,
  Check,
  ChevronDown,
  CircleAlert,
  Clock,
  Compass,
  DatabaseBackup,
  Eye,
  FileCheck2,
  FileText,
  FolderOpen,
  History,
  KeyRound,
  Layers,
  Lock,
  MessagesSquare,
  MonitorSmartphone,
  PenLine,
  Route,
  ScanLine,
  ScrollText,
  Server,
  ShieldCheck,
  UserCheck,
  type LucideIcon,
} from 'lucide-react';
import { Badge } from '../../../components/ui/Badge';
import { InfoBulle } from '../../../components/ui/Aide';
import { Lien } from '../../../components/ui/Lien';
import { cn } from '../../../lib/utils';
import { mouvementReduit, useMouvementReduit } from '../../../lib/mouvement';
import { STATUT_LABELS } from '../../../types/ticket';
import { adresseContact } from '../../legal/contact';
import { ESSAI, FORFAITS, SOCLE_COMMUN, fonctionsDuForfait, forfaitMinimal, formatPrice } from '../config/pricing';
import {
  ACCROCHE,
  BANDE,
  CNDP_STATUT,
  FAQ,
  FONCTIONNALITES,
  FORMES_COUVERTES,
  HEBERGEMENT,
  IA,
  PARCOURS,
  PILIERS,
  PROBLEME,
  RAPPEL_FINAL,
  ROLES,
  SECURITE,
  type Fonctionnalite,
  type IconeFonction,
  type IconeSecurite,
  type Pilier,
} from '../contenu';
import type { NomCapture } from '../captures';
import type { NomScene } from '../demonstrations/scenes';
import { DemonstrationAnimee } from '../demonstrations/DemonstrationAnimee';
import { CaptureEcran } from './CaptureEcran';
import { EmblemeAnime } from './EmblemeAnime';
import { MotifZellige } from './MotifZellige';
import { Vivante } from './Vivante';

/**
 * Lot L6b (E10, V2) : sections de la vitrine, dans l'ordre de la page.
 *
 * Mise en page restauree de la vitrine d'avant L6b (accroche centree sur fond decoratif,
 * grande capture sous l'accroche, bande des operations, piliers alternes texte et capture,
 * bande marine de l'IA, tarifs avec forfait mis en avant, appel final marine), sur les
 * jetons du systeme de design (aucune couleur en dur, clair et sombre).
 *
 * Contenu : contenu.ts et config/pricing.ts uniquement. Visuels : vraies captures de la
 * plateforme (captures.ts). Mouvement : `data-apparait` (apparition au defilement, voir
 * useApparitions et vitrine.css) ; rien ne bouge si les animations sont reduites.
 *
 * V3 (demande du 2026-10-15) : accroche animee (embleme qui se dessine, titre mot par mot,
 * capture en profondeur), bandeau defilant des parcours sur fond marine a motif de zellige,
 * recit au defilement des fonctionnalites (capture fixe qui change d'ecran), captures
 * vivantes, bascule clair / sombre, cartes de tarifs qui se soulevent, questions qui
 * s'ouvrent en douceur. Transform et opacity seulement (vitrine.css).
 *
 * V4 (demande du 2026-10-16) : courtes videos en boucle (retirees en L6c) dans
 * l'accroche et dans le recit, a la place des captures fixes (la Data Room garde la sienne) ;
 * la bascule clair / sombre de la capture du tableau de bord devient la video du passage
 * du theme clair au theme sombre.
 *
 * L6c (demande du 2026-10-17) : les videos sont remplacees par des demonstrations animees
 * construites en HTML avec les vrais composants (demonstrations/, DemonstrationAnimee),
 * sur des donnees fictives et fixes ; aucune video ni image animee.
 */

/* ------------------------------------------------------------------- Briques */

function Surtitre({ children, ton = 'clair' }: { children: ReactNode; ton?: 'clair' | 'marine' }) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full px-3.5 py-1 text-xs font-semibold uppercase tracking-[0.14em]',
        ton === 'clair' ? 'v-pastille text-accent' : 'border border-nav-border text-nav-active',
      )}
    >
      {children}
    </span>
  );
}

function EnTeteSection({
  id,
  surtitre,
  titre,
  intro,
  alignement = 'centre',
}: {
  id: string;
  surtitre: string;
  titre: string;
  intro?: ReactNode;
  alignement?: 'centre' | 'gauche';
}) {
  return (
    <div className={cn(alignement === 'centre' ? 'mx-auto max-w-3xl text-center' : 'max-w-xl')} data-apparait>
      <Surtitre>{surtitre}</Surtitre>
      <h2 id={`${id}-titre`} className="mt-4 text-3xl font-bold leading-tight tracking-tight text-heading sm:text-4xl">
        {titre}
      </h2>
      {intro && <div className="mt-4 text-lg leading-relaxed text-fg-muted">{intro}</div>}
    </div>
  );
}

function Section({
  id,
  surtitre,
  titre,
  intro,
  fond = 'page',
  children,
  testId,
}: {
  id: string;
  surtitre: string;
  titre: string;
  intro?: ReactNode;
  fond?: 'page' | 'releve';
  children: ReactNode;
  testId?: string;
}) {
  return (
    <section
      id={id}
      aria-labelledby={`${id}-titre`}
      data-testid={testId}
      className={cn('scroll-mt-16 py-20 lg:py-28', fond === 'releve' && 'border-y border-border bg-bg-raised')}
    >
      <div className="mx-auto max-w-6xl px-4 sm:px-6">
        <EnTeteSection id={id} surtitre={surtitre} titre={titre} intro={intro} />
        <div className="mt-14">{children}</div>
      </div>
    </section>
  );
}

function Coche({ className }: { className?: string }) {
  return <Check className={cn('mt-0.5 h-4 w-4 shrink-0 text-success', className)} aria-hidden="true" />;
}

function TuileIcone({ icone: Icone, ton = 'or' }: { icone: LucideIcon; ton?: 'or' | 'marine' }) {
  return (
    <span
      className={cn(
        'flex h-10 w-10 shrink-0 items-center justify-center rounded-lg',
        ton === 'or' ? 'bg-accent-soft text-accent' : 'border border-nav-border text-nav-active',
      )}
    >
      <Icone className="h-5 w-5" aria-hidden="true" />
    </span>
  );
}

/* ---------------------------------------------------------------- Accroche */

/** Mots du titre, chacun dans une boite qui peut glisser (apparition mot par mot). */
function Mots({ texte, depart, accent = false }: { texte: string; depart: number; accent?: boolean }) {
  return (
    <>
      {texte.split(/\s+/).map((mot, i) => (
        <span key={`${mot}-${i}`}>
          <span className={cn('v-mot', accent && 'text-accent')} style={{ '--i': depart + i } as CSSProperties}>
            {mot}
          </span>{' '}
        </span>
      ))}
    </>
  );
}

/**
 * La capture de l'accroche entre en profondeur au chargement si elle est a l'ecran ; sinon
 * (petit ecran), son entree attend qu'elle arrive a l'ecran (animation en pause, puis sans
 * delai). Sans IntersectionObserver, l'animation joue au chargement.
 */
function useEntreeALecran(ref: { current: HTMLElement | null }) {
  useEffect(() => {
    const el = ref.current;
    if (!el || typeof window.IntersectionObserver !== 'function') return undefined;
    if (el.getBoundingClientRect().top < window.innerHeight * 0.85) return undefined;
    el.setAttribute('data-attente', '');
    el.style.animationDelay = '0ms';
    const obs = new window.IntersectionObserver(
      (entrees) => {
        if (!entrees.some((e) => e.isIntersecting)) return;
        el.removeAttribute('data-attente');
        obs.disconnect();
      },
      { rootMargin: '0px 0px -12% 0px', threshold: 0 },
    );
    obs.observe(el);
    return () => {
      obs.disconnect();
      el.removeAttribute('data-attente');
    };
  }, [ref]);
}

/**
 * V4 (demande du 2026-10-16), L6c : demonstration principale de l'accroche, la creation
 * d'un ticket de creation de SARL jusqu'a l'aide de la premiere etape du parcours : le geste
 * central de la plateforme, ouvrir une operation et etre guide.
 */
const DEMO_ACCROCHE: NomScene = 'creation-ticket';

export function Accroche() {
  const motsTitre = ACCROCHE.titre.split(/\s+/).length;
  const profondeur = useRef<HTMLDivElement>(null);
  useEntreeALecran(profondeur);
  return (
    <section id="haut" aria-labelledby="accroche-titre" className="relative scroll-mt-16 overflow-hidden" data-testid="hero">
      <div className="v-fond-accroche" aria-hidden="true" />
      <div className="relative mx-auto max-w-6xl px-4 pb-20 pt-12 text-center sm:px-6 lg:pb-28 lg:pt-16">
        <EmblemeAnime className="mx-auto mb-6" />
        <span className="v-entree v-pastille inline-flex items-center gap-2 rounded-full px-4 py-1.5 text-xs font-semibold uppercase tracking-[0.14em] text-accent" style={{ '--d': '150ms' } as CSSProperties}>
          <span className="h-2 w-2 rounded-full bg-success" aria-hidden="true" />
          {ACCROCHE.surtitre}
        </span>
        <h1
          id="accroche-titre"
          className="mx-auto mt-6 max-w-4xl text-4xl font-bold leading-[1.1] tracking-tight text-heading sm:text-5xl lg:text-6xl"
        >
          <Mots texte={ACCROCHE.titre} depart={0} />
          <br />
          <Mots texte={ACCROCHE.accent1} depart={motsTitre} />
          <Mots texte="et" depart={motsTitre + 1} />
          <Mots texte={ACCROCHE.accent2} depart={motsTitre + 2} accent />
        </h1>
        <p className="v-entree mx-auto mt-6 max-w-3xl text-lg leading-relaxed text-fg-muted" style={{ '--d': '550ms' } as CSSProperties}>
          {ACCROCHE.texte}
        </p>
        <p className="v-entree mx-auto mt-6 inline-flex max-w-2xl items-center gap-2 rounded-lg border border-border bg-bg-raised px-4 py-2 text-left text-sm font-medium text-fg" style={{ '--d': '650ms' } as CSSProperties}>
          <ShieldCheck className="h-4 w-4 shrink-0 text-accent" aria-hidden="true" />
          {ACCROCHE.garantie}
        </p>
        <div className="v-entree mt-8 flex flex-wrap justify-center gap-3" style={{ '--d': '750ms' } as CSSProperties}>
          <Lien to="/essai" commeBouton={{ variant: 'primary', size: 'lg' }} className="v-bouton" data-testid="hero-essai-cta">
            Essayer gratuitement pendant {ESSAI.dureeJours} jours
            <ArrowRight className="v-fleche h-4 w-4" aria-hidden="true" />
          </Lien>
          <Lien href="#fonctionnalites" commeBouton={{ variant: 'secondary', size: 'lg' }} className="v-bouton">
            Découvrir la plateforme
          </Lien>
        </div>
        <p className="v-entree mt-4 text-sm text-fg-subtle" style={{ '--d': '800ms' } as CSSProperties}>
          Sans moyen de paiement, un essai par cabinet.{' '}
          <Lien to="/signup" variante="discret" data-testid="hero-signup-cta">
            Souscrire directement un forfait
          </Lien>
        </p>

        <div className="v-scene mx-auto mt-14 max-w-5xl text-left">
          <div ref={profondeur} className="v-profondeur">
            <Vivante classeContenu="rounded-xl">
              <DemonstrationAnimee nom={DEMO_ACCROCHE} />
            </Vivante>
          </div>
        </div>
      </div>
    </section>
  );
}

/* --------------------------------------------------- Bande des operations */

/**
 * Bandeau des parcours reellement couverts (contenu.ts, PARCOURS : CDC 16.1 et types de
 * tickets de l'application) : il defile lentement (transform), s'arrete au survol ou au
 * focus ; la copie qui ferme la boucle est cachee aux lecteurs d'ecran. "Reduire les
 * animations" : une seule liste, immobile, sur plusieurs lignes.
 */
function BandeauParcours() {
  const reduit = useMouvementReduit();
  const titres = PARCOURS.map((p) => p.titre);
  const liste = (cachee: boolean) => (
    <ul className="v-defile-liste" aria-hidden={cachee ? 'true' : undefined}>
      {titres.map((t) => (
        <li key={t} className="v-defile-element rounded-full border border-nav-border px-4 py-1.5 text-sm font-medium text-nav-fg-hi">
          {t}
        </li>
      ))}
    </ul>
  );
  return (
    <div className="v-defile mt-5" data-testid="bandeau-parcours" data-defile={reduit ? undefined : ''}>
      <div className="v-defile-rail">
        {liste(false)}
        {!reduit && liste(true)}
      </div>
    </div>
  );
}

export function BandeOperations() {
  return (
    <section aria-label="Opérations couvertes et textes applicables" className="zone-marine v-marine-fond py-10 text-nav-fg">
      <MotifZellige />
      <div className="mx-auto max-w-6xl px-4 text-center sm:px-6">
        <p className="text-sm">{BANDE.titre}</p>
      </div>
      <BandeauParcours />
      <div className="mx-auto max-w-6xl px-4 text-center sm:px-6">
        <ul className="mt-5 flex flex-wrap justify-center gap-x-6 gap-y-2 text-xs font-semibold uppercase tracking-[0.12em] text-nav-active">
          {BANDE.textes.map((t) => (
            <li key={t}>{t}</li>
          ))}
        </ul>
      </div>
    </section>
  );
}

/* ------------------------------------------------------ Probleme et solution */

const ICONES_ROLE: Record<string, LucideIcon> = {
  Digitaliser: MonitorSmartphone,
  Centraliser: Layers,
  Guider: Compass,
  Produire: FileCheck2,
  Conserver: Archive,
  Superviser: Eye,
};

export function ProblemeSolution() {
  return (
    <Section id="solution" surtitre="Le constat et la réponse" titre="Digitaliser et centraliser la gestion juridique des sociétés">
      <div className="grid gap-6 lg:grid-cols-[1fr_2fr]">
        <div className="self-start rounded-2xl border border-border bg-bg-overlay p-7" data-apparait>
          <h3 className="text-lg font-semibold text-heading">{PROBLEME.titre}</h3>
          <ul className="mt-5 space-y-4 text-sm leading-relaxed text-fg-muted">
            {PROBLEME.points.map((p) => (
              <li key={p} className="flex gap-3">
                <CircleAlert className="mt-0.5 h-4 w-4 shrink-0 text-warning" aria-hidden="true" />
                <span>{p}</span>
              </li>
            ))}
          </ul>
        </div>
        <div>
          <h3 className="text-lg font-semibold text-heading" data-apparait>
            Avec JURIKA
          </h3>
          <ul className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {ROLES.map((r) => (
              <li key={r.titre} className="v-carte rounded-xl border border-border bg-bg-raised p-5 shadow-card" data-apparait>
                <TuileIcone icone={ICONES_ROLE[r.titre] ?? Check} />
                <p className="mt-4 font-semibold text-heading">{r.titre}</p>
                <p className="mt-1.5 text-sm leading-relaxed text-fg-muted">{r.texte}</p>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </Section>
  );
}

/* ------------------------------------------------------------ Fonctionnalites */

const ICONES_FONCTION: Record<IconeFonction, LucideIcon> = {
  parcours: Route,
  saisie: ScanLine,
  actes: FileCheck2,
  dataroom: FolderOpen,
  client: UserCheck,
  chatbot: BotMessageSquare,
  suivi: History,
  delais: Clock,
  messagerie: MessagesSquare,
  edition: PenLine,
};

function fonction(cle: IconeFonction): Fonctionnalite {
  const f = FONCTIONNALITES.find((x) => x.icone === cle);
  if (!f) throw new Error(`Fonctionnalite absente de contenu.ts : ${cle}`);
  return f;
}

function ElementFonction({ f, ton = 'clair' }: { f: Fonctionnalite; ton?: 'clair' | 'marine' }) {
  const minimal = f.fonction4_5 ? forfaitMinimal(f.fonction4_5) : null;
  return (
    <li className={cn('flex gap-4', ton === 'marine' && 'v-carte-marine rounded-xl p-5')} data-apparait>
      <TuileIcone icone={ICONES_FONCTION[f.icone]} ton={ton === 'marine' ? 'marine' : 'or'} />
      <div className="min-w-0">
        <h4 className={cn('text-base font-semibold', ton === 'marine' ? 'text-nav-fg-hi' : 'text-heading')}>{f.titre}</h4>
        <p className={cn('mt-1 text-sm leading-relaxed', ton === 'marine' ? 'text-nav-fg' : 'text-fg-muted')}>{f.texte}</p>
        {minimal && (
          <p className="mt-2.5">
            <Badge variant="neutral">À partir du forfait {minimal.label}</Badge>
          </p>
        )}
      </div>
    </li>
  );
}

/**
 * Etapes du recit, dans l'ordre demande (2026-10-15) : tableau de bord, ticket, Data Room,
 * acte, JURI. Chaque etape reprend un pilier de contenu.ts (ou la bande de l'IA) et l'ecran
 * de la plateforme qui le montre.
 */
/** Visuel d'une etape : une demonstration animee (L6c), ou une capture fixe. */
type Visuel = { demo: NomScene } | { capture: NomCapture };

function nomVisuel(v: Visuel): string {
  return 'demo' in v ? v.demo : v.capture;
}

interface Etape {
  id: string;
  /** Libelle court de l'ecran (indicateur d'etape). */
  ecran: string;
  visuel: Visuel;
  surtitre: string;
  titre: string;
  texte: string;
  fonctions: IconeFonction[];
}

function pilier(id: string): Pilier {
  const p = PILIERS.find((x) => x.id === id);
  if (!p) throw new Error(`Pilier absent de contenu.ts : ${id}`);
  return p;
}

function etapeDuPilier(id: string, ecran: string, visuel: Visuel): Etape {
  const p = pilier(id);
  return { id: p.id, ecran, visuel, surtitre: p.surtitre, titre: p.titre, texte: p.texte, fonctions: p.fonctions };
}

/**
 * V4 (demande du 2026-10-16), L6c : une demonstration animee par fonctionnalite :
 * tableau de bord (passage du theme clair au theme sombre), ticket (la frise des demarches
 * qui se coche), acte (generation des statuts et apercu), JURI (il sort du bord et ouvre
 * l'aide). La Data Room garde sa capture fixe.
 */
const ETAPES: Etape[] = [
  etapeDuPilier('pilier-suivi', 'Tableau de bord', { demo: 'theme-clair-sombre' }),
  etapeDuPilier('pilier-parcours', 'Ticket', { demo: 'frise-demarches' }),
  etapeDuPilier('pilier-dataroom', 'Data Room', { capture: 'data-room-clair' }),
  etapeDuPilier('pilier-actes', 'Acte', { demo: 'statuts-apercu' }),
  { id: 'ia', ecran: 'JURI', visuel: { demo: 'juri-aide' }, surtitre: IA.surtitre, titre: IA.titre, texte: IA.texte, fonctions: [IA.fonction] },
];

const TAILLE_RECIT = '(min-width: 1024px) 600px, calc(100vw - 2rem)';

function VisuelEtape({ etape, actif = true }: { etape: Etape; actif?: boolean }) {
  if ('demo' in etape.visuel) {
    return (
      <Vivante classeContenu="rounded-xl">
        <DemonstrationAnimee nom={etape.visuel.demo} actif={actif} />
      </Vivante>
    );
  }
  return <CaptureEcran nom={etape.visuel.capture} sizes={TAILLE_RECIT} />;
}

function BlocEtape({
  etape,
  index,
  fixe,
  refEtape,
}: {
  etape: Etape;
  index: number;
  /** Capture fixe a cote (recit) ; sinon la capture suit son texte a toutes les largeurs. */
  fixe: boolean;
  refEtape: (el: HTMLElement | null) => void;
}) {
  return (
    <article
      id={etape.id}
      ref={refEtape}
      aria-labelledby={`${etape.id}-titre`}
      data-etape={index}
      data-capture={nomVisuel(etape.visuel)}
      className={cn(
        'scroll-mt-24',
        fixe ? 'lg:flex lg:min-h-[78vh] lg:flex-col lg:justify-center lg:py-12' : 'lg:grid lg:grid-cols-2 lg:items-center lg:gap-16',
      )}
    >
      <div>
        <div data-apparait>
          <Surtitre>{etape.surtitre}</Surtitre>
          <h3 id={`${etape.id}-titre`} className="mt-4 text-2xl font-bold leading-snug tracking-tight text-heading sm:text-3xl">
            {etape.titre}
          </h3>
          <p className="mt-4 text-base leading-relaxed text-fg-muted">{etape.texte}</p>
        </div>
        <ul className="mt-8 space-y-6">
          {etape.fonctions.map((cle) => (
            <ElementFonction key={cle} f={fonction(cle)} />
          ))}
        </ul>
      </div>
      {/* Sous 1024 px (ou animations reduites), pas de capture fixe : l'ecran suit son texte. */}
      <div className={cn('mt-10', fixe ? 'lg:hidden' : 'lg:mt-0')} data-apparait>
        <VisuelEtape etape={etape} />
      </div>
    </article>
  );
}

/**
 * Recit au defilement : a partir de 1024 px, la colonne de droite reste fixe (position
 * sticky, defilement natif) pendant que les textes defilent ; elle montre l'ecran de
 * l'etape lue (IntersectionObserver sur une ligne au milieu de la fenetre). Changement
 * d'ecran : fondu et leger glissement (opacity, transform) ; instantane si les animations
 * sont reduites. Seul l'ecran actif est expose aux lecteurs d'ecran. Les captures des
 * autres ecrans sont chargees en differe.
 * "Reduire les animations" : aucun ecran ne change au defilement ; chaque etape montre sa
 * capture a cote de son texte (tout est visible d'emblee).
 */
function RecitFonctionnalites() {
  const reduit = useMouvementReduit();
  const [actif, setActif] = useState(0);
  const etapes = useRef<(HTMLElement | null)[]>([]);

  useEffect(() => {
    if (reduit || typeof window.IntersectionObserver !== 'function') return undefined;
    const obs = new window.IntersectionObserver(
      (entrees) => {
        for (const e of entrees) {
          if (!e.isIntersecting) continue;
          const i = Number((e.target as HTMLElement).dataset.etape);
          if (Number.isFinite(i)) setActif(i);
        }
      },
      { rootMargin: '-50% 0px -50% 0px', threshold: 0 },
    );
    for (const el of etapes.current) if (el) obs.observe(el);
    return () => obs.disconnect();
  }, [reduit]);

  if (reduit) {
    return (
      <div className="mt-16 space-y-24 lg:space-y-32">
        {ETAPES.map((e, i) => (
          <BlocEtape key={e.id} etape={e} index={i} fixe={false} refEtape={(el) => (etapes.current[i] = el)} />
        ))}
      </div>
    );
  }

  function allerA(i: number) {
    etapes.current[i]?.scrollIntoView({ behavior: mouvementReduit() ? 'auto' : 'smooth', block: 'center' });
  }

  return (
    <div className="mt-16 lg:grid lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)] lg:gap-16">
      <div className="space-y-24 lg:space-y-0">
        {ETAPES.map((e, i) => (
          <BlocEtape key={e.id} etape={e} index={i} fixe refEtape={(el) => (etapes.current[i] = el)} />
        ))}
      </div>
      <div className="hidden lg:block">
        <div className="v-recit-fixe">
          <div className="mb-5 flex items-center gap-2">
            <ol className="flex flex-wrap gap-2" aria-label="Écrans de la plateforme">
              {ETAPES.map((e, i) => (
                <li key={e.id}>
                  <button
                    type="button"
                    onClick={() => allerA(i)}
                    aria-current={actif === i ? 'step' : undefined}
                    className={cn(
                      'v-bouton inline-flex h-8 items-center rounded-full border px-3 text-xs font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus',
                      actif === i ? 'border-accent bg-accent-soft text-accent' : 'border-border bg-bg-raised text-fg-muted hover:text-fg',
                    )}
                  >
                    {e.ecran}
                  </button>
                </li>
              ))}
            </ol>
            <InfoBulle
              libelle="Écrans de la plateforme"
              texte="L’écran montré change avec la fonctionnalité que vous lisez. Cliquez sur un écran pour aller directement à sa fonctionnalité."
            />
          </div>
          <div className="v-pile">
            {ETAPES.map((e, i) => (
              <div
                key={e.id}
                className="v-recit-ecran"
                data-recit-ecran={nomVisuel(e.visuel)}
                data-actif={actif === i ? '' : undefined}
                aria-hidden={actif === i ? undefined : 'true'}
                inert={actif !== i}
              >
                <VisuelEtape etape={e} actif={actif === i} />
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

export function Fonctionnalites() {
  return (
    <section id="fonctionnalites" aria-labelledby="fonctionnalites-titre" className="scroll-mt-16 py-20 lg:py-28">
      <div className="mx-auto max-w-6xl px-4 sm:px-6">
        <EnTeteSection
          id="fonctionnalites"
          surtitre="La plateforme"
          titre="Ce que fait la plateforme"
          intro="Un seul flux de travail, du premier rendez-vous à la clôture du dossier. Les fonctions réservées à un forfait sont signalées comme telles."
        />
        <RecitFonctionnalites />
      </div>
    </section>
  );
}

/* -------------------------------------------------------------------- Parcours */

const ETAPES_TICKET = ['CREATION_TICKET', 'GENERATION_DOCUMENTS', 'DEROULEMENT_DEMARCHE', 'CLOTURE_DOSSIER'] as const;

function FriseStatuts() {
  const libelles = ETAPES_TICKET.map((c) => STATUT_LABELS[c]);
  return (
    <div className="rounded-2xl border border-border bg-bg p-6 sm:p-8" data-apparait>
      <p className="text-sm font-semibold text-heading">Chaque opération suit son ticket, dans l’ordre</p>
      <p className="sr-only">Les statuts d’un ticket se suivent dans cet ordre : {libelles.join(', ')}.</p>
      <div className="mt-6 grid gap-6 sm:grid-cols-4 sm:gap-4" aria-hidden="true">
        {libelles.map((l, i) => (
          <div key={l} className="relative flex gap-3 sm:block">
            {i < libelles.length - 1 && <span className="absolute left-4 right-[-1rem] top-4 hidden h-px bg-border-hi sm:block" />}
            <span
              className={cn(
                'relative flex h-8 w-8 shrink-0 items-center justify-center rounded-full border text-sm',
                i === 0 ? 'border-accent bg-accent-soft text-accent' : 'border-border-hi bg-bg-raised text-fg-muted',
              )}
            >
              <Check className="h-4 w-4" />
            </span>
            <span className="block pt-1 text-sm font-medium text-fg sm:mt-3 sm:pt-0">{l}</span>
          </div>
        ))}
      </div>
      <p className="mt-6 border-t border-border pt-4 text-sm text-fg-muted">
        Chaque passage est contrôlé par le serveur ; un ticket peut aussi être annulé, avec son motif.
      </p>
    </div>
  );
}

export function Parcours() {
  return (
    <Section
      id="parcours"
      surtitre="Parcours couverts"
      titre="Les opérations que la plateforme conduit"
      intro={FORMES_COUVERTES}
      fond="releve"
      testId="parcours"
    >
      <FriseStatuts />
      <ul className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {PARCOURS.map((p) => (
          <li key={p.titre} className="v-carte rounded-xl border border-border bg-bg p-5" data-apparait>
            <p className="flex gap-2 font-semibold text-heading">
              <Coche className="text-accent" />
              <span>{p.titre}</span>
            </p>
            <p className="mt-2 text-sm leading-relaxed text-fg-muted">{p.texte}</p>
          </li>
        ))}
      </ul>
      <p className="mt-8 text-center text-sm text-fg-muted">
        Chaque parcours suit les référentiels de démarches du cabinet : démarches, conditions, délais et documents.
      </p>
    </Section>
  );
}

/* -------------------------------------------------------------------- Securite */

const ICONES_SECURITE: Record<IconeSecurite, LucideIcon> = {
  authentification: KeyRound,
  cloisonnement: Building2,
  droits: ShieldCheck,
  journal: ScrollText,
  sauvegarde: DatabaseBackup,
  chiffrement: Lock,
  proprietaire: FolderOpen,
  ia: BrainCircuit,
};

export function Securite() {
  return (
    <Section
      id="securite"
      surtitre="Sécurité et confidentialité"
      titre="Le même niveau de sécurité pour tous les forfaits"
      intro="Ce que la plateforme applique et ce que le contrat d’abonnement garantit, sans différence d’un forfait à l’autre."
      testId="securite"
    >
      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {SECURITE.map((s) => (
          <li key={s.titre} className="v-carte rounded-xl border border-border bg-bg-raised p-5 shadow-card" data-apparait>
            <TuileIcone icone={ICONES_SECURITE[s.icone]} />
            <h3 className="mt-4 text-base font-semibold text-heading">{s.titre}</h3>
            <p className="mt-1.5 text-sm leading-relaxed text-fg-muted">{s.texte}</p>
          </li>
        ))}
      </ul>

      <div className="mt-6 grid gap-4 lg:grid-cols-2">
        <div className="rounded-xl border border-border bg-bg-raised p-6" data-testid="securite-hebergement" data-apparait>
          <div className="flex flex-wrap items-center gap-3">
            <Server className="h-5 w-5 text-accent" aria-hidden="true" />
            <h3 className="text-base font-semibold text-heading">Hébergement</h3>
            <Badge variant="warning" dot>
              {CNDP_STATUT}
            </Badge>
          </div>
          <p className="mt-3 text-sm leading-relaxed text-fg-muted">{HEBERGEMENT}</p>
        </div>
        <div className="rounded-xl border border-border bg-bg-raised p-6" data-apparait>
          <div className="flex items-center gap-3">
            <FileText className="h-5 w-5 text-accent" aria-hidden="true" />
            <h3 className="text-base font-semibold text-heading">Données personnelles</h3>
          </div>
          <p className="mt-3 text-sm leading-relaxed text-fg-muted">
            Pour les données de ses dossiers, le cabinet est responsable du traitement et l’éditeur agit comme
            sous-traitant, selon l’accord de traitement des données du contrat (loi n° 09-08).{' '}
            <Lien to="/cgu" variante="discret">
              Lire le contrat d’abonnement
            </Lien>
          </p>
        </div>
      </div>
    </Section>
  );
}

/* ---------------------------------------------------------------------- Tarifs */

export function Tarifs() {
  const contact = adresseContact();
  return (
    <Section
      id="tarifs"
      surtitre="Tarifs"
      titre="Quatre forfaits, souscrits pour un an"
      fond="releve"
      intro={
        <p>
          Prix hors taxes. Réglez en une fois (paiement annuel) ou chaque mois (paiement mensuel) : le paiement
          annuel revient à deux mois offerts.{' '}
          <InfoBulle
            libelle="Comment se règle un forfait ?"
            texte="Le forfait est souscrit pour un an. Paiement annuel : un seul règlement pour l’année. Paiement mensuel : douze mensualités égales au dixième du prix annuel. Modes de paiement prévus : carte bancaire en ligne, virement, chèque ou espèces ; chaque paiement donne lieu à une facture."
          />
        </p>
      }
    >
      <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-4" data-testid="pricing-grid">
        {FORFAITS.map((forfait) => {
          const surDevis = forfait.prixAnnuelHt === null || forfait.prixMensuelHt === null;
          const professionnel = forfait.id === 'professionnel';
          return (
            <article
              key={forfait.id}
              className={cn(
                'v-carte v-carte-tarif flex flex-col rounded-2xl bg-bg p-6',
                professionnel ? 'border-2 border-accent shadow-card-lifted' : 'border border-border shadow-card',
              )}
              data-testid={`pricing-card-${forfait.id}`}
              aria-labelledby={`forfait-${forfait.id}`}
              data-apparait
            >
              {/* Rangee du badge reservee dans chaque carte : titres et prix alignes d'une carte a l'autre. */}
              <div className="h-6">
                {professionnel && <Badge variant="default">Forfait de l’essai gratuit</Badge>}
              </div>
              <h3 id={`forfait-${forfait.id}`} className="mt-3 text-lg font-semibold text-heading">
                {forfait.label}
              </h3>
              {surDevis ? (
                <div className="mt-4 min-h-24">
                  <p className="text-3xl font-bold tracking-tight text-heading">Sur devis</p>
                </div>
              ) : (
                <div className="mt-4 min-h-24">
                  <p className="text-heading">
                    <span className="text-4xl font-bold tracking-tight">{formatPrice(forfait.prixAnnuelHt ?? 0)}</span>{' '}
                    <span className="text-sm text-fg-muted">DH HT / an</span>
                  </p>
                  <p className="mt-1.5 text-sm text-fg-muted" data-testid={`pricing-mensuel-${forfait.id}`}>
                    <span className="block">ou {formatPrice(forfait.prixMensuelHt ?? 0)} DH HT / mois,</span>{' '}
                    <span className="block">soit {formatPrice((forfait.prixMensuelHt ?? 0) * 12)} DH HT sur un an</span>
                  </p>
                </div>
              )}
              <div className="v-filet-or my-5" aria-hidden="true" />
              <ul className="flex-1 space-y-2.5 text-sm text-fg">
                <li className="flex gap-2">
                  <Coche />
                  <span>
                    {forfait.dossiers}{' '}
                    <InfoBulle
                      libelle="Comment les dossiers sont-ils comptés ?"
                      texte="Un dossier juridique est une société suivie par le cabinet. Il est compté une seule fois dans le mois où un ticket y est ouvert, quel que soit le nombre de tickets ; le compteur repart à zéro chaque mois. Les dossiers existants restent toujours accessibles."
                    />
                  </span>
                </li>
                <li className="flex gap-2"><Coche /><span>{forfait.utilisateurs}</span></li>
                <li className="flex gap-2"><Coche /><span>{forfait.stockage}</span></li>
                {fonctionsDuForfait(forfait.id).map((f) => (
                  <li key={f} className="flex gap-2"><Coche /><span>{f}</span></li>
                ))}
              </ul>
              <div className="mt-6">
                {surDevis ? (
                  contact ? (
                    <Lien
                      href={`mailto:${contact}?subject=${encodeURIComponent('Demande de devis JURIKA')}`}
                      commeBouton={{ variant: 'secondary', size: 'md' }}
                      className="v-bouton w-full"
                      data-testid="pricing-cta-sur-devis"
                    >
                      Demander un devis
                    </Lien>
                  ) : (
                    <p className="text-sm text-fg-muted" data-testid="pricing-sur-devis-sans-contact">
                      Au-delà de 100 dossiers par mois, le forfait est établi avec JURIKA, qui ouvre alors l’espace de
                      votre cabinet.
                    </p>
                  )
                ) : (
                  <Lien
                    to="/signup"
                    commeBouton={{ variant: professionnel ? 'primary' : 'secondary', size: 'md' }}
                    className="v-bouton w-full"
                    data-testid={`pricing-cta-${forfait.id}`}
                  >
                    Choisir {forfait.label}
                  </Lien>
                )}
              </div>
            </article>
          );
        })}
      </div>

      <div className="mt-8 rounded-2xl border border-border bg-bg p-6 sm:p-8" data-testid="pricing-socle" data-apparait>
        <div className="flex items-start gap-4">
          <TuileIcone icone={ShieldCheck} />
          <div>
            <h3 className="text-base font-semibold text-heading">Inclus dans tous les forfaits</h3>
            <p className="mt-1 text-sm text-fg-muted">La sécurité et vos droits sur vos données ne dépendent pas du forfait.</p>
          </div>
        </div>
        <ul className="mt-5 grid gap-2.5 text-sm text-fg sm:grid-cols-2">
          {SOCLE_COMMUN.map((g) => (
            <li key={g} className="flex gap-2"><Coche /><span>{g}</span></li>
          ))}
        </ul>
      </div>
    </Section>
  );
}

/* ------------------------------------------------------------------------ Essai */

export function Essai() {
  return (
    <Section
      id="essai"
      surtitre="Essai gratuit"
      titre={`Essai gratuit de ${ESSAI.dureeJours} jours`}
      intro="Pour découvrir la plateforme avant de souscrire, aux conditions d’utilisation de l’essai (pièce D du contrat d’abonnement)."
      testId="pricing-essai"
    >
      <div className="grid overflow-hidden rounded-2xl border border-border bg-bg-raised shadow-card lg:grid-cols-2" data-apparait>
        <div className="border-b border-border bg-accent-soft p-7 lg:border-b-0 lg:border-r">
          <h3 className="text-base font-semibold text-heading">Ce que comprend l’essai</h3>
          <ul className="mt-5 space-y-3 text-sm text-fg">
            {ESSAI.conditions.map((c) => (
              <li key={c} className="flex gap-2"><Coche /><span>{c}</span></li>
            ))}
          </ul>
        </div>
        <div className="flex flex-col p-7">
          <h3 className="flex items-center gap-1 text-base font-semibold text-heading">
            À savoir avant de commencer
            <InfoBulle
              libelle="Et à la fin de l’essai ?"
              texte="Si vous souscrivez un forfait, pendant ou après l’essai, vous conservez tout votre travail ; les actes régénérés après la souscription ne portent plus la mention d’évaluation."
            />
          </h3>
          <ul className="mt-5 flex-1 space-y-3 text-sm text-fg-muted">
            {ESSAI.reserves.map((r) => (
              <li key={r} className="flex gap-2">
                <CircleAlert className="mt-0.5 h-4 w-4 shrink-0 text-fg-subtle" aria-hidden="true" />
                <span>{r}</span>
              </li>
            ))}
          </ul>
          <div className="mt-7 flex flex-wrap gap-3">
            <Lien to="/essai" commeBouton={{ variant: 'primary', size: 'md' }} className="v-bouton" data-testid="essai-cta">
              Commencer l’essai gratuit
            </Lien>
            <Lien to="/cgu" commeBouton={{ variant: 'secondary', size: 'md' }} className="v-bouton">
              Lire les conditions de l’essai
            </Lien>
          </div>
        </div>
      </div>
    </Section>
  );
}

/* --------------------------------------------------------- Questions frequentes */

export function Questions() {
  return (
    <section id="faq" aria-labelledby="faq-titre" className="scroll-mt-16 border-y border-border bg-bg-raised py-20 lg:py-28">
      <div className="mx-auto grid max-w-6xl gap-12 px-4 sm:px-6 lg:grid-cols-[1fr_2fr]">
        <div className="lg:sticky lg:top-24 lg:self-start">
          <EnTeteSection
            id="faq"
            surtitre="Questions fréquentes"
            titre="Ce que vous voulez vérifier avant de souscrire"
            intro={<p className="text-base">Les réponses reprennent le cahier des charges et le contrat d’abonnement.</p>}
            alignement="gauche"
          />
        </div>
        <div className="space-y-3">
          {FAQ.map((f) => (
            <details key={f.q} className="group rounded-xl border border-border bg-bg" data-apparait>
              <summary className="flex cursor-pointer list-none items-center justify-between gap-4 rounded-xl px-5 py-4 text-base font-medium text-heading hover:bg-bg-overlay [&::-webkit-details-marker]:hidden">
                <span>{f.q}</span>
                <ChevronDown className="v-chevron h-5 w-5 shrink-0 text-fg-subtle" aria-hidden="true" />
              </summary>
              <p className="v-reponse px-5 pb-5 text-sm leading-relaxed text-fg-muted">{f.a}</p>
            </details>
          ))}
        </div>
      </div>
    </section>
  );
}

/* ----------------------------------------------------------------------- Contact */

export function Contact() {
  const contact = adresseContact();
  return (
    <section id="contact" aria-labelledby="contact-titre" className="scroll-mt-16 py-20 lg:py-28">
      <div className="mx-auto max-w-6xl px-4 sm:px-6">
        <div className="zone-marine v-marine-fond rounded-3xl px-6 py-14 text-center text-nav-fg sm:px-12 lg:py-20" data-apparait>
          <MotifZellige />
          <Surtitre ton="marine">Prêt à digitaliser votre cabinet ?</Surtitre>
          <h2 id="contact-titre" className="mx-auto mt-5 max-w-3xl text-3xl font-bold leading-tight tracking-tight text-nav-fg-hi sm:text-4xl">
            Ouvrir l’espace de votre cabinet
          </h2>
          <p className="mx-auto mt-4 max-w-2xl text-base leading-relaxed">
            Deux façons de rejoindre la plateforme : l’inscription en ligne, avec le choix du forfait et le paiement,
            ou une demande à JURIKA, qui crée alors l’espace de votre cabinet.
          </p>
          <ul className="mx-auto mt-10 grid max-w-3xl gap-x-8 gap-y-5 text-left sm:grid-cols-2">
            {RAPPEL_FINAL.map((r) => (
              <li key={r.titre} className="flex gap-3">
                <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full border border-nav-active text-nav-active">
                  <Check className="h-3 w-3" aria-hidden="true" />
                </span>
                <span>
                  <span className="block text-sm font-semibold text-nav-fg-hi">{r.titre}</span>
                  <span className="block text-sm">{r.texte}</span>
                </span>
              </li>
            ))}
          </ul>
          <div className="mt-10 flex flex-wrap justify-center gap-3">
            <Lien to="/essai" commeBouton={{ variant: 'primary', size: 'lg' }} className="v-bouton">
              Essai gratuit
              <ArrowRight className="v-fleche h-4 w-4" aria-hidden="true" />
            </Lien>
            <Lien to="/signup" commeBouton={{ variant: 'secondary', size: 'lg' }} className="v-bouton">
              Souscrire un forfait
            </Lien>
          </div>
          {contact ? (
            <p className="mt-6 text-sm">
              Écrire à JURIKA :{' '}
              <a
                href={`mailto:${contact}`}
                className="rounded-sm text-nav-fg-hi underline decoration-1 underline-offset-2 hover:decoration-2"
                data-testid="contact-adresse"
              >
                {contact}
              </a>
            </p>
          ) : (
            <p className="mt-6 text-sm" data-testid="contact-sans-adresse">
              L’adresse de contact de JURIKA n’est pas encore publiée sur cette page.
            </p>
          )}
          <p className="mt-4 text-xs">
            Essai gratuit de {ESSAI.dureeJours} jours, sans moyen de paiement · prix hors taxes · hébergement prévu chez
            Amazon Web Services, à Paris
          </p>
        </div>
      </div>
    </section>
  );
}
