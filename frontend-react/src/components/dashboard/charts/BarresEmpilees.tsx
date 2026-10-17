import type { ReactNode } from 'react';
import { cn } from '../../../lib/utils';
import type { TonSerie } from './apparence';

export interface SerieEmpilee {
  cle: string;
  nom: string;
  ton: TonSerie;
}

export interface LigneEmpilee {
  cle: string;
  libelle: string;
  /** Precision sous le libelle ("2 clotures · 1 annule"). */
  detail?: ReactNode;
  /** Valeur de chaque serie (cle de la serie -> nombre entier). */
  valeurs: Record<string, number>;
}

interface Props {
  lignes: LigneEmpilee[];
  series: SerieEmpilee[];
  /** Nom du graphique pour les lecteurs d'ecran (legende du tableau equivalent). */
  legende: string;
  /** Echelle : 'max' (longueur relative a la plus grande ligne) ou 'part' (chaque ligne = 100 %). */
  echelle?: 'max' | 'part';
}

const REMPLISSAGE: Record<TonSerie, string> = {
  info: 'bg-info',
  succes: 'bg-success',
  accent: 'bg-accent',
  attention: 'bg-warning',
  danger: 'bg-danger',
  neutre: 'bg-fg-subtle',
};

const PASTILLE = REMPLISSAGE;

/**
 * Lot L6b (E18) : barres horizontales EMPILEES, une ligne par element (employe, type
 * d'echange), un segment par serie (etape, etat). Le total de chaque ligne est ecrit ; la
 * legende porte le total de chaque serie ; chaque segment donne sa valeur au survol. Rendu HTML
 * (clair et sombre, toutes largeurs) et tableau equivalent pour les lecteurs d'ecran.
 */
export function BarresEmpilees({ lignes, series, legende, echelle = 'max' }: Props) {
  const totalLigne = (l: LigneEmpilee) => series.reduce((n, s) => n + (l.valeurs[s.cle] ?? 0), 0);
  const max = Math.max(1, ...lignes.map(totalLigne));
  const totauxSeries = series.map((s) => lignes.reduce((n, l) => n + (l.valeurs[s.cle] ?? 0), 0));

  return (
    <figure className="m-0">
      <ul className="space-y-3" aria-hidden="true">
        {lignes.map((l) => {
          const total = totalLigne(l);
          const base = echelle === 'part' ? Math.max(1, total) : max;
          return (
            <li key={l.cle}>
              <div className="mb-1 flex items-baseline justify-between gap-3 text-sm">
                <span className="min-w-0">
                  <span className="block truncate font-medium text-fg" title={l.libelle}>
                    {l.libelle}
                  </span>
                  {l.detail && <span className="block text-xs text-fg-subtle">{l.detail}</span>}
                </span>
                <span className="shrink-0 font-semibold tabular-nums text-heading">{total}</span>
              </div>
              <div className="flex h-3 overflow-hidden rounded-full bg-bg-overlay">
                {series.map((s) => {
                  const v = l.valeurs[s.cle] ?? 0;
                  if (v <= 0) return null;
                  return (
                    <span
                      key={s.cle}
                      title={`${s.nom} : ${v}`}
                      className={cn('h-full border-r-2 border-bg-raised last:border-r-0', REMPLISSAGE[s.ton])}
                      style={{ width: `${(v / base) * 100}%` }}
                    />
                  );
                })}
              </div>
            </li>
          );
        })}
      </ul>
      <figcaption className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-fg-muted">
        {series.map((s, i) => (
          <span key={s.cle} className="inline-flex items-center gap-1.5">
            <span aria-hidden="true" className={cn('h-2.5 w-2.5 rounded-sm', PASTILLE[s.ton])} />
            {s.nom}
            <span className="font-semibold tabular-nums text-fg">{totauxSeries[i]}</span>
          </span>
        ))}
      </figcaption>
      <table className="sr-only">
        <caption>{legende}</caption>
        <thead>
          <tr>
            <th scope="col">Élément</th>
            {series.map((s) => (
              <th key={s.cle} scope="col">
                {s.nom}
              </th>
            ))}
            <th scope="col">Total</th>
          </tr>
        </thead>
        <tbody>
          {lignes.map((l) => (
            <tr key={l.cle}>
              <th scope="row">{l.libelle}</th>
              {series.map((s) => (
                <td key={s.cle}>{l.valeurs[s.cle] ?? 0}</td>
              ))}
              <td>{totalLigne(l)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}
