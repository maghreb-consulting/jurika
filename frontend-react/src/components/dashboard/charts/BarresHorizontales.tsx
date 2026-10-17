import { Link } from 'react-router-dom';
import { cn } from '../../../lib/utils';
import type { TonSerie } from './apparence';

export interface Barre {
  cle: string;
  /** Libelle complet en francais (jamais un code). */
  libelle: string;
  valeur: number;
  ton?: TonSerie;
  /** Destination du libelle (liste, fiche...). */
  lien?: string;
}

interface Props {
  barres: Barre[];
  /** Nom de la liste pour les lecteurs d'ecran. */
  legende: string;
  ton?: TonSerie;
  /** Ecrit la part de chaque barre dans le total ("40 %"). */
  part?: boolean;
  /** Libelle d'une ligne de total sous les barres ; absent : pas de total. */
  total?: string;
  /** Lot L6b (E18) : ecriture de la valeur (ex. "2,5 Go") ; par defaut le nombre tel quel. */
  format?: (valeur: number) => string;
}

const REMPLISSAGE: Record<TonSerie, string> = {
  info: 'bg-info',
  succes: 'bg-success',
  accent: 'bg-accent',
  attention: 'bg-warning',
  danger: 'bg-danger',
  neutre: 'bg-fg-subtle',
};

/**
 * Lot L6b (E17) : barres horizontales etiquetees, a la place des anneaux sans chiffres. Chaque
 * ligne porte son libelle complet, sa valeur et (au choix) sa part du total ; la longueur est
 * relative a la plus grande valeur. Rendu HTML (pas de SVG) : lisible en clair et en sombre,
 * a toutes les largeurs, et lu tel quel par un lecteur d'ecran. L'appelant gere l'etat vide.
 */
export function BarresHorizontales({ barres, legende, ton = 'info', part = false, total, format = String }: Props) {
  const max = Math.max(0, ...barres.map((b) => b.valeur));
  const somme = barres.reduce((n, b) => n + b.valeur, 0);
  return (
    <div>
      <ul aria-label={legende} className="space-y-3">
        {barres.map((b) => {
          const largeur = max > 0 ? Math.max(b.valeur > 0 ? 2 : 0, (b.valeur / max) * 100) : 0;
          return (
            <li key={b.cle}>
              <div className="mb-1 flex items-baseline justify-between gap-3 text-sm">
                {b.lien ? (
                  <Link to={b.lien} className="min-w-0 truncate rounded-sm text-fg hover:underline" title={b.libelle}>
                    {b.libelle}
                  </Link>
                ) : (
                  <span className="min-w-0 truncate text-fg" title={b.libelle}>
                    {b.libelle}
                  </span>
                )}
                <span className="shrink-0 tabular-nums">
                  <span className="font-semibold text-heading">{format(b.valeur)}</span>
                  {part && somme > 0 && <span className="ml-1.5 text-xs text-fg-subtle">({Math.round((b.valeur / somme) * 100)} %)</span>}
                </span>
              </div>
              <div className="h-2 overflow-hidden rounded-full bg-bg-overlay" aria-hidden="true">
                <div className={cn('h-full rounded-full', REMPLISSAGE[b.ton ?? ton])} style={{ width: `${largeur}%` }} />
              </div>
            </li>
          );
        })}
      </ul>
      {total && (
        <p className="mt-4 flex items-baseline justify-between border-t border-border pt-3 text-sm text-fg-muted">
          <span>{total}</span>
          <span className="font-semibold tabular-nums text-heading">{format(somme)}</span>
        </p>
      )}
    </div>
  );
}
