import { Fragment, createContext, createElement, useCallback, useContext, useEffect, useRef, useState, useLayoutEffect } from 'react';
import type { ReactNode } from 'react';
import { useParams } from 'react-router-dom';
import { STORAGE_ACCESS, extractError } from '../../lib/api';
import { useAuthStore } from '../../store/authStore';
import { ConflitsBrouillon } from '../../components/workflow/ConflitsBrouillon';
import { ticketService } from '../../services/ticket.service';
import { workflowService } from '../../services/workflow.service';
import type { Ticket } from '../../types/ticket';
import { TICKET_TO_WORKFLOW, type ExecuteStepResult, type WorkflowProgress } from '../../types/workflow';
import {
  ATTENTE_MAX_MS,
  EnregistrementCtx,
  ErreurEnvoiFiable,
  LECTURE_SAISIE_MS,
  envoyerBrouillonFiable,
  type EtatEnregistrement,
} from './enregistrementAuto';
import type { RegisterDirty } from './useStepAutosave';
import {
  EVENEMENT_AVANT_DECONNEXION,
  EVENEMENT_SESSION_OUVERTE,
  accuserReception,
  canonique,
  ecrireBrouillon,
  effacerBrouillon,
  lireBrouillons,
  lireConflits,
  natureEchec,
  retirerSiIdentique,
  signalerParcoursOuvert,
  synchroniserBrouillons,
  type AuteurBrouillon,
  type BrouillonLocal,
} from './brouillonsLocaux';

/** Auteur des brouillons locaux : l'utilisateur connecte (aucun sans session). */
function auteurCourant(): AuteurBrouillon | null {
  const u = useAuthStore.getState().user;
  return u?.userId && u.workspaceId ? { userId: u.userId, workspaceId: u.workspaceId } : null;
}

/**
 * Fix 2026-06-11 — Cache module-scope (PAS un hook) qui partage la Promise du
 * load() initial entre les deux montages React StrictMode (dev). Sans ce cache :
 * deux `useEffect` concurrents tirent chacun un GET puis un /start ; le 1er
 * /start cree le workflow, le 2nd hit la contrainte unique et peut renvoyer 500.
 *
 * Placer cette Map ici (et NON dans un `useRef` du hook) evite deux problemes :
 *   1. Ce n'est plus un hook -> aucun risque d'invariant "ordre des hooks" en HMR.
 *   2. La Map survit aux re-mounts du composant (ce qui est exactement ce qu'on
 *      veut pour la deduplication, contrairement a un useRef qui repart vide
 *      a chaque montage React).
 *
 * La Promise est retiree de la Map des sa resolution (finally) pour qu'un
 * reload() ulterieur sur le meme ticketId puisse re-tirer un nouveau load.
 */
const loadPromiseByTicket = new Map<string, Promise<WorkflowProgress>>();

/**
 * Sprint 2026-06-21 (Cowork P1) — Getter "dirty" expose par chaque etape.
 *
 * Une etape s'enregistre au montage via {@link UseWorkflowState.registerDirty}
 * en fournissant un getter qui retourne le payload courant des champs (ou
 * `null` si pas de donnees a sauver). Avant chaque changement de `viewStep`
 * (navigation goToStep/goPrev/goNext), useWorkflow appelle ce getter et fait
 * un {@code saveDraft} synchrone -> les valeurs saisies sont persistees meme
 * si l'employe ne clique pas "Valider".
 */
export type DirtyGetter = () => Record<string, unknown> | null;

export interface UseWorkflowState {
  ticketId: string | undefined;
  ticket: Ticket | null;
  progress: WorkflowProgress | null;
  /** Etape actuellement AFFICHEE par l'UI (peut differer de progress.currentStep). */
  viewStep: number;
  /**
   * High-water mark : etape la plus avancee jamais atteinte = progress.currentStep.
   * Les etapes ≤ maxStep sont navigables (cliquables dans le roadmap). Les etapes
   * > maxStep sont verrouillees tant que les pre-requis ne sont pas valides.
   */
  maxStep: number;
  loading: boolean;
  saving: boolean;
  error: string | null;
  setError: (e: string | null) => void;
  stepData: Record<string, Record<string, unknown>>;
  saveDraft: (currentStep: number, data: Record<string, unknown>) => Promise<void>;
  executeStep: (step: number, payload: Record<string, unknown>) => Promise<ExecuteStepResult | null>;
  reload: () => Promise<void>;
  /**
   * Va a l'etape n si elle est deja atteinte (n ≤ maxStep). Bloque sinon.
   *
   * P1 2026-06-21 : flush synchrone du draft de l'etape courante AVANT la
   * navigation (via dirty getters enregistres), pour ne JAMAIS perdre une
   * saisie en quittant l'etape sans cliquer "Valider".
   */
  goToStep: (n: number) => Promise<void> | void;
  goPrev: () => Promise<void> | void;
  goNext: () => Promise<void> | void;
  /**
   * Enregistre un getter pour l'etape donnee. Retourne une fonction
   * d'unregister a appeler en cleanup (useEffect return). Si plusieurs
   * appels se succedent pour la meme etape, le dernier gagne (cas re-mount
   * StrictMode).
   */
  registerDirty: RegisterDirty;
  /** Lot L7 (E2) : etat de l'enregistrement automatique (indicateur a l'ecran). */
  enregistrement: EtatEnregistrement;
  /**
   * Force un flush immediat (await-able) du draft de l'etape donnee
   * (defaut : viewStep courant). Utile avant un onBlur explicite.
   */
  flushDraft: (step?: number) => Promise<void>;
  /**
   * Lot L7 (E12) : brouillons de ce navigateur en conflit avec le serveur (l'etape a
   * ete modifiee ailleurs depuis). Rien n'est ecrase tant que l'employe n'a pas choisi.
   */
  conflits: BrouillonLocal[];
  resoudreConflit: (b: BrouillonLocal, garder: 'local' | 'serveur') => Promise<void>;
  /** Incremente quand les champs doivent relire le serveur (brouillon local retenu). */
  generation: number;
}

/**
 * Hook generique d'orchestration des wizards workflow (9 types).
 *
 * P0 fix 2026-06-04 (Rules of Hooks defensive refactor) :
 *  - TOUS les hooks (useState, useRef, useCallback, useEffect) sont declares
 *    UNE FOIS chacun au TOP du hook, dans le MEME ORDRE a chaque render.
 *  - AUCUN return anticipe entre les hooks.
 *  - Les callbacks (goToStep/goPrev/goNext) ont des DEPS STABLES (refs)
 *    pour ne pas etre recrees a chaque changement de `progress` -- ce qui
 *    pourrait perturber un consumer qui ferait `useEffect(..., [goToStep])`.
 *  - Le hook count est invariant entre tous les renders, peu importe les
 *    valeurs de loading/progress/ticket.
 */
function useWorkflowEngine(): UseWorkflowState {
  // --- HOOKS (ordre fixe, jamais conditionnel) ---
  const { ticketId } = useParams<{ ticketId: string }>();

  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [progress, setProgress] = useState<WorkflowProgress | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [viewStep, setViewStep] = useState<number | null>(null);
  const [enregistrement, setEnregistrement] = useState<EtatEnregistrement>({ type: 'repos' });
  const [conflits, setConflits] = useState<BrouillonLocal[]>([]);
  const [generation, setGeneration] = useState(0);
  // Lot L7 (E12) : version du serveur connue par cle d'etape (forme canonique), base
  // des brouillons locaux ; derniere saisie ecrite dans ce navigateur, par cle.
  const serveurConnuRef = useRef<Map<string, string>>(new Map());
  const dernierLocalRef = useRef<Map<string, string>>(new Map());

  // Refs : lectures stables pour les callbacks sans creer de churn de useCallback.
  const progressRef = useRef<WorkflowProgress | null>(null);

  // P1 2026-06-21 — registre des dirty getters par etape. Lu par flushDraft()
  // avant tout changement de viewStep pour persister les saisies sans submit.
  const dirtyGettersRef = useRef<Map<number, DirtyGetter>>(new Map());
  // Derniere saisie enregistree par cle d'etape (brouillon ou etape validee) : une saisie
  // identique n'est pas renvoyee (moteur d'enregistrement automatique, plus bas).
  const dernierEnregistreRef = useRef<Map<string, string>>(new Map());
  const ticketIdRef = useRef<string | undefined>(ticketId);
  const viewStepRef = useRef<number | null>(viewStep);
  // Lot L6 : les refs ne s'ecrivent pas pendant le rendu ; elles suivent l'etat juste apres,
  // avant tout effet et tout evenement qui les lit.
  useLayoutEffect(() => {
    progressRef.current = progress;
    ticketIdRef.current = ticketId;
    viewStepRef.current = viewStep;
  });

  // load() est stable tant que ticketId ne change pas. Pas de dep sur progress.
  // La deduplication entre 2 mounts StrictMode est assuree par
  // {@link loadPromiseByTicket} (module-scope) -- voir les notes en bas du fichier.
  const load = useCallback(async () => {
    if (!ticketId) {
      setLoading(false);
      return;
    }
    try {
      const t = await ticketService.get(ticketId);
      setTicket(t);
      const wfType = TICKET_TO_WORKFLOW[t.type];
      // Recuperation OU creation : on partage la Promise pour eviter le race
      // StrictMode (deux montages = un seul appel reseau).
      let pPromise = loadPromiseByTicket.get(ticketId);
      if (!pPromise) {
        pPromise = (async () => {
          try {
            return await workflowService.get(ticketId);
          } catch {
            return await workflowService.start(ticketId, wfType);
          }
        })();
        loadPromiseByTicket.set(ticketId, pPromise);
        // Liberation de la Promise une fois resolue, pour autoriser un reload() ulterieur.
        void pPromise.finally(() => loadPromiseByTicket.delete(ticketId));
      }
      let p = await pPromise;
      // Lot L7 (E12) : a l'ouverture du parcours, la saisie gardee dans ce navigateur
      // (session expiree, coupure) est renvoyee AVANT que les champs ne montent : ils
      // relisent alors le serveur, qui la porte. Conflit : rien n'est ecrase.
      const auteur = auteurCourant();
      if (auteur) {
        if (progressRef.current == null && lireBrouillons(auteur, ticketId).length > 0) {
          try {
            p = (await synchroniserBrouillons(auteur, ticketId, p)).progress;
          } catch {
            // Renvoi impossible : les brouillons restent dans ce navigateur.
          }
        }
        setConflits(lireConflits(auteur, ticketId));
      }
      serveurConnuRef.current = new Map(
        Object.entries(p.data ?? {}).map(([k, v]) => [k, canonique(v)]),
      );
      setProgress(p);
      setViewStep((prev) => (prev == null ? p.currentStep : prev));
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }, [ticketId]);

  useEffect(() => {
    // Chargement par promesse : aucun etat pose de facon synchrone dans l'effet.
    Promise.resolve().then(load).catch(() => undefined);
  }, [load]);

  const saveDraft = useCallback(
    async (currentStep: number, data: Record<string, unknown>) => {
      if (!ticketId) return;
      setSaving(true);
      try {
        const p = await workflowService.save(ticketId, currentStep, data);
        const auteur = auteurCourant();
        for (const [cle, contenu] of Object.entries(data)) {
          if (!contenu || typeof contenu !== 'object') continue;
          serveurConnuRef.current.set(cle, canonique(contenu));
          dernierLocalRef.current.delete(cle);
          if (auteur) accuserReception(auteur, ticketId, cle, contenu as Record<string, unknown>);
        }
        setProgress(p);
      } catch (err) {
        setError(extractError(err).message);
      } finally {
        setSaving(false);
      }
    },
    [ticketId],
  );

  const executeStep = useCallback(
    async (step: number, payload: Record<string, unknown>): Promise<ExecuteStepResult | null> => {
      if (!ticketId) return null;
      setError(null);
      setSaving(true);
      try {
        const r = await workflowService.executeStep(ticketId, step, payload);
        setProgress(r.progress);
        if (!r.advanced) {
          const msg =
            (r.stepData?.message as string) ??
            (r.stepData?.error as string) ??
            "L'etape ne peut pas etre validee. Verifiez les donnees.";
          setError(msg);
        } else {
          // Lot L7 (E12) : l'etape validee est au serveur ; son brouillon local n'a plus d'objet.
          const cle = `step${step}`;
          serveurConnuRef.current.set(cle, canonique(r.progress.data?.[cle]));
          // Lot L6b (parcours d'import) : la saisie validee est au serveur, ENRICHIE par lui
          // (step2.siege, step3.capital...). Le demontage de l'etape ne doit pas la renvoyer
          // en brouillon : POST /save remplace le sac de l'etape et effacerait cet ajout.
          const getter = dirtyGettersRef.current.get(step);
          if (getter) {
            try {
              const saisie = getter();
              if (saisie) {
                const cleSaisie = typeof saisie.__stepKey === 'string' ? saisie.__stepKey : cle;
                const contenu: Record<string, unknown> = { ...saisie };
                delete contenu.__stepKey;
                dernierEnregistreRef.current.set(cleSaisie, JSON.stringify(contenu));
              }
            } catch {
              // getter en erreur : rien a marquer.
            }
          }
          dernierLocalRef.current.delete(cle);
          const auteur = auteurCourant();
          if (auteur) {
            for (const b of lireBrouillons(auteur, ticketId)) {
              if (b.cle === cle && !b.conflit) effacerBrouillon(b);
            }
          }
          const nextDisplayStep = step >= r.progress.totalSteps ? step : step + 1;
          setViewStep(nextDisplayStep);
        }
        return r;
      } catch (err) {
        setError(extractError(err).message);
        return null;
      } finally {
        setSaving(false);
      }
    },
    [ticketId],
  );

  // ------------------------------------------------------------------
  //  Lot L7 (E2) -- AUCUNE SAISIE PERDUE
  // ------------------------------------------------------------------
  // Chaque etape inscrit un getter de sa saisie (useStepAutosave). Le moteur :
  //  - lit la saisie de l'etape affichee chaque seconde et l'enregistre des
  //    qu'elle est stable (pause apres la frappe), au plus tard apres 5 s de
  //    frappe continue ;
  //  - l'enregistre avant toute navigation (goToStep/goPrev/goNext) ;
  //  - l'enregistre quand l'etape se demonte (bouton "Quitter", changement de page) ;
  //  - l'envoie de facon fiable (fetch keepalive) quand l'onglet se masque ou se
  //    ferme (visibilitychange, pagehide).
  // Une saisie identique a la derniere enregistree n'est pas renvoyee. Les envois
  // ordinaires passent l'un apres l'autre (une ancienne saisie ne peut pas
  // ecraser une plus recente). L'etat est publie pour l'indicateur a l'ecran ;
  // un echec y est ecrit en clair et la saisie sera renvoyee.
  //
  // Le stockage reste `workflow_progress.data` (JSONB, cloisonne par workspace),
  // deja la source de verite du parcours (decision du 2026-08-14).
  const fileRef = useRef<Promise<void>>(Promise.resolve());
  // Saisie dont l'envoi a echoue : la lecture periodique ne la renvoie pas en
  // boucle ; elle repart a la prochaine modification, navigation ou sortie, au
  // retour du reseau ou a la reprise de la session (lot L7, E12).
  const echecRef = useRef<Map<string, string>>(new Map());

  /**
   * Lot L7 (E12) : ecrit la saisie dans ce navigateur AVANT tout envoi. Elle y reste
   * jusqu'a l'accuse du serveur. Renvoie faux si le navigateur refuse de la garder.
   */
  const garderLocalement = useCallback(
    (step: number, cle: string, contenu: Record<string, unknown>, serialise: string): boolean => {
      const auteur = auteurCourant();
      const tid = ticketIdRef.current;
      if (!auteur || !tid) return false;
      if (dernierLocalRef.current.get(cle) === serialise) return true;
      const base = serveurConnuRef.current.get(cle) ?? canonique(undefined);
      const ok = ecrireBrouillon(auteur, tid, cle, step, contenu, base);
      if (ok) dernierLocalRef.current.set(cle, serialise);
      return ok;
    },
    [],
  );

  /** Envoi d'une saisie deja serialisee (et deja gardee localement). */
  const envoyer = useCallback(
    (step: number, cle: string, contenu: Record<string, unknown>, serialise: string,
      gardee: boolean, fiable: boolean): Promise<void> => {
      const tid = ticketIdRef.current;
      if (!tid) return Promise.resolve();
      const wrapped: Record<string, unknown> = { [cle]: contenu };
      const envoi = async () => {
        setEnregistrement({ type: 'en-cours' });
        try {
          const p = fiable
            ? await envoyerBrouillonFiable(tid, step, wrapped)
            : await workflowService.save(tid, step, wrapped);
          if (p) setProgress(p);
          echecRef.current.delete(cle);
          serveurConnuRef.current.set(cle, canonique(contenu));
          dernierLocalRef.current.delete(cle);
          const auteur = auteurCourant();
          if (auteur) accuserReception(auteur, tid, cle, contenu);
          setEnregistrement({ type: 'enregistre', a: new Date() });
        } catch (err) {
          // La saisie reste a l'ecran (et dans ce navigateur) ; elle sera renvoyee.
          if (dernierEnregistreRef.current.get(cle) === serialise) {
            dernierEnregistreRef.current.delete(cle);
          }
          echecRef.current.set(cle, serialise);
          const nature = natureEchec(err);
          const auteur = auteurCourant();
          if (nature === 'refus') {
            // Refus definitif du serveur (droit, ticket clos) : la renvoyer a chaque
            // connexion n'y changerait rien ; le message reste a l'ecran.
            if (auteur) retirerSiIdentique(auteur, tid, cle, contenu);
            dernierLocalRef.current.delete(cle);
          }
          if (nature === 'session' && gardee) {
            setEnregistrement({ type: 'session-expiree' });
          } else if (nature === 'hors-ligne' && gardee) {
            setEnregistrement({ type: 'hors-ligne' });
          } else {
            const message = err instanceof ErreurEnvoiFiable ? err.message : extractError(err).message;
            setEnregistrement({ type: 'erreur', message, gardeLocalement: gardee && nature !== 'refus' });
          }
        }
      };
      // Page qui se ferme : envoi immediat, sans attendre la file.
      if (fiable) return envoi();
      const suite = fileRef.current.then(envoi);
      fileRef.current = suite;
      return suite;
    },
    [],
  );

  const enregistrerSiChange = useCallback(
    (step: number, getter: DirtyGetter, fiable = false): Promise<void> => {
      const tid = ticketIdRef.current;
      if (!tid) return Promise.resolve();
      let payload: Record<string, unknown> | null = null;
      try {
        payload = getter();
      } catch {
        payload = null;
      }
      if (!payload) return Promise.resolve();
      // Convention stepN, SAUF si le getter designe explicitement sa cle via
      // `__stepKey` (2026-08-14). Certaines pages regroupent plusieurs etapes sous
      // une meme cle -- Modification range les etapes 3 et 4 dans `step3`.
      const cle = typeof payload.__stepKey === 'string' ? payload.__stepKey : `step${step}`;
      const contenu: Record<string, unknown> = { ...payload };
      delete contenu.__stepKey;
      const serialise = JSON.stringify(contenu);
      if (dernierEnregistreRef.current.get(cle) === serialise) return Promise.resolve();
      dernierEnregistreRef.current.set(cle, serialise);
      // Lot L7 (E12) : ecrite dans ce navigateur AVANT l'envoi (401, coupure, fermeture).
      const gardee = garderLocalement(step, cle, contenu, serialise);
      return envoyer(step, cle, contenu, serialise, gardee, fiable);
    },
    [garderLocalement, envoyer],
  );

  // P1 2026-06-21 -- registre des getters par etape ; chaque etape s'y inscrit au
  // montage et se desinscrit au demontage (avec un dernier envoi, lot L7).
  const registerDirty = useCallback<RegisterDirty>((step, getter) => {
    dirtyGettersRef.current.set(step, getter);
    // Point de depart : la saisie telle qu'affichee a l'ouverture de l'etape. Une
    // etape ouverte puis quittee sans rien changer n'envoie rien.
    try {
      const initial = getter();
      if (initial) {
        const cle = typeof initial.__stepKey === 'string' ? initial.__stepKey : `step${step}`;
        if (!dernierEnregistreRef.current.has(cle)) {
          const contenu: Record<string, unknown> = { ...initial };
          delete contenu.__stepKey;
          dernierEnregistreRef.current.set(cle, JSON.stringify(contenu));
        }
      }
    } catch {
      // getter en erreur a l'ouverture : la premiere lecture reussie sera envoyee.
    }
    return (options) => {
      if (options?.flush) void enregistrerSiChange(step, getter);
      // Cleanup : ne supprime que si c'est TOUJOURS notre getter (cas remount
      // StrictMode ou bascule d'etape rapide -- le dernier register gagne).
      if (dirtyGettersRef.current.get(step) === getter) {
        dirtyGettersRef.current.delete(step);
      }
    };
  }, [enregistrerSiChange]);

  const flushDraft = useCallback(async (step?: number) => {
    const target = step ?? viewStepRef.current ?? null;
    if (target == null) return;
    const getter = dirtyGettersRef.current.get(target);
    if (!getter) return;
    // On ne bloque PAS la navigation sur un echec (l'indicateur l'affiche).
    await enregistrerSiChange(target, getter);
  }, [enregistrerSiChange]);

  useEffect(() => {
    // Lecture de la saisie de l'etape affichee : envoi apres une pause, ou apres
    // ATTENTE_MAX_MS de frappe continue.
    let derniereLecture: string | null = null;
    let enAttenteDepuis: number | null = null;

    const lireCourante = (): { target: number; getter: DirtyGetter; cle: string;
      contenu: Record<string, unknown>; serialise: string } | null => {
      const target = viewStepRef.current;
      const getter = target == null ? null : dirtyGettersRef.current.get(target);
      if (target == null || !getter) return null;
      let payload: Record<string, unknown> | null = null;
      try {
        payload = getter();
      } catch {
        return null;
      }
      if (!payload) return null;
      const cle = typeof payload.__stepKey === 'string' ? payload.__stepKey : `step${target}`;
      const contenu: Record<string, unknown> = { ...payload };
      delete contenu.__stepKey;
      return { target, getter, cle, contenu, serialise: JSON.stringify(contenu) };
    };

    const lire = () => {
      const l = lireCourante();
      if (!l) return;
      const { target, getter, cle, contenu, serialise } = l;
      if (dernierEnregistreRef.current.get(cle) === serialise || echecRef.current.get(cle) === serialise) {
        derniereLecture = null;
        enAttenteDepuis = null;
        return;
      }
      // Lot L7 (E12) : la saisie est gardee dans ce navigateur des qu'elle est lue,
      // avant meme l'envoi (au plus une seconde de frappe expose a une fermeture brutale).
      garderLocalement(target, cle, contenu, serialise);
      const maintenant = Date.now();
      const stable = serialise === derniereLecture;
      derniereLecture = serialise;
      if (enAttenteDepuis == null) enAttenteDepuis = maintenant;
      if (!stable && maintenant - enAttenteDepuis < ATTENTE_MAX_MS) return;
      derniereLecture = null;
      enAttenteDepuis = null;
      void enregistrerSiChange(target, getter);
    };

    // Onglet masque ou ferme : envoi fiable de ce qui n'est pas encore parti (la
    // saisie est ecrite dans ce navigateur avant l'envoi).
    const envoiFiable = () => {
      const target = viewStepRef.current;
      const getter = target == null ? null : dirtyGettersRef.current.get(target);
      if (target != null && getter) void enregistrerSiChange(target, getter, true);
    };
    const onVisibilite = () => {
      if (document.visibilityState === 'hidden') envoiFiable();
    };

    // Lot L7 (E12) : retour du reseau, nouvelle session (ici ou dans un autre onglet) :
    // la saisie en echec et les brouillons de ce ticket repartent.
    const relancer = () => {
      echecRef.current.clear();
      const auteur = auteurCourant();
      const tid = ticketIdRef.current;
      const l = lireCourante();
      if (l) void enregistrerSiChange(l.target, l.getter);
      if (!auteur || !tid) return;
      // Brouillons des autres etapes : renvoyes avec le controle de conflit (le
      // serveur doit porter encore la version sur laquelle ils ont ete faits).
      if (!lireBrouillons(auteur, tid).some((b) => !b.conflit && b.cle !== l?.cle)) return;
      synchroniserBrouillons(auteur, tid, undefined, l?.cle)
        .then((r) => {
          for (const [k, v] of Object.entries(r.progress.data ?? {})) {
            if (k !== l?.cle) serveurConnuRef.current.set(k, canonique(v));
          }
          setProgress(r.progress);
          setConflits(r.conflits);
        })
        .catch(() => undefined);
    };
    const onStockage = (e: StorageEvent) => {
      if (e.key === STORAGE_ACCESS && e.newValue) relancer();
    };
    // Deconnexion volontaire : la saisie a l'ecran est ecrite, l'appelant l'envoie.
    const avantDeconnexion = () => {
      const l = lireCourante();
      if (l && dernierEnregistreRef.current.get(l.cle) !== l.serialise) {
        garderLocalement(l.target, l.cle, l.contenu, l.serialise);
      }
    };

    const id = window.setInterval(lire, LECTURE_SAISIE_MS);
    document.addEventListener('visibilitychange', onVisibilite);
    window.addEventListener('pagehide', envoiFiable);
    window.addEventListener('online', relancer);
    window.addEventListener(EVENEMENT_SESSION_OUVERTE, relancer);
    window.addEventListener('storage', onStockage);
    window.addEventListener(EVENEMENT_AVANT_DECONNEXION, avantDeconnexion);
    return () => {
      window.clearInterval(id);
      document.removeEventListener('visibilitychange', onVisibilite);
      window.removeEventListener('pagehide', envoiFiable);
      window.removeEventListener('online', relancer);
      window.removeEventListener(EVENEMENT_SESSION_OUVERTE, relancer);
      window.removeEventListener('storage', onStockage);
      window.removeEventListener(EVENEMENT_AVANT_DECONNEXION, avantDeconnexion);
    };
  }, [enregistrerSiChange, garderLocalement]);

  // Lot L7 (E12) : le renvoi global (reconnexion, retour du reseau) laisse ce ticket
  // au moteur, qui connait la saisie a l'ecran.
  useEffect(() => (ticketId ? signalerParcoursOuvert(ticketId) : undefined), [ticketId]);

  /**
   * Lot L7 (E12) : choix de l'employe face a un brouillon en conflit. `local` : la
   * saisie de ce navigateur remplace l'etape au serveur et les champs la relisent ;
   * `serveur` : le brouillon local est efface.
   */
  const resoudreConflit = useCallback(async (b: BrouillonLocal, garder: 'local' | 'serveur') => {
    const tid = ticketIdRef.current;
    if (!tid) return;
    if (garder === 'serveur') {
      effacerBrouillon(b);
      setConflits((c) => c.filter((x) => x.idConflit !== b.idConflit));
      return;
    }
    setError(null);
    try {
      const p = await workflowService.save(tid, b.step, { [b.cle]: b.contenu });
      effacerBrouillon(b);
      // Une saisie faite depuis sur la version du serveur est remplacee par ce choix.
      const auteur = auteurCourant();
      if (auteur) {
        for (const x of lireBrouillons(auteur, tid)) if (x.cle === b.cle && !x.conflit) effacerBrouillon(x);
      }
      serveurConnuRef.current.set(b.cle, canonique(b.contenu));
      dernierEnregistreRef.current.set(b.cle, JSON.stringify(b.contenu));
      dernierLocalRef.current.delete(b.cle);
      setProgress(p);
      setConflits((c) => c.filter((x) => x.idConflit !== b.idConflit));
      setGeneration((g) => g + 1);
    } catch (err) {
      setError(extractError(err).message);
    }
  }, []);

  // goToStep / goPrev / goNext : DEPS STABLES (vide) -- lisent progressRef.current.
  // Garantit que la reference de la callback ne change pas entre renders.
  // P1 2026-06-21 : await flushDraft() AVANT le changement de viewStep.
  const goToStep = useCallback(async (n: number) => {
    setError(null);
    await flushDraft();
    const p = progressRef.current;
    const hwm = p?.currentStep ?? 1;
    const total = p?.totalSteps ?? 99;
    const target = Math.max(1, Math.min(n, hwm, total));
    setViewStep((prev) => (target !== prev ? target : prev));
  }, [flushDraft]);

  const goPrev = useCallback(async () => {
    setError(null);
    await flushDraft();
    setViewStep((prev) => {
      const fallback = progressRef.current?.currentStep ?? 1;
      const cur = prev ?? fallback;
      return Math.max(1, cur - 1);
    });
  }, [flushDraft]);

  const goNext = useCallback(async () => {
    setError(null);
    await flushDraft();
    setViewStep((prev) => {
      const p = progressRef.current;
      const hwm = p?.currentStep ?? 1;
      const total = p?.totalSteps ?? 99;
      const cur = prev ?? hwm;
      return Math.min(cur + 1, hwm, total);
    });
  }, [flushDraft]);

  // --- FIN DES HOOKS. Toute logique derivee en dessous est PURE (pas de hook). ---

  const stepData = (progress?.data as Record<string, Record<string, unknown>>) ?? {};
  const maxStep = progress?.currentStep ?? 1;
  const effectiveViewStep = viewStep ?? maxStep;

  return {
    ticketId,
    ticket,
    progress,
    viewStep: effectiveViewStep,
    maxStep,
    loading,
    saving,
    error,
    setError,
    stepData,
    saveDraft,
    executeStep,
    reload: load,
    goToStep,
    goPrev,
    goNext,
    registerDirty,
    flushDraft,
    enregistrement,
    conflits,
    resoudreConflit,
    generation,
  };
}

// ======================================================================
//  Montage differe — corrige la perte des saisies apres reconnexion
// ======================================================================

/**
 * LE DEFAUT (2026-08-14). Chaque page d'etape initialise ses champs ainsi :
 *
 * ```ts
 * const [dateAG, setDateAG] = useState((stepData.step1?.dateAG as string) ?? '');
 * ```
 *
 * Or `useState(x)` ne lit `x` qu'au **PREMIER** render. A ce moment la requete
 * n'a pas repondu : `progress` est `null`, donc `stepData` vaut `{}` et TOUS les
 * champs se figent a la chaine vide. Quand les donnees arrivent, React ne
 * re-initialise rien — les etats gardent leur valeur initiale.
 *
 * Le `if (loading) return <Loader/>` present dans les pages n'y change rien : il
 * s'execute APRES les hooks, qui ont deja fige les valeurs vides.
 *
 * Invisible en usage courant, parce qu'on enchaine les etapes sans recharger la
 * page : les etats sont alors deja peuples. Le defaut ne se voit qu'apres un
 * remontage a froid — F5, deconnexion/reconnexion, ou simple retour sur le
 * ticket : l'etape est marquee validee mais tous ses champs sont vides.
 *
 * LA CORRECTION. On ne monte le formulaire qu'UNE FOIS les donnees chargees.
 * Le chargement vit desormais dans {@link WorkflowDataProvider} ; les pages
 * continuent d'appeler `useWorkflow()` sans changer une ligne de leur corps —
 * le hook lit le contexte au lieu de declencher le chargement lui-meme.
 *
 * Corriger page par page (un `useEffect` de re-hydratation par champ) aurait
 * demande des dizaines de synchronisations, chacune capable d'ecraser une saisie
 * en cours. Ici, le probleme disparait a la racine.
 */
const WorkflowCtx = createContext<UseWorkflowState | null>(null);

export interface WorkflowDataProviderProps {
  /** Rendu tant que le workflow n'est pas charge (evite de monter les champs). */
  fallback?: ReactNode;
  /** Rendu si le chargement echoue (workflow ou ticket introuvable). */
  renderError?: (message: string) => ReactNode;
  children: ReactNode;
}

export function WorkflowDataProvider({
  fallback,
  renderError,
  children,
}: WorkflowDataProviderProps) {
  const state = useWorkflowEngine();

  // Tant que le chargement court, on ne monte PAS les enfants : c'est tout
  // l'objet de ce provider (cf. note ci-dessus).
  if (state.loading) {
    return createElement(Fragment, null, fallback ?? null);
  }
  if (!state.ticket || !state.progress) {
    const msg = state.error ?? 'Workflow introuvable';
    return createElement(Fragment, null, renderError ? renderError(msg) : null);
  }
  // Lot L7 (E12) : un brouillon de ce navigateur en conflit avec le serveur est
  // presente au-dessus du parcours ; `generation` remonte les champs quand
  // l'employe retient la saisie locale (ils relisent alors le serveur).
  return createElement(
    WorkflowCtx.Provider,
    { value: state },
    createElement(
      EnregistrementCtx.Provider,
      { value: state.enregistrement },
      createElement(ConflitsBrouillon, { conflits: state.conflits, onResoudre: state.resoudreConflit }),
      createElement(Fragment, { key: state.generation }, children),
    ),
  );
}

/**
 * Etat du wizard. DOIT etre appele sous un {@link WorkflowDataProvider} : c'est
 * lui qui garantit que `stepData` est deja peuple au premier render, donc que
 * les `useState(stepData.stepN?.x)` des pages voient les valeurs persistees.
 */
export function useWorkflow(): UseWorkflowState {
  const ctx = useContext(WorkflowCtx);
  if (!ctx) {
    throw new Error(
      'useWorkflow() doit etre appele sous <WorkflowDataProvider> : sans lui, les '
        + 'champs des etapes s\'initialisent avant l\'arrivee des donnees et restent vides.',
    );
  }
  return ctx;
}
