import type { ReactNode } from 'react';
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
import { useThemeStore } from '../../../store/themeStore';
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
import { CaptureEcran } from './CaptureEcran';

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
 * useApparitions et vitrine.css), survols de 150 ms ; rien ne bouge si les animations sont
 * reduites.
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

export function Accroche() {
  const mode = useThemeStore((s) => s.mode);
  return (
    <section id="haut" aria-labelledby="accroche-titre" className="relative scroll-mt-16 overflow-hidden" data-testid="hero">
      <div className="v-fond-accroche" aria-hidden="true" />
      <div className="relative mx-auto max-w-6xl px-4 pb-20 pt-16 text-center sm:px-6 lg:pb-28 lg:pt-24">
        <span className="v-pastille inline-flex items-center gap-2 rounded-full px-4 py-1.5 text-xs font-semibold uppercase tracking-[0.14em] text-accent">
          <span className="h-2 w-2 rounded-full bg-success" aria-hidden="true" />
          {ACCROCHE.surtitre}
        </span>
        <h1
          id="accroche-titre"
          className="mx-auto mt-6 max-w-4xl text-4xl font-bold leading-[1.1] tracking-tight text-heading sm:text-5xl lg:text-6xl"
        >
          {ACCROCHE.titre}
          <br />
          <span>{ACCROCHE.accent1}</span> et <span className="text-accent">{ACCROCHE.accent2}</span>
        </h1>
        <p className="mx-auto mt-6 max-w-3xl text-lg leading-relaxed text-fg-muted">{ACCROCHE.texte}</p>
        <p className="mx-auto mt-6 inline-flex max-w-2xl items-center gap-2 rounded-lg border border-border bg-bg-raised px-4 py-2 text-left text-sm font-medium text-fg">
          <ShieldCheck className="h-4 w-4 shrink-0 text-accent" aria-hidden="true" />
          {ACCROCHE.garantie}
        </p>
        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <Lien to="/essai" commeBouton={{ variant: 'primary', size: 'lg' }} data-testid="hero-essai-cta">
            Essayer gratuitement pendant {ESSAI.dureeJours} jours
            <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </Lien>
          <Lien href="#fonctionnalites" commeBouton={{ variant: 'secondary', size: 'lg' }}>
            Découvrir la plateforme
          </Lien>
        </div>
        <p className="mt-4 text-sm text-fg-subtle">
          Sans moyen de paiement, un essai par cabinet.{' '}
          <Lien to="/signup" variante="discret" data-testid="hero-signup-cta">
            Souscrire directement un forfait
          </Lien>
        </p>

        <div className="mx-auto mt-14 max-w-5xl text-left">
          <CaptureEcran
            nom={mode === 'dark' ? 'tableau-de-bord-employe-sombre' : 'tableau-de-bord-employe-clair'}
            sizes="(min-width: 1100px) 1024px, calc(100vw - 2rem)"
            prioritaire
          />
        </div>
      </div>
    </section>
  );
}

/* --------------------------------------------------- Bande des operations */

export function BandeOperations() {
  return (
    <section aria-label="Opérations couvertes et textes applicables" className="border-y border-border bg-bg-raised py-10">
      <div className="mx-auto max-w-6xl px-4 text-center sm:px-6">
        <p className="text-sm text-fg-muted">{BANDE.titre}</p>
        <ul className="mt-5 flex flex-wrap justify-center gap-2.5">
          {BANDE.operations.map((o) => (
            <li key={o} className="rounded-full border border-border bg-bg px-4 py-1.5 text-sm font-medium text-heading">
              {o}
            </li>
          ))}
        </ul>
        <ul className="mt-4 flex flex-wrap justify-center gap-x-6 gap-y-2 text-xs font-semibold uppercase tracking-[0.12em] text-fg-subtle">
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

function VisuelPilier({ pilier }: { pilier: Pilier }) {
  if (pilier.capture !== 'duo') {
    return <CaptureEcran nom={pilier.capture} sizes="(min-width: 1024px) 560px, calc(100vw - 2rem)" />;
  }
  return (
    <figure>
      <div className="grid grid-cols-2 gap-4">
        <CaptureEcran nom="tableau-de-bord-employe-clair" sizes="(min-width: 1024px) 272px, 50vw" compacte />
        <CaptureEcran nom="tableau-de-bord-employe-sombre" sizes="(min-width: 1024px) 272px, 50vw" compacte />
      </div>
      {pilier.legende && <figcaption className="mt-3 text-center text-sm text-fg-subtle">{pilier.legende}</figcaption>}
    </figure>
  );
}

function BlocPilier({ pilier, inverse }: { pilier: Pilier; inverse: boolean }) {
  return (
    <article id={pilier.id} aria-labelledby={`${pilier.id}-titre`} className="grid items-center gap-10 lg:grid-cols-2 lg:gap-16">
      <div className={cn(inverse && 'lg:order-2')}>
        <div data-apparait>
          <Surtitre>{pilier.surtitre}</Surtitre>
          <h3 id={`${pilier.id}-titre`} className="mt-4 text-2xl font-bold leading-snug tracking-tight text-heading sm:text-3xl">
            {pilier.titre}
          </h3>
          <p className="mt-4 text-base leading-relaxed text-fg-muted">{pilier.texte}</p>
        </div>
        <ul className="mt-8 space-y-6">
          {pilier.fonctions.map((cle) => (
            <ElementFonction key={cle} f={fonction(cle)} />
          ))}
        </ul>
      </div>
      <div className={cn(inverse && 'lg:order-1')} data-apparait>
        <VisuelPilier pilier={pilier} />
      </div>
    </article>
  );
}

export function Fonctionnalites() {
  return (
    <section id="fonctionnalites" aria-labelledby="fonctionnalites-titre" className="scroll-mt-16 pt-20 lg:pt-28">
      <div className="mx-auto max-w-6xl px-4 sm:px-6">
        <EnTeteSection
          id="fonctionnalites"
          surtitre="La plateforme"
          titre="Ce que fait la plateforme"
          intro="Un seul flux de travail, du premier rendez-vous à la clôture du dossier. Les fonctions réservées à un forfait sont signalées comme telles."
        />
        <div className="mt-16 space-y-24 lg:space-y-32">
          {PILIERS.map((p, i) => (
            <BlocPilier key={p.id} pilier={p} inverse={i % 2 === 1} />
          ))}
        </div>
      </div>

      <div id="ia" className="zone-marine v-marine-fond mt-24 py-20 text-nav-fg lg:mt-32 lg:py-28">
        <div className="mx-auto grid max-w-6xl items-center gap-12 px-4 sm:px-6 lg:grid-cols-2 lg:gap-16">
          <div>
            <div data-apparait>
              <Surtitre ton="marine">{IA.surtitre}</Surtitre>
              <h3 className="mt-4 text-2xl font-bold leading-snug tracking-tight text-nav-fg-hi sm:text-3xl">{IA.titre}</h3>
              <p className="mt-4 text-base leading-relaxed text-nav-fg">{IA.texte}</p>
            </div>
            <ul className="mt-8">
              <ElementFonction f={fonction(IA.fonction)} ton="marine" />
            </ul>
          </div>
          <div data-apparait>
            <CaptureEcran nom="juri-chatbot-clair" sizes="(min-width: 1024px) 560px, calc(100vw - 2rem)" />
          </div>
        </div>
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
                'v-carte flex flex-col rounded-2xl bg-bg p-6',
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
                      className="w-full"
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
                    className="w-full"
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
            <Lien to="/essai" commeBouton={{ variant: 'primary', size: 'md' }} data-testid="essai-cta">
              Commencer l’essai gratuit
            </Lien>
            <Lien to="/cgu" commeBouton={{ variant: 'secondary', size: 'md' }}>
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
              <p className="px-5 pb-5 text-sm leading-relaxed text-fg-muted">{f.a}</p>
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
            <Lien to="/essai" commeBouton={{ variant: 'primary', size: 'lg' }}>
              Essai gratuit
              <ArrowRight className="h-4 w-4" aria-hidden="true" />
            </Lien>
            <Lien to="/signup" commeBouton={{ variant: 'secondary', size: 'lg' }}>
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
