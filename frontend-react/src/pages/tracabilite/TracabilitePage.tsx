import { useCallback, useEffect, useMemo, useState } from 'react';
import { ChevronDown, History, Loader2, RotateCcw } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/TextField';
import { Select } from '../../components/ui/Select';
import { tracabiliteService } from '../../services/tracabilite.service';
import { extractError } from '../../lib/api';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { formatDateTime } from '../../lib/date';
import { useWorkspaceDirectory } from '../../components/tracabilite/useWorkspaceDirectory';
import { useEntityNames } from '../../components/tracabilite/useEntityNames';
import type { TracabiliteEntry, TracabiliteFilters } from '../../types/tracabilite';

const PAGE_SIZE = 50;

// Actions metier les plus utiles en filtre rapide (le backend accepte tout code).
const ACTION_OPTIONS = [
  { value: '', label: 'Toutes les actions' },
  { value: 'TICKET_CREATED', label: 'Ticket créé' },
  { value: 'TICKET_TRANSITIONED', label: 'Statut du ticket modifié' },
  { value: 'TICKET_ASSIGNED', label: 'Ticket réattribué' },
  { value: 'DOSSIER_TRANSFERE', label: 'Dossier transféré' },
  { value: 'DOCUMENT_UPLOADED', label: 'Document juridique déposé' },
  { value: 'COMPTABLE_UPLOADED', label: 'Document comptable déposé' },
  { value: 'FISCAL_UPLOADED', label: 'Document fiscal déposé' },
  { value: 'EXERCICE_OPENED', label: 'Exercice ouvert' },
  { value: 'DATAROOM_DELETED', label: 'Data Room supprimée' },
  { value: 'PERMISSIONS_CHANGED', label: 'Droits d’accès modifiés' },
];

const ENTITY_OPTIONS = [
  { value: '', label: 'Tous les éléments' },
  { value: 'ticket', label: 'Ticket' },
  { value: 'dossier', label: 'Dossier' },
  { value: 'document', label: 'Document' },
];

// Filtre par statut de l'acteur (applique cote backend, cf. actorStatus).
const ACTOR_STATUS_OPTIONS = [
  { value: '', label: 'Tous' },
  { value: 'ACTIVE', label: 'Actifs' },
  { value: 'RETIRED', label: 'Retirés (désactivés ou supprimés)' },
];

/** Libelle d'un type d'element trace (ticket, dossier, document). */
function libelleTypeElement(type: string | null | undefined): string {
  if (!type) return '—';
  return ENTITY_OPTIONS.find((o) => o.value === type)?.label ?? type;
}

export function TracabilitePage() {
  const [items, setItems] = useState<TracabiliteEntry[]>([]);
  const [total, setTotal] = useState(0);
  const [offset, setOffset] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filters, setFilters] = useState<TracabiliteFilters>({});
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const { nameOf, isRetired, users: directory } = useWorkspaceDirectory();
  const { dossiers, tickets, entityLabelOf } = useEntityNames();

  // Acteur : <optgroup> "Employes & superviseur" / "Clients", tries par nom.
  // Un compte desactive est suffixe " (retire)" ; pour un client on ajoute son
  // email afin de le distinguer d'un homonyme.
  const actorGroups = useMemo(() => {
    const fmt = (u: (typeof directory)[number]) => {
      const name = `${u.firstName} ${u.lastName}`.trim() || u.email;
      const retired = u.status === 'INACTIVE' ? ' (retiré)' : '';
      return { u, name, retired };
    };
    const byName = (a: { name: string }, b: { name: string }) => a.name.localeCompare(b.name, 'fr');
    const employees = directory
      .filter((u) => u.role === 'EMPLOYE' || u.role === 'SUPERVISEUR')
      .map(fmt)
      .sort(byName)
      .map(({ u, name, retired }) => ({ value: u.userId, label: `${name}${retired}` }));
    const clients = directory
      .filter((u) => u.role === 'CLIENT')
      .map(fmt)
      .sort(byName)
      .map(({ u, name, retired }) => ({
        value: u.userId,
        label: `${name}${retired}${u.email ? ` — ${u.email}` : ''}`,
      }));
    const groups: { label: string; options: { value: string; label: string }[] }[] = [];
    if (employees.length) groups.push({ label: 'Employés et superviseur', options: employees });
    if (clients.length) groups.push({ label: 'Clients', options: clients });
    return groups;
  }, [directory]);

  const entityType = filters.entityType;
  const entityOptions = useMemo(() => {
    if (entityType === 'dossier') {
      return [
        { value: '', label: 'Tous les dossiers' },
        ...dossiers.map((d) => ({ value: d.id, label: d.raisonSociale })),
      ];
    }
    if (entityType === 'ticket') {
      return [
        { value: '', label: 'Tous les tickets' },
        ...tickets.map((t) => ({ value: t.id, label: `${t.reference} — ${t.titre}` })),
      ];
    }
    return [{ value: '', label: 'Choisissez d’abord un type d’élément' }];
  }, [entityType, dossiers, tickets]);

  // Le <Select> entite n'est pertinent que pour les types listables (dossier/ticket).
  const entitySelectable = entityType === 'dossier' || entityType === 'ticket';

  // Recuperation seule : les mises a jour d'etat suivent la reponse.
  const recuperer = useCallback(
    (off: number, f: TracabiliteFilters) =>
      tracabiliteService
        .search({ ...f, limit: PAGE_SIZE, offset: off })
        .then((page) => {
          setItems(page.items);
          setTotal(page.total);
          setOffset(page.offset);
          setError(null);
        })
        .catch((err) => setError(extractError(err).message))
        .finally(() => setLoading(false)),
    [],
  );

  useEffect(() => {
    void recuperer(0, {});
  }, [recuperer]);

  const load = useCallback(
    (off: number, f: TracabiliteFilters) => {
      setLoading(true);
      setError(null);
      void recuperer(off, f);
    },
    [recuperer],
  );

  function applyFilters() {
    load(0, filters);
  }

  function reset() {
    setFilters({});
    load(0, {});
  }

  const from = total === 0 ? 0 : offset + 1;
  const to = Math.min(offset + PAGE_SIZE, total);

  return (
    <div className="space-y-5 p-2 md:p-4">
      <header className="flex items-center gap-3">
        <div className="rounded-xl bg-accent/10 p-2.5 text-accent">
          <History className="h-6 w-6" />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-fg">Traçabilité</h1>
          <p className="text-sm text-fg-subtle">
            Journal d’activité du cabinet : qui a fait quoi, et quand.
          </p>
        </div>
      </header>

      <TexteAide cle="tracabilite" titre="Retrouver une action">
        <p>
          Choisissez une action, un dossier ou un ticket, une personne ou une période, puis cliquez sur
          « Filtrer ». « Réinitialiser » affiche de nouveau toute l’activité. Le journal se consulte
          seulement : aucune ligne ne peut être modifiée ni supprimée.
        </p>
      </TexteAide>

      <Card className="p-4">
        <div className="grid gap-3 md:grid-cols-3 lg:grid-cols-4">
          <Select
            name="filter-action"
            label="Action"
            value={filters.action ?? ''}
            onChange={(e) => setFilters((f) => ({ ...f, action: e.target.value || undefined }))}
            options={ACTION_OPTIONS}
          />
          <Select
            name="filter-entity-type"
            label="Type d’élément"
            value={filters.entityType ?? ''}
            onChange={(e) =>
              // Le changement de type invalide l'entite choisie precedemment.
              setFilters((f) => ({ ...f, entityType: e.target.value || undefined, entityId: undefined }))
            }
            options={ENTITY_OPTIONS}
          />
          <Select
            name="filter-entity"
            label="Élément"
            value={filters.entityId ?? ''}
            disabled={!entitySelectable}
            onChange={(e) => setFilters((f) => ({ ...f, entityId: e.target.value || undefined }))}
            options={entityOptions}
          />
          <Select
            name="filter-user"
            label="Auteur"
            value={filters.userId ?? ''}
            onChange={(e) => setFilters((f) => ({ ...f, userId: e.target.value || undefined }))}
            options={[{ value: '', label: 'Tous les auteurs' }]}
            groups={actorGroups}
          />
          <Select
            name="filter-actor-status"
            label="Statut de l’auteur"
            value={filters.actorStatus ?? ''}
            onChange={(e) =>
              setFilters((f) => ({
                ...f,
                actorStatus: (e.target.value || undefined) as TracabiliteFilters['actorStatus'],
              }))
            }
            options={ACTOR_STATUS_OPTIONS}
          />
          <TextField
            label="Du"
            type="datetime-local"
            value={filters.from ?? ''}
            onChange={(e) =>
              setFilters((f) => ({ ...f, from: e.target.value ? new Date(e.target.value).toISOString() : undefined }))
            }
          />
          <TextField
            label="Au"
            type="datetime-local"
            value={filters.to ?? ''}
            onChange={(e) =>
              setFilters((f) => ({ ...f, to: e.target.value ? new Date(e.target.value).toISOString() : undefined }))
            }
          />
          <div className="flex items-end gap-2">
            <Button onClick={applyFilters} loading={loading}>
              Filtrer
            </Button>
            <Button variant="secondary" onClick={reset}>
              <RotateCcw className="mr-1 h-4 w-4" /> Réinitialiser
            </Button>
          </div>
        </div>

        {/* Recherche avancee par UUID — pour les cas hors liste (ex. documents) */}
        <div className="mt-3 border-t border-border pt-3">
          <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => setAdvancedOpen((v) => !v)}
            className="flex items-center gap-1 text-xs font-medium text-fg-subtle transition hover:text-fg"
          >
            <ChevronDown className={`h-3.5 w-3.5 transition-transform ${advancedOpen ? 'rotate-180' : ''}`} />
            Recherche avancée par identifiant technique
          </button>
          <InfoBulle
            libelle="Aide : recherche par identifiant"
            texte="Pour un élément absent des listes (un document, par exemple) : collez son identifiant technique exact, tel qu’il figure dans un rapport ou un message d’erreur."
          />
          </div>
          {advancedOpen && (
            <div className="mt-3 grid gap-3 md:grid-cols-2 lg:grid-cols-4">
              <TextField
                label="Identifiant de l’élément (exact)"
                placeholder="Identifiant exact"
                value={filters.entityId ?? ''}
                onChange={(e) => setFilters((f) => ({ ...f, entityId: e.target.value || undefined }))}
              />
              <TextField
                label="Identifiant de l’utilisateur (exact)"
                placeholder="Identifiant exact"
                value={filters.userId ?? ''}
                onChange={(e) => setFilters((f) => ({ ...f, userId: e.target.value || undefined }))}
              />
            </div>
          )}
        </div>
      </Card>

      {error && (
        <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
          {error}
        </div>
      )}

      <Card className="overflow-hidden">
        {loading ? (
          <div className="flex justify-center py-12">
            <Loader2 className="h-8 w-8 animate-spin text-accent" />
          </div>
        ) : items.length === 0 ? (
          <p className="py-12 text-center text-sm text-fg-subtle">
            Aucune activité pour ces critères. Élargissez la période ou cliquez sur « Réinitialiser ».
          </p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-fg-subtle">
                <th className="px-4 py-2 font-medium">Quand</th>
                <th className="px-4 py-2 font-medium">Qui</th>
                <th className="px-4 py-2 font-medium">Action</th>
                <th className="px-4 py-2 font-medium">Élément</th>
                <th className="px-4 py-2 font-medium">Service</th>
              </tr>
            </thead>
            <tbody>
              {items.map((e) => (
                <tr key={e.id} className="border-b border-border/60 last:border-0 hover:bg-bg-overlay">
                  <td className="whitespace-nowrap px-4 py-2 text-fg-subtle">
                    {formatDateTime(e.createdAt)}
                  </td>
                  <td className="px-4 py-2 font-medium text-fg">
                    <span className="inline-flex items-center gap-1.5">
                      <span>{nameOf(e.userId)}</span>
                      {isRetired(e.userId) && (
                        <span
                          className="rounded-full border border-border bg-bg-overlay px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide text-fg-subtle"
                          title="Compte désactivé ou supprimé"
                        >
                          retiré
                        </span>
                      )}
                    </span>
                  </td>
                  <td className="px-4 py-2 text-fg">{e.actionLabel}</td>
                  <td className="px-4 py-2 text-fg-subtle">
                    {libelleTypeElement(e.entityType)}
                    {e.entityId ? ` · ${entityLabelOf(e.entityType, e.entityId)}` : ''}
                  </td>
                  <td className="px-4 py-2 text-xs text-fg-subtle">{e.sourceService ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      <div className="flex items-center justify-between text-sm text-fg-subtle">
        <span>
          {from}–{to} sur {total}
        </span>
        <div className="flex gap-2">
          <Button
            variant="secondary"
            size="sm"
            disabled={offset === 0 || loading}
            onClick={() => load(Math.max(offset - PAGE_SIZE, 0), filters)}
          >
            Précédent
          </Button>
          <Button
            variant="secondary"
            size="sm"
            disabled={to >= total || loading}
            onClick={() => load(offset + PAGE_SIZE, filters)}
          >
            Suivant
          </Button>
        </div>
      </div>
    </div>
  );
}
