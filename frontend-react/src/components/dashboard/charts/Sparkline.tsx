import { COULEUR_SERIE, type TonSerie } from './apparence';

interface Props {
  /** Valeurs dans l'ordre du temps (au moins deux). */
  valeurs: number[];
  /** Phrase lue par les lecteurs d'ecran ("Cabinets : de 2 a 4 sur douze mois"). */
  libelle: string;
  ton?: TonSerie;
  largeur?: number;
  hauteur?: number;
}

/**
 * Lot L6b (E18) : petite courbe de tendance d'un indicateur (trace lineaire en escalier, aucun
 * lissage : chaque point est une valeur rendue par le serveur). Rien n'est dessine avec moins de
 * deux points.
 */
export function Sparkline({ valeurs, libelle, ton = 'info', largeur = 96, hauteur = 28 }: Props) {
  if (valeurs.length < 2) return null;
  const min = Math.min(...valeurs);
  const max = Math.max(...valeurs);
  const etendue = max - min || 1;
  const pas = largeur / (valeurs.length - 1);
  const y = (v: number) => hauteur - 2 - ((v - min) / etendue) * (hauteur - 4);
  // Escalier : la valeur tient jusqu'au point suivant (comptages discrets).
  let d = `M0 ${y(valeurs[0])}`;
  for (let i = 1; i < valeurs.length; i++) d += ` H${i * pas} V${y(valeurs[i])}`;
  const aire = `${d} V${hauteur} H0 Z`;
  return (
    <svg width={largeur} height={hauteur} viewBox={`0 0 ${largeur} ${hauteur}`} role="img" aria-label={libelle} className="overflow-visible">
      <path d={aire} fill={COULEUR_SERIE[ton]} opacity={0.12} />
      <path d={d} fill="none" stroke={COULEUR_SERIE[ton]} strokeWidth={1.75} strokeLinejoin="round" />
      <circle cx={largeur} cy={y(valeurs[valeurs.length - 1])} r={2.5} fill={COULEUR_SERIE[ton]} />
    </svg>
  );
}
