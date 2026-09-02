import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AlertTriangle,
  Calendar,
  Loader2,
  RefreshCw,
  Sparkles,
} from 'lucide-react';
import { agentService } from '../../services/agent.service';
import type { AgentBriefing, EcheanceSignal, PlanItem } from '../../types/agent';

/**
 * Lot IA-1 — Panneau « Copilote » du dashboard employe.
 *
 * <p>Affiche le briefing du jour de l'agent (observe -> raisonne -> PROPOSE) :
 * (1) plan du jour priorise deep-linke, (2) echeances imminentes/depassees.
 * AUCUNE action irreversible n'est declenchee par l'agent — l'employe valide
 * chaque suggestion. (Perimetre : echeances + priorisation + briefing matinal.)
 */
export function CopilotePanel() {
  const navigate = useNavigate();
  const [data, setData] = useState<AgentBriefing | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;

    // `initial` = 1er chargement (spinner + null si echec) ; les refetch silencieux
    // (retour sur l'onglet) conservent les donnees precedentes en cas d'echec.
    const load = (initial: boolean) => {
      agentService
        .getMyBriefing()
        .then((b) => {
          if (!mounted) return;
          setData(b);
          // Marque vu (best-effort, ne bloque pas l'UI).
          if (!b.seenAt) agentService.markSeen(b.id).catch(() => undefined);
        })
        .catch(() => {
          if (mounted && initial) setData(null);
        })
        .finally(() => {
          if (mounted && initial) setLoading(false);
        });
    };

    load(true);

    // Re-fetch au retour sur l'onglet : le traitement d'un item se reflete sans clic
    // (coherent avec la re-agregation live cote backend).
    const onVisible = () => {
      if (document.visibilityState === 'visible') load(false);
    };
    document.addEventListener('visibilitychange', onVisible);

    return () => {
      mounted = false;
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, []);

  const handleRefresh = () => {
    setRefreshing(true);
    setError(null);
    agentService
      .refresh()
      .then((b) => {
        setData(b);
        setError(null);
      })
      .catch(() => setError('Actualisation impossible, réessayez.'))
      .finally(() => setRefreshing(false));
  };

  if (loading) {
    return (
      <div className="mt-6 flex h-24 items-center justify-center rounded-xl border border-border bg-bg-raised">
        <Loader2 className="h-5 w-5 animate-spin text-fg-subtle" />
      </div>
    );
  }
  // Degradation gracieuse : dashboard-service KO -> pas de panneau.
  if (!data) return null;

  const c = data.signaux.counts;
  const rienASignaler = c.echeances === 0 && c.tickets === 0 && c.demandes === 0;

  return (
    <div className="mt-6 rounded-xl border border-border bg-bg-raised p-5">
      {/* En-tete */}
      <div className="mb-4 flex items-start justify-between gap-3">
        <div className="flex items-center gap-2">
          <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-violet-100 text-violet-600">
            <Sparkles className="h-5 w-5" />
          </span>
          <div>
            <h3 className="text-lg font-semibold text-fg">Copilote</h3>
            <p className="text-xs text-fg-subtle">
              Suggestions de l'assistant — à valider
            </p>
          </div>
        </div>
        <button
          type="button"
          onClick={handleRefresh}
          disabled={refreshing}
          className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-bg-raised px-3 py-1.5 text-xs font-medium text-fg-subtle transition hover:bg-bg-overlay disabled:opacity-60"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${refreshing ? 'animate-spin' : ''}`} />
          Rafraîchir
        </button>
      </div>

      {/* Erreur d'actualisation (discrete, les donnees precedentes restent affichees) */}
      {error && (
        <div
          role="alert"
          className="mb-4 rounded-lg bg-danger/10 px-3 py-2 text-xs font-medium text-danger"
        >
          {error}
        </div>
      )}

      {/* Narration LLM (optionnelle) */}
      {data.texteLlm && (
        <div className="mb-4 whitespace-pre-line rounded-lg bg-violet-50 p-3 text-sm text-violet-900">
          {data.texteLlm}
        </div>
      )}

      {rienASignaler ? (
        <p className="py-6 text-center text-sm text-fg-subtle">
          Rien à signaler aujourd'hui — vos dossiers sont à jour.
        </p>
      ) : (
        <div className="space-y-5">
          {/* 1. À faire en priorité (plan du jour) */}
          <section>
            <h4 className="mb-2 text-sm font-semibold text-fg">À faire en priorité</h4>
            {data.planDuJour.length === 0 ? (
              <p className="py-3 text-center text-xs text-fg-subtle">Aucune action prioritaire</p>
            ) : (
              <ol className="space-y-2">
                {data.planDuJour.map((item) => (
                  <PlanRow key={`${item.ordre}-${item.targetId}`} item={item} onOpen={() => navigate(item.lien)} />
                ))}
              </ol>
            )}
          </section>

          {/* 2. Échéances */}
          {data.signaux.echeances.length > 0 && (
            <section>
              <h4 className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-fg">
                <Calendar className="h-4 w-4 text-fg-subtle" /> Échéances à ne pas manquer
              </h4>
              <ul className="divide-y divide-border">
                {data.signaux.echeances.slice(0, 8).map((e) => (
                  <EcheanceRow key={`${e.source}-${e.refId}`} e={e} onOpen={() => navigate(e.lien)} />
                ))}
              </ul>
            </section>
          )}
        </div>
      )}
    </div>
  );
}

const URGENCE_BADGE: Record<string, string> = {
  CRITIQUE: 'bg-danger/20 text-danger',
  HAUTE: 'bg-amber-100 text-amber-700',
  MOYENNE: 'bg-accent/10 text-accent',
  BASSE: 'bg-bg-overlay text-fg-muted',
};

function PlanRow({ item, onOpen }: { item: PlanItem; onOpen: () => void }) {
  return (
    <li
      role="button"
      tabIndex={0}
      onClick={onOpen}
      onKeyDown={(ev) => {
        if (ev.key === 'Enter' || ev.key === ' ') {
          ev.preventDefault();
          onOpen();
        }
      }}
      className="flex cursor-pointer items-start gap-3 rounded-lg border border-border bg-bg-raised p-3 transition hover:border-violet-300 hover:shadow-sm"
    >
      <span className="flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-full bg-bg-overlay text-xs font-bold text-fg-subtle">
        {item.ordre}
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium text-fg">{item.action}</p>
        <p className="text-xs text-fg-subtle">{item.justification}</p>
      </div>
      <span
        className={`flex-shrink-0 rounded-full px-2 py-0.5 text-[10px] font-bold ${
          URGENCE_BADGE[item.urgence] ?? 'bg-bg-overlay text-fg-muted'
        }`}
      >
        {item.urgence}
      </span>
    </li>
  );
}

function EcheanceRow({ e, onOpen }: { e: EcheanceSignal; onOpen: () => void }) {
  const badge = e.depassee
    ? 'bg-danger/20 text-danger'
    : e.joursRestants <= 3
      ? 'bg-danger/20 text-danger'
      : e.joursRestants <= 7
        ? 'bg-amber-100 text-amber-700'
        : 'bg-bg-overlay text-fg-muted';
  const label = e.depassee
    ? `Dépassée · ${Math.abs(e.joursRestants)} j`
    : `J-${e.joursRestants}`;
  return (
    <li
      role="button"
      tabIndex={0}
      onClick={onOpen}
      onKeyDown={(ev) => {
        if (ev.key === 'Enter' || ev.key === ' ') {
          ev.preventDefault();
          onOpen();
        }
      }}
      className="flex cursor-pointer items-center justify-between gap-3 py-2.5 text-sm hover:opacity-80"
    >
      <div className="min-w-0">
        <p className="flex items-center gap-1.5 truncate font-medium text-fg">
          {e.depassee && <AlertTriangle className="h-3.5 w-3.5 flex-shrink-0 text-danger" />}
          {e.intitule}
        </p>
        <p className="text-xs text-fg-subtle">
          {e.dossierNom}
          {e.date ? ` · ${new Date(e.date).toLocaleDateString('fr-FR')}` : ''}
        </p>
      </div>
      <span className={`flex-shrink-0 rounded-full px-2 py-0.5 text-[10px] font-bold ${badge}`}>
        {label}
      </span>
    </li>
  );
}
