import { useEffect, useMemo, useState, useEffectEvent } from 'react';
import { Search } from 'lucide-react';
import { InfoBulle } from '../ui/Aide';
import { dataroomService } from '../../services/dataroom.service';
import { LIQUIDATION_DELAI_JOURS, joursCalendairesEntre } from './liquidationDelai';
import { displayFormeJuridique } from '../../types/dataroom';
import type { DossierBrief } from '../../types/dataroom';

interface DossierAutocompleteProps {
  /** Identifiant actuel selectionne (UUID) — peut etre vide. */
  value: string;
  /** Callback appele avec un DossierBrief complet (ou null si reset). */
  onSelect: (dossier: DossierBrief | null) => void;
  /** Filtre par statut (vide = tous). Casse-insensible. */
  allowedStatuts?: string[];
  /** Label de l'input (defaut "Societe"). */
  label?: string;
  /** Aide texte sous le champ. */
  help?: string;
  /** Disable input (lecture seule). */
  disabled?: boolean;
  /** Forme juridique requise (filtre la liste). */
  requiredForme?: string[];
  /**
   * Lot DIVERS §C (2026-08-13) — origine des dossiers proposés.
   * Défaut `'MAROCAINE'` : les sociétés mères ÉTRANGÈRES (dossiers créés par le
   * workflow SUCCURSALE_ETR pour porter leur Data Room) ne doivent PAS apparaître
   * dans les workflows de droit marocain (dissolution, modification, PV AGO…).
   * Le workflow SUCCURSALE_ETR passe `'ETRANGERE'` pour proposer la réutilisation
   * d'une mère déjà enregistrée. `'TOUTES'` désactive le filtre.
   */
  origine?: 'MAROCAINE' | 'ETRANGERE' | 'TOUTES';
  /**
   * Lot W2 (2026-07-04) — affiche pour chaque dossier un badge « delai 15 j »
   * (RG-LI03) base sur {@link DossierBrief.dateDissolution}. Utilise par le
   * workflow LIQUIDATION pour signaler si le delai legal depuis la dissolution
   * est ecoule (vert), en cours (orange) ou inconnu (neutre).
   */
  showDissolutionDelay?: boolean;
}

interface DelaiBadge {
  label: string;
  className: string;
}

/**
 * Calcule le badge de delai a partir de la date de dissolution persistee.
 *
 * <p>Lot « Liquidation 4 etapes » (2026-08-13) : ce badge portait l'ANCIENNE regle
 * (seuil 16 j + ecart calcule en millisecondes). Il est desormais aligne sur le noyau
 * partage {@code liquidationDelai} — seuil {@link LIQUIDATION_DELAI_JOURS} = 15 et
 * jours CALENDAIRES (insensibles au fuseau / aux changements d'heure) — sinon il
 * contredirait l'etape 1 du workflow (une societe dissoute depuis 15 jours serait
 * affichee « delai en cours » alors que la cloture est acceptee).
 *
 * <p>La comparaison se fait ici avec AUJOURD'HUI : c'est une aide a la SELECTION
 * (« cette societe est-elle mure pour la liquidation ? »). La regle BLOQUANTE, elle,
 * compare la dissolution a la date de cloture saisie (cf. {@code liquidationDelaiError}).
 */
function computeDissolutionDelayBadge(d: DossierBrief): DelaiBadge {
  const inconnue: DelaiBadge = {
    label: 'Date de dissolution inconnue',
    className: 'bg-bg-overlay text-fg-subtle border border-border',
  };
  if (!d.dateDissolution) return inconnue;
  const aujourdhui = new Date().toISOString().slice(0, 10);
  const joursDepuis = joursCalendairesEntre(String(d.dateDissolution), aujourdhui);
  if (joursDepuis === null) return inconnue;
  if (joursDepuis >= LIQUIDATION_DELAI_JOURS) {
    return {
      label: `Délai de ${LIQUIDATION_DELAI_JOURS} jours écoulé`,
      className: 'bg-emerald-100 text-emerald-800 border border-emerald-300',
    };
  }
  const reste = Math.max(0, LIQUIDATION_DELAI_JOURS - joursDepuis);
  return {
    label: `Délai en cours (il reste ${reste} jour(s))`,
    className: 'bg-warning/10 text-amber-800 border border-amber-300',
  };
}

/** L6 : statut de la société en clair (jamais le code interne). */
const LIBELLES_STATUT: Record<string, string> = {
  ACTIVE: 'en activité',
  ACTIF: 'en activité',
  DISSOUTE: 'dissoute',
  RADIEE: 'radiée',
  EN_COURS: 'en cours de création',
  CLOTURE: 'clôturée',
  ARCHIVE: 'archivée',
};

function libelleStatut(statut: unknown): string {
  const code = (statut ?? '').toString().toUpperCase();
  return LIBELLES_STATUT[code] ?? code.toLowerCase().replace(/_/g, ' ');
}

/**
 * Composant production : autocomplete sur la liste des dossiers du workspace.
 * Mode UX : tape pour filtrer, clic pour selectionner. Fallback : si liste vide,
 * affiche un message d'absence d'entreprise eligible.
 *
 * Pas de pagination V1 (liste cabinet typiquement < 200 dossiers).
 */
export function DossierAutocomplete({
  value,
  onSelect,
  allowedStatuts,
  label = 'Société concernée',
  help,
  disabled = false,
  requiredForme,
  origine = 'MAROCAINE',
  showDissolutionDelay = false,
}: DossierAutocompleteProps) {
  const [dossiers, setDossiers] = useState<DossierBrief[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState<DossierBrief | null>(null);

  // Lot L6 : chargement au montage en « événement d'effet » (useEffectEvent) : il lit la valeur
  // courante sans en dépendre, sans désactiver la règle des dépendances.
  const chargerDossiers = useEffectEvent(async (estAnnule: () => boolean) => {
      setLoading(true);
      setError(null);
      try {
        const list = await dataroomService.listDossiers();
        if (estAnnule()) return;
        setDossiers(list);
        if (value) {
          const cur = list.find((d) => d.id === value) ?? null;
          setSelected(cur);
        }
      } catch (e) {
        if (!estAnnule()) {
          setError(
            (e as { message?: string })?.message ??
              'Impossible de charger la liste des sociétés. Rechargez la page.',
          );
        }
      } finally {
        if (!estAnnule()) setLoading(false);
      }
  });

  useEffect(() => {
    let cancelled = false;
    // Lancement par promesse : aucun état posé de façon synchrone dans l'effet.
    Promise.resolve().then(() => chargerDossiers(() => cancelled)).catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    return dossiers.filter((d) => {
      if (origine !== 'TOUTES') {
        // Un dossier sans `origine` est marocain (colonne à défaut 'MAROCAINE',
        // ou backend non redéployé).
        const o = (d.origine ?? 'MAROCAINE').toString().toUpperCase();
        if (o !== origine) return false;
      }
      if (allowedStatuts && allowedStatuts.length > 0) {
        const st = (d.statut ?? '').toString().toUpperCase();
        if (!allowedStatuts.map((s) => s.toUpperCase()).includes(st)) return false;
      }
      if (requiredForme && requiredForme.length > 0) {
        const f = (d.formeJuridique ?? '').toString().toUpperCase();
        if (!requiredForme.map((s) => s.toUpperCase()).includes(f)) return false;
      }
      if (term === '') return true;
      return (
        d.raisonSociale.toLowerCase().includes(term) ||
        (d.ice ?? '').toLowerCase().includes(term) ||
        (d.ville ?? '').toLowerCase().includes(term)
      );
    });
  }, [search, dossiers, allowedStatuts, requiredForme, origine]);

  const handlePick = (d: DossierBrief) => {
    setSelected(d);
    setSearch('');
    setOpen(false);
    onSelect(d);
  };

  const handleClear = () => {
    setSelected(null);
    setSearch('');
    onSelect(null);
  };

  return (
    <div className="space-y-1">
      <label className="flex items-center gap-1 text-sm font-medium text-fg-muted">
        {label} *
        <InfoBulle
          libelle="Comment choisir la société ?"
          texte="Tapez une partie du nom, de l’ICE ou de la ville, puis cliquez sur la société. Ses données (identité, associés, gérants) sont reprises du dossier : vous n’aurez pas à les ressaisir. Seules les sociétés concernées par cette opération sont proposées."
        />
      </label>

      {selected ? (
        <div className="flex items-center justify-between rounded-lg border border-emerald-300 bg-emerald-50 px-3 py-2">
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold text-emerald-900">
              {selected.raisonSociale}
            </p>
            <p className="truncate text-xs text-emerald-700">
              {displayFormeJuridique(selected) ?? 'Forme inconnue'} ·{' '}
              {selected.origine === 'ETRANGERE'
                ? (selected.pays ?? 'Pays inconnu')
                : selected.ice
                  ? `ICE ${selected.ice}`
                  : 'ICE manquant'}{' '}
              · {selected.ville ?? '—'} · {libelleStatut(selected.statut)}
            </p>
            {showDissolutionDelay && (
              <span
                className={`mt-1 inline-block rounded px-1.5 py-0.5 text-[11px] font-medium ${
                  computeDissolutionDelayBadge(selected).className
                }`}
              >
                {computeDissolutionDelayBadge(selected).label}
              </span>
            )}
          </div>
          {!disabled && (
            <button
              type="button"
              onClick={handleClear}
              className="ml-3 rounded px-2 py-1 text-xs text-emerald-800 hover:bg-emerald-100"
              aria-label="Changer la société sélectionnée"
            >
              Changer
            </button>
          )}
        </div>
      ) : (
        <div className="relative">
          <div className="flex items-center gap-2 rounded-lg border border-border-hi bg-bg-raised px-3 py-2 focus-within:border-indigo-500 focus-within:ring-2 focus-within:ring-indigo-200">
            <Search className="h-4 w-4 text-fg-subtle" />
            <input
              type="text"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setOpen(true);
              }}
              onFocus={() => setOpen(true)}
              placeholder={loading ? 'Chargement…' : 'Rechercher par nom, ICE ou ville…'}
              disabled={disabled || loading}
              className="flex-1 bg-transparent text-sm outline-none disabled:cursor-not-allowed"
              aria-label="Recherche de société"
              aria-expanded={open}
            />
          </div>
          {error && (
            <p role="alert" className="mt-1 text-xs text-danger">
              {error}
            </p>
          )}
          {open && !loading && (
            <ul
              role="listbox"
              className="absolute z-10 mt-1 max-h-72 w-full overflow-y-auto rounded-lg border border-border bg-bg-raised shadow-lg"
            >
              {filtered.length === 0 ? (
                <li className="px-3 py-4 text-center text-xs text-fg-subtle">
                  Aucune société ne correspond à la recherche
                  {allowedStatuts && allowedStatuts.length > 0
                    ? ` (seules les sociétés ${allowedStatuts.map(libelleStatut).join(' ou ')} sont proposées)`
                    : ''}
                  .
                </li>
              ) : (
                filtered.map((d) => (
                  <li
                    key={d.id}
                    role="option"
                    aria-selected={false}
                    className="cursor-pointer border-b border-border last:border-none px-3 py-2 hover:bg-accent/10"
                    onClick={() => handlePick(d)}
                  >
                    <p className="text-sm font-semibold text-fg">{d.raisonSociale}</p>
                    <p className="text-[11px] text-fg-subtle">
                      {displayFormeJuridique(d) ?? 'Forme inconnue'} ·{' '}
                      {d.origine === 'ETRANGERE'
                        ? (d.pays ?? 'Pays inconnu')
                        : d.ice
                          ? `ICE ${d.ice}`
                          : 'ICE manquant'}{' '}
                      · {d.ville ?? '—'} ·{' '}
                      <span className="font-medium">{libelleStatut(d.statut)}</span>
                    </p>
                    {showDissolutionDelay && (
                      <span
                        className={`mt-1 inline-block rounded px-1.5 py-0.5 text-[10px] font-medium ${
                          computeDissolutionDelayBadge(d).className
                        }`}
                      >
                        {computeDissolutionDelayBadge(d).label}
                      </span>
                    )}
                  </li>
                ))
              )}
            </ul>
          )}
        </div>
      )}

      {help && <p className="text-xs text-fg-subtle">{help}</p>}
    </div>
  );
}
