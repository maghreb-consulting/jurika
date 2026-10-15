import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertCircle, Building2, FolderOpen, Search, Ticket as IconeTicket } from 'lucide-react';
import { cn } from '../../lib/utils';
import { extractError } from '../../lib/api';
import { useAuthStore } from '../../store/authStore';
import { useAidesStore } from '../../store/aidesStore';
import { useAssistantStore } from '../../store/assistantStore';
import { useCommandesStore } from '../../store/commandesStore';
import { usePreferencesStore } from '../../store/preferencesStore';
import { ticketService } from '../../services/ticket.service';
import { dataroomService } from '../../services/dataroom.service';
import type { DossierBrief } from '../../types/dataroom';
import { STATUT_LABELS, type Ticket } from '../../types/ticket';
import type { Role } from '../../types/auth';
import { Modal } from '../ui/Modal';
import { Spinner } from '../ui/Button';
import { cleVisite } from '../ui/visiteEtapes';
import { useEtatJuri, useToastSiDisponible } from '../assistant/useJuri';
import { LIBELLES_GROUPE, commandesDuRole, correspond, filtrerCommandes, type Commande, type GroupeCommande } from './commandes';
import { Touches } from './Touches';

/**
 * Lot L6b (E11, point 8) : palette de commandes (Ctrl+K / Cmd+K, champ de l'en-tete).
 *
 * Recherche par les appels EXISTANTS, sans nouvel endpoint :
 * - tickets : GET /tickets?q=...&limit=6 (ticketService.list, filtre du serveur), cabinet ;
 * - dossiers et societes : GET /dataroom/dossiers (dataroomService.listDossiers), lu une
 *   fois a la premiere recherche puis filtre ici (reference du dossier : groupe Dossiers ;
 *   raison sociale, ICE, ville, forme : groupe Societes). Le serveur ne rend que les
 *   dossiers du role (le client : les siens ; l'employe : ceux qu'il suit) ;
 * - le super-admin n'a ni tickets ni dossiers (RG-U07) : actions et ecrans seulement.
 *
 * Anti-rebond de 250 ms ; etat de recherche, erreur par source (les autres resultats
 * restent) et absence de resultat annonces. Clavier : fleches haut et bas, Entree, Echap
 * (fenetre native), Ctrl+K ferme. Motif ARIA "combobox" + "listbox".
 */
const DELAI_ANTI_REBOND_MS = 250;
const MAX_PAR_GROUPE = 6;
const ORDRE: GroupeCommande[] = ['tickets', 'dossiers', 'societes', 'actions', 'ecrans'];

function rolesTickets(role: Role): boolean {
  return role === 'EMPLOYE' || role === 'SUPERVISEUR';
}
function rolesDossiers(role: Role): boolean {
  return role === 'EMPLOYE' || role === 'SUPERVISEUR' || role === 'CLIENT';
}

function useValeurDifferee(valeur: string, delai: number): string {
  const [differee, setDifferee] = useState(valeur);
  useEffect(() => {
    const t = window.setTimeout(() => setDifferee(valeur), delai);
    return () => window.clearTimeout(t);
  }, [valeur, delai]);
  return differee;
}

type Resultat<T> = { pour: string; liste: T[]; erreur: null } | { pour: string; liste: null; erreur: string };

export function PaletteCommandes() {
  const role = useAuthStore((s) => s.user?.role);
  if (!role) return null;
  return <Palette role={role} />;
}

function Palette({ role }: { role: Role }) {
  const navigate = useNavigate();
  const fermer = useCommandesStore((s) => s.fermer);
  const ouvrir = useCommandesStore((s) => s.ouvrir);
  const choisirTheme = usePreferencesStore((s) => s.choisirTheme);
  const definirJuriMasque = usePreferencesStore((s) => s.definirJuriMasque);
  const reafficherAide = useAidesStore((s) => s.reafficher);
  const ouvrirChatbot = useAssistantStore((s) => s.ouvrir);
  const { masque: juriMasque } = useEtatJuri();
  const toast = useToastSiDisponible();

  const [q, setQ] = useState('');
  const [actif, setActif] = useState(0);
  const recherche = useValeurDifferee(q, DELAI_ANTI_REBOND_MS);
  const rechercheNette = recherche.trim();

  const champ = useRef<HTMLInputElement | null>(null);
  // Apres l'ouverture de la fenetre (showModal place le focus sur son premier bouton) :
  // le focus va au champ de recherche.
  useEffect(() => {
    champ.current?.focus();
  }, []);

  const idListe = useId();
  const idOption = useId();

  const commandes = useMemo(
    () =>
      commandesDuRole({
        role,
        juriMasque,
        naviguer: (to) => navigate(to),
        ouvrir,
        choisirTheme: (c) => {
          void choisirTheme(c).then((ok) => {
            if (ok) toast?.enregistre('Thème enregistré');
          });
        },
        definirJuriMasque: (m) => {
          void definirJuriMasque(m).then((ok) => {
            if (ok) toast?.enregistre(m ? 'JURI est masqué ; le menu de votre compte le réaffiche' : 'JURI est de retour');
          });
        },
        ouvrirChatbot,
        revoirVisite: () => reafficherAide(cleVisite(role)),
      }),
    [role, juriMasque, navigate, ouvrir, choisirTheme, definirJuriMasque, ouvrirChatbot, reafficherAide, toast],
  );

  // ---- Tickets (serveur, q) ----
  const chercherTickets = rolesTickets(role) && rechercheNette.length >= 2;
  const [tickets, setTickets] = useState<Resultat<Ticket> | null>(null);
  useEffect(() => {
    if (!chercherTickets) return;
    let actuel = true;
    ticketService.list({ q: rechercheNette, limit: MAX_PAR_GROUPE }).then(
      (page) => {
        if (actuel) setTickets({ pour: rechercheNette, liste: page.items ?? [], erreur: null });
      },
      (err) => {
        if (actuel) setTickets({ pour: rechercheNette, liste: null, erreur: extractError(err).message });
      },
    );
    return () => {
      actuel = false;
    };
  }, [chercherTickets, rechercheNette]);
  const ticketsAJour = chercherTickets && tickets?.pour === rechercheNette ? tickets : null;

  // ---- Dossiers et societes (liste du role, lue une fois) ----
  const chercherDossiers = rolesDossiers(role) && rechercheNette.length >= 1;
  const [dossiers, setDossiers] = useState<Resultat<DossierBrief> | null>(null);
  const dossiersDemandes = useRef(false);
  useEffect(() => {
    if (!chercherDossiers || dossiersDemandes.current) return;
    dossiersDemandes.current = true;
    dataroomService.listDossiers().then(
      (liste) => setDossiers({ pour: '', liste: Array.isArray(liste) ? liste : [], erreur: null }),
      (err) => setDossiers({ pour: '', liste: null, erreur: extractError(err).message }),
    );
  }, [chercherDossiers]);

  const enAttente = q.trim() !== rechercheNette;
  const ticketsEnCours = chercherTickets && !ticketsAJour;
  const dossiersEnCours = chercherDossiers && !dossiers;
  const enCours = enAttente || ticketsEnCours || dossiersEnCours;

  const options = useMemo(() => {
    const liste: Commande[] = [];
    if (ticketsAJour?.liste) {
      for (const t of ticketsAJour.liste.slice(0, MAX_PAR_GROUPE)) {
        liste.push({
          id: `ticket-${t.id}`,
          groupe: 'tickets',
          libelle: `${t.reference} — ${t.titre}`,
          detail: STATUT_LABELS[t.statut],
          icone: IconeTicket,
          executer: () => navigate(`/tickets/${t.id}`),
        });
      }
    }
    if (chercherDossiers && dossiers?.liste) {
      const parReference: Commande[] = [];
      const parSociete: Commande[] = [];
      for (const d of dossiers.liste) {
        const cible = () => navigate(`/data-rooms?dossier=${encodeURIComponent(d.id)}`);
        const detail = [d.reference, d.formeJuridique, d.ville].filter(Boolean).join(' · ');
        // La reference ("annee-numero - societe") contient la raison sociale : un dossier
        // trouve par la societe va au groupe Societes, par son seul numero au groupe Dossiers.
        if (correspond(`${d.raisonSociale} ${d.ice ?? ''} ${d.ville ?? ''} ${d.formeJuridique ?? ''}`, rechercheNette)) {
          parSociete.push({ id: `societe-${d.id}`, groupe: 'societes', libelle: d.raisonSociale, detail: detail || undefined, icone: Building2, executer: cible });
        } else if (d.reference && correspond(d.reference, rechercheNette)) {
          parReference.push({ id: `dossier-${d.id}`, groupe: 'dossiers', libelle: d.reference, detail: d.raisonSociale, icone: FolderOpen, executer: cible });
        }
      }
      liste.push(...parReference.slice(0, MAX_PAR_GROUPE), ...parSociete.slice(0, MAX_PAR_GROUPE));
    }
    liste.push(...filtrerCommandes(commandes, rechercheNette));
    return liste;
  }, [ticketsAJour, chercherDossiers, dossiers, commandes, rechercheNette, navigate]);

  const indexActif = options.length === 0 ? -1 : Math.min(actif, options.length - 1);
  const idActif = indexActif >= 0 ? `${idOption}-${indexActif}` : undefined;

  useEffect(() => {
    if (!idActif) return;
    document.getElementById(idActif)?.scrollIntoView?.({ block: 'nearest' });
  }, [idActif]);

  function executer(c: Commande) {
    // La palette se ferme d'abord : une action qui ouvre une autre fenetre la remplace.
    fermer('palette');
    c.executer();
  }

  function surTouche(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      if (options.length === 0) return;
      const pas = e.key === 'ArrowDown' ? 1 : -1;
      setActif(((indexActif < 0 ? 0 : indexActif) + pas + options.length) % options.length);
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (indexActif >= 0) executer(options[indexActif]);
    } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      fermer('palette');
    }
  }

  const erreurs: string[] = [];
  if (ticketsAJour?.erreur) erreurs.push(`La recherche des tickets n’a pas abouti : ${ticketsAJour.erreur}`);
  if (chercherDossiers && dossiers?.erreur) erreurs.push(`La liste des dossiers n’a pas pu être lue : ${dossiers.erreur}`);

  const placeholder =
    role === 'SUPER_ADMIN'
      ? 'Aller à un écran ou lancer une action…'
      : role === 'CLIENT'
        ? 'Rechercher un dossier, une société ou une action…'
        : 'Rechercher un dossier, un ticket, une société ou une action…';

  const annonce = enCours
    ? 'Recherche en cours.'
    : options.length === 0
      ? 'Aucun résultat.'
      : `${options.length} résultat${options.length > 1 ? 's' : ''}.`;

  return (
    <Modal
      open
      onOpenChange={(o) => {
        if (!o) fermer('palette');
      }}
      title="Palette de commandes"
      size="lg"
      className="mb-auto mt-[10vh]"
    >
      <div className="space-y-3">
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg-subtle" aria-hidden="true" />
          <input
            type="text"
            role="combobox"
            aria-expanded="true"
            aria-controls={idListe}
            aria-activedescendant={idActif}
            aria-autocomplete="list"
            aria-label={placeholder.replace('…', '')}
            autoComplete="off"
            spellCheck={false}
            ref={champ}
            value={q}
            placeholder={placeholder}
            onChange={(e) => {
              setQ(e.target.value);
              setActif(0);
            }}
            onKeyDown={surTouche}
            data-testid="palette-champ"
            className="h-11 w-full rounded-lg border border-border-input bg-bg pl-9 pr-10 text-sm text-fg placeholder:text-fg-subtle focus:border-focus focus:outline-none focus-visible:ring-2 focus-visible:ring-focus/40"
          />
          {enCours && <Spinner className="absolute right-3 top-1/2 -translate-y-1/2 text-fg-subtle" />}
        </div>

        {erreurs.map((m) => (
          <p key={m} role="alert" className="flex items-start gap-2 rounded-lg bg-danger-soft px-3 py-2 text-sm text-danger">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <span>
              {m} Les autres résultats restent proposés ; réessayez dans un instant.
            </span>
          </p>
        ))}

        {rolesTickets(role) && rechercheNette.length === 1 && (
          <p className="text-xs text-fg-muted">Les tickets se cherchent à partir de deux caractères.</p>
        )}

        <div
          id={idListe}
          role="listbox"
          aria-label="Résultats"
          className="max-h-[min(55vh,26rem)] overflow-y-auto overscroll-contain"
          data-testid="palette-liste"
        >
          {ORDRE.map((g) => {
            const membres = options.filter((o) => o.groupe === g);
            if (membres.length === 0) return null;
            const idGroupe = `${idListe}-${g}`;
            return (
              <div key={g} role="group" aria-labelledby={idGroupe} className="pb-2">
                <p id={idGroupe} role="presentation" className="px-2 pb-1 pt-2 text-xs font-semibold uppercase tracking-wide text-fg-subtle">
                  {LIBELLES_GROUPE[g]}
                </p>
                {membres.map((c) => {
                  const i = options.indexOf(c);
                  const choisi = i === indexActif;
                  const Icone = c.icone;
                  return (
                    <div
                      key={c.id}
                      id={`${idOption}-${i}`}
                      role="option"
                      aria-selected={choisi}
                      data-testid={`palette-option-${c.id}`}
                      onMouseMove={() => {
                        if (!choisi) setActif(i);
                      }}
                      onMouseDown={(e) => e.preventDefault()}
                      onClick={() => executer(c)}
                      className={cn(
                        'flex cursor-pointer items-center gap-3 rounded-lg px-2 py-2 text-sm',
                        choisi ? 'bg-accent-soft text-fg' : 'text-fg-muted',
                      )}
                    >
                      <Icone className={cn('h-4 w-4 shrink-0', choisi ? 'text-accent' : 'text-fg-subtle')} aria-hidden="true" />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate font-medium text-fg">{c.libelle}</span>
                        {c.detail && <span className="block truncate text-xs text-fg-muted">{c.detail}</span>}
                      </span>
                      {c.raccourci && <Touches touches={c.raccourci} />}
                    </div>
                  );
                })}
              </div>
            );
          })}
          {!enCours && options.length === 0 && (
            <div className="px-2 py-8 text-center" data-testid="palette-vide">
              <p className="text-sm font-semibold text-heading">Aucun résultat pour « {rechercheNette} »</p>
              <p className="mt-1 text-sm text-fg-muted">
                {role === 'SUPER_ADMIN'
                  ? 'Essayez le nom d’un écran du menu ou d’une action (thème, raccourcis…).'
                  : 'Essayez la raison sociale, l’ICE, la référence du dossier ou du ticket, ou le nom d’un écran du menu.'}
              </p>
            </div>
          )}
        </div>

        <p className="flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-border pt-3 text-xs text-fg-muted">
          <span>
            <Touches touches={['↑']} /> <Touches touches={['↓']} /> choisir
          </span>
          <span>
            <Touches touches={['Entrée']} /> lancer
          </span>
          <span>
            <Touches touches={['Échap']} /> fermer
          </span>
        </p>
        <p role="status" aria-live="polite" className="sr-only">
          {annonce}
        </p>
      </div>
    </Modal>
  );
}
