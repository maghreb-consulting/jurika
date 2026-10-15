import type { ReactNode } from 'react';
import {
  ArrowRight,
  BotMessageSquare,
  Building2,
  Check,
  ChevronDown,
  CircleAlert,
  Clock,
  DatabaseBackup,
  FileCheck2,
  FolderOpen,
  History,
  KeyRound,
  Lock,
  MessagesSquare,
  PenLine,
  Route,
  ScanLine,
  ScrollText,
  Server,
  ShieldCheck,
  UserCheck,
  BrainCircuit,
  type LucideIcon,
} from 'lucide-react';
import { Badge } from '../../../components/ui/Badge';
import { InfoBulle } from '../../../components/ui/Aide';
import { Lien } from '../../../components/ui/Lien';
import { cn } from '../../../lib/utils';
import { STATUT_LABELS } from '../../../types/ticket';
import { adresseContact } from '../../legal/contact';
import { ESSAI, FORFAITS, SOCLE_COMMUN, fonctionsDuForfait, forfaitMinimal, formatPrice } from '../config/pricing';
import {
  CNDP_STATUT,
  DISPONIBILITE,
  FAQ,
  FONCTIONNALITES,
  FORMES_COUVERTES,
  HEBERGEMENT,
  PARCOURS,
  PROBLEME,
  ROLES,
  SECURITE,
  type IconeFonction,
  type IconeSecurite,
} from '../contenu';

/**
 * Lot L6b (E10) : sections de la vitrine, dans l'ordre de la page.
 *
 * Mise en page sobre : jetons du systeme de design (aucune couleur en dur), marine pour la
 * structure, or pour la seule action principale et les accents. Aucune animation d'entree
 * (le contenu est la des le premier rendu : aucun decalage) ; seules les transitions de
 * survol (150 ms) restent, coupees par "reduire les animations" (index.css).
 */

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
      className={cn('scroll-mt-16 py-16 lg:py-24', fond === 'releve' && 'border-y border-border bg-bg-raised')}
    >
      <div className="mx-auto max-w-6xl px-4 sm:px-6">
        <div className="max-w-3xl">
          <p className="eyebrow">{surtitre}</p>
          <h2 id={`${id}-titre`} className="font-heading mt-2 text-3xl font-semibold tracking-tight">
            {titre}
          </h2>
          {intro && <div className="mt-4 text-base leading-relaxed text-fg-muted">{intro}</div>}
        </div>
        <div className="mt-10">{children}</div>
      </div>
    </section>
  );
}

function Coche({ className }: { className?: string }) {
  return <Check className={cn('mt-0.5 h-4 w-4 shrink-0 text-success', className)} aria-hidden="true" />;
}

/* ---------------------------------------------------------------- Accroche */

const ETAPES_TICKET = ['CREATION_TICKET', 'GENERATION_DOCUMENTS', 'DEROULEMENT_DEMARCHE', 'CLOTURE_DOSSIER'] as const;

export function Accroche() {
  return (
    <section id="haut" aria-labelledby="accroche-titre" className="scroll-mt-16" data-testid="hero">
      <div className="mx-auto grid max-w-6xl items-center gap-12 px-4 py-16 sm:px-6 lg:grid-cols-[1.2fr_1fr] lg:py-24">
        <div>
          <p className="eyebrow">Gestion juridique des sociétés au Maroc</p>
          <h1 id="accroche-titre" className="font-heading mt-3 text-4xl font-semibold leading-tight tracking-tight sm:text-5xl">
            Toute la vie juridique des sociétés que suit votre cabinet, en un seul endroit
          </h1>
          <p className="mt-6 max-w-2xl text-lg leading-relaxed text-fg-muted">
            JURIKA digitalise et centralise la gestion juridique des sociétés : saisie unique des données, actes
            générés à partir de vos modèles validés, démarches suivies jusqu’à la clôture et Data Room partagée
            avec chaque client.
          </p>
          <p className="mt-6 max-w-2xl border-l-4 border-accent pl-4 text-base font-medium text-fg">
            Aucune intelligence artificielle ne rédige un acte : à données identiques, acte identique.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Lien to="/essai" commeBouton={{ variant: 'primary', size: 'lg' }} data-testid="hero-essai-cta">
              Essayer gratuitement pendant 7 jours
              <ArrowRight className="h-4 w-4" aria-hidden="true" />
            </Lien>
            <Lien href="#tarifs" commeBouton={{ variant: 'secondary', size: 'lg' }}>
              Voir les tarifs
            </Lien>
          </div>
          <p className="mt-4 text-sm text-fg-subtle">
            Sans moyen de paiement, un essai par cabinet.{' '}
            <Lien to="/signup" variante="discret" data-testid="hero-signup-cta">
              Souscrire directement un forfait
            </Lien>
          </p>
        </div>

        <figure className="rounded-xl border border-border bg-bg-raised p-6 shadow-card" aria-labelledby="schema-ticket">
          <figcaption id="schema-ticket" className="text-sm font-semibold text-heading">
            Chaque opération suit son ticket, dans l’ordre
          </figcaption>
          <ol className="mt-5 space-y-0">
            {ETAPES_TICKET.map((code, i) => (
              <li key={code} className="relative flex gap-4 pb-6 last:pb-0">
                {i < ETAPES_TICKET.length - 1 && (
                  <span className="absolute left-[15px] top-8 h-[calc(100%-2rem)] w-px bg-border-hi" aria-hidden="true" />
                )}
                <span
                  className={cn(
                    'flex h-8 w-8 shrink-0 items-center justify-center rounded-full border text-sm',
                    i === 0 ? 'border-accent bg-accent-soft text-accent' : 'border-border-hi bg-bg text-fg-muted',
                  )}
                  aria-hidden="true"
                >
                  <Check className="h-4 w-4" />
                </span>
                <span className="pt-1 text-sm font-medium text-fg">{STATUT_LABELS[code]}</span>
              </li>
            ))}
          </ol>
          <p className="mt-5 border-t border-border pt-4 text-sm text-fg-muted">
            Chaque passage est contrôlé par le serveur ; un ticket peut aussi être annulé, avec son motif.
          </p>
        </figure>
      </div>
    </section>
  );
}

/* ------------------------------------------------------ Probleme et solution */

export function ProblemeSolution() {
  return (
    <Section
      id="solution"
      surtitre="Le constat et la réponse"
      titre="Digitaliser et centraliser la gestion juridique des sociétés"
      fond="releve"
    >
      <div className="grid gap-8 lg:grid-cols-[1fr_2fr]">
        <div className="self-start rounded-xl border border-border bg-bg p-6">
          <h3 className="text-lg font-semibold">{PROBLEME.titre}</h3>
          <ul className="mt-4 space-y-3 text-sm leading-relaxed text-fg-muted">
            {PROBLEME.points.map((p) => (
              <li key={p} className="flex gap-3">
                <CircleAlert className="mt-0.5 h-4 w-4 shrink-0 text-fg-subtle" aria-hidden="true" />
                <span>{p}</span>
              </li>
            ))}
          </ul>
        </div>
        <div>
          <h3 className="text-lg font-semibold">Avec JURIKA</h3>
          <ul className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {ROLES.map((r) => (
              <li key={r.titre} className="rounded-xl border border-border bg-bg-raised p-5">
                <p className="font-semibold text-heading">{r.titre}</p>
                <p className="mt-2 text-sm leading-relaxed text-fg-muted">{r.texte}</p>
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

export function Fonctionnalites() {
  return (
    <Section
      id="fonctionnalites"
      surtitre="Fonctionnalités"
      titre="Ce que fait la plateforme"
      intro="Les fonctions communes à tous les forfaits, et celles qui sont réservées à un forfait, signalées comme telles."
    >
      <ul className="grid gap-4 md:grid-cols-2">
        {FONCTIONNALITES.map((f) => {
          const Icone = ICONES_FONCTION[f.icone];
          const minimal = f.fonction4_5 ? forfaitMinimal(f.fonction4_5) : null;
          return (
            <li key={f.titre} className="flex gap-4 rounded-xl border border-border bg-bg-raised p-5 shadow-card">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-bg-overlay text-heading">
                <Icone className="h-5 w-5" aria-hidden="true" />
              </span>
              <div className="min-w-0">
                <h3 className="text-base font-semibold">{f.titre}</h3>
                <p className="mt-1.5 text-sm leading-relaxed text-fg-muted">{f.texte}</p>
                {minimal && (
                  <p className="mt-3">
                    <Badge variant="neutral">À partir du forfait {minimal.label}</Badge>
                  </p>
                )}
              </div>
            </li>
          );
        })}
      </ul>
    </Section>
  );
}

/* -------------------------------------------------------------------- Parcours */

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
      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {PARCOURS.map((p) => (
          <li key={p.titre} className="rounded-xl border border-border bg-bg p-5">
            <p className="flex gap-2 font-semibold text-heading">
              <Coche className="text-accent" />
              <span>{p.titre}</span>
            </p>
            <p className="mt-2 text-sm leading-relaxed text-fg-muted">{p.texte}</p>
          </li>
        ))}
      </ul>
      <p className="mt-6 text-sm text-fg-muted">
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
        {SECURITE.map((s) => {
          const Icone = ICONES_SECURITE[s.icone];
          return (
            <li key={s.titre} className="rounded-xl border border-border bg-bg-raised p-5">
              <Icone className="h-5 w-5 text-heading" aria-hidden="true" />
              <h3 className="mt-3 text-base font-semibold">{s.titre}</h3>
              <p className="mt-1.5 text-sm leading-relaxed text-fg-muted">{s.texte}</p>
            </li>
          );
        })}
      </ul>

      <div className="mt-8 grid gap-4 lg:grid-cols-[2fr_1fr]">
        <div className="rounded-xl border border-border bg-bg-raised p-6" data-testid="securite-hebergement">
          <div className="flex flex-wrap items-center gap-3">
            <Server className="h-5 w-5 text-heading" aria-hidden="true" />
            <h3 className="text-base font-semibold">Hébergement et données personnelles</h3>
            <Badge variant="warning" dot>
              {CNDP_STATUT}
            </Badge>
          </div>
          <p className="mt-3 text-sm leading-relaxed text-fg-muted">{HEBERGEMENT}</p>
          <p className="mt-3 text-sm leading-relaxed text-fg-muted">
            Pour les données de ses dossiers, le cabinet est responsable du traitement et l’éditeur agit comme
            sous-traitant, selon l’accord de traitement des données du contrat (loi n° 09-08).{' '}
            <Lien to="/cgu" variante="discret">
              Lire le contrat d’abonnement
            </Lien>
          </p>
        </div>
        <div className="rounded-xl border border-border bg-bg-raised p-6">
          <h3 className="text-base font-semibold">Disponibilité</h3>
          <p className="mt-3 text-sm leading-relaxed text-fg-muted">{DISPONIBILITE}</p>
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
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4" data-testid="pricing-grid">
        {FORFAITS.map((forfait) => {
          const surDevis = forfait.prixAnnuelHt === null || forfait.prixMensuelHt === null;
          const professionnel = forfait.id === 'professionnel';
          return (
            <article
              key={forfait.id}
              className={cn(
                'flex flex-col rounded-xl border bg-bg p-6',
                professionnel ? 'border-accent shadow-card-lifted' : 'border-border',
              )}
              data-testid={`pricing-card-${forfait.id}`}
              aria-labelledby={`forfait-${forfait.id}`}
            >
              {/* Rangee du badge reservee dans chaque carte : titres et prix alignes d'une carte a l'autre. */}
              <div className="h-6">
                {professionnel && <Badge variant="default">Forfait de l’essai gratuit</Badge>}
              </div>
              <h3 id={`forfait-${forfait.id}`} className="mt-2 text-lg font-semibold">
                {forfait.label}
              </h3>
              {surDevis ? (
                <div className="mt-4 min-h-20">
                  <p className="text-3xl font-semibold tracking-tight text-heading">Sur devis</p>
                </div>
              ) : (
                <div className="mt-4 min-h-20">
                  <p className="text-heading">
                    <span className="text-3xl font-semibold tracking-tight">{formatPrice(forfait.prixAnnuelHt ?? 0)}</span>{' '}
                    <span className="text-sm text-fg-muted">DH HT / an</span>
                  </p>
                  <p className="mt-1 text-sm text-fg-muted" data-testid={`pricing-mensuel-${forfait.id}`}>
                    <span className="block">ou {formatPrice(forfait.prixMensuelHt ?? 0)} DH HT / mois,</span>{' '}
                    <span className="block">soit {formatPrice((forfait.prixMensuelHt ?? 0) * 12)} DH HT sur un an</span>
                  </p>
                </div>
              )}
              <ul className="mt-6 flex-1 space-y-2.5 text-sm text-fg">
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

      <div className="mt-8 rounded-xl border border-border bg-bg p-6" data-testid="pricing-socle">
        <h3 className="text-base font-semibold">Inclus dans tous les forfaits</h3>
        <p className="mt-1 text-sm text-fg-muted">La sécurité et vos droits sur vos données ne dépendent pas du forfait.</p>
        <ul className="mt-4 grid gap-2.5 text-sm text-fg sm:grid-cols-2">
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
      <div className="grid gap-6 lg:grid-cols-2">
        <div className="rounded-xl border border-border bg-bg-raised p-6">
          <h3 className="text-base font-semibold">Ce que comprend l’essai</h3>
          <ul className="mt-4 space-y-2.5 text-sm text-fg">
            {ESSAI.conditions.map((c) => (
              <li key={c} className="flex gap-2"><Coche /><span>{c}</span></li>
            ))}
          </ul>
        </div>
        <div className="flex flex-col rounded-xl border border-border bg-bg-raised p-6">
          <h3 className="flex items-center gap-1 text-base font-semibold">
            À savoir avant de commencer
            <InfoBulle
              libelle="Et à la fin de l’essai ?"
              texte="Si vous souscrivez un forfait, pendant ou après l’essai, vous conservez tout votre travail ; les actes régénérés après la souscription ne portent plus la mention d’évaluation."
            />
          </h3>
          <ul className="mt-4 flex-1 space-y-2.5 text-sm text-fg-muted">
            {ESSAI.reserves.map((r) => (
              <li key={r} className="flex gap-2">
                <CircleAlert className="mt-0.5 h-4 w-4 shrink-0 text-fg-subtle" aria-hidden="true" />
                <span>{r}</span>
              </li>
            ))}
          </ul>
          <div className="mt-6 flex flex-wrap gap-3">
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
    <Section id="faq" surtitre="Questions fréquentes" titre="Ce que vous voulez vérifier avant de souscrire" fond="releve">
      <div className="max-w-3xl divide-y divide-border rounded-xl border border-border bg-bg">
        {FAQ.map((f) => (
          <details key={f.q} className="group">
            <summary className="flex cursor-pointer list-none items-center justify-between gap-4 rounded-xl px-5 py-4 text-base font-medium text-fg hover:bg-bg-overlay [&::-webkit-details-marker]:hidden">
              <span>{f.q}</span>
              <ChevronDown
                className="h-5 w-5 shrink-0 text-fg-subtle transition-transform duration-150 group-open:rotate-180"
                aria-hidden="true"
              />
            </summary>
            <p className="px-5 pb-5 text-sm leading-relaxed text-fg-muted">{f.a}</p>
          </details>
        ))}
      </div>
    </Section>
  );
}

/* ----------------------------------------------------------------------- Contact */

export function Contact() {
  const contact = adresseContact();
  return (
    <Section id="contact" surtitre="Contact" titre="Ouvrir l’espace de votre cabinet">
      <div className="flex flex-col gap-6 rounded-xl border border-border bg-bg-raised p-6 lg:flex-row lg:items-center lg:justify-between">
        <div className="max-w-2xl text-sm leading-relaxed text-fg-muted">
          <p>
            Deux façons de rejoindre la plateforme : l’inscription en ligne, avec le choix du forfait et le paiement,
            ou une demande à JURIKA, qui crée alors l’espace de votre cabinet.
          </p>
          {contact ? (
            <p className="mt-3 text-fg">
              Écrire à JURIKA :{' '}
              <Lien href={`mailto:${contact}`} data-testid="contact-adresse">
                {contact}
              </Lien>
            </p>
          ) : (
            <p className="mt-3" data-testid="contact-sans-adresse">
              L’adresse de contact de JURIKA n’est pas encore publiée sur cette page.
            </p>
          )}
        </div>
        <div className="flex flex-wrap gap-3">
          <Lien to="/essai" commeBouton={{ variant: 'primary', size: 'md' }}>
            Essai gratuit
          </Lien>
          <Lien to="/signup" commeBouton={{ variant: 'secondary', size: 'md' }}>
            Souscrire un forfait
          </Lien>
        </div>
      </div>
    </Section>
  );
}
