/**
 * Catalogue des types de résolutions du moteur PV directeur + éditeur de champs
 * réutilisable (Phase 2, 2026-08-10).
 *
 * Extrait de {@link ModificationOperationForm} pour être partagé entre :
 *  - l'étape 2 (saisie des nouvelles valeurs, seedée depuis la sélection) ;
 *  - l'étape 3 ({@link ModificationOperationForm}, séance + génération).
 *
 * Aligné 1:1 sur {@code ModificationDirecteurMapper.fillTypeVars} (33 types).
 */
import { useEffect } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { TextField } from '../ui/TextField';
import { Select } from '../ui/Select';

export type FieldKind = 'text' | 'number' | 'date' | 'textarea' | 'select';

export interface FieldSpec {
  key: string;
  label: string;
  kind: FieldKind;
  options?: { value: string; label: string }[];
  placeholder?: string;
}

export interface SubListSpec {
  key: string;
  label: string;
  columns: { key: string; label: string; kind: FieldKind }[];
}

export interface TypeSpec {
  label: string;
  family: string;
  fields: FieldSpec[];
  sublist?: SubListSpec;
  /** true = disponible uniquement en SARL AU. */
  auOnly?: boolean;
  /** true = indisponible en SARL AU. */
  auExclu?: boolean;
}

export const OUI_NON = [
  { value: 'non', label: 'Non' },
  { value: 'oui', label: 'Oui' },
];

export const FAM_IDENTITE = 'Identité & statuts';
export const FAM_CAPITAL = 'Capital';
export const FAM_PARTS = 'Parts sociales';
export const FAM_GERANCE = 'Gérance';
export const FAM_TRANSFO = 'Transformation & restructuration';
export const FAM_CONTROLE = 'Contrôle & conventions';
export const FAM_COMPTES = 'Comptes & résultat';
export const FAM_DIVERS = 'Formation & pouvoirs';

export const RES_SPECS: Record<string, TypeSpec> = {
  // ---- Identité & statuts ----
  transfert_siege: {
    label: 'Transfert du siège social', family: FAM_IDENTITE,
    fields: [
      { key: 'siegeMemePrefecture', label: 'Même préfecture / province ?', kind: 'select', options: OUI_NON },
      { key: 'nouveauSiege', label: 'Nouveau siège', kind: 'text' },
      { key: 'dateEffet', label: "Date d'effet", kind: 'date' },
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text', placeholder: '4' },
    ],
  },
  modification_denomination: {
    label: 'Changement de dénomination', family: FAM_IDENTITE,
    fields: [
      { key: 'nouvelleDenomination', label: 'Nouvelle dénomination', kind: 'text' },
      { key: 'dateEffet', label: "Date d'effet", kind: 'date' },
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text', placeholder: '2' },
    ],
  },
  modification_objet: {
    label: "Modification de l'objet social", family: FAM_IDENTITE,
    fields: [
      { key: 'objetAction', label: 'Action', kind: 'select', options: [
        { value: 'modification', label: 'Modification (nouvel objet)' },
        { value: 'extension', label: "Extension (ajout d'activités)" },
      ] },
      { key: 'objetModification', label: 'Texte (nouvel objet / activités ajoutées)', kind: 'textarea' },
      { key: 'dateEffet', label: "Date d'effet", kind: 'date' },
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text', placeholder: '3' },
    ],
  },
  modification_exercice: {
    label: "Modification de la date de clôture de l'exercice", family: FAM_IDENTITE,
    fields: [
      { key: 'nouvelleDateCloture', label: 'Nouvelle date de clôture', kind: 'text', placeholder: '31 décembre' },
      { key: 'exerciceTransitoireDuree', label: 'Durée exercice transitoire (mois)', kind: 'number' },
      { key: 'exerciceTransitoireDebut', label: 'Début exercice transitoire', kind: 'date' },
      { key: 'exerciceTransitoireFin', label: 'Fin exercice transitoire', kind: 'date' },
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text', placeholder: '24' },
    ],
  },
  prorogation_duree: {
    label: 'Prorogation de la durée', family: FAM_IDENTITE,
    fields: [
      { key: 'dateExpirationInitiale', label: "Date d'expiration initiale", kind: 'date' },
      { key: 'prorogationDuree', label: 'Prorogation (années)', kind: 'number' },
      { key: 'nouvelleDureeEcheance', label: 'Nouvelle échéance', kind: 'date' },
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text', placeholder: '5' },
    ],
  },
  mise_harmonie_statuts: {
    label: 'Mise en harmonie des statuts (loi 5-96)', family: FAM_IDENTITE, fields: [],
  },
  modification_statuts_autre: {
    label: 'Autre modification statutaire', family: FAM_IDENTITE,
    fields: [
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text' },
      { key: 'statutsNouveauLibelle', label: 'Nouveau libellé', kind: 'textarea' },
    ],
  },
  // ---- Capital ----
  augmentation_capital_numeraire: {
    label: 'Augmentation de capital en numéraire', family: FAM_CAPITAL,
    fields: [
      { key: 'augcapMontantChiffres', label: "Montant de l'augmentation (MAD)", kind: 'number' },
      { key: 'augcapNouveauCapital', label: 'Nouveau capital (MAD)', kind: 'number' },
      { key: 'augcapMode', label: "Mode", kind: 'select', options: [
        { value: 'création de parts nouvelles', label: 'Création de parts nouvelles' },
        { value: 'élévation du nominal', label: 'Élévation du nominal' },
      ] },
      { key: 'augcapNbPartsNouvelles', label: 'Nb de parts nouvelles', kind: 'number' },
      { key: 'valeurNominalePart', label: 'Valeur nominale (MAD)', kind: 'number' },
      { key: 'augcapPartsDe', label: 'Parts numérotées de', kind: 'number' },
      { key: 'augcapPartsA', label: 'à', kind: 'number' },
      { key: 'nouvelleValeurNominale', label: 'Nouvelle valeur nominale (si élévation)', kind: 'number' },
      { key: 'augcapLiberationMode', label: 'Libération', kind: 'select', options: [
        { value: 'numéraire', label: 'En numéraire' },
        { value: 'compensation', label: 'Par compensation de créances' },
      ] },
      { key: 'primeEmission', label: "Prime d'émission ?", kind: 'select', options: OUI_NON },
      { key: 'primeEmissionMontant', label: 'Prime par part (MAD)', kind: 'number' },
      { key: 'dpsSuppression', label: 'Suppression du DPS ?', kind: 'select', options: OUI_NON },
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text', placeholder: '6 et 7' },
    ],
    sublist: {
      key: 'beneficiairesDps', label: 'Bénéficiaires (si DPS supprimé)',
      columns: [
        { key: 'nom', label: 'Nom', kind: 'text' },
        { key: 'nbParts', label: 'Nb de parts', kind: 'number' },
      ],
    },
  },
  augmentation_capital_nature: {
    label: 'Augmentation de capital par apport en nature', family: FAM_CAPITAL,
    fields: [
      { key: 'commissaireApportsNom', label: 'Commissaire aux apports', kind: 'text' },
      { key: 'apporteurNom', label: "Apporteur", kind: 'text' },
      { key: 'apportNatureDescription', label: "Description de l'apport", kind: 'textarea' },
      { key: 'apportNatureValeurChiffres', label: "Valeur de l'apport (MAD)", kind: 'number' },
      { key: 'augcapMontantChiffres', label: "Montant de l'augmentation (MAD)", kind: 'number' },
      { key: 'augcapNouveauCapital', label: 'Nouveau capital (MAD)', kind: 'number' },
      { key: 'augcapNbPartsNouvelles', label: 'Nb de parts nouvelles', kind: 'number' },
      { key: 'valeurNominalePart', label: 'Valeur nominale (MAD)', kind: 'number' },
      { key: 'fondsCommerce', label: 'Apport = fonds de commerce ?', kind: 'select', options: OUI_NON },
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text', placeholder: '6 et 7' },
    ],
  },
  augmentation_capital_incorporation: {
    label: 'Augmentation de capital par incorporation de réserves', family: FAM_CAPITAL,
    fields: [
      { key: 'augcapMontantChiffres', label: "Montant de l'augmentation (MAD)", kind: 'number' },
      { key: 'augcapNouveauCapital', label: 'Nouveau capital (MAD)', kind: 'number' },
      { key: 'incorporationPoste', label: 'Poste incorporé', kind: 'text', placeholder: 'Report à nouveau' },
      { key: 'augcapMode', label: 'Mode', kind: 'select', options: [
        { value: 'création de parts nouvelles', label: 'Création de parts nouvelles' },
        { value: 'élévation du nominal', label: 'Élévation du nominal' },
      ] },
      { key: 'augcapNbPartsNouvelles', label: 'Nb de parts nouvelles', kind: 'number' },
      { key: 'valeurNominalePart', label: 'Valeur nominale (MAD)', kind: 'number' },
      { key: 'nouvelleValeurNominale', label: 'Nouvelle valeur nominale (si élévation)', kind: 'number' },
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text', placeholder: '6 et 7' },
    ],
  },
  reduction_capital: {
    label: 'Réduction de capital', family: FAM_CAPITAL,
    fields: [
      { key: 'redcapMotif', label: 'Motif', kind: 'select', options: [
        { value: 'pertes', label: 'Apurement de pertes' },
        { value: 'non motivée par des pertes', label: 'Non motivée par des pertes' },
      ] },
      { key: 'redcapMontant', label: 'Montant de la réduction (MAD)', kind: 'number' },
      { key: 'redcapNouveauCapital', label: 'Nouveau capital (MAD)', kind: 'number' },
      { key: 'redcapMode', label: 'Mode', kind: 'select', options: [
        { value: 'diminution du nominal', label: 'Diminution du nominal' },
        { value: 'réduction du nombre de parts', label: 'Réduction du nombre de parts' },
        { value: 'annulation de parts rachetées', label: 'Annulation de parts rachetées' },
      ] },
      { key: 'valeurNominalePart', label: 'Valeur nominale (MAD)', kind: 'number' },
      { key: 'nouvelleValeurNominale', label: 'Nouvelle valeur nominale', kind: 'number' },
      { key: 'redcapNbPartsNouvelles', label: 'Nb de parts après échange', kind: 'number' },
      { key: 'rachatPrixPart', label: 'Prix de rachat par part (MAD)', kind: 'number' },
      { key: 'rachatDateLimite', label: 'Date limite de rachat', kind: 'date' },
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text', placeholder: '6 et 7' },
    ],
  },
  // ---- Parts sociales ----
  agrement_cession: {
    label: "Agrément d'une cession de parts", family: FAM_PARTS, auExclu: true,
    fields: [
      { key: 'cessionNbParts', label: 'Nb de parts cédées', kind: 'number' },
      { key: 'cessionPartsDe', label: 'Parts numérotées de', kind: 'number' },
      { key: 'cessionPartsA', label: 'à', kind: 'number' },
      { key: 'cedantNom', label: 'Cédant', kind: 'text' },
      { key: 'cessionnaireNom', label: 'Cessionnaire', kind: 'text' },
      { key: 'cessionPrix', label: 'Prix global (MAD)', kind: 'number' },
      { key: 'cessionnaireTiers', label: 'Cessionnaire tiers (non associé) ?', kind: 'select', options: OUI_NON },
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text', placeholder: '6 et 7' },
    ],
  },
  agrement_transmission: {
    label: "Agrément d'une transmission de parts", family: FAM_PARTS, auExclu: true,
    fields: [
      { key: 'cessionNbParts', label: 'Nb de parts transmises', kind: 'number' },
      { key: 'transmissionOrigine', label: 'Origine', kind: 'text', placeholder: 'une succession' },
      { key: 'cessionnaireNom', label: 'Attributaire', kind: 'text' },
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text', placeholder: '6 et 7' },
    ],
  },
  nantissement_parts: {
    label: 'Consentement à un nantissement de parts', family: FAM_PARTS, auExclu: true,
    fields: [
      { key: 'nantissementNbParts', label: 'Nb de parts nanties', kind: 'number' },
      { key: 'nantissementConstituantNom', label: 'Constituant (associé)', kind: 'text' },
      { key: 'nantissementCreancierNom', label: 'Créancier nanti', kind: 'text' },
      { key: 'nantissementCreanceObjet', label: 'Créance garantie', kind: 'text' },
    ],
  },
  cession_parts_pluripersonnelle: {
    label: 'Cession de parts — passage en SARL pluripersonnelle', family: FAM_PARTS, auOnly: true,
    fields: [
      { key: 'cessionNbParts', label: 'Nb de parts cédées', kind: 'number' },
      { key: 'cessionnaireNom', label: 'Cessionnaire', kind: 'text' },
      { key: 'cessionPrix', label: 'Prix global (MAD)', kind: 'number' },
      { key: 'articlesModifies', label: 'Article(s) modifié(s)', kind: 'text', placeholder: '6, 7 et 8' },
    ],
  },
  // ---- Gérance ----
  nomination_gerant: {
    label: "Nomination d'un gérant", family: FAM_GERANCE,
    fields: [
      { key: 'gerantSortant', label: 'Gérant sortant ?', kind: 'select', options: OUI_NON },
      { key: 'gerantSortantNom', label: 'Nom du gérant sortant', kind: 'text' },
      { key: 'gerantSortantMotif', label: 'Motif de cessation', kind: 'text', placeholder: 'démission' },
      { key: 'gerantSortantDateEffet', label: "Date d'effet cessation", kind: 'date' },
      { key: 'dureeGerance', label: 'Durée du mandat', kind: 'text', placeholder: 'durée illimitée' },
    ],
    sublist: {
      key: 'gerants', label: 'Gérant(s) nommé(s)',
      columns: [
        { key: 'civilite', label: 'Civilité', kind: 'text' },
        { key: 'prenom', label: 'Prénom', kind: 'text' },
        { key: 'nom', label: 'Nom', kind: 'text' },
        { key: 'nationalite', label: 'Nationalité', kind: 'text' },
        { key: 'dateNaissance', label: 'Né(e) le', kind: 'date' },
        { key: 'adresse', label: 'Adresse', kind: 'text' },
        { key: 'pieceType', label: 'Pièce', kind: 'text' },
        { key: 'pieceNumero', label: 'N° pièce', kind: 'text' },
      ],
    },
  },
  renouvellement_gerant: {
    label: 'Renouvellement du mandat de gérant', family: FAM_GERANCE,
    fields: [
      { key: 'gerantNom', label: 'Gérant', kind: 'text' },
      { key: 'dureeGerance', label: 'Durée du mandat', kind: 'text', placeholder: 'durée illimitée' },
    ],
  },
  revocation_gerant: {
    label: "Révocation d'un gérant", family: FAM_GERANCE,
    fields: [{ key: 'gerantSortantNom', label: 'Gérant révoqué', kind: 'text' }],
  },
  remuneration_gerant: {
    label: 'Rémunération de la gérance', family: FAM_GERANCE,
    fields: [
      { key: 'gerantNom', label: 'Gérant', kind: 'text' },
      { key: 'remunerationPeriodicite', label: 'Périodicité', kind: 'text', placeholder: 'mensuelle' },
      { key: 'remunerationMontant', label: 'Montant (MAD)', kind: 'number' },
      { key: 'remunerationDateEffet', label: "Date d'effet", kind: 'date' },
    ],
  },
  autorisation_gerance: {
    label: 'Autorisation donnée à la gérance', family: FAM_GERANCE,
    fields: [{ key: 'autorisationObjet', label: "Objet de l'autorisation", kind: 'textarea',
      placeholder: 'caution, aval, garantie, location-gérance…' }],
  },
  // ---- Transformation & restructuration ----
  transformation: {
    label: 'Transformation de la société', family: FAM_TRANSFO,
    fields: [
      { key: 'commissaireTransformationExiste', label: 'Commissaire à la transformation ?', kind: 'select', options: OUI_NON },
      { key: 'commissaireTransformationNom', label: 'Commissaire à la transformation', kind: 'text' },
      { key: 'transformationForme', label: 'Forme nouvelle', kind: 'text', placeholder: 'société anonyme' },
      { key: 'transformationOrganes', label: 'Organes de la forme nouvelle', kind: 'textarea' },
    ],
  },
  operation_restructuration: {
    label: 'Opération de restructuration (fusion / scission…)', family: FAM_TRANSFO, auExclu: true,
    fields: [
      { key: 'restructurationNature', label: 'Nature', kind: 'select', options: [
        { value: 'fusion', label: 'Fusion' },
        { value: 'scission', label: 'Scission' },
        { value: "apport partiel d'actif", label: "Apport partiel d'actif" },
      ] },
      { key: 'restructurationModalites', label: 'Modalités', kind: 'textarea' },
    ],
  },
  capitaux_propres_art86: {
    label: 'Capitaux propres < ¼ du capital (art. 86) — poursuite', family: FAM_TRANSFO,
    fields: [{ key: 'exerciceClosDate', label: "Date de clôture de l'exercice", kind: 'date' }],
  },
  designation_commissaire_transformation: {
    label: 'Désignation du commissaire à la transformation', family: FAM_TRANSFO, auExclu: true,
    fields: [{ key: 'commissaireTransformationNom', label: 'Commissaire à la transformation', kind: 'text' }],
  },
  // ---- Contrôle & conventions ----
  commissaire_comptes: {
    label: 'Commissaire aux comptes (nomination / renouvellement / fin)', family: FAM_CONTROLE,
    fields: [
      { key: 'cacAction', label: 'Action', kind: 'select', options: [
        { value: 'nomination', label: 'Nomination' },
        { value: 'renouvellement', label: 'Renouvellement' },
        { value: 'fin de mandat', label: 'Fin de mandat' },
      ] },
      { key: 'commissaireComptesNom', label: 'Commissaire aux comptes', kind: 'text' },
      { key: 'dureeMandatCac', label: 'Durée du mandat (exercices)', kind: 'text', placeholder: 'trois' },
      { key: 'cacFinMandatDate', label: 'Exercice clos (fin de mandat)', kind: 'date' },
    ],
  },
  conventions_reglementees: {
    label: 'Approbation des conventions réglementées', family: FAM_CONTROLE, fields: [],
  },
  // ---- Comptes & résultat ----
  approbation_comptes: {
    label: 'Approbation des comptes', family: FAM_COMPTES,
    fields: [
      { key: 'exerciceClosDate', label: "Date de clôture de l'exercice", kind: 'date' },
      { key: 'resultatSens', label: 'Résultat', kind: 'select', options: [
        { value: 'bénéfice', label: 'Bénéfice' },
        { value: 'perte', label: 'Perte' },
      ] },
      { key: 'resultatMontant', label: 'Montant du résultat (MAD)', kind: 'number' },
    ],
  },
  affectation_resultat: {
    label: 'Affectation du résultat', family: FAM_COMPTES,
    fields: [
      { key: 'exerciceClosDate', label: "Date de clôture de l'exercice", kind: 'date' },
      { key: 'resultatMontant', label: 'Montant du résultat (MAD)', kind: 'number' },
    ],
    sublist: {
      key: 'affectations', label: 'Affectations',
      columns: [
        { key: 'poste', label: 'Poste', kind: 'text' },
        { key: 'montant', label: 'Montant (MAD)', kind: 'number' },
      ],
    },
  },
  distribution_dividendes: {
    label: 'Distribution de dividendes', family: FAM_COMPTES,
    fields: [
      { key: 'dividendeTotal', label: 'Dividende global (MAD)', kind: 'number' },
      { key: 'dividendeParPart', label: 'Dividende par part (MAD)', kind: 'number' },
      { key: 'dateMisePaiement', label: 'Mise en paiement au plus tard le', kind: 'date' },
    ],
  },
  distribution_reserves: {
    label: 'Distribution de réserves', family: FAM_COMPTES,
    fields: [
      { key: 'prelevementMontant', label: 'Montant prélevé (MAD)', kind: 'number' },
      { key: 'prelevementPoste', label: 'Poste de réserve', kind: 'text' },
      { key: 'dividendeParPart', label: 'Par part (MAD)', kind: 'number' },
      { key: 'dateMisePaiement', label: 'Mise en paiement au plus tard le', kind: 'date' },
    ],
  },
  acompte_dividendes: {
    label: 'Acompte sur dividendes', family: FAM_COMPTES,
    fields: [
      { key: 'acompteMontant', label: 'Acompte global (MAD)', kind: 'number' },
      { key: 'acompteParPart', label: 'Acompte par part (MAD)', kind: 'number' },
    ],
  },
  // ---- Formation & pouvoirs ----
  ratification_actes_formation: {
    label: 'Ratification des actes de la période de formation', family: FAM_DIVERS,
    fields: [{ key: 'engagementsMandat', label: 'État des engagements', kind: 'textarea' }],
  },
  pouvoirs_formalites: {
    label: 'Pouvoirs pour les formalités', family: FAM_DIVERS, fields: [],
  },
};

/** Ordre d'affichage des familles dans le sélecteur de type. */
export const FAMILY_ORDER = [
  FAM_IDENTITE, FAM_CAPITAL, FAM_PARTS, FAM_GERANCE,
  FAM_TRANSFO, FAM_CONTROLE, FAM_COMPTES, FAM_DIVERS,
];

// --------------------------------------------------------------------------
// État d'une résolution + helpers
// --------------------------------------------------------------------------
export interface ResolutionState {
  /** Identifiant stable (id de la décision officielle seedée, ou généré). */
  id: string;
  /** Type de résolution du moteur directeur (snake_case). */
  type: string;
  objet: string;
  values: Record<string, string>;
  rows: Record<string, Array<Record<string, string>>>;
  /** Nouvel associé saisi via OCR CIN (décisions d'entrée au capital). */
  newAssocie?: Record<string, string>;
}

/** Crée une résolution vierge d'un type donné. */
export function newResolution(type: string, id: string, objet = ''): ResolutionState {
  return { id, type, objet, values: {}, rows: {} };
}

/**
 * Réconcilie l'état des résolutions avec la sélection courante : conserve les
 * valeurs déjà saisies pour les décisions maintenues, ajoute les nouvelles
 * (une résolution par décision), retire celles dé-sélectionnées, en préservant
 * l'ordre de la sélection. Pure / testable.
 */
export function seedResolutions(
  prev: ResolutionState[],
  decisions: { id: string; resolutionType: string; label: string }[],
): ResolutionState[] {
  const byId = new Map(prev.map((r) => [r.id, r]));
  return decisions.map((d) => {
    const existing = byId.get(d.id);
    if (existing && existing.type === d.resolutionType) return existing;
    return newResolution(d.resolutionType, d.id, d.label);
  });
}

/** Aplati une résolution en payload attendu par ModificationDirecteurMapper. */
export function flattenResolution(r: ResolutionState): Record<string, unknown> {
  const out: Record<string, unknown> = { type: r.type };
  if (r.objet.trim()) out.objet = r.objet.trim();
  const spec = RES_SPECS[r.type];
  for (const f of spec?.fields ?? []) {
    const v = r.values[f.key];
    if (v != null && v !== '') out[f.key] = v;
  }
  if (spec?.sublist) {
    const rows = (r.rows[spec.sublist.key] ?? []).filter((row) =>
      Object.values(row).some((v) => (v ?? '').toString().trim() !== ''),
    );
    if (rows.length) out[spec.sublist.key] = rows;
  }
  if (r.newAssocie && Object.values(r.newAssocie).some((v) => (v ?? '').trim() !== '')) {
    out.nouvelAssocie = r.newAssocie;
  }
  return out;
}

/** Types disponibles pour une forme (groupés par famille). */
export function availableTypesFor(isAU: boolean): Record<string, { value: string; label: string }[]> {
  const byFamily: Record<string, { value: string; label: string }[]> = {};
  for (const [value, spec] of Object.entries(RES_SPECS)) {
    if (isAU && spec.auExclu) continue;
    if (!isAU && spec.auOnly) continue;
    (byFamily[spec.family] ??= []).push({ value, label: spec.label });
  }
  return byFamily;
}

/** Une paire ancienne-valeur (lecture seule) affichée à côté des nouveaux champs. */
export interface OldValue {
  label: string;
  value: string;
}

function fmt(v: unknown): string {
  if (v === null || v === undefined || `${v}`.trim() === '') return '—';
  return `${v}`;
}
function fmtMad(v: unknown): string {
  if (v === null || v === undefined || `${v}`.trim() === '') return '—';
  const n = Number(v);
  return Number.isFinite(n) ? `${n.toLocaleString('fr-FR')} MAD` : `${v}`;
}

/**
 * Anciennes valeurs pertinentes pour un `resolutionType`, à afficher en lecture
 * seule à côté des nouveaux champs. Source : fiche EFFECTIVE (BD ⊕ overrides).
 */
export function oldValuesFor(
  resolutionType: string,
  fiche: Record<string, unknown> | undefined,
): OldValue[] {
  const f = fiche ?? {};
  switch (resolutionType) {
    case 'modification_denomination':
      return [{ label: 'Dénomination actuelle', value: fmt(f.denomination) }];
    case 'modification_objet':
      return [{ label: 'Objet social actuel', value: fmt(f.objetSocial) }];
    case 'transfert_siege':
      return [{ label: 'Siège actuel', value: fmt(f.adresseSiege) }];
    case 'prorogation_duree':
      return [{ label: 'Durée actuelle (années)', value: fmt(f.dureeAnnees) }];
    case 'modification_exercice':
      return [{ label: 'Clôture actuelle', value: fmt(f.dateClotureExercice) }];
    case 'augmentation_capital_numeraire':
    case 'augmentation_capital_nature':
    case 'augmentation_capital_incorporation':
    case 'reduction_capital':
      return [
        { label: 'Capital actuel', value: fmtMad(f.capitalSocial) },
        { label: 'Valeur nominale actuelle', value: fmtMad(f.valeurNominale) },
        { label: 'Nombre de parts actuel', value: fmt(f.nombreParts) },
      ];
    case 'agrement_cession':
    case 'agrement_transmission':
    case 'nantissement_parts':
    case 'cession_parts_pluripersonnelle':
      return [
        { label: 'Capital actuel', value: fmtMad(f.capitalSocial) },
        { label: 'Nombre de parts actuel', value: fmt(f.nombreParts) },
      ];
    default:
      return [];
  }
}

// --------------------------------------------------------------------------
// Composants d'édition réutilisables
// --------------------------------------------------------------------------

/**
 * Champ « liste de choix » — Fix M1 (2026-08-16).
 *
 * <b>Ce qui est affiché doit être ce qui est envoyé.</b> Le select affichait la
 * première option comme repli visuel (`value || options[0].value`) sans jamais
 * l'écrire dans l'état. Or {@link flattenResolution} écarte les valeurs vides :
 * la clé n'atteignait donc pas le payload, et le mapper backend appliquait SON
 * propre défaut — parfois l'INVERSE de ce que l'employé lisait à l'écran.
 *
 * Cas constaté en simulation : « Même préfecture / province ? » affichait
 * « Non », et le PV de transfert de siège écrivait « située dans la <b>même</b>
 * préfecture ». Un acte juridiquement faux, sans le moindre signal.
 *
 * On matérialise donc le défaut affiché dans l'état dès le montage : plus aucun
 * écart possible entre l'écran et l'acte.
 */
function SelectField({
  spec,
  value,
  onChange,
}: {
  spec: FieldSpec;
  value: string;
  onChange: (v: string) => void;
}) {
  const fallback = spec.options?.[0]?.value ?? '';
  const effective = value || fallback;
  useEffect(() => {
    if (!value && fallback) onChange(fallback);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value, fallback]);
  return (
    <Select
      label={spec.label}
      value={effective}
      onChange={(e) => onChange(e.target.value)}
      options={spec.options ?? []}
    />
  );
}

export function DynamicField({
  spec,
  value,
  onChange,
}: {
  spec: FieldSpec;
  value: string;
  onChange: (v: string) => void;
}) {
  if (spec.kind === 'select') {
    return <SelectField spec={spec} value={value} onChange={onChange} />;
  }
  if (spec.kind === 'textarea') {
    return (
      <label className="flex flex-col gap-1 text-sm md:col-span-2">
        <span className="font-medium text-fg">{spec.label}</span>
        <textarea
          className="min-h-[80px] rounded-lg border border-border bg-bg-raised px-3 py-2 text-sm text-fg"
          value={value}
          placeholder={spec.placeholder}
          onChange={(e) => onChange(e.target.value)}
        />
      </label>
    );
  }
  return (
    <TextField
      label={spec.label}
      type={spec.kind === 'number' ? 'number' : spec.kind === 'date' ? 'date' : 'text'}
      value={value}
      placeholder={spec.placeholder}
      onChange={(e) => onChange(e.target.value)}
    />
  );
}

export function SubListEditor({
  spec,
  rows,
  onChange,
}: {
  spec: SubListSpec;
  rows: Array<Record<string, string>>;
  onChange: (rows: Array<Record<string, string>>) => void;
}) {
  const addRow = () => onChange([...rows, {}]);
  const removeRow = (i: number) => onChange(rows.filter((_, idx) => idx !== i));
  const setCell = (i: number, key: string, v: string) =>
    onChange(rows.map((row, idx) => (idx === i ? { ...row, [key]: v } : row)));

  return (
    <div className="rounded-lg border border-dashed border-border p-3 space-y-3">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-fg">{spec.label}</span>
        <button
          type="button"
          onClick={addRow}
          className="inline-flex items-center gap-1 text-sm text-violet-600 hover:underline"
        >
          <Plus className="h-4 w-4" /> Ajouter
        </button>
      </div>
      {rows.length === 0 && <p className="text-xs text-fg-subtle">Aucune ligne.</p>}
      {rows.map((row, i) => (
        <div key={i} className="grid gap-2 md:grid-cols-2">
          {spec.columns.map((c) => (
            <TextField
              key={c.key}
              label={c.label}
              type={c.kind === 'number' ? 'number' : c.kind === 'date' ? 'date' : 'text'}
              value={row[c.key] ?? ''}
              onChange={(e) => setCell(i, c.key, e.target.value)}
            />
          ))}
          <div className="md:col-span-2">
            <button
              type="button"
              onClick={() => removeRow(i)}
              className="inline-flex items-center gap-1 text-xs text-danger hover:underline"
            >
              <Trash2 className="h-3.5 w-3.5" /> Retirer la ligne
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}

// --------------------------------------------------------------------------
// P3 (2026-08-10) — associés & gérants EXISTANTS = sélection dans une liste BD,
// JAMAIS de re-saisie d'identité. Seul un NOUVEL associé entrant passe par l'OCR.
// --------------------------------------------------------------------------

/** Listes de noms existants (fiche BD) pour peupler les dropdowns personne. */
export interface PersonLists {
  associe: string[];
  gerant: string[];
}

/** Champs de résolution qui référencent une personne EXISTANTE (→ dropdown BD). */
export const PERSON_FIELD_SOURCE: Record<string, keyof PersonLists> = {
  gerantNom: 'gerant',
  gerantSortantNom: 'gerant',
  cedantNom: 'associe',
  nantissementConstituantNom: 'associe',
};

/** Extrait les noms lisibles d'une liste d'associés / gérants de la fiche BD. */
export function personNames(list: unknown): string[] {
  if (!Array.isArray(list)) return [];
  const out: string[] = [];
  for (const p of list) {
    if (!p || typeof p !== 'object') continue;
    const o = p as Record<string, unknown>;
    const full = [o.prenom, o.nom]
      .filter((x) => x != null && String(x).trim() !== '')
      .map(String)
      .join(' ')
      .trim();
    const name =
      full || (o.denomination ? String(o.denomination) : '') || (o.nom ? String(o.nom) : '');
    if (name && !out.includes(name)) out.push(name);
  }
  return out;
}

/** Sélecteur d'une personne EXISTANTE (associé/gérant) par son nom. */
function PersonSelectField({
  label,
  value,
  names,
  onChange,
}: {
  label: string;
  value: string;
  names: string[];
  onChange: (v: string) => void;
}) {
  return (
    <Select
      label={label}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      options={[{ value: '', label: '— Sélectionner —' }, ...names.map((n) => ({ value: n, label: n }))]}
    />
  );
}

/** Éditeur des champs (+ sous-liste) d'une résolution. */
export function ResolutionFieldsEditor({
  spec,
  values,
  rows,
  personLists,
  onField,
  onRows,
}: {
  spec: TypeSpec | undefined;
  values: Record<string, string>;
  rows: Record<string, Array<Record<string, string>>>;
  /** Listes BD des associés/gérants existants (P3 — sélection, pas de re-saisie). */
  personLists?: PersonLists;
  onField: (key: string, value: string) => void;
  onRows: (key: string, rows: Array<Record<string, string>>) => void;
}) {
  if (!spec) return null;
  return (
    <>
      {spec.fields.length ? (
        <div className="grid gap-3 md:grid-cols-2">
          {spec.fields.map((f) => {
            const source = PERSON_FIELD_SOURCE[f.key];
            const names = source ? personLists?.[source] ?? [] : [];
            // Personne existante + liste BD disponible → dropdown (jamais de re-saisie).
            if (source && names.length > 0) {
              return (
                <PersonSelectField
                  key={f.key}
                  label={f.label}
                  value={values[f.key] ?? ''}
                  names={names}
                  onChange={(v) => onField(f.key, v)}
                />
              );
            }
            return (
              <DynamicField
                key={f.key}
                spec={f}
                value={values[f.key] ?? ''}
                onChange={(v) => onField(f.key, v)}
              />
            );
          })}
        </div>
      ) : (
        <p className="text-sm text-fg-subtle">Aucun champ à renseigner pour ce type.</p>
      )}
      {spec.sublist && (
        <SubListEditor
          spec={spec.sublist}
          rows={rows[spec.sublist.key] ?? []}
          onChange={(r) => onRows(spec.sublist!.key, r)}
        />
      )}
    </>
  );
}
