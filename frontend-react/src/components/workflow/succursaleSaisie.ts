/**
 * Saisie de la succursale (lot L6) : etat, controles et charge utile, separes du formulaire
 * (SuccursaleSaisieForm.tsx) pour que ce dernier n'exporte que des composants
 * (rafraichissement a chaud de Vite). Voir l'en-tete de SuccursaleSaisieForm.tsx.
 */

export type ResponsableSource = 'BD' | 'EXTERNE';
export type ResponsableTypePersonne = 'PHYSIQUE' | 'MORALE';

export interface SuccursaleResponsableState {
  present: boolean;
  source: ResponsableSource;
  /** Clé de l'option BD retenue ('' si externe). */
  partieKey: string;
  typePersonne: ResponsableTypePersonne;
  civilite: string;
  prenom: string;
  nom: string;
  /** Personne morale : dénomination + représentant légal. */
  denomination: string;
  representantNom: string;
  nationalite: string;
  adresse: string;
  pieceType: string;
  pieceNumero: string;
  pouvoirs: string;
}

export interface SuccursaleSaisieState {
  enseigne: string;
  adresse: string;
  ville: string;
  villeGreffe: string;
  activite: string;
  dateOuverture: string;
  dotationPresente: boolean;
  dotationMontant: string;
  responsable: SuccursaleResponsableState;
}

export function emptySuccursaleSaisie(): SuccursaleSaisieState {
  return {
    enseigne: '',
    adresse: '',
    ville: '',
    villeGreffe: '',
    activite: '',
    dateOuverture: '',
    dotationPresente: false,
    dotationMontant: '',
    responsable: {
      present: false,
      source: 'BD',
      partieKey: '',
      typePersonne: 'PHYSIQUE',
      civilite: 'M.',
      prenom: '',
      nom: '',
      denomination: '',
      representantNom: '',
      nationalite: '', // L4a (E2) : proposee a l'ecran
      adresse: '',
      pieceType: '', // L3 (D6) : "CIN" propose a l'ecran, puis confirme
      pieceNumero: '',
      pouvoirs: '',
    },
  };
}

export type SuccursaleSaisieErrors = Partial<
  Record<
    'enseigne' | 'adresse' | 'ville' | 'activite' | 'dateOuverture' | 'villeGreffe'
      | 'dotation' | 'responsable',
    string
  >
>;

/**
 * Miroir EXACT des règles du backend (`SuccursaleMaWorkflow.stepSuccursale`) : ce qui
 * est refusé ici est refusé là-bas, et réciproquement.
 */
export function succursaleSaisieErrors(s: SuccursaleSaisieState): SuccursaleSaisieErrors {
  const e: SuccursaleSaisieErrors = {};
  if (!s.enseigne.trim()) e.enseigne = 'L’enseigne de la succursale est obligatoire.';
  if (!s.adresse.trim()) e.adresse = 'L’adresse de la succursale est obligatoire.';
  if (!s.ville.trim()) e.ville = 'La ville de la succursale est obligatoire.';
  if (!s.activite.trim()) e.activite = 'L’activité de la succursale est obligatoire.';
  if (!s.dateOuverture) e.dateOuverture = 'La date d’ouverture est obligatoire.';
  if (s.dotationPresente) {
    const n = Number(String(s.dotationMontant).replace(/[\s\u00a0\u202f_]/g, ''));
    if (!Number.isFinite(n) || n <= 0) {
      e.dotation = 'Dotation annoncée : précisez son montant (en dirhams).';
    }
  }
  if (s.responsable.present) {
    const r = s.responsable;
    const identifie = r.typePersonne === 'MORALE' ? r.denomination.trim() : r.nom.trim();
    if (!identifie) {
      e.responsable =
        'Désignez le responsable : choisissez un gérant ou un associé existant, ou saisissez un responsable externe.';
    } else if (!r.adresse.trim()) {
      e.responsable = 'L’adresse du responsable de la succursale est obligatoire.';
    } else if (!r.pieceNumero.trim()) {
      e.responsable =
        'Le numéro de pièce d’identité du responsable est obligatoire (importez la CIN recto-verso : elle est lue automatiquement).';
    } else if (!r.pouvoirs.trim()) {
      e.responsable =
        'Précisez les pouvoirs conférés au responsable : ils sont publiés dans l’annonce légale.';
    }
  }
  return e;
}

/** Bloc `succursale` envoyé au backend / aux mappers (contrat `SuccursaleVarsBuilder`). */
export function toSuccursalePayload(s: SuccursaleSaisieState): Record<string, unknown> {
  const r = s.responsable;
  return {
    enseigne: s.enseigne.trim(),
    adresse: s.adresse.trim(),
    ville: s.ville.trim(),
    villeGreffe: (s.villeGreffe || s.ville).trim(),
    activite: s.activite.trim(),
    dateOuverture: s.dateOuverture,
    dotationPresente: s.dotationPresente,
    dotationMontant: s.dotationPresente ? s.dotationMontant.trim() : '',
    responsablePresent: r.present,
    responsable: r.present
      ? {
          source: r.source,
          typePersonne: r.typePersonne,
          civilite: r.civilite,
          prenom: r.prenom.trim(),
          // Personne morale : le « nom » publié est la dénomination.
          nom: (r.typePersonne === 'MORALE' ? r.denomination : r.nom).trim(),
          denomination: r.denomination.trim(),
          representantNom: r.representantNom.trim(),
          nationalite: r.nationalite.trim(),
          adresse: r.adresse.trim(),
          pieceType: r.pieceType.trim(),
          pieceNumero: r.pieceNumero.trim(),
          pouvoirs: r.pouvoirs.trim(),
        }
      : {},
  };
}
