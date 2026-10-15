import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { chartTheme } from '../../lib/chartTheme';
import { AXES, INFOBULLE_GRAPHIQUE, LIBELLE_INFOBULLE } from './charts/apparence';

interface Props {
  data: { day: string; count: number }[];
  color?: string;
  height?: number;
}

/**
 * Sprint 12.5 T7 — EvolutionChart au theme marketing.
 * Default color = accent (or signature). Lot L6b (E9) : grille, axes et infobulle aux jetons.
 */
export function EvolutionChart({ data, color = chartTheme.accent, height = 200 }: Props) {
  const formatted = data.map((d) => ({
    ...d,
    label: new Date(d.day).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' }),
  }));
  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart data={formatted} margin={{ top: 5, right: 10, bottom: 0, left: -10 }}>
        <CartesianGrid strokeDasharray="3 3" stroke={AXES.grille} />
        <XAxis dataKey="label" tick={{ fontSize: 11, fill: AXES.texte }} />
        <YAxis tick={{ fontSize: 11, fill: AXES.texte }} />
        <Tooltip
          contentStyle={INFOBULLE_GRAPHIQUE}
          labelStyle={LIBELLE_INFOBULLE}
          cursor={{ stroke: AXES.curseur }}
        />
        <Line type="monotone" dataKey="count" stroke={color} strokeWidth={2} dot={{ r: 3, fill: color }} />
      </LineChart>
    </ResponsiveContainer>
  );
}
