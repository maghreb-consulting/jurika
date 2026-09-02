import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AlertCircle,
  CheckCircle,
  Info,
  Megaphone,
  Paperclip,
  Plus,
  Sparkles,
  Trash2,
} from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/TextField';
import { Select } from '../../components/ui/Select';
import { WorkflowShell } from '../../components/workflow/WorkflowShell';
import type { RoadmapStep } from '../../components/workflow/WorkflowRoadmap';
import { ticketService } from '../../services/ticket.service';
import { workflowService, type DossierParties } from '../../services/workflow.service';
import { CancelTicketDialog } from '../tickets/CancelTicketDialog';
import { useWorkflow } from './useWorkflow';
import { useStepAutosave } from './useStepAutosave';
import { WorkflowBoot } from './WorkflowBoot';
import { DossierAutocomplete } from '../../components/workflow/DossierAutocomplete';
import { dataroomService } from '../../services/dataroom.service';
import {
  WorkflowDocumentBlock,
  freshDocState,
  useDocumentBlocks,
  type DocState,
} from '../../components/workflow/WorkflowDocumentBlock';
import {
  listTemplatesForWorkflow,
  type TemplateInfo,
} from '../../services/workflowDocumentService';
import { IncidentSeancePanel } from '../../components/workflow/IncidentSeancePanel';
import { ConvocationPanel, FeuillePresencePanel } from '../../components/workflow/SeanceDocPanels';
import {
  SeanceFormFields,
  sanitizeFilename,
  useSeanceForm,
  type AssocieInput,
  type GerantInput,
} from '../../components/workflow/SeanceForm';
import {
  PiecesJointesPanel,
  type PieceJointeEntry,
} from '../../components/workflow/PiecesJointesPanel';
import {
  CONVOCATION_DELAI_JOURS,
  addDaysIso,
  convocationDelaiError,
} from '../../components/workflow/convocationDelai';
import { formeLabel } from '../../components/workflow/workflowFilename';
import type { DocumentType, DossierBrief } from '../../types/dataroom';

/**
 * Workflow PV AGO — approbation des comptes, en 5 étapes (spec directeur, lot DIVERS §E,
 * 2026-08-13).
 *
 *  1. Société + assemblée — société ACTIVE (identité lue en BD et **verrouillée**),
 *     **date de l'AGO** (assemblée ORDINAIRE par nature : aucun sélecteur de type),
 *     convocation OPTIONNELLE (règle des 16 jours), exercice clos.
 *  2. Données du PV — résultat de l'exercice, affectation du résultat, distribution de
 *     dividendes le cas échéant, quitus à la gérance, conventions réglementées / CAC.
 *  3. Génération — PV d'approbation + rapport de gestion (OPTIONNEL) + optionnels de
 *     séance. **Aucune annonce légale** : l'approbation n'est pas opposable aux tiers.
 *  4. Pièces jointes — dépôt des versions légalisées (OPTIONNELLE).
 *  5. Synthèse.
 *
 * ANTI-DUPLICATION : l'ordre du jour et les résolutions ne sont plus saisis en texte
 * libre — ils sont **dérivés** côté serveur des données structurées de l'étape 2
 * (`ApprobationComptesMapper`). Deux saisies concurrentes pour la même information
 * pouvaient diverger du corps du PV.
 */

const STEPS: RoadmapStep[] = [
  { number: 1, label: 'Société + AGO' },
  { number: 2, label: 'Données du PV' },
  { number: 3, label: 'Génération' },
  { number: 4, label: 'Pièces jointes' },
  { number: 5, label: 'Synthèse' },
];

const currentYear = new Date().getFullYear();
const YEARS = Array.from({ length: 10 }, (_, i) => `${currentYear - i}`);

const ALLOWED_STATUTS = ['ACTIVE'];

/**
 * Sections de séance pertinentes pour l'AGO d'approbation.
 *
 * `ordreDuJour` et `resolutions` sont désormais FAUX : leur contenu est entièrement
 * déterminé par les données structurées de l'étape 2 et construit côté serveur. Les
 * laisser en saisie libre créait un second champ pour la même information.
 */
const SEANCE_SECTIONS = {
  seance: true,
  bureau: true,
  convocation: true,
  ordreDuJour: false,
  resolutions: false,
  presence: true,
  voix: true,
} as const;

interface Affectation {
  libelle: string;
  montant: string;
}

function mapTemplateToDocumentType(code: string): DocumentType {
  if (code.startsWith('PV_APPROBATION_COMPTES')) return 'PV_AGO';
  if (code.startsWith('RAPPORT_GESTION')) return 'AUTRE';
  return 'AUTRE';
}

const num = (v: string | number | null | undefined): number => Number(v) || 0;

/**
 * Monte {@link PvAgoWorkflowPageBody} SOUS {@link WorkflowBoot} : les champs de chaque
 * etape s'initialisent avec `useState(stepData...)`, qui ne lit sa valeur qu'au
 * premier render. Sans ce montage differe, ce premier render a lieu AVANT la
 * reponse du serveur et tous les champs restent vides apres un rechargement
 * (F5, deconnexion/reconnexion), meme sur une etape deja validee.
 */
/**
 * Ordre du jour de l'AGO d'approbation des comptes — il était vide. Il découle de
 * l'objet même de l'assemblée (art. 70 loi 5-96) et ne varie pas. Amorce éditable.
 */
/**
 * Montant d'une affectation retrouvée par son libellé, insensible à la casse et
 * aux accents. L'employé peut renommer la ligne (« Réserve légale (5 %) »), d'où
 * la recherche par inclusion plutôt que par égalité stricte.
 */
function montantAffectation(
  lignes: { libelle: string; montant: string }[],
  cle: string,
): number {
  const norm = (s: string) =>
    s.toLowerCase().normalize('NFD').replace(/\p{M}/gu, '').trim();
  const cible = norm(cle);
  const ligne = lignes.find((a) => norm(a.libelle ?? '').includes(cible));
  if (!ligne) return 0;
  const n = Number.parseFloat(String(ligne.montant ?? '').replace(',', '.'));
  return Number.isFinite(n) ? n : 0;
}

const ORDRE_DU_JOUR_AGO = [
  'Lecture du rapport de gestion de la gérance',
  'Approbation des comptes de l\'exercice clos',
  'Affectation du résultat de l\'exercice',
  'Quitus à la gérance',
  'Pouvoirs en vue des formalités légales',
];

export function PvAgoWorkflowPage() {
  return (
    <WorkflowBoot>
      <PvAgoWorkflowPageBody />
    </WorkflowBoot>
  );
}

function PvAgoWorkflowPageBody() {
  const navigate = useNavigate();
  const {
    ticket, progress, loading, saving, error, setError, stepData,
    saveDraft, executeStep, viewStep, maxStep, goToStep, goPrev: navPrev,
    registerDirty,
  } = useWorkflow();
  const [showCancel, setShowCancel] = useState(false);

  // -------- Step 1 — Société + exercice --------
  const initialId =
    (stepData.step1?.dossierId as string) ?? ticket?.dossierId ?? '';
  const [dossierId, setDossierId] = useState(initialId);
  const [dossier, setDossier] = useState<DossierBrief | null>(null);
  /**
   * Fix PV-AGO-parts / D3 (2026-08-16) — parties prenantes lues EN BASE.
   *
   * Le PV AGO ne chargeait rien du dossier : « Nombre de parts », capital, siège,
   * RC et ville du greffe devaient être RE-SAISIS à la main alors qu'ils sont en
   * base depuis la création — et la feuille de présence de l'AGO repartait d'une
   * liste d'associés vide. Ce chargement supprime toute re-saisie.
   */
  const [dossierParties, setDossierParties] = useState<DossierParties | null>(null);
  useEffect(() => {
    if (!dossierId) {
      setDossierParties(null);
      return;
    }
    let cancelled = false;
    workflowService
      .getDossierParties(dossierId)
      .then((p) => {
        if (!cancelled) setDossierParties(p);
      })
      .catch(() => {
        /* best-effort : la saisie manuelle reste possible */
      });
    return () => {
      cancelled = true;
    };
  }, [dossierId]);

  const bdAssocies = useMemo<AssocieInput[]>(
    () =>
      (dossierParties?.associes ?? [])
        .map((a): AssocieInput => {
          const parts = String(a?.nombreParts ?? '').trim();
          const morale = String(a?.typePersonne ?? '').toUpperCase() === 'MORALE';
          return {
            typePersonne: morale ? 'MORALE' : 'PHYSIQUE',
            civilite: String(a?.civilite ?? ''),
            prenom: String(a?.prenom ?? ''),
            nom: String(a?.nom ?? ''),
            denomination: String(a?.denomination ?? ''),
            adresse: String(a?.adresse ?? ''),
            nombreParts: parts,
            nombreVoix: parts,
            presence: 'présent',
            mandataireNom: '',
            pieceType: 'CIN',
            pieceNumero: String(a?.cin ?? a?.cinNumero ?? a?.pieceNumero ?? ''),
          };
        })
        .filter((a) => a.nom || a.prenom || a.denomination),
    [dossierParties],
  );

  const bdGerants = useMemo<GerantInput[]>(
    () =>
      (dossierParties?.gerants ?? [])
        .map((g): GerantInput => ({
          civilite: String(g?.civilite ?? ''),
          prenom: String(g?.prenom ?? ''),
          nom: String(g?.nom ?? ''),
        }))
        .filter((g) => g.nom || g.prenom),
    [dossierParties],
  );
  const [exercice, setExercice] = useState(
    (stepData.step1?.exerciceClos as string) ??
      (stepData.step1?.exercice as string) ??
      `${currentYear - 1}`,
  );
  const [dateAgo, setDateAgo] = useState(
    (stepData.step1?.dateAGO as string) ?? (stepData.step1?.dateAgo as string) ?? '',
  );
  // Convocation OPTIONNELLE — règle DURE des 16 jours (miroir du backend).
  const [convocationDate, setConvocationDate] = useState(
    ((stepData.step1?.convocation as { date?: string } | undefined) ?? {}).date ?? '',
  );
  const [convocationHeure, setConvocationHeure] = useState(
    ((stepData.step1?.convocation as { heure?: string } | undefined) ?? {}).heure ?? '',
  );

  // Identité société (pré-remplie depuis le dossier, complétée par le cabinet).
  const [capital, setCapital] = useState((stepData.step1?.capital as string) ?? '');
  const [siegeSocial, setSiegeSocial] = useState((stepData.step1?.siegeSocial as string) ?? '');
  const [nombreParts, setNombreParts] = useState((stepData.step1?.nombreParts as string) ?? '');
  const [rcNumero, setRcNumero] = useState((stepData.step1?.rcNumero as string) ?? '');
  const [villeGreffe, setVilleGreffe] = useState((stepData.step1?.villeGreffe as string) ?? '');

  // Fix PV-AGO-parts — amorçage des champs d'identité société depuis la base. On
  // ne remplit QUE ce qui est encore vide : jamais d'écrasement d'une saisie.
  useEffect(() => {
    if (!dossierParties) return;
    const set = (v: unknown, apply: (s: string) => void) => {
      const s = v == null ? '' : String(v).trim();
      if (s) apply(s);
    };
    set(dossierParties.nombreParts, (v) => setNombreParts((p) => (p.trim() ? p : v)));
    set(dossierParties.capitalSocial, (v) => setCapital((p) => (p.trim() ? p : v)));
    set(dossierParties.siegeSocial, (v) => setSiegeSocial((p) => (p.trim() ? p : v)));
    set(dossierParties.rcNumero, (v) => setRcNumero((p) => (p.trim() ? p : v)));
    set(dossierParties.villeGreffe, (v) => setVilleGreffe((p) => (p.trim() ? p : v)));
  }, [dossierParties]);

  /**
   * Fix PV1 (2026-08-16) — la forme vient de la BASE, pas d'un libellé.
   *
   * `rawForme` partait du `dossier` chargé par l'autocomplete (champ annexe, absent
   * tant que la société n'est pas re-sélectionnée) puis d'un champ d'étape. Le test
   * `includes('AU')` sur une chaîne libre est en outre fragile : « SARL AU », « SA »
   * suivi d'un mot en AU, une casse inattendue… Or ce booléen décide du MODÈLE de PV
   * (décision d'associé unique vs assemblée). Se tromper produit un acte de la
   * mauvaise nature.
   *
   * On préfère donc `dossierParties.formeJuridique` — lu en base par
   * `GET /dossiers/{id}/parties` — et on normalise strictement au lieu d'un
   * `includes`. Les autres sources restent en repli, l'override manuel garde le
   * dernier mot.
   */
  const rawForme =
    dossierParties?.formeJuridique
    ?? dossier?.formeJuridique
    ?? (stepData.step1?.formeJuridique as string | undefined);
  const formeAutoDetectee = !!rawForme && rawForme.trim() !== '';
  const [formeOverride, setFormeOverride] = useState<string | null>(null);
  const formeJuridique = useMemo(() => {
    if (formeOverride) return formeOverride;
    if (!rawForme) return 'SARL';
    // Normalisation stricte : « SARL_AU », « SARL AU », « sarl-au » → SARL_AU.
    const n = rawForme.toUpperCase().replace(/[^A-Z]/g, '');
    return n === 'SARLAU' ? 'SARL_AU' : 'SARL';
  }, [rawForme, formeOverride]);
  const isAU = formeJuridique === 'SARL_AU';

  const denomination = dossier?.raisonSociale ?? (stepData.step1?.denomination as string) ?? '';

  // -------- Step 2 — Séance (sous-formulaire partagé) --------
  const societeInput = useMemo(
    () => ({
      denomination,
      capitalChiffres: capital || null,
      siegeSocial: siegeSocial || null,
      nombreParts: nombreParts || null,
      rcNumero: rcNumero || null,
      villeGreffe: villeGreffe || null,
    }),
    [denomination, capital, siegeSocial, nombreParts, rcNumero, villeGreffe],
  );
  const seance = useSeanceForm({
    formeJuridique: isAU ? 'SARL_AU' : 'SARL',
    societe: societeInput,
    defaults: { type: 'ordinaire', date: dateAgo },
    // Fix D1/PV AGO — sans eux la présence du PV AGO repartait d'un associé vierge.
    initialAssocies: bdAssocies,
    initialGerants: bdGerants,
  });

  // -------- Step 2 — Approbation des comptes --------
  const exerciceClosDefault = `${exercice}-12-31`;
  const [exerciceClosDate, setExerciceClosDate] = useState(
    (stepData.step2?.exerciceClosDate as string) ?? exerciceClosDefault,
  );
  const [commissairePresent, setCommissairePresent] = useState<'oui' | 'non'>(
    (stepData.step2?.commissairePresent as 'oui' | 'non') ?? 'non',
  );
  const [commissaireNom, setCommissaireNom] = useState(
    (stepData.step2?.commissaireNom as string) ?? '',
  );
  const [resultatType, setResultatType] = useState<'bénéfice' | 'perte'>(
    (stepData.step2?.resultatType as 'bénéfice' | 'perte') ?? 'bénéfice',
  );
  const [resultatNet, setResultatNet] = useState((stepData.step2?.resultatNet as string) ?? '');
  const [affectations, setAffectations] = useState<Affectation[]>(
    (stepData.step2?.affectations as Affectation[]) ?? [
      { libelle: 'Réserve légale', montant: '' },
      { libelle: 'Report à nouveau', montant: '' },
    ],
  );
  const [dividendeDistribue, setDividendeDistribue] = useState<'oui' | 'non'>(
    (stepData.step2?.dividendeDistribue as 'oui' | 'non') ?? 'non',
  );
  const [dividendeParPart, setDividendeParPart] = useState(
    (stepData.step2?.dividendeParPart as string) ?? '',
  );
  const [dividendeMontantTotal, setDividendeMontantTotal] = useState(
    (stepData.step2?.dividendeMontantTotal as string) ?? '',
  );
  const [dividendeMiseEnPaiementDate, setDividendeMiseEnPaiementDate] = useState(
    (stepData.step2?.dividendeMiseEnPaiementDate as string) ?? '',
  );
  // Lot DIVERS §E — oublis couverts : quitus à la gérance et conventions réglementées.
  const [quitusGerance, setQuitusGerance] = useState<'oui' | 'non'>(
    (stepData.step2?.quitusGerance as 'oui' | 'non') ?? 'oui',
  );
  const [conventionsReglementees, setConventionsReglementees] = useState<'oui' | 'non'>(
    (stepData.step2?.conventionsReglementees as 'oui' | 'non') ?? 'non',
  );
  const [conventionsDetail, setConventionsDetail] = useState(
    (stepData.step2?.conventionsDetail as string) ?? '',
  );
  // -------- Étape 4 — Pièces jointes --------
  const [piecesJointes, setPiecesJointes] = useState<PieceJointeEntry[]>(
    () => (stepData.step4?.piecesJointes as PieceJointeEntry[] | undefined) ?? [],
  );
  // Rapport de gestion (optionnel).
  const [chiffreAffaires, setChiffreAffaires] = useState(
    (stepData.step2?.chiffreAffaires as string) ?? '',
  );
  const [commentaireActivite, setCommentaireActivite] = useState(
    (stepData.step2?.commentaireActivite as string) ?? '',
  );
  const [perspectives, setPerspectives] = useState(
    (stepData.step2?.perspectives as string) ?? '',
  );

  const resultatNetNum = num(resultatNet);

  /**
   * Fix PV2 (2026-08-16) — LE DIVIDENDE NE SE SAISIT QU'UNE FOIS.
   *
   * Le montant devait être tapé DEUX fois : dans le bloc « Distribution de
   * dividendes » ET dans une ligne « Dividendes » du tableau d'affectation. Oublier
   * la seconde faisait échouer le garde-fou de cohérence (Σ affectations = résultat
   * net) sans que rien n'indique laquelle des deux saisies manquait — et deux
   * montants divergents produisaient un PV incohérent.
   *
   * La ligne « Dividendes » de l'affectation est donc DÉRIVÉE du bloc dividendes :
   * une seule source, aucune divergence possible. Le garde-fou de cohérence est
   * conservé — il porte désormais sur des valeurs qui ne peuvent plus se contredire.
   */
  const affectationsEffectives = useMemo<Affectation[]>(() => {
    const estLigneDividende = (a: Affectation) =>
      (a.libelle ?? '').toLowerCase().normalize('NFD').replace(/\p{M}/gu, '')
        .includes('dividende');
    const autres = affectations.filter((a) => !estLigneDividende(a));
    if (dividendeDistribue !== 'oui') return autres;
    return [...autres, { libelle: 'Dividendes', montant: dividendeMontantTotal }];
  }, [affectations, dividendeDistribue, dividendeMontantTotal]);

  const sumAffectations = useMemo(
    () => affectationsEffectives.reduce((acc, a) => acc + num(a.montant), 0),
    [affectationsEffectives],
  );
  const affectationOk = Math.abs(sumAffectations - resultatNetNum) < 0.01;

  // -------- Blocs documents (step 3) --------
  const [docs, setDocs] = useState<Record<string, DocState>>(() => {
    const persisted = (stepData.step3?.documents as Record<string, Partial<DocState>>) ?? {};
    const merged: Record<string, DocState> = {};
    for (const [k, v] of Object.entries(persisted)) {
      merged[k] = {
        ...freshDocState(),
        generated: !!v.generated,
        validated: !!v.validated,
        depositedToDataroom: !!v.depositedToDataroom,
        filename: v.filename,
      };
    }
    return merged;
  });
  const [templates, setTemplates] = useState<TemplateInfo[]>([]);
  const [loadingTemplates, setLoadingTemplates] = useState(false);
  const [loadTplErr, setLoadTplErr] = useState<string | null>(null);

  const [revealErrors, setRevealErrors] = useState(false);
  useEffect(() => { setRevealErrors(false); }, [viewStep]);

  // Point 4 (audit directeur) — « si une variable a deja sa valeur, on la recupere
  // depuis la BD ». A la selection d'une societe, on pre-remplit l'identite depuis
  // l'endpoint juridique (capital / siege / RC / ville du greffe). On ne remplit QUE
  // les champs vides : jamais d'ecrasement d'une saisie ou d'un brouillon. Best-effort.
  // Champs effectivement fournis par la BD : ils passent en LECTURE SEULE.
  // Raison : `SocieteIdentityEnricher` (ai-service) re-impose la valeur BD a la
  // generation. Laisser ces champs editables donnait donc l'illusion d'une saisie
  // prise en compte alors qu'elle etait silencieusement ecrasee. La correction se
  // fait a la source : Data Room > onglet Juridique > « Identifiants ».
  const [fromBd, setFromBd] = useState<Record<string, boolean>>({});

  useEffect(() => {
    if (!dossierId) return;
    let cancelled = false;
    dataroomService
      .getJuridique(dossierId)
      .then((v) => {
        if (cancelled) return;
        const seen: Record<string, boolean> = {};
        if (v.capitalSocialMad != null) {
          setCapital((p) => p || String(v.capitalSocialMad));
          seen.capital = true;
        }
        if (v.adresseSiege) {
          setSiegeSocial((p) => p || v.adresseSiege!);
          seen.siegeSocial = true;
        }
        if (v.rcNumero) {
          setRcNumero((p) => p || v.rcNumero!);
          seen.rcNumero = true;
        }
        const greffe = v.rcTribunal || v.ville;
        if (greffe) {
          setVilleGreffe((p) => p || greffe);
          seen.villeGreffe = true;
        }
        setFromBd(seen);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [dossierId]);

  const buildPayload = useCallback((): Record<string, unknown> => {
    const base = seance.buildPayload();
    const baseSociete = (base.societe as Record<string, unknown>) ?? {};
    return {
      ...base,
      // Identité société : enrichie pour le rapport de gestion (adresseSiege / rcVille /
      // IF / ICE / activité), en plus des clés de séance déjà présentes.
      societe: {
        ...baseSociete,
        adresseSiege: siegeSocial || null,
        rcVille: villeGreffe || null,
        ifNumero: null,
        iceNumero: dossier?.ice ?? null,
        activiteSociete: null,
      },
      approbation: {
        exerciceClosDate,
        commissairePresent,
        commissaireNom: commissairePresent === 'oui' ? commissaireNom : '',
        resultatType,
        resultatNet: resultatNetNum,
        // Fix PV2 — on envoie l'affectation EFFECTIVE (ligne « Dividendes » dérivée
        // du bloc dividendes), jamais la saisie brute : le PV ne peut plus porter
        // deux montants de dividende différents.
        affectations: affectationsEffectives
          .filter((a) => a.libelle.trim() || a.montant.trim())
          .map((a) => ({ libelle: a.libelle.trim(), montant: num(a.montant) })),
        dividendeDistribue,
        dividendeMontantTotal:
          dividendeDistribue === 'oui' ? num(dividendeMontantTotal) : 0,
        dividendeParPart: dividendeDistribue === 'oui' ? num(dividendeParPart) : 0,
        dividendeMiseEnPaiementDate:
          dividendeDistribue === 'oui' ? dividendeMiseEnPaiementDate : '',
        // Lot DIVERS §E — alimentent les résolutions dérivées côté serveur.
        quitusGerance,
        conventionsReglementees,
        conventionsDetail: conventionsReglementees === 'oui' ? conventionsDetail : '',
      },
      // Rapport de gestion (RAPPORT_GESTION, délégué à AnnualReportMapper côté back).
      //
      // 2026-08-14 — Réserve légale, dividendes et report à nouveau MANQUAIENT ici :
      // ils sont bien saisis à l'étape 2, mais rangés dans `approbation.affectations`
      // (liste libellé/montant) alors que le mapper du rapport les lit dans `exercice`.
      // Le rapport sortait donc avec trois mentions « VALEUR MANQUANTE » en rouge,
      // pour des montants pourtant déjà à l'écran. On les dérive ici plutôt que de
      // les faire re-saisir : l'affectation reste l'unique lieu de saisie.
      exercice: {
        dateCloture: exerciceClosDate,
        resultatNet: resultatNetNum,
        chiffreAffaires: num(chiffreAffaires),
        reserveLegale: montantAffectation(affectations, 'réserve légale'),
        reportANouveau: montantAffectation(affectations, 'report à nouveau'),
        dividendes: dividendeDistribue === 'oui' ? num(dividendeMontantTotal) : 0,
        commentaireActivite,
        evenementsPerspectives: perspectives,
      },
      gerant: { nom: seance.gerants.map((g) => `${g.prenom} ${g.nom}`.trim()).filter(Boolean)[0] ?? '' },
      signature: {
        lieuDateEmission: villeGreffe
          ? `${villeGreffe}, le ${dateAgo || exerciceClosDate}`
          : '',
      },
    };
  }, [
    seance, siegeSocial, villeGreffe, dossier, exerciceClosDate, commissairePresent,
    commissaireNom, resultatType, resultatNetNum, affectations, dividendeDistribue,
    dividendeMontantTotal, dividendeParPart, dividendeMiseEnPaiementDate,
    quitusGerance, conventionsReglementees, conventionsDetail,
    chiffreAffaires, commentaireActivite, perspectives, dateAgo,
  ]);

  // Point 3 (audit directeur) — nommage conforme du PV d'approbation des comptes
  // (« Type + exercice + Dénomination ») au lieu du code technique backend.
  const filenameFor = useCallback(
    (tpl: TemplateInfo): string | undefined => {
      if (tpl.code.startsWith('PV_APPROBATION_COMPTES')) {
        return (
          sanitizeFilename(
            `PV — Approbation des comptes ${exercice} - ${denomination || 'Société'}`,
          ) + '.docx'
        );
      }
      return undefined;
    },
    [exercice, denomination],
  );

  const { updateDoc, generateOne, downloadOne, validateOne } = useDocumentBlocks({
    workflowCode: 'PV_AGO',
    dossierId: dossier?.id ?? dossierId ?? ticket?.dossierId,
    ticketId: ticket?.id,
    mapType: mapTemplateToDocumentType,
    docs,
    setDocs,
    buildPayload,
    filenameFor,
  });

  useEffect(() => {
    if (viewStep < 3 || templates.length > 0) return;
    let cancelled = false;
    setLoadingTemplates(true);
    setLoadTplErr(null);
    listTemplatesForWorkflow('PV_AGO')
      .then((items) => {
        if (cancelled) return;
        const filtered = items.filter((t) => {
          if (t.code.endsWith('_SARL_AU')) return isAU;
          if (t.code.endsWith('_SARL')) return !isAU;
          return true; // RAPPORT_GESTION (sans suffixe)
        });
        const sorted = [...filtered].sort((a, b) => {
          const aPv = a.code.startsWith('PV_APPROBATION_COMPTES');
          const bPv = b.code.startsWith('PV_APPROBATION_COMPTES');
          if (aPv && !bPv) return -1;
          if (bPv && !aPv) return 1;
          return a.code.localeCompare(b.code);
        });
        setTemplates(sorted);
      })
      .catch((e) => {
        if (!cancelled) {
          setLoadTplErr(e instanceof Error ? e.message : 'Chargement modèles impossible.');
        }
      })
      .finally(() => { if (!cancelled) setLoadingTemplates(false); });
    return () => { cancelled = true; };
  }, [viewStep, templates.length, isAU]);

  const pvTemplate = templates.find((t) => t.code.startsWith('PV_APPROBATION_COMPTES'));
  const rapportTemplate = templates.find((t) => t.code === 'RAPPORT_GESTION');

  // Règle DURE des 16 jours (miroir exact du backend WorkflowSteps.convocationDelaiError).
  const convocationError = useMemo(
    () => convocationDelaiError(convocationDate, dateAgo),
    [convocationDate, dateAgo],
  );

  const errors = useMemo(() => {
    const e: Record<string, string> = {};
    if (viewStep === 1) {
      if (!dossierId) e.dossierId = 'Sélectionnez la société.';
      if (!exercice) e.exercice = 'Exercice clos obligatoire.';
      if (!dateAgo) e.dateAgo = "La date de l'assemblée générale ordinaire est obligatoire.";
      if (convocationError) e.convocation = convocationError;
    }
    if (viewStep === 2) {
      if (!resultatNet || Number.isNaN(Number(resultatNet))) {
        e.resultatNet = 'Résultat net obligatoire (valeur numérique).';
      }
      if (!exerciceClosDate) e.exerciceClosDate = 'Date de clôture obligatoire.';
      if (!affectationOk) {
        e.affectation = `Total affectation : ${sumAffectations.toLocaleString('fr-FR')} MAD `
          + `(doit égaler le résultat net : ${resultatNetNum.toLocaleString('fr-FR')} MAD).`;
      }
      if (commissairePresent === 'oui' && !commissaireNom.trim()) {
        e.commissaireNom = 'Nom du commissaire aux comptes obligatoire.';
      }
      if (dividendeDistribue === 'oui') {
        if (!dividendeMontantTotal.trim()) {
          e.dividendeMontantTotal = 'Montant total des dividendes obligatoire.';
        } else if (num(dividendeMontantTotal) > Math.abs(resultatNetNum)) {
          e.dividendeMontantTotal =
            'Le montant total des dividendes dépasse le résultat à affecter.';
        }
        if (!dividendeParPart.trim()) {
          e.dividendeParPart = 'Dividende par part obligatoire.';
        }
        if (!dividendeMiseEnPaiementDate) {
          e.dividendeMiseEnPaiementDate = 'Date de mise en paiement obligatoire.';
        }
      }
      if (conventionsReglementees === 'oui' && !conventionsDetail.trim()) {
        e.conventionsDetail =
          'Décrivez les conventions réglementées : elles figurent dans une résolution du PV.';
      }
    }
    return e;
  }, [
    viewStep, dossierId, exercice, dateAgo, convocationError, resultatNet,
    exerciceClosDate, affectationOk, sumAffectations, resultatNetNum,
    commissairePresent, commissaireNom, dividendeDistribue, dividendeMontantTotal,
    dividendeParPart, dividendeMiseEnPaiementDate, conventionsReglementees,
    conventionsDetail,
  ]);

  if (loading) return <Loader />;
  if (!ticket || !progress)
    return (
      <div className="rounded-lg border border-danger/40 bg-danger/10 p-6 text-sm text-danger">
        {error ?? 'Workflow introuvable'}
      </div>
    );

  const step = viewStep;
  const isTerminated = progress.statut === 'TERMINE';
  const showErr = (k: string): string | undefined => (revealErrors ? errors[k] : undefined);

  async function handleValidate() {
    setError(null);
    const errs = Object.values(errors).filter(Boolean);
    if (errs.length > 0) {
      setRevealErrors(true);
      setError(`Veuillez corriger ${errs.length} erreur${errs.length > 1 ? 's' : ''} (surlignées en rouge).`);
      return;
    }
    if (step === 1) {
      await executeStep(1, {
        dossierId,
        exerciceClos: exercice,
        dateAGO: dateAgo,
        convocation: convocationDate
          ? { date: convocationDate, heure: convocationHeure || undefined }
          : undefined,
      });
    } else if (step === 2) {
      await executeStep(2, {
        approbation: {
          exerciceClosDate,
          commissairePresent,
          commissaireNom: commissairePresent === 'oui' ? commissaireNom : '',
          resultatType,
          resultatNet: resultatNetNum,
          affectations: affectations
            .filter((a) => a.libelle.trim() || a.montant.trim())
            .map((a) => ({ libelle: a.libelle.trim(), montant: num(a.montant) })),
          dividendeDistribue,
          dividendeMontantTotal:
            dividendeDistribue === 'oui' ? num(dividendeMontantTotal) : 0,
          dividendeParPart: dividendeDistribue === 'oui' ? num(dividendeParPart) : 0,
          dividendeMiseEnPaiementDate:
            dividendeDistribue === 'oui' ? dividendeMiseEnPaiementDate : '',
          quitusGerance,
          conventionsReglementees,
          conventionsDetail: conventionsReglementees === 'oui' ? conventionsDetail : '',
        },
      });
    } else if (step === 3) {
      if (!pvTemplate || !docs[pvTemplate.code]?.validated) {
        setError("Générez puis validez le PV d'approbation avant de continuer.");
        return;
      }
      await executeStep(3, {
        pvValide: true,
        // Rapport de gestion : OPTIONNEL, jamais bloquant.
        rapportGestionValide: !!(rapportTemplate && docs[rapportTemplate.code]?.validated),
      });
    } else if (step === 4) {
      // Étape OPTIONNELLE : jamais bloquante, peut rester vide.
      await executeStep(4, {
        piecesJointes: piecesJointes.map((p) => ({
          id: p.id,
          label: p.label,
          filename: p.filename,
          version: p.version ?? null,
        })),
      });
    } else if (step === 5) {
      await executeStep(5, {});
    }
  }

  /** Contenu courant de l'étape — voir la note dans DissolutionWorkflowPage. */
  const draftPayloadFor = useCallback(
    (n: number): Record<string, unknown> | null => {
      if (n === 1) {
        return {
          dossierId, exerciceClos: exercice, exercice, formeJuridique,
          dateAGO: dateAgo, dateAgo,
          convocation: convocationDate
            ? { date: convocationDate, heure: convocationHeure || undefined }
            : undefined,
          denomination, capital, siegeSocial, nombreParts, rcNumero, villeGreffe,
        };
      }
      if (n === 2) {
        return {
          exerciceClosDate, commissairePresent, commissaireNom, resultatType, resultatNet,
          affectations, dividendeDistribue, dividendeMontantTotal, dividendeParPart,
          dividendeMiseEnPaiementDate, quitusGerance, conventionsReglementees,
          conventionsDetail, chiffreAffaires, commentaireActivite, perspectives,
        };
      }
      if (n === 3) {
        // Seules les métadonnées des documents sont persistables (pas les blobs).
        const persistableDocs: Record<string, Partial<DocState>> = {};
        for (const [code, st] of Object.entries(docs)) {
          persistableDocs[code] = {
            generated: st.generated,
            validated: st.validated,
            depositedToDataroom: st.depositedToDataroom,
            filename: st.filename,
          };
        }
        return { documents: persistableDocs };
      }
      if (n === 4) return { piecesJointes };
      return null;
    },
    [dossierId, exercice, formeJuridique, dateAgo, convocationDate, convocationHeure,
      denomination, capital, siegeSocial, nombreParts, rcNumero, villeGreffe,
      exerciceClosDate, commissairePresent, commissaireNom, resultatType, resultatNet,
      affectations, dividendeDistribue, dividendeMontantTotal, dividendeParPart,
      dividendeMiseEnPaiementDate, quitusGerance, conventionsReglementees,
      conventionsDetail, chiffreAffaires, commentaireActivite, perspectives,
      docs, piecesJointes],
  );

  useStepAutosave(step, () => draftPayloadFor(step), registerDirty);

  async function handleSaveDraft() {
    const payload = draftPayloadFor(step);
    if (payload) await saveDraft(step, { [`step${step}`]: payload });
  }

  const templateCode = isAU ? 'PV_APPROBATION_COMPTES_SARL_AU' : 'PV_APPROBATION_COMPTES_SARL';

  return (
    <>
      <WorkflowShell
        ticket={ticket}
        steps={STEPS}
        currentStep={maxStep}
        viewStep={step}
        onNavigate={goToStep}
        error={error}
        saving={saving}
        onPrev={step > 1 ? navPrev : undefined}
        onSaveDraft={!isTerminated && step < 5 ? handleSaveDraft : undefined}
        onValidate={!isTerminated ? handleValidate : undefined}
        onCancel={() => setShowCancel(true)}
        footer={{ validateLabel: step === 5 ? 'Finaliser' : "Valider l'étape" }}
      >
        {step === 1 && (
          <div className="space-y-4">
            <header>
              <h2 className="text-lg font-semibold text-fg">
                Étape 1 — Société + exercice clos
              </h2>
              <p className="text-sm text-fg-subtle">
                Sélectionnez la société (statut ACTIVE) puis l'exercice fiscal clos à approuver.
                L'identité de la société est <strong>lue en base</strong> : les champs qu'elle
                fournit sont verrouillés. Le modèle de PV s'adapte automatiquement à la forme
                juridique. L'approbation des comptes relève <strong>toujours</strong> d'une
                assemblée générale <strong>ordinaire</strong> — aucun type n'est à choisir.
              </p>
            </header>

            <DossierAutocomplete
              value={dossierId}
              onSelect={(d) => {
                setDossier(d);
                setDossierId(d?.id ?? '');
                setFormeOverride(null);
                if (d?.ville && !villeGreffe) setVilleGreffe(d.ville);
              }}
              allowedStatuts={ALLOWED_STATUTS}
              label="Société"
              help="L'AGO concerne les sociétés ACTIVES (post-création, avant dissolution)."
            />
            {showErr('dossierId') && <FieldError msg={showErr('dossierId')!} />}

            <div className="grid gap-3 md:grid-cols-3">
              <Select
                label="Exercice clos *"
                value={exercice}
                onChange={(e) => {
                  setExercice(e.target.value);
                  setExerciceClosDate(`${e.target.value}-12-31`);
                }}
                onBlur={() => setRevealErrors(true)}
                options={[
                  { value: '', label: 'Sélectionnez...' },
                  ...YEARS.map((y) => ({ value: y, label: y })),
                ]}
                error={showErr('exercice')}
              />
              <TextField
                label="Date de l'AGO *"
                type="date"
                value={dateAgo}
                data-testid="pvago-date-ago"
                onChange={(e) => setDateAgo(e.target.value)}
                onBlur={() => setRevealErrors(true)}
                error={showErr('dateAgo')}
                hint="Assemblée ORDINAIRE par nature : aucun type à choisir. Elle se tient après la clôture."
              />
              {formeAutoDetectee ? (
                <div className="flex flex-col gap-1">
                  <label className="text-sm font-medium text-fg-muted">Forme juridique</label>
                  <div className="flex items-center gap-2 rounded-lg border border-border bg-bg-overlay px-3 py-2 text-sm">
                    <CheckCircle className="h-4 w-4 flex-shrink-0 text-success" />
                    <span className="font-medium text-fg">
                      {isAU ? 'SARL AU (associé unique)' : 'SARL pluri-associés'}
                    </span>
                  </div>
                  <p className="text-xs text-fg-subtle">Déduit du dossier</p>
                </div>
              ) : (
                <div className="flex flex-col gap-1">
                  <Select
                    label="Forme juridique *"
                    value={formeJuridique}
                    onChange={(e) => setFormeOverride(e.target.value)}
                    options={[
                      { value: 'SARL', label: 'SARL pluri-associés' },
                      { value: 'SARL_AU', label: 'SARL AU (associé unique)' },
                    ]}
                  />
                  <p className="text-xs text-warning">
                    Forme non détectée sur le dossier — sélectionnez-la.
                  </p>
                </div>
              )}
            </div>

            <div className="rounded-xl border border-border bg-bg-overlay p-4">
              <h3 className="mb-3 text-sm font-semibold text-fg">
                Identité de la société (pour l'en-tête du PV)
              </h3>
              <div className="grid gap-3 md:grid-cols-2">
                <TextField label="Dénomination" value={denomination} readOnly hint="Depuis le dossier" />
                <TextField
                  label="Capital social (MAD)"
                  type="number"
                  value={capital}
                  readOnly={!!fromBd.capital}
                  hint={fromBd.capital ? 'Depuis le dossier' : undefined}
                  onChange={(e) => setCapital(e.target.value)}
                  placeholder="Ex : 100000"
                />
                <div className="md:col-span-2">
                  <TextField
                    label="Siège social"
                    value={siegeSocial}
                    readOnly={!!fromBd.siegeSocial}
                    hint={fromBd.siegeSocial ? 'Depuis le dossier' : undefined}
                    onChange={(e) => setSiegeSocial(e.target.value)}
                    placeholder="Adresse du siège"
                  />
                </div>
                <TextField
                  label="Nombre de parts"
                  type="number"
                  value={nombreParts}
                  onChange={(e) => setNombreParts(e.target.value)}
                  hint="Non stocké en colonne — saisi ici"
                />
                <TextField
                  label="N° RC"
                  value={rcNumero}
                  readOnly={!!fromBd.rcNumero}
                  hint={fromBd.rcNumero ? 'Depuis le dossier' : undefined}
                  onChange={(e) => setRcNumero(e.target.value)}
                />
                <TextField
                  label="Ville du greffe"
                  value={villeGreffe}
                  readOnly={!!fromBd.villeGreffe}
                  hint={fromBd.villeGreffe ? 'Depuis le dossier' : undefined}
                  onChange={(e) => setVilleGreffe(e.target.value)}
                  placeholder="Ex : Casablanca"
                />
              </div>
              <p className="mt-2 text-[11px] text-fg-subtle">
                Les champs « Depuis le dossier » sont en lecture seule : ils sont réimposés
                depuis la base à la génération. Pour les corriger, passez par la Data Room →
                onglet Juridique → <strong>Identifiants</strong>.
              </p>
            </div>

            <section
              className="space-y-3 rounded-xl border border-border bg-bg-raised p-4"
              data-testid="pvago-convocation"
            >
              <div className="flex items-center gap-2">
                <Megaphone className="h-4 w-4 text-accent" />
                <h4 className="text-sm font-semibold text-fg">Convocation (optionnelle)</h4>
              </div>
              <div className="grid gap-3 md:grid-cols-2">
                <TextField
                  label="Date de convocation"
                  type="date"
                  value={convocationDate}
                  data-testid="pvago-convocation-date"
                  onChange={(e) => {
                    const v = e.target.value;
                    setConvocationDate(v);
                    if (v && !dateAgo) setDateAgo(addDaysIso(v, CONVOCATION_DELAI_JOURS));
                  }}
                />
                <TextField
                  label="Heure de convocation"
                  type="time"
                  value={convocationHeure}
                  onChange={(e) => setConvocationHeure(e.target.value)}
                />
              </div>
              <p className="text-[11px] text-fg-subtle">
                Règle des <strong>{CONVOCATION_DELAI_JOURS} jours</strong> : l'assemblée ne peut se
                tenir moins de {CONVOCATION_DELAI_JOURS} jours calendaires après la convocation.
                Laisser vide si aucune convocation n'est émise.
              </p>
              {convocationError && (
                <div
                  role="alert"
                  data-testid="pvago-convocation-16j-error"
                  className="flex items-start gap-2 rounded-lg border border-danger/40 bg-danger/10 p-2 text-xs text-danger"
                >
                  <AlertCircle className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" />
                  <span>{convocationError}</span>
                </div>
              )}
            </section>

            <div className="rounded-lg border border-accent/40 bg-accent/10 p-3 text-xs text-fg-muted">
              <div className="flex items-center gap-2">
                <Info className="h-4 w-4 text-accent" />
                <span>
                  Modèle détecté : <code className="rounded bg-bg-overlay px-1.5 py-0.5">{templateCode}</code>
                  {' '}— <strong>aucune annonce légale</strong> : l'approbation des comptes n'est
                  pas opposable aux tiers.
                </span>
              </div>
            </div>
          </div>
        )}

        {step === 2 && (
          <div className="space-y-5">
            <header>
              <h2 className="text-lg font-semibold text-fg">
                Étape 2 — Données du procès-verbal
              </h2>
              <p className="text-sm text-fg-subtle">
                Renseignez la tenue de la séance (président, présence des associés) puis le
                résultat de l'exercice, son affectation, les dividendes, le quitus et les
                conventions réglementées. L'<strong>ordre du jour</strong> et les{' '}
                <strong>résolutions</strong> sont <strong>déduits</strong> de ces données : ils
                ne sont plus saisis à la main, pour qu'ils ne puissent pas contredire le corps
                du PV.
              </p>
            </header>

            {/* Sous-formulaire de séance PARTAGÉ (réutilisé par les documents de séance). */}
            <SeanceFormFields form={seance} sections={SEANCE_SECTIONS} />

            {/* Bloc approbation des comptes. */}
            <div className="rounded-xl border border-border bg-bg-raised p-4">
              <h3 className="mb-3 text-sm font-semibold text-fg">Comptes et résultat de l'exercice</h3>
              <div className="grid gap-3 md:grid-cols-3">
                <TextField
                  label="Date de clôture de l'exercice *"
                  type="date"
                  value={exerciceClosDate}
                  onChange={(e) => setExerciceClosDate(e.target.value)}
                  onBlur={() => setRevealErrors(true)}
                  error={showErr('exerciceClosDate')}
                />
                <Select
                  label="Type de résultat"
                  value={resultatType}
                  onChange={(e) => setResultatType(e.target.value as 'bénéfice' | 'perte')}
                  options={[
                    { value: 'bénéfice', label: 'Bénéfice' },
                    { value: 'perte', label: 'Perte' },
                  ]}
                />
                <TextField
                  label="Résultat net (MAD) *"
                  type="number"
                  value={resultatNet}
                  onChange={(e) => setResultatNet(e.target.value)}
                  onBlur={() => setRevealErrors(true)}
                  placeholder="Ex : 250000"
                  error={showErr('resultatNet')}
                />
              </div>

              <div className="mt-4 grid gap-3 md:grid-cols-2">
                <Select
                  label="Commissaire aux comptes présent ?"
                  value={commissairePresent}
                  onChange={(e) => setCommissairePresent(e.target.value as 'oui' | 'non')}
                  options={[{ value: 'non', label: 'Non' }, { value: 'oui', label: 'Oui' }]}
                />
                {commissairePresent === 'oui' && (
                  <TextField
                    label="Nom du commissaire aux comptes *"
                    value={commissaireNom}
                    onChange={(e) => setCommissaireNom(e.target.value)}
                    error={showErr('commissaireNom')}
                  />
                )}
              </div>
            </div>

            {/* Affectation du résultat (liste : la somme doit égaler le résultat net). */}
            <div className="rounded-xl border border-border bg-bg-raised p-4">
              <div className="mb-3 flex items-center justify-between">
                <h3 className="text-sm font-semibold text-fg">Affectation du résultat</h3>
                <button
                  type="button"
                  onClick={() => setAffectations((p) => [...p, { libelle: '', montant: '' }])}
                  className="inline-flex items-center gap-1 rounded-lg border border-border bg-bg-raised px-2.5 py-1 text-xs font-medium text-fg hover:border-accent"
                >
                  <Plus className="h-3.5 w-3.5" /> Ajouter un poste
                </button>
              </div>
              <div className="space-y-2">
                {affectations.map((a, i) => (
                  <div key={i} className="grid items-end gap-2 md:grid-cols-[1fr_10rem_auto]">
                    <TextField
                      label={i === 0 ? 'Poste d\'affectation' : undefined}
                      value={a.libelle}
                      onChange={(e) =>
                        setAffectations((p) => p.map((x, j) => (j === i ? { ...x, libelle: e.target.value } : x)))
                      }
                      placeholder="Ex : Réserve légale, Dividendes, Report à nouveau"
                    />
                    <TextField
                      label={i === 0 ? 'Montant (MAD)' : undefined}
                      type="number"
                      value={a.montant}
                      onChange={(e) =>
                        setAffectations((p) => p.map((x, j) => (j === i ? { ...x, montant: e.target.value } : x)))
                      }
                    />
                    <button
                      type="button"
                      onClick={() => setAffectations((p) => p.filter((_, j) => j !== i))}
                      disabled={affectations.length <= 1}
                      className="inline-flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg border border-border bg-bg-raised text-fg-subtle transition hover:border-danger hover:text-danger disabled:cursor-not-allowed disabled:opacity-40"
                      aria-label="Supprimer"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  </div>
                ))}
              </div>
              <div
                className={`mt-3 rounded-lg p-2 text-xs ${
                  affectationOk ? 'bg-emerald-50 text-emerald-700' : 'bg-danger/10 text-danger'
                }`}
              >
                Total affecté : <strong>{sumAffectations.toLocaleString('fr-FR')} MAD</strong>{' '}
                {affectationOk
                  ? '✓ (= résultat net)'
                  : `(doit égaler le résultat net : ${resultatNetNum.toLocaleString('fr-FR')} MAD)`}
              </div>
              {showErr('affectation') && <FieldError msg={showErr('affectation')!} />}
            </div>

            {/* Dividendes. */}
            <div className="rounded-xl border border-border bg-bg-raised p-4">
              <h3 className="mb-3 text-sm font-semibold text-fg">Dividendes</h3>
              <div className="grid gap-3 md:grid-cols-3">
                <Select
                  label="Dividende distribué ?"
                  value={dividendeDistribue}
                  onChange={(e) => setDividendeDistribue(e.target.value as 'oui' | 'non')}
                  options={[{ value: 'non', label: 'Non' }, { value: 'oui', label: 'Oui' }]}
                />
                {dividendeDistribue === 'oui' && (
                  <>
                    <TextField
                      label="Montant total distribué (MAD) *"
                      type="number"
                      value={dividendeMontantTotal}
                      data-testid="pvago-dividende-total"
                      onChange={(e) => setDividendeMontantTotal(e.target.value)}
                      onBlur={() => setRevealErrors(true)}
                      error={showErr('dividendeMontantTotal')}
                      hint="Doit aussi figurer comme poste « Dividendes » dans l'affectation."
                    />
                    <TextField
                      label="Dividende par part (MAD) *"
                      type="number"
                      value={dividendeParPart}
                      onChange={(e) => setDividendeParPart(e.target.value)}
                      onBlur={() => setRevealErrors(true)}
                      error={showErr('dividendeParPart')}
                    />
                    <TextField
                      label="Date de mise en paiement *"
                      type="date"
                      value={dividendeMiseEnPaiementDate}
                      onChange={(e) => setDividendeMiseEnPaiementDate(e.target.value)}
                      onBlur={() => setRevealErrors(true)}
                      error={showErr('dividendeMiseEnPaiementDate')}
                    />
                  </>
                )}
              </div>
            </div>

            {/* Quitus + conventions réglementées — oublis couverts par le lot DIVERS §E. */}
            <div className="rounded-xl border border-border bg-bg-raised p-4">
              <h3 className="mb-3 text-sm font-semibold text-fg">
                Quitus et conventions réglementées
              </h3>
              <div className="grid gap-3 md:grid-cols-2">
                <Select
                  label="Quitus donné à la gérance ?"
                  value={quitusGerance}
                  data-testid="pvago-quitus"
                  onChange={(e) => setQuitusGerance(e.target.value as 'oui' | 'non')}
                  options={[
                    { value: 'oui', label: 'Oui — quitus entier et sans réserve' },
                    { value: 'non', label: 'Non — quitus refusé / différé' },
                  ]}
                  hint="Génère (ou non) la résolution de quitus dans le PV."
                />
                <Select
                  label="Conventions réglementées intervenues ?"
                  value={conventionsReglementees}
                  data-testid="pvago-conventions"
                  onChange={(e) => setConventionsReglementees(e.target.value as 'oui' | 'non')}
                  options={[
                    { value: 'non', label: 'Non — aucune (loi 5-96)' },
                    { value: 'oui', label: 'Oui — à approuver' },
                  ]}
                />
                {conventionsReglementees === 'oui' && (
                  <div className="md:col-span-2">
                    <label className="mb-1 block text-sm font-medium text-fg-muted">
                      Description des conventions *
                    </label>
                    <textarea
                      aria-label="Description des conventions reglementees"
                      rows={3}
                      value={conventionsDetail}
                      data-testid="pvago-conventions-detail"
                      onChange={(e) => setConventionsDetail(e.target.value)}
                      onBlur={() => setRevealErrors(true)}
                      placeholder="Ex : convention de compte courant d'associé conclue le ..., bail consenti par le gérant ..."
                      className="w-full rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200"
                    />
                    {showErr('conventionsDetail') && (
                      <FieldError msg={showErr('conventionsDetail')!} />
                    )}
                  </div>
                )}
              </div>
              <p className="mt-2 text-[11px] text-fg-subtle">
                Ces deux réponses produisent directement les résolutions correspondantes du PV —
                elles ne sont pas à retaper en texte libre.
              </p>
            </div>

            {/* Rapport de gestion (optionnel). */}
            <div className="rounded-xl border border-border bg-bg-overlay p-4">
              <h3 className="mb-3 text-sm font-semibold text-fg">
                Contenu du rapport de gestion (optionnel)
              </h3>
              <div className="grid gap-3 md:grid-cols-2">
                <TextField
                  label="Chiffre d'affaires (MAD)"
                  type="number"
                  value={chiffreAffaires}
                  onChange={(e) => setChiffreAffaires(e.target.value)}
                />
              </div>
              <div className="mt-3 space-y-3">
                <div>
                  <label className="mb-1 block text-sm font-medium text-fg-muted">
                    Commentaire sur l'activité
                  </label>
                  <textarea
                    aria-label="Commentaire sur l'activite de l'exercice"
                    rows={3}
                    value={commentaireActivite}
                    onChange={(e) => setCommentaireActivite(e.target.value)}
                    placeholder="Faits marquants de l'exercice, croissance, contrats..."
                    className="w-full rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-sm font-medium text-fg-muted">
                    Événements et perspectives
                  </label>
                  <textarea
                    aria-label="Evenements post-cloture et perspectives"
                    rows={2}
                    value={perspectives}
                    onChange={(e) => setPerspectives(e.target.value)}
                    placeholder="Perspectives N+1, événements post-clôture..."
                    className="w-full rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200"
                  />
                </div>
              </div>
            </div>
          </div>
        )}

        {step === 3 && (
          <div className="space-y-4">
            <header>
              <div className="flex items-center gap-2">
                <Sparkles className="h-5 w-5 text-violet-600" />
                <h2 className="text-lg font-semibold text-fg">
                  Étape 3 — Génération du PV d'approbation + rapport de gestion
                </h2>
              </div>
              <p className="text-sm text-fg-subtle">
                Générez le PV d'approbation des comptes et, en option, le rapport de gestion. Les
                documents validés sont automatiquement déposés en dataroom juridique. Modèle
                appliqué : <code className="rounded bg-bg-overlay px-1.5 py-0.5">{templateCode}</code>
              </p>
            </header>

            {loadingTemplates && <p className="text-sm text-fg-subtle">Chargement des modèles…</p>}
            {loadTplErr && (
              <div className="rounded-lg border border-danger/30 bg-danger/10 p-3 text-sm text-danger" role="alert">
                {loadTplErr}
              </div>
            )}
            {!loadingTemplates && templates.length === 0 && !loadTplErr && (
              <div className="rounded-lg border border-amber-200 bg-warning/10 p-3 text-sm text-amber-800">
                Aucun modèle compatible n'a été trouvé pour ce dossier.
              </div>
            )}

            <div className="grid gap-4 md:grid-cols-2">
              {templates.map((tpl) => (
                <WorkflowDocumentBlock
                  key={tpl.code}
                  tpl={tpl}
                  state={docs[tpl.code] ?? freshDocState()}
                  onGenerate={() => generateOne(tpl)}
                  onDownload={() => downloadOne(tpl)}
                  onRegenerate={() => generateOne(tpl)}
                  onValidate={() => validateOne(tpl)}
                  onTogglePreview={() =>
                    updateDoc(tpl.code, { previewOpen: !docs[tpl.code]?.previewOpen })
                  }
                />
              ))}
            </div>

            {pvTemplate && !docs[pvTemplate.code]?.validated && (
              <p className="text-xs text-warning">
                Le PV d'approbation doit être validé pour avancer. Le rapport de gestion est recommandé mais optionnel.
              </p>
            )}

            {/* La date d'assemblee ET la convocation (date + heure) sont saisies a
                l'etape 1 : le panneau les recoit et ne les redemande pas. Il garde
                son repli — contrairement aux autres workflows, il n'est pas ici
                dans une section « Convocation » de la page, mais parmi les
                documents de l'etape. */}
            <ConvocationPanel
              dossierId={dossier?.id ?? dossierId ?? ticket?.dossierId}
              ticketId={ticket?.id}
              formeJuridique={isAU ? 'SARL_AU' : 'SARL'}
              societe={societeInput}
              defaultSeance={{
                type: 'ordinaire',
                date: dateAgo,
                convDate: convocationDate,
                convHeure: convocationHeure,
              }}
              initialOrdreDuJour={ORDRE_DU_JOUR_AGO}
              lockType
              hideSeanceDate
              hideConvDate
              initialAssocies={bdAssocies}
              initialGerants={bdGerants}
              contexteSeance={`AGO${dateAgo ? ` du ${dateAgo}` : ''}`}
            />
            {/*
              Fix M7 (2026-08-16) — pas d'assemblée, donc pas de documents d'assemblée.
              En SARL AU les décisions sont prises par l'associé unique : ni quorum à
              constater, ni feuille de présence multi-signataires à faire émarger.
              Ces panneaux étaient pourtant proposés, et les PV produits n'avaient
              aucune portée. On les masque quand la forme est SARL AU.
            */}
            {!(isAU) && (
              <IncidentSeancePanel
                dossierId={dossier?.id ?? dossierId ?? ticket?.dossierId}
                ticketId={ticket?.id}
                formeJuridique={isAU ? 'SARL_AU' : 'SARL'}
                societe={societeInput}
                // Type, date d'assemblee et convocation viennent de l'etape 1 :
                // ces panneaux les recoivent, ils ne les redemandent pas.
                defaultSeance={{
                  type: 'ordinaire',
                  date: dateAgo,
                  convDate: convocationDate,
                  convHeure: convocationHeure,
                }}
                lockType
                hideSeanceDate
                hideConvDate
                initialAssocies={bdAssocies}
                initialGerants={bdGerants}
              />
            )}
            {!(isAU) && (
              <FeuillePresencePanel
                dossierId={dossier?.id ?? dossierId ?? ticket?.dossierId}
                ticketId={ticket?.id}
                formeJuridique={isAU ? 'SARL_AU' : 'SARL'}
                societe={societeInput}
                // Type, date d'assemblee et convocation viennent de l'etape 1 :
                // ces panneaux les recoivent, ils ne les redemandent pas.
                defaultSeance={{
                  type: 'ordinaire',
                  date: dateAgo,
                  convDate: convocationDate,
                  convHeure: convocationHeure,
                }}
                lockType
                hideSeanceDate
                hideConvDate
                initialAssocies={bdAssocies}
                initialGerants={bdGerants}
                contexteSeance={`AGO${dateAgo ? ` du ${dateAgo}` : ''}`}
              />
            )}
          </div>
        )}

        {step === 4 && (
          <div className="space-y-4" data-testid="pvago-step4-pieces">
            <header>
              <div className="flex items-center gap-2">
                <Paperclip className="h-5 w-5 text-accent" />
                <h2 className="text-lg font-semibold text-fg">
                  Étape 4 — Pièces jointes (optionnelle)
                </h2>
              </div>
              <p className="text-sm text-fg-subtle">
                Déposez ici les <strong>versions légalisées / signées</strong> (PV signé, rapport
                de gestion signé, états de synthèse, liasse fiscale). Cette étape est{' '}
                <strong>entièrement facultative</strong> : vous pouvez la laisser vide et
                poursuivre vers la synthèse. Chaque dépôt est versionné en Data Room.
              </p>
            </header>
            <PiecesJointesPanel
              dossierId={dossier?.id ?? dossierId ?? ticket?.dossierId}
              ticketId={ticket?.id}
              denomination={denomination}
              forme={formeLabel(formeJuridique)}
              motif={`Approbation des comptes ${exercice}`}
              onDeposited={(entry) =>
                setPiecesJointes((prev) => {
                  const next = prev.filter((p) => p.id !== entry.id);
                  next.push(entry);
                  return next;
                })
              }
            />
            {piecesJointes.length === 0 && (
              <p className="rounded-lg border border-border bg-bg-overlay p-3 text-xs text-fg-subtle">
                Aucune pièce jointe déposée pour le moment — cette étape est facultative.
              </p>
            )}
          </div>
        )}

        {step === 5 && (
          <div className="space-y-4" data-testid="pvago-synthese">
            <div className="rounded-xl bg-gradient-to-r from-amber-500 to-amber-600 p-6 text-bg-raised">
              <CheckCircle className="mb-2 h-8 w-8" />
              <h2 className="text-2xl font-bold">PV d'approbation {exercice} finalisé</h2>
              <p className="text-sm text-amber-100">
                Exercice {exercice} — {templateCode}. PV et rapport déposés en dataroom.
              </p>
            </div>
            <div className="rounded-xl border border-border bg-bg-raised p-4 text-sm">
              <p className="mb-3 font-semibold text-fg">Récapitulatif</p>
              <ul className="space-y-1 text-fg-muted">
                <li>• Société : <strong>{denomination || '—'}</strong></li>
                <li>• Exercice clos : <strong>{exerciceClosDate || exercice}</strong></li>
                <li>• Date de l'AGO : <strong>{dateAgo || '—'}</strong> (assemblée ordinaire)</li>
                <li>
                  • Convocation :{' '}
                  {convocationDate
                    ? `${convocationDate}${convocationHeure ? ` à ${convocationHeure}` : ''}`
                    : 'Aucune'}
                </li>
                <li>• Résultat : <strong>{resultatType} — {resultatNetNum.toLocaleString('fr-FR')} MAD</strong></li>
                <li>• Affectation totale : {sumAffectations.toLocaleString('fr-FR')} MAD</li>
                <li>
                  • Dividendes :{' '}
                  {dividendeDistribue === 'oui'
                    ? `${num(dividendeMontantTotal).toLocaleString('fr-FR')} MAD au total, `
                      + `${num(dividendeParPart).toLocaleString('fr-FR')} MAD par part`
                      + (dividendeMiseEnPaiementDate
                        ? ` — mise en paiement le ${dividendeMiseEnPaiementDate}`
                        : '')
                    : 'Aucune distribution'}
                </li>
                <li>• Quitus à la gérance : {quitusGerance === 'oui' ? 'Accordé' : 'Non accordé'}</li>
                <li>
                  • Conventions réglementées :{' '}
                  {conventionsReglementees === 'oui' ? 'Approuvées' : 'Aucune (loi 5-96)'}
                </li>
                <li>
                  • Commissaire aux comptes :{' '}
                  {commissairePresent === 'oui' ? commissaireNom || 'présent' : 'absent'}
                </li>
                <li>• PV d'approbation validé : ✓</li>
                <li>
                  • Rapport de gestion :{' '}
                  {rapportTemplate && docs[rapportTemplate.code]?.validated
                    ? 'généré et validé'
                    : 'non généré (optionnel)'}
                </li>
                <li>
                  • Pièces jointes :{' '}
                  {piecesJointes.length > 0
                    ? piecesJointes.map((p) => p.label).join(', ')
                    : 'aucune (étape facultative)'}
                </li>
              </ul>
            </div>
            <div className="rounded-xl border border-border bg-bg-raised p-4 text-sm text-fg-muted">
              <p>
                <strong>Aucune annonce légale :</strong> l'approbation des comptes n'est pas
                opposable aux tiers — elle ne fait l'objet d'aucune publication au Journal
                d'Annonces Légales.
              </p>
            </div>
            {isTerminated && (
              <Button onClick={() => navigate('/tickets')}>Retour aux tickets</Button>
            )}
          </div>
        )}
      </WorkflowShell>

      {showCancel && ticket && (
        <CancelTicketDialog
          ticket={ticket}
          onClose={() => setShowCancel(false)}
          onConfirm={async (comment) => {
            await ticketService.transition(ticket.id, { target: 'ANNULE', comment });
            navigate('/tickets');
          }}
        />
      )}
    </>
  );
}

function FieldError({ msg }: { msg: string }) {
  return (
    <p className="mt-1 flex items-start gap-1 text-xs text-danger" role="alert">
      <AlertCircle className="mt-0.5 h-3 w-3 flex-shrink-0" />
      <span>{msg}</span>
    </p>
  );
}

function Loader() {
  return (
    <div className="flex h-64 items-center justify-center">
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-border border-t-indigo-600" />
    </div>
  );
}
