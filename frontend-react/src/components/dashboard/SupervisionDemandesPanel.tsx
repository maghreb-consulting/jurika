import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
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
import { Badge, type BadgeVariant } from '../ui/Badge';
import { EmptyState } from '../ui/EmptyState';
import { Tableau, type ColonneTableau } from '../ui/Tableau';

/**
 * Vue SUPERVISEUR (workspace-wide) : toutes les demandes des clients ET requetes
 * aux clients du cabinet, chaque ligne montrant le nom du dataroom (raison
 * sociale) et l'employe responsable (noms resolus par jointure cote back --
 * jamais d'UUID). Lecture seule (RG-U02-04) : aucune action de mutation ici.
 *
 * Lot L5 (E9, RG-COM-07) : demandes et requetes sont suivies par les trois memes
 * etats (non traitee, en cours, traitee), calcules par le serveur ; le statut
 * detaille d'une requete reste affiche en second.
 *
 * Lot L6b (E9) : composants de la bibliotheque ; une liste en echec est dite
 * indisponible (et non "aucune demande", motif 9).
 */

const ETAT_BADGE: Record<EtatSuivi, BadgeVariant> = {
  NON_TRAITEE: 'danger',
  EN_COURS: 'warning',
  TRAITEE: 'success',
};

const SANS_ETAT = 'SANS_ETAT';

export function SupervisionDemandesPanel() {
  const [demandes, setDemandes] = useState<DemandeSupervisionRow[] | null>([]);
  const [requetes, setRequetes] = useState<DemandeSupervisionRow[] | null>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    Promise.all([
      dataroomService.listSupervisionDemandes('CLIENT_TO_EMPLOYE').catch(() => null),
      dataroomService.listSupervisionDemandes('EMPLOYE_TO_CLIENT').catch(() => null),
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
    <section className="mt-6 space-y-3" aria-labelledby="titre-demandes-requetes">
      <h2 id="titre-demandes-requetes" className="text-lg font-semibold text-heading">
        Demandes et requêtes
      </h2>
      <TexteAide cle="supervision-demandes-requetes" titre="Suivi des demandes et des requêtes">
        Chaque demande d’un client et chaque requête du cabinet est suivie par trois états :
        « Non traitée » (rien n’a encore été fait), « En cours » (prise en charge, ou réponse du
        client à vérifier) et « Traitée ». Cette vue est en lecture seule : l’employé responsable
        du dossier traite la demande ou valide la requête depuis la Data Room ; cliquez sur une
        ligne pour l’ouvrir.
      </TexteAide>
      <div className="grid gap-4 xl:grid-cols-2">
        <SupervisionBlock
          title="Demandes des clients"
          icon={MessageSquare}
          direction="CLIENT_TO_EMPLOYE"
          rows={demandes}
          loading={loading}
          emptyLabel="Aucune demande client dans le cabinet."
        />
        <SupervisionBlock
          title="Requêtes du cabinet aux clients"
          icon={Send}
          direction="EMPLOYE_TO_CLIENT"
          rows={requetes}
          loading={loading}
          emptyLabel="Aucune requête envoyée aux clients."
        />
      </div>
    </section>
  );
}

function SupervisionBlock({
  title,
  icon: Icon,
  direction,
  rows: lignes,
  loading,
  emptyLabel,
}: {
  title: string;
  icon: typeof MessageSquare;
  direction: 'CLIENT_TO_EMPLOYE' | 'EMPLOYE_TO_CLIENT';
  rows: DemandeSupervisionRow[] | null;
  loading: boolean;
  emptyLabel: string;
}) {
  const navigate = useNavigate();
  const indisponible = lignes === null;
  const rows = useMemo(() => lignes ?? [], [lignes]);
  // Lot L6 (E9) : une requete s'ouvre sur l'onglet " Requetes du cabinet "
  // (l'onglet Demandes ne les porte plus depuis le lot 1).
  const onglet = direction === 'EMPLOYE_TO_CLIENT' ? 'requetes' : 'demandes';
  const lien = (r: DemandeSupervisionRow) => `/data-rooms?dossier=${r.dossierId}&tab=${onglet}`;

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
      (a, b) => rank(a.etatSuivi) - rank(b.etatSuivi) || new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    );
  }, [rows]);

  const colonnes: ColonneTableau<DemandeSupervisionRow>[] = [
    {
      cle: 'sujet',
      titre: 'Sujet',
      rendu: (r) => (
        <span className="block max-w-[14rem]">
          <Link
            to={lien(r)}
            onClick={(e) => e.stopPropagation()}
            className="block truncate rounded-sm font-medium text-fg hover:underline"
            title="Ouvrir dans la Data Room"
          >
            {r.sujet}
          </Link>
          <span className="text-xs text-fg-subtle">{new Date(r.createdAt).toLocaleDateString('fr-FR')}</span>
        </span>
      ),
    },
    {
      cle: 'dataroom',
      titre: 'Data Room',
      rendu: (r) => (
        <span className="flex items-center gap-1 text-fg" title={r.raisonSociale ?? undefined}>
          <Building2 className="h-3.5 w-3.5 shrink-0 text-fg-subtle" aria-hidden="true" />
          <span className="max-w-[9rem] truncate">{r.raisonSociale ?? r.dossierId.slice(0, 8).toUpperCase()}</span>
        </span>
      ),
    },
    {
      cle: 'employe',
      titre: 'Employé',
      masquerSous: 'md',
      rendu: (r) => (
        <span className="flex items-center gap-1 text-fg-muted">
          <User className="h-3.5 w-3.5 shrink-0 text-fg-subtle" aria-hidden="true" />
          <span className="max-w-[9rem] truncate">{r.responsableNom ?? '--'}</span>
        </span>
      ),
    },
    {
      cle: 'etat',
      titre: 'État',
      rendu: (r) => (
        <span>
          <Badge variant={r.etatSuivi ? ETAT_BADGE[r.etatSuivi] : 'neutral'}>
            {r.etatSuivi ? ETAT_SUIVI_LABELS[r.etatSuivi] : 'État inconnu'}
          </Badge>
          {direction === 'EMPLOYE_TO_CLIENT' && (
            <span className="mt-0.5 block text-xs text-fg-subtle">
              {REQUETE_STATUT_LABELS[r.statut as keyof typeof REQUETE_STATUT_LABELS] ?? r.statut}
            </span>
          )}
        </span>
      ),
    },
  ];

  return (
    <section className="min-w-0 rounded-xl border border-border bg-bg-raised shadow-card">
      <div className="flex items-center justify-between gap-2 border-b border-border px-5 py-3">
        <h3 className="flex items-center gap-2 text-base font-semibold text-heading">
          <Icon className="h-5 w-5 text-fg-subtle" aria-hidden="true" /> {title}
          <InfoBulle
            libelle={`Aide : ${title}`}
            texte={
              direction === 'EMPLOYE_TO_CLIENT'
                ? 'Requête « Non traitée » : le client n’a pas encore répondu. « En cours » : le client a répondu (à vérifier) ou un complément lui est demandé. « Traitée » : le cabinet a validé la réponse.'
                : 'Demande « Non traitée » : l’employé responsable ne l’a pas encore prise en charge. « En cours » : prise en charge. « Traitée » : le client a reçu la réponse par courriel.'
            }
          />
        </h3>
        <span className="text-xs text-fg-subtle">{indisponible ? '--' : `${rows.length} au total`}</span>
      </div>

      {rows.length > 0 && (
        <div className="flex flex-wrap gap-2 border-b border-border px-5 py-2.5">
          {ETAT_SUIVI_ORDRE.filter((s) => counts[s]).map((s) => (
            <Badge key={s} variant={ETAT_BADGE[s]}>
              <span data-testid={`compteur-etat-${s}`}>
                {ETAT_SUIVI_LABELS[s]} · {counts[s]}
              </span>
            </Badge>
          ))}
          {counts[SANS_ETAT] ? <Badge variant="neutral">État inconnu · {counts[SANS_ETAT]}</Badge> : null}
        </div>
      )}

      <Tableau
        legende={`Liste : ${title.toLowerCase()}`}
        colonnes={colonnes}
        lignes={sorted}
        cleLigne={(r) => r.id}
        chargement={loading}
        lignesChargement={3}
        onLigne={(r) => navigate(lien(r))}
        hauteurMax="24rem"
        className="rounded-none rounded-b-xl border-0"
        vide={
          indisponible ? (
            <EmptyState compact title="Liste indisponible" description="Elle n’a pas pu être chargée. Rechargez la page dans quelques instants." />
          ) : (
            <EmptyState compact icon={<Inbox />} title={emptyLabel} />
          )
        }
      />
    </section>
  );
}
