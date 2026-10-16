import { Link } from 'react-router-dom';
import { CalendarCheck } from 'lucide-react';
import type { DossierRisk, EcheanceItem } from '../../types/dashboard';
import { MOTIF_RISQUE_LABELS, labelOf } from '../../lib/dashboardLabels';
import { Card } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { EmptyState } from '../ui/EmptyState';
import { InfoBulle } from '../ui/Aide';
import { Lien } from '../ui/Lien';
import { Message } from '../ui/Message';
import { Skeleton, SqueletteZone } from '../ui/LoadingSkeleton';
import { dateCourte, etatDelai, joursRestants } from './delais';

const MAX_A_VENIR = 8;

/**
 * Lot L6b (E17) : "Delais a surveiller" du tableau de bord du superviseur, ce qui demande une
 * action maintenant : les dossiers dont un delai legal est depasse (dossiersARisque) puis les
 * delais legaux des demarches des 30 prochains jours (tranche J-30 du serveur, qui contient les
 * tranches J-15 et J-3 : les trois tranches se recouvraient a l'ecran), du plus proche au plus
 * lointain, avec le nom du dossier.
 *
 * Le serveur rend chaque delai par dossier (identifiant) : le nom vient de la liste des dossiers
 * du cabinet deja chargee (`nomDossier`) ; nom inconnu : le delai s'affiche sans nom, jamais avec
 * un nom invente. `risques` / `aVenir` null : la donnee n'a pas pu etre chargee (dit).
 */
export function DelaisCabinetCarte({
  risques,
  aVenir,
  nomDossier,
  loading,
  aujourdHui = new Date(),
}: {
  risques: DossierRisk[] | null;
  aVenir: EcheanceItem[] | null;
  nomDossier: (dossierId: string) => string | undefined;
  loading: boolean;
  aujourdHui?: Date;
}) {
  const titre = (
    <span className="inline-flex items-center gap-1.5">
      Délais à surveiller
      <InfoBulle
        libelle="Aide : délais à surveiller"
        texte="Délais légaux des démarches restant à accomplir. Un délai court dès que l’étape de référence de la démarche est cochée sur le ticket. « En retard » : au moins un délai du dossier est dépassé. Ouvrez la Data Room du dossier pour voir son responsable et, au besoin, réaffecter le dossier."
      />
    </span>
  );
  const indisponible = risques === null || aVenir === null;
  const tries = [...(aVenir ?? [])]
    .filter((e) => e.dateEcheance)
    .sort((a, b) => (a.dateEcheance ?? '').localeCompare(b.dateEcheance ?? ''));

  return (
    <Card accent title={titre} description="Dossiers en retard, puis délais légaux des 30 prochains jours, du plus proche au plus lointain." className="mb-8">
      {loading ? (
        <SqueletteZone libelle="Chargement des délais…">
          <div className="space-y-3">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-9 w-full" />
            ))}
          </div>
        </SqueletteZone>
      ) : indisponible ? (
        <Message ton="attention" titre="Délais indisponibles">
          Les délais des démarches n’ont pas pu être chargés. Rechargez la page dans quelques instants ; les autres zones
          du tableau de bord restent à jour.
        </Message>
      ) : risques!.length === 0 && tries.length === 0 ? (
        <EmptyState
          compact
          icon={<CalendarCheck />}
          title="Aucun délai à surveiller"
          description="Aucun dossier en retard et aucun délai légal dans les 30 prochains jours. Un délai apparaît ici dès que l’étape de référence d’une démarche est cochée sur un ticket."
        />
      ) : (
        <div className="space-y-5">
          {risques!.length > 0 && (
            <div>
              <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-danger">En retard · {risques!.length}</h3>
              <ul className="divide-y divide-border rounded-lg border border-danger/40 bg-danger-soft/40">
                {risques!.map((r) => (
                  <li key={r.dossierId + r.motif} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2 text-sm">
                    <Link to={`/data-rooms?dossier=${r.dossierId}`} className="rounded-sm font-medium text-fg hover:underline">
                      {r.raisonSociale}
                    </Link>
                    <Badge variant="danger">{labelOf(MOTIF_RISQUE_LABELS, r.motif)}</Badge>
                  </li>
                ))}
              </ul>
            </div>
          )}
          <div>
            <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-fg-muted">30 prochains jours · {tries.length}</h3>
            {tries.length === 0 ? (
              <p className="text-sm text-fg-muted">Aucun délai légal dans les 30 prochains jours.</p>
            ) : (
              <ol className="divide-y divide-border" aria-label="Délais légaux des 30 prochains jours, du plus proche au plus lointain">
                {tries.slice(0, MAX_A_VENIR).map((e) => {
                  const d = etatDelai(joursRestants(e.dateEcheance!, aujourdHui));
                  const nom = nomDossier(e.dossierId);
                  return (
                    <li key={e.id} className="grid grid-cols-[4.5rem_1fr] items-start gap-x-3 gap-y-1 py-2.5 text-sm sm:grid-cols-[4.5rem_1fr_auto] sm:items-center">
                      <span className="tabular-nums text-fg-muted">{dateCourte(e.dateEcheance!)}</span>
                      <span className="min-w-0">
                        {nom && (
                          <Link to={`/data-rooms?dossier=${e.dossierId}`} className="mr-2 rounded-sm font-medium text-fg hover:underline">
                            {nom}
                          </Link>
                        )}
                        <span className="text-fg-muted">{e.libelle}</span>
                      </span>
                      <span className="col-start-2 sm:col-start-auto">
                        <Badge variant={d.variante}>{d.texte}</Badge>
                      </span>
                    </li>
                  );
                })}
              </ol>
            )}
            {tries.length > MAX_A_VENIR && (
              <p className="mt-2 text-sm text-fg-muted">
                et {tries.length - MAX_A_VENIR} autre{tries.length - MAX_A_VENIR > 1 ? 's' : ''} délai
                {tries.length - MAX_A_VENIR > 1 ? 's' : ''} dans les 30 jours : <Lien to="/calendar">ouvrir le calendrier</Lien>.
              </p>
            )}
          </div>
        </div>
      )}
    </Card>
  );
}
