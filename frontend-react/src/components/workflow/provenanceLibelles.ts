import type { VariableDuMagasin } from '../../services/workflow.service';

/** Lot L3 (RG-VAR-02) : libelle de la provenance d'une donnee, et ou la corriger (RG-VAR-05). */
export function libelleProvenance(v: Pick<VariableDuMagasin, 'origine' | 'occasion'>): string {
  switch (v.origine) {
    case 'FICHE':
      return 'Reprise de la fiche de la société';
    case 'EXTRAITE':
      return 'Lue sur une pièce, confirmée';
    case 'DERIVEE':
      return 'Calculée';
    case 'BASE':
      return 'Reprise du dossier';
    default: {
      const etape = /^etape-(\d+)$/.exec(v.occasion ?? '');
      return etape ? `Saisie à l’étape ${etape[1]}` : 'Saisie';
    }
  }
}

export function ouCorriger(v: Pick<VariableDuMagasin, 'origine' | 'occasion'>): string {
  if (v.origine === 'FICHE') return 'Corrigez-la dans « Identifiants de la société » (Data Room).';
  const etape = /^etape-(\d+)$/.exec(v.occasion ?? '');
  return etape ? `Corrigez-la à l’étape ${etape[1]}.` : 'Corrigez-la là où elle a été saisie.';
}
