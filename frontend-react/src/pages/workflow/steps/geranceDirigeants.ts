/**
 * Etape 5 de la creation (lot L6) : le dirigeant et l'agregation des mandats de la
 * gerance, separes du rendu (Step5Dirigeants.tsx) pour que ce dernier n'exporte que des
 * composants (rafraichissement a chaud de Vite).
 */

/**
 * EX5 2026-06-09 — Un dirigeant peut etre une personne PHYSIQUE (champs CIN)
 * ou MORALE (entite avec RC/ICE/IF + representant legal physique).
 */
export type TypePersonne = 'PHYSIQUE' | 'MORALE';

export interface Dirigeant {
  id: string;
  /** Lot L4a (E3) : CIN, passeport ou carte de sejour ; absent = CIN (numero dans `cinNumero`). */
  pieceType?: string;
  /** Lot L3 : champs remplis par extraction d'une piece et confirmes (provenance EXTRAITE). */
  _extraits?: string[];
  typePersonne: TypePersonne;
  // ---- PHYSIQUE ----
  civilite: 'M' | 'Mme';
  nom: string;
  prenom: string;
  cinNumero: string;
  nationalite: string;
  dateNaissance: string;
  /** 2026-06-11 — Lieu de naissance pour les Statuts (placeholder gerant_lieu_naissance). */
  lieuNaissance?: string;
  /** 2026-06-11 — CIN/passeport valable jusqu'au (placeholder gerant_piece_validite). */
  pieceValidite?: string;
  adresse: string;
  // ---- MORALE (EX5 2026-06-09) ----
  /** Nom de l'entite morale (raison sociale). */
  denomination?: string;
  formeJuridiqueEntite?: string;
  rc?: string;
  ice?: string;
  ifFiscal?: string;
  siege?: string;
  /** 2026-06-11 — Capital de l'entite (MAD) + date de deliberation pour Statuts. */
  capitalEntite?: number;
  deliberationDate?: string;
  /** Representant legal de l'entite morale (toujours une personne PHYSIQUE).
   *  2026-06-11 — Set de champs complet (idem dirigeant physique) :
   *  civilite/nom/prenom/CIN/adresse/dateNaissance/lieuNaissance/nationalite/
   *  pieceValidite (CIN valable jusqu'au) + qualite (Gerant/President/etc.). */
  repCivilite?: 'M' | 'Mme';
  repNom?: string;
  repPrenom?: string;
  repCin?: string;
  repAdresse?: string;
  repDateNaissance?: string;
  repLieuNaissance?: string;
  repNationalite?: string;
  repPieceValidite?: string;
  repQualite?: string;
  /** Justificatifs MORALE : RC + statuts de l'entite. */
  rcUploaded?: boolean;
  rcFileName?: string;
  statutsEntiteUploaded?: boolean;
  statutsEntiteFileName?: string;
  /** F2 2026-06-09 — Upload obligatoire de la CIN du representant legal (MORALE). */
  repCinUploaded?: boolean;
  repCinFileName?: string;
  // ---- commun ----
  fonction: 'GERANT' | 'CO_GERANT';
  isStatutaire: boolean;
  /**
   * RG-C15 — Le dirigeant est-il aussi associe ? Pour SARL_AU, un seul
   * dirigeant peut avoir cette case cochee. La valeur est propagee
   * automatiquement a l'etape Associes via stepData.step5.dirigeants.
   */
  isAssociate?: boolean;
  cinUploaded: boolean;
  cinFileName?: string;
  extracting: boolean;
  /**
   * 2026-08-18 — MANDAT ET POUVOIRS, PAR DIRIGEANT.
   *
   * Ces quatre données vivaient dans DEUX sections globales à la fin de l'étape :
   * « Gouvernance de la gerance » (durée + rémunération) et « Designation &
   * pouvoirs de la gerance » (mode de désignation + durée + limitation). Deux
   * conséquences :
   *  - la durée du mandat était saisie DEUX FOIS, sous deux formes différentes
   *    (choix structuré d'un côté, texte libre « 99 années » de l'autre) — et
   *    c'est le texte libre qui l'emportait vers les actes, si bien qu'un mandat
   *    déclaré « illimité » ressortait « 99 années » ;
   *  - une SARL peut nommer plusieurs gérants aux mandats distincts (durées,
   *    rémunérations et limitations de pouvoirs différentes), ce qu'une saisie
   *    unique rendait impossible à exprimer.
   *
   * Le mode de désignation n'est pas saisi : il découle de `isStatutaire`, coché
   * en tête de la fiche du dirigeant.
   *
   * La LIMITATION DES POUVOIRS, en revanche, n'est PAS ici : c'est une clause
   * statutaire portant sur la gérance comme ORGANE (« les actes suivants requièrent
   * l'accord préalable des associés : … »), et non un attribut de personne. Elle se
   * saisit une seule fois, sous la liste des dirigeants.
   */
  dureeMandatType: 'illimitee' | 'determinee';
  dureeAnnees: number;
  remunerationMode: '' | 'non_remunere' | 'decision_collective' | 'montant_fixe';
  remunerationMontant: number;
}

/** Mandat agrégé de la gérance, tel qu'il alimente les variables racine des modèles. */
export interface GeranceAgregee {
  dureeGerance: string;
  remunerationMode: '' | 'non_remunere' | 'decision_collective' | 'montant_fixe';
  remunerationMontant: number;
  /**
   * Lot L6b (E13) : phrase de la rémunération, prête pour les actes (gerance.remunerationMode).
   * Mode commun : son libellé ; modes différents : énumération nominative, jamais vide
   * (une chaîne vide faisait refuser l'étape par le serveur, en 400).
   */
  remunerationPhrase: string;
}

/**
 * Agrège en UNE valeur les mandats saisis dirigeant par dirigeant (2026-08-18).
 *
 * Les modèles directeur portent `$DUREE_GERANCE` et `$LIMITATION_POUVOIRS` À LA
 * RACINE, hors de la boucle `GERANTS` : les statuts écrivent « sont nommés premiers
 * gérants […] pour une durée de $DUREE_GERANCE » puis énumèrent les gérants. Un
 * seul mandat peut donc y être imprimé, alors que la saisie en autorise désormais
 * un par dirigeant — et les .docx sont intouchables.
 *
 * L'agrégation ne devine donc jamais :
 *  - mandats IDENTIQUES  -> la valeur commune ; le rendu est inchangé, ce qui est
 *    le cas courant (une gérance aux règles uniformes) ;
 *  - mandats DIFFÉRENTS  -> une énumération NOMINATIVE (« 3 année(s) pour
 *    M. Ahmed ALAOUI et illimitée (jusqu'à révocation) pour Mme Salma BENJELLOUN »),
 *    qui se lit correctement après « pour une durée de » et reste vraie.
 *
 * Retenir le mandat du premier gérant aurait été plus simple — et faux dès que
 * deux gérants diffèrent, sans que rien ne le signale.
 */
export function agregerGerance(dirigeants: Dirigeant[]): GeranceAgregee {
  // Les fiches encore vierges (aucun nom saisi) ne doivent pas peser sur
  // l'agrégat : elles rendraient « différents » des mandats en réalité identiques.
  const nommes = dirigeants.filter(
    (d) => d.nom.trim() || d.prenom.trim() || (d.denomination ?? '').trim(),
  );
  const source = nommes.length > 0 ? nommes : dirigeants;

  const commun = <T,>(lire: (d: Dirigeant) => T): T | null => {
    if (source.length === 0) return null;
    const premiere = lire(source[0]);
    return source.every((d) => lire(d) === premiere) ? premiere : null;
  };

  const enumerer = (liste: Dirigeant[], lire: (d: Dirigeant) => string): string => {
    const parts = liste
      .map((d, i) => {
        const v = lire(d).trim();
        return v ? `${v} pour ${dirigeantLabel(d, i)}` : '';
      })
      .filter(Boolean);
    if (parts.length === 0) return '';
    if (parts.length === 1) return parts[0];
    return `${parts.slice(0, -1).join(', ')} et ${parts[parts.length - 1]}`;
  };

  const dureeCommune = commun((d) => dureeMandatLabel(d));
  const modeCommun = commun((d) => d.remunerationMode);

  return {
    dureeGerance: dureeCommune ?? enumerer(source, (d) => dureeMandatLabel(d)),
    remunerationMode: modeCommun ?? '',
    remunerationMontant: commun((d) => d.remunerationMontant) ?? 0,
    remunerationPhrase: modeCommun !== null
      ? REMUNERATION_LABELS[modeCommun] ?? ''
      : enumerer(source, (d) => REMUNERATION_LABELS[d.remunerationMode] ?? ''),
  };
}

/* ------------------------------------------------------------------ *
 * Gérance — mandat et pouvoirs, PAR DIRIGEANT (2026-08-18)
 * ------------------------------------------------------------------ */

/** Libellés des modes de rémunération, partagés par la saisie et l'agrégation. */
export const REMUNERATION_LABELS: Record<string, string> = {
  non_remunere: 'non rémunéré',
  decision_collective: 'fixée par décision collective des associés',
  montant_fixe: 'montant fixe',
};

/** Durée du mandat d'un dirigeant, en toutes lettres, prête pour les actes. */
export function dureeMandatLabel(d: {
  dureeMandatType?: string;
  dureeAnnees?: number;
}): string {
  if (d.dureeMandatType === 'determinee' && Number(d.dureeAnnees) > 0) {
    return `${d.dureeAnnees} année(s)`;
  }
  return "illimitée (jusqu'à révocation)";
}

/** Nom lisible d'un dirigeant, pour attribuer un mandat dans un acte. */
export function dirigeantLabel(
  d: {
    typePersonne?: string;
    civilite?: string;
    prenom?: string;
    nom?: string;
    denomination?: string;
  },
  index: number,
): string {
  if (d.typePersonne === 'MORALE') {
    return (d.denomination ?? '').trim() || `Gérant ${index + 1}`;
  }
  const civ = d.civilite === 'Mme' ? 'Mme' : 'M.';
  const nom = [d.prenom, d.nom].filter(Boolean).join(' ').trim();
  return nom ? `${civ} ${nom}` : `Gérant ${index + 1}`;
}

/**
 * RG-C15 (SARL AU) : un seul dirigeant peut etre coche « Egalement associe ». Le dirigeant
 * `garder` (celui que l'on vient de cocher) l'emporte ; a defaut, le dernier coche de la
 * liste (regle d'avant le lot L6, pour une liste rechargee).
 */
export function unSeulAssocie(arr: Dirigeant[], garder?: string): Dirigeant[] {
  const associes = arr.filter((d) => d.isAssociate);
  if (associes.length <= 1) return arr;
  const garde = garder && associes.some((d) => d.id === garder)
    ? garder
    : associes[associes.length - 1].id;
  return arr.map((d) => (d.isAssociate && d.id !== garde ? { ...d, isAssociate: false } : d));
}
