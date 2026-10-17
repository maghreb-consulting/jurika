import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { COULEURS_CATEGORIES, INFOBULLE_GRAPHIQUE, TEXTE_LEGENDE } from './apparence';

export interface DonutSlice {
  /** Cle brute (pour resolution couleur semantique optionnelle). */
  key?: string;
  label: string;
  count: number;
}

interface Props {
  data: DonutSlice[];
  height?: number;
  /** Map cle->couleur (semantique). A defaut, palette categorielle par index. */
  colorByKey?: Record<string, string>;
  /** Lot L6b (E18) : nom du graphique pour les lecteurs d'ecran (liste equivalente). */
  legende?: string;
}

/**
 * Donut categoriel a la charte JURIKA (Recharts PieChart, innerRadius).
 * Couleurs : soit semantiques via `colorByKey`, soit palette categorielle.
 * L'appelant gere l'etat vide (data === []) en amont.
 *
 * Lot L6b (E17) : le total s'ecrit au centre et chaque libelle de la legende porte sa valeur
 * (un anneau sans chiffres ne se lit pas). Les tableaux de bord lui preferent desormais les
 * barres horizontales etiquetees ; il reste utilise par la fiche d'un cabinet (super-admin).
 * Lot L6b (E18) : couleurs par defaut lues dans les jetons (clair et sombre), liste equivalente
 * pour les lecteurs d'ecran ; sert aux repartitions sans ordre (types d'operation, forfaits).
 */
export function DonutChart({ data, height = 200, colorByKey, legende }: Props) {
  const total = data.reduce((n, s) => n + (Number.isFinite(s.count) ? s.count : 0), 0);
  const parLibelle = new Map(data.map((s) => [s.label, s.count]));
  return (
    <div className="relative">
      {legende && (
        <ul className="sr-only" aria-label={`${legende} (total ${total})`}>
          {data.map((s) => (
            <li key={s.label}>
              {s.label} : {s.count}
            </li>
          ))}
        </ul>
      )}
      <ResponsiveContainer width="100%" height={height}>
        <PieChart>
          <Pie
            data={data}
            dataKey="count"
            nameKey="label"
            cx="50%"
            cy="45%"
            innerRadius="55%"
            outerRadius="80%"
            paddingAngle={2}
            stroke="none"
            isAnimationActive={false}
          >
            {data.map((slice, i) => (
              <Cell
                key={slice.label}
                fill={(slice.key && colorByKey?.[slice.key]) || COULEURS_CATEGORIES[i % COULEURS_CATEGORIES.length]}
              />
            ))}
          </Pie>
          <Tooltip contentStyle={INFOBULLE_GRAPHIQUE} itemStyle={{ color: 'var(--color-fg)' }} />
          <Legend
            verticalAlign="bottom"
            height={28}
            iconType="circle"
            iconSize={9}
            formatter={(value) => (
              <span style={TEXTE_LEGENDE}>
                {value} · {parLibelle.get(String(value)) ?? 0}
              </span>
            )}
          />
        </PieChart>
      </ResponsiveContainer>
      <div className="pointer-events-none absolute inset-x-0 flex flex-col items-center" style={{ top: `calc(${height * 0.45}px - 1.1rem)` }}>
        <span className="text-xl font-semibold tabular-nums text-heading">{total}</span>
        <span className="text-[0.65rem] uppercase tracking-wide text-fg-subtle">Total</span>
      </div>
    </div>
  );
}
