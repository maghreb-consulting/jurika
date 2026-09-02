import { useEffect, useId, useState } from 'react';
import {
  AlertTriangle,
  Building2,
  CheckCircle,
  ChevronRight,
  FileText,
  Trash2,
  Upload,
  User,
  UserPlus,
} from 'lucide-react';
import type { FormeJuridique } from '../../../types/ticket';
import { IdentityExtractor } from '../../../components/identity/IdentityExtractor';
import { toIsoDate } from '../../../types/identity';
import { useStepAutosave } from '../useStepAutosave';
import type { DirtyGetter } from '../useWorkflow';
import { BlocageValidation } from '../../../components/workflow/BlocageValidation';

interface Props {
  existing?: Record<string, unknown>;
  /**
   * Forme juridique courante — propage depuis Step1 via le wrapper
   * CreationSarlWorkflowPage. Pour SARL_AU, RG-C15 : un seul dirigeant peut
   * porter le flag `isAssociate=true`. Si le user en coche plusieurs, on
   * decoche automatiquement les autres pour rester conforme.
   */
  formeJuridique?: FormeJuridique;
  /**
   * 2026-06-25 (refonte IMPORT) — Pour une societe existante, les pieces
   * (CIN, RC, statuts de l'entite...) sont deposees a l'etape d'upload
   * juridique dediee. On n'exige donc PAS les uploads inline ici : seules les
   * donnees d'IDENTITE (+ gouvernance) restent obligatoires. Defaut
   * {@code false} : la CREATION reste inchangee.
   */
  importMode?: boolean;
  saving: boolean;
  onSubmit: (payload: Record<string, unknown>) => Promise<void>;
  /** P1 2026-06-21 — Cowork : draft callback pour flush au navigation. */
  onSave?: (payload: Record<string, unknown>) => Promise<void>;
  /** P1 2026-06-21 — Cowork : enregistre un getter "dirty" pour useWorkflow.flushDraft(). */
  registerDirty?: (step: number, getter: DirtyGetter) => () => void;
  /** RG transverse 2026-06-05 : registre pieces persistantes (cross-step). */
  onPieceUploaded?: (
    code: string,
    label: string,
    file: File,
  ) => Promise<void> | void;
  /**
   * 2026-06-15 — dossierId du ticket courant. Permet a IdentityExtractor
   * (mode="cin") d'archiver le PDF recto+verso fusionne dans la Data
   * Room du dossier (DataroomIdentityArchiver). Optionnel : sans
   * dossierId, l'extraction se fait sans archivage automatique.
   */
  dossierId?: string | null;
}

/**
 * EX5 2026-06-09 — Un dirigeant peut etre une personne PHYSIQUE (champs CIN)
 * ou MORALE (entite avec RC/ICE/IF + representant legal physique).
 */
type TypePersonne = 'PHYSIQUE' | 'MORALE';

interface Dirigeant {
  id: string;
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

function newDirigeant(): Dirigeant {
  return {
    id: crypto.randomUUID(),
    typePersonne: 'PHYSIQUE',
    civilite: 'M',
    nom: '',
    prenom: '',
    cinNumero: '',
    nationalite: 'Marocaine',
    dateNaissance: '',
    lieuNaissance: '',
    pieceValidite: '',
    adresse: '',
    capitalEntite: 0,
    deliberationDate: '',
    fonction: 'GERANT',
    isStatutaire: true,
    isAssociate: false,
    cinUploaded: false,
    extracting: false,
    // Mandat : aucune valeur devinée. « illimitée » est le régime de droit commun
    // d'une gérance de SARL ; la rémunération, elle, doit être choisie (cf. canSubmit).
    dureeMandatType: 'illimitee',
    dureeAnnees: 0,
    remunerationMode: '',
    remunerationMontant: 0,
    // MORALE defaults
    denomination: '',
    formeJuridiqueEntite: 'SARL',
    rc: '',
    ice: '',
    ifFiscal: '',
    siege: '',
    repCivilite: 'M',
    repNom: '',
    repPrenom: '',
    repCin: '',
    repAdresse: '',
    repDateNaissance: '',
    repLieuNaissance: '',
    repNationalite: 'Marocaine',
    repPieceValidite: '',
    repQualite: 'Gerant',
    rcUploaded: false,
    statutsEntiteUploaded: false,
    repCinUploaded: false,
  };
}

/** Validation CIN marocaine : 1 a 2 lettres + 5 a 7 chiffres. */
function cinInvalidMessage(value: string): string | null {
  if (!value) return null;
  const v = value.replace(/\s+/g, '').toUpperCase();
  if (!/^[A-Z]{1,2}[0-9]{5,7}$/.test(v)) {
    return 'Format CIN invalide (ex : X 123456)';
  }
  return null;
}

/** EX5 — Validation ICE marocain : 15 chiffres. */
function iceInvalidMessage(value: string): string | null {
  if (!value) return null;
  const v = value.replace(/[\s-]/g, '');
  if (!/^\d{15}$/.test(v)) {
    return 'ICE invalide (15 chiffres)';
  }
  return null;
}

/** EX5 — Validation RC : nombre simple (1 a 8 chiffres). */
function rcInvalidMessage(value: string): string | null {
  if (!value) return null;
  const v = value.replace(/\s+/g, '');
  if (!/^\d{1,8}$/.test(v)) {
    return 'RC invalide (chiffres uniquement)';
  }
  return null;
}

/** EX5 — Validation IF (Identifiant Fiscal) : 7 a 8 chiffres. */
function ifInvalidMessage(value: string): string | null {
  if (!value) return null;
  const v = value.replace(/\s+/g, '');
  if (!/^\d{7,8}$/.test(v)) {
    return 'IF invalide (7 a 8 chiffres)';
  }
  return null;
}

export function Step5Dirigeants({
  existing,
  formeJuridique = 'SARL',
  importMode = false,
  saving,
  onSubmit,
  registerDirty,
  onPieceUploaded,
  dossierId,
}: Props) {
  const isSarlAu = formeJuridique === 'SARL_AU';
  /*
   * Reprise des brouillons ANTÉRIEURS à la gérance par dirigeant (2026-08-18).
   *
   * Jusqu'ici le mandat vivait dans le bag global `gerance` ; les dirigeants
   * persistés n'en portent donc aucune trace. Rouvrir un dossier en cours
   * remettrait chaque mandat à zéro — durée « illimitée », rémunération vide,
   * limitation perdue — et le bouton « Valider » se rebloquerait sans explication.
   * On reverse donc la gérance globale dans chaque dirigeant qui n'a pas encore
   * la sienne. Les valeurs propres au dirigeant, elles, l'emportent toujours.
   */
  const geranceHeritee = (existing?.gerance as Record<string, unknown> | undefined) ?? {};
  function reprendreMandat(d: Dirigeant): Partial<Dirigeant> {
    if (d.dureeMandatType) return {};   // fiche déjà au nouveau format
    const dureeMandat = String(geranceHeritee.dureeMandat ?? '');
    const dureeGerance = String(geranceHeritee.dureeGerance ?? '');
    // `dureeGerance` (texte libre) primait dans l'ancien modèle : on le lit d'abord,
    // et on n'en retient un nombre d'années que s'il en porte réellement un.
    const annees = Number(
      (dureeGerance.match(/\d+/) ?? dureeMandat.match(/\d+/) ?? [0])[0],
    );
    const modeBrut = String(
      geranceHeritee.remunerationModeKey ?? geranceHeritee.remunerationMode ?? '',
    );
    const parPhrase: Record<string, Dirigeant['remunerationMode']> = {
      'non rémunéré': 'non_remunere',
      'fixée par décision collective des associés': 'decision_collective',
      'montant fixe': 'montant_fixe',
    };
    const mode =
      modeBrut === 'non_remunere' || modeBrut === 'decision_collective'
        || modeBrut === 'montant_fixe'
        ? (modeBrut as Dirigeant['remunerationMode'])
        : parPhrase[modeBrut] ?? '';
    return {
      dureeMandatType: annees > 0 ? 'determinee' : 'illimitee',
      dureeAnnees: annees > 0 ? annees : 0,
      remunerationMode: mode,
      remunerationMontant: Number(geranceHeritee.remunerationMontant ?? 0),
    };
  }
  const fromData = ((existing?.dirigeants as Dirigeant[]) ?? []).map((d) => ({
    ...newDirigeant(),
    ...d,
    ...reprendreMandat(d),
    id: d.id ?? crypto.randomUUID(),
  }));
  const [dirigeants, setDirigeants] = useState<Dirigeant[]>(
    fromData.length > 0 ? fromData : [newDirigeant()],
  );

  function update(id: string, patch: Partial<Dirigeant>) {
    setDirigeants((arr) =>
      arr.map((d) => (d.id === id ? { ...d, ...patch } : d)),
    );
  }

  /**
   * RG-C15 (SARL_AU) — Quand l'utilisateur coche `isAssociate=true` sur un
   * dirigeant, on decoche automatiquement tous les autres. Cas oppose
   * (decochage) laisse l'etat tel quel : 0 ou 1 dirigeant peut etre associe.
   */
  useEffect(() => {
    if (!isSarlAu) return;
    const associates = dirigeants.filter((d) => d.isAssociate);
    if (associates.length <= 1) return;
    const keep = associates[associates.length - 1].id;
    setDirigeants((arr) =>
      arr.map((d) =>
        d.isAssociate && d.id !== keep ? { ...d, isAssociate: false } : d,
      ),
    );
  }, [dirigeants, isSarlAu]);

  /**
   * 2026-06-15 — Reçoit les valeurs CIN extraites par IdentityExtractor
   * (mode="cin") après validation human-in-the-loop. Le mapping snake_case
   * backend → camelCase Dirigeant :
   *   nom            -> nom
   *   prenom         -> prenom
   *   cin            -> cinNumero
   *   date_naissance -> dateNaissance       (converti en ISO YYYY-MM-DD)
   *   lieu_naissance -> lieuNaissance
   *   date_validite  -> pieceValidite       (converti en ISO YYYY-MM-DD)
   *   nationalite    -> nationalite
   *   adresse        -> adresse
   *   sexe (M|F)     -> civilite (M|Mme)
   * Le helper {@link toIsoDate} convertit les dates FR (DD.MM.YYYY) reçues
   * du backend vers le format attendu par les inputs HTML `type="date"`.
   * `meta.archivedDocumentId` est utilisé pour marquer cinUploaded=true et
   * tracer la référence dataroom dans `cinFileName`.
   */
  function applyCinExtraction(
    id: string,
    values: Record<string, string | undefined>,
    meta?: { archivedDocumentId: string | null; source: string },
  ) {
    const patch: Partial<Dirigeant> = {};
    if (values.nom) patch.nom = values.nom;
    if (values.prenom) patch.prenom = values.prenom;
    if (values.cin) patch.cinNumero = values.cin;
    if (values.date_naissance) patch.dateNaissance = toIsoDate(values.date_naissance);
    if (values.lieu_naissance) patch.lieuNaissance = values.lieu_naissance;
    // 2026-06-16 — date d'expiration de la CIN.
    if (values.date_validite) patch.pieceValidite = toIsoDate(values.date_validite);
    if (values.nationalite) patch.nationalite = values.nationalite;
    if (values.adresse) patch.adresse = values.adresse;
    if (values.sexe) {
      patch.civilite = values.sexe.toUpperCase() === 'F' ? 'Mme' : 'M';
    }
    if (meta?.archivedDocumentId) {
      patch.cinUploaded = true;
      patch.cinFileName = `Archive Data Room ${meta.archivedDocumentId.slice(0, 8)}`;
    }
    if (Object.keys(patch).length > 0) update(id, patch);
  }

  function removeDirigeant(id: string) {
    if (dirigeants.length > 1) {
      setDirigeants((arr) => arr.filter((d) => d.id !== id));
    }
  }

  // EX5 — validation conditionnelle par typePersonne :
  //   PHYSIQUE : CIN + identite + upload CIN.
  //   MORALE   : denomination + RC + ICE + IF + siege + representant legal complet
  //              + upload RC + upload statuts entite.
  function isDirigeantValid(d: Dirigeant): boolean {
    if (d.typePersonne === 'MORALE') {
      return Boolean(
        d.denomination &&
          d.rc &&
          !rcInvalidMessage(d.rc ?? '') &&
          d.ice &&
          !iceInvalidMessage(d.ice ?? '') &&
          d.ifFiscal &&
          !ifInvalidMessage(d.ifFiscal ?? '') &&
          d.siege &&
          d.repNom &&
          d.repPrenom &&
          d.repCin &&
          !cinInvalidMessage(d.repCin ?? '') &&
          d.repAdresse &&
          d.repQualite &&
          // 2026-06-25 — En IMPORT, les pieces sont deposees a l'etape d'upload
          // juridique : on n'exige pas les uploads inline.
          (importMode || (d.rcUploaded &&
          d.statutsEntiteUploaded &&
          d.repCinUploaded)), // F2 2026-06-09 — CIN du representant legal obligatoire
      );
    }
    return Boolean(
      d.nom &&
        d.prenom &&
        d.cinNumero &&
        d.adresse &&
        (importMode || d.cinUploaded) &&
        !cinInvalidMessage(d.cinNumero),
    );
  }
  const dirigeantsValid = dirigeants.length >= 1 && dirigeants.every(isDirigeantValid);

  // 2026-06-19 (Cowork) — Re-hydratation des pieces : set des dirigeants
  // en cours de "Remplacer la CIN" (force le re-affichage de l'IdentityExtractor
  // meme si une archive Data Room est deja presente). Evite le bug "redemande
  // d'upload" a la navigation Step6 -> Step5.
  const [replacingCinIds, setReplacingCinIds] = useState<Set<string>>(new Set());

  // 2026-06-19 — Commissaire aux comptes (optionnel — pose le flag {{cac_nomme}}
  // qui pilote le bloc CAC dans les Statuts).
  const existingCacNomme = (existing?.cacNomme as boolean) ?? false;
  const [cacNomme, setCacNomme] = useState<boolean>(existingCacNomme);
  const [cacNom, setCacNom] = useState<string>(
    (existing?.cacNom as string) ?? '',
  );

  // Persistance « bulletproof » : le bag `gerance` porte encore la signature
  // sociale, le CAC et l'agrégation des mandats. Lecture résiliente `gerance ??
  // existing` (comme Step2-4) pour les brouillons antérieurs.
  const existingGer =
    (existing?.gerance as Record<string, unknown> | undefined) ??
    (existing as Record<string, unknown> | undefined) ??
    {};

  // Agrégation des mandats (fonction pure, cf. `agregerGerance`).
  const { dureeGerance, remunerationMode, remunerationMontant } =
    agregerGerance(dirigeants);

  /*
   * Limitation des pouvoirs — UNE SEULE FOIS, sur la gérance comme ORGANE.
   *
   * Les statuts la rendent ainsi : « Par exception, les actes et opérations suivants
   * requièrent l'accord préalable des associés : $LIMITATION_POUVOIRS. » C'est une
   * clause qui borne ce que la GÉRANCE peut faire, pas un attribut attaché à une
   * personne. La saisir par dirigeant aurait produit une clause énumérative
   * (« … pour M. X et … pour Mme Y ») qui décrit mal l'objet de la limitation.
   */
  const [limitationPouvoirs, setLimitationPouvoirs] = useState<string>(
    (existingGer.limitationPouvoirs as string) ?? '',
  );

  // 2026-08 — Mode de designation DERIVE des cases « Gerant statutaire » de
  // chaque gerant (plus de double saisie) : tous statutaires -> « statutaire »,
  // sinon « non statutaire » (un acte de nomination separe sera genere).
  const gerantModeDesignation: 'statutaire' | 'non statutaire' =
    dirigeants.every((d) => d.isStatutaire) ? 'statutaire' : 'non statutaire';

  // 2026-08 (contrat CREATION) — Signature sociale (art. 15). La semantique
  // vit desormais ici (le builder lit cap.modeSignature ?? gerance5.modeSignature).
  const [modeSignature, setModeSignature] = useState<string>(
    (existingGer.modeSignature as string) ?? 'séparée',
  );
  const [signaturePlafond, setSignaturePlafond] = useState<number>(
    Number(existingGer.signaturePlafond ?? 0),
  );
  const [signatureMandataire, setSignatureMandataire] = useState<boolean>(
    existingGer.signatureMandataire === true,
  );
  const [mandataireNom, setMandataireNom] = useState<string>(
    (existingGer.mandataireNom as string) ?? '',
  );
  const [mandataireActeDelegation, setMandataireActeDelegation] = useState<string>(
    (existingGer.mandataireActeDelegation as string) ?? '',
  );
  const [modeSignatureAdmin, setModeSignatureAdmin] = useState<
    'identique' | 'signature seule'
  >(
    (existingGer.modeSignatureAdmin as 'identique' | 'signature seule') ??
      'identique',
  );

  // 2026-08 (contrat CREATION) — signataires[] (nom + qualite), choisis parmi
  // les gerants. Persistes sous step5.signataires ; lus par le builder.
  interface Signataire {
    id: string;
    nom: string;
    qualite: string;
  }
  const [signataires, setSignataires] = useState<Signataire[]>(
    ((existing?.signataires as Signataire[]) ?? []).map((s) => ({
      id: s.id ?? crypto.randomUUID(),
      nom: s.nom ?? '',
      qualite: s.qualite ?? '',
    })),
  );
  const signataireListId = useId();
  const signataireCandidates = dirigeants
    .filter((d) => d.typePersonne !== 'MORALE')
    .map((d) => `${d.prenom ?? ''} ${d.nom ?? ''}`.trim())
    .filter(Boolean);

  // Objet gouvernance pret pour le payload (phrases consommees par le mapper :
  // gerant_duree_mandat = String, gerant_duree_annees = Long, gerant_remuneration_*).
  const REMUNERATION_PHRASES: Record<string, string> = {
    non_remunere: 'non rémunéré',
    decision_collective: 'fixée par décision collective des associés',
    montant_fixe: 'montant fixe',
  };
  const gerance = {
    // 2026-08-18 — UNE SEULE SOURCE pour la durée du mandat.
    //
    // `dureeMandat` (choix structuré) et `dureeGerance` (texte libre pré-rempli
    // « 99 années ») coexistaient, et la page de création préférait le SECOND :
    // un mandat déclaré « illimité » ressortait donc « 99 années » dans l'acte de
    // nomination. Les deux clés sont conservées pour les consommateurs existants,
    // mais elles portent maintenant la MÊME valeur, agrégée des dirigeants.
    dureeMandat: dureeGerance,
    dureeGerance,
    remunerationMode: remunerationMode ? REMUNERATION_PHRASES[remunerationMode] : '',
    // 2026-08 (persistance) — clé interne persistée pour ré-hydrater le <select>
    // (la phrase ci-dessus reste consommée par le mapper/builder).
    remunerationModeKey: remunerationMode,
    remunerationMontant:
      remunerationMode === 'montant_fixe' ? remunerationMontant : null,
    // 2026-08 (contrat CREATION) — champs explicites lus par la voie directeur.
    gerantModeDesignation,
    limitationPouvoirs,
    modeSignature,
    signaturePlafond:
      modeSignature === 'séparée avec plafond' ? signaturePlafond : 0,
    signatureMandataire,
    mandataireNom: signatureMandataire ? mandataireNom : '',
    mandataireActeDelegation: signatureMandataire ? mandataireActeDelegation : '',
    modeSignatureAdmin,
  };

  /** Le mandat d'UN dirigeant est-il complètement renseigné ? */
  function mandatValide(d: Dirigeant): boolean {
    const dureeOk =
      d.dureeMandatType === 'illimitee' ||
      (d.dureeMandatType === 'determinee' && d.dureeAnnees > 0);
    const remuOk =
      d.remunerationMode !== '' &&
      (d.remunerationMode !== 'montant_fixe' || d.remunerationMontant > 0);
    return dureeOk && remuOk;
  }
  // La gérance est valide quand CHAQUE dirigeant a son mandat : la saisie étant
  // désormais par dirigeant, un contrôle global laisserait passer un mandat vide.
  const geranceValid = dirigeants.length >= 1 && dirigeants.every(mandatValide);

  const canSubmit = dirigeantsValid && geranceValid;

  /**
   * Fix A4 (2026-08-16) — motifs EXPLICITES du blocage.
   *
   * « Valider » restait grisé sans le moindre message tant que la CIN n'avait pas
   * été extraite par OCR : joindre le fichier ne suffisait pas (`cinUploaded` n'est
   * posé que par `applyCinExtraction`), et rien à l'écran ne le signalait. On liste
   * donc ce qui manque, dirigeant par dirigeant.
   */
  const raisonsBlocage: string[] = (() => {
    if (canSubmit) return [];
    const out: string[] = [];
    if (dirigeants.length < 1) out.push('Ajoutez au moins un dirigeant.');
    dirigeants.forEach((d, i) => {
      // Ne PAS sortir tôt sur un dirigeant valide : son identité peut être complète
      // alors que son MANDAT ne l'est pas (la gérance se saisit par dirigeant).
      const qui = [d.prenom, d.nom].filter(Boolean).join(' ').trim() || `Dirigeant ${i + 1}`;
      const manques: string[] = [];
      if (!isDirigeantValid(d)) {
      if (d.typePersonne === 'MORALE') {
        if (!d.denomination) manques.push('dénomination');
        if (!d.repNom || !d.repPrenom) manques.push('représentant légal');
        if (!d.repCin || cinInvalidMessage(d.repCin ?? '')) manques.push('CIN du représentant');
        if (!importMode && !d.repCinUploaded) manques.push('CIN du représentant extraite (bouton « Extraire »)');
        if (!importMode && !d.rcUploaded) manques.push('RC de l’entité');
        if (!importMode && !d.statutsEntiteUploaded) manques.push('statuts de l’entité');
      } else {
        if (!d.nom) manques.push('nom');
        if (!d.prenom) manques.push('prénom');
        if (!d.cinNumero) manques.push('n° de CIN');
        else if (cinInvalidMessage(d.cinNumero)) manques.push('n° de CIN valide');
        if (!d.adresse) manques.push('adresse');
        // LE piège : le fichier joint ne suffit pas, il faut lancer l'extraction.
        if (!importMode && !d.cinUploaded) {
          manques.push('CIN extraite — joindre le fichier ne suffit pas, cliquez « Extraire »');
        }
        }
      }
      // Mandat du dirigeant (section « Gerance » de sa fiche).
      if (d.dureeMandatType === 'determinee' && !(d.dureeAnnees > 0)) {
        manques.push('durée du mandat en années');
      }
      if (d.remunerationMode === '') manques.push('mode de rémunération de la gérance');
      else if (d.remunerationMode === 'montant_fixe' && !(d.remunerationMontant > 0)) {
        manques.push('montant de la rémunération');
      }
      if (manques.length) out.push(`${qui} : ${manques.join(', ')}.`);
    });
    return out;
  })();

  // P1 2026-06-21 — Getter "dirty" pour flush au navigation. Le payload
  // miroite ce que onSubmit envoie ; ainsi quitter l'etape sans cliquer
  // "Valider" persiste tout de meme dirigeants + cacNomme + cacNom.
  useStepAutosave(
    5,
    () => ({
      dirigeants,
      cacNomme,
      cacNom: cacNomme ? cacNom : '',
      gerance,
      signataires,
    }),
    registerDirty,
  );

  return (
    <form
      noValidate
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit({
          dirigeants,
          cacNomme,
          cacNom: cacNomme ? cacNom : '',
          gerance,
          signataires,
        });
      }}
      className="mx-auto max-w-[880px] space-y-6"
    >
      <div className="rounded-xl border border-border bg-accent/10 p-4 text-xs text-fg">
        Identifiez le ou les gerants de la societe. Au moins un gerant est
        requis. <strong>L'upload de la CIN est obligatoire</strong> pour chaque
        dirigeant. Vous pouvez aussi cliquer sur &laquo; Extraire les donnees
        &raquo; pour pre-remplir le formulaire via OCR (aide optionnelle).
        {isSarlAu && (
          <p className="mt-1 font-medium text-accent">
            SARL_AU : un seul dirigeant peut etre coche comme &laquo; Associe
            &raquo; (RG-C15) — il sera propage automatiquement a l'etape
            Associes.
          </p>
        )}
      </div>

      {dirigeants.map((d, i) => {
        const cinError = cinInvalidMessage(d.cinNumero);
        const isMorale = d.typePersonne === 'MORALE';
        return (
          <div
            key={d.id}
            className="overflow-hidden rounded-xl border border-border bg-bg-raised shadow-sm"
          >
            {/* EX4 2026-06-09 — En-tete refondu : badges colores lisibles + select fonction. */}
            <div className="flex flex-col gap-3 border-b border-border bg-bg-overlay px-6 py-4 md:flex-row md:items-center md:justify-between">
              <div className="flex flex-wrap items-center gap-3">
                <div className="flex h-9 w-9 items-center justify-center rounded-full bg-accent text-sm font-bold text-bg-raised">
                  {i + 1}
                </div>
                <span className="text-sm font-bold text-fg">
                  Dirigeant {i + 1}
                </span>
                <select
                  value={d.fonction}
                  onChange={(e) =>
                    update(d.id, {
                      fonction: e.target.value as Dirigeant['fonction'],
                    })
                  }
                  className="h-8 rounded-lg border border-border bg-bg-raised px-2 text-xs font-medium"
                >
                  <option value="GERANT">Gerant</option>
                  <option value="CO_GERANT">Co-Gerant</option>
                </select>
                {/* Badges d'etat : visibles d'un coup d'oeil */}
                <div className="flex flex-wrap items-center gap-2">
                  {d.isStatutaire && (
                    <span className="inline-flex items-center gap-1 rounded-full border border-success/40 bg-success/10 px-2.5 py-1 text-[11px] font-bold uppercase text-success">
                      <CheckCircle className="h-3 w-3" /> Statutaire
                    </span>
                  )}
                  {d.isAssociate && (
                    <span className="inline-flex items-center gap-1 rounded-full border border-accent/40 bg-accent/10 px-2.5 py-1 text-[11px] font-bold uppercase text-accent">
                      <UserPlus className="h-3 w-3" /> Aussi associe
                    </span>
                  )}
                </div>
              </div>
              {dirigeants.length > 1 && (
                <button
                  type="button"
                  onClick={() => removeDirigeant(d.id)}
                  className="rounded-lg p-2 text-danger hover:bg-danger/10"
                  title="Retirer ce dirigeant"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              )}
            </div>

            {/* EX5 2026-06-09 — Toggle PHYSIQUE / MORALE en tete de carte (forme la suite). */}
            <div className="border-b border-border bg-bg-raised px-6 py-3">
              <p className="mb-2 text-[11px] font-bold uppercase tracking-wide text-fg-subtle">
                Nature du dirigeant
              </p>
              <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
                <button
                  type="button"
                  role="radio"
                  aria-checked={d.typePersonne === 'PHYSIQUE'}
                  onClick={() => update(d.id, { typePersonne: 'PHYSIQUE' })}
                  className={`flex items-center gap-3 rounded-lg border-2 px-3 py-2.5 text-left transition ${
                    d.typePersonne === 'PHYSIQUE'
                      ? 'border-accent bg-accent/5'
                      : 'border-border bg-bg-overlay hover:border-accent/40'
                  }`}
                >
                  <User className={`h-4 w-4 ${d.typePersonne === 'PHYSIQUE' ? 'text-accent' : 'text-fg-subtle'}`} />
                  <div className="min-w-0">
                    <p className="text-sm font-bold text-fg">Personne physique</p>
                    <p className="text-[11px] text-fg-subtle">Individu titulaire d'une CIN</p>
                  </div>
                </button>
                <button
                  type="button"
                  role="radio"
                  aria-checked={d.typePersonne === 'MORALE'}
                  onClick={() => update(d.id, { typePersonne: 'MORALE' })}
                  className={`flex items-center gap-3 rounded-lg border-2 px-3 py-2.5 text-left transition ${
                    d.typePersonne === 'MORALE'
                      ? 'border-accent bg-accent/5'
                      : 'border-border bg-bg-overlay hover:border-accent/40'
                  }`}
                >
                  <Building2 className={`h-4 w-4 ${d.typePersonne === 'MORALE' ? 'text-accent' : 'text-fg-subtle'}`} />
                  <div className="min-w-0">
                    <p className="text-sm font-bold text-fg">Personne morale</p>
                    <p className="text-[11px] text-fg-subtle">Societe / entite (RC + ICE + IF + representant legal)</p>
                  </div>
                </button>
              </div>
            </div>

            {/* EX4 — Cartes de selection grandes + lisibles, libelles explicites + aide contextuelle. */}
            <div className="grid grid-cols-1 gap-3 border-b border-border bg-bg-raised px-6 py-4 md:grid-cols-2">
              <button
                type="button"
                role="switch"
                aria-checked={d.isStatutaire}
                onClick={() =>
                  update(d.id, { isStatutaire: !d.isStatutaire })
                }
                className={`group flex w-full items-start gap-3 rounded-xl border-2 p-4 text-left transition ${
                  d.isStatutaire
                    ? 'border-success bg-success/5 shadow-sm'
                    : 'border-border bg-bg-overlay hover:border-success/40 hover:bg-success/5'
                }`}
              >
                <div
                  className={`mt-0.5 flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full border-2 ${
                    d.isStatutaire
                      ? 'border-success bg-success'
                      : 'border-border bg-bg-raised'
                  }`}
                >
                  {d.isStatutaire && (
                    <CheckCircle className="h-4 w-4 text-bg-raised" />
                  )}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-bold text-fg">
                    Gerant statutaire
                  </p>
                  <p className="mt-0.5 text-[11px] text-fg-subtle">
                    Nomme directement dans les statuts (pas d'acte separe). Decoche
                    si la nomination se fait par acte poste-statuts.
                  </p>
                </div>
              </button>

              <button
                type="button"
                role="switch"
                aria-checked={!!d.isAssociate}
                onClick={() =>
                  update(d.id, { isAssociate: !d.isAssociate })
                }
                className={`group flex w-full items-start gap-3 rounded-xl border-2 p-4 text-left transition ${
                  d.isAssociate
                    ? 'border-accent bg-accent/5 shadow-sm'
                    : 'border-border bg-bg-overlay hover:border-accent/40 hover:bg-accent/5'
                }`}
                title="Si coche, le dirigeant est aussi associe -- ses infos seront propagees a l'etape Associes."
              >
                <div
                  className={`mt-0.5 flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full border-2 ${
                    d.isAssociate
                      ? 'border-accent bg-accent'
                      : 'border-border bg-bg-raised'
                  }`}
                >
                  {d.isAssociate && (
                    <CheckCircle className="h-4 w-4 text-bg-raised" />
                  )}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-bold text-fg">
                    Egalement associe
                  </p>
                  <p className="mt-0.5 text-[11px] text-fg-subtle">
                    Le dirigeant detient des parts sociales. Son identite sera
                    propagee automatiquement a l'etape Associes.
                    {isSarlAu && (
                      <span className="ml-1 font-medium text-accent">
                        SARL_AU : un seul dirigeant peut etre coche.
                      </span>
                    )}
                  </p>
                </div>
              </button>
            </div>

            <div className="p-6">
              {/* EX5 — Bloc PHYSIQUE : CIN + identite. */}
              {!isMorale && (
              <>
              {/*
               * 2026-06-15 — Assistant d'extraction CIN dedie aux Etapes 5/6.
               * Mode "cin" : recto + verso + toggle [Ancienne | Nouvelle]
               * (AUCUNE option CN). Archive=true + dossierId -> le PDF
               * recto+verso fusionne est range automatiquement dans la Data
               * Room du dossier (DataroomIdentityArchiver). L'ancien bloc
               * d'upload single-file + extraction Tesseract est SUPPRIME
               * (doublon). L'extracteur conserve la saisie manuelle :
               * chaque champ pre-rempli reste editable et l'utilisateur
               * peut ignorer l'assistant et tout saisir a la main.
               */}
              <div className="mb-5">
                {/* 2026-06-19 (Cowork) — Re-hydratation des pièces. Si la CIN
                    est déjà archivée en Data Room on n'affiche PAS l'extracteur
                    par défaut — on indique l'archive existante + bouton
                    "Remplacer" qui rouvre l'extracteur. */}
                {(!d.cinUploaded || !d.cinFileName || replacingCinIds.has(d.id)) && (
                  <IdentityExtractor
                    mode="cin"
                    dossierId={dossierId}
                    onApply={(values, meta) => {
                      applyCinExtraction(d.id, values, meta);
                      setReplacingCinIds((s) => {
                        const next = new Set(s); next.delete(d.id); return next;
                      });
                    }}
                  />
                )}
                {d.cinUploaded && d.cinFileName && (
                  <div className="mt-2 flex items-center justify-between gap-2 rounded-md border border-success/30 bg-success/5 p-2">
                    <p className="flex items-center gap-1.5 text-[11px] text-success">
                      <CheckCircle className="h-3 w-3" />
                      {d.cinFileName}
                    </p>
                    {!replacingCinIds.has(d.id) && (
                      <button
                        type="button"
                        onClick={() => setReplacingCinIds((s) => new Set(s).add(d.id))}
                        className="text-[11px] font-medium text-accent hover:underline"
                      >
                        Remplacer
                      </button>
                    )}
                    {replacingCinIds.has(d.id) && (
                      <button
                        type="button"
                        onClick={() =>
                          setReplacingCinIds((s) => {
                            const next = new Set(s); next.delete(d.id); return next;
                          })
                        }
                        className="text-[11px] text-fg-subtle hover:underline"
                      >
                        Annuler
                      </button>
                    )}
                  </div>
                )}
              </div>

              <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
                <div>
                  <label className="mb-1 block text-xs text-fg-subtle">
                    Civilite
                  </label>
                  <select
                    value={d.civilite}
                    onChange={(e) =>
                      update(d.id, { civilite: e.target.value as 'M' | 'Mme' })
                    }
                    className="h-9 w-full rounded-lg border border-border bg-bg-overlay px-3 text-sm"
                  >
                    <option value="M">Monsieur</option>
                    <option value="Mme">Madame</option>
                  </select>
                </div>
                <Field
                  label="Nom"
                  required
                  value={d.nom}
                  onChange={(e) => update(d.id, { nom: e.target.value })}
                />
                <Field
                  label="Prenom"
                  required
                  value={d.prenom}
                  onChange={(e) => update(d.id, { prenom: e.target.value })}
                />
                <div>
                  <label className="mb-1 block text-xs text-fg-subtle">
                    N° CIN <span className="text-danger">*</span>
                  </label>
                  <input aria-label="N° CIN"
                    type="text"
                    value={d.cinNumero}
                    onChange={(e) =>
                      update(d.id, { cinNumero: e.target.value.toUpperCase() })
                    }
                    placeholder="X 123456"
                    title="Format CIN : 1 a 2 lettres puis 5 a 7 chiffres (ex : X 123456)"
                    aria-invalid={!!cinError}
                    className={`h-9 w-full rounded-lg border bg-bg-overlay px-3 text-sm uppercase ${
                      cinError ? 'border-danger' : 'border-border'
                    }`}
                  />
                  {cinError && (
                    <p className="mt-0.5 text-[11px] font-medium text-danger">
                      {cinError}
                    </p>
                  )}
                </div>
                <Field
                  label="Nationalite"
                  value={d.nationalite}
                  onChange={(e) =>
                    update(d.id, { nationalite: e.target.value })
                  }
                />
                <Field
                  label="Date de naissance"
                  type="date"
                  value={d.dateNaissance}
                  onChange={(e) =>
                    update(d.id, { dateNaissance: e.target.value })
                  }
                />
                {/* 2026-06-11 — Champs ajoutes pour les Statuts (placeholders
                    gerant_lieu_naissance + gerant_piece_validite). */}
                <Field
                  label="Lieu de naissance"
                  value={d.lieuNaissance ?? ''}
                  onChange={(e) =>
                    update(d.id, { lieuNaissance: e.target.value })
                  }
                  placeholder="Ville, pays"
                />
                <Field
                  label="CIN valable jusqu'au"
                  type="date"
                  value={d.pieceValidite ?? ''}
                  onChange={(e) =>
                    update(d.id, { pieceValidite: e.target.value })
                  }
                />
                <div className="md:col-span-3">
                  <label className="mb-1 block text-xs text-fg-subtle">
                    Adresse <span className="text-danger">*</span>
                  </label>
                  <input aria-label="Adresse"
                    type="text"
                    value={d.adresse}
                    onChange={(e) => update(d.id, { adresse: e.target.value })}
                    placeholder="Adresse complete"
                    className="h-9 w-full rounded-lg border border-border bg-bg-overlay px-3 text-sm"
                  />
                </div>
              </div>

              {!d.isStatutaire && (
                <div className="mt-3 flex items-start gap-2 rounded-lg border border-warning/20 bg-warning/10 p-3">
                  <AlertTriangle className="h-4 w-4 flex-shrink-0 text-warning" />
                  <p className="text-xs text-warning">
                    Gerant non statutaire — un acte de nomination separe sera
                    genere a l'etape Statuts.
                  </p>
                </div>
              )}

              <GeranceFields
                dirigeant={d}
                onChange={(patch) => update(d.id, patch)}
              />
              </>
              )}

              {/* EX5 2026-06-09 — Bloc MORALE : entite + representant legal. */}
              {isMorale && (
                <>
                  <MoraleForm
                    dirigeant={d}
                    dossierId={dossierId}
                    onChange={(patch) => update(d.id, patch)}
                    onPieceUploaded={onPieceUploaded}
                  />
                  {/* Une personne morale gérante a elle aussi un mandat. */}
                  <GeranceFields
                    dirigeant={d}
                    onChange={(patch) => update(d.id, patch)}
                  />
                </>
              )}
            </div>
          </div>
        );
      })}

      <button
        type="button"
        onClick={() => setDirigeants([...dirigeants, newDirigeant()])}
        className="flex w-full items-center justify-center gap-2 rounded-lg border-2 border-dashed border-border py-3 text-sm text-accent transition hover:border-accent hover:bg-accent/10"
      >
        <UserPlus className="h-4 w-4" /> Ajouter un dirigeant
      </button>

      {/* Limitation des pouvoirs — clause STATUTAIRE sur la gérance comme organe.
          Elle ne vit pas dans les fiches dirigeants : elle borne ce que la gérance
          peut faire, pas ce qu'un gérant donné peut faire. */}
      <div className="rounded-xl border border-border bg-bg-raised p-5 shadow-sm">
        <label
          htmlFor="limitation-pouvoirs-gerance"
          className="block text-sm font-bold text-fg"
        >
          Limitation des pouvoirs de la gerance{' '}
          <span className="font-normal text-fg-subtle">(optionnel)</span>
        </label>
        <p className="mt-1 text-xs text-fg-subtle">
          Clause des statuts, commune a toute la gerance : &laquo; les actes suivants
          requierent l'accord prealable des associes &raquo;.
        </p>
        <textarea
          id="limitation-pouvoirs-gerance"
          rows={2}
          value={limitationPouvoirs}
          onChange={(ev) => setLimitationPouvoirs(ev.target.value)}
          placeholder="Ex: les actes de disposition superieurs a 100 000 MAD requierent l'accord des associes."
          className="mt-3 w-full resize-none rounded-lg border-2 border-border bg-bg-overlay px-3 py-2 text-sm focus:border-accent focus:outline-none"
        />
      </div>

      {/* 2026-08 (contrat CREATION) — Signature sociale (art. 15). */}
      <div className="rounded-xl border border-border bg-bg-raised p-5 shadow-sm">
        <p className="text-sm font-bold text-fg">Signature sociale (art. 15)</p>
        <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Mode de signature
            </label>
            <select
              value={modeSignature}
              onChange={(ev) => setModeSignature(ev.target.value)}
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            >
              <option value="séparée">Separee (sans limitation)</option>
              <option value="séparée avec plafond">Separee avec plafond</option>
              <option value="conjointe">Conjointe (deux au moins)</option>
            </select>
          </div>
          {modeSignature === 'séparée avec plafond' && (
            <div>
              <label className="mb-1 block text-xs font-medium text-fg">
                Plafond par operation (MAD)
              </label>
              <input aria-label="Plafond par operation (MAD)"
                type="number"
                min={0}
                value={signaturePlafond || ''}
                onChange={(ev) => setSignaturePlafond(Number(ev.target.value))}
                placeholder="Ex: 50000"
                className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
              />
            </div>
          )}
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Signature des actes administratifs
            </label>
            <select
              value={modeSignatureAdmin}
              onChange={(ev) =>
                setModeSignatureAdmin(
                  ev.target.value as 'identique' | 'signature seule',
                )
              }
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            >
              <option value="identique">Identique a la signature sociale</option>
              <option value="signature seule">Signature seule du gerant</option>
            </select>
          </div>
        </div>

        {/* signataires[] repetables */}
        <div className="mt-4">
          <p className="mb-2 text-xs font-medium text-fg">Signataires autorises</p>
          <datalist id={signataireListId}>
            {signataireCandidates.map((c) => (
              <option key={c} value={c} />
            ))}
          </datalist>
          <div className="space-y-2">
            {signataires.map((s, idx) => (
              <div key={s.id} className="grid grid-cols-1 gap-2 md:grid-cols-[1fr_1fr_auto] md:items-end">
                <div>
                  <label className="mb-1 block text-[10px] text-fg-subtle">Nom</label>
                  <input aria-label="Nom"
                    type="text"
                    list={signataireListId}
                    value={s.nom}
                    onChange={(ev) =>
                      setSignataires((arr) =>
                        arr.map((x, i) => (i === idx ? { ...x, nom: ev.target.value } : x)),
                      )
                    }
                    placeholder="Nom du signataire"
                    className="h-9 w-full rounded-lg border border-border bg-bg-overlay px-3 text-sm"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-[10px] text-fg-subtle">Qualite</label>
                  <input aria-label="Qualite"
                    type="text"
                    value={s.qualite}
                    onChange={(ev) =>
                      setSignataires((arr) =>
                        arr.map((x, i) => (i === idx ? { ...x, qualite: ev.target.value } : x)),
                      )
                    }
                    placeholder="Ex: Gerant"
                    className="h-9 w-full rounded-lg border border-border bg-bg-overlay px-3 text-sm"
                  />
                </div>
                <button
                  type="button"
                  onClick={() => setSignataires((arr) => arr.filter((_, i) => i !== idx))}
                  className="h-9 rounded-lg border border-danger/30 px-2 text-danger hover:bg-danger/10"
                  title="Retirer ce signataire"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            ))}
          </div>
          <button
            type="button"
            onClick={() =>
              setSignataires((arr) => [
                ...arr,
                { id: crypto.randomUUID(), nom: '', qualite: 'Gerant' },
              ])
            }
            className="mt-2 flex w-full items-center justify-center gap-2 rounded-lg border-2 border-dashed border-border py-2 text-xs text-accent transition hover:border-accent hover:bg-accent/10"
          >
            <UserPlus className="h-3.5 w-3.5" /> Ajouter un signataire
          </button>
        </div>

        {/* Mandataire (delegation de signature) */}
        <div className="mt-4 rounded-lg border border-border bg-bg-overlay p-3">
          <label className="flex items-center gap-2 text-xs font-bold text-fg">
            <input
              type="checkbox"
              checked={signatureMandataire}
              onChange={(ev) => setSignatureMandataire(ev.target.checked)}
              className="h-4 w-4 accent-accent"
            />
            Signature deleguee a un mandataire
          </label>
          {signatureMandataire && (
            <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
              <div>
                <label className="mb-1 block text-xs text-fg-subtle">Nom du mandataire</label>
                <input aria-label="Nom du mandataire"
                  type="text"
                  value={mandataireNom}
                  onChange={(ev) => setMandataireNom(ev.target.value)}
                  placeholder="Nom du mandataire"
                  className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs text-fg-subtle">
                  Reference de l'acte de delegation
                </label>
                <input aria-label="Reference de l'acte de delegation"
                  type="text"
                  value={mandataireActeDelegation}
                  onChange={(ev) => setMandataireActeDelegation(ev.target.value)}
                  placeholder="Ex: procuration du 01/01/2026"
                  className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
                />
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 2026-06-19 — Commissaire aux comptes (CAC) — optionnel.
          Loi 5-96 Art. 80 : la designation d'un CAC est obligatoire en SARL si
          le CA > 50 MDH ; sinon facultative. On l'expose ici comme conditionnel. */}
      <div className="rounded-xl border border-border bg-bg-raised p-5 shadow-sm">
        <label className="flex items-center gap-2 text-sm font-bold text-fg">
          <input
            type="checkbox"
            checked={cacNomme}
            onChange={(ev) => setCacNomme(ev.target.checked)}
            className="h-4 w-4 accent-accent"
          />
          Commissaire aux comptes designe
        </label>
        <p className="mt-1 text-xs text-fg-subtle">
          Optionnel — obligatoire pour SARL au CA &gt; 50 MDH (Loi 5-96 Art. 80).
        </p>
        {cacNomme && (
          <input
            type="text"
            value={cacNom}
            onChange={(ev) => setCacNom(ev.target.value)}
            placeholder="Nom du commissaire aux comptes"
            aria-label="Nom du commissaire aux comptes"
            className="mt-3 h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
          />
        )}
      </div>

      {/* Fix A4 — jamais de bouton désactivé sans motif affiché. */}
      <BlocageValidation raisons={raisonsBlocage} testId="step5-blocage" />

      <div className="flex items-center justify-end pt-2">
        <button
          type="submit"
          disabled={!canSubmit || saving}
          className={`flex items-center gap-2 rounded-lg px-8 h-12 font-medium transition ${
            canSubmit && !saving
              ? 'bg-accent text-bg-raised hover:bg-accent-hover'
              : 'cursor-not-allowed bg-border text-fg-subtle'
          }`}
        >
          {saving ? 'Enregistrement…' : 'Valider et continuer'}
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>
    </form>
  );
}

function Field({
  label,
  required,
  ...rest
}: { label: string; required?: boolean } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div>
      <label className="mb-1 block text-xs text-fg-subtle">
        {label} {required && <span className="text-danger">*</span>}
      </label>
      {/* `aria-label` derive du libelle : ce champ vit dans des listes (.map),
          ou un `id` statique se dupliquerait et casserait l'association. */}
      <input
        aria-label={label}
        {...rest}
        className="h-9 w-full rounded-lg border border-border bg-bg-overlay px-3 text-sm"
      />
    </div>
  );
}

/**
 * EX5 2026-06-09 — Formulaire pour un dirigeant MORALE.
 * Champs entite (RC/ICE/IF/siege) + uploads (RC + statuts entite) + representant legal physique.
 */
/** Mandat agrégé de la gérance, tel qu'il alimente les variables racine des modèles. */
export interface GeranceAgregee {
  dureeGerance: string;
  remunerationMode: '' | 'non_remunere' | 'decision_collective' | 'montant_fixe';
  remunerationMontant: number;
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

  return {
    dureeGerance: dureeCommune ?? enumerer(source, (d) => dureeMandatLabel(d)),
    remunerationMode: commun((d) => d.remunerationMode) ?? '',
    remunerationMontant: commun((d) => d.remunerationMontant) ?? 0,
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
 * Section UNIQUE « Gérance » d'un dirigeant.
 *
 * Elle remplace les deux sections globales de fin d'étape (« Gouvernance de la
 * gerance » et « Designation & pouvoirs de la gerance »), qui demandaient la durée
 * du mandat DEUX FOIS et ne permettaient qu'un seul jeu de règles pour toute la
 * gérance. Le mode de désignation n'y est pas saisi : il découle de la case
 * « Gerant statutaire » de la fiche, et n'est affiché qu'en rappel.
 */
function GeranceFields({
  dirigeant,
  onChange,
}: {
  dirigeant: Dirigeant;
  onChange: (patch: Partial<Dirigeant>) => void;
}) {
  const d = dirigeant;
  const qui = dirigeantLabel(d, 0);
  return (
    <div
      className="mt-4 rounded-lg border border-accent/30 bg-accent/5 p-4"
      data-testid={`gerance-${d.id}`}
    >
      <p className="text-xs font-bold text-fg">
        Gerance — mandat et pouvoirs <span className="text-danger">*</span>
      </p>
      <p className="mt-0.5 text-[10px] text-fg-subtle">
        Propre a ce dirigeant : deux gerants peuvent avoir des mandats differents.
      </p>

      <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
        {/* Mode de designation — DERIVE de la case « Gerant statutaire », jamais saisi. */}
        <div>
          <label className="mb-1 block text-xs font-medium text-fg">
            Mode de designation
          </label>
          <p
            data-testid={`gerance-mode-${d.id}`}
            className="flex h-9 items-center rounded-lg border border-border bg-bg-overlay px-3 text-sm text-fg-subtle"
          >
            {d.isStatutaire
              ? 'Statutaire — nomme dans les statuts'
              : 'Non statutaire — acte de nomination separe'}
          </p>
          <p className="mt-0.5 text-[10px] text-fg-subtle">
            Suit la case &laquo; Gerant statutaire &raquo; ci-dessus.
          </p>
        </div>

        {/* Duree du mandat */}
        <div>
          <label className="mb-1 block text-xs font-medium text-fg">
            Duree du mandat <span className="text-danger">*</span>
          </label>
          <div className="flex flex-wrap items-center gap-2">
            <select
              value={d.dureeMandatType}
              aria-label={`Duree du mandat de ${qui}`}
              onChange={(e) =>
                onChange({
                  dureeMandatType: e.target.value as 'illimitee' | 'determinee',
                })
              }
              className="h-9 rounded-lg border border-border bg-bg-overlay px-3 text-sm"
            >
              <option value="illimitee">Illimitee (jusqu&apos;a revocation)</option>
              <option value="determinee">Duree determinee</option>
            </select>
            {d.dureeMandatType === 'determinee' && (
              <div className="flex items-center gap-1">
                <input
                  type="number"
                  min={1}
                  value={d.dureeAnnees || ''}
                  aria-label={`Nombre d'annees du mandat de ${qui}`}
                  onChange={(e) => onChange({ dureeAnnees: Number(e.target.value) })}
                  placeholder="Annees"
                  className="h-9 w-24 rounded-lg border border-border bg-bg-overlay px-3 text-sm"
                />
                <span className="text-xs text-fg-subtle">annee(s)</span>
              </div>
            )}
          </div>
        </div>

        {/* Remuneration */}
        <div>
          <label className="mb-1 block text-xs font-medium text-fg">
            Remuneration de la gerance <span className="text-danger">*</span>
          </label>
          <div className="flex flex-wrap items-center gap-2">
            <select
              value={d.remunerationMode}
              aria-label={`Remuneration de ${qui}`}
              onChange={(e) =>
                onChange({
                  remunerationMode: e.target.value as Dirigeant['remunerationMode'],
                })
              }
              className="h-9 rounded-lg border border-border bg-bg-overlay px-3 text-sm"
            >
              <option value="">— Choisir —</option>
              <option value="non_remunere">Fonctions non remunerees</option>
              <option value="decision_collective">
                Fixee par decision collective des associes
              </option>
              <option value="montant_fixe">Montant fixe</option>
            </select>
            {d.remunerationMode === 'montant_fixe' && (
              <div className="flex items-center gap-1">
                <input
                  type="number"
                  min={1}
                  value={d.remunerationMontant || ''}
                  aria-label={`Montant de la remuneration de ${qui} (MAD)`}
                  onChange={(e) =>
                    onChange({ remunerationMontant: Number(e.target.value) })
                  }
                  placeholder="Montant"
                  className="h-9 w-28 rounded-lg border border-border bg-bg-overlay px-3 text-sm"
                />
                <span className="text-xs text-fg-subtle">MAD</span>
              </div>
            )}
          </div>
        </div>

      </div>
    </div>
  );
}

function MoraleForm({
  dirigeant: d,
  dossierId,
  onChange,
  onPieceUploaded,
}: {
  dirigeant: Dirigeant;
  dossierId?: string | null;
  onChange: (patch: Partial<Dirigeant>) => void;
  onPieceUploaded?: (code: string, label: string, file: File) => Promise<void> | void;
}) {
  const rcError = rcInvalidMessage(d.rc ?? '');
  const iceError = iceInvalidMessage(d.ice ?? '');
  const ifError = ifInvalidMessage(d.ifFiscal ?? '');
  // 2026-06-21 (Cowork) — La CIN du representant legal MORALE n'est plus
  // saisie a la main : l'IdentityExtractor capte le numero + archive la
  // piece en Data Room. Plus de validation regex sur un champ texte.

  function uploadRcFor(file: File) {
    onChange({ rcUploaded: true, rcFileName: file.name });
    void onPieceUploaded?.(
      `RC_DIRIGEANT_MORALE_${d.id.slice(0, 8)}`,
      `RC entite dirigeante ${d.denomination || d.id.slice(0, 8)}`,
      file,
    );
  }
  function uploadStatutsFor(file: File) {
    onChange({ statutsEntiteUploaded: true, statutsEntiteFileName: file.name });
    void onPieceUploaded?.(
      `STATUTS_ENTITE_DIRIGEANT_${d.id.slice(0, 8)}`,
      `Statuts entite dirigeante ${d.denomination || d.id.slice(0, 8)}`,
      file,
    );
  }
  // 2026-06-21 (Cowork) — uploadRepCinFor + UploadCard CIN du representant
  // RETIRES : doublon avec IdentityExtractor qui archive deja recto+verso
  // fusionnes en Data Room et pose repCinUploaded via meta.archivedDocumentId.

  return (
    <div className="space-y-5">
      {/* Identite de l'entite morale */}
      <div className="rounded-lg border border-border bg-bg-overlay p-4">
        <p className="mb-3 flex items-center gap-2 text-xs font-bold uppercase text-fg-subtle">
          <Building2 className="h-3.5 w-3.5" /> Identification de l'entite
        </p>
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">
              Denomination / Raison sociale <span className="text-danger">*</span>
            </label>
            <input aria-label="Denomination / Raison sociale"
              type="text"
              value={d.denomination ?? ''}
              onChange={(e) => onChange({ denomination: e.target.value })}
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">
              Forme juridique
            </label>
            <select
              value={d.formeJuridiqueEntite ?? 'SARL'}
              onChange={(e) => onChange({ formeJuridiqueEntite: e.target.value })}
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
            >
              <option value="SARL">SARL</option>
              <option value="SARL_AU">SARL AU</option>
              <option value="SA">SA</option>
              <option value="SAS">SAS</option>
              <option value="SNC">SNC</option>
              <option value="GIE">GIE</option>
              <option value="ASSOC">Association</option>
              <option value="AUTRE">Autre</option>
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">
              RC (n° Registre du commerce) <span className="text-danger">*</span>
            </label>
            <input aria-label="RC (n° Registre du commerce)"
              type="text"
              value={d.rc ?? ''}
              onChange={(e) => onChange({ rc: e.target.value })}
              aria-invalid={!!rcError}
              className={`h-9 w-full rounded-lg border bg-bg-raised px-3 text-sm ${
                rcError ? 'border-danger' : 'border-border'
              }`}
            />
            {rcError && (
              <p className="mt-0.5 text-[11px] font-medium text-danger">{rcError}</p>
            )}
          </div>
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">
              ICE <span className="text-danger">*</span>
            </label>
            <input aria-label="ICE"
              type="text"
              value={d.ice ?? ''}
              onChange={(e) => onChange({ ice: e.target.value.replace(/[^\d]/g, '') })}
              maxLength={15}
              aria-invalid={!!iceError}
              className={`h-9 w-full rounded-lg border bg-bg-raised px-3 text-sm font-mono ${
                iceError ? 'border-danger' : 'border-border'
              }`}
            />
            {iceError && (
              <p className="mt-0.5 text-[11px] font-medium text-danger">{iceError}</p>
            )}
          </div>
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">
              IF (Identifiant Fiscal) <span className="text-danger">*</span>
            </label>
            <input aria-label="IF (Identifiant Fiscal)"
              type="text"
              value={d.ifFiscal ?? ''}
              onChange={(e) => onChange({ ifFiscal: e.target.value.replace(/[^\d]/g, '') })}
              maxLength={8}
              aria-invalid={!!ifError}
              className={`h-9 w-full rounded-lg border bg-bg-raised px-3 text-sm font-mono ${
                ifError ? 'border-danger' : 'border-border'
              }`}
            />
            {ifError && (
              <p className="mt-0.5 text-[11px] font-medium text-danger">{ifError}</p>
            )}
          </div>
          <div className="md:col-span-2">
            <label className="mb-1 block text-xs text-fg-subtle">
              Adresse du siege <span className="text-danger">*</span>
            </label>
            <input aria-label="Adresse du siege"
              type="text"
              value={d.siege ?? ''}
              onChange={(e) => onChange({ siege: e.target.value })}
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
            />
          </div>
        </div>
      </div>

      {/* Justificatifs entite : RC + statuts */}
      <div className="rounded-lg border border-border bg-bg-overlay p-4">
        <p className="mb-3 text-xs font-bold uppercase text-fg-subtle">
          Justificatifs entite (obligatoires)
        </p>
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          <UploadCard
            uploaded={!!d.rcUploaded}
            filename={d.rcFileName}
            label="Modele J / RC recent"
            onFile={uploadRcFor}
          />
          <UploadCard
            uploaded={!!d.statutsEntiteUploaded}
            filename={d.statutsEntiteFileName}
            label="Statuts de l'entite"
            onFile={uploadStatutsFor}
          />
        </div>
      </div>

      {/* Representant legal — personne physique + upload CIN obligatoire */}
      <div className="rounded-lg border border-accent/30 bg-accent/5 p-4">
        <p className="mb-3 flex items-center gap-2 text-xs font-bold uppercase text-accent">
          <User className="h-3.5 w-3.5" /> Representant legal (personne physique)
        </p>
        {/* 2026-06-19 — Representant legal = personne physique : meme assistant
            CIN recto/verso + toggle nouvelle/ancienne que pour un dirigeant PP.
            Aucune option CN. archive=true + dossierId declenche la fusion
            recto+verso et l'archivage automatique en Data Room. */}
        <div className="mb-3">
          <IdentityExtractor
            mode="cin"
            dossierId={dossierId}
            onApply={(values, meta) => {
              const patch: Partial<Dirigeant> = {};
              if (values.nom) patch.repNom = values.nom;
              if (values.prenom) patch.repPrenom = values.prenom;
              if (values.cin) patch.repCin = values.cin.toUpperCase();
              if (values.adresse) patch.repAdresse = values.adresse;
              if (values.date_naissance) patch.repDateNaissance = toIsoDate(values.date_naissance);
              if (values.lieu_naissance) patch.repLieuNaissance = values.lieu_naissance;
              if (values.date_validite) patch.repPieceValidite = toIsoDate(values.date_validite);
              if (values.nationalite) patch.repNationalite = values.nationalite;
              if (values.sexe) {
                patch.repCivilite = values.sexe.toUpperCase() === 'F' ? 'Mme' : 'M';
              }
              // 2026-06-21 (Cowork) — Robustesse validation : des qu'on a un
              // numero CIN extrait, on flag repCinUploaded=true meme sans
              // archivedDocumentId (cas sans dossierId : pas d'archivage Data
              // Room mais OCR reussi). Le numero CIN tient lieu de preuve.
              if (meta?.archivedDocumentId) {
                patch.repCinUploaded = true;
                patch.repCinFileName = `Archive Data Room ${meta.archivedDocumentId.slice(0, 8)}`;
              } else if (values.cin) {
                patch.repCinUploaded = true;
                patch.repCinFileName = `CIN ${values.cin.toUpperCase()}`;
              }
              if (Object.keys(patch).length > 0) onChange(patch);
            }}
          />
        </div>
        {/* 2026-06-21 (Cowork) — UploadCard CIN du representant RETIREE :
            l'IdentityExtractor archive deja la piece. Affichage lecture seule
            pour controle humain de l'OCR. */}
        {d.repCinUploaded && (
          <div className="mb-3 flex items-start gap-2 rounded-md border border-success/30 bg-success/5 p-2 text-xs">
            <CheckCircle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-success" />
            <div className="flex-1">
              <p className="font-semibold text-success">CIN du representant captee</p>
              <p className="text-fg-subtle">
                {d.repPrenom || d.repNom
                  ? `${d.repPrenom ?? ''} ${d.repNom ?? ''}`.trim() + (d.repCin ? ` — ${d.repCin}` : '')
                  : (d.repCin ? `Numero : ${d.repCin}` : 'Donnees extraites')}
                {d.repCinFileName ? ` · ${d.repCinFileName}` : ''}
              </p>
            </div>
          </div>
        )}
        <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">Civilite</label>
            <select
              value={d.repCivilite ?? 'M'}
              onChange={(e) =>
                onChange({ repCivilite: e.target.value as 'M' | 'Mme' })
              }
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
            >
              <option value="M">Monsieur</option>
              <option value="Mme">Madame</option>
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">
              Nom <span className="text-danger">*</span>
            </label>
            <input aria-label="Nom"
              type="text"
              value={d.repNom ?? ''}
              onChange={(e) => onChange({ repNom: e.target.value })}
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">
              Prenom <span className="text-danger">*</span>
            </label>
            <input aria-label="Prenom"
              type="text"
              value={d.repPrenom ?? ''}
              onChange={(e) => onChange({ repPrenom: e.target.value })}
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
            />
          </div>
          {/* 2026-06-21 (Cowork) — Input CIN representant RETIRE :
              l'IdentityExtractor capte le numero (values.cin -> repCin).
              Le numero est visible dans la banniere "CIN captee" ci-dessus
              pour controle humain. */}
          {/* 2026-06-11 — Champs ajoutes : Adresse, Date+Lieu naissance,
              Nationalite, CIN valable jusqu'au + Qualite. */}
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">Nationalite</label>
            <input aria-label="Nationalite"
              type="text"
              value={d.repNationalite ?? ''}
              onChange={(e) => onChange({ repNationalite: e.target.value })}
              placeholder="Marocaine"
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">Date de naissance</label>
            <input aria-label="Date de naissance"
              type="date"
              value={d.repDateNaissance ?? ''}
              onChange={(e) => onChange({ repDateNaissance: e.target.value })}
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">Lieu de naissance</label>
            <input aria-label="Lieu de naissance"
              type="text"
              value={d.repLieuNaissance ?? ''}
              onChange={(e) => onChange({ repLieuNaissance: e.target.value })}
              placeholder="Ville, pays"
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">CIN valable jusqu'au</label>
            <input aria-label="CIN valable jusqu'au"
              type="date"
              value={d.repPieceValidite ?? ''}
              onChange={(e) => onChange({ repPieceValidite: e.target.value })}
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
            />
          </div>
          <div className="md:col-span-3">
            <label className="mb-1 block text-xs text-fg-subtle">
              Adresse <span className="text-danger">*</span>
            </label>
            <input aria-label="Adresse"
              type="text"
              value={d.repAdresse ?? ''}
              onChange={(e) => onChange({ repAdresse: e.target.value })}
              placeholder="Adresse complete"
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
            />
          </div>
          <div className="md:col-span-3">
            <label className="mb-1 block text-xs text-fg-subtle">
              Qualite <span className="text-danger">*</span>
            </label>
            <input aria-label="Qualite"
              type="text"
              value={d.repQualite ?? ''}
              onChange={(e) => onChange({ repQualite: e.target.value })}
              placeholder="Gerant / President / Mandataire..."
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
            />
          </div>
        </div>
      </div>
    </div>
  );
}

/** EX5 — Carte d'upload generique (RC, statuts entite). */
function UploadCard({
  uploaded,
  filename,
  label,
  onFile,
}: {
  uploaded: boolean;
  filename?: string;
  label: string;
  onFile: (file: File) => void;
}) {
  if (uploaded) {
    return (
      <div className="flex items-center gap-2 rounded-lg border border-success bg-success/10 p-3">
        <FileText className="h-4 w-4 text-success" />
        <span className="flex-1 truncate text-xs">{filename ?? label}</span>
        <CheckCircle className="h-4 w-4 text-success" />
        <label className="cursor-pointer text-[11px] text-accent hover:underline">
          Remplacer
          <input
            type="file"
            accept="image/*,application/pdf"
            onChange={(ev) => ev.target.files?.[0] && onFile(ev.target.files[0])}
            className="hidden"
          />
        </label>
      </div>
    );
  }
  return (
    <label className="block cursor-pointer rounded-lg border-2 border-dashed border-danger/40 p-4 text-center transition hover:border-danger hover:bg-danger/5">
      <Upload className="mx-auto mb-1 h-5 w-5 text-danger" />
      <p className="text-xs font-semibold text-fg">
        {label} <span className="text-danger">*</span>
      </p>
      <input
        type="file"
        accept="image/*,application/pdf"
        onChange={(ev) => ev.target.files?.[0] && onFile(ev.target.files[0])}
        className="hidden"
      />
    </label>
  );
}
