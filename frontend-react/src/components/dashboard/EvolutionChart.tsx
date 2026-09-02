import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { chartTheme, chartTooltipStyle } from '../../lib/chartTheme';

interface Props {
  data: { day: string; count: number }[];
  color?: string;
  height?: number;
}

/**
 * Sprint 12.5 T7 — EvolutionChart au theme marketing.
 * Default color = accent (or signature) au lieu de #2563EB.
 */
export function EvolutionChart({ data, color = chartTheme.accent, height = 200 }: Props) {
  const formatted = data.map((d) => ({
    ...d,
    label: new Date(d.day).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' }),
  }));
  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart data={formatted} margin={{ top: 5, right: 10, bottom: 0, left: -10 }}>
        <CartesianGrid strokeDasharray="3 3" stroke={chartTheme.border} />
        <XAxis dataKey="label" tick={{ fontSize: 11, fill: chartTheme.fgSubtle }} />
        <YAxis tick={{ fontSize: 11, fill: chartTheme.fgSubtle }} />
        <Tooltip
          contentStyle={chartTooltipStyle}
          labelStyle={{ fontWeight: 600, color: chartTheme.fg }}
          cursor={{ stroke: chartTheme.border }}
        />
        <Line type="monotone" dataKey="count" stroke={color} strokeWidth={2} dot={{ r: 3, fill: color }} />
      </LineChart>
    </ResponsiveContainer>
  );
}
