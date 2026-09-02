import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertCircle, Gauge } from 'lucide-react';
import { billingService } from '../../services/billing.service';
import type { UsageSnapshot } from '../../types/billing';

/**
 * Sprint Beta (pricing-deploy) — TASK 3.
 *
 * <p>Affiche la consommation du plan en compact (header) ou en card
 * (page dashboard). Quand un quota est atteint, un message rouge avec
 * lien vers /app/billing s'affiche.
 *
 * <p>Polling : recharge a chaque mount. Pas de polling continu — le
 * delta sur compteurs (users, dossiers) est volontaire (l'utilisateur
 * sait qu'il vient de creer), donc on rafraichira au prochain navigate.
 */
export interface PlanUsageBadgeProps {
  /** 'compact' (header) ou 'full' (card dashboard). Defaut compact. */
  variant?: 'compact' | 'full';
}

function pct(n: number, max: number) {
  if (max <= 0) return 0;
  return Math.min(100, Math.round((n / max) * 100));
}

function barColor(percent: number): string {
  if (percent >= 100) return 'bg-danger';
  if (percent >= 80) return 'bg-warning';
  return 'bg-success';
}

export function PlanUsageBadge({ variant = 'compact' }: PlanUsageBadgeProps) {
  const [usage, setUsage] = useState<UsageSnapshot | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    billingService
      .getUsage()
      .then((u) => { if (!cancelled) { setUsage(u); setLoading(false); } })
      .catch(() => { if (!cancelled) { setUsage(null); setLoading(false); } });
    return () => { cancelled = true; };
  }, []);

  if (loading) return null;
  if (!usage) return null;

  const usersPct = usage.usersUnlimited ? 0 : pct(usage.users, usage.maxUsers);
  const dossiersPct = usage.dossiersUnlimited ? 0 : pct(usage.dossiers, usage.maxDossiers);

  const atUsersLimit = !usage.usersUnlimited && usage.users >= usage.maxUsers;
  const atDossiersLimit = !usage.dossiersUnlimited && usage.dossiers >= usage.maxDossiers;
  const atAnyLimit = atUsersLimit || atDossiersLimit;

  if (variant === 'compact') {
    return (
      <Link
        to="/app/billing"
        className="inline-flex items-center gap-2 rounded-full border border-border bg-bg-raised px-3 py-1 text-xs text-fg-muted hover:bg-bg-overlay"
        data-testid="plan-usage-badge"
      >
        {atAnyLimit ? (
          <AlertCircle className="h-3.5 w-3.5 text-danger" />
        ) : (
          <Gauge className="h-3.5 w-3.5 text-fg-muted" />
        )}
        <span className="font-medium text-fg">{usage.planLabel}</span>
        <span className="text-fg-muted">·</span>
        <span className={atDossiersLimit ? 'font-bold text-danger' : ''}>
          {usage.dossiersUnlimited
            ? `${usage.dossiers} dossiers`
            : `${usage.dossiers}/${usage.maxDossiers}`}
        </span>
      </Link>
    );
  }

  return (
    <section
      className="rounded-2xl border border-border bg-bg-raised p-5 shadow-card"
      data-testid="plan-usage-card"
    >
      <header className="mb-4 flex items-center justify-between">
        <div>
          <p className="text-xs uppercase tracking-widest text-fg-muted">Plan actuel</p>
          <h3 className="font-heading text-xl font-semibold text-fg">{usage.planLabel}</h3>
        </div>
        <Link
          to="/app/billing"
          className="text-xs font-medium text-accent hover:underline"
        >
          Voir la facturation →
        </Link>
      </header>

      <div className="space-y-3">
        <UsageRow
          label="Utilisateurs"
          current={usage.users}
          max={usage.maxUsers}
          unlimited={usage.usersUnlimited}
          percent={usersPct}
        />
        <UsageRow
          label="Dossiers actifs"
          current={usage.dossiers}
          max={usage.maxDossiers}
          unlimited={usage.dossiersUnlimited}
          percent={dossiersPct}
        />
        <StorageRow
          bytes={usage.storageBytes}
          maxGb={usage.maxStorageGb}
          unlimited={usage.storageUnlimited}
        />
      </div>

      {atAnyLimit && (
        <div
          className="mt-4 rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger"
          role="alert"
          data-testid="plan-usage-alert"
        >
          <AlertCircle className="mr-1 inline h-4 w-4" />
          Limite atteinte sur votre plan {usage.planLabel}.{' '}
          <Link to="/app/billing" className="underline">
            Passez au plan supérieur
          </Link>{' '}
          pour continuer.
        </div>
      )}
    </section>
  );
}

function UsageRow({
  label,
  current,
  max,
  unlimited,
  percent,
}: {
  label: string;
  current: number;
  max: number;
  unlimited: boolean;
  percent: number;
}) {
  return (
    <div>
      <div className="mb-1 flex items-center justify-between text-sm">
        <span className="text-fg-muted">{label}</span>
        <span className={`font-mono text-xs ${percent >= 100 ? 'font-bold text-danger' : 'text-fg'}`}>
          {unlimited ? `${current} · illimité` : `${current} / ${max}`}
        </span>
      </div>
      {!unlimited && (
        <div className="h-1.5 rounded-full bg-bg-overlay">
          <div
            className={`h-full rounded-full transition-all ${barColor(percent)}`}
            style={{ width: `${percent}%` }}
          />
        </div>
      )}
    </div>
  );
}

function StorageRow({
  bytes,
  maxGb,
  unlimited,
}: {
  bytes: number;
  maxGb: number;
  unlimited: boolean;
}) {
  const usedGb = bytes / (1024 * 1024 * 1024);
  const percent = unlimited || maxGb <= 0 ? 0 : Math.min(100, Math.round((usedGb / maxGb) * 100));
  return (
    <div>
      <div className="mb-1 flex items-center justify-between text-sm">
        <span className="text-fg-muted">Stockage</span>
        <span className={`font-mono text-xs ${percent >= 100 ? 'font-bold text-danger' : 'text-fg'}`}>
          {unlimited
            ? `${usedGb.toFixed(2)} Go · sur mesure`
            : `${usedGb.toFixed(2)} / ${maxGb} Go`}
        </span>
      </div>
      {!unlimited && (
        <div className="h-1.5 rounded-full bg-bg-overlay">
          <div
            className={`h-full rounded-full transition-all ${barColor(percent)}`}
            style={{ width: `${percent}%` }}
          />
        </div>
      )}
    </div>
  );
}
