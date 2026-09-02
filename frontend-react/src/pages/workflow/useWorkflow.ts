import {
  Fragment,
  createContext,
  createElement,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react';
import type { ReactNode } from 'react';
import { useParams } from 'react-router-dom';
import { extractError } from '../../lib/api';
import { ticketService } from '../../services/ticket.service';
import { workflowService } from '../../services/workflow.service';
import type { Ticket } from '../../types/ticket';
import { TICKET_TO_WORKFLOW, type ExecuteStepResult, type WorkflowProgress } from '../../types/workflow';

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
  registerDirty: (step: number, getter: DirtyGetter) => () => void;
  /**
   * Force un flush immediat (await-able) du draft de l'etape donnee
   * (defaut : viewStep courant). Utile avant un onBlur explicite.
   */
  flushDraft: (step?: number) => Promise<void>;
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

  // Refs : lectures stables pour les callbacks sans creer de churn de useCallback.
  const progressRef = useRef<WorkflowProgress | null>(null);
  progressRef.current = progress;

  // P1 2026-06-21 — registre des dirty getters par etape. Lu par flushDraft()
  // avant tout changement de viewStep pour persister les saisies sans submit.
  const dirtyGettersRef = useRef<Map<number, DirtyGetter>>(new Map());
  const ticketIdRef = useRef<string | undefined>(ticketId);
  ticketIdRef.current = ticketId;
  const viewStepRef = useRef<number | null>(viewStep);
  viewStepRef.current = viewStep;

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
      const p = await pPromise;
      setProgress(p);
      setViewStep((prev) => (prev == null ? p.currentStep : prev));
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }, [ticketId]);

  useEffect(() => {
    void load();
  }, [load]);

  const saveDraft = useCallback(
    async (currentStep: number, data: Record<string, unknown>) => {
      if (!ticketId) return;
      setSaving(true);
      try {
        const p = await workflowService.save(ticketId, currentStep, data);
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

  // P1 2026-06-21 — Helpers dirty getters + flush.
  // Le registre est un Map<step, getter> ; chaque etape s'y inscrit au mount
  // et se desinscrit au unmount. flushDraft appelle saveDraft directement
  // (pas la version useCallback) pour rester independant des hooks.
  const registerDirty = useCallback((step: number, getter: DirtyGetter) => {
    dirtyGettersRef.current.set(step, getter);
    return () => {
      // Cleanup : ne supprime que si c'est TOUJOURS notre getter (cas remount
      // StrictMode ou bascule d'etape rapide -- le dernier register gagne).
      if (dirtyGettersRef.current.get(step) === getter) {
        dirtyGettersRef.current.delete(step);
      }
    };
  }, []);

  const flushDraft = useCallback(async (step?: number) => {
    const tid = ticketIdRef.current;
    if (!tid) return;
    const target = step ?? viewStepRef.current ?? null;
    if (target == null) return;
    const getter = dirtyGettersRef.current.get(target);
    if (!getter) return;
    let payload: Record<string, unknown> | null = null;
    try {
      payload = getter();
    } catch {
      payload = null;
    }
    if (!payload) return;
    // Convention stepN, SAUF si le getter designe explicitement sa cle via
    // `__stepKey` (2026-08-14). Certaines pages regroupent plusieurs etapes sous
    // une meme cle — Modification range les etapes 3 et 4 dans `step3`. Sans ce
    // signal, la sauvegarde automatique aurait ecrit `step4`, un bloc que la page
    // ne relit jamais : la saisie aurait paru enregistree puis disparu au retour.
    const cle = typeof payload.__stepKey === 'string' ? payload.__stepKey : `step${target}`;
    const { __stepKey: _ignore, ...contenu } = payload;
    const wrapped: Record<string, unknown> = { [cle]: contenu };
    try {
      const p = await workflowService.save(tid, target, wrapped);
      setProgress(p);
    } catch (err) {
      // On ne bloque PAS la navigation sur un echec de draft (best-effort) ;
      // l'employe peut toujours valider l'etape pour persister.
      setError(extractError(err).message);
    }
  }, []);

  // ------------------------------------------------------------------
  //  Sauvegarde AUTOMATIQUE (2026-08-14)
  // ------------------------------------------------------------------
  // Jusqu'ici, une saisie n'etait persistee QUE sur navigation entre etapes
  // (flushDraft) ou sur clic explicite « Sauvegarder brouillon ». Un simple F5,
  // une deconnexion ou la fermeture de l'onglet perdait donc tout ce qui avait
  // ete tape depuis. Verifie en conditions reelles : trois champs saisis puis
  // rafraichissement -> les trois revenaient vides.
  //
  // Le stockage retenu est `workflow_progress.data` (JSONB Postgres), deja la
  // source de verite du wizard : scope par workspace, survit a la deconnexion et
  // au changement de poste, et relu tel quel au retour. Un `localStorage` aurait
  // desynchronise les postes et contourne le cloisonnement multi-tenant.
  //
  // Cadence : toutes les 20 s, et seulement si le payload a CHANGE (comparaison
  // du JSON serialise) — pas de requete inutile en lecture seule ou a l'arret.
  const lastSavedRef = useRef<string | null>(null);
  useEffect(() => {
    const AUTOSAVE_MS = 20_000;
    let stopped = false;

    const tick = async () => {
      if (stopped) return;
      const target = viewStepRef.current;
      const getter = target == null ? null : dirtyGettersRef.current.get(target);
      if (!getter) return;
      let payload: Record<string, unknown> | null = null;
      try {
        payload = getter();
      } catch {
        return;
      }
      if (!payload) return;
      const serialized = JSON.stringify(payload);
      if (serialized === lastSavedRef.current) return; // rien de neuf
      lastSavedRef.current = serialized;
      await flushDraft(target ?? undefined);
    };

    const id = window.setInterval(() => void tick(), AUTOSAVE_MS);
    // Onglet masque ou ferme : dernier flush avant de perdre la main.
    const onHide = () => {
      if (document.visibilityState === 'hidden') void tick();
    };
    document.addEventListener('visibilitychange', onHide);
    return () => {
      stopped = true;
      window.clearInterval(id);
      document.removeEventListener('visibilitychange', onHide);
    };
  }, [flushDraft]);

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
  return createElement(WorkflowCtx.Provider, { value: state }, children);
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
