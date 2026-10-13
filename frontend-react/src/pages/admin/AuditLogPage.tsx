import { useEffect, useState } from 'react';
import {
  auditService,
  type AuditEntry,
  type AuditFacets,
  type AuditFilters,
  type AuditPage,
} from '../../services/audit.service';
import { adminService } from '../../services/admin.service';
import type { AdminUserRow, AdminWorkspaceRow } from '../../types/admin';
import { extractError } from '../../lib/api';
import { libelleActionAudit } from '../../lib/adminLabels';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';

const humanizeAction = libelleActionAudit;

export default function AuditLogPage() {
  const [page, setPage] = useState<AuditPage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [filters, setFilters] = useState<AuditFilters>({ offset: 0, limit: 50 });
  // Filtres auxquels correspond le dernier resultat : le chargement en cours se
  // deduit pendant le rendu (aucun setState synchrone dans l'effet).
  const [chargePour, setChargePour] = useState<AuditFilters | null>(null);
  const loading = chargePour !== filters;

  // Sources des listes deroulantes (donnees reelles).
  const [workspaces, setWorkspaces] = useState<AdminWorkspaceRow[]>([]);
  const [users, setUsers] = useState<AdminUserRow[]>([]);
  const [facets, setFacets] = useState<AuditFacets>({ actions: [], sourceServices: [] });

  useEffect(() => {
    let annule = false;
    auditService
      .search(filters)
      .then((result) => {
        if (annule) return;
        setPage(result);
        setError(null);
        setChargePour(filters);
      })
      .catch((e) => {
        if (annule) return;
        setError(`Le journal n’a pas pu être chargé : ${extractError(e).message}`);
        setChargePour(filters);
      });
    return () => {
      annule = true;
    };
  }, [filters]);

  // Workspaces + facettes (actions / services) au montage.
  useEffect(() => {
    adminService.listWorkspaces().then(setWorkspaces).catch(() => setWorkspaces([]));
    auditService.facets().then(setFacets).catch(() => setFacets({ actions: [], sourceServices: [] }));
  }, []);

  // Utilisateurs de la liste deroulante : filtres sur le workspace + le type
  // d'utilisateur choisis (sinon les 100 premiers de la plateforme).
  useEffect(() => {
    adminService
      .listUsers({ workspaceId: filters.workspaceId, role: filters.role, limit: 100 })
      .then((p) => setUsers(p.items))
      .catch(() => setUsers([]));
  }, [filters.workspaceId, filters.role]);

  function update<K extends keyof AuditFilters>(key: K, value: AuditFilters[K]) {
    setFilters((prev) => ({ ...prev, [key]: value, offset: 0 }));
  }

  // Changer de cabinet remet a zero le filtre utilisateur (l'ancien user
  // peut ne pas appartenir au nouveau workspace).
  function updateWorkspace(value: string | undefined) {
    setFilters((prev) => ({ ...prev, workspaceId: value, userId: undefined, offset: 0 }));
  }

  // Changer le type d'utilisateur remet a zero l'utilisateur precis selectionne.
  function updateRole(value: string | undefined) {
    setFilters((prev) => ({ ...prev, role: value, userId: undefined, offset: 0 }));
  }

  function exportCsv() {
    if (!page) return;
    const csv = auditService.exportCsv(page.items);
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `journal-audit-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  function resetFilters() {
    setFilters({ offset: 0, limit: 50 });
  }

  const items: AuditEntry[] = page?.items ?? [];
  const total = page?.total ?? 0;
  const offset = filters.offset ?? 0;
  const limit = filters.limit ?? 50;

  const selectClass =
    'rounded-lg border border-border bg-bg-raised px-3 py-2 text-sm text-fg focus:border-accent focus:outline-none';
  const hasActiveFilter =
    filters.workspaceId || filters.userId || filters.role || filters.action ||
    filters.sourceService || filters.fromDate || filters.toDate;

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-heading text-2xl font-semibold text-fg">Journal d’audit</h1>
          <p className="mt-1 text-sm text-fg-subtle">
            Toutes les actions tracées sur la plateforme, filtrables par cabinet, utilisateur, action et service.
          </p>
        </div>
        <button
          type="button"
          className="rounded-lg border border-border-hi bg-bg-raised px-3 py-1.5 text-sm text-fg transition hover:border-accent hover:text-accent disabled:opacity-50"
          onClick={exportCsv}
          disabled={!items.length}
        >
          Exporter en CSV
        </button>
      </header>

      <TexteAide cle="admin-journal-audit" titre="Comment lire le journal">
        <p>
          Choisissez un cabinet, un utilisateur, une action ou une période pour retrouver un événement
          (connexion, changement de mot de passe, suspension d’un cabinet…). L’export CSV reprend la
          page affichée ; affinez les filtres avant d’exporter. Le journal se consulte : il ne se
          modifie pas.
        </p>
      </TexteAide>

      {/* Filtres — listes deroulantes sur donnees reelles */}
      <div className="grid grid-cols-1 gap-3 rounded-xl border border-border bg-bg-raised p-4 sm:grid-cols-2 lg:grid-cols-3">
        <label className="flex flex-col gap-1 text-xs text-fg-subtle">
          Cabinet
          <select
            className={selectClass}
            value={filters.workspaceId ?? ''}
            onChange={(e) => updateWorkspace(e.target.value || undefined)}
          >
            <option value="">Tous les cabinets</option>
            {workspaces.map((w) => (
              <option key={w.id} value={w.id}>
                {w.code} — {w.name}
              </option>
            ))}
          </select>
        </label>

        <label className="flex flex-col gap-1 text-xs text-fg-subtle">
          Type d’utilisateur
          <select
            className={selectClass}
            value={filters.role ?? ''}
            onChange={(e) => updateRole(e.target.value || undefined)}
          >
            <option value="">Tous les types</option>
            <option value="CLIENT">Client</option>
            <option value="SUPERVISEUR">Superviseur</option>
            <option value="EMPLOYE">Employé</option>
            <option value="SUPER_ADMIN">Super-administrateur</option>
          </select>
        </label>

        <label className="flex flex-col gap-1 text-xs text-fg-subtle">
          Utilisateur
          <select
            className={selectClass}
            value={filters.userId ?? ''}
            onChange={(e) => update('userId', e.target.value || undefined)}
          >
            <option value="">Tous les utilisateurs</option>
            {users.map((u) => (
              <option key={u.id} value={u.id}>
                {u.firstName} {u.lastName}
                {u.workspaceCode ? ` — ${u.workspaceCode}` : ''}
              </option>
            ))}
          </select>
        </label>

        <label className="flex flex-col gap-1 text-xs text-fg-subtle">
          Action
          <select
            className={selectClass}
            value={filters.action ?? ''}
            onChange={(e) => update('action', e.target.value || undefined)}
          >
            <option value="">Toutes les actions</option>
            {facets.actions.map((a) => (
              <option key={a} value={a}>
                {humanizeAction(a)}
              </option>
            ))}
          </select>
        </label>

        <label className="flex flex-col gap-1 text-xs text-fg-subtle">
          <span className="inline-flex items-center gap-1">
            Service d’origine
            <InfoBulle
              libelle="Aide : service d’origine"
              texte="Composant de la plateforme qui a enregistré l’événement (authentification, Data Room, documents…). Utile pour un diagnostic technique."
            />
          </span>
          <select
            className={selectClass}
            value={filters.sourceService ?? ''}
            onChange={(e) => update('sourceService', e.target.value || undefined)}
          >
            <option value="">Tous les services</option>
            {facets.sourceServices.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>

        <label className="flex flex-col gap-1 text-xs text-fg-subtle">
          Du
          <input
            type="datetime-local"
            className={selectClass}
            value={filters.fromDate?.slice(0, 16) ?? ''}
            onChange={(e) => update('fromDate', e.target.value ? new Date(e.target.value).toISOString() : undefined)}
          />
        </label>

        <label className="flex flex-col gap-1 text-xs text-fg-subtle">
          Au
          <input
            type="datetime-local"
            className={selectClass}
            value={filters.toDate?.slice(0, 16) ?? ''}
            onChange={(e) => update('toDate', e.target.value ? new Date(e.target.value).toISOString() : undefined)}
          />
        </label>

        {hasActiveFilter && (
          <div className="sm:col-span-2 lg:col-span-3">
            <button
              type="button"
              onClick={resetFilters}
              className="text-xs font-medium text-accent hover:underline"
            >
              Réinitialiser les filtres
            </button>
          </div>
        )}
      </div>

      {loading && <p className="text-sm text-fg-subtle">Chargement…</p>}
      {error && <p role="alert" className="text-sm text-danger">{error}</p>}

      <div className="overflow-x-auto rounded-xl border border-border">
        <table className="min-w-full text-sm">
          <thead className="bg-bg-overlay text-left text-xs uppercase tracking-wide text-fg-subtle">
            <tr>
              <th className="px-3 py-2 font-medium">Date</th>
              <th className="px-3 py-2 font-medium">Service</th>
              <th className="px-3 py-2 font-medium">Action</th>
              <th className="px-3 py-2 font-medium">Cabinet</th>
              <th className="px-3 py-2 font-medium">Auteur</th>
              <th className="px-3 py-2 font-medium">Élément concerné</th>
              <th className="px-3 py-2 font-medium" title="Identifiant qui relie les événements d’une même opération">Corrélation</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {items.map((e) => (
              <tr key={e.id} className="hover:bg-bg-overlay/50">
                <td className="whitespace-nowrap px-3 py-1.5 text-fg-muted">
                  {new Date(e.createdAt).toLocaleString('fr-FR')}
                </td>
                <td className="px-3 py-1.5 text-fg-muted">{e.sourceService ?? '—'}</td>
                <td className="px-3 py-1.5 font-medium text-fg">{humanizeAction(e.action)}</td>
                <td className="px-3 py-1.5 font-mono text-xs text-fg-subtle">
                  {workspaceLabel(e.workspaceId, workspaces)}
                </td>
                <td className="px-3 py-1.5 font-mono text-xs text-fg-subtle">
                  {userLabel(e.userId, users)}
                </td>
                <td className="px-3 py-1.5 font-mono text-xs text-fg-subtle">
                  {e.entityType ? `${e.entityType}#${shortId(e.entityId)}` : '—'}
                </td>
                <td className="px-3 py-1.5 font-mono text-xs text-fg-subtle">
                  {e.correlationId ?? '—'}
                </td>
              </tr>
            ))}
            {!loading && !items.length && (
              <tr>
                <td colSpan={7} className="px-3 py-6 text-center text-fg-subtle">
                  {hasActiveFilter
                    ? 'Aucun événement ne correspond à ces filtres. Élargissez la période ou réinitialisez les filtres.'
                    : 'Aucun événement enregistré pour le moment.'}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <footer className="flex items-center justify-between text-sm text-fg-subtle">
        <span>
          {total === 0 ? 0 : offset + 1}–{Math.min(offset + items.length, total)} / {total}
        </span>
        <div className="flex gap-2">
          <button
            type="button"
            className="rounded-lg border border-border px-3 py-1 disabled:opacity-40"
            disabled={offset === 0}
            onClick={() => setFilters((p) => ({ ...p, offset: Math.max(0, (p.offset ?? 0) - limit) }))}
          >
            Précédent
          </button>
          <button
            type="button"
            className="rounded-lg border border-border px-3 py-1 disabled:opacity-40"
            disabled={offset + limit >= total}
            onClick={() => setFilters((p) => ({ ...p, offset: (p.offset ?? 0) + limit }))}
          >
            Suivant
          </button>
        </div>
      </footer>
    </div>
  );
}

function shortId(id: string | null): string {
  return id ? id.slice(0, 8) : '?';
}

function workspaceLabel(id: string | null, workspaces: AdminWorkspaceRow[]): string {
  if (!id) return '—';
  const ws = workspaces.find((w) => w.id === id);
  return ws ? ws.code : shortId(id);
}

function userLabel(id: string | null, users: AdminUserRow[]): string {
  if (!id) return '—';
  const u = users.find((x) => x.id === id);
  return u ? `${u.firstName} ${u.lastName}` : shortId(id);
}
