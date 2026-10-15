import { useCallback, useEffect, useMemo, useState } from 'react';
import { ChevronDown, Filter, History, RotateCcw } from 'lucide-react';
import { Page } from '../../components/layout/Page';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Message } from '../../components/ui/Message';
import { EmptyState } from '../../components/ui/EmptyState';
import { Tableau, type ColonneTableau } from '../../components/ui/Tableau';
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
  if (!type) return '--';
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

  const colonnes: ColonneTableau<TracabiliteEntry>[] = [
    { cle: 'quand', titre: 'Quand', rendu: (e) => <span className="whitespace-nowrap tabular-nums text-fg-muted">{formatDateTime(e.createdAt)}</span> },
    {
      cle: 'qui',
      titre: 'Qui',
      rendu: (e) => (
        <span className="inline-flex items-center gap-1.5 font-medium text-fg">
          <span>{nameOf(e.userId)}</span>
          {isRetired(e.userId) && (
            <Badge variant="neutral" title="Compte désactivé ou supprimé">
              retiré
            </Badge>
          )}
        </span>
      ),
    },
    { cle: 'action', titre: 'Action', rendu: (e) => <span className="text-fg">{e.actionLabel}</span> },
    {
      cle: 'element',
      titre: 'Élément',
      rendu: (e) => (
        <span className="text-fg-muted">
          {libelleTypeElement(e.entityType)}
          {e.entityId ? ` · ${entityLabelOf(e.entityType, e.entityId)}` : ''}
        </span>
      ),
    },
    { cle: 'service', titre: 'Service', masquerSous: 'lg', rendu: (e) => <span className="text-xs text-fg-subtle">{e.sourceService ?? '--'}</span> },
  ];

  const from = total === 0 ? 0 : offset + 1;
  const to = Math.min(offset + PAGE_SIZE, total);

  return (
    <Page
      titre="Traçabilité"
      description="Journal d’activité du cabinet : qui a fait quoi, et quand."
      aide={
        <TexteAide cle="tracabilite" titre="Retrouver une action">
          <p>
            Choisissez une action, un dossier ou un ticket, une personne ou une période, puis cliquez sur
            « Filtrer ». « Réinitialiser » affiche de nouveau toute l’activité. Le journal se consulte
            seulement : aucune ligne ne peut être modifiée ni supprimée.
          </p>
        </TexteAide>
      }
    >
      <div className="space-y-5">
      <Card className="p-4">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            applyFilters();
          }}
          aria-label="Filtres du journal"
        >
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
            <Button type="submit" loading={loading} icon={<Filter />}>
              Filtrer
            </Button>
            <Button type="button" variant="secondary" onClick={reset} icon={<RotateCcw />}>
              Réinitialiser
            </Button>
          </div>
        </div>

        {/* Recherche avancee par UUID — pour les cas hors liste (ex. documents) */}
        <div className="mt-3 border-t border-border pt-3">
          <div className="flex items-center gap-1">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            aria-expanded={advancedOpen}
            onClick={() => setAdvancedOpen((v) => !v)}
            icon={<ChevronDown className={`transition-transform duration-150 motion-reduce:transition-none ${advancedOpen ? 'rotate-180' : ''}`} />}
          >
            Recherche avancée par identifiant technique
          </Button>
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
        </form>
      </Card>

      {error && (
        <Message ton="erreur" titre="Le journal n’a pas pu être chargé">
          {error}
        </Message>
      )}

      <Tableau
        legende="Journal d’activité du cabinet"
        colonnes={colonnes}
        lignes={items}
        cleLigne={(e) => String(e.id)}
        chargement={loading}
        lignesChargement={8}
        vide={
          <EmptyState
            compact
            icon={<History />}
            title="Aucune activité pour ces critères"
            description="Élargissez la période ou cliquez sur « Réinitialiser » pour afficher de nouveau toute l’activité."
          />
        }
      />

      <div className="flex flex-wrap items-center justify-between gap-2 text-sm text-fg-muted">
        <span>
          {from}–{to} sur {total}
        </span>
        <div className="flex gap-2">
          <Button
            type="button"
            variant="secondary"
            size="sm"
            disabled={offset === 0 || loading}
            onClick={() => load(Math.max(offset - PAGE_SIZE, 0), filters)}
          >
            Précédent
          </Button>
          <Button
            type="button"
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
    </Page>
  );
}
