import { useId, useState } from 'react';
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
import { TypePieceProposee, ValeurProposee } from '../../../components/workflow/ValeurProposee';
import { libelleNumeroPiece, pieceInvalidMessage, pieceSansExtraction } from './pieceIdentite';
import { IdentityExtractor } from '../../../components/identity/IdentityExtractor';
import { toIsoDate } from '../../../types/identity';
import { useStepAutosave } from '../useStepAutosave';
import type { DirtyGetter } from '../useWorkflow';
import { BlocageValidation } from '../../../components/workflow/BlocageValidation';
import { ajouterExtraits, retirerExtraits } from '../provenance';
import { AIDE_SIGNATAIRES } from '../../../lib/usagesCabinet';
import { InfoBulle, TexteAide } from '../../../components/ui/Aide';
import {
  agregerGerance,
  dirigeantLabel,
  unSeulAssocie,
  type Dirigeant,
} from './geranceDirigeants';

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



function newDirigeant(): Dirigeant {
  return {
    id: crypto.randomUUID(),
    typePersonne: 'PHYSIQUE',
    civilite: 'M',
    nom: '',
    prenom: '',
    cinNumero: '',
    nationalite: '', // L4a (E2) : proposee a l'ecran, jamais supposee
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
    repQualite: 'Gérant',
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
    return 'Numéro de CIN invalide (ex. : X 123456).';
  }
  return null;
}

/** EX5 — Validation ICE marocain : 15 chiffres. */
function iceInvalidMessage(value: string): string | null {
  if (!value) return null;
  const v = value.replace(/[\s-]/g, '');
  if (!/^\d{15}$/.test(v)) {
    return 'ICE invalide : il compte 15 chiffres.';
  }
  return null;
}

/** EX5 — Validation RC : nombre simple (1 a 8 chiffres). */
function rcInvalidMessage(value: string): string | null {
  if (!value) return null;
  const v = value.replace(/\s+/g, '');
  if (!/^\d{1,8}$/.test(v)) {
    return 'Numéro de RC invalide : chiffres uniquement.';
  }
  return null;
}

/** EX5 — Validation IF (Identifiant Fiscal) : 7 a 8 chiffres. */
function ifInvalidMessage(value: string): string | null {
  if (!value) return null;
  const v = value.replace(/\s+/g, '');
  if (!/^\d{7,8}$/.test(v)) {
    return 'Identifiant fiscal invalide : 7 ou 8 chiffres.';
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

  /**
   * RG-C15 (SARL_AU) — Quand l'utilisateur coche `isAssociate=true` sur un
   * dirigeant, on decoche automatiquement tous les autres. Cas oppose
   * (decochage) laisse l'etat tel quel : 0 ou 1 dirigeant peut etre associe.
   *
   * L6 : la regle s'applique dans le gestionnaire (`update`), qui garde le dirigeant
   * QUE L'ON VIENT DE COCHER (avant, un effet gardait le dernier de la liste, meme
   * quand on cochait le premier). Garde-fou au rendu pour une liste rechargee ou une
   * forme qui passe en SARL AU : on garde le dernier coche, comme avant.
   */
  if (isSarlAu && dirigeants.filter((d) => d.isAssociate).length > 1) {
    setDirigeants(unSeulAssocie(dirigeants));
  }

  /**
   * Lot L3 : `extraction` vrai quand le patch vient de la lecture d'une piece confirmee
   * (provenance EXTRAITE) ; sinon, les champs touches redeviennent des saisies.
   */
  function update(id: string, patch: Partial<Dirigeant>, extraction = false) {
    setDirigeants((arr) =>
      arr.map((d) => {
        if (d.id !== id) return d;
        const champs = Object.keys(patch);
        const extraits = extraction
          ? ajouterExtraits(d._extraits, champs)
          : retirerExtraits(d._extraits, champs);
        return { ...d, ...patch, ...(extraits ? { _extraits: extraits } : {}) };
      }),
    );
    if (isSarlAu && patch.isAssociate === true) {
      setDirigeants((arr) => unSeulAssocie(arr, id));
    }
  }

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
    if (Object.keys(patch).length > 0) update(id, patch, true);
    if (meta?.archivedDocumentId) {
      update(id, {
        cinUploaded: true,
        cinFileName: 'Pièce d’identité archivée dans la Data Room du dossier',
      });
    }
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
        !pieceInvalidMessage(d.pieceType, d.cinNumero) &&
        // Lot 2 (2026-09-07) — l'acte de nomination du gerant imprime
        // $GERANT_DATE_NAISSANCE : sans elle, l'acte sortait avec un trou
        // (« né le , demeurant à… »). Une date de naissance manquante dans un
        // acte est une lacune de fond, pas de forme.
        d.dateNaissance,
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
        if (!importMode && !d.repCinUploaded) manques.push('CIN du représentant lue (bouton « Extraire »)');
        if (!importMode && !d.rcUploaded) manques.push('modèle J ou extrait du RC de la société');
        if (!importMode && !d.statutsEntiteUploaded) manques.push('statuts de la société');
      } else {
        if (!d.nom) manques.push('nom');
        if (!d.prenom) manques.push('prénom');
        if (!d.pieceType) manques.push('type de pièce d’identité');
        if (!d.cinNumero) manques.push(libelleNumeroPiece(d.pieceType).replace('N°', 'n°'));
        else if (pieceInvalidMessage(d.pieceType, d.cinNumero)) manques.push(`${libelleNumeroPiece(d.pieceType).replace('N°', 'n°')} valide`);
        if (!d.adresse) manques.push('adresse');
        if (!d.nationalite?.trim()) manques.push('nationalité');
        if (!d.dateNaissance) {
          manques.push('date de naissance (imprimée dans l’acte de nomination)');
        }
        // LE piège : le fichier joint ne suffit pas, il faut lancer l'extraction.
        if (!importMode && !d.cinUploaded) {
          manques.push('CIN lue : joindre le fichier ne suffit pas, cliquez sur « Extraire »');
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
      <div className="space-y-3 rounded-xl border border-border bg-accent/10 p-4 text-xs text-fg">
        <h3 className="text-base font-bold text-fg">La gérance de la société</h3>
        {/* Lot 2 (2026-09-07) — le texte disait « aide optionnelle » alors que
            l'etape BLOQUE tant que l'extraction n'a pas ete lancee. L'un des
            deux mentait ; c'est celui-ci, puisque la regle metier veut que la
            CIN soit lue, pas seulement jointe. */}
        <p>
          Identifiez le ou les gérants de la société. Au moins un gérant est
          requis. <strong>Pour chaque dirigeant, la CIN doit être jointe puis
          lue</strong> : importez le recto, cliquez sur « Extraire »,
          puis sur « Appliquer au formulaire ». Les champs préremplis
          restent modifiables à la main.
        </p>
        {isSarlAu && (
          <p className="font-medium text-accent">
            SARL AU : un seul dirigeant peut être coché comme « Également associé » ;
            il est repris automatiquement à l’étape des associés.
          </p>
        )}
        <TexteAide cle={importMode ? 'import-etape-gerance' : 'creation-etape-5'} titre="Ce qu’il faut faire">
          Pour chaque gérant : son identité, son mandat (durée et rémunération) et, s’il
          est aussi associé, la case « Également associé ». Puis, pour toute la gérance :
          la limitation éventuelle des pouvoirs et la signature sociale. Ce qui manque est
          listé au-dessus du bouton « Valider et continuer ». L’étape suivante porte sur
          les associés et la répartition du capital.
        </TexteAide>
      </div>

      {dirigeants.map((d, i) => {
        const cinError = pieceInvalidMessage(d.pieceType, d.cinNumero);
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
                  aria-label={`Fonction du dirigeant ${i + 1}`}
                  value={d.fonction}
                  onChange={(e) =>
                    update(d.id, {
                      fonction: e.target.value as Dirigeant['fonction'],
                    })
                  }
                  className="h-8 rounded-lg border border-border bg-bg-raised px-2 text-xs font-medium"
                >
                  <option value="GERANT">Gérant</option>
                  <option value="CO_GERANT">Cogérant</option>
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
                      <UserPlus className="h-3 w-3" /> Aussi associé
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
                    <p className="text-[11px] text-fg-subtle">Une personne, titulaire d’une pièce d’identité</p>
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
                    <p className="text-[11px] text-fg-subtle">Une société (RC, ICE, IF et représentant légal)</p>
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
                    Gérant statutaire
                  </p>
                  <p className="mt-0.5 text-[11px] text-fg-subtle">
                    Nommé directement dans les statuts (pas d’acte séparé). Décochez
                    si la nomination se fait par un acte distinct des statuts.
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
                title="Cochée, le dirigeant est aussi associé : son identité est reprise à l’étape des associés."
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
                    Également associé
                  </p>
                  <p className="mt-0.5 text-[11px] text-fg-subtle">
                    Le dirigeant détient des parts sociales. Son identité sera
                    reprise automatiquement à l’étape des associés.
                    {isSarlAu && (
                      <span className="ml-1 font-medium text-accent">
                        SARL AU : un seul dirigeant peut être coché.
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
                {/* L4a (E3) : passeport ou carte de sejour, simplement joints (pas de lecture automatique). */}
                {pieceSansExtraction(d.pieceType) && (!d.cinUploaded || replacingCinIds.has(d.id)) && (
                  <label className="mb-3 block text-xs font-medium text-fg">
                    Joindre la pièce d’identité ({d.pieceType}) *
                    <input
                      type="file"
                      accept="application/pdf,image/*"
                      className="mt-1 block text-xs"
                      onChange={(e) => {
                        const file = e.target.files?.[0];
                        if (!file) return;
                        update(d.id, { cinUploaded: true, cinFileName: file.name });
                        void onPieceUploaded?.(
                          `PIECE_IDENTITE_DIRIGEANT_${d.id.slice(0, 8)}`,
                          `${d.pieceType} de ${[d.prenom, d.nom].filter(Boolean).join(' ') || 'du dirigeant'}`,
                          file,
                        );
                        setReplacingCinIds((st) => { const next = new Set(st); next.delete(d.id); return next; });
                      }}
                    />
                  </label>
                )}
                {!pieceSansExtraction(d.pieceType) && (!d.cinUploaded || !d.cinFileName || replacingCinIds.has(d.id)) && (
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
                    Civilité
                  </label>
                  <select
                    aria-label="Civilité"
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
                  label="Prénom"
                  required
                  value={d.prenom}
                  onChange={(e) => update(d.id, { prenom: e.target.value })}
                />
                <TypePieceProposee
                  id={`dirigeant-piece-type-${d.id}`}
                  valeur={d.pieceType || undefined}
                  onChange={(v) => update(d.id, { pieceType: v })}
                />
                <div>
                  <label className="mb-1 flex items-center gap-1 text-xs text-fg-subtle">
                    {libelleNumeroPiece(d.pieceType)} <span className="text-danger">*</span>
                    <InfoBulle
                      libelle="Format du numéro"
                      texte="CIN marocaine : une ou deux lettres puis 5 à 7 chiffres (ex. : X 123456). Le numéro est lu sur la pièce par « Extraire » ; corrigez-le si la lecture est inexacte."
                    />
                  </label>
                  <input aria-label="N° CIN"
                    type="text"
                    value={d.cinNumero}
                    onChange={(e) =>
                      update(d.id, { cinNumero: e.target.value.toUpperCase() })
                    }
                    placeholder="X 123456"
                    title="CIN : une ou deux lettres puis 5 à 7 chiffres (ex. : X 123456)"
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
                <ValeurProposee
                  id={`dirigeant-nationalite-${d.id}`}
                  label="Nationalité"
                  proposition="marocaine"
                  valeur={d.nationalite || undefined}
                  onChange={(v) => update(d.id, { nationalite: v })}
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
                  label="Pièce valable jusqu’au"
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
                    placeholder="Adresse complète"
                    className="h-9 w-full rounded-lg border border-border bg-bg-overlay px-3 text-sm"
                  />
                </div>
              </div>

              {!d.isStatutaire && (
                <div className="mt-3 flex items-start gap-2 rounded-lg border border-warning/20 bg-warning/10 p-3">
                  <AlertTriangle className="h-4 w-4 flex-shrink-0 text-warning" />
                  <p className="text-xs text-warning">
                    Gérant non statutaire : un acte de nomination séparé sera
                    généré avec les statuts.
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
          Limitation des pouvoirs de la gérance{' '}
          <span className="font-normal text-fg-subtle">(facultatif)</span>
        </label>
        <p className="mt-1 text-xs text-fg-subtle">
          Clause des statuts, commune à toute la gérance : « les actes suivants
          requièrent l’accord préalable des associés ». Laissez vide s’il n’y en a pas.
        </p>
        <textarea
          id="limitation-pouvoirs-gerance"
          rows={2}
          value={limitationPouvoirs}
          onChange={(ev) => setLimitationPouvoirs(ev.target.value)}
          placeholder="Ex. : les actes de disposition supérieurs à 100 000 MAD."
          className="mt-3 w-full resize-none rounded-lg border-2 border-border bg-bg-overlay px-3 py-2 text-sm focus:border-accent focus:outline-none"
        />
      </div>

      {/* 2026-08 (contrat CREATION) — Signature sociale (art. 15). */}
      <div className="rounded-xl border border-border bg-bg-raised p-5 shadow-sm">
        <p className="flex items-center gap-1 text-sm font-bold text-fg">
          Signature sociale (art. 15)
          <InfoBulle
            libelle="Qu’est-ce que la signature sociale ?"
            texte="Elle fixe, dans les statuts, comment les gérants engagent la société : chacun seul (séparée), chacun seul jusqu’à un plafond par opération, ou à deux au moins (conjointe). Les signataires autorisés sont ceux qui signent pour la société."
          />
        </p>
        <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Mode de signature
            </label>
            <select
              aria-label="Mode de signature"
              value={modeSignature}
              onChange={(ev) => setModeSignature(ev.target.value)}
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            >
              <option value="séparée">Séparée (sans limitation)</option>
              <option value="séparée avec plafond">Séparée avec plafond</option>
              <option value="conjointe">Conjointe (deux au moins)</option>
            </select>
          </div>
          {modeSignature === 'séparée avec plafond' && (
            <div>
              <label className="mb-1 block text-xs font-medium text-fg">
                Plafond par opération (MAD)
              </label>
              <input aria-label="Plafond par opération (MAD)"
                type="number"
                min={0}
                value={signaturePlafond || ''}
                onChange={(ev) => setSignaturePlafond(Number(ev.target.value))}
                placeholder="Ex. : 50000"
                className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
              />
            </div>
          )}
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Signature des actes administratifs
            </label>
            <select
              aria-label="Signature des actes administratifs"
              value={modeSignatureAdmin}
              onChange={(ev) =>
                setModeSignatureAdmin(
                  ev.target.value as 'identique' | 'signature seule',
                )
              }
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            >
              <option value="identique">Identique à la signature sociale</option>
              <option value="signature seule">Signature seule du gérant</option>
            </select>
          </div>
        </div>

        {/* signataires[] repetables */}
        <div className="mt-4">
          <p className="mb-2 text-xs font-medium text-fg">Signataires autorisés</p>
          {/* Lot L4d (E4) : regle du cabinet (directeur, 2026-10-11), dite a l'ecran. */}
          <TexteAide cle="signataires-defaut" titre="Signataires">
            <p data-testid="aide-signataires">{AIDE_SIGNATAIRES}</p>
          </TexteAide>
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
                  <input aria-label={`Nom du signataire ${idx + 1}`}
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
                  <label className="mb-1 block text-[10px] text-fg-subtle">Qualité</label>
                  <input aria-label={`Qualité du signataire ${idx + 1}`}
                    type="text"
                    value={s.qualite}
                    onChange={(ev) =>
                      setSignataires((arr) =>
                        arr.map((x, i) => (i === idx ? { ...x, qualite: ev.target.value } : x)),
                      )
                    }
                    placeholder="Ex. : gérant"
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
                { id: crypto.randomUUID(), nom: '', qualite: 'Gérant' },
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
            Signature déléguée à un mandataire
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
                  Référence de l’acte de délégation
                </label>
                <input aria-label="Référence de l’acte de délégation"
                  type="text"
                  value={mandataireActeDelegation}
                  onChange={(ev) => setMandataireActeDelegation(ev.target.value)}
                  placeholder="Ex. : procuration du 01/01/2026"
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
          Commissaire aux comptes désigné
        </label>
        <p className="mt-1 text-xs text-fg-subtle">
          Facultatif ; obligatoire pour une SARL dont le chiffre d’affaires dépasse
          50 millions de dirhams (loi 5-96, article 80).
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
        Gérance : mandat et pouvoirs <span className="text-danger">*</span>
      </p>
      <p className="mt-0.5 text-[10px] text-fg-subtle">
        Propre à ce dirigeant : deux gérants peuvent avoir des mandats différents.
      </p>

      <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
        {/* Mode de designation — DERIVE de la case « Gerant statutaire », jamais saisi. */}
        <div>
          <label className="mb-1 block text-xs font-medium text-fg">
            Mode de désignation
          </label>
          <p
            data-testid={`gerance-mode-${d.id}`}
            className="flex h-9 items-center rounded-lg border border-border bg-bg-overlay px-3 text-sm text-fg-subtle"
          >
            {d.isStatutaire
              ? 'Statutaire : nommé dans les statuts'
              : 'Non statutaire : acte de nomination séparé'}
          </p>
          <p className="mt-0.5 text-[10px] text-fg-subtle">
            Suit la case « Gérant statutaire » ci-dessus.
          </p>
        </div>

        {/* Duree du mandat */}
        <div>
          <label className="mb-1 block text-xs font-medium text-fg">
            Durée du mandat <span className="text-danger">*</span>
          </label>
          <div className="flex flex-wrap items-center gap-2">
            <select
              value={d.dureeMandatType}
              aria-label={`Durée du mandat de ${qui}`}
              onChange={(e) =>
                onChange({
                  dureeMandatType: e.target.value as 'illimitee' | 'determinee',
                })
              }
              className="h-9 rounded-lg border border-border bg-bg-overlay px-3 text-sm"
            >
              <option value="illimitee">Illimitée (jusqu’à révocation)</option>
              <option value="determinee">Durée déterminée</option>
            </select>
            {d.dureeMandatType === 'determinee' && (
              <div className="flex items-center gap-1">
                <input
                  type="number"
                  min={1}
                  value={d.dureeAnnees || ''}
                  aria-label={`Nombre d’années du mandat de ${qui}`}
                  onChange={(e) => onChange({ dureeAnnees: Number(e.target.value) })}
                  placeholder="Années"
                  className="h-9 w-24 rounded-lg border border-border bg-bg-overlay px-3 text-sm"
                />
                <span className="text-xs text-fg-subtle">année(s)</span>
              </div>
            )}
          </div>
        </div>

        {/* Remuneration */}
        <div>
          <label className="mb-1 block text-xs font-medium text-fg">
            Rémunération de la gérance <span className="text-danger">*</span>
          </label>
          <div className="flex flex-wrap items-center gap-2">
            <select
              value={d.remunerationMode}
              aria-label={`Rémunération de ${qui}`}
              onChange={(e) =>
                onChange({
                  remunerationMode: e.target.value as Dirigeant['remunerationMode'],
                })
              }
              className="h-9 rounded-lg border border-border bg-bg-overlay px-3 text-sm"
            >
              <option value="">— Choisir —</option>
              <option value="non_remunere">Fonctions non rémunérées</option>
              <option value="decision_collective">
                Fixée par décision collective des associés
              </option>
              <option value="montant_fixe">Montant fixe</option>
            </select>
            {d.remunerationMode === 'montant_fixe' && (
              <div className="flex items-center gap-1">
                <input
                  type="number"
                  min={1}
                  value={d.remunerationMontant || ''}
                  aria-label={`Montant de la rémunération de ${qui} (MAD)`}
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

/**
 * EX5 2026-06-09 — Formulaire pour un dirigeant MORALE.
 * Champs entite (RC/ICE/IF/siege) + uploads (RC + statuts entite) + representant legal physique.
 */
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
      `RC de la société gérante ${d.denomination || ''}`.trim(),
      file,
    );
  }
  function uploadStatutsFor(file: File) {
    onChange({ statutsEntiteUploaded: true, statutsEntiteFileName: file.name });
    void onPieceUploaded?.(
      `STATUTS_ENTITE_DIRIGEANT_${d.id.slice(0, 8)}`,
      `Statuts de la société gérante ${d.denomination || ''}`.trim(),
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
          <Building2 className="h-3.5 w-3.5" /> Identification de la société gérante
        </p>
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">
              Dénomination (raison sociale) <span className="text-danger">*</span>
            </label>
            <input aria-label="Dénomination (raison sociale)"
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
              aria-label="Forme juridique de la société gérante"
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
              Numéro du registre du commerce (RC) <span className="text-danger">*</span>
            </label>
            <input aria-label="Numéro du registre du commerce (RC)"
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
              Identifiant fiscal (IF) <span className="text-danger">*</span>
            </label>
            <input aria-label="Identifiant fiscal (IF)"
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
              Adresse du siège <span className="text-danger">*</span>
            </label>
            <input aria-label="Adresse du siège"
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
          Justificatifs de la société gérante (obligatoires)
        </p>
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          <UploadCard
            uploaded={!!d.rcUploaded}
            filename={d.rcFileName}
            label="Modèle J ou extrait récent du RC"
            onFile={uploadRcFor}
          />
          <UploadCard
            uploaded={!!d.statutsEntiteUploaded}
            filename={d.statutsEntiteFileName}
            label="Statuts de la société"
            onFile={uploadStatutsFor}
          />
        </div>
      </div>

      {/* Representant legal — personne physique + upload CIN obligatoire */}
      <div className="rounded-lg border border-accent/30 bg-accent/5 p-4">
        <p className="mb-3 flex items-center gap-2 text-xs font-bold uppercase text-accent">
          <User className="h-3.5 w-3.5" /> Représentant légal (personne physique)
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
                patch.repCinFileName = 'archivée dans la Data Room du dossier';
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
              <p className="font-semibold text-success">CIN du représentant lue</p>
              <p className="text-fg-subtle">
                {d.repPrenom || d.repNom
                  ? `${d.repPrenom ?? ''} ${d.repNom ?? ''}`.trim() + (d.repCin ? ` — ${d.repCin}` : '')
                  : (d.repCin ? `Numéro : ${d.repCin}` : 'Données lues sur la pièce')}
                {d.repCinFileName ? ` · ${d.repCinFileName}` : ''}
              </p>
            </div>
          </div>
        )}
        <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">Civilité</label>
            <select
              aria-label="Civilité du représentant"
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
            <input aria-label="Nom du représentant"
              type="text"
              value={d.repNom ?? ''}
              onChange={(e) => onChange({ repNom: e.target.value })}
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">
              Prénom <span className="text-danger">*</span>
            </label>
            <input aria-label="Prénom du représentant"
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
            <label className="mb-1 block text-xs text-fg-subtle">Nationalité</label>
            <input aria-label="Nationalité du représentant"
              type="text"
              value={d.repNationalite ?? ''}
              onChange={(e) => onChange({ repNationalite: e.target.value })}
              placeholder="Marocaine"
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">Date de naissance</label>
            <input aria-label="Date de naissance du représentant"
              type="date"
              value={d.repDateNaissance ?? ''}
              onChange={(e) => onChange({ repDateNaissance: e.target.value })}
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">Lieu de naissance</label>
            <input aria-label="Lieu de naissance du représentant"
              type="text"
              value={d.repLieuNaissance ?? ''}
              onChange={(e) => onChange({ repLieuNaissance: e.target.value })}
              placeholder="Ville, pays"
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
            />
          </div>
          <div>
            <label className="mb-1 block text-xs text-fg-subtle">CIN valable jusqu’au</label>
            <input aria-label="CIN du représentant valable jusqu’au"
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
            <input aria-label="Adresse du représentant"
              type="text"
              value={d.repAdresse ?? ''}
              onChange={(e) => onChange({ repAdresse: e.target.value })}
              placeholder="Adresse complète"
              className="h-9 w-full rounded-lg border border-border bg-bg-raised px-3 text-sm"
            />
          </div>
          <div className="md:col-span-3">
            <label className="mb-1 block text-xs text-fg-subtle">
              Qualité <span className="text-danger">*</span>
            </label>
            <input aria-label="Qualité du représentant"
              type="text"
              value={d.repQualite ?? ''}
              onChange={(e) => onChange({ repQualite: e.target.value })}
              placeholder="Gérant, président, mandataire…"
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
