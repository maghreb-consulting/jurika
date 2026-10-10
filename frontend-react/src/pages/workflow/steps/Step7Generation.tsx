import { useEffect, useMemo, useState } from 'react';
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
  GenerationRefuseeError,
  listTemplatesForWorkflow,
  type DonneeManquante,
  type DonneeNommee,
  type TemplateInfo,
} from '../../../services/workflowDocumentService';
import { workflowService, type DonneeAttendue } from '../../../services/workflow.service';
import { DonneesAttendues } from '../../../components/workflow/DonneesAttendues';
import { RetourGeneration } from '../../../components/workflow/RetourGeneration';
import { InfoBulle, TexteAide } from '../../../components/ui/Aide';
import { DocumentEditor } from '../../../components/document/DocumentEditor';
import { CollaboraEditor } from '../../../components/document/CollaboraEditor';
import { dataroomService } from '../../../services/dataroom.service';
import {
  bouclesParDocument,
  CHAMPS_CREATION,
  CHOIX_STATUT_2,
  champsParDocument,
  DOCUMENTS_PARCOURS,
  REPRISES_AUTOMATIQUES,
  type ChampCreation,
} from './documents-creation';
import { buildDocFilename } from '../../../components/workflow/workflowFilename';
import type { DocumentType } from '../../../types/dataroom';

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
  /** Lot L3 : donnees internes manquantes nommees par le serveur (generation refusee). */
  refus?: DonneeManquante[];
  /** Lot L3 : donnees externes manquantes, marquees « À OBTENIR » dans l'acte et reclamees. */
  aObtenir?: DonneeNommee[];
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
  // Lot B — codes du corpus du 9 septembre. Les codes `_DIRECTEUR` etaient ceux
  // des modeles d'aout, sortis du manifeste au lot A.
  SARL: 'STATUTS_SARL',
  SARL_AU: 'STATUTS_SARL_AU',
};

/**
 * Code template de l'acte de nomination du gerant selectionne dynamiquement
 * (variante SARL vs SARL_AU). N'est inclus dans l'allowlist que si au moins
 * un gerant n'est PAS statutaire (RG transverse 2026-06-09).
 */
const ACTE_BY_FORME: Record<'SARL' | 'SARL_AU', string> = {
  SARL: 'ACTE_NOMINATION_GERANT',
  SARL_AU: 'ACTE_NOMINATION_GERANT',
};

/**
 * Lot B — LES DOCUMENTS QUE L'ETAPE 7 PEUT PRODUIRE : les DIX du statut 2.
 *
 * L'allowlist du lot 5 en figeait cinq, ecrits a la main. Le parcours du
 * 9 septembre en compte dix, et le catalogue les derive — y compris les deux
 * lignes qui portent chacune deux variantes. On ne conserve donc de la liste que
 * son role : ne pas pousser dans l'UI les treize autres modeles du corpus, qui
 * relevent d'autres statuts du ticket.
 *
 * Les variantes sont toutes deux exposees ici ; c'est `codesRetenus` qui tranche
 * entre elles, d'apres la forme juridique et la voie retenue pour le siege.
 */
const TEMPLATES_STATUT_2: string[] = CHOIX_STATUT_2.flatMap((c) => c.codes);

/*
 * Lot B (2026-09-11) — `DOCUMENTS_OBLIGATOIRES` est retiré.
 *
 * Le lot 5 cochait d'office six documents « obligatoires dans tous les
 * dossiers », non décochables. Le parcours du 9 septembre dit autre chose, et
 * c'est une décision du cabinet : **Statuts et Annonce légale sont cochés par
 * défaut ; l'employé choisit les huit autres** — y compris ceux dont la
 * condition porte « tous dossiers », parce que c'est lui qui sait si le cabinet
 * dépose au nom du client.
 *
 * La liste vit désormais au catalogue (`CHOIX_STATUT_2`), dérivé du parcours.
 */

/**
 * Étape 7 (noms propres 2026-08) — Libellés « métier » affichés à l'utilisateur.
 * Le code technique du template (ex. `STATUTS_SARL_DIRECTEUR`) reste la clé
 * interne (payload, dataroom, allowlist) mais n'est JAMAIS montré : ni titre de
 * carte, ni nom de fichier téléchargé. On mappe chaque code vers le seul type
 * du document.
 */
const DOCUMENT_LABELS: Record<string, string> = {
  STATUTS_SARL: 'Statuts',
  STATUTS_SARL_AU: 'Statuts',
  ACTE_NOMINATION_GERANT: 'Acte de nomination du gérant',
  ANNONCE_LEGALE_CONSTITUTION: 'Annonce légale',
  DEMANDE_TAXE_PROFESSIONNELLE: "Demande d'inscription à la taxe professionnelle",
  DECLARATION_EXISTENCE: "Déclaration d'existence",
  DECLARATION_IMMATRICULATION_RC: "Déclaration d'immatriculation au RC (modèle 2)",
  PV_DEFAUT_QUORUM_SARL: 'PV — Défaut de quorum',
  PV_DEFAUT_QUORUM_SARL_AU: 'PV — Défaut de quorum',
  PV_IRREGULARITE_CONVOCATION_SARL: 'PV — Irrégularité de convocation',
  PV_IRREGULARITE_CONVOCATION_SARL_AU: 'PV — Irrégularité de convocation',
};

/**
 * Lot B — le libellé d'un document, lu au catalogue plutôt qu'à une table écrite
 * à la main : le corpus en compte vingt-trois, et il changera.
 */
function libelleDocument(code: string): string {
  const doc = DOCUMENTS_PARCOURS.find((d) => d.code === code);
  return doc?.libelle || DOCUMENT_LABELS[code] || code;
}

/**
 * Lot B — LA SAISIE D'UNE BOUCLE.
 *
 * Un bailleur, trois bénéficiaires effectifs, cinq traitements de données : ces
 * champs ne se saisissent pas une fois mais autant de fois qu'il y a
 * d'occurrences. Le rang de chacune n'est jamais demandé — il est dérivé côté
 * serveur, parce que demander « quel numéro ? » revient à faire compter
 * l'employé et à lui faire porter une erreur de numérotation.
 */
function BoucleSaisie({
  boucle,
  champs,
  occurrences,
  onChange,
}: {
  boucle: { nom: string; label: string };
  champs: ChampCreation[];
  occurrences: Record<string, string>[];
  onChange: (items: Record<string, string>[]) => void;
}) {
  function modifier(index: number, cle: string, valeur: string) {
    const next = occurrences.map((o, i) => (i === index ? { ...o, [cle]: valeur } : o));
    onChange(next);
  }

  return (
    <div className="rounded-lg border border-border p-4" data-testid={`boucle-${boucle.nom}`}>
      <div className="flex items-center justify-between">
        <p className="text-xs font-semibold uppercase tracking-wide text-accent">
          {boucle.label}
        </p>
        <button
          type="button"
          onClick={() => onChange([...occurrences, {}])}
          className="rounded-md border border-border px-2 py-0.5 text-xs text-fg hover:bg-bg-overlay"
        >
          Ajouter
        </button>
      </div>

      {occurrences.length === 0 && (
        <p className="mt-2 text-[11px] text-fg-subtle">
          Aucune occurrence. Le document sortira sans cette section &mdash; ce qui est
          correct si le dossier n&rsquo;en comporte pas.
        </p>
      )}

      {occurrences.map((occurrence, index) => (
        <div key={index} className="mt-3 rounded-md border border-border/60 p-3">
          <div className="flex items-center justify-between">
            <p className="text-xs font-medium text-fg">
              {boucle.label} n&deg; {index + 1}
            </p>
            <button
              type="button"
              onClick={() => onChange(occurrences.filter((_, i) => i !== index))}
              className="text-xs text-danger hover:underline"
            >
              Retirer
            </button>
          </div>
          <div className="mt-2 grid grid-cols-1 gap-3 sm:grid-cols-2">
            {champs.map((champ) => (
              <div key={champ.cle}>
                <label
                  htmlFor={`boucle-${boucle.nom}-${index}-${champ.cle}`}
                  className="mb-1 block text-xs font-medium text-fg"
                >
                  {champ.label}
                </label>
                {champ.type === 'select' ? (
                  <select
                    id={`boucle-${boucle.nom}-${index}-${champ.cle}`}
                    value={occurrence[champ.cle] ?? ''}
                    onChange={(e) => modifier(index, champ.cle, e.target.value)}
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
                    id={`boucle-${boucle.nom}-${index}-${champ.cle}`}
                    rows={2}
                    value={occurrence[champ.cle] ?? ''}
                    onChange={(e) => modifier(index, champ.cle, e.target.value)}
                    className="w-full rounded-lg border border-border bg-bg px-3 py-2 text-sm text-fg"
                  />
                ) : (
                  <input
                    id={`boucle-${boucle.nom}-${index}-${champ.cle}`}
                    type={champ.type === 'date' ? 'date' : champ.type === 'number' ? 'number' : 'text'}
                    value={occurrence[champ.cle] ?? ''}
                    onChange={(e) => modifier(index, champ.cle, e.target.value)}
                    className="w-full rounded-lg border border-border bg-bg px-3 py-2 text-sm text-fg"
                  />
                )}
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

/** Libellé propre d'un template (jamais le code brut). */
function docLabel(tpl: TemplateInfo): string {
  return DOCUMENT_LABELS[tpl.code] || tpl.documentKind || tpl.code;
}

/**
 * Lot B — LES CODES DE MODÈLE QUE LES LIGNES RETENUES IMPLIQUENT.
 *
 * Une ligne du parcours porte un ou deux modèles. Quand elle en porte deux,
 * l'employé ne choisit pas : la forme juridique décide entre SARL et SARL AU, la
 * voie retenue pour le siège décide entre bail et domiciliation. Si la donnée
 * n'est pas encore là, on retient les deux variantes plutôt qu'aucune — mieux
 * vaut un champ de trop qu'un document qu'on ne sait plus produire.
 */
export function codesRetenus(
  lignes: Set<number>,
  formeJuridique: string | undefined,
  voieSiege: string | undefined,
): string[] {
  const out: string[] = [];
  for (const choix of CHOIX_STATUT_2) {
    if (!lignes.has(choix.ligne)) continue;
    if (choix.codes.length === 1) {
      out.push(choix.codes[0]);
      continue;
    }
    const variante = choix.codes.find((code) => {
      if (code === 'STATUTS_SARL') return formeJuridique === 'SARL';
      if (code === 'STATUTS_SARL_AU') return formeJuridique === 'SARL_AU';
      if (code === 'CONTRAT_BAIL') return voieSiege === 'BAIL';
      if (code === 'CONTRAT_DOMICILIATION') return voieSiege === 'DOMICILIATION';
      return false;
    });
    if (variante) out.push(variante);
    else out.push(...choix.codes);
  }
  return out;
}

/** `BENEFICIAIRES_EFFECTIFS` -> `beneficiairesEffectifs`, comme côté serveur. */
export function cleDeBoucle(nom: string): string {
  const mots = nom.toLowerCase().split('_');
  return mots[0] + mots.slice(1).map((m) => m.charAt(0).toUpperCase() + m.slice(1)).join('');
}

/**
 * Étape 7 (persistance 2026-08) — code template → `DocumentType` juridique sous
 * lequel le document est déposé en Data Room (miroir de `mapPieceToDocumentType`
 * côté {@link CreationSarlWorkflowPage}). Sert à retrouver, au montage, le
 * document en vigueur correspondant pour ré-hydrater son blob.
 */
const TEMPLATE_TO_DOCTYPE: Record<string, DocumentType> = {
  STATUTS_SARL: 'STATUTS',
  STATUTS_SARL_AU: 'STATUTS',
  ACTE_NOMINATION_GERANT: 'ACTE_NOMINATION',
  ANNONCE_LEGALE_CONSTITUTION: 'ANNONCE_JAL',
  // Lot 5 — chaque formulaire a SON type : sans cela les trois tombaient en
  // « AUTRE », et deux « AUTRE » de meme titre se dedupliquent en Data Room.
  DEMANDE_TAXE_PROFESSIONNELLE: 'DEMANDE_TAXE_PROFESSIONNELLE',
  DECLARATION_EXISTENCE: 'DECLARATION_EXISTENCE',
  DECLARATION_IMMATRICULATION_RC: 'DECLARATION_IMMATRICULATION_RC',
  // Lot B — les treize modeles restants du corpus du 9 septembre. Sans type
  // propre, ils tombaient tous en « AUTRE » et deux « AUTRE » de meme titre se
  // dedupliquent en Data Room : un document en effacait un autre.
  CONTRAT_BAIL: 'CONTRAT_BAIL',
  CONTRAT_DOMICILIATION: 'CONTRAT_DOMICILIATION',
  ETAT_ACTES_SOCIETE_EN_FORMATION: 'ETAT_ACTES_FORMATION',
  ATTESTATION_SOUSCRIPTION_LIBERATION: 'ATTESTATION_SOUSCRIPTION_LIBERATION',
  POUVOIR_FORMALITES_CREATION: 'POUVOIR',
  BORDEREAU_REMISE_DOSSIER: 'BORDEREAU_REMISE',
  FICHE_RENSEIGNEMENTS_CREATION: 'FICHE_RENSEIGNEMENTS',
  RAPPORT_COMMISSAIRE_APPORTS: 'RAPPORT_COMMISSAIRE_APPORTS',
  DEMANDE_AFFILIATION_CNSS: 'DEMANDE_AFFILIATION_CNSS',
  DECLARATION_BENEFICIAIRES_EFFECTIFS: 'DECLARATION_BENEFICIAIRES_EFFECTIFS',
  DEMANDE_DEBLOCAGE_CAPITAL: 'DEMANDE_DEBLOCAGE_CAPITAL',
  DECLARATION_CNDP: 'DECLARATION_CNDP',
  DEMANDE_ADHESION_SIMPL: 'DEMANDE_ADHESION_SIMPL',
  NOTE_CONFORMITE_MENTIONS_LEGALES: 'NOTE_CONFORMITE',
  NOTE_ANNULATION_DOSSIER: 'NOTE_ANNULATION',
  LETTRE_RETRAIT_DEPOT: 'LETTRE_RETRAIT_DEPOT',
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
  /**
   * Lot B — LES DIX DOCUMENTS DU STATUT 2, ET CE QUI EST COCHÉ D'OFFICE.
   *
   * Décision du cabinet, non rouvrable : **Statuts et Annonce légale** sont
   * cochés par défaut ; les huit autres sont décochés, et l'employé choisit —
   * avec, sous les yeux, la condition d'application que le parcours énonce. Le
   * système ne décide pas à sa place.
   *
   * La sélection est faite par LIGNE du parcours, pas par code de modèle : une
   * ligne peut porter deux variantes (bail ou domiciliation, SARL ou SARL AU) et
   * ce n'est pas l'employé qui tranche entre elles — c'est la voie retenue pour
   * le siège, et c'est `$ASSOCIE_UNIQUE`.
   */
  const [lignesRetenues, setLignesRetenues] = useState<Set<number>>(() => {
    const memorise = previous.lignesRetenues as number[] | undefined;
    if (memorise) return new Set(memorise);
    return new Set(CHOIX_STATUT_2.filter((c) => c.cocheParDefaut).map((c) => c.ligne));
  });

  /**
   * Lot B — LES SAISIES DES BOUCLES, par nom de boucle.
   *
   * Chaque occurrence est un objet dont les clés sont celles du catalogue. Le
   * mapper les relit sous `payload.creation.<boucle>` et le moteur les expanse
   * dans le `.docx` — la numérotation (`$BE_NUMERO`, `$TRAITEMENT_NUMERO`) est
   * DÉRIVÉE du rang, jamais saisie : la demander reviendrait à faire compter
   * l'employé.
   */
  const [boucles, setBoucles] = useState<Record<string, Record<string, string>[]>>(
    () => (previous.boucles as Record<string, Record<string, string>[]> | undefined) ?? {},
  );

  const [templates, setTemplates] = useState<TemplateInfo[]>([]);
  /** Lot L3 : donnees externes reclamees pour les documents du ticket (regle des variables). */
  const [attendues, setAttendues] = useState<DonneeAttendue[]>([]);
  useEffect(() => {
    if (!ticketId || readOnly) return undefined;
    let actif = true;
    workflowService
      .donneesAttendues(ticketId)
      .then((l) => actif && setAttendues(l))
      .catch(() => actif && setAttendues([]));
    return () => {
      actif = false;
    };
  }, [ticketId, readOnly]);
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
  /**
   * Lot B — la voie retenue pour le siège, saisie à l'étape 2. C'est elle qui
   * décide entre contrat de bail et contrat de domiciliation : la ligne 2 du
   * parcours porte les deux, et sa condition dit « selon la voie retenue pour le
   * siège ». Ce n'est donc pas un choix offert à l'étape 7.
   */
  const voieSiege = siege.justificatifType as string | undefined;
  const cap = unwrapStep(data.step3, 'capital');
  const formeFromStep1 = den.formeJuridique as string | undefined;
  const forme: 'SARL' | 'SARL_AU' = formeFromStep1 === 'SARL_AU' ? 'SARL_AU' : 'SARL';
  const STATUTS_TEMPLATE_CODE = STATUTS_BY_FORME[forme];
  const ACTE_TEMPLATE_CODE = ACTE_BY_FORME[forme];
  const dirs = ((data.step5 as { dirigeants?: Array<Record<string, unknown>> })
    ?.dirigeants ?? []) as Array<Record<string, unknown>>;
  const statutaires = dirs.filter((d) => d.isStatutaire === true);
  const nonStatutaires = dirs.filter((d) => d.isStatutaire === false);
  const hasNonStatutaire = nonStatutaires.length > 0;

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
        const allow = new Set<string>(TEMPLATES_STATUT_2);
        // L'acte de nomination n'a de sens que si un gerant n'est PAS statutaire.
        // La ligne 5 reste cochable par l'employe, mais on ne lui propose pas de
        // generer un acte dont la condition du parcours dit qu'il est sans objet.
        if (!hasNonStatutaire) allow.delete(ACTE_TEMPLATE_CODE);
        // La variante non retenue par la forme juridique sort de la liste : on ne
        // produit pas des statuts de SARL pour un associe unique.
        allow.delete(forme === 'SARL' ? 'STATUTS_SARL_AU' : 'STATUTS_SARL');
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

  /**
   * Lot L3 (P2, RG-VAR-05) — LA CHARGE UTILE EST CONSTRUITE PAR LE SERVEUR.
   *
   * Le navigateur n'envoie plus que le ticket : ai-service relit le magasin de
   * variables (ConstructeurChargeUtileCreation, workflow-service). Une correction
   * faite a un seul endroit se repercute sur toutes les generations suivantes, et
   * plus aucune valeur n'est inventee ici (la date du jour et « Casablanca »
   * remplissaient la date et le lieu de signature).
   *
   * `persister` : enregistre d'abord l'etat de l'etape (documents retenus, saisies
   * complementaires), sans quoi le magasin generait avec la saisie precedente.
   */
  const chargeServeur = async (persister: boolean): Promise<Record<string, unknown>> => {
    if (!ticketId) {
      throw new GenerationRefuseeError(
        'Ce parcours n’est rattaché à aucun ticket : rechargez la page, puis relancez la génération.',
        'SANS_TICKET',
      );
    }
    if (persister && !readOnly) {
      await workflowService.save(ticketId, 7, { step7: etatEtape() });
    }
    return { ticketId };
  };

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
              await chargeServeur(false),
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
      refus: [],
      validated: false,
    });
    try {
      const { blob, filename, donneesAObtenir } = await generateDocument(
        'CREATION_SARL',
        tpl.code,
        await chargeServeur(true),
      );
      updateDoc(tpl.code, {
        generating: false,
        generated: true,
        blob,
        filename,
        version: nextVersion,
        aObtenir: donneesAObtenir,
      });
      // Pas de DEPOT dataroom ici : on attend la validation de l'employe. Mais
      // le document est PERSISTE comme brouillon, sans quoi il ne survivrait pas
      // a un rechargement (lot 2).
      await persistBrouillon(tpl, blob, cleanDocFilename(tpl, denomination, forme, nextVersion));
      if (ticketId) setAttendues(await workflowService.donneesAttendues(ticketId));
    } catch (err) {
      updateDoc(tpl.code, {
        generating: false,
        error: err instanceof Error ? err.message : 'Génération impossible.',
        refus: err instanceof GenerationRefuseeError ? err.donneesManquantes : [],
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
        await chargeServeur(true),
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
          await chargeServeur(true),
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
   * Lot B — LES CODES DE MODÈLE QUI DÉCOULENT DES LIGNES RETENUES.
   *
   * Une ligne du parcours peut porter deux variantes. Le choix entre elles n'est
   * pas offert : la forme juridique (étape 1) décide entre SARL et SARL AU, la
   * voie retenue pour le siège (étape 2) décide entre bail et domiciliation.
   * Offrir ce choix serait laisser produire des statuts de SARL pour un associé
   * unique.
   */
  const documentsRetenus = useMemo(
    () => codesRetenus(lignesRetenues, forme, voieSiege),
    [lignesRetenues, forme, voieSiege],
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

  /**
   * Lot B — LES BOUCLES. Un bailleur, trois bénéficiaires effectifs, cinq
   * traitements de données : ces champs ne se saisissent pas une fois mais
   * autant de fois qu'il y a d'occurrences. Elles suivent la même règle que les
   * champs simples — une boucle n'apparaît que si un document qui la porte est
   * retenu.
   */
  const bouclesAffichees = useMemo(
    () => bouclesParDocument(documentsRetenus),
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

  /** Etat persiste de l'etape : documents, selection et saisies propres aux formulaires. */
  function etatEtape(): Record<string, unknown> {
    return {
      statutsDocumentId: statutsState?.documentId ?? null,
      actesNominationIds: [],
      statutsValides: !!statutsState?.validated,
      documents: documentsPayload,
      // Lot 5 — la selection et les saisies propres aux formulaires vivent avec
      // l'etape : revenir dessus (ou decocher un document) ne doit rien effacer.
      lignesRetenues: Array.from(lignesRetenues),
      complements,
      boucles,
    };
  }

  return (
    <form
      noValidate
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit(etatEtape());
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
          <h4 className="text-sm font-semibold text-fg">
            Les dix documents de ce statut — lesquels générer ?
          </h4>
          <p className="mt-1 text-xs text-fg-subtle">
            <strong className="text-fg">Statuts</strong> et{' '}
            <strong className="text-fg">Annonce légale</strong> sont retenus par défaut. Les huit
            autres sont à vous : chacun porte, sous son titre, la condition d&rsquo;application
            telle qu&rsquo;elle figure au parcours. Le système ne décide pas à votre place.
          </p>
          <div className="mt-3 space-y-2">
            {CHOIX_STATUT_2.map((choix) => {
              const retenu = lignesRetenues.has(choix.ligne);
              return (
                <label
                  key={choix.ligne}
                  data-testid={`choix-document-${choix.ligne}`}
                  className={`flex items-start gap-2 rounded-lg border px-3 py-2 text-sm ${
                    retenu ? 'border-accent/40 bg-accent/5' : 'border-border'
                  }`}
                >
                  <input
                    type="checkbox"
                    className="mt-0.5"
                    checked={retenu}
                    disabled={readOnly}
                    onChange={() =>
                      setLignesRetenues((prev) => {
                        const next = new Set(prev);
                        if (next.has(choix.ligne)) next.delete(choix.ligne);
                        else next.add(choix.ligne);
                        return next;
                      })
                    }
                  />
                  <span className="min-w-0">
                    <span className="block font-medium text-fg">{choix.libelle}</span>
                    {choix.condition && (
                      <span className="mt-0.5 block text-[11px] text-fg-subtle">
                        {choix.condition}
                      </span>
                    )}
                  </span>
                </label>
              );
            })}
          </div>
        </div>
      )}

      {!readOnly && documentsRetenus.length > 0 && (
        <section aria-labelledby="signature-actes" className="rounded-xl border border-border bg-bg-raised p-5">
          <h4 id="signature-actes" className="flex items-center gap-1 text-sm font-semibold text-fg">
            Signature des actes
            <InfoBulle
              libelle="Pourquoi le lieu et la date de signature ?"
              texte="Les statuts et la plupart des actes se terminent par « Fait à …, le … ». La plateforme n’invente ni la ville ni la date : sans elles, ces actes ne sont pas générés."
            />
          </h4>
          <TexteAide cle="creation-signature" titre="Lieu et date de signature">
            <p>
              Indiquez la ville et la date auxquelles les associés signent les actes. Elles servent à
              tous les documents retenus ; corrigez-les ici, et la correction vaut pour toutes les
              générations suivantes.
            </p>
          </TexteAide>
          <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div>
              <label htmlFor="complement-lieuSignature" className="mb-1 block text-xs font-medium text-fg">
                Lieu de signature (ville)
              </label>
              <input
                id="complement-lieuSignature"
                type="text"
                value={complements.lieuSignature ?? ''}
                onChange={(e) => setComplements((prev) => ({ ...prev, lieuSignature: e.target.value }))}
                className="w-full rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-sm text-fg"
              />
            </div>
            <div>
              <label htmlFor="complement-dateSignature" className="mb-1 block text-xs font-medium text-fg">
                Date de signature
              </label>
              <input
                id="complement-dateSignature"
                type="date"
                value={complements.dateSignature ?? ''}
                onChange={(e) => setComplements((prev) => ({ ...prev, dateSignature: e.target.value }))}
                className="w-full rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-sm text-fg"
              />
            </div>
          </div>
        </section>
      )}

      {!readOnly && groupesChamps.length > 0 && (
        <div className="rounded-xl border border-border bg-bg-raised p-5">
          <h4 className="text-sm font-semibold text-fg">
            Complements demandes par les documents retenus
          </h4>
          <p className="mt-1 text-xs text-fg-subtle">
            Ces champs n&rsquo;apparaissent que parce que le document qui les consomme est
            retenu. Décochez un document et ses questions disparaissent.
          </p>
          <p className="mt-1 text-xs text-fg-subtle">
            Aucun n&rsquo;est marqué obligatoire, et ce n&rsquo;est pas un oubli : c&rsquo;est
            le <strong className="text-fg">document produit</strong> qui tranche. Une valeur
            manquante au milieu d&rsquo;une phrase fait refuser la génération et l&rsquo;acte
            n&rsquo;est pas produit ; une case d&rsquo;imprimé administratif laissée blanche
            reste recevable et passe.
          </p>
          {groupesChamps.map((groupe) => (
            <div key={groupe.code} className="mt-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-accent">
                {libelleDocument(groupe.code)}
              </p>
              <div className="mt-2 grid grid-cols-1 gap-3 sm:grid-cols-2">
                {groupe.champs.map((champ) => (
                  <div key={champ.cle}>
                    <label
                      htmlFor={`complement-${champ.cle}`}
                      className="mb-1 block text-xs font-medium text-fg"
                    >
                      {champ.label}
                      {champ.documents.length > 1 && (
                        <span className="ml-1 font-normal text-fg-subtle">
                          — sert aussi a{' '}
                          {champ.documents
                            .filter((d) => d !== groupe.code)
                            .map(libelleDocument)
                            .join(', ')}
                        </span>
                      )}
                    </label>
                    {champ.type === 'select' ? (
                      <select
                        id={`complement-${champ.cle}`}
                        value={complements[champ.cle] ?? ''}
                        onChange={(e) =>
                          setComplements((prev) => ({
                            ...prev,
                            [champ.cle]: e.target.value,
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
                        id={`complement-${champ.cle}`}
                        rows={2}
                        value={complements[champ.cle] ?? ''}
                        onChange={(e) =>
                          setComplements((prev) => ({
                            ...prev,
                            [champ.cle]: e.target.value,
                          }))
                        }
                        className="w-full rounded-lg border border-border bg-bg px-3 py-2 text-sm text-fg"
                      />
                    ) : (
                      <input
                        id={`complement-${champ.cle}`}
                        type={champ.type === 'date' ? 'date' : 'text'}
                        value={complements[champ.cle] ?? ''}
                        onChange={(e) =>
                          setComplements((prev) => ({
                            ...prev,
                            [champ.cle]: e.target.value,
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

          {bouclesAffichees.length > 0 && (
            <div className="mt-6 space-y-4">
              {bouclesAffichees.map((boucle) => (
                <BoucleSaisie
                  key={boucle.nom}
                  boucle={boucle}
                  champs={CHAMPS_CREATION.filter((c) => c.boucle === boucle.nom)}
                  occurrences={boucles[cleDeBoucle(boucle.nom)] ?? []}
                  onChange={(items) =>
                    setBoucles((prev) => ({ ...prev, [cleDeBoucle(boucle.nom)]: items }))
                  }
                />
              ))}
            </div>
          )}

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
        {templates.filter((t) => documentsRetenus.includes(t.code)).map((tpl) => {
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
                <RetourGeneration erreur={st.error} refus={st.refus} aObtenir={st.aObtenir} />
                {!readOnly && (
                  <DonneesAttendues
                    attendues={attendues.filter((a) => a.templateCode === tpl.code)}
                    enCours={st.generating}
                    onRegenerer={() => generateOne(tpl)}
                  />
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
