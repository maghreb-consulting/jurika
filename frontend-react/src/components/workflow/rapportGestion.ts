/**
 * Lot L4d (E11e) : saisie du rapport de gestion de la gerance (modeles du corpus
 * RAPPORT_GESTION_EXERCICE_SARL / _SARL_AU). Les donnees de la societe (siege, objet,
 * capital, parts, gerants) et des comptes (resultat, affectation, dividendes, commissaire,
 * conventions) viennent du dossier et de l'etape 2 : elles ne se ressaisissent pas ici.
 * Aucun texte n'est propose par defaut ; une donnee n'est demandee que si le modele
 * l'imprime (reserve legale en cas de benefice, pertes cumulees en cas de perte...).
 */

export type OuiNon = 'oui' | 'non' | '';

export interface FaitMarquant {
  date: string;
  libelle: string;
}

export interface Indicateur {
  libelle: string;
  montantExercice: string;
  montantExercicePrecedent: string;
}

export interface EvenementPostCloture {
  date: string;
  libelle: string;
  incidence: string;
}

export interface RapportGestionSaisie {
  exerciceDebut: string;
  activiteExpose: string;
  faitsMarquants: FaitMarquant[];
  indicateurs: Indicateur[];
  evolutionCommentaire: string;
  capitauxPropres: string;
  evenementsPostCloture: OuiNon;
  evenementsPostClotureListe: EvenementPostCloture[];
  perspectives: string;
  rechercheDeveloppement: OuiNon;
  rechercheDeveloppementExpose: string;
  reserveLegaleDoteeIntegralement: OuiNon;
  pertesCumulees: string;
  lieuSignature: string;
  date: string;
}

export function rapportGestionVide(): RapportGestionSaisie {
  return {
    exerciceDebut: '',
    activiteExpose: '',
    faitsMarquants: [{ date: '', libelle: '' }],
    indicateurs: [{ libelle: '', montantExercice: '', montantExercicePrecedent: '' }],
    evolutionCommentaire: '',
    capitauxPropres: '',
    evenementsPostCloture: '',
    evenementsPostClotureListe: [{ date: '', libelle: '', incidence: '' }],
    perspectives: '',
    rechercheDeveloppement: '',
    rechercheDeveloppementExpose: '',
    reserveLegaleDoteeIntegralement: '',
    pertesCumulees: '',
    lieuSignature: '',
    date: '',
  };
}

/** Reprend une saisie enregistree (brouillon), completee des champs absents. */
export function rapportGestionDepuis(brut: unknown): RapportGestionSaisie {
  const base = rapportGestionVide();
  if (!brut || typeof brut !== 'object') return base;
  const r = brut as Partial<RapportGestionSaisie>;
  return {
    ...base,
    ...r,
    faitsMarquants: Array.isArray(r.faitsMarquants) && r.faitsMarquants.length ? r.faitsMarquants : base.faitsMarquants,
    indicateurs: Array.isArray(r.indicateurs) && r.indicateurs.length ? r.indicateurs : base.indicateurs,
    evenementsPostClotureListe:
      Array.isArray(r.evenementsPostClotureListe) && r.evenementsPostClotureListe.length
        ? r.evenementsPostClotureListe
        : base.evenementsPostClotureListe,
  };
}

const plein = (s: string | undefined) => !!s && s.trim() !== '';

const faitsSaisis = (r: RapportGestionSaisie) => r.faitsMarquants.filter((f) => plein(f.date) || plein(f.libelle));
const indicateursSaisis = (r: RapportGestionSaisie) =>
  r.indicateurs.filter((i) => plein(i.libelle) || plein(i.montantExercice) || plein(i.montantExercicePrecedent));
const evenementsSaisis = (r: RapportGestionSaisie) =>
  r.evenementsPostClotureListe.filter((e) => plein(e.date) || plein(e.libelle) || plein(e.incidence));

/**
 * Donnees encore a saisir pour generer le rapport, nommees comme a l'ecran. Le rapport
 * est optionnel : cette liste n'empeche pas de valider l'etape, elle dit ce qui manque.
 */
export function rapportGestionManquants(
  r: RapportGestionSaisie,
  contexte: { resultatType: string; dateAgo?: string },
): string[] {
  const m: string[] = [];
  if (!plein(r.exerciceDebut)) m.push('date d’ouverture de l’exercice');
  if (!plein(r.activiteExpose)) m.push('activité de l’exercice');
  const faits = faitsSaisis(r);
  if (faits.length === 0) m.push('au moins un fait marquant');
  else if (faits.some((f) => !plein(f.date) || !plein(f.libelle))) m.push('date et libellé de chaque fait marquant');
  const indicateurs = indicateursSaisis(r);
  if (indicateurs.length === 0) m.push('au moins un indicateur');
  else if (indicateurs.some((i) => !plein(i.libelle) || !plein(i.montantExercice) || !plein(i.montantExercicePrecedent))) {
    m.push('libellé et montants de chaque indicateur');
  }
  if (!plein(r.evolutionCommentaire)) m.push('commentaire de la gérance sur l’évolution');
  if (!plein(r.capitauxPropres)) m.push('capitaux propres à la clôture');
  if (!r.evenementsPostCloture) m.push('événements survenus depuis la clôture (oui ou non)');
  if (r.evenementsPostCloture === 'oui') {
    const ev = evenementsSaisis(r);
    if (ev.length === 0 || ev.some((e) => !plein(e.date) || !plein(e.libelle) || !plein(e.incidence))) {
      m.push('date, nature et incidence de chaque événement postérieur à la clôture');
    }
  }
  if (!plein(r.perspectives)) m.push('perspectives');
  if (!r.rechercheDeveloppement) m.push('recherche et développement (oui ou non)');
  if (r.rechercheDeveloppement === 'oui' && !plein(r.rechercheDeveloppementExpose)) {
    m.push('exposé des travaux de recherche et de développement');
  }
  if (contexte.resultatType === 'bénéfice' && !r.reserveLegaleDoteeIntegralement) {
    m.push('réserve légale au cinquième du capital (oui ou non)');
  }
  if (contexte.resultatType === 'perte' && !plein(r.pertesCumulees)) m.push('pertes cumulées après affectation');
  if (!plein(r.lieuSignature)) m.push('lieu de signature du rapport');
  if (!plein(r.date)) m.push('date du rapport');
  return m;
}

/** Le rapport est etabli et communique AVANT l'assemblee (art. 70 de la loi 5-96). */
export function rapportGestionDateErreur(r: RapportGestionSaisie, dateAgo: string | undefined): string | undefined {
  if (plein(r.date) && plein(dateAgo) && r.date > (dateAgo as string)) {
    return 'Le rapport doit être daté au plus tard du jour de l’assemblée : il est communiqué aux associés avant elle.';
  }
  return undefined;
}

/** Charge utile du rapport : lignes vides retirees, branches non imprimees ecartees. */
export function rapportGestionPayload(r: RapportGestionSaisie, resultatType: string): Record<string, unknown> {
  const t = (s: string) => s.trim();
  return {
    exerciceDebut: r.exerciceDebut,
    activiteExpose: t(r.activiteExpose),
    faitsMarquants: faitsSaisis(r).map((f) => ({ date: f.date, libelle: t(f.libelle) })),
    indicateurs: indicateursSaisis(r).map((i) => ({
      libelle: t(i.libelle),
      montantExercice: t(i.montantExercice),
      montantExercicePrecedent: t(i.montantExercicePrecedent),
    })),
    evolutionCommentaire: t(r.evolutionCommentaire),
    capitauxPropres: t(r.capitauxPropres),
    evenementsPostCloture: r.evenementsPostCloture,
    evenementsPostClotureListe:
      r.evenementsPostCloture === 'oui'
        ? evenementsSaisis(r).map((e) => ({ date: e.date, libelle: t(e.libelle), incidence: t(e.incidence) }))
        : [],
    perspectives: t(r.perspectives),
    rechercheDeveloppement: r.rechercheDeveloppement,
    rechercheDeveloppementExpose: r.rechercheDeveloppement === 'oui' ? t(r.rechercheDeveloppementExpose) : '',
    reserveLegaleDoteeIntegralement: resultatType === 'bénéfice' ? r.reserveLegaleDoteeIntegralement : '',
    pertesCumulees: resultatType === 'perte' ? t(r.pertesCumulees) : '',
    lieuSignature: t(r.lieuSignature),
    date: r.date,
  };
}
