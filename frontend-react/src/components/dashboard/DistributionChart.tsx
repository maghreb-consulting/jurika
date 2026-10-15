import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { chartTheme } from '../../lib/chartTheme';
import { AXES, INFOBULLE_GRAPHIQUE, LIBELLE_INFOBULLE } from './charts/apparence';

interface Props {
  data: { label: string; count: number }[];
  color?: string;
  height?: number;
}

/**
 * Sprint 12.5 T7 — DistributionChart au theme marketing.
 * Default color = success (emerald) pour distribution positive ; passe en
 * accent (or) ou autre token via la prop color au besoin.
 */
export function DistributionChart({ data, color = chartTheme.success, height = 200 }: Props) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart layout="vertical" data={data} margin={{ top: 5, right: 10, bottom: 0, left: 30 }}>
        <CartesianGrid strokeDasharray="3 3" stroke={AXES.grille} />
        <XAxis type="number" tick={{ fontSize: 11, fill: AXES.texte }} />
        <YAxis
          type="category"
          dataKey="label"
          tick={{ fontSize: 11, fill: AXES.texte }}
          width={80}
        />
        <Tooltip contentStyle={INFOBULLE_GRAPHIQUE} labelStyle={LIBELLE_INFOBULLE} cursor={{ fill: AXES.grille, opacity: 0.4 }} />
        <Bar dataKey="count" fill={color} radius={[0, 4, 4, 0]} />
      </BarChart>
    </ResponsiveContainer>
  );
}
