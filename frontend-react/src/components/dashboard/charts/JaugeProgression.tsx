import { cn } from '../../../lib/utils';

interface Props {
  /** Etapes franchies ou en cours (1..total). */
  valeur: number;
  total: number;
  /** Phrase lue par les lecteurs d'ecran ("Etape 2 sur 4 : preparation des actes"). */
  libelle: string;
  taille?: number;
  className?: string;
}

/**
 * Lot L6b (E18) : jauge circulaire d'avancement (etape n sur N). L'arc suit le jeton d'accent,
 * la piste celui des fonds : lisible en clair et en sombre. Le chiffre est ecrit au centre.
 */
export function JaugeProgression({ valeur, total, libelle, taille = 64, className }: Props) {
  const rayon = 26;
  const circonference = 2 * Math.PI * rayon;
  const part = total > 0 ? Math.min(1, Math.max(0, valeur / total)) : 0;
  return (
    <div className={cn('relative shrink-0', className)} style={{ width: taille, height: taille }} role="img" aria-label={libelle}>
      <svg viewBox="0 0 64 64" width={taille} height={taille} className="-rotate-90">
        <circle cx="32" cy="32" r={rayon} fill="none" strokeWidth="7" className="stroke-bg-overlay" />
        <circle
          cx="32"
          cy="32"
          r={rayon}
          fill="none"
          strokeWidth="7"
          strokeLinecap="round"
          className="stroke-accent"
          strokeDasharray={`${circonference * part} ${circonference}`}
        />
      </svg>
      <span className="absolute inset-0 flex items-center justify-center text-sm font-semibold tabular-nums text-heading" aria-hidden="true">
        {valeur}/{total}
      </span>
    </div>
  );
}
