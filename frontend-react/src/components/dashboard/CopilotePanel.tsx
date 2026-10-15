import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, Calendar, RefreshCw, Sparkles } from 'lucide-react';
import { agentService } from '../../services/agent.service';
import type { AgentBriefing, EcheanceSignal, PlanItem } from '../../types/agent';
import { Card } from '../ui/Card';
import { Button } from '../ui/Button';
import { Badge, type BadgeVariant } from '../ui/Badge';
import { Message } from '../ui/Message';
import { SqueletteTexte, SqueletteZone } from '../ui/LoadingSkeleton';

/**
 * Lot IA-1 -- Panneau « Copilote » du dashboard employe.
 *
 * <p>Affiche le briefing du jour de l'agent (observe -> raisonne -> PROPOSE) :
 * (1) plan du jour priorise deep-linke, (2) echeances imminentes/depassees.
 * AUCUNE action irreversible n'est declenchee par l'agent -- l'employe valide
 * chaque suggestion. (Perimetre : echeances + priorisation + briefing matinal.)
 *
 * Lot L6b (E9) : composants de la bibliotheque ; chaque suggestion est un lien (clavier) ;
 * un echec du premier chargement est dit au lieu de faire disparaitre le panneau.
 */
export function CopilotePanel() {
  const [data, setData] = useState<AgentBriefing | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;

    // `initial` = 1er chargement (squelette + null si echec) ; les refetch silencieux
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

  const titre = (
    <span className="inline-flex items-center gap-2">
      <Sparkles className="h-4 w-4 text-fg-subtle" aria-hidden="true" />
      Copilote
    </span>
  );

  if (loading) {
    return (
      <Card title={titre} description="Suggestions de l’assistant, à valider" className="mt-6">
        <SqueletteZone libelle="Chargement des suggestions…">
          <SqueletteTexte lignes={4} />
        </SqueletteZone>
      </Card>
    );
  }
  if (!data) {
    return (
      <Message ton="info" titre="Copilote indisponible" className="mt-6">
        Les suggestions du jour n’ont pas pu être chargées. Elles reviendront au prochain chargement de la page.
      </Message>
    );
  }

  // Lot L6b (E9) : un briefing partiel (sans signaux ni plan) ne plante plus l'ecran.
  const echeances = data.signaux?.echeances ?? [];
  const plan = data.planDuJour ?? [];
  const c = data.signaux?.counts;
  const rienASignaler = c
    ? (c.echeances ?? 0) === 0 && (c.tickets ?? 0) === 0 && (c.demandes ?? 0) === 0
    : plan.length === 0 && echeances.length === 0;

  return (
    <Card
      title={titre}
      description="Suggestions de l’assistant, à valider"
      className="mt-6"
      actions={
        <Button type="button" variant="secondary" size="sm" onClick={handleRefresh} loading={refreshing} icon={<RefreshCw />}>
          Rafraîchir
        </Button>
      }
    >
      {/* Erreur d'actualisation (discrete, les donnees precedentes restent affichees) */}
      {error && (
        <Message ton="erreur" className="mb-4">
          {error}
        </Message>
      )}

      {/* Narration LLM (optionnelle) */}
      {data.texteLlm && <div className="mb-4 whitespace-pre-line rounded-lg bg-info-soft p-3 text-sm text-fg">{data.texteLlm}</div>}

      {rienASignaler ? (
        <p className="py-6 text-center text-sm text-fg-muted">Rien à signaler aujourd’hui : vos dossiers sont à jour.</p>
      ) : (
        <div className="space-y-5">
          {/* 1. A faire en priorite (plan du jour) */}
          <section>
            <h3 className="mb-2 text-sm font-semibold text-heading">À faire en priorité</h3>
            {plan.length === 0 ? (
              <p className="py-3 text-center text-sm text-fg-muted">Aucune action prioritaire</p>
            ) : (
              <ol className="space-y-2">
                {plan.map((item) => (
                  <PlanRow key={`${item.ordre}-${item.targetId}`} item={item} />
                ))}
              </ol>
            )}
          </section>

          {/* 2. Echeances */}
          {echeances.length > 0 && (
            <section>
              <h3 className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-heading">
                <Calendar className="h-4 w-4 text-fg-subtle" aria-hidden="true" /> Échéances à ne pas manquer
              </h3>
              <ul className="divide-y divide-border">
                {echeances.slice(0, 8).map((e) => (
                  <EcheanceRow key={`${e.source}-${e.refId}`} e={e} />
                ))}
              </ul>
            </section>
          )}
        </div>
      )}
    </Card>
  );
}

const URGENCE_BADGE: Record<string, BadgeVariant> = {
  CRITIQUE: 'danger',
  HAUTE: 'warning',
  MOYENNE: 'info',
  BASSE: 'neutral',
};
const URGENCE_LIBELLE: Record<string, string> = {
  CRITIQUE: 'Critique',
  HAUTE: 'Haute',
  MOYENNE: 'Moyenne',
  BASSE: 'Basse',
};

function PlanRow({ item }: { item: PlanItem }) {
  return (
    <li className="relative flex items-start gap-3 rounded-lg border border-border bg-bg-raised p-3 transition-shadow duration-150 hover:border-border-hi hover:shadow-card">
      <span
        aria-hidden="true"
        className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-bg-overlay text-xs font-semibold text-fg-muted"
      >
        {item.ordre}
      </span>
      <div className="min-w-0 flex-1">
        <Link to={item.lien} className="rounded-sm text-sm font-medium text-fg after:absolute after:inset-0 after:rounded-lg hover:underline">
          {item.action}
        </Link>
        <p className="text-xs text-fg-muted">{item.justification}</p>
      </div>
      <Badge variant={URGENCE_BADGE[item.urgence] ?? 'neutral'} className="shrink-0">
        {URGENCE_LIBELLE[item.urgence] ?? item.urgence}
      </Badge>
    </li>
  );
}

function EcheanceRow({ e }: { e: EcheanceSignal }) {
  const variante: BadgeVariant = e.depassee || e.joursRestants <= 3 ? 'danger' : e.joursRestants <= 7 ? 'warning' : 'neutral';
  const label = e.depassee ? `Dépassée · ${Math.abs(e.joursRestants)} j` : `J-${e.joursRestants}`;
  return (
    <li className="relative flex items-center justify-between gap-3 py-2.5 text-sm">
      <div className="min-w-0">
        <p className="flex items-center gap-1.5 truncate font-medium text-fg">
          {e.depassee && <AlertTriangle className="h-3.5 w-3.5 shrink-0 text-danger" aria-hidden="true" />}
          <Link to={e.lien} className="truncate rounded-sm after:absolute after:inset-0 hover:underline">
            {e.intitule}
          </Link>
        </p>
        <p className="text-xs text-fg-muted">
          {e.dossierNom}
          {e.date ? ` · ${new Date(e.date).toLocaleDateString('fr-FR')}` : ''}
        </p>
      </div>
      <Badge variant={variante} className="shrink-0">
        {label}
      </Badge>
    </li>
  );
}
