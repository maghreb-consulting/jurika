import { Bar, CartesianGrid, ComposedChart, LabelList, Line, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { AXES, COULEUR_SERIE, INFOBULLE_GRAPHIQUE, LIBELLE_INFOBULLE, type TonSerie } from './apparence';

export interface SerieColonnes {
  cle: string;
  nom: string;
  ton: TonSerie;
}

interface Props {
  /** Une ligne par periode : { [cleX]: libelle, [serie.cle]: nombre entier }. */
  donnees: Array<Record<string, string | number>>;
  cleX: string;
  series: SerieColonnes[];
  /** Description du graphique pour les lecteurs d'ecran (legende du tableau equivalent). */
  legende: string;
  hauteur?: number;
  /**
   * Lot L6b (E18) : serie lue sur un second axe (a droite), tracee en ligne droite (cumul,
   * total) ; sa derniere valeur est ecrite dans la legende.
   */
  ligne?: SerieColonnes;
}

function nombre(v: unknown): number {
  return typeof v === 'number' && Number.isFinite(v) ? v : 0;
}

/**
 * Lot L6b (E17) : colonnes (une ou plusieurs series) pour des COMPTAGES par periode. Des
 * comptages sont discrets : des colonnes, jamais une courbe lissee qui inventerait des valeurs
 * entre deux points. Axe en entiers, valeur ecrite au-dessus de chaque colonne non nulle,
 * legende avec le total de chaque serie, et un tableau equivalent pour les lecteurs d'ecran.
 */
export function ColonnesChart({ donnees, cleX, series, legende, hauteur = 220, ligne }: Props) {
  const totaux = series.map((s) => donnees.reduce((n, d) => n + nombre(d[s.cle]), 0));
  return (
    <figure className="m-0">
      <div aria-hidden="true">
        <ResponsiveContainer width="100%" height={hauteur}>
          <ComposedChart data={donnees} margin={{ top: 18, right: ligne ? -8 : 4, bottom: 0, left: -18 }} barGap={3} barCategoryGap="22%">
            <CartesianGrid vertical={false} stroke={AXES.grille} />
            <XAxis
              dataKey={cleX}
              tickLine={false}
              axisLine={{ stroke: AXES.grille }}
              tick={{ fontSize: 11, fill: AXES.texte }}
              interval="preserveStartEnd"
            />
            <YAxis yAxisId="g" allowDecimals={false} tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: AXES.texte }} width={44} />
            {ligne && (
              <YAxis yAxisId="d" orientation="right" allowDecimals={false} tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: AXES.texte }} width={36} />
            )}
            <Tooltip contentStyle={INFOBULLE_GRAPHIQUE} labelStyle={LIBELLE_INFOBULLE} cursor={{ fill: AXES.grille, opacity: 0.35 }} />
            {series.map((s) => (
              <Bar
                yAxisId="g"
                key={s.cle}
                dataKey={s.cle}
                name={s.nom}
                fill={COULEUR_SERIE[s.ton]}
                radius={[3, 3, 0, 0]}
                maxBarSize={28}
                isAnimationActive={false}
              >
                <LabelList
                  dataKey={s.cle}
                  position="top"
                  formatter={(v: unknown) => (nombre(v) > 0 ? String(v) : '')}
                  style={{ fontSize: 11, fill: AXES.texte }}
                />
              </Bar>
            ))}
            {ligne && (
              <Line
                yAxisId="d"
                type="linear"
                dataKey={ligne.cle}
                name={ligne.nom}
                stroke={COULEUR_SERIE[ligne.ton]}
                strokeWidth={2}
                dot={{ r: 2.5, fill: COULEUR_SERIE[ligne.ton] }}
                isAnimationActive={false}
              />
            )}
          </ComposedChart>
        </ResponsiveContainer>
      </div>
      <figcaption className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-fg-muted">
        {series.map((s, i) => (
          <span key={s.cle} className="inline-flex items-center gap-1.5">
            <span aria-hidden="true" className="h-2.5 w-2.5 rounded-sm" style={{ backgroundColor: COULEUR_SERIE[s.ton] }} />
            {s.nom} sur la période :
            <span className="font-semibold tabular-nums text-fg">{totaux[i]}</span>
          </span>
        ))}
        {ligne && donnees.length > 0 && (
          <span className="inline-flex items-center gap-1.5">
            <span aria-hidden="true" className="h-0.5 w-3 rounded-sm" style={{ backgroundColor: COULEUR_SERIE[ligne.ton] }} />
            {ligne.nom} (axe de droite) :
            <span className="font-semibold tabular-nums text-fg">{nombre(donnees[donnees.length - 1][ligne.cle])}</span>
          </span>
        )}
      </figcaption>
      <table className="sr-only">
        <caption>{legende}</caption>
        <thead>
          <tr>
            <th scope="col">Période</th>
            {series.map((s) => (
              <th key={s.cle} scope="col">
                {s.nom}
              </th>
            ))}
            {ligne && <th scope="col">{ligne.nom}</th>}
          </tr>
        </thead>
        <tbody>
          {donnees.map((d) => (
            <tr key={String(d[cleX])}>
              <th scope="row">{String(d[cleX])}</th>
              {series.map((s) => (
                <td key={s.cle}>{nombre(d[s.cle])}</td>
              ))}
              {ligne && <td>{nombre(d[ligne.cle])}</td>}
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}
