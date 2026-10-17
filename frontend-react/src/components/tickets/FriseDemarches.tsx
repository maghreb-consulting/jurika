import { AlarmClock, AlertOctagon, CircleCheck, CircleSlash, Clock, Minus, type LucideIcon } from 'lucide-react';
import { Card } from '../ui/Card';
import { InfoBulle } from '../ui/Aide';
import { cn } from '../../lib/utils';
import type { VueDemarches } from '../../types/demarche';
import { compterTons, construireFrise, JOURS_ECHEANCE_PROCHE, type TonDelai } from './friseDemarches';

/**
 * Lot L6b (E7, point 8) : la frise des demarches du ticket. Chaque demarche, dans l'ordre
 * du guide, avec la couleur de son delai : vert (dans les temps), orange (echeance proche),
 * rouge (depassee). La couleur n'est jamais seule : chaque etape porte une icone et son
 * etat en toutes lettres. Paires texte / fond : celles des badges de la bibliotheque
 * (contraste AA verifie dans les deux themes).
 */
const TONS: Record<TonDelai, { icone: LucideIcon; puce: string; filet: string; legende: string }> = {
  rouge: { icone: AlertOctagon, puce: 'bg-danger-soft text-danger border-danger/30', filet: 'border-l-danger', legende: 'Dépassée' },
  orange: { icone: AlarmClock, puce: 'bg-warning-soft text-warning border-warning/30', filet: 'border-l-warning', legende: 'Échéance proche' },
  vert: { icone: Clock, puce: 'bg-success-soft text-success border-success/30', filet: 'border-l-success', legende: 'Dans les temps' },
  accomplie: { icone: CircleCheck, puce: 'bg-success-soft text-success border-success/30', filet: 'border-l-success', legende: 'Accomplie' },
  ecartee: { icone: CircleSlash, puce: 'bg-bg-overlay text-fg-muted border-border', filet: 'border-l-border-hi', legende: 'Écartée' },
  sans: { icone: Minus, puce: 'bg-bg-overlay text-fg-muted border-border', filet: 'border-l-border-hi', legende: 'Sans échéance' },
};

const ORDRE_LEGENDE: TonDelai[] = ['rouge', 'orange', 'vert', 'accomplie', 'ecartee', 'sans'];

export function FriseDemarches({ vue }: { vue: VueDemarches }) {
  const phases = construireFrise(vue).filter((p) => p.etapes.length > 0);
  if (phases.length === 0) return null;
  const n = compterTons(phases);

  return (
    <Card
      title={
        <span className="flex items-center gap-1">
          Frise des démarches
          <InfoBulle
            libelle="Comment lire la frise ?"
            texte={`Chaque démarche du ticket, dans l’ordre du guide. Rouge : l’échéance est dépassée. Orange : l’échéance tombe dans ${JOURS_ECHEANCE_PROCHE} jours ou moins (seuil calculé par la plateforme). Vert : l’échéance est connue et plus lointaine. Une démarche sans échéance n’en a pas encore : son point de départ n’est pas posé, ou le guide n’en fixe pas.`}
          />
        </span>
      }
      titleAs="h3"
      description={
        n.rouge + n.orange > 0
          ? `${n.rouge} dépassée${n.rouge > 1 ? 's' : ''}, ${n.orange} à échéance proche : traitez-les d’abord.`
          : 'Aucune échéance dépassée ni proche.'
      }
    >
      <div className="space-y-4" data-testid="frise-demarches">
        <ul className="flex flex-wrap gap-2" aria-label="Légende de la frise">
          {ORDRE_LEGENDE.filter((t) => n[t] > 0).map((t) => {
            const { icone: Icone, puce, legende } = TONS[t];
            return (
              <li key={t} className={cn('inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-xs font-medium', puce)}>
                <Icone className="h-3.5 w-3.5" aria-hidden="true" />
                {legende} : {n[t]}
              </li>
            );
          })}
        </ul>

        {phases.map((phase) => (
          <section key={phase.code} aria-label={phase.libelle}>
            <h4 className="mb-2 text-xs font-semibold text-fg-muted">{phase.libelle}</h4>
            <ol className="grid gap-2" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(11.5rem, 1fr))' }}>
              {phase.etapes.map(({ demarche: d, etat }) => {
                const { icone: Icone, puce, filet } = TONS[etat.ton];
                return (
                  <li
                    key={d.demarcheId}
                    data-testid={`frise-${d.ordre}`}
                    data-ton={etat.ton}
                    className={cn('flex min-w-0 flex-col gap-1.5 rounded-lg border border-border border-l-4 bg-bg-raised p-2.5', filet)}
                  >
                    <p className="line-clamp-2 break-words text-xs text-fg" title={d.libelle}>
                      <span className="font-semibold tabular-nums">{d.ordre}.</span> {d.libelle}
                    </p>
                    <p className={cn('inline-flex w-fit max-w-full items-center gap-1 rounded-full border px-2 py-0.5 text-[11px] font-medium', puce)}>
                      <Icone className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                      <span className="truncate">{etat.libelle}</span>
                    </p>
                    {etat.detail && <p className="text-[11px] leading-snug text-fg-muted">{etat.detail}</p>}
                  </li>
                );
              })}
            </ol>
          </section>
        ))}
      </div>
    </Card>
  );
}
