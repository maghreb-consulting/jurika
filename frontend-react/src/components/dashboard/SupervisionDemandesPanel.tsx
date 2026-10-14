import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Building2, Inbox, MessageSquare, Send, User } from 'lucide-react';
import { dataroomService } from '../../services/dataroom.service';
import {
  ETAT_SUIVI_LABELS,
  ETAT_SUIVI_ORDRE,
  REQUETE_STATUT_LABELS,
  type DemandeSupervisionRow,
  type EtatSuivi,
} from '../../types/dataroom';
import { InfoBulle, TexteAide } from '../ui/Aide';

/**
 * Vue SUPERVISEUR (workspace-wide) : toutes les demandes des clients ET requetes
 * aux clients du cabinet, chaque ligne montrant le nom du dataroom (raison
 * sociale) et l'employe responsable (noms resolus par jointure cote back —
 * jamais d'UUID). Lecture seule (RG-U02-04) : aucune action de mutation ici.
 *
 * Lot L5 (E9, RG-COM-07) : demandes et requêtes sont suivies par les trois mêmes
 * états (non traitée, en cours, traitée), calculés par le serveur ; le statut
 * détaillé d'une requête reste affiché en second.
 */

const ETAT_BADGE: Record<EtatSuivi, string> = {
  NON_TRAITEE: 'bg-danger/10 text-danger',
  EN_COURS: 'bg-accent/10 text-accent',
  TRAITEE: 'bg-emerald-100 text-emerald-700',
};

const SANS_ETAT = 'SANS_ETAT';

export function SupervisionDemandesPanel() {
  const [demandes, setDemandes] = useState<DemandeSupervisionRow[]>([]);
  const [requetes, setRequetes] = useState<DemandeSupervisionRow[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    Promise.all([
      dataroomService.listSupervisionDemandes('CLIENT_TO_EMPLOYE').catch(() => [] as DemandeSupervisionRow[]),
      dataroomService.listSupervisionDemandes('EMPLOYE_TO_CLIENT').catch(() => [] as DemandeSupervisionRow[]),
    ]).then(([dem, req]) => {
      if (!mounted) return;
      setDemandes(dem);
      setRequetes(req);
      setLoading(false);
    });
    return () => {
      mounted = false;
    };
  }, []);

  return (
    <div className="mt-6 space-y-3">
      <TexteAide cle="supervision-demandes-requetes" titre="Suivi des demandes et des requêtes">
        Chaque demande d’un client et chaque requête du cabinet est suivie par trois états :
        « Non traitée » (rien n’a encore été fait), « En cours » (prise en charge, ou réponse du
        client à vérifier) et « Traitée ». Cette vue est en lecture seule : l’employé responsable
        du dossier traite la demande ou valide la requête depuis la Data Room ; cliquez sur une
        ligne pour l’ouvrir.
      </TexteAide>
    <div className="grid gap-6 lg:grid-cols-2">
      <SupervisionBlock
        title="Demandes des clients"
        icon={MessageSquare}
        iconColor="text-violet-600"
        direction="CLIENT_TO_EMPLOYE"
        rows={demandes}
        loading={loading}
        emptyLabel="Aucune demande client dans le cabinet."
      />
      <SupervisionBlock
        title="Requêtes du cabinet aux clients"
        icon={Send}
        iconColor="text-accent"
        direction="EMPLOYE_TO_CLIENT"
        rows={requetes}
        loading={loading}
        emptyLabel="Aucune requête envoyée aux clients."
      />
    </div>
    </div>
  );
}

function SupervisionBlock({
  title,
  icon: Icon,
  iconColor,
  direction,
  rows,
  loading,
  emptyLabel,
}: {
  title: string;
  icon: typeof MessageSquare;
  iconColor: string;
  direction: 'CLIENT_TO_EMPLOYE' | 'EMPLOYE_TO_CLIENT';
  rows: DemandeSupervisionRow[];
  loading: boolean;
  emptyLabel: string;
}) {
  const navigate = useNavigate();

  // Compteurs par etat de suivi (RG-COM-07) + tri des lignes par etat.
  const counts = useMemo(() => {
    const map: Record<string, number> = {};
    for (const r of rows) {
      const k = r.etatSuivi ?? SANS_ETAT;
      map[k] = (map[k] ?? 0) + 1;
    }
    return map;
  }, [rows]);

  const sorted = useMemo(() => {
    const rank = (e: EtatSuivi | null | undefined) => {
      const i = e ? ETAT_SUIVI_ORDRE.indexOf(e) : -1;
      return i === -1 ? ETAT_SUIVI_ORDRE.length : i;
    };
    return [...rows].sort(
      (a, b) =>
        rank(a.etatSuivi) - rank(b.etatSuivi) ||
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    );
  }, [rows]);

  return (
    <div className="rounded-xl border border-border bg-bg-raised shadow-sm">
      <div className="flex items-center justify-between border-b border-border px-6 py-4">
        <h3 className="flex items-center gap-2 text-base font-semibold text-fg">
          <Icon className={`h-5 w-5 ${iconColor}`} /> {title}
          <InfoBulle
            libelle={`Aide : ${title}`}
            texte={
              direction === 'EMPLOYE_TO_CLIENT'
                ? 'Requête « Non traitée » : le client n’a pas encore répondu. « En cours » : le client a répondu (à vérifier) ou un complément lui est demandé. « Traitée » : le cabinet a validé la réponse.'
                : 'Demande « Non traitée » : l’employé responsable ne l’a pas encore prise en charge. « En cours » : prise en charge. « Traitée » : le client a reçu la réponse par courriel.'
            }
          />
        </h3>
        <span className="text-xs text-fg-subtle">{rows.length} au total</span>
      </div>

      {/* Repartition par statut (chips de comptage). */}
      {rows.length > 0 && (
        <div className="flex flex-wrap gap-2 border-b border-border px-6 py-2.5">
          {ETAT_SUIVI_ORDRE
            .filter((s) => counts[s])
            .map((s) => (
              <span
                key={s}
                data-testid={`compteur-etat-${s}`}
                className={`rounded-full px-2.5 py-0.5 text-[11px] font-semibold ${ETAT_BADGE[s]}`}
              >
                {ETAT_SUIVI_LABELS[s]} · {counts[s]}
              </span>
            ))}
          {counts[SANS_ETAT] ? (
            <span className="rounded-full bg-bg-overlay px-2.5 py-0.5 text-[11px] font-semibold text-fg-muted">
              État inconnu · {counts[SANS_ETAT]}
            </span>
          ) : null}
        </div>
      )}

      {loading ? (
        <p className="px-6 py-10 text-center text-sm text-fg-subtle">Chargement…</p>
      ) : rows.length === 0 ? (
        <div className="flex flex-col items-center justify-center px-6 py-10 text-center">
          <Inbox className="mb-2 h-8 w-8 text-fg-subtle" />
          <p className="text-sm text-fg-subtle">{emptyLabel}</p>
        </div>
      ) : (
        <div className="max-h-96 overflow-y-auto">
          <table className="w-full">
            <thead className="sticky top-0 bg-bg-overlay">
              <tr>
                <th className="px-6 py-2.5 text-left text-xs font-semibold uppercase text-fg-subtle">Sujet</th>
                <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase text-fg-subtle">Data Room</th>
                <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase text-fg-subtle">Employé</th>
                <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase text-fg-subtle">État</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {sorted.map((r) => (
                <tr
                  key={r.id}
                  // Lot L6 (E9) : une requete s'ouvre sur l'onglet « Requetes du cabinet »
                  // (l'onglet Demandes ne les porte plus depuis le lot 1).
                  onClick={() => navigate(`/data-rooms?dossier=${r.dossierId}&tab=${direction === 'EMPLOYE_TO_CLIENT' ? 'requetes' : 'demandes'}`)}
                  className="cursor-pointer hover:bg-bg-overlay"
                  title="Ouvrir dans la Data Room"
                >
                  <td className="max-w-[14rem] px-6 py-3">
                    <p className="truncate text-sm font-medium text-fg">{r.sujet}</p>
                    <p className="text-[10px] text-fg-subtle">
                      {new Date(r.createdAt).toLocaleDateString('fr-FR')}
                    </p>
                  </td>
                  <td className="px-4 py-3">
                    <span className="flex items-center gap-1 text-xs text-fg" title={r.raisonSociale ?? undefined}>
                      <Building2 className="h-3.5 w-3.5 shrink-0 text-fg-subtle" />
                      <span className="max-w-[9rem] truncate">
                        {r.raisonSociale ?? r.dossierId.slice(0, 8).toUpperCase()}
                      </span>
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <span className="flex items-center gap-1 text-xs text-fg-muted">
                      <User className="h-3.5 w-3.5 shrink-0 text-fg-subtle" />
                      <span className="max-w-[9rem] truncate">{r.responsableNom ?? '—'}</span>
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${r.etatSuivi ? ETAT_BADGE[r.etatSuivi] : 'bg-bg-overlay text-fg-muted'}`}
                    >
                      {r.etatSuivi ? ETAT_SUIVI_LABELS[r.etatSuivi] : 'État inconnu'}
                    </span>
                    {direction === 'EMPLOYE_TO_CLIENT' && (
                      <p className="mt-0.5 text-[10px] text-fg-subtle">
                        {REQUETE_STATUT_LABELS[r.statut as keyof typeof REQUETE_STATUT_LABELS] ?? r.statut}
                      </p>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
