import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertCircle,
  AlertTriangle,
  CheckCircle,
  ChevronDown,
  ChevronRight,
  ChevronUp,
  Download,
  Eye,
  FileText,
  Loader,
  Maximize2,
  Pencil,
  RefreshCcw,
  Sparkles,
  X,
} from 'lucide-react';
import {
  generateDocument,
  listTemplatesForWorkflow,
  type TemplateInfo,
} from '../../../services/workflowDocumentService';
import { DocumentEditor } from '../../../components/document/DocumentEditor';
import { CollaboraEditor } from '../../../components/document/CollaboraEditor';
import { dataroomService } from '../../../services/dataroom.service';
import { REPRISES_AUTOMATIQUES, champsParDocument } from './documents-creation';
import { dureeMandatLabel } from './Step5Dirigeants';
import { buildDocFilename } from '../../../components/workflow/workflowFilename';
import type { DocumentType } from '../../../types/dataroom';
import { formatObjetSocial } from '../objetSocial';

/**
 * 2026-06-09 (fix LLM-off) — Plus AUCUN passage par LLM (Groq / Ollama / autre)
 * pour l'assemblage des Statuts. La generation est purement deterministe :
 * remplissage du gabarit .docx directeur via le DocxTemplateEngine. Cela garantit
 * que la mise en page Word est preservee bit-pour-bit et que la sortie est
 * reproductible. Le LLM reste autorise uniquement pour l'OCR (image -> champs),
 * jamais pour generer le contenu d'un document.
 */

interface Props {
  existing?: Record<string, unknown>;
  data: Record<string, Record<string, unknown>>;
  saving: boolean;
  onSubmit: (payload: Record<string, unknown>) => Promise<void>;
  /** C4 2026-06-21 — navigation vers une etape pour completer un champ manquant. */
  onNavigate?: (step: number) => void;
  /**
   * RG transverse 2026-06-05 : depot auto dataroom des documents GENERES.
   * Le parent capture (templateCode, blob, filename) et delegue au service
   * dataroom (ou bufferise jusqu'a finalisation du dossier).
   */
  onDocumentGenerated?: (
    templateCode: string,
    title: string,
    blob: Blob,
    filename: string,
  ) => Promise<void> | void;
  /**
   * Étape 7 (persistance 2026-08) — dossier rattaché au ticket. Sert à
   * ré-hydrater les blobs des documents générés/validés depuis la Data Room
   * (priorité) au montage de l'étape, pour que l'aperçu/édition survivent à la
   * navigation Step7 → autre étape → Step7.
   */
  dossierId?: string | null;
  /**
   * Lot 2 (2026-09-07) — opération porteuse. Un document généré est persisté
   * comme BROUILLON rattaché à ce ticket dès sa génération : c'est ce qui le
   * fait survivre à un rechargement de page comme à un aller-retour entre
   * étapes. Sans ticket, on retombe sur l'ancien comportement (mémoire seule).
   */
  ticketId?: string | null;
  /**
   * 2026-08-12 (consultation lecture seule) — quand `true` (ticket clôturé /
   * annulé / workflow terminé) : documents affichés en **Aperçu + Télécharger**
   * uniquement (ni Générer, ni Régénérer, ni Éditer, ni Valider), aucune
   * génération (y compris le fallback de ré-hydratation), aucun dépôt.
   */
  readOnly?: boolean;
}

interface DocState {
  generating: boolean;
  generated: boolean;
  filename?: string;
  blob?: Blob;
  documentId?: string;
  validated: boolean;
  error: string | null;
  /**
   * Étape 7 (noms propres 2026-08) — Numéro de version du document. `1` (ou
   * absent) = première génération, SANS suffixe de version dans le nom de
   * fichier. Incrémenté à chaque régénération OU édition/sauvegarde → le nom
   * téléchargé porte alors « - v2 », « - v3 »… (cf. {@link cleanDocFilename}).
   */
  version?: number;
  /**
   * Lot 2 (2026-09-07) — `documentId` designe un BROUILLON (document persiste
   * mais pas encore valide). Sert a promouvoir ce document exact a la
   * validation, plutot que de re-televerser le binaire.
   */
  brouillon?: boolean;
  /**
   * Lot 3 (2026-09-07) — date de la derniere edition manuelle dans l'editeur
   * bureautique. Sert a NOMMER ce qu'une regeneration ferait perdre : un
   * avertissement generique se clique sans se lire.
   */
  editeManuellementAt?: string | null;
}

function freshState(): DocState {
  return {
    generating: false,
    generated: false,
    validated: false,
    error: null,
  };
}

function triggerDownload(blob: Blob, filename: string) {
  const url = window.URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => window.URL.revokeObjectURL(url), 0);
}

/**
 * Codes templates "principaux" par forme juridique (post-2026-06-09).
 * Le moteur de generation utilise le template DOCX manifest L3.
 */
const STATUTS_BY_FORME: Record<'SARL' | 'SARL_AU', string> = {
  SARL: 'STATUTS_SARL_DIRECTEUR',
  SARL_AU: 'STATUTS_SARL_AU_DIRECTEUR',
};

/**
 * Code template de l'acte de nomination du gerant selectionne dynamiquement
 * (variante SARL vs SARL_AU). N'est inclus dans l'allowlist que si au moins
 * un gerant n'est PAS statutaire (RG transverse 2026-06-09).
 */
const ACTE_BY_FORME: Record<'SARL' | 'SARL_AU', string> = {
  SARL: 'ACTE_NOMINATION_GERANT_DIRECTEUR',
  SARL_AU: 'ACTE_NOMINATION_GERANT_DIRECTEUR',
};

/**
 * Allowlist des templates exposes a Step7 (par forme juridique + presence
 * de gerant non statutaire). Tous les autres templates du manifest restent
 * accessibles cote backend mais ne sont pas pousses dans l'UI Step7 pour
 * eviter le bruit (decision 2026-06-09).
 */
const TEMPLATE_ALLOWLIST_BASE: Record<'SARL' | 'SARL_AU', string[]> = {
  SARL: [
    'STATUTS_SARL_DIRECTEUR',
    'ANNONCE_LEGALE_DIRECTEUR',
    // Lot 5 (2026-09-07) — les trois formulaires administratifs (etapes 19, 20
    // et 21 du guide). Obligatoires « dans tous les dossiers » au referentiel.
    'DEMANDE_TAXE_PROFESSIONNELLE',
    'DECLARATION_EXISTENCE',
    'DECLARATION_IMMATRICULATION_RC',
  ],
  SARL_AU: [
    'STATUTS_SARL_AU_DIRECTEUR',
    'ANNONCE_LEGALE_DIRECTEUR',
    'DEMANDE_TAXE_PROFESSIONNELLE',
    'DECLARATION_EXISTENCE',
    'DECLARATION_IMMATRICULATION_RC',
  ],
};

/**
 * Lot 5 — documents OBLIGATOIRES du parcours (referentiel V20, colonne
 * « Tous dossiers ») : coches d'office et non decochables. Le seul document
 * conditionnel est l'acte de nomination, qui suit la reponse « gerance
 * statutaire ? » de l'etape 5.
 */
const DOCUMENTS_OBLIGATOIRES = new Set<string>([
  'STATUTS_SARL_DIRECTEUR',
  'STATUTS_SARL_AU_DIRECTEUR',
  'ANNONCE_LEGALE_DIRECTEUR',
  'DEMANDE_TAXE_PROFESSIONNELLE',
  'DECLARATION_EXISTENCE',
  'DECLARATION_IMMATRICULATION_RC',
]);

/**
 * Étape 7 (noms propres 2026-08) — Libellés « métier » affichés à l'utilisateur.
 * Le code technique du template (ex. `STATUTS_SARL_DIRECTEUR`) reste la clé
 * interne (payload, dataroom, allowlist) mais n'est JAMAIS montré : ni titre de
 * carte, ni nom de fichier téléchargé. On mappe chaque code vers le seul type
 * du document.
 */
const DOCUMENT_LABELS: Record<string, string> = {
  STATUTS_SARL_DIRECTEUR: 'Statuts',
  STATUTS_SARL_AU_DIRECTEUR: 'Statuts',
  ACTE_NOMINATION_GERANT_DIRECTEUR: 'Acte de nomination du gérant',
  ANNONCE_LEGALE_DIRECTEUR: 'Annonce légale',
  DEMANDE_TAXE_PROFESSIONNELLE: "Demande d'inscription à la taxe professionnelle",
  DECLARATION_EXISTENCE: "Déclaration d'existence",
  DECLARATION_IMMATRICULATION_RC: "Déclaration d'immatriculation au RC (modèle 2)",
  PV_DEFAUT_QUORUM_SARL: 'PV — Défaut de quorum',
  PV_DEFAUT_QUORUM_SARL_AU: 'PV — Défaut de quorum',
  PV_IRREGULARITE_CONVOCATION_SARL: 'PV — Irrégularité de convocation',
  PV_IRREGULARITE_CONVOCATION_SARL_AU: 'PV — Irrégularité de convocation',
};

/** Libellé propre d'un template (jamais le code brut). */
function docLabel(tpl: TemplateInfo): string {
  return DOCUMENT_LABELS[tpl.code] || tpl.documentKind || tpl.code;
}

/**
 * Étape 7 (persistance 2026-08) — code template → `DocumentType` juridique sous
 * lequel le document est déposé en Data Room (miroir de `mapPieceToDocumentType`
 * côté {@link CreationSarlWorkflowPage}). Sert à retrouver, au montage, le
 * document en vigueur correspondant pour ré-hydrater son blob.
 */
const TEMPLATE_TO_DOCTYPE: Record<string, DocumentType> = {
  STATUTS_SARL_DIRECTEUR: 'STATUTS',
  STATUTS_SARL_AU_DIRECTEUR: 'STATUTS',
  ACTE_NOMINATION_GERANT_DIRECTEUR: 'ACTE_NOMINATION',
  ANNONCE_LEGALE_DIRECTEUR: 'ANNONCE_JAL',
  // Lot 5 — chaque formulaire a SON type : sans cela les trois tombaient en
  // « AUTRE », et deux « AUTRE » de meme titre se dedupliquent en Data Room.
  DEMANDE_TAXE_PROFESSIONNELLE: 'DEMANDE_TAXE_PROFESSIONNELLE',
  DECLARATION_EXISTENCE: 'DECLARATION_EXISTENCE',
  DECLARATION_IMMATRICULATION_RC: 'DECLARATION_IMMATRICULATION_RC',
};

/**
 * Étape 7 (noms propres 2026-08) — Nom de fichier « soigné », délégué au helper
 * de nommage versionné partagé ({@link buildDocFilename}) :
 * « <Type> - <Dénomination> - <Forme>[ - v<n>].docx ».
 *   - <Type>          = le libellé métier ({@link docLabel}).
 *   - <Dénomination>  = la raison sociale saisie en Step1.
 *   - <Forme>         = la forme juridique lisible (« SARL » / « SARL AU »).
 *   - « - v<n> »      = ajouté UNIQUEMENT à partir de la v2 (document régénéré ou
 *                       édité au moins une fois). La 1re génération n'a aucun suffixe.
 * `ext = ''` produit le libellé sans extension (aperçu, titre, ligne « … généré »).
 * Jamais le code technique du backend.
 */
function cleanDocFilename(
  tpl: TemplateInfo,
  denomination: string,
  forme: string,
  version?: number,
): string {
  return buildDocFilename(docLabel(tpl), denomination, forme, version);
}

/** Idem sans l'extension (aperçu, titre, ligne « … généré »). */
function cleanDocBaseName(
  tpl: TemplateInfo,
  denomination: string,
  forme: string,
  version?: number,
): string {
  return buildDocFilename(docLabel(tpl), denomination, forme, version, '');
}

export function Step7Generation({
  existing,
  data,
  saving,
  onSubmit,
  onNavigate,
  onDocumentGenerated,
  dossierId,
  ticketId,
  readOnly = false,
}: Props) {
  // 2026-06-10 — Fix persistance : le onSubmit envoie { documents: {...} }
  // directement, sans wrapper "generation". Le bug pre-existant lisait
  // "existing.generation.documents" et trouvait toujours vide -> l'employe
  // devait regenerer apres chaque navigation Step7/Step8. Fallback sur la
  // cle wrapped pour compat ascendante avec d'anciens drafts.
  const previous = (existing ?? {}) as Record<string, unknown>;
  const directDocs = previous.documents as
    | Record<string, Partial<DocState>>
    | undefined;
  const wrappedGen = previous.generation as
    | { documents?: Record<string, Partial<DocState>> }
    | undefined;
  const persistedDocs: Record<string, Partial<DocState>> =
    directDocs ?? wrappedGen?.documents ?? {};

  /**
   * Lot 5 (2026-09-07) — SAISIES PROPRES AUX FORMULAIRES.
   *
   * Elles vivent avec l'etape (donc dans le brouillon), pas dans un etat volatile :
   * revenir sur l'etape 7 apres avoir genere ne doit pas les faire disparaitre.
   */
  const [complements, setComplements] = useState<Record<string, string>>(
    () => (previous.complements as Record<string, string> | undefined) ?? {},
  );

  /**
   * Lot 5 — documents RETENUS. Les obligatoires sont coches d'office et ne se
   * decochent pas ; le seul conditionnel (acte de nomination) suit la reponse
   * « gerance statutaire ? » donnee a l'etape 5. La liste sert AUSSI a decider
   * quels champs complementaires afficher : un champ n'apparait que si le
   * document qui le consomme est retenu.
   */
  const [ecartes, setEcartes] = useState<Set<string>>(
    () => new Set((previous.documentsEcartes as string[] | undefined) ?? []),
  );

  const [templates, setTemplates] = useState<TemplateInfo[]>([]);
  const [loadingTemplates, setLoadingTemplates] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [docs, setDocs] = useState<Record<string, DocState>>(() => {
    const seed: Record<string, DocState> = {};
    for (const [code, st] of Object.entries(persistedDocs)) {
      seed[code] = {
        ...freshState(),
        ...st,
      } as DocState;
    }
    return seed;
  });
  // Sprint 2026-06-12 — éditeur in-app TipTap : code du template ouvert dans
  // le modal d'édition (null = modal fermé).
  const [editingTplCode, setEditingTplCode] = useState<string | null>(null);
  /**
   * Lot 3 — code du template dont la régénération attend une confirmation.
   * On ne demande cette confirmation QUE si le document a été édité à la main :
   * une confirmation systématique se clique sans se lire.
   */
  const [regenerationAConfirmer, setRegenerationAConfirmer] = useState<string | null>(null);
  // Étape 7 (aperçu 2026-08) — aperçu fidèle plein écran : code du template
  // ouvert dans le modal d'aperçu (null = fermé). L'aperçu s'affiche sur toute
  // la largeur disponible et défile sur toute la longueur du document.
  const [previewingTplCode, setPreviewingTplCode] = useState<string | null>(null);
  // Étape 7 (aperçu 2026-08) — mini-aperçu fidèle INLINE affiché par défaut sous
  // la carte dès qu'un blob est disponible ; repliable par carte (clé = code).
  // Le bouton « Plein écran » ouvre le même rendu en modal pleine largeur.
  const [previewCollapsed, setPreviewCollapsed] = useState<Record<string, boolean>>({});
  // Étape 7 (persistance 2026-08) — cartes en cours de ré-hydratation du blob
  // (téléchargement Data Room ou régénération silencieuse) au montage de l'étape.
  const [restoring, setRestoring] = useState<Record<string, boolean>>({});

  // 2026-06-09 (fix payload denomination) — Step1 a 2 shapes possibles :
  //  - FLAT (live submit) : { denomination: "PARACOSME TRANSP", ice: "...", ... }
  //  - NESTED (rehydrated draft) : { denomination: { denomination: "PARACOSME...",
  //    ice: "...", formeJuridique: "SARL", ... } } — c'est ainsi que la persistence
  //    encapsule le formulaire Step1.
  // On normalise systematiquement : si den.denomination est un objet, on prend
  // l'objet imbrique comme veritable Step1 ; sinon on reste sur la map flat.
  // Sans ce normaliser, buildPayload envoyait l'objet entier au backend et le
  // mapper Java rendait `{ice=..., denomination=PARACOSME TRANSP, ...}` au lieu
  // de `PARACOSME TRANSP`.
  // 2026-06-11 (fix payload nesting — tous les steps) — le backend
  // CreationSarlWorkflow stocke chaque step sous une cle dediee :
  //   data.step1 = { denomination: <form>, formeJuridique }
  //   data.step2 = { siege: <form> }
  //   data.step3 = { capital: <out> }
  //   data.step4 = { activite: <form> }
  // (cf. backend StepResult.ok(Map.of("siege", p))). Sans denester,
  // siege.adresse / cap.capitalSocialMad / den.ice etaient undefined ce qui
  // produisait "Capital de 0 dirhams", "Siège social : ", "ICE :   —  IF :"
  // et la denomination etait serialisee en `{ice=..., denomination=...}`.
  const unwrapStep = (step: unknown, key: string): Record<string, unknown> => {
    const raw = (step as Record<string, unknown> | undefined) ?? {};
    const nested = raw[key];
    if (typeof nested === 'object' && nested !== null && !Array.isArray(nested)) {
      return nested as Record<string, unknown>;
    }
    return raw;
  };
  const den = unwrapStep(data.step1, 'denomination');
  // Étape 7 (noms propres 2026-08) — raison sociale (Step1) pour les noms de
  // fichiers « <Type> - <Dénomination>[ - v<n>].docx ».
  const denomination = String(den.denomination ?? '').trim();
  const siege = unwrapStep(data.step2, 'siege');
  const cap = unwrapStep(data.step3, 'capital');
  const act = unwrapStep(data.step4, 'activite');
  const formeFromStep1 = den.formeJuridique as string | undefined;
  const forme: 'SARL' | 'SARL_AU' = formeFromStep1 === 'SARL_AU' ? 'SARL_AU' : 'SARL';
  const STATUTS_TEMPLATE_CODE = STATUTS_BY_FORME[forme];
  const ACTE_TEMPLATE_CODE = ACTE_BY_FORME[forme];
  const dirs = ((data.step5 as { dirigeants?: Array<Record<string, unknown>> })
    ?.dirigeants ?? []) as Array<Record<string, unknown>>;
  const statutaires = dirs.filter((d) => d.isStatutaire === true);
  const nonStatutaires = dirs.filter((d) => d.isStatutaire === false);
  const hasNonStatutaire = nonStatutaires.length > 0;
  const associesStep6 = ((data.step6 as { associes?: Array<Record<string, unknown>> })
    ?.associes ?? []) as Array<Record<string, unknown>>;

  // C4 2026-06-21 — Preflight : ceinture-et-bretelles de la politique stricte.
  // C1 force deja la saisie a chaque etape ; ce panneau attrape les brouillons
  // anciens (crees avant C1) et bloque la generation tant qu'un champ
  // OBLIGATOIRE reste vide, avec un lien direct vers l'etape a completer.
  const gerance7 =
    (data.step5 as { gerance?: Record<string, unknown> })?.gerance ?? {};
  const isEmptyVal = (v: unknown) =>
    v == null || (typeof v === 'string' && v.trim() === '');
  const preflightMissing: { label: string; step: number }[] = [];
  // 2026-06-22 — sigle facultatif (vide => « néant ») : plus dans le preflight.
  // 2026-08 — Banque + n° de compte requis UNIQUEMENT si les fonds sont deposes
  // en compte bloque (depotFondsBloque = « oui »). Sinon, non demandes (aligne
  // sur Step3 et sur la validation backend).
  const depotBloque7 =
    cap.depotFondsBloque === 'oui' || cap.depotFondsBloque === true;
  if (depotBloque7 && isEmptyVal(cap.depotBanqueNom))
    preflightMissing.push({ label: 'Banque de dépôt du capital', step: 3 });
  if (depotBloque7 && isEmptyVal(cap.depotNumero))
    preflightMissing.push({ label: 'N° du compte de dépôt', step: 3 });
  if (isEmptyVal(gerance7.dureeMandat))
    preflightMissing.push({ label: 'Durée du mandat des gérants', step: 5 });
  if (isEmptyVal(gerance7.remunerationMode))
    preflightMissing.push({ label: 'Rémunération de la gérance', step: 5 });
  const preflightBlocked = preflightMissing.length > 0;

  // Chargement des templates manifest.
  useEffect(() => {
    let cancelled = false;
    setLoadingTemplates(true);
    setLoadError(null);
    listTemplatesForWorkflow('CREATION_SARL')
      .then((items) => {
        if (cancelled) return;
        // 1) Filtre : on garde le sous-ensemble pertinent pour la forme juridique
        //    + ACTE_NOMINATION_GERANT seulement si au moins un gerant non statutaire
        //    (RG transverse 2026-06-09).
        const allow = new Set<string>(TEMPLATE_ALLOWLIST_BASE[forme]);
        if (hasNonStatutaire) allow.add(ACTE_TEMPLATE_CODE);
        const filtered = items.filter((t) => allow.has(t.code));
        // 2) Trie : statuts en premier, puis JAL, puis acte, puis le reste.
        const order = (code: string) =>
          code === STATUTS_TEMPLATE_CODE ? 0
            : code.startsWith('ANNONCE_') ? 1
            : code.startsWith('ACTE_NOMINATION_GERANT') ? 2
            // Lot 5 — les trois formulaires viennent apres les actes, dans
            // l'ordre du parcours : TP (etape 19), existence (20), RC (21).
            : code === 'DEMANDE_TAXE_PROFESSIONNELLE' ? 3
            : code === 'DECLARATION_EXISTENCE' ? 4
            : code === 'DECLARATION_IMMATRICULATION_RC' ? 5
            : 6;
        const sorted = filtered.sort((a, b) => {
          const oa = order(a.code), ob = order(b.code);
          if (oa !== ob) return oa - ob;
          return (a.documentKind || a.code).localeCompare(b.documentKind || b.code);
        });
        setTemplates(sorted);
      })
      .catch((err) => {
        if (cancelled) return;
        setLoadError(
          err instanceof Error
            ? err.message
            : 'Impossible de charger la liste des documents',
        );
      })
      .finally(() => {
        if (!cancelled) setLoadingTemplates(false);
      });
    return () => {
      cancelled = true;
    };
    // Re-filter quand forme ou presence de non-statutaire change.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [forme, hasNonStatutaire, STATUTS_TEMPLATE_CODE, ACTE_TEMPLATE_CODE]);

  const buildPayload = useCallback(
    (): Record<string, unknown> => {
      const capital = Number(cap.capitalSocialMad ?? 0);
      const numeraireLibere = Number(cap.apportNumeraireLibere ?? 0);
      const apportNature = Number(cap.apportNature ?? 0);
      const apportIndustrie = Number(cap.apportIndustrie ?? 0);
      // Le total libere = numeraire libere + nature (100%) + industrie (100%).
      const capitalLibere = numeraireLibere + apportNature + apportIndustrie;
      const today = new Date().toISOString().slice(0, 10);

      return {
        societe: {
          denomination: den.denomination,
          formeJuridique: forme,
          adresseSiege: siege.adresse ?? siege.adresseLigne1,
          capitalChiffres: capital,
          // 2026-08 — objet social multi-activités : liste à tirets si plusieurs
          // activités (le moteur rend les \n en <w:br/>), phrase sinon.
          objetSocial: formatObjetSocial(act.activites, act.description),
          activiteSociete: act.description,
          nombreParts: Number(cap.nombreParts ?? 0),
          valeurPart: Number(cap.valeurNominale ?? 0),
          dureeAnnees: Number(cap.dureeAnnees ?? 99),
          iceNumero: den.ice,
          // 2026-06-11 — IF collecte en Step1 (optionnel) - propage aux Statuts.
          ifNumero: den.ifFiscal,
          rcVille:
            ((siege.commune as string | undefined) ??
              (siege.ville as string | undefined) ??
              'Casablanca'),
          dateConstitution: (existing as Record<string, unknown> | undefined)
            ?.dateConstitution ?? today,
          // 2026-08 (fix VILLE_GREFFE) — clé manquante dans CE builder : sans elle,
          // le mapper retombait sur `tribunalCompetent`/`rcVille` = la COMMUNE.
          // On injecte la ville du greffe (Step2) — jamais la commune ; défaut =
          // province/ville du siège.
          villeGreffe:
            ((siege.villeGreffe as string | undefined) ??
              (siege.province as string | undefined) ??
              'Casablanca'),
          // tribunalCompetent : ville du tribunal — on privilégie désormais la
          // ville du greffe / la province (jamais la commune administrative).
          tribunalCompetent:
            (siege.tribunal as string | undefined) ??
            (siege.villeGreffe as string | undefined) ??
            (siege.province as string | undefined) ??
            'Casablanca',
          villeSignature:
            (siege.villeGreffe as string | undefined) ??
            (siege.province as string | undefined) ??
            (siege.commune as string | undefined) ??
            'Casablanca',
          // Lot 5 (2026-09-07) — donnees DEJA saisies aux etapes 1, 2 et 4 mais que
          // le payload ne transportait pas, faute de modele qui les consomme. Les
          // trois formulaires les reclament : les redemander aurait ete une saisie
          // en double.
          sigle: den.sigle,
          certificatNegatifNumero: den.cnNumero,
          certificatNegatifDate: den.cnDate,
          ville:
            ((siege.commune as string | undefined) ??
              (siege.ville as string | undefined) ??
              (siege.province as string | undefined)),
          activites: act.activites,
          nationalite: 'marocaine',
          // 2026-09-08 — repli de la durée du mandat, quand aucun gérant ne porte
          // le sien (brouillon antérieur à la saisie par dirigeant). Même source
          // que `buildPayloadCreationSarl`, qui la transmettait déjà.
          dureeGerance: (gerance7.dureeGerance ?? gerance7.dureeMandat) as string | undefined,
        },
        // Lot 5 — saisies propres aux formulaires (cf. `documents-creation.ts`).
        // Elles n'existent nulle part ailleurs dans le workflow.
        formulaires: complements,
        gerants: dirs.map((d) => ({
          civilite: d.civilite ?? 'M',
          prenom: d.prenom ?? '',
          nom: d.nom ?? '',
          cin: d.cinNumero ?? d.cin ?? '',
          nationalite: d.nationalite ?? 'marocaine',
          adresse: d.adresse ?? '',
          dateNaissance: d.dateNaissance ?? undefined,
          lieuNaissance: d.lieuNaissance ?? undefined,
          // 2026-06-11 — Pour {{gerant_piece_validite}}.
          pieceValidite: d.pieceValidite ?? undefined,
          /**
           * 2026-09-08 — LA DURÉE DU MANDAT ARRIVE JUSQU'À L'ACTE.
           *
           * Elle était saisie à l'étape 5, rendue OBLIGATOIRE là-bas, et le
           * préflight ci-dessus BLOQUE encore la génération si elle manque — puis
           * ce payload ne la transmettait pas. Le moteur retombait sur son défaut
           * codé en dur, et l'acte de nomination annonçait « pour une durée de
           * 99 années » — la durée de la SOCIÉTÉ — quel que soit le mandat choisi.
           * On forçait une saisie pour la jeter.
           *
           * `buildPayloadCreationSarl` (étape 9) transmettait déjà la donnée : les
           * deux constructeurs de payload divergeaient, et c'est celui qui produit
           * le document validé par l'employé qui avait tort.
           *
           * Le mandat PROPRE au dirigeant prime ; l'agrégat de l'étape 5 sert de
           * repli pour les brouillons antérieurs à la saisie par dirigeant.
           */
          dureeMandat: d.dureeMandatType
            ? dureeMandatLabel(d as { dureeMandatType?: string; dureeAnnees?: number })
            : (gerance7.dureeMandat as string | undefined),
          isStatutaire: d.isStatutaire === true,
          typePersonne: d.typePersonne ?? 'PHYSIQUE',
          denomination: d.denomination ?? undefined,
          rc: d.rc ?? undefined,
          ice: d.ice ?? undefined,
          ifFiscal: d.ifFiscal ?? undefined,
          siege: d.siege ?? undefined,
          // 2026-06-11 — Pour {{gerant_pm_capital}} / {{gerant_pm_deliberation_date}}.
          capitalEntite: d.capitalEntite !== undefined ? Number(d.capitalEntite) : undefined,
          deliberationDate: d.deliberationDate ?? undefined,
          representantLegal:
            d.representantLegal ??
            (d.repPrenom || d.repNom
              ? `${(d.repCivilite as string) === 'M' ? 'M.' : (d.repCivilite ?? '')} ${d.repPrenom ?? ''} ${d.repNom ?? ''}`.trim()
              : undefined),
        })),
        associes: associesStep6.map((a) => ({
          civilite: a.civilite ?? 'M',
          prenom: a.prenom ?? '',
          nom: a.nom ?? '',
          cin: a.cin ?? '',
          nationalite: a.nationalite ?? 'marocaine',
          adresse: a.adresse ?? '',
          dateNaissance: a.dateNaissance ?? undefined,
          lieuNaissance: a.lieuNaissance ?? undefined,
          // 2026-06-11 — Pour {{associe_pp_piece_validite}}.
          pieceValidite: a.pieceValidite ?? undefined,
          nombreParts: Number(a.nombreParts ?? 0),
          montantApport: Number(a.montantApport ?? 0),
          typeApport: a.typeApport ?? 'NUMERAIRE',
          typePersonne: a.typePersonne ?? 'PHYSIQUE',
          // PM
          denomination: a.denomination ?? undefined,
          rc: a.rc ?? undefined,
          ice: a.ice ?? undefined,
          ifFiscal: a.ifFiscal ?? undefined,
          siege: a.siege ?? undefined,
          // 2026-06-11 — Pour {{associe_pm_capital}} / {{associe_pm_deliberation_date}}.
          capitalEntite: a.capitalEntite !== undefined ? Number(a.capitalEntite) : undefined,
          deliberationDate: a.deliberationDate ?? undefined,
          representantLegal:
            (a.repPrenom || a.repNom)
              ? `${(a.repCivilite as string) === 'M' ? 'M.' : (a.repCivilite ?? '')} ${a.repPrenom ?? ''} ${a.repNom ?? ''}`.trim()
              : undefined,
          repCivilite: a.repCivilite ?? undefined,
          repPrenom: a.repPrenom ?? undefined,
          repNom: a.repNom ?? undefined,
          repCin: a.repCin ?? undefined,
          repQualite: a.repQualite ?? undefined,
        })),
        depot: {
          banque: cap.banqueDepot ?? undefined,
          dateSignature: today,
          capitalLibere,
        },
        // Champs plats (compat retro avec l'ancien payload).
        denomination: den.denomination,
        formeJuridique: forme,
        siegeAdresse: siege.adresse,
        capitalSocial: capital,
        objetSocial: act.description,
        nombreParts: Number(cap.nombreParts ?? 0),
        valeurPart: Number(cap.valeurNominale ?? 0),
        dureeAnnees: Number(cap.dureeAnnees ?? 99),
        iceNumero: den.ice,
      };
    },
    [
      den.denomination,
      den.ice,
      den.ifFiscal,
      forme,
      siege.adresse,
      siege.adresseLigne1,
      siege.commune,
      siege.ville,
      siege.tribunal,
      cap.capitalSocialMad,
      cap.nombreParts,
      cap.valeurNominale,
      cap.dureeAnnees,
      cap.apportNumeraireLibere,
      cap.apportNature,
      cap.apportIndustrie,
      cap.banqueDepot,
      act.description,
      act.activites,
      gerance7.dureeGerance,
      gerance7.dureeMandat,
      den.sigle,
      den.cnNumero,
      den.cnDate,
      siege.province,
      siege.villeGreffe,
      complements,
      dirs,
      associesStep6,
      existing,
    ],
  );

  function updateDoc(code: string, patch: Partial<DocState>) {
    setDocs((prev) => ({
      ...prev,
      [code]: { ...(prev[code] ?? freshState()), ...patch },
    }));
  }

  /**
   * Étape 7 (persistance 2026-08) — RÉ-HYDRATATION au montage.
   *
   * Le brouillon persiste les DRAPEAUX (`generated`/`validated`/`version`/
   * `documentId`) mais JAMAIS le `Blob` (non sérialisable). Sans ré-hydratation,
   * revenir sur l'étape masquait l'aperçu et donnait l'impression d'un document
   * perdu. On restaure donc chaque document précédemment généré/validé :
   *   1. PRIORITÉ Data Room : si un document du type attendu est en vigueur pour
   *      le dossier, on télécharge son blob (la version validée/déposée fait foi).
   *   2. FALLBACK : sinon (généré mais pas déposé), on régénère silencieusement
   *      pour restaurer le blob (spinner par carte).
   * Best-effort : un échec laisse la carte en « généré » avec le bouton
   * Régénérer disponible ; il ne bloque jamais l'étape.
   *
   * Exécuté une seule fois au montage (les blobs vivent en mémoire React).
   */
  useEffect(() => {
    let cancelled = false;

    (async () => {
      // Lot 2 — 0) LES BROUILLONS D'ABORD. Un document généré est persisté dès
      // sa génération : il se retrouve même si l'étape n'a jamais été soumise,
      // et il porte l'édition faite avant de quitter la page. C'est ce qui rend
      // la restauration fiable au lieu de dépendre des drapeaux du brouillon de
      // workflow (qui, eux, ne sont écrits qu'à « Valider et continuer »).
      let brouillons: import('../../../types/dataroom').DocumentSummary[] = [];
      if (ticketId) {
        try {
          brouillons = await dataroomService.listBrouillons(ticketId);
        } catch {
          // best-effort : on retombe sur les documents en vigueur.
        }
      }
      if (cancelled) return;

      const codesBrouillon = Object.entries(TEMPLATE_TO_DOCTYPE)
        .filter(([code, type]) =>
          !docs[code]?.blob && brouillons.some((b) => b.documentType === type),
        )
        .map(([code]) => code);

      // Codes persistés comme générés/validés mais sans blob en mémoire.
      const codesToRestore = Object.entries(persistedDocs)
        .filter(([code, st]) => (st.generated || st.validated) && !docs[code]?.blob)
        .map(([code]) => code)
        .filter((code) => !codesBrouillon.includes(code));

      const tousLesCodes = [...codesBrouillon, ...codesToRestore];
      if (tousLesCodes.length === 0) return;

      setRestoring((prev) => {
        const next = { ...prev };
        for (const code of tousLesCodes) next[code] = true;
        return next;
      });

      for (const code of codesBrouillon) {
        if (cancelled) return;
        try {
          const type = TEMPLATE_TO_DOCTYPE[code];
          const b = brouillons.find((x) => x.documentType === type);
          if (!b) continue;
          const blob = await dataroomService.fetchDocumentBlob(b.id);
          if (cancelled) return;
          updateDoc(code, {
            blob,
            documentId: b.id,
            brouillon: true,
            generated: true,
            filename: b.filename,
            // Lot 3 — l'état « modifié manuellement » doit survivre au
            // rechargement : c'est lui qui commande l'avertissement de
            // régénération.
            editeManuellementAt: b.editeManuellementAt ?? null,
          });
        } catch {
          // best-effort : la carte reste telle quelle, Régénérer reste dispo.
        } finally {
          if (!cancelled) setRestoring((prev) => ({ ...prev, [code]: false }));
        }
      }

      // 1) Un seul appel Data Room : liste des documents en vigueur du dossier.
      let enVigueur: import('../../../types/dataroom').DocumentSummary[] = [];
      if (dossierId && codesToRestore.length > 0) {
        try {
          const view = await dataroomService.getJuridique(dossierId);
          enVigueur = view.documentsEnVigueur ?? [];
        } catch {
          // best-effort : on bascule sur la régénération pour tous les codes.
        }
      }
      for (const code of codesToRestore) {
        if (cancelled) return;
        try {
          const wantType = TEMPLATE_TO_DOCTYPE[code];
          const match = wantType
            ? enVigueur
                .filter((d) => d.documentType === wantType)
                .sort(
                  (a, b) =>
                    (b.version ?? 0) - (a.version ?? 0) ||
                    (b.createdAt ?? '').localeCompare(a.createdAt ?? ''),
                )[0]
            : undefined;
          if (match) {
            // PRIORITÉ Data Room — récupère le blob déposé (sans re-télécharger).
            const blob = await dataroomService.fetchDocumentBlob(match.id);
            if (cancelled) return;
            updateDoc(code, {
              blob,
              documentId: match.id,
              editeManuellementAt: match.editeManuellementAt ?? null,
            });
          } else if (!readOnly) {
            // FALLBACK — régénération silencieuse (déterministe, pas d'IA).
            // JAMAIS en lecture seule (aucune génération sur ticket clôturé) :
            // on se contente alors du blob Data Room, sinon Aperçu/Télécharger
            // restent inactifs pour ce document.
            const { blob, filename } = await generateDocument(
              'CREATION_SARL',
              code,
              buildPayload(),
            );
            if (cancelled) return;
            updateDoc(code, { blob, filename });
          }
        } catch {
          // best-effort : la carte reste « générée », Régénérer reste dispo.
        } finally {
          if (!cancelled) {
            setRestoring((prev) => ({ ...prev, [code]: false }));
          }
        }
      }
    })();

    return () => {
      cancelled = true;
    };
    // Montage uniquement : les blobs ne survivent pas au démontage, la
    // ré-hydratation doit se rejouer à chaque retour sur l'étape.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Étape 7 (noms propres 2026-08) — nom de fichier / libellé « soignés » pour un
  // template, tenant compte de la dénomination (Step1) et de la version courante
  // (suffixe « - v<n> » à partir de la v2). Utilisés partout (aperçu, download,
  // dépôt Data Room, ligne de confirmation) pour ne JAMAIS montrer le code brut.
  const fileFor = (tpl: TemplateInfo): string =>
    cleanDocFilename(tpl, denomination, forme, docs[tpl.code]?.version);
  const baseNameFor = (tpl: TemplateInfo): string =>
    cleanDocBaseName(tpl, denomination, forme, docs[tpl.code]?.version);

  /**
   * Lot 2 (2026-09-07) — PERSISTANCE À LA GÉNÉRATION.
   *
   * Le document généré (ou ré-édité) est enregistré comme BROUILLON de la Data
   * Room, immédiatement, avant toute validation. C'est la correction de la
   * cause unique des quatre symptômes rapportés : jusqu'ici l'acte n'existait
   * que comme Blob en mémoire React, donc un rechargement ou un simple
   * Précédent/Suivant le perdait — édition comprise.
   *
   * Un brouillon n'apparaît dans aucune vue du dossier juridique. Régénérer
   * remplace le brouillon précédent au lieu de l'empiler (garanti en base).
   * Best-effort : un échec de persistance n'empêche pas de travailler sur le
   * document en mémoire, il est signalé sur la carte.
   */
  /**
   * Lot 3 — après une séance d'édition, le document en base a changé : on
   * recharge son binaire pour que l'aperçu et le téléchargement montrent la
   * version ÉDITÉE, et on note la date d'édition pour l'avertissement de
   * régénération. Sans cela, la carte continuerait d'afficher l'avant.
   */
  async function recupererApresEdition(tpl: TemplateInfo) {
    const st = docs[tpl.code];
    if (!st?.documentId || !dossierId) return;
    try {
      const vue = await dataroomService.getJuridique(dossierId);
      const type = TEMPLATE_TO_DOCTYPE[tpl.code];
      const courant = vue.documentsEnVigueur.find((d) => d.documentType === type);
      // Un brouillon n'apparaît pas dans les documents en vigueur : on garde
      // alors son identifiant, seul le binaire est rechargé.
      const cible = st.brouillon ? st.documentId : (courant?.id ?? st.documentId);
      const blob = await dataroomService.fetchDocumentBlob(cible);
      updateDoc(tpl.code, {
        blob,
        documentId: cible,
        validated: false,
        editeManuellementAt:
          (st.brouillon ? null : courant?.editeManuellementAt ?? null) ?? new Date().toISOString(),
      });
    } catch {
      updateDoc(tpl.code, {
        error:
          'Modifications enregistrées, mais l’aperçu n’a pas pu être rechargé. '
          + 'Rouvrez l’étape pour le rafraîchir.',
      });
    }
  }

  async function persistBrouillon(
    tpl: TemplateInfo,
    blob: Blob,
    filename: string,
  ): Promise<string | undefined> {
    if (readOnly || !dossierId || !ticketId) return undefined;
    const type = TEMPLATE_TO_DOCTYPE[tpl.code];
    if (!type) return undefined;
    try {
      const file = new File([blob], filename, {
        type: blob.type || 'application/octet-stream',
      });
      const saved = await dataroomService.saveBrouillon(dossierId, {
        file,
        documentType: type,
        title: docLabel(tpl),
        ticketId,
      });
      updateDoc(tpl.code, { documentId: saved.id, brouillon: true });
      return saved.id;
    } catch (err) {
      updateDoc(tpl.code, {
        error:
          'Document généré, mais NON enregistré : ' +
          (err instanceof Error ? err.message : 'échec de la sauvegarde') +
          '. Il sera perdu si vous quittez la page.',
      });
      return undefined;
    }
  }

  /**
   * 2026-06-09 (fix LLM-off) — Generation 100% deterministe via le template engine.
   * Tout template (Statuts, JAL, Acte) emprunte la meme pipeline /ai/workflows/.../documents
   * qui renvoie un .docx natif rendu a partir du gabarit du directeur. Aucune sortie
   * LLM ne touche le contenu du document — la mise en page est preservee bit-pour-bit.
   */
  async function generateOne(tpl: TemplateInfo) {
    // Étape 7 (noms propres 2026-08) — versionnage : la 1re génération = v1 (pas
    // de suffixe) ; toute RE-génération d'un document déjà généré incrémente la
    // version (« - v2 », « - v3 »… dans le nom de fichier).
    const prev = docs[tpl.code];
    const nextVersion = prev?.generated ? (prev.version ?? 1) + 1 : 1;
    updateDoc(tpl.code, {
      generating: true,
      error: null,
      validated: false,
    });
    try {
      const { blob, filename } = await generateDocument(
        'CREATION_SARL',
        tpl.code,
        buildPayload(),
      );
      updateDoc(tpl.code, {
        generating: false,
        generated: true,
        blob,
        filename,
        version: nextVersion,
      });
      // Pas de DEPOT dataroom ici : on attend la validation de l'employe. Mais
      // le document est PERSISTE comme brouillon, sans quoi il ne survivrait pas
      // a un rechargement (lot 2).
      await persistBrouillon(tpl, blob, cleanDocFilename(tpl, denomination, forme, nextVersion));
    } catch (err) {
      updateDoc(tpl.code, {
        generating: false,
        error: err instanceof Error ? err.message : 'Generation impossible',
      });
    }
  }

  /**
   * Telechargement DOCX (le seul format natif V1 — l'utilisateur exporte en PDF
   * depuis Word si besoin). On regenere a la volee si le blob n'est pas en memoire
   * (cas reprise apres navigation Step7 -> Step8 -> Step7).
   */
  async function downloadDocx(tpl: TemplateInfo) {
    const st = docs[tpl.code];
    if (!st) return;
    // Nom de fichier propre « <Type> - <Dénomination>[ - v<n>].docx », jamais le
    // code technique renvoyé par le backend (`<code>.docx`).
    const cleanName = fileFor(tpl);
    try {
      if (st.blob) {
        triggerDownload(st.blob, cleanName);
        return;
      }
      updateDoc(tpl.code, { generating: true, error: null });
      const { blob, filename } = await generateDocument(
        'CREATION_SARL',
        tpl.code,
        buildPayload(),
      );
      updateDoc(tpl.code, { generating: false, blob, filename });
      triggerDownload(blob, cleanName);
    } catch (err) {
      updateDoc(tpl.code, {
        generating: false,
        error:
          err instanceof Error
            ? `Telechargement DOCX echec : ${err.message}`
            : 'Telechargement DOCX echec',
      });
    }
  }

  // 2026-06-22 — Téléchargement PDF RETIRÉ : la conversion LibreOffice est jugée
  // non fiable. Seul le .docx natif (fidèle au gabarit) est proposé ; l'export PDF
  // se fait depuis Word. Fonction downloadPdf et imports associés supprimés.

  /**
   * Validation : marque le document comme valide et declenche le depot auto dataroom.
   * Le .docx est natif et deja en memoire (ou regenere a la demande).
   */
  async function validateOne(tpl: TemplateInfo) {
    const st = docs[tpl.code];
    if (!st || !st.generated) return;
    let blob = st.blob;
    let filename = st.filename;
    if (!blob || !filename) {
      try {
        updateDoc(tpl.code, { generating: true, error: null });
        const result = await generateDocument(
          'CREATION_SARL',
          tpl.code,
          buildPayload(),
        );
        blob = result.blob;
        filename = result.filename;
        updateDoc(tpl.code, { generating: false, blob, filename });
      } catch (err) {
        updateDoc(tpl.code, {
          generating: false,
          error: err instanceof Error
            ? `Validation echec : ${err.message}`
            : 'Validation echec',
        });
        return;
      }
    }
    // Lot 2 — le document valide est CELUI qui a ete apercu et edite : on
    // promeut le brouillon deja stocke plutot que de re-televerser un binaire.
    // Il emprunte alors le versionnement juridique existant (l'occupant du meme
    // emplacement bascule en historique avec sa version).
    const st2 = docs[tpl.code];
    if (st2?.documentId && st2.brouillon) {
      try {
        await dataroomService.validerBrouillon(st2.documentId);
        updateDoc(tpl.code, { validated: true, brouillon: false });
        return;
      } catch (err) {
        updateDoc(tpl.code, {
          error:
            'Validation impossible : ' +
            (err instanceof Error ? err.message : 'échec du dépôt en Data Room'),
        });
        return;
      }
    }
    updateDoc(tpl.code, { validated: true });
    // Repli (aucun brouillon : ticket absent, ou persistance en echec) — depot
    // classique au nom propre, jamais le code technique.
    void onDocumentGenerated?.(tpl.code, docLabel(tpl), blob, fileFor(tpl));
  }

  /**
   * Au moins le document principal (STATUTS) doit etre genere ET valide pour
   * passer a l'etape suivante. Les autres documents sont optionnels (peuvent
   * etre regeneres a tout moment depuis la dataroom).
   */
  const statutsState = docs[STATUTS_TEMPLATE_CODE];
  const canSubmit = !!statutsState?.generated && !!statutsState?.validated;

  /**
   * Lot 5 — documents RETENUS : tous ceux que l'allowlist expose, moins ceux que
   * l'employe a explicitement ecartes. Un document obligatoire ne peut pas etre
   * ecarte (le referentiel le donne « Tous dossiers »).
   */
  const documentsRetenus = useMemo(
    () => templates.map((t) => t.code).filter((c) => !ecartes.has(c)),
    [templates, ecartes],
  );

  /**
   * Lot 5 — LES CHAMPS SUIVENT LES DOCUMENTS. Groupes par document, et jamais
   * affiches deux fois : un champ que deux imprimes partagent (le telephone de la
   * societe) n'est demande qu'une seule fois.
   */
  const groupesChamps = useMemo(
    () => champsParDocument(documentsRetenus),
    [documentsRetenus],
  );

  const documentsPayload = useMemo(() => {
    const out: Record<string, unknown> = {};
    for (const [code, st] of Object.entries(docs)) {
      out[code] = {
        generated: st.generated,
        validated: st.validated,
        filename: st.filename,
        documentId: st.documentId,
        // Persister la version pour que le suffixe « - v<n> » survive à la
        // navigation Step7 → Step8 → Step7.
        version: st.version,
      };
    }
    return out;
  }, [docs]);

  return (
    <form
      noValidate
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit({
          statutsDocumentId: statutsState?.documentId ?? null,
          actesNominationIds: [],
          statutsValides: !!statutsState?.validated,
          documents: documentsPayload,
          // Lot 5 — la selection et les saisies propres aux formulaires vivent
          // avec l'etape : revenir dessus ne doit rien effacer.
          documentsEcartes: Array.from(ecartes),
          complements,
        });
      }}
      className="space-y-6"
    >
      <div className="rounded-xl border border-accent/20 bg-accent/10 p-4 text-sm text-fg">
        <div className="flex items-start gap-3">
          {readOnly ? (
            <Eye className="mt-0.5 h-5 w-5 flex-shrink-0 text-accent" />
          ) : (
            <Sparkles className="mt-0.5 h-5 w-5 flex-shrink-0 text-accent" />
          )}
          <div>
            <p className="font-semibold text-accent">
              {readOnly ? 'Documents générés' : 'Génération des documents'} —{' '}
              {String(den.denomination ?? 'votre société')}
            </p>
            <p className="text-xs text-fg-subtle">
              {readOnly
                ? 'Consultation (lecture seule) : les documents générés sont consultables (aperçu) et téléchargeables. Aucune génération, modification ni validation n’est possible sur un ticket clôturé.'
                : 'Chaque document peut être généré indépendamment. Une fois généré : aperçu, téléchargement, modification et validation. Mise en page conforme, génération automatique sans IA. Les documents validés sont automatiquement déposés dans la Data Room du dossier.'}
            </p>
          </div>
        </div>
      </div>

      {loadingTemplates && (
        <div className="rounded-xl border border-border bg-bg-raised p-6">
          <div className="flex items-center gap-3 text-sm text-fg-subtle">
            <Loader className="h-4 w-4 animate-spin" /> Chargement des
            documents disponibles…
          </div>
        </div>
      )}

      {loadError && !loadingTemplates && (
        <div className="rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm text-warning">
          <AlertTriangle className="mr-2 inline h-4 w-4" />
          {loadError}
        </div>
      )}

      {/*
        Lot A (2026-09-10) — la liste est vide PAR CONSTRUCTION, et le message le
        dit. Le corpus de creation a ete remplace par les 23 modeles livres le
        9 septembre ; leur resolution (253 variables nouvelles) est l'objet du
        lot B. Tant qu'aucun mapper ne les route, le backend renvoie une liste
        vide. L'ancien message envoyait verifier le manifest : il est pourtant
        complet, et l'employe serait parti chercher au mauvais endroit.
      */}
      {!loadingTemplates && templates.length === 0 && !loadError && (
        <div className="rounded-xl border border-border bg-bg-raised p-6 text-sm text-fg-subtle">
          La génération des documents de création est en cours de refonte : les
          23 modèles livrés le 9 septembre ont remplacé les précédents, et leur
          alimentation reste à câbler. Aucun document n'est générable depuis cette
          étape pour l'instant.
        </div>
      )}

      {!readOnly && preflightBlocked && (
        <div className="rounded-xl border-l-4 border-danger bg-danger/10 p-4">
          <p className="flex items-center gap-2 text-sm font-bold text-danger">
            <AlertCircle className="h-5 w-5" />
            Informations obligatoires manquantes — à compléter avant de générer
          </p>
          <p className="mt-1 text-xs text-fg-subtle">
            La génération est bloquée tant que ces champs ne sont pas renseignés
            (politique « valeur réelle ou saisie forcée »).
          </p>
          <ul className="mt-3 space-y-1.5 text-sm text-fg">
            {preflightMissing.map((m) => (
              <li
                key={m.label}
                className="flex items-center justify-between gap-3"
              >
                <span>• {m.label}</span>
                {onNavigate && (
                  <button
                    type="button"
                    onClick={() => onNavigate(m.step)}
                    className="rounded-md border border-danger/40 px-2 py-0.5 text-xs font-medium text-danger hover:bg-danger/10"
                  >
                    Aller à l'étape {m.step}
                  </button>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}

      {!loadingTemplates && templates.length > 0 && (
        <div className="rounded-xl border border-border bg-bg-raised p-5">
          <h4 className="text-sm font-semibold text-fg">Documents a generer</h4>
          <p className="mt-1 text-xs text-fg-subtle">
            Les documents obligatoires dans tous les dossiers sont retenus d&rsquo;office.
            L&rsquo;acte de nomination n&rsquo;apparait que si la gerance n&rsquo;est pas
            designee dans les statuts &mdash; reponse donnee a l&rsquo;etape 5.
          </p>
          <div className="mt-3 grid grid-cols-1 gap-2 sm:grid-cols-2">
            {templates.map((tpl) => {
              const obligatoire = DOCUMENTS_OBLIGATOIRES.has(tpl.code);
              const retenu = !ecartes.has(tpl.code);
              return (
                <label
                  key={tpl.code}
                  className={`flex items-start gap-2 rounded-lg border px-3 py-2 text-sm ${
                    retenu ? 'border-accent/40 bg-accent/5' : 'border-border'
                  }`}
                >
                  <input
                    type="checkbox"
                    className="mt-0.5"
                    checked={retenu}
                    disabled={obligatoire || readOnly}
                    onChange={() =>
                      setEcartes((prev) => {
                        const next = new Set(prev);
                        if (next.has(tpl.code)) next.delete(tpl.code);
                        else next.add(tpl.code);
                        return next;
                      })
                    }
                  />
                  <span className="min-w-0">
                    <span className="block truncate font-medium text-fg">
                      {docLabel(tpl)}
                    </span>
                    <span className="text-[11px] text-fg-subtle">
                      {obligatoire ? 'Obligatoire — tous dossiers' : 'Conditionnel'}
                    </span>
                  </span>
                </label>
              );
            })}
          </div>
        </div>
      )}

      {!readOnly && groupesChamps.length > 0 && (
        <div className="rounded-xl border border-border bg-bg-raised p-5">
          <h4 className="text-sm font-semibold text-fg">
            Complements demandes par les documents retenus
          </h4>
          <p className="mt-1 text-xs text-fg-subtle">
            Ces champs n&rsquo;apparaissent que parce que le document qui les consomme
            est retenu. Ils sont <strong>facultatifs</strong> : ils alimentent des
            cases d&rsquo;imprimes administratifs, jamais une phrase d&rsquo;acte
            &mdash; une case laissee blanche ne bloque pas la generation.
          </p>
          {groupesChamps.map((groupe) => (
            <div key={groupe.code} className="mt-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-accent">
                {DOCUMENT_LABELS[groupe.code] ?? groupe.code}
              </p>
              <div className="mt-2 grid grid-cols-1 gap-3 sm:grid-cols-2">
                {groupe.champs.map((champ) => (
                  <div key={champ.key}>
                    <label
                      htmlFor={`complement-${champ.key}`}
                      className="mb-1 block text-xs font-medium text-fg"
                    >
                      {champ.label}
                      {champ.documents.length > 1 && (
                        <span className="ml-1 font-normal text-fg-subtle">
                          — sert aussi a{' '}
                          {champ.documents
                            .filter((d) => d !== groupe.code)
                            .map((d) => DOCUMENT_LABELS[d] ?? d)
                            .join(', ')}
                        </span>
                      )}
                    </label>
                    {champ.type === 'select' ? (
                      <select
                        id={`complement-${champ.key}`}
                        value={complements[champ.key] ?? ''}
                        onChange={(e) =>
                          setComplements((prev) => ({
                            ...prev,
                            [champ.key]: e.target.value,
                          }))
                        }
                        className="w-full rounded-lg border border-border bg-bg px-3 py-2 text-sm text-fg"
                      >
                        <option value="">&mdash; non renseigne &mdash;</option>
                        {(champ.options ?? []).map((o) => (
                          <option key={o} value={o}>
                            {o}
                          </option>
                        ))}
                      </select>
                    ) : champ.type === 'textarea' ? (
                      <textarea
                        id={`complement-${champ.key}`}
                        rows={2}
                        value={complements[champ.key] ?? ''}
                        onChange={(e) =>
                          setComplements((prev) => ({
                            ...prev,
                            [champ.key]: e.target.value,
                          }))
                        }
                        className="w-full rounded-lg border border-border bg-bg px-3 py-2 text-sm text-fg"
                      />
                    ) : (
                      <input
                        id={`complement-${champ.key}`}
                        type={champ.type === 'date' ? 'date' : 'text'}
                        value={complements[champ.key] ?? ''}
                        onChange={(e) =>
                          setComplements((prev) => ({
                            ...prev,
                            [champ.key]: e.target.value,
                          }))
                        }
                        className="w-full rounded-lg border border-border bg-bg px-3 py-2 text-sm text-fg"
                      />
                    )}
                    {champ.aide && (
                      <p className="mt-1 text-[11px] text-fg-subtle">{champ.aide}</p>
                    )}
                  </div>
                ))}
              </div>
            </div>
          ))}

          <div className="mt-5 rounded-lg border border-border bg-bg-overlay p-3">
            <p className="text-xs font-semibold text-fg">
              Repris automatiquement &mdash; rien a ressaisir
            </p>
            <ul className="mt-1.5 space-y-0.5 text-[11px] text-fg-subtle">
              {REPRISES_AUTOMATIQUES.map((r) => (
                <li key={r.libelle}>
                  &bull; {r.libelle} <span className="opacity-70">({r.source})</span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        {templates.filter((t) => !ecartes.has(t.code)).map((tpl) => {
          const st = docs[tpl.code] ?? freshState();
          const isStatuts = tpl.code === STATUTS_TEMPLATE_CODE;
          return (
            <div
              key={tpl.code}
              className={`overflow-hidden rounded-xl border bg-bg-raised shadow-sm ${
                st.validated ? 'border-success' : 'border-border'
              }`}
            >
              <div className="flex items-start justify-between gap-2 border-b border-border bg-bg-overlay px-5 py-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <FileText className="h-4 w-4 flex-shrink-0 text-accent" />
                    <h4
                      className="truncate text-sm font-semibold text-fg"
                      title={docLabel(tpl)}
                    >
                      {docLabel(tpl)}
                    </h4>
                    {isStatuts && (
                      <span className="rounded bg-danger/10 px-1.5 py-0.5 text-[10px] font-bold uppercase text-danger">
                        Principal
                      </span>
                    )}
                  </div>
                </div>
                {st.validated && (
                  <span className="inline-flex items-center gap-1 rounded-full bg-success/15 px-2 py-0.5 text-[10px] font-semibold uppercase text-success">
                    <CheckCircle className="h-3 w-3" /> Valide
                  </span>
                )}
              </div>

              <div className="space-y-3 p-5">
                {st.error && (
                  <div className="flex items-start gap-2 rounded-lg border border-danger/30 bg-danger/10 p-2 text-xs text-danger">
                    <AlertCircle className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" />
                    <span className="flex-1">{st.error}</span>
                  </div>
                )}

                {!st.generated && readOnly && (
                  <div className="rounded-lg border border-border bg-bg-overlay p-3 text-[11px] text-fg-subtle">
                    <FileText className="mr-1 inline h-3.5 w-3.5 align-text-bottom" />
                    Document non généré pour ce dossier.
                  </div>
                )}

                {!st.generated && !readOnly && (
                  <button
                    type="button"
                    onClick={() => generateOne(tpl)}
                    disabled={st.generating || preflightBlocked}
                    className={`flex h-10 w-full items-center justify-center gap-2 rounded-lg text-sm font-medium transition ${
                      st.generating
                        ? 'cursor-wait bg-border text-fg-subtle'
                        : 'bg-accent text-bg-raised hover:bg-accent-hover'
                    }`}
                  >
                    {st.generating ? (
                      <>
                        <Loader className="h-4 w-4 animate-spin" /> Generation…
                      </>
                    ) : (
                      <>
                        <Sparkles className="h-4 w-4" /> Generer
                      </>
                    )}
                  </button>
                )}

                {st.generated && (
                  <>
                    <div className="flex items-center gap-2 rounded-lg border border-success/30 bg-success/5 p-2 text-xs">
                      <CheckCircle className="h-4 w-4 text-success" />
                      <span className="flex-1 truncate text-fg" title={fileFor(tpl)}>
                        {baseNameFor(tpl)} — généré
                      </span>
                    </div>

                    {/* Étape 7 (persistance 2026-08) — indicateur de
                        restauration du blob (Data Room prioritaire, sinon
                        régénération silencieuse) au retour sur l'étape.
                        L'aperçu FIDÈLE plein écran s'ouvre via « Aperçu ». */}
                    {restoring[tpl.code] && (
                      <div className="flex items-center gap-2 rounded-lg border border-border bg-bg-overlay p-2 text-[11px] text-fg-subtle">
                        <Loader className="h-3.5 w-3.5 animate-spin" />
                        Restauration de l'aperçu…
                      </div>
                    )}
                    {!st.blob && !restoring[tpl.code] && (
                      <div className="rounded-lg border border-border bg-bg-overlay p-3 text-[11px] text-fg-subtle">
                        <Eye className="mr-1 inline h-3.5 w-3.5 align-text-bottom" />
                        {readOnly
                          ? 'Aperçu indisponible (document non retrouvé dans la Data Room).'
                          : 'Aperçu momentanément indisponible. Cliquez sur « Régénérer » pour le restaurer.'}
                      </div>
                    )}

                    {/* Étape 7 (aperçu 2026-08) — MINI-APERÇU fidèle inline, visible
                        par défaut dès qu'un blob est disponible (docx-preview, même
                        rendu que le téléchargement). Repliable ; « Plein écran »
                        ouvre le même rendu en modal pleine largeur. */}
                    {/* Lot 2 — l'apercu s'efface pendant l'edition : on est
                        dans un mode ou dans l'autre, jamais les deux (et le
                        document n'est plus rendu deux fois simultanement). */}
                    {st.blob && editingTplCode !== tpl.code && (
                      <div className="rounded-lg border border-border bg-bg-overlay">
                        <button
                          type="button"
                          onClick={() =>
                            setPreviewCollapsed((prev) => ({
                              ...prev,
                              [tpl.code]: !prev[tpl.code],
                            }))
                          }
                          className="flex w-full items-center justify-between gap-2 px-3 py-2 text-[11px] font-medium text-fg-subtle hover:text-fg"
                          aria-expanded={!previewCollapsed[tpl.code]}
                        >
                          <span className="inline-flex items-center gap-1.5">
                            <Eye className="h-3.5 w-3.5" /> Aperçu du document
                          </span>
                          {previewCollapsed[tpl.code] ? (
                            <ChevronDown className="h-3.5 w-3.5" />
                          ) : (
                            <ChevronUp className="h-3.5 w-3.5" />
                          )}
                        </button>
                        {!previewCollapsed[tpl.code] && (
                          <div className="max-h-[460px] overflow-auto border-t border-border">
                            <DocumentEditor
                              docxBlob={st.blob}
                              filename={fileFor(tpl)}
                              title={docLabel(tpl)}
                              readOnly
                              forceMode="fidele"
                            />
                          </div>
                        )}
                      </div>
                    )}

                    {/* 2026-08-12 (consultation lecture seule) — en lecture seule,
                        seulement Aperçu + Télécharger ; ni Régénérer, ni Éditer,
                        ni Valider. */}
                    <div className="grid grid-cols-2 gap-2">
                      <button
                        type="button"
                        onClick={() => setPreviewingTplCode(tpl.code)}
                        disabled={!st.blob || restoring[tpl.code]}
                        title="Aperçu plein écran du document"
                        className="flex h-9 items-center justify-center gap-1.5 rounded-lg border border-border bg-bg-raised text-xs text-fg hover:border-accent disabled:opacity-60"
                      >
                        <Maximize2 className="h-3.5 w-3.5" /> Plein écran
                      </button>
                      <button
                        type="button"
                        onClick={() => void downloadDocx(tpl)}
                        disabled={st.generating || restoring[tpl.code] || (readOnly ? !st.blob : preflightBlocked)}
                        className="flex h-9 items-center justify-center gap-1.5 rounded-lg border border-border bg-bg-raised text-xs text-fg hover:border-accent disabled:opacity-60"
                      >
                        <Download className="h-3.5 w-3.5" /> .docx
                      </button>
                      {!readOnly && (
                        <>
                          <button
                            type="button"
                            onClick={() => {
                              // Régénérer repart des VARIABLES : les retouches
                              // manuelles disparaissent. On ne le fait pas dans
                              // le dos de l'employé.
                              if (st.editeManuellementAt) {
                                setRegenerationAConfirmer(tpl.code);
                                return;
                              }
                              void generateOne(tpl);
                            }}
                            disabled={st.generating || restoring[tpl.code] || preflightBlocked}
                            className="flex h-9 items-center justify-center gap-1.5 rounded-lg border border-border bg-bg-raised text-xs text-fg hover:border-accent disabled:opacity-60"
                          >
                            <RefreshCcw className="h-3.5 w-3.5" />
                            Regenerer
                          </button>
                          <button
                            type="button"
                            onClick={() => setEditingTplCode(tpl.code)}
                            disabled={st.generating || restoring[tpl.code] || !st.blob}
                            className="flex h-9 items-center justify-center gap-1.5 rounded-lg border border-accent/30 bg-accent/5 text-xs font-medium text-accent hover:bg-accent/10 disabled:opacity-60"
                            title="Éditer le document (style Word)"
                          >
                            <Pencil className="h-3.5 w-3.5" /> Éditer
                          </button>
                          {/* 2026-06-22 — Bouton .pdf retiré (conversion LibreOffice
                              jugée non fiable). Export PDF depuis Word si besoin. */}
                          {!st.validated ? (
                            <button
                              type="button"
                              onClick={() => void validateOne(tpl)}
                              disabled={st.generating || preflightBlocked}
                              className="col-span-2 flex h-9 items-center justify-center gap-1.5 rounded-lg bg-success text-xs font-semibold text-bg-raised transition hover:bg-success/85 disabled:cursor-wait disabled:opacity-60"
                            >
                              <CheckCircle className="h-3.5 w-3.5" /> Valider et deposer en Dataroom
                            </button>
                          ) : (
                            <button
                              type="button"
                              onClick={() =>
                                updateDoc(tpl.code, { validated: false })
                              }
                              className="col-span-2 flex h-9 items-center justify-center gap-1.5 rounded-lg border border-success bg-success/10 text-xs text-success"
                            >
                              <RefreshCcw className="h-3.5 w-3.5" /> Document valide — Devalider pour modifier
                            </button>
                          )}
                        </>
                      )}
                    </div>
                  </>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {nonStatutaires.length > 0 && (
        <div className="flex items-start gap-3 rounded-xl border border-warning/30 bg-warning/10 p-4">
          <AlertTriangle className="mt-0.5 h-5 w-5 flex-shrink-0 text-warning" />
          <div className="text-xs">
            <p className="font-semibold text-warning">
              {nonStatutaires.length} gerant(s) non statutaire(s)
            </p>
            <p className="mt-0.5 text-warning">
              {nonStatutaires.map((d) => `${d.prenom} ${d.nom}`).join(', ')} —
              un acte de nomination distinct doit etre genere ci-dessus
              (template ACTE_NOMINATION).
            </p>
          </div>
        </div>
      )}

      {statutaires.length === 0 && (
        <div className="flex items-start gap-3 rounded-xl border border-danger/30 bg-danger/10 p-4">
          <AlertTriangle className="mt-0.5 h-5 w-5 flex-shrink-0 text-danger" />
          <p className="text-xs text-danger">
            Aucun gerant statutaire detecte. Retournez a l'etape 5 pour en
            designer au moins un avant de generer les statuts.
          </p>
        </div>
      )}

      {/* Consultation lecture seule : pas de validation d'étape. */}
      {!readOnly && (
        <div className="flex items-center justify-end pt-2">
          <button
            type="submit"
            disabled={!canSubmit || saving}
            className={`flex items-center gap-2 rounded-lg px-8 h-12 font-medium transition ${
              canSubmit && !saving
                ? 'bg-accent text-bg-raised hover:bg-accent-hover'
                : 'cursor-not-allowed bg-border text-fg-subtle'
            }`}
            title={
              canSubmit
                ? undefined
                : 'Les statuts (document principal) doivent etre generes et valides.'
            }
          >
            {saving ? 'Enregistrement…' : 'Valider et continuer'}
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>
      )}

      {/* Étape 7 (aperçu 2026-08) — modal d'aperçu FIDÈLE plein écran. Le
          document s'affiche sur toute la largeur disponible et défile sur toute
          sa longueur, en tête le libellé du type de document. Rendu fiable via
          DocumentEditor (docx-preview) en lecture seule — jamais un placeholder. */}
      {previewingTplCode && (() => {
        const tpl = templates.find((t) => t.code === previewingTplCode);
        const st = docs[previewingTplCode];
        if (!tpl || !st?.blob) return null;
        return (
          <div
            role="dialog"
            aria-modal="true"
            className="fixed inset-0 z-50 flex flex-col bg-black/60 backdrop-blur-sm"
            onClick={(e) => {
              if (e.target === e.currentTarget) setPreviewingTplCode(null);
            }}
          >
            <div className="m-auto flex h-[90vh] w-[min(1200px,95vw)] flex-col overflow-hidden rounded-xl bg-bg-raised shadow-xl">
              <div className="flex items-center justify-between border-b border-border bg-bg-overlay px-4 py-3">
                <div className="flex items-center gap-2">
                  <Eye className="h-4 w-4 text-accent" />
                  <h3 className="text-sm font-semibold text-fg">
                    Aperçu — {docLabel(tpl)}
                  </h3>
                </div>
                <button
                  type="button"
                  onClick={() => setPreviewingTplCode(null)}
                  className="rounded p-1 text-fg-subtle hover:bg-border"
                  aria-label="Fermer"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
              <div className="flex-1 overflow-auto p-4">
                <DocumentEditor
                  docxBlob={st.blob}
                  filename={fileFor(tpl)}
                  title={docLabel(tpl)}
                  readOnly
                />
              </div>
            </div>
          </div>
        );
      })()}

      {/* Sprint 2026-06-12 — modal d'édition WYSIWYG TipTap. */}
      {/*
        ÉDITION BUREAUTIQUE FIDÈLE (lot 3, 2026-09-07).

        La modale n'ouvre plus TipTap : son aller-retour `.docx → HTML → .docx`
        détruisait la mise en page du directeur (mesuré au lot 2 : styles.xml
        absent, 49 paragraphes stylés perdus, 11 numérotations et 89 alignements
        à zéro). Collabora édite le .docx lui-même, sans format intermédiaire.

        La séparation obtenue au lot 2 tient : on est en aperçu OU en édition,
        jamais les deux — l'aperçu en ligne se démonte pendant l'édition.
      */}
      {/*
        AVERTISSEMENT DE RÉGÉNÉRATION (lot 3).

        Régénérer repart des variables du dossier : toutes les retouches faites
        dans l'éditeur disparaissent. Une confirmation générique — « êtes-vous
        sûr ? » — se clique sans se lire ; celle-ci NOMME ce qui sera perdu,
        avec la date de l'édition.
      */}
      {regenerationAConfirmer && (() => {
        const tpl = templates.find((t) => t.code === regenerationAConfirmer);
        const st = docs[regenerationAConfirmer];
        if (!tpl) return null;
        const quand = st?.editeManuellementAt
          ? new Date(st.editeManuellementAt).toLocaleString('fr-FR', {
              day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit',
            })
          : null;
        return (
          <div
            role="dialog"
            aria-modal="true"
            data-testid="confirmation-regeneration"
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-6 backdrop-blur-sm"
            onClick={(e) => {
              if (e.target === e.currentTarget) setRegenerationAConfirmer(null);
            }}
          >
            <div className="w-[min(560px,95vw)] rounded-xl bg-bg-raised p-5 shadow-xl">
              <div className="mb-3 flex items-center gap-2">
                <AlertTriangle className="h-5 w-5 text-warning" />
                <h3 className="text-sm font-semibold text-fg">
                  Régénérer effacera vos modifications
                </h3>
              </div>
              <p className="mb-2 text-sm leading-relaxed text-fg">
                Ce document ({docLabel(tpl)}) a été <strong>modifié à la main</strong>
                {quand ? <> le <strong>{quand}</strong></> : null}. La régénération repart
                des données du dossier :{' '}
                <strong>vos modifications manuelles seront remplacées</strong>.
              </p>
              <p className="mb-4 text-xs leading-relaxed text-fg-subtle">
                La version actuelle ne disparaît pas : elle reste consultable dans
                «&nbsp;Anciennes versions&nbsp;» du document, et peut être restaurée.
              </p>
              <div className="flex items-center justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setRegenerationAConfirmer(null)}
                  className="rounded-lg border border-border bg-bg-raised px-3 py-2 text-xs font-semibold text-fg"
                >
                  Conserver mes modifications
                </button>
                <button
                  type="button"
                  data-testid="confirmer-regeneration"
                  onClick={() => {
                    setRegenerationAConfirmer(null);
                    void generateOne(tpl);
                  }}
                  className="rounded-lg bg-warning px-3 py-2 text-xs font-semibold text-bg-raised"
                >
                  Régénérer et remplacer
                </button>
              </div>
            </div>
          </div>
        );
      })()}

      {editingTplCode && (() => {
        const tpl = templates.find((t) => t.code === editingTplCode);
        const st = docs[editingTplCode];
        if (!tpl) return null;
        if (!st?.documentId) {
          // Sans document persisté, il n'y a rien à ouvrir dans l'éditeur : on
          // le dit au lieu d'afficher un cadre vide.
          return (
            <div
              role="dialog"
              aria-modal="true"
              className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-6 backdrop-blur-sm"
              onClick={() => setEditingTplCode(null)}
            >
              <div className="max-w-md rounded-xl bg-bg-raised p-5 text-sm text-fg shadow-xl">
                Ce document n'est pas encore enregistré : régénérez-le avant de l'éditer.
              </div>
            </div>
          );
        }
        return (
          <div
            role="dialog"
            aria-modal="true"
            className="fixed inset-0 z-50 flex flex-col bg-black/60 backdrop-blur-sm"
            onClick={(e) => {
              if (e.target === e.currentTarget) setEditingTplCode(null);
            }}
          >
            <div className="m-auto flex h-[92vh] w-[min(1400px,97vw)] flex-col overflow-hidden rounded-xl bg-bg-raised shadow-xl">
              <div className="flex items-center justify-between border-b border-border bg-bg-overlay px-4 py-3">
                <div className="flex items-center gap-2">
                  <Pencil className="h-4 w-4 text-accent" />
                  <h3 className="text-sm font-semibold text-fg">
                    Édition — {docLabel(tpl)}
                  </h3>
                </div>
                <button
                  type="button"
                  onClick={() => setEditingTplCode(null)}
                  className="rounded p-1 text-fg-subtle hover:bg-border"
                  aria-label="Fermer"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
              <div className="min-h-0 flex-1">
                <CollaboraEditor
                  documentId={st.documentId}
                  titre={docLabel(tpl)}
                  onClose={() => setEditingTplCode(null)}
                  onEdited={() => void recupererApresEdition(tpl)}
                />
              </div>
            </div>
          </div>
        );
      })()}
    </form>
  );
}
