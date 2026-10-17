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
import { libelleAction } from '../../lib/libelleAction';
import { Download, RotateCcw, ScrollText } from 'lucide-react';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { Page } from '../../components/layout/Page';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Message } from '../../components/ui/Message';
import { EmptyState } from '../../components/ui/EmptyState';
import { TextField } from '../../components/ui/TextField';
import { Select } from '../../components/ui/Select';
import { Tableau, type ColonneTableau } from '../../components/ui/Tableau';

// Lot L6b (E9) : le libelle d'une action vient du serveur (actionLabel, facettes) ; plus de
// table de libelles propre a l'ecran (elle ignorait la plupart des codes et montrait le code brut).

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

  const hasActiveFilter =
    filters.workspaceId || filters.userId || filters.role || filters.action ||
    filters.sourceService || filters.fromDate || filters.toDate;

  const colonnes: ColonneTableau<AuditEntry>[] = [
    { cle: 'date', titre: 'Date', rendu: (e) => <span className="whitespace-nowrap tabular-nums text-fg-muted">{new Date(e.createdAt).toLocaleString('fr-FR')}</span> },
    { cle: 'service', titre: 'Service', masquerSous: 'lg', rendu: (e) => <span className="text-fg-muted">{e.sourceService ?? '--'}</span> },
    { cle: 'action', titre: 'Action', rendu: (e) => <span className="font-medium text-fg">{libelleAction(e.actionLabel, e.action)}</span> },
    { cle: 'cabinet', titre: 'Cabinet', rendu: (e) => <span className="font-mono text-xs text-fg-muted">{workspaceLabel(e.workspaceId, workspaces)}</span> },
    { cle: 'auteur', titre: 'Auteur', rendu: (e) => <span className="text-fg-muted">{userLabel(e.userId, users)}</span> },
    {
      cle: 'element',
      titre: 'Élément concerné',
      masquerSous: 'md',
      rendu: (e) => <span className="font-mono text-xs text-fg-muted">{e.entityType ? `${e.entityType}#${shortId(e.entityId)}` : '--'}</span>,
    },
    {
      cle: 'correlation',
      titre: (
        <span className="inline-flex items-center gap-1">
          Corrélation
          <InfoBulle libelle="Aide : corrélation" texte="Identifiant qui relie les événements d’une même opération." />
        </span>
      ),
      masquerSous: 'lg',
      rendu: (e) => <span className="font-mono text-xs text-fg-subtle">{e.correlationId ?? '--'}</span>,
    },
  ];

  return (
    <Page
      titre="Journal d’audit"
      description="Toutes les actions tracées sur la plateforme, filtrables par cabinet, utilisateur, action et service."
      actions={
        <Button type="button" variant="secondary" icon={<Download />} onClick={exportCsv} disabled={!items.length}>
          Exporter en CSV
        </Button>
      }
      aide={
        <TexteAide cle="admin-journal-audit" titre="Comment lire le journal">
          <p>
            Choisissez un cabinet, un utilisateur, une action ou une période pour retrouver un événement
            (connexion, changement de mot de passe, suspension d’un cabinet…). L’export CSV reprend la
            page affichée ; affinez les filtres avant d’exporter. Le journal se consulte : il ne se
            modifie pas.
          </p>
        </TexteAide>
      }
    >
      <div className="space-y-4">
        {/* Filtres -- listes deroulantes sur donnees reelles */}
        <Card className="p-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <Select
              label="Cabinet"
              value={filters.workspaceId ?? ''}
              onChange={(e) => updateWorkspace(e.target.value || undefined)}
              options={[{ value: '', label: 'Tous les cabinets' }, ...workspaces.map((w) => ({ value: w.id, label: `${w.code} — ${w.name}` }))]}
            />
            <Select
              label="Type d’utilisateur"
              value={filters.role ?? ''}
              onChange={(e) => updateRole(e.target.value || undefined)}
              options={[
                { value: '', label: 'Tous les types' },
                { value: 'CLIENT', label: 'Client' },
                { value: 'SUPERVISEUR', label: 'Superviseur' },
                { value: 'EMPLOYE', label: 'Employé' },
                { value: 'SUPER_ADMIN', label: 'Super-administrateur' },
              ]}
            />
            <Select
              label="Utilisateur"
              value={filters.userId ?? ''}
              onChange={(e) => update('userId', e.target.value || undefined)}
              options={[
                { value: '', label: 'Tous les utilisateurs' },
                ...users.map((u) => ({ value: u.id, label: `${u.firstName} ${u.lastName}${u.workspaceCode ? ` — ${u.workspaceCode}` : ''}` })),
              ]}
            />
            <Select
              label="Action"
              value={filters.action ?? ''}
              onChange={(e) => update('action', e.target.value || undefined)}
              options={[{ value: '', label: 'Toutes les actions' }, ...(facets.actions ?? []).map((a) => ({ value: a, label: libelleAction(facets.actionLabels?.[a], a) }))]}
            />
            <Select
              label="Service d’origine"
              hint="Composant de la plateforme qui a enregistré l’événement (authentification, Data Room, documents…). Utile pour un diagnostic technique."
              value={filters.sourceService ?? ''}
              onChange={(e) => update('sourceService', e.target.value || undefined)}
              options={[{ value: '', label: 'Tous les services' }, ...(facets.sourceServices ?? []).map((s) => ({ value: s, label: s }))]}
            />
            <div className="grid grid-cols-2 gap-3">
              <TextField
                label="Du"
                type="datetime-local"
                value={filters.fromDate?.slice(0, 16) ?? ''}
                onChange={(e) => update('fromDate', e.target.value ? new Date(e.target.value).toISOString() : undefined)}
              />
              <TextField
                label="Au"
                type="datetime-local"
                value={filters.toDate?.slice(0, 16) ?? ''}
                onChange={(e) => update('toDate', e.target.value ? new Date(e.target.value).toISOString() : undefined)}
              />
            </div>
          </div>
          {hasActiveFilter && (
            <div className="mt-3 border-t border-border pt-3">
              <Button type="button" variant="ghost" size="sm" icon={<RotateCcw />} onClick={resetFilters}>
                Réinitialiser les filtres
              </Button>
            </div>
          )}
        </Card>

        {error && <Message ton="erreur">{error}</Message>}

        <Tableau
          legende="Journal d’audit de la plateforme"
          colonnes={colonnes}
          lignes={items}
          cleLigne={(e) => String(e.id)}
          chargement={loading}
          lignesChargement={10}
          vide={
            <EmptyState
              compact
              icon={<ScrollText />}
              title={hasActiveFilter ? 'Aucun événement pour ces filtres' : 'Aucun événement enregistré'}
              description={
                hasActiveFilter
                  ? 'Aucun événement ne correspond à ces filtres. Élargissez la période ou réinitialisez les filtres.'
                  : 'Aucun événement enregistré pour le moment.'
              }
            />
          }
        />

        <div className="flex flex-wrap items-center justify-between gap-2 text-sm text-fg-muted">
          <span className="tabular-nums">
            {total === 0 ? 0 : offset + 1}–{Math.min(offset + items.length, total)} / {total}
          </span>
          <div className="flex gap-2">
            <Button
              type="button"
              variant="secondary"
              size="sm"
              disabled={offset === 0}
              onClick={() => setFilters((p) => ({ ...p, offset: Math.max(0, (p.offset ?? 0) - limit) }))}
            >
              Précédent
            </Button>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              disabled={offset + limit >= total}
              onClick={() => setFilters((p) => ({ ...p, offset: (p.offset ?? 0) + limit }))}
            >
              Suivant
            </Button>
          </div>
        </div>
      </div>
    </Page>
  );
}

function shortId(id: string | null): string {
  return id ? id.slice(0, 8) : '?';
}

function workspaceLabel(id: string | null, workspaces: AdminWorkspaceRow[]): string {
  if (!id) return '--';
  const ws = workspaces.find((w) => w.id === id);
  return ws ? ws.code : shortId(id);
}

function userLabel(id: string | null, users: AdminUserRow[]): string {
  if (!id) return '--';
  const u = users.find((x) => x.id === id);
  return u ? `${u.firstName} ${u.lastName}` : shortId(id);
}
