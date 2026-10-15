import { useCallback, useEffect, useState } from 'react';
import { Activity, RefreshCw } from 'lucide-react';
import { Button } from '../../../components/ui/Button';
import { Drawer } from '../../../components/ui/Drawer';
import { Badge, type BadgeVariant } from '../../../components/ui/Badge';
import { Message } from '../../../components/ui/Message';
import { EmptyState } from '../../../components/ui/EmptyState';
import { Tableau, type ColonneTableau } from '../../../components/ui/Tableau';
import { InfoBulle } from '../../../components/ui/Aide';
import { dataroomService } from '../../../services/dataroom.service';
import { authService } from '../../../services/auth.service';
import { extractError } from '../../../lib/api';
import { formatDateTime } from '../../../lib/date';
import { useCurrentUser } from '../../../store/authStore';
import type { DossierClient } from '../../../types/auth';
import {
  ACCESS_LOG_ACTION_LABELS,
  type AccessLogAction,
  type AccessLogEntry,
  type DocumentSummary,
} from '../../../types/dataroom';

/**
 * Sprint 7 / TASK 5.3 -- Drawer "Activite client" pour le cabinet.
 *
 * Affiche les 50 dernieres actions client (VIEW_DOSSIER, PREVIEW_DOC,
 * DOWNLOAD_DOC, DOWNLOAD_VERSION, PRINT_DOC, DEPOT_DOC, DEMANDE) sur un
 * dossier donne, avec date, action, utilisateur, document, IP. Pagination
 * "Charger plus" cote frontend.
 *
 * Lot L6b (E6), backlog L6 : l'utilisateur et le document sont NOMMES (jamais un
 * identifiant tronque). Le nom vient du client rattache au dossier (deja lu par la
 * page) et, pour le superviseur, de l'annuaire du cabinet (appel existant, reserve a
 * son role) ; le titre du document vient du dossier juridique et des depots du
 * client (appels existants). Introuvable : "Compte inconnu" / "Document introuvable".
 *
 * Vue cabinet uniquement : le drawer n'est jamais expose au role CLIENT.
 */
interface Props {
  open: boolean;
  dossierId: string;
  raisonSociale?: string;
  /** Client rattache au dossier (null : aucun ; absent : inconnu). */
  client?: DossierClient | null;
  onClose: () => void;
}

const PAGE_SIZE = 50;

/** Une teinte d'etat par famille d'action ; le libelle porte toujours le sens. */
const ACTION_VARIANTE: Record<string, BadgeVariant> = {
  VIEW_DOSSIER: 'neutral',
  PREVIEW_DOC: 'info',
  DOWNLOAD_DOC: 'success',
  DOWNLOAD_VERSION: 'success',
  PRINT_DOC: 'warning',
  DEPOT_DOC: 'info',
  DEMANDE: 'warning',
};

const COMPTE_INCONNU = 'Compte inconnu';
const DOCUMENT_INTROUVABLE = 'Document introuvable';

function titresDesDocuments(docs: DocumentSummary[], into: Map<string, string>) {
  for (const d of docs) into.set(d.id, d.title || d.filename);
}

export function ClientAccessLogDrawer({ open, dossierId, raisonSociale, client, onClose }: Props) {
  const user = useCurrentUser();
  const annuaireAutorise = user?.role === 'SUPERVISEUR' || user?.role === 'SUPER_ADMIN';
  const [items, setItems] = useState<AccessLogEntry[]>([]);
  const [total, setTotal] = useState(0);
  const [offset, setOffset] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [noms, setNoms] = useState<Map<string, string>>(new Map());
  const [titres, setTitres] = useState<Map<string, string>>(new Map());

  const load = useCallback(
    async (nextOffset: number, replace = false) => {
      setLoading(true);
      setError(null);
      try {
        const page = await dataroomService.getAccessLog(dossierId, PAGE_SIZE, nextOffset);
        setItems((prev) => (replace ? page.items : [...prev, ...page.items]));
        setTotal(page.total);
        setOffset(nextOffset + page.items.length);
      } catch (err) {
        setError(extractError(err).message);
      } finally {
        setLoading(false);
      }
    },
    [dossierId],
  );

  // Reset + premier chargement a l'ouverture
  useEffect(() => {
    if (!open) return;
    // Remise a zero par promesse : aucun etat pose de facon synchrone dans l'effet
    void Promise.resolve().then(() => {
      setItems([]);
      setOffset(0);
      setTotal(0);
      return load(0, true);
    });
  }, [open, load]);

  // Titres des documents du dossier (juridique, versions remplacees, depots du client) :
  // au mieux ; un echec laisse "Document introuvable".
  useEffect(() => {
    if (!open) return undefined;
    let actif = true;
    void Promise.all([
      dataroomService.getJuridique(dossierId).catch(() => null),
      dataroomService.listDepots(dossierId).catch(() => []),
    ]).then(([vue, depots]) => {
      if (!actif) return;
      const m = new Map<string, string>();
      if (vue) {
        titresDesDocuments(vue.documentsEnVigueur ?? [], m);
        for (const t of vue.dossiersParTicket ?? []) for (const g of t.groupes) titresDesDocuments(g.documents, m);
        for (const h of vue.historiqueOperations ?? []) {
          titresDesDocuments(h.generatedDocuments, m);
          titresDesDocuments(h.replacedDocuments, m);
        }
      }
      for (const d of depots) m.set(d.id, d.title || d.filename);
      setTitres(m);
    });
    return () => {
      actif = false;
    };
  }, [open, dossierId]);

  // Annuaire du cabinet : superviseur seulement (le serveur le refuse a l'employe).
  useEffect(() => {
    if (!open || !annuaireAutorise) return undefined;
    let actif = true;
    authService
      .listWorkspaceDirectory()
      .then((liste) => {
        if (!actif) return;
        const m = new Map<string, string>();
        for (const u of liste) m.set(u.userId, `${u.firstName} ${u.lastName}`.trim() || u.email);
        setNoms(m);
      })
      .catch(() => undefined);
    return () => {
      actif = false;
    };
  }, [open, annuaireAutorise]);

  function nomDe(userId: string | null): string {
    if (!userId) return COMPTE_INCONNU;
    if (client && client.userId === userId) return `${client.firstName} ${client.lastName}`.trim() || client.email;
    return noms.get(userId) ?? COMPTE_INCONNU;
  }

  const colonnes: ColonneTableau<AccessLogEntry>[] = [
    {
      cle: 'date',
      titre: 'Date',
      largeur: 'w-40',
      rendu: (r) => <span className="whitespace-nowrap text-fg-muted">{formatDateTime(r.createdAt)}</span>,
    },
    {
      cle: 'action',
      titre: 'Action',
      rendu: (r) => (
        <Badge variant={ACTION_VARIANTE[r.action] ?? 'neutral'}>
          {ACCESS_LOG_ACTION_LABELS[r.action as AccessLogAction] ?? r.action}
        </Badge>
      ),
    },
    { cle: 'utilisateur', titre: 'Utilisateur', rendu: (r) => <span className="text-fg">{nomDe(r.userId)}</span> },
    {
      cle: 'document',
      titre: 'Document',
      rendu: (r) =>
        r.documentId ? (
          <span className={titres.has(r.documentId) ? 'text-fg' : 'text-fg-subtle'}>
            {titres.get(r.documentId) ?? DOCUMENT_INTROUVABLE}
          </span>
        ) : (
          <span className="text-fg-subtle">—</span>
        ),
    },
    {
      cle: 'ip',
      titre: 'Adresse IP',
      masquerSous: 'md',
      rendu: (r) => <span className="font-mono text-xs text-fg-muted">{r.ipAddress ?? '—'}</span>,
    },
  ];

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title="Activité du client"
      subtitle={raisonSociale ?? 'Historique des consultations du client'}
      width="xl"
      footer={
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="text-xs text-fg-muted">
            {items.length} sur {total} entrée{total > 1 ? 's' : ''}
          </p>
          <div className="flex gap-2">
            <Button
              type="button"
              variant="secondary"
              size="sm"
              icon={<RefreshCw />}
              onClick={() => void load(0, true)}
              disabled={loading}
            >
              Actualiser
            </Button>
            {items.length < total && (
              <Button type="button" size="sm" onClick={() => void load(offset)} loading={loading}>
                Charger plus
              </Button>
            )}
          </div>
        </div>
      }
    >
      <p className="mb-4 flex items-center gap-1 text-sm text-fg-muted">
        Ce que le client a consulté, téléchargé, imprimé ou déposé dans sa Data Room, du plus récent au plus ancien.
        <InfoBulle
          libelle="Que montre l’activité du client ?"
          texte="Chaque action du client sur sa Data Room est enregistrée : consultation, aperçu, téléchargement, impression, dépôt, demande. Un compte qui n’est plus rattaché au dossier apparaît « Compte inconnu » ; un document supprimé depuis, « Document introuvable »."
        />
      </p>

      {error && (
        <Message ton="erreur" className="mb-3">
          {error}
        </Message>
      )}

      {!error && items.length === 0 && !loading ? (
        <EmptyState
          icon={<Activity />}
          title="Le client n’a encore rien consulté"
          description="Aucune activité enregistrée pour ce dossier. Ses consultations, téléchargements et dépôts apparaîtront ici dès sa première connexion."
          compact
        />
      ) : (
        !error && (
          <Tableau
            colonnes={colonnes}
            lignes={items}
            cleLigne={(r) => r.id}
            legende="Activité du client sur la Data Room"
            chargement={loading && items.length === 0}
            enTeteCollant={false}
          />
        )
      )}
    </Drawer>
  );
}
