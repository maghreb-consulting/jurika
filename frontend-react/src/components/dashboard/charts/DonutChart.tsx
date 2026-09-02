import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { chartCategoricalPalette, chartTooltipStyle } from '../../../lib/chartTheme';

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
}

/**
 * Donut categoriel a la charte JURIKA (Recharts PieChart, innerRadius).
 * Couleurs : soit semantiques via `colorByKey`, soit palette categorielle.
 * L'appelant gere l'etat vide (data === []) en amont.
 */
export function DonutChart({ data, height = 200, colorByKey }: Props) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <PieChart>
        <Pie
          data={data}
          dataKey="count"
          nameKey="label"
          cx="50%"
          cy="50%"
          innerRadius="55%"
          outerRadius="80%"
          paddingAngle={2}
          stroke="none"
        >
          {data.map((slice, i) => (
            <Cell
              key={slice.label}
              fill={
                (slice.key && colorByKey?.[slice.key]) ||
                chartCategoricalPalette[i % chartCategoricalPalette.length]
              }
            />
          ))}
        </Pie>
        <Tooltip contentStyle={chartTooltipStyle} />
        <Legend
          verticalAlign="bottom"
          height={28}
          iconType="circle"
          iconSize={9}
          formatter={(value) => <span style={{ fontSize: 11, color: '#1c3461' }}>{value}</span>}
        />
      </PieChart>
    </ResponsiveContainer>
  );
}
