import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { chartTheme, chartTooltipStyle } from '../../../lib/chartTheme';

export interface TrendSeries {
  key: string;
  name: string;
  color: string;
}

interface Props {
  /** Chaque point : { [xKey]: string, [series.key]: number, ... } */
  data: Array<Record<string, string | number>>;
  xKey: string;
  series: TrendSeries[];
  height?: number;
}

/**
 * Courbe(s) generiques a la charte. Supporte 1..N series (legende affichee
 * des que >1). Utilise pour l'evolution mensuelle (crees vs clotures) et les
 * inscriptions mensuelles (serie unique). Etat vide gere par l'appelant.
 */
export function TrendLineChart({ data, xKey, series, height = 220 }: Props) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart data={data} margin={{ top: 5, right: 12, bottom: 0, left: -12 }}>
        <CartesianGrid strokeDasharray="3 3" stroke={chartTheme.border} />
        <XAxis dataKey={xKey} tick={{ fontSize: 11, fill: chartTheme.fgSubtle }} />
        <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: chartTheme.fgSubtle }} />
        <Tooltip
          contentStyle={chartTooltipStyle}
          labelStyle={{ fontWeight: 600, color: chartTheme.fg }}
          cursor={{ stroke: chartTheme.border }}
        />
        {series.length > 1 && (
          <Legend
            verticalAlign="top"
            height={24}
            iconType="plainline"
            formatter={(value) => <span style={{ fontSize: 11, color: chartTheme.fgMuted }}>{value}</span>}
          />
        )}
        {series.map((s) => (
          <Line
            key={s.key}
            type="monotone"
            dataKey={s.key}
            name={s.name}
            stroke={s.color}
            strokeWidth={2}
            dot={{ r: 2.5, fill: s.color }}
          />
        ))}
      </LineChart>
    </ResponsiveContainer>
  );
}
