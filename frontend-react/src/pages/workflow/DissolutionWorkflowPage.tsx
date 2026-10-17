/**
 * Workflow DISSOLUTION en 4 étapes — spec directeur (2026-08-12).
 *
 *  1. Saisie      — société, motif, date de l'AGE (extraordinaire uniquement),
 *                   liquidateur (BD d'abord, sinon externe + OCR CIN), siège de la
 *                   liquidation, convocation OPTIONNELLE (règle des 16 jours).
 *  2. Génération  — PV de dissolution + annonce légale de dissolution ; feuille de
 *                   présence et PV d'incident optionnels (non bloquants).
 *  3. Pièces jointes — dépôt des versions légalisées (OPTIONNELLE, peut rester vide).
 *  4. Synthèse    — récapitulatif + clôture.
 *
 * ZÉRO re-saisie : l'identité société (dénomination, capital, siège, RC, ville du greffe)
 * est lue en BD ; les données de l'étape 1 alimentent le PV ET l'annonce.
 */
import { useCallback, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertCircle, AlertTriangle, Megaphone, Paperclip, Sparkles } from 'lucide-react';
import { InfoBulle } from '../../components/ui/Aide';
import { ValeurProposee } from '../../components/workflow/ValeurProposee';
import { Button } from '../../components/ui/Button';
import { TextField, ZoneTexte } from '../../components/ui/TextField';
import { WorkflowShell } from '../../components/workflow/WorkflowShell';
import type { RoadmapStep } from '../../components/workflow/WorkflowRoadmap';
import { ticketService } from '../../services/ticket.service';
import { CancelTicketDialog } from '../tickets/CancelTicketDialog';
import { useWorkflow } from './useWorkflow';
import { useStepAutosave } from './useStepAutosave';
import { WorkflowBoot } from './WorkflowBoot';
import { AideEtape } from './AideEtape';
import { ETAPES_DISSOLUTION, etapesRoadmap } from './aidesParcours';
import { bornesDateSeance, useDossierParties, useEtatReinitialise } from './parcoursHooks';
import { DossierAutocomplete } from '../../components/workflow/DossierAutocomplete';
import { SeanceOperationForm } from '../../components/workflow/SeanceOperationForm';
import { ConvocationPanel, FeuillePresencePanel } from '../../components/workflow/SeanceDocPanels';
import { IncidentSeancePanel } from '../../components/workflow/IncidentSeancePanel';
import { DissolutionAnnoncePanel } from '../../components/workflow/DissolutionAnnoncePanel';
import { LiquidateurPicker, type LiquidateurState } from '../../components/workflow/LiquidateurPicker';
import { buildLiquidateurOptions, emptyLiquidateur } from '../../components/workflow/liquidateurOptions';
import { champsPersonneMorale, identiteLiquidateur, manqueLiquidateur } from '../../lib/liquidateur';
import {
  champsMandatLiquidateur,
  dissolutionPv,
  mandatPv,
  manqueMandatLiquidateur,
  manquesDetailPv,
  type DetailPvDissolution,
  type MentionsPv,
} from '../../lib/liquidationPv';
import { MandatLiquidateurFields, PrecisionsMotifFields } from '../../components/workflow/MandatLiquidateurFields';
import {
  PiecesJointesPanel,
  type PieceJointeEntry,
} from '../../components/workflow/PiecesJointesPanel';
import {
  CONVOCATION_DELAI_JOURS,
  addDaysIso,
  convocationDelaiError,
} from '../../components/workflow/convocationDelai';
import { LIQUIDATION_DELAI_JOURS } from '../../components/workflow/liquidationDelai';
import {
  buildDocFilename,
  formeLabel,
} from '../../components/workflow/workflowFilename';
import type { TemplateInfo } from '../../services/workflowDocumentService';
import type { AssocieInput, GerantInput } from '../../components/workflow/SeanceForm';
import type { DossierBrief } from '../../types/dataroom';
import { ErreurParcours, SqueletteParcours } from '../../components/workflow/EtatsParcours';
import { BandeauFinParcours } from '../../components/workflow/BandeauFinParcours';
import { Select } from '../../components/ui/Select';

const STEPS: RoadmapStep[] = etapesRoadmap(ETAPES_DISSOLUTION);

const MOTIF_MIN_LENGTH = 20;

// Lot W1 revise (2026-07-04) : seules les societes ACTIVE sont dissolvables.
const ALLOWED_STATUTS = ['ACTIVE'];

/** Libellé « type de document » pour le nommage `type - dénom - forme (- v<n>)`. */
function dissolutionDocType(code: string): string {
  if (code.startsWith('ANNONCE_LEGALE_DISSOLUTION')) return 'Annonce légale — Dissolution';
  if (code.startsWith('PV_DISSOLUTION_LIQUIDATION')) return 'PV — Dissolution';
  if (code.startsWith('CONVOCATION')) return 'Convocation';
  if (code.startsWith('FEUILLE_PRESENCE')) return 'Feuille de présence';
  if (code.includes('DEFAUT_QUORUM')) return 'PV — Défaut de quorum';
  if (code.includes('IRREGULARITE')) return 'PV — Irrégularité de convocation';
  return code;
}

/**
 * Monte {@link DissolutionWorkflowPageBody} SOUS {@link WorkflowBoot} : les champs de chaque
 * etape s'initialisent avec `useState(stepData...)`, qui ne lit sa valeur qu'au
 * premier render. Sans ce montage differe, ce premier render a lieu AVANT la
 * reponse du serveur et tous les champs restent vides apres un rechargement
 * (F5, deconnexion/reconnexion), meme sur une etape deja validee.
 */
/**
 * Ordre du jour de l'AGE de dissolution — il était vide, alors qu'il est fixé par
 * la loi et toujours identique. Amorce éditable, pas un verrou.
 */
const ORDRE_DU_JOUR_DISSOLUTION = [
  'Dissolution anticipée de la société',
  'Nomination du liquidateur et détermination de ses pouvoirs',
  'Fixation du siège de la liquidation',
  'Pouvoirs en vue des formalités légales',
];


/** L4b (E7) : motifs de dissolution du corpus ($DISSOLUTION_MOTIF), valeurs exactes du modèle. */
const NATURES_DISSOLUTION = [
  { value: 'volonte_associes', label: 'Dissolution anticipée décidée par les associés' },
  { value: 'terme_statutaire', label: 'Arrivée du terme statutaire' },
  { value: 'realisation_objet', label: "Réalisation de l'objet social" },
  { value: 'extinction_objet', label: "Extinction de l'objet social" },
  { value: 'situation_nette_art86', label: 'Capitaux propres inférieurs au quart du capital (article 86)' },
  { value: 'cause_statutaire', label: 'Cause de dissolution prévue par les statuts' },
  { value: 'autre', label: 'Autre motif (le motif saisi est publié)' },
];

/** Valeurs enregistrées avant L4b, relues dans le vocabulaire du corpus. */
function natureCorpus(v: string): string {
  if (v === 'anticipée') return 'volonte_associes';
  if (v === 'article 86') return 'situation_nette_art86';
  if (v === 'arrivée du terme') return 'terme_statutaire';
  return v;
}

export function DissolutionWorkflowPage() {
  return (
    <WorkflowBoot>
      <DissolutionWorkflowPageBody />
    </WorkflowBoot>
  );
}

function DissolutionWorkflowPageBody() {
  const navigate = useNavigate();
  const {
    ticket,
    progress,
    loading,
    saving,
    error,
    setError,
    stepData,
    viewStep,
    maxStep,
    saveDraft,
    executeStep,
    goToStep,
    goPrev: navPrev,
    registerDirty,
  } = useWorkflow();
  const [showCancel, setShowCancel] = useState(false);

  // ---------- Etape 1 : saisie ----------
  const initialId = (stepData.step1?.dossierId as string) ?? ticket?.dossierId ?? '';
  const [dossierIdSaisi, setDossierId] = useState(initialId);
  // Societe du brouillon reprise quand la selection est videe (ancien effet de resynchronisation).
  const dossierId = dossierIdSaisi || ((stepData.step1?.dossierId as string | undefined) ?? '');
  const [dossier, setDossier] = useState<DossierBrief | null>(null);
  const [motif, setMotif] = useState((stepData.step1?.motif as string) ?? '');
  // L4a (E2) : nature de la dissolution, SAISIE (le serveur la deduisait du motif libre et
  // supposait "volontaire" ; vide, le modele imprimait la branche "arrivee du terme").
  // L4b (E7) : motifs de dissolution du corpus (DISSOLUTION_MOTIF), mot pour mot. Les valeurs
  // enregistrees avant L4b (« anticipée », « article 86 », « arrivée du terme ») sont relues.
  const [nature, setNature] = useState(() => natureCorpus((stepData.step1?.nature as string) ?? ''));
  const [dateEffet, setDateEffet] = useState((stepData.step1?.dateEffet as string) ?? '');
  const [termeDate, setTermeDate] = useState((stepData.step1?.termeDate as string) ?? '');
  const [exerciceClosDate, setExerciceClosDate] = useState((stepData.step1?.exerciceClosDate as string) ?? '');
  const [clauseArticle, setClauseArticle] = useState((stepData.step1?.clauseArticle as string) ?? '');
  const [siegeIdentique, setSiegeIdentique] = useState((stepData.step1?.siegeIdentique as string) ?? '');
  const [mandatDateEffet, setMandatDateEffet] = useState((stepData.step1?.mandatDateEffet as string) ?? '');
  // L4d (E11d) : precisions du motif et mandat du liquidateur, lus par le PV du corpus.
  const [detailPv, setDetailPv] = useState<DetailPvDissolution>(
    () => (stepData.step1?.detailPv as DetailPvDissolution | undefined) ?? {},
  );
  const changerDetailPv = (patch: DetailPvDissolution) => setDetailPv((d) => ({ ...d, ...patch }));
  // L4d (E11d) : mentions du PV saisies a l'etape 2 (brouillon de l'etape).
  const [mentionsPv, setMentionsPv] = useState<MentionsPv>(
    () => (stepData.step2?.mentionsPv as MentionsPv | undefined) ?? {},
  );
  const [dateAGE, setDateAGE] = useState((stepData.step1?.dateAGE as string) ?? '');
  // Erreurs revelees a l'arrivee sur l'etape 1 si une saisie existe deja ; remises a zero a chaque changement d'etape.
  const [revealErrors, setRevealErrors] = useEtatReinitialise(
    viewStep,
    () => viewStep === 1 && (motif.length > 0 || !!dateAGE),
  );
  const [liquidateur, setLiquidateur] = useState<LiquidateurState>(
    () => (stepData.step1?.liquidateur as LiquidateurState | undefined) ?? emptyLiquidateur(),
  );
  const [siegeLiquidation, setSiegeLiquidation] = useState(
    (stepData.step1?.siegeLiquidation as string) ?? '',
  );
  const [convocationDate, setConvocationDate] = useState(
    ((stepData.step1?.convocation as { date?: string } | undefined) ?? {}).date ?? '',
  );
  const [convocationHeure, setConvocationHeure] = useState(
    ((stepData.step1?.convocation as { heure?: string } | undefined) ?? {}).heure ?? '',
  );
  const [convocationSnapshot, setConvocationSnapshot] = useState<Record<string, unknown> | null>(
    (stepData.step1?.convocationSnapshot as Record<string, unknown>) ?? null,
  );

  // ---------- Etape 2 : generation ----------
  const [pvValidated, setPvValidated] = useState<boolean>(
    !!(stepData.step2?.pvValide as boolean),
  );
  const [annonceValidated, setAnnonceValidated] = useState<boolean>(
    !!(stepData.step2?.annonceValide as boolean),
  );
  const [depotLegalNumero, setDepotLegalNumero] = useState(
    ((stepData.step2?.depotLegal as { numero?: string } | undefined) ?? {}).numero ?? '',
  );
  const [depotLegalDate, setDepotLegalDate] = useState(
    ((stepData.step2?.depotLegal as { date?: string } | undefined) ?? {}).date ?? '',
  );

  // ---------- Etape 3 : pieces jointes ----------
  const [piecesJointes, setPiecesJointes] = useState<PieceJointeEntry[]>(
    () => (stepData.step3?.piecesJointes as PieceJointeEntry[] | undefined) ?? [],
  );

  // Parties prenantes (gerants/associes) DES la selection de la societe : elles
  // peuplent le selecteur de liquidateur ET la convocation, sans re-saisie.
  // Best-effort : un echec de lecture laisse la saisie externe possible.
  const { dossierParties, partiesLoading } = useDossierParties(dossierId);

  const formeJuridique = useMemo(
    () =>
      (dossier?.formeJuridique as string | null | undefined) ??
      (stepData.step1?.formeJuridique as string | undefined) ??
      null,
    [dossier, stepData.step1?.formeJuridique],
  );
  const isAU = formeJuridique === 'SARL_AU';

  const liquidateurOptions = useMemo(
    () => buildLiquidateurOptions(dossierParties),
    [dossierParties],
  );

  // Associes / gerants BD -> pre-remplissage des seances (convocation, feuille de
  // presence) : aucune re-saisie d'identite.
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
            presence: '', // L3 (D6) : saisie a l'ecran, jamais supposee
            mandataireNom: '',
            // L3 (D6) : type de piece repris de la fiche, sinon "CIN" propose a l'ecran.
            pieceType: String(a?.pieceType ?? ''),
            nationalite: String(a?.nationalite ?? ''), // L4a (E2) : reprise de la fiche, sinon proposee
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

  const [{ minDate, maxDate }] = useState(bornesDateSeance);

  // Regle DURE des 16 jours (miroir exact du backend WorkflowSteps.convocationDelaiError).
  const convocationError = useMemo(
    () => convocationDelaiError(convocationDate, dateAGE),
    [convocationDate, dateAGE],
  );

  type FieldErrors = {
    dossierId?: string;
    dateAGE?: string;
    motif?: string;
    nature?: string;
    dateEffet?: string;
    natureDetail?: string;
    siegeIdentique?: string;
    mandatDateEffet?: string;
    liquidateur?: string;
    siegeLiquidation?: string;
    convocation?: string;
    mandatLiquidateur?: string;
    objetEvenement?: string;
    situationNetteMontant?: string;
    approbationComptesDate?: string;
    art86MotifsEcartement?: string;
    clauseTexte?: string;
    dureeMandat?: string;
    redditionPeriodicite?: string;
  };
  const fieldErrors = useMemo<FieldErrors>(() => {
    const errs: FieldErrors = {};
    if (!dossierId.trim()) errs.dossierId = 'Sélectionnez la société à dissoudre.';
    if (!dateAGE) errs.dateAGE = isAU ? 'La date de la décision est obligatoire.' : 'La date de l’AGE est obligatoire.';
    if (!nature) errs.nature = 'Choisissez la nature de la dissolution.';
    if (!dateEffet) errs.dateEffet = "Confirmez ou saisissez la date d'effet de la dissolution.";
    if (nature === 'terme_statutaire' && !termeDate) errs.natureDetail = 'Indiquez la date du terme statutaire.';
    if (nature === 'situation_nette_art86' && !exerciceClosDate) {
      errs.natureDetail = "Indiquez la date de clôture de l'exercice dont les états de synthèse constatent les pertes.";
    }
    if (nature === 'cause_statutaire' && !clauseArticle.trim()) {
      errs.natureDetail = "Indiquez l'article des statuts qui prévoit la cause de dissolution.";
    }
    if (!siegeIdentique) errs.siegeIdentique = 'Indiquez si le siège de la liquidation est le siège social.';
    if (!mandatDateEffet) errs.mandatDateEffet = "Confirmez ou saisissez la date d'entrée en fonctions du liquidateur.";
    if (motif.trim().length < MOTIF_MIN_LENGTH) {
      errs.motif = `Le motif est trop court (${motif.trim().length}/${MOTIF_MIN_LENGTH} caractères minimum) : précisez la cause.`;
    }
    // Lot L4d (E5) : personne physique ou morale, message nomme.
    const manque = manqueLiquidateur(liquidateur);
    if (manque) errs.liquidateur = manque;
    if (siegeIdentique === 'non' && !siegeLiquidation.trim()) {
      errs.siegeLiquidation = "Indiquez l'adresse du siège de la liquidation.";
    }
    if (convocationError) errs.convocation = convocationError;
    // L4d (E11d) : donnees du PV de dissolution du corpus, nommees des l'etape 1.
    const mandat = manqueMandatLiquidateur(liquidateur);
    if (mandat && !errs.liquidateur) errs.mandatLiquidateur = mandat;
    Object.assign(errs, manquesDetailPv(nature, detailPv));
    return errs;
  }, [dossierId, dateAGE, motif, nature, dateEffet, termeDate, exerciceClosDate, clauseArticle, siegeIdentique,
    mandatDateEffet, liquidateur, siegeLiquidation, convocationError, detailPv, isAU]);

  const targetDenomination = dossier?.raisonSociale ?? ticket?.titre ?? '';

  /** Nommage transverse : `type - dénomination - forme (- v<n>)`. */
  const workflowFilenameFor = useCallback(
    (tpl: TemplateInfo, opts?: { version?: number }) =>
      buildDocFilename(
        dissolutionDocType(tpl.code),
        targetDenomination,
        formeLabel(formeJuridique),
        opts?.version,
      ),
    [targetDenomination, formeJuridique],
  );

  const depositMotif = useMemo(
    () => `Dissolution : ${motif || 'dissolution anticipée'}`,
    [motif],
  );

  const convocationPayload = useCallback(() => {
    if (!convocationDate) return undefined;
    return { date: convocationDate, heure: convocationHeure || undefined };
  }, [convocationDate, convocationHeure]);

  // Bloc « opération » consommé par le PV : le liquidateur vient de l'étape 1 —
  // le sous-formulaire ne le redemande donc PAS (zéro champ dupliqué).
  const operationInput = useMemo(
    () => ({
      liquidateur: {
        civilite: liquidateur.civilite,
        prenom: liquidateur.prenom,
        nom: liquidateur.nom,
        adresse: liquidateur.adresse,
        pieceType: liquidateur.pieceType ?? '', // L3 (D6) : confirme a l'ecran
        pieceNumero: liquidateur.cin,
        genre: (liquidateur.civilite === 'Mme' || liquidateur.civilite === 'Mlle'
          ? 'féminin'
          : 'masculin') as 'masculin' | 'féminin',
        remuneration: liquidateur.remuneration,
        ...champsPersonneMorale(liquidateur), // L4d (E5)
        ...champsMandatLiquidateur(liquidateur), // L4d (E11d)
        ...mandatPv(detailPv),
        siege: siegeIdentique === 'non' ? siegeLiquidation : '',
        siegeIdentique,
        mandatDateEffet,
      },
      dissolutionDate: dateAGE,
      // L4d (E11d) : date d'effet confirmee et precisions du motif (PV du corpus).
      dissolution: {
        dateEffet, termeDate, exerciceClosDate, clauseArticle,
        siegeLiquidationIdentique: siegeIdentique,
        ...dissolutionPv(detailPv),
      },
    }),
    [liquidateur, siegeLiquidation, siegeIdentique, mandatDateEffet, dateAGE, dateEffet, termeDate,
      exerciceClosDate, clauseArticle, detailPv],
  );

  /**
   * Contenu courant de l'étape — source unique du brouillon (2026-08-14).
   *
   * Il n'existait pas : `handleSaveDraft` construisait ce payload en interne et
   * ce workflow n'enregistrait AUCUN « dirty getter ». `flushDraft` ne trouvait
   * donc rien à sauvegarder, et tout ce qui était saisi sans valider disparaissait
   * au rafraîchissement comme à la déconnexion. Extrait ici, il sert à la fois au
   * bouton « Sauvegarder brouillon » et à la sauvegarde automatique.
   */
  const draftPayloadFor = useCallback(
    (n: number): Record<string, unknown> | null => {
      if (n === 1) {
        return {
          dossierId,
          motif,
          nature,
          dateEffet,
          termeDate,
          exerciceClosDate,
          clauseArticle,
          siegeIdentique,
          mandatDateEffet,
          dateAGE,
          formeJuridique,
          liquidateur,
          siegeLiquidation,
          detailPv,
          convocation: convocationPayload(),
          convocationSnapshot,
        };
      }
      if (n === 2) {
        return {
          pvValide: pvValidated,
          annonceValide: annonceValidated,
          mentionsPv,
          depotLegal: { numero: depotLegalNumero, date: depotLegalDate },
        };
      }
      if (n === 3) return { piecesJointes };
      return null;
    },
    [dossierId, motif, nature, dateEffet, termeDate, exerciceClosDate, clauseArticle, siegeIdentique,
      mandatDateEffet, dateAGE, formeJuridique, liquidateur, siegeLiquidation, detailPv, mentionsPv,
      convocationPayload, convocationSnapshot, pvValidated,
      annonceValidated, depotLegalNumero, depotLegalDate, piecesJointes],
  );

  // Enregistre le getter pour l'etape AFFICHEE : navigation ET sauvegarde
  // automatique (20 s) persistent la saisie en cours. Appele avant les retours
  // anticipes : l'ordre des hooks ne varie jamais.
  useStepAutosave(viewStep, () => draftPayloadFor(viewStep), registerDirty);

  if (loading) return <Loader />;
  if (!ticket || !progress)
    return (
      <ErreurParcours>
        {error ?? 'Parcours introuvable : rechargez la page ou revenez à la liste des tickets.'}
      </ErreurParcours>
    );

  const step = viewStep;
  const isTerminated = progress.statut === 'TERMINE';

  async function handleValidate() {
    setError(null);
    if (step === 1) {
      const errs = Object.values(fieldErrors).filter(Boolean) as string[];
      if (errs.length > 0) {
        setRevealErrors(true);
        setError(`Veuillez corriger ${errs.length} erreur${errs.length > 1 ? 's' : ''} (signalée${errs.length > 1 ? 's' : ''} en rouge).`);
        return;
      }
      await executeStep(1, {
        dossierId,
        motifDissolution: motif,
        dateAGE,
        formeJuridique: formeJuridique ?? undefined,
        liquidateur: {
          source: liquidateur.source,
          civilite: liquidateur.civilite,
          prenom: liquidateur.prenom,
          nom: liquidateur.nom,
          cin: liquidateur.cin || undefined,
          adresse: liquidateur.adresse,
          remuneration: liquidateur.remuneration,
          ...champsPersonneMorale(liquidateur), // L4d (E5)
          ...champsMandatLiquidateur(liquidateur), // L4d (E11d)
          ...mandatPv(detailPv),
          siege: siegeIdentique === 'non' ? siegeLiquidation : '',
          siegeIdentique,
          mandatDateEffet,
        },
        siegeLiquidation: siegeIdentique === 'non' ? siegeLiquidation : '',
        siegeIdentique,
        nature,
        dateEffet,
        termeDate,
        exerciceClosDate,
        clauseArticle,
        detailPv,
        convocation: convocationPayload(),
      });
    } else if (step === 2) {
      if (!pvValidated) {
        setError('Générez puis validez le procès-verbal de dissolution avant de continuer.');
        return;
      }
      if (!annonceValidated) {
        setError(
          'Générez puis validez l’annonce légale de dissolution : sa publication dans un journal '
            + 'd’annonces légales est obligatoire (le numéro et la date du dépôt légal restent '
            + 'facultatifs à ce stade).',
        );
        return;
      }
      await executeStep(2, {
        pvValide: true,
        annonceValide: true,
        mentionsPv,
        depotLegal:
          depotLegalNumero || depotLegalDate
            ? { numero: depotLegalNumero || null, date: depotLegalDate || null }
            : undefined,
      });
    } else if (step === 3) {
      // Etape OPTIONNELLE : jamais bloquante, peut etre vide.
      await executeStep(3, {
        piecesJointes: piecesJointes.map((p) => ({
          id: p.id,
          label: p.label,
          filename: p.filename,
          version: p.version ?? null,
        })),
      });
    } else if (step === 4) {
      await executeStep(4, {});
    }
  }

  async function handleSaveDraft() {
    const payload = draftPayloadFor(step);
    if (payload) await saveDraft(step, { [`step${step}`]: payload });
  }

  async function handleCloturer() {
    if (!ticket) return;
    try {
      await ticketService.transition(ticket.id, { target: 'CLOTURE_DOSSIER' });
      navigate('/tickets');
    } catch (err) {
      setError((err as Error)?.message ?? 'La clôture du parcours a échoué : réessayez.');
    }
  }

  const decisionLabel = isAU ? 'Décision de l’associé unique' : 'Assemblée générale extraordinaire (AGE)';
  const showErr = (field: keyof FieldErrors): string | null =>
    revealErrors ? fieldErrors[field] ?? null : null;

  // 2026-08-14 — Ne portait que la denomination et la ville : le lieu de seance
  // (defaut = siege social) et le capital restaient donc vides dans les documents
  // de seance, alors que la base les connait.
  const societeInput = {
    denomination: targetDenomination,
    villeGreffe: dossierParties?.villeGreffe || dossier?.ville,
    siegeSocial: dossierParties?.siegeSocial,
    capitalChiffres: dossierParties?.capitalSocial,
    nombreParts: dossierParties?.nombreParts,
    rcNumero: dossierParties?.rcNumero,
  };

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
        onSaveDraft={!isTerminated && step < 4 ? handleSaveDraft : undefined}
        onValidate={!isTerminated ? handleValidate : undefined}
        onCancel={() => setShowCancel(true)}
        footer={{ validateLabel: step === 4 ? 'Finaliser' : 'Valider l’étape' }}
      >
        <AideEtape
          parcours="dissolution"
          etapes={ETAPES_DISSOLUTION}
          etape={step}
          manquants={step === 1 ? (Object.values(fieldErrors).filter(Boolean) as string[]) : undefined}
        />

        {step === 1 && (
          <div className="space-y-5" data-testid="dissolution-step1">
            <header>
              <h2 className="text-lg font-semibold text-fg">Étape 1 — Saisie</h2>
              <p className="text-sm text-fg-subtle">
                Sélectionnez la société <strong>active</strong> à dissoudre, puis renseignez le
                motif, la date de l’assemblée, le liquidateur et le siège de la liquidation.
                L’identité de la société (dénomination, capital, siège, RC, ville du greffe) est
                lue dans le dossier : vous n’avez <strong>jamais à la ressaisir</strong>.
              </p>
            </header>

            <div>
              <DossierAutocomplete
                value={dossierId}
                onSelect={(d) => {
                  setDossier(d);
                  setDossierId(d?.id ?? '');
                }}
                allowedStatuts={ALLOWED_STATUTS}
                help="Seules les sociétés au statut « active » peuvent être dissoutes."
              />
              {showErr('dossierId') && (
                <p className="mt-1 flex items-start gap-1 text-xs text-danger" role="alert">
                  <AlertCircle className="mt-0.5 h-3 w-3 flex-shrink-0" />
                  {showErr('dossierId')}
                </p>
              )}
            </div>

            {dossier && (
              <div className="rounded-lg border border-info/30 bg-info-soft p-3 text-xs text-fg-muted" role="status">
                Organe compétent : <strong>{decisionLabel}</strong>. Actes produits : procès-verbal
                de dissolution anticipée{isAU ? ' (SARL à associé unique)' : ' (SARL)'} et annonce
                légale de dissolution.
              </div>
            )}

            <TextField
              label={`Date ${isAU ? 'de la décision' : 'de l’AGE'} *`}
              type="date"
              value={dateAGE}
              min={minDate}
              max={maxDate}
              data-testid="dissolution-date-age"
              onChange={(e) => setDateAGE(e.target.value)}
              onBlur={() => setRevealErrors(true)}
              error={showErr('dateAGE') ?? undefined}
              hint={
                isAU
                  ? 'La dissolution relève de la décision de l’associé unique.'
                  : 'La dissolution se décide toujours en assemblée générale extraordinaire.'
              }
            />

            <div>
              <label htmlFor="dissolution-nature" className="mb-1 flex items-center gap-1 text-sm font-medium text-fg-muted">
                Nature de la dissolution *
                <InfoBulle
                  libelle="Pourquoi la nature de la dissolution ?"
                  texte="Le procès-verbal ne rédige pas la même résolution selon la cause : décision anticipée des associés, capitaux propres devenus inférieurs au quart du capital (article 86 de la loi 5-96) ou arrivée du terme statutaire. Le motif détaillé ci-dessous complète ce choix."
                />
              </label>
              <Select
                id="dissolution-nature"
                value={nature}
                onChange={(e) => setNature(e.target.value)}
                aria-invalid={!!showErr('nature')}
                error={showErr('nature') || undefined}
              >
                <option value="">Choisir…</option>
                {NATURES_DISSOLUTION.map((n) => (
                  <option key={n.value} value={n.value}>{n.label}</option>
                ))}
              </Select>
            </div>

            {nature === 'terme_statutaire' && (
              <TextField label="Date du terme statutaire *" type="date" value={termeDate}
                data-testid="dissolution-terme-date" onChange={(e) => setTermeDate(e.target.value)}
                error={showErr('natureDetail') ?? undefined} />
            )}
            {nature === 'situation_nette_art86' && (
              <TextField label="Exercice clos le *" type="date" value={exerciceClosDate}
                data-testid="dissolution-exercice-clos" onChange={(e) => setExerciceClosDate(e.target.value)}
                error={showErr('natureDetail') ?? undefined}
                hint="Date de clôture de l’exercice dont les états de synthèse constatent les pertes." />
            )}
            {nature === 'cause_statutaire' && (
              <TextField label="Article des statuts *" value={clauseArticle}
                data-testid="dissolution-clause-article" onChange={(e) => setClauseArticle(e.target.value)}
                error={showErr('natureDetail') ?? undefined} hint="Ex. : 34" />
            )}
            <PrecisionsMotifFields nature={nature} valeurs={detailPv} onChange={changerDetailPv}
              erreur={(cle) => showErr(cle as keyof FieldErrors) ?? undefined} />

            <div>
              <ValeurProposee
                id="dissolution-date-effet"
                label="Date d’effet de la dissolution *"
                type="date"
                proposition={dateAGE}
                valeur={dateEffet || undefined}
                onChange={setDateEffet}
              />
              {showErr('dateEffet') && (
                <p className="mt-1 text-xs text-danger" role="alert">{showErr('dateEffet')}</p>
              )}
              <p className="mt-1 text-[11px] text-fg-subtle">
                La date de l’assemblée est proposée ; l’avis légal imprime la date d’effet confirmée.
              </p>
            </div>

            <div>
              {/* `htmlFor` + `id` : ce <textarea> est ecrit a la main (hors TextField),
                  son libelle n'etait donc rattache a rien — champ anonyme pour un
                  lecteur d'ecran, et clic sur le libelle sans effet. */}
              <label
                htmlFor="dissolution-motif"
                className="mb-1 block text-sm font-medium text-fg-muted"
              >
                Motif de la dissolution *
              </label>
              <ZoneTexte
                id="dissolution-motif"
                rows={4}
                value={motif}
                onChange={(e) => setMotif(e.target.value)}
                onBlur={() => setRevealErrors(true)}
                placeholder="Cessation d’activité, mésentente entre associés, perte d’agrément…"
                aria-invalid={!!showErr('motif')}
              />
              <p className={`mt-1 text-xs ${motif.trim().length < MOTIF_MIN_LENGTH ? 'text-danger' : 'text-success'}`}>
                {motif.trim().length} / {MOTIF_MIN_LENGTH} caractères minimum
                {motif.trim().length >= MOTIF_MIN_LENGTH && ' ✓'}
              </p>
              {showErr('motif') && (
                <p className="mt-1 flex items-start gap-1 text-xs text-danger" role="alert">
                  <AlertCircle className="mt-0.5 h-3 w-3 flex-shrink-0" />
                  {showErr('motif')}
                </p>
              )}
              <p className="mt-1 text-[11px] text-fg-subtle">
                Le motif est imprimé au procès-verbal lorsque la nature choisie est « Autre motif » ;
                il est conservé au dossier dans tous les cas et n’apparaît pas dans l’annonce légale.
              </p>
            </div>

            <LiquidateurPicker
              value={liquidateur}
              onChange={setLiquidateur}
              options={liquidateurOptions}
              loading={partiesLoading}
              dossierId={dossierId}
              error={showErr('liquidateur')}
            />

            <div>
              <ValeurProposee
                id="dissolution-mandat-date-effet"
                label="Entrée en fonctions du liquidateur *"
                type="date"
                proposition={dateEffet || dateAGE}
                valeur={mandatDateEffet || undefined}
                onChange={setMandatDateEffet}
              />
              {showErr('mandatDateEffet') && (
                <p className="mt-1 text-xs text-danger" role="alert">{showErr('mandatDateEffet')}</p>
              )}
            </div>

            <MandatLiquidateurFields valeurs={detailPv} onChange={changerDetailPv}
              erreur={(cle) => showErr(cle as keyof FieldErrors) ?? undefined} />
            {showErr('mandatLiquidateur') && (
              <p className="text-xs text-danger" role="alert" data-testid="dissolution-mandat-liquidateur-erreur">
                {showErr('mandatLiquidateur')}
              </p>
            )}

            <div>
              <label htmlFor="dissolution-siege-identique" className="mb-1 flex items-center gap-1 text-sm font-medium text-fg-muted">
                Siège de la liquidation *
                <InfoBulle
                  libelle="Où fixer le siège de la liquidation ?"
                  texte="C’est l’adresse où la correspondance est adressée et où les actes de la liquidation sont notifiés ; l’avis légal la publie. Elle peut être le siège social ou une autre adresse."
                />
              </label>
              <Select
                id="dissolution-siege-identique"
                value={siegeIdentique}
                onChange={(e) => setSiegeIdentique(e.target.value)}
                error={showErr('siegeIdentique') || undefined}
              >
                <option value="">Choisir…</option>
                <option value="oui">Au siège social</option>
                <option value="non">À une autre adresse</option>
              </Select>
            </div>
            {siegeIdentique === 'non' && (
              <TextField
                label="Adresse du siège de la liquidation *"
                value={siegeLiquidation}
                data-testid="dissolution-siege-liquidation"
                onChange={(e) => setSiegeLiquidation(e.target.value)}
                onBlur={() => setRevealErrors(true)}
                error={showErr('siegeLiquidation') ?? undefined}
                hint="Adresse à laquelle sont notifiés les actes de la liquidation (publiée au JAL)."
              />
            )}

            <section className="space-y-3 rounded-xl border border-border bg-bg-raised p-4" data-testid="dissolution-convocation">
              <div className="flex items-center gap-2">
                <Megaphone className="h-4 w-4 text-fg-muted" />
                <h4 className="text-sm font-semibold text-fg">Convocation (facultative)</h4>
              </div>
              <div className="grid gap-3 md:grid-cols-2">
                <TextField
                  label="Date de convocation"
                  type="date"
                  value={convocationDate}
                  data-testid="dissolution-convocation-date"
                  onChange={(e) => {
                    const v = e.target.value;
                    setConvocationDate(v);
                    // L'assemblee se tient au minimum 16 jours apres la convocation.
                    if (v && !dateAGE) setDateAGE(addDaysIso(v, CONVOCATION_DELAI_JOURS));
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
                Règle des <strong>{CONVOCATION_DELAI_JOURS} jours</strong> : l’assemblée ne peut se
                tenir moins de {CONVOCATION_DELAI_JOURS} jours calendaires après la convocation.
                Laissez vide si aucune convocation n’est émise.
              </p>
              {convocationError && (
                <div
                  role="alert"
                  data-testid="dissolution-convocation-16j-error"
                  className="flex items-start gap-2 rounded-lg border border-danger/40 bg-danger-soft p-2 text-xs text-danger"
                >
                  <AlertTriangle className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" />
                  <span>{convocationError}</span>
                </div>
              )}
              <ConvocationPanel
                dossierId={dossierId || ticket?.dossierId}
                ticketId={ticket?.id}
                formeJuridique={isAU ? 'SARL_AU' : 'SARL'}
                societe={societeInput}
                // Date d'assemblee, date ET heure de convocation viennent de cette
                // etape : le panneau les recoit, il ne les redemande pas.
                defaultSeance={{
                  type: 'extraordinaire',
                  date: dateAGE,
                  convDate: convocationDate,
                  convHeure: convocationHeure,
                }}
                initialOrdreDuJour={ORDRE_DU_JOUR_DISSOLUTION}
                embedded
                initialAssocies={bdAssocies}
                initialGerants={bdGerants}
                initialSnapshot={convocationSnapshot}
                onSnapshot={setConvocationSnapshot}
                lockType
                hideSeanceDate
                hideConvDate
              contexteSeance={`Dissolution${dateAGE ? ` du ${dateAGE}` : ''}`}
                versioned
                motif={depositMotif}
                filenameFor={workflowFilenameFor}
              />
            </section>
          </div>
        )}

        {step === 2 && (
          <div className="space-y-4" data-testid="dissolution-step2">
            <header>
              <div className="flex items-center gap-2">
                <Sparkles className="h-5 w-5 text-info" />
                <h2 className="text-lg font-semibold text-fg">Étape 2 — Génération des actes</h2>
              </div>
              <p className="text-sm text-fg-subtle">
                Le <strong>procès-verbal de dissolution</strong> et l’<strong>annonce légale</strong> sont
                produits à partir de l’étape 1 et de la Data Room : aucune donnée n’est ressaisie.
                À la validation, chaque document est <strong>déposé automatiquement</strong> et
                versionné dans la Data Room juridique.
              </p>
            </header>

            <SeanceOperationForm
              mode="dissolution"
              dossierId={ticket?.dossierId ?? dossierId}
              ticketId={ticket?.id}
              formeJuridique={isAU ? 'SARL_AU' : 'SARL'}
              societe={societeInput}
              defaultDate={dateAGE}
              defaultConvocation={
                convocationDate ? { date: convocationDate, heure: convocationHeure || undefined } : undefined
              }
              motif={motif}
              nature={nature}
              operation={operationInput}
              initialAssocies={bdAssocies}
              initialGerants={bdGerants}
              // L4d (E6) : reference lisible du dossier (annee, numero, societe), pas celle du ticket.
              referenceDossier={dossier?.reference ?? ''}
              initialMentions={mentionsPv}
              onMentions={setMentionsPv}
              onReady={setPvValidated}
              versioned
              depositMotif={depositMotif}
              filenameFor={workflowFilenameFor}
            />

            <DissolutionAnnoncePanel
              dossierId={ticket?.dossierId ?? dossierId}
              ticketId={ticket?.id}
              formeJuridique={isAU ? 'SARL_AU' : 'SARL'}
              denomination={targetDenomination}
              dateAGE={dateAGE}
              motif={motif}
              nature={nature}
              dissolutionDetail={{ dateEffet, termeDate, exerciceClosDate, clauseArticle, siegeIdentique, mandatDateEffet }}
              liquidateur={liquidateur}
              siegeLiquidation={siegeIdentique === 'non' ? siegeLiquidation : ''}
              depotLegalNumero={depotLegalNumero}
              depotLegalDate={depotLegalDate}
              onDepotLegalNumeroChange={setDepotLegalNumero}
              onDepotLegalDateChange={setDepotLegalDate}
              onReady={setAnnonceValidated}
              filenameFor={workflowFilenameFor}
              depositMotif={depositMotif}
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
                dossierId={ticket?.dossierId ?? dossierId}
                ticketId={ticket?.id}
                formeJuridique={isAU ? 'SARL_AU' : 'SARL'}
                societe={societeInput}
                // Type, date d'assemblee et convocation viennent de l'etape 1 :
                // ces panneaux les recoivent, ils ne les redemandent pas.
                defaultSeance={{
                  type: 'extraordinaire',
                  date: dateAGE,
                  convDate: convocationDate,
                  convHeure: convocationHeure,
                }}
                lockType
                hideSeanceDate
                hideConvDate
                initialAssocies={bdAssocies}
                initialOrdreDuJour={ORDRE_DU_JOUR_DISSOLUTION}
                initialGerants={bdGerants}
              />
            )}
            {!(isAU) && (
              <FeuillePresencePanel
                dossierId={ticket?.dossierId ?? dossierId}
                ticketId={ticket?.id}
                formeJuridique={isAU ? 'SARL_AU' : 'SARL'}
                societe={societeInput}
                // Type, date d'assemblee et convocation viennent de l'etape 1 :
                // ces panneaux les recoivent, ils ne les redemandent pas.
                defaultSeance={{
                  type: 'extraordinaire',
                  date: dateAGE,
                  convDate: convocationDate,
                  convHeure: convocationHeure,
                }}
                lockType
                hideSeanceDate
                hideConvDate
                contexteSeance={`Dissolution${dateAGE ? ` du ${dateAGE}` : ''}`}
                initialAssocies={bdAssocies}
                initialGerants={bdGerants}
                versioned
                motif={depositMotif}
                filenameFor={workflowFilenameFor}
              />
            )}
          </div>
        )}

        {step === 3 && (
          <div className="space-y-4" data-testid="dissolution-step3-pieces">
            <header>
              <div className="flex items-center gap-2">
                <Paperclip className="h-5 w-5 text-fg-muted" />
                <h2 className="text-lg font-semibold text-fg">Étape 3 — Pièces jointes (facultative)</h2>
              </div>
              <p className="text-sm text-fg-subtle">
                Déposez ici les <strong>versions légalisées</strong> des documents produits
                (procès-verbal signé, annonce publiée, modèle J…). Cette étape est{' '}
                <strong>entièrement facultative</strong> : vous pouvez la laisser vide et
                poursuivre vers la synthèse. Chaque dépôt est versionné dans la Data Room.
              </p>
            </header>
            <PiecesJointesPanel
              dossierId={dossierId || ticket?.dossierId}
              ticketId={ticket?.id}
              denomination={targetDenomination}
              forme={formeLabel(formeJuridique)}
              motif={depositMotif}
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

        {step === 4 && (
          <div className="space-y-4" data-testid="dissolution-synthese">
            <BandeauFinParcours titre="Dissolution finalisée">
                Le procès-verbal de dissolution et l’annonce légale ont été produits, validés et
                déposés dans la Data Room. La société est désormais <strong>dissoute</strong>.
              </BandeauFinParcours>

            <div className="rounded-xl border border-border bg-bg-raised p-4" data-testid="synthese-societe">
              <p className="mb-3 text-sm font-semibold text-fg">Société et décision</p>
              <div className="grid gap-4 rounded-lg bg-bg-overlay p-3 sm:grid-cols-2">
                <div>
                  <p className="text-xs text-fg-subtle">Dénomination</p>
                  <p className="text-sm font-medium text-fg">{targetDenomination || '—'}</p>
                </div>
                <div>
                  <p className="text-xs text-fg-subtle">Forme juridique</p>
                  <p className="text-sm font-medium text-fg">{formeJuridique || '—'}</p>
                </div>
                <div>
                  <p className="text-xs text-fg-subtle">Date {isAU ? 'de la décision' : 'de l’AGE'}</p>
                  <p className="text-sm font-medium text-fg">{dateAGE || '—'}</p>
                </div>
                <div>
                  <p className="text-xs text-fg-subtle">Convocation</p>
                  <p className="text-sm font-medium text-fg">
                    {convocationDate
                      ? `${convocationDate}${convocationHeure ? ` à ${convocationHeure}` : ''}`
                      : 'Aucune'}
                  </p>
                </div>
                <div className="sm:col-span-2">
                  <p className="text-xs text-fg-subtle">Motif</p>
                  <p className="text-sm font-medium text-fg">{motif || '—'}</p>
                </div>
              </div>
            </div>

            <div className="rounded-xl border border-border bg-bg-raised p-4" data-testid="synthese-liquidateur">
              <p className="mb-3 text-sm font-semibold text-fg">Liquidateur</p>
              <div className="grid gap-4 rounded-lg bg-bg-overlay p-3 sm:grid-cols-2">
                <div>
                  <p className="text-xs text-fg-subtle">Identité</p>
                  <p className="text-sm font-medium text-fg">
                    {identiteLiquidateur(liquidateur) || '—'}{' '}
                    <span className="text-xs font-normal text-fg-subtle">
                      ({liquidateur.source === 'BD' ? 'partie prenante du dossier' : 'externe'})
                    </span>
                  </p>
                </div>
                <div>
                  <p className="text-xs text-fg-subtle">Adresse</p>
                  <p className="text-sm font-medium text-fg">{liquidateur.adresse || '—'}</p>
                </div>
                <div className="sm:col-span-2">
                  <p className="text-xs text-fg-subtle">Siège de la liquidation</p>
                  <p className="text-sm font-medium text-fg">{siegeLiquidation || '—'}</p>
                </div>
              </div>
            </div>

            <div className="rounded-xl border border-border bg-bg-raised p-4" data-testid="synthese-documents">
              <p className="mb-3 text-sm font-semibold text-fg">Documents et dépôts</p>
              <ul className="space-y-1 rounded-lg bg-bg-overlay p-3 text-sm text-fg-muted">
                <li>
                  Procès-verbal de dissolution : {pvValidated ? 'produit et validé' : 'non validé'} — déposé
                  et versionné dans la Data Room.
                </li>
                <li>
                  Annonce légale de dissolution : {annonceValidated ? 'produite et validée' : 'non validée'}
                  {depotLegalNumero || depotLegalDate
                    ? ` — dépôt légal n° ${depotLegalNumero || '—'} du ${depotLegalDate || '—'}`
                    : ' — numéro et date du dépôt légal à compléter après le dépôt au greffe'}
                  .
                </li>
                <li>
                  Pièces jointes : {piecesJointes.length > 0
                    ? piecesJointes.map((p) => p.label).join(', ')
                    : 'aucune (étape facultative)'}
                  .
                </li>
              </ul>
            </div>

            <div className="rounded-xl border border-border bg-bg-raised p-4 text-sm text-fg-muted">
              <p><strong>Statut de la société :</strong> dissoute</p>
              <p className="mt-1"><strong>Archivage dans la Data Room :</strong> confirmé.</p>
              <p className="mt-1">
                <strong>Prochaine étape suggérée :</strong> ouvrir un ticket « Liquidation » lorsque le
                délai de {LIQUIDATION_DELAI_JOURS} jours après la dissolution est écoulé.
              </p>
            </div>

            {!isTerminated ? (
              <div className="flex gap-2">
                <Button onClick={handleCloturer}>Clôturer le parcours</Button>
              </div>
            ) : (
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
            navigate(`/tickets?ticket=${ticket.id}`);
          }}
        />
      )}
    </>
  );
}

function Loader() {
  return <SqueletteParcours />;
}
