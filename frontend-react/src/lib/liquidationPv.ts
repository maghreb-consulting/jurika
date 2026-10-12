import type { LiquidateurState } from '../components/workflow/LiquidateurPicker';

/**
 * Lot L4d (E11d) : donnees des proces-verbaux du corpus de la dissolution et de la
 * liquidation (PV_DISSOLUTION_ANTICIPEE_*, PV_CLOTURE_LIQUIDATION_*) qui ne viennent ni de la
 * fiche de la societe ni de la seance : precisions du motif, mandat du liquidateur, mentions
 * de l'acte et decisions de cloture. Rien n'est suppose : une donnee absente est nommee a
 * l'ecran ou a la generation.
 */

/** Precisions de la dissolution et mandat du liquidateur, saisis a l'etape 1. */
export interface DetailPvDissolution {
  objetEvenement?: string;
  situationNetteMontant?: string;
  approbationComptesDate?: string;
  art86MotifsEcartement?: string;
  clauseTexte?: string;
  dureeMandat?: string;
  redditionPeriodicite?: string;
  /** Un pouvoir par ligne (facultatif). */
  pouvoirsComplementaires?: string;
  pouvoirsLimitation?: string;
}

const vide = (s: string | undefined) => !(s ?? '').trim();

/** Ce qui manque a l'etape 1 pour rediger le PV de dissolution (messages nommes, par champ). */
export function manquesDetailPv(nature: string, d: DetailPvDissolution): Record<string, string> {
  const e: Record<string, string> = {};
  if ((nature === 'realisation_objet' || nature === 'extinction_objet') && vide(d.objetEvenement)) {
    e.objetEvenement = "Décrivez l'événement par lequel l'objet social a été réalisé ou s'est éteint.";
  }
  if (nature === 'situation_nette_art86') {
    if (vide(d.situationNetteMontant)) e.situationNetteMontant = 'Indiquez le montant de la situation nette.';
    if (vide(d.approbationComptesDate)) e.approbationComptesDate = "Indiquez la date d'approbation des comptes de l'exercice.";
    if (vide(d.art86MotifsEcartement)) {
      e.art86MotifsEcartement = 'Indiquez pourquoi la reconstitution des capitaux propres et la réduction du capital sont écartées.';
    }
  }
  if (nature === 'cause_statutaire' && vide(d.clauseTexte)) {
    e.clauseTexte = 'Décrivez les circonstances de la survenance de la cause statutaire.';
  }
  if (vide(d.dureeMandat)) e.dureeMandat = 'Indiquez la durée du mandat du liquidateur.';
  if (vide(d.redditionPeriodicite)) e.redditionPeriodicite = 'Indiquez la périodicité des comptes rendus du liquidateur.';
  return e;
}

/** Une ligne par element, lignes vides retirees. */
export function lignes(texte: string | undefined): string[] {
  return (texte ?? '').split('\n').map((l) => l.trim()).filter(Boolean);
}

/** Precisions du motif, transmises dans le bloc `dissolution`. */
export function dissolutionPv(d: DetailPvDissolution | undefined): Record<string, string> {
  if (!d) return {};
  return {
    objetEvenement: (d.objetEvenement ?? '').trim(),
    situationNetteMontant: (d.situationNetteMontant ?? '').trim(),
    approbationComptesDate: d.approbationComptesDate ?? '',
    art86MotifsEcartement: (d.art86MotifsEcartement ?? '').trim(),
    clauseTexte: (d.clauseTexte ?? '').trim(),
  };
}

/** Mandat du liquidateur, transmis dans le bloc `liquidateur` (conserve avec lui en base). */
export function mandatPv(d: DetailPvDissolution | undefined): Record<string, unknown> {
  if (!d) return {};
  return {
    dureeMandat: (d.dureeMandat ?? '').trim(),
    redditionPeriodicite: (d.redditionPeriodicite ?? '').trim(),
    pouvoirsComplementaires: lignes(d.pouvoirsComplementaires),
    pouvoirsLimitation: (d.pouvoirsLimitation ?? '').trim(),
  };
}

/**
 * Identite complementaire et remuneration du liquidateur (personne physique : nationalite,
 * date de naissance ; ancien gerant ; remuneration), transmises dans le bloc `liquidateur`.
 */
export function champsMandatLiquidateur(l: LiquidateurState): Record<string, string> {
  const out: Record<string, string> = {
    remunerationApplicable: l.remunerationApplicable ?? '',
    remunerationMontant: l.remunerationApplicable === 'oui' ? (l.remunerationMontant ?? '').trim() : '',
    remunerationModalites: l.remunerationApplicable === 'oui' ? (l.remunerationModalites ?? '').trim() : '',
  };
  if (l.forme !== 'morale') {
    out.nationalite = (l.nationalite ?? '').trim();
    out.dateNaissance = l.dateNaissance ?? '';
    out.ancienGerant = estGerantChoisi(l) ? 'oui' : (l.ancienGerant ?? '');
  }
  if (l.partieKey) out.partieKey = l.partieKey;
  return out;
}

/** Le liquidateur a ete choisi parmi les gerants du dossier. */
export function estGerantChoisi(l: LiquidateurState): boolean {
  return l.source === 'BD' && (l.partieKey ?? '').startsWith('gerant:');
}

/** Ce qui manque au liquidateur pour le PV de dissolution (message nomme), ou null. */
export function manqueMandatLiquidateur(l: LiquidateurState): string | null {
  if (l.forme !== 'morale') {
    if (vide(l.nationalite)) return 'Indiquez la nationalité du liquidateur : le procès-verbal la mentionne.';
    if (vide(l.dateNaissance)) return 'Indiquez la date de naissance du liquidateur : le procès-verbal la mentionne.';
    if (!estGerantChoisi(l) && !l.ancienGerant) return 'Indiquez si le liquidateur a été gérant de la société.';
  }
  if (!l.remunerationApplicable) return 'Indiquez si le liquidateur est rémunéré.';
  if (l.remunerationApplicable === 'oui') {
    if (vide(l.remunerationMontant)) return 'Indiquez le montant de la rémunération du liquidateur.';
    if (vide(l.remunerationModalites)) return 'Indiquez les modalités de versement de la rémunération.';
  }
  return null;
}

/** Mentions et decisions saisies a l'etape de generation (une valeur par cle). */
export type MentionsPv = Record<string, string>;

/** Cles transmises dans `mentionsPv` (mentions de l'acte, commissaire, dissolution). */
export const CLES_MENTIONS = [
  'dossierReference', 'registreFolio', 'enregistrementLieu', 'annonceJournal', 'quitusGerance',
  'salariesDixOuPlus', 'salariesNombre', 'cacExiste', 'cacNom', 'cacPresent', 'cacMandatLiquidation',
  'cacFinMandatDateEffet',
] as const;

/** Cles transmises dans `cloture` (PV de cloture). */
export const CLES_CLOTURE = [
  'annonceJournal', 'annoncePublicationDate', 'dissolutionRcInscriptionDate', 'convocationAuteur',
  'operationsResume', 'frais', 'isMontant', 'comptesBancairesClotures', 'contestations',
  'compteDefinitifApprobation', 'refusMotifs', 'quitusReserveTexte', 'quitusRefusMotifs',
  'quitusRefusMandataireNom', 'retenueApplicable', 'retenueAssiette', 'retenueTaux', 'retenueMontant',
  'retenueExonerationInvoquee', 'retenueExonerationBeneficiaire', 'retenueNonApplicableMotif',
  'repartitionModalites', 'repartitionDateLimite', 'maliRepartition', 'conservationDepositaireNom',
  'conservationDepositaireQualite', 'conservationLieu', 'conservationDuree', 'passifEventuelApplicable',
  'passifEventuelNature', 'passifEventuelMontant', 'passifEventuelModalites', 'passifEventuelSequestreNom',
  'passifEventuelDuree', 'passifEventuelChargePersonne',
] as const;

function choisir(m: MentionsPv, cles: readonly string[]): Record<string, string> {
  const out: Record<string, string> = {};
  for (const k of cles) {
    const v = (m[k] ?? '').trim();
    if (v) out[k] = v;
  }
  return out;
}

/** Bloc `mentionsPv` de la charge utile. */
export function mentionsPayload(m: MentionsPv): Record<string, string> {
  return choisir(m, CLES_MENTIONS);
}

/** Donnees de cloture de la charge utile (fusionnees dans le bloc `cloture`). */
export function cloturePayload(m: MentionsPv): Record<string, unknown> {
  const out: Record<string, unknown> = choisir(m, CLES_CLOTURE);
  if (m.convocationAuteur === 'mandataire') {
    out.ordonnance = {
      associeDemandeur: (m.ordonnanceAssocieDemandeur ?? '').trim(),
      tribunal: (m.ordonnanceTribunal ?? '').trim(),
      numero: (m.ordonnanceNumero ?? '').trim(),
      date: m.ordonnanceDate ?? '',
      mandataireNom: (m.ordonnanceMandataireNom ?? '').trim(),
    };
  }
  const pieces = lignes(m.piecesRadiation);
  if (pieces.length) out.piecesRadiation = pieces;
  return out;
}
