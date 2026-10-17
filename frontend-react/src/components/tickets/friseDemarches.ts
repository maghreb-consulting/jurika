import type { LigneDemarche, PointAttention, VueDemarches } from '../../types/demarche';

/**
 * Lot L6b (E7, point 8) : etat de delai d'une demarche sur la frise du ticket.
 *
 * Rien n'est calcule ici : les seuils et les echeances sont ceux du SERVEUR.
 * `DemarcheUseCases.pointsAttention` (ticket-service) range une echeance d'une demarche
 * a faire en DEPASSE (jours < 0), CRITIQUE (J-3) ou APPROCHE (J-15) et n'envoie rien
 * au-dela de 15 jours. D'ou :
 *  - rouge  : point d'attention DEPASSE ;
 *  - orange : point d'attention CRITIQUE ou APPROCHE (echeance a 15 jours ou moins) ;
 *  - vert   : demarche a faire dont l'echeance est connue (`echeance`) et sans point
 *             d'attention (plus de 15 jours) ;
 *  - accomplie, ecartee : demarche cochee, ou mise hors perimetre ;
 *  - sans   : a faire, aucune echeance connue (aucune date n'est fabriquee).
 */
export type TonDelai = 'rouge' | 'orange' | 'vert' | 'accomplie' | 'ecartee' | 'sans';

export interface EtatDelai {
  ton: TonDelai;
  /** Libelle court de l'etat, toujours affiche (la couleur ne parle jamais seule). */
  libelle: string;
  /** Precision : date et jours restants, quand le serveur les donne. */
  detail: string | null;
}

/** Seuil "echeance proche" du serveur (DemarcheUseCases.JOURS_APPROCHE), en jours. */
export const JOURS_ECHEANCE_PROCHE = 15;

function jourCivil(iso: string): string {
  const [a, m, j] = iso.slice(0, 10).split('-');
  return a && m && j ? `${j}/${m}/${a}` : iso;
}

function jours(n: number): string {
  return `${n} jour${n > 1 ? 's' : ''}`;
}

export function etatDelai(d: LigneDemarche, point: PointAttention | undefined): EtatDelai {
  if (d.etat === 'COCHEE') {
    return { ton: 'accomplie', libelle: 'Accomplie', detail: d.cocheAt ? `le ${jourCivil(d.cocheAt)}` : null };
  }
  if (d.etat === 'NON_APPLICABLE') return { ton: 'ecartee', libelle: 'Écartée', detail: null };
  if (point) {
    const date = jourCivil(point.echeance);
    if (point.severite === 'DEPASSE') {
      return { ton: 'rouge', libelle: 'Dépassée', detail: `échéance du ${date}, dépassée de ${jours(Math.abs(point.joursRestants))}` };
    }
    return {
      ton: 'orange',
      libelle: 'Échéance proche',
      detail: point.joursRestants === 0 ? `échéance aujourd’hui (${date})` : `le ${date}, dans ${jours(point.joursRestants)}`,
    };
  }
  if (d.echeance) return { ton: 'vert', libelle: 'Dans les temps', detail: `échéance le ${jourCivil(d.echeance)}` };
  return { ton: 'sans', libelle: 'Sans échéance', detail: null };
}

export interface EtapeFrise {
  demarche: LigneDemarche;
  etat: EtatDelai;
}

export interface PhaseFrise {
  code: string;
  libelle: string;
  etapes: EtapeFrise[];
}

/** Les demarches du ticket, par phase et dans l'ordre du guide, avec leur etat de delai. */
export function construireFrise(vue: VueDemarches): PhaseFrise[] {
  const points = new Map(vue.avancement.pointsAttention.map((p) => [p.ordre, p]));
  return vue.phases.map((phase) => ({
    code: phase.code,
    libelle: phase.libelle,
    etapes: [...phase.demarches]
      .sort((a, b) => a.ordre - b.ordre)
      .map((d) => ({ demarche: d, etat: etatDelai(d, points.get(d.ordre)) })),
  }));
}

/** Nombre d'etapes par ton (resume en tete de frise). */
export function compterTons(phases: PhaseFrise[]): Record<TonDelai, number> {
  const n: Record<TonDelai, number> = { rouge: 0, orange: 0, vert: 0, accomplie: 0, ecartee: 0, sans: 0 };
  for (const p of phases) for (const e of p.etapes) n[e.etat.ton] += 1;
  return n;
}
