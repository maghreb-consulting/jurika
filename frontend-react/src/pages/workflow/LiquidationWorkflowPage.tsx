/**
 * Workflow LIQUIDATION en 4 étapes — spec directeur (2026-08-13).
 *
 *  1. Saisie      — société DISSOUTE, date de l'AGE de clôture (extraordinaire uniquement),
 *                   comptes finaux (→ boni/mali calculé), convocation OPTIONNELLE (16 jours).
 *                   Le liquidateur et la date de dissolution sont REPRIS DE LA BD.
 *  2. Génération  — PV de clôture + rapport de liquidation + annonce légale de clôture ;
 *                   feuille de présence et PV d'incident optionnels (non bloquants).
 *  3. Pièces jointes — dépôt des versions légalisées (OPTIONNELLE, peut rester vide).
 *  4. Synthèse    — récapitulatif + finalisation (la société passe en LIQUIDEE).
 *
 * ZÉRO re-saisie : l'identité société, la **date de dissolution**
 * (`entreprise_dossiers.date_dissolution`) et le **liquidateur** (nommé à l'étape 1 du
 * workflow Dissolution) sont lus en base et affichés en LECTURE SEULE. Un cas de secours
 * couvre les dossiers dissous avant cette évolution : la donnée manquante est saisie
 * **une seule fois**, puis persistée par le backend.
 *
 * RG-LI03 — règle des 15 jours : la clôture ne peut intervenir moins de 15 jours calendaires
 * après la dissolution. Contrôle **bloquant**, appliqué ici ET côté serveur
 * (`LiquidationWorkflow` / `WorkflowSteps.liquidationDelaiError`).
 */
import { useCallback, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AlertCircle,
  AlertTriangle,
  CheckCircle,
  Lock,
  Megaphone,
  Paperclip,
  Sparkles,
} from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { InfoBulle } from '../../components/ui/Aide';
import { TextField } from '../../components/ui/TextField';
import { WorkflowShell } from '../../components/workflow/WorkflowShell';
import type { RoadmapStep } from '../../components/workflow/WorkflowRoadmap';
import { ticketService } from '../../services/ticket.service';
import { CancelTicketDialog } from '../tickets/CancelTicketDialog';
import { useWorkflow } from './useWorkflow';
import { useStepAutosave } from './useStepAutosave';
import { WorkflowBoot } from './WorkflowBoot';
import { AideEtape } from './AideEtape';
import { ETAPES_LIQUIDATION, etapesRoadmap } from './aidesParcours';
import { bornesDateSeance, useDossierParties, useEtatReinitialise, useSuccursalesActives } from './parcoursHooks';
import { DossierAutocomplete } from '../../components/workflow/DossierAutocomplete';
import { SeanceOperationForm } from '../../components/workflow/SeanceOperationForm';
import { ConvocationPanel, FeuillePresencePanel } from '../../components/workflow/SeanceDocPanels';
import { IncidentSeancePanel } from '../../components/workflow/IncidentSeancePanel';
import { LiquidationAnnoncePanel, type AvisCloture } from '../../components/workflow/LiquidationAnnoncePanel';
import { LiquidateurPicker, type LiquidateurState } from '../../components/workflow/LiquidateurPicker';
import { buildLiquidateurOptions, emptyLiquidateur } from '../../components/workflow/liquidateurOptions';
import { champsPersonneMorale, estPersonneMorale, identiteLiquidateur } from '../../lib/liquidateur';
import { champsMandatLiquidateur, type MentionsPv } from '../../lib/liquidationPv';
import { RapportLiquidateurPanel } from '../../components/workflow/RapportLiquidateurPanel';
import { rapportPayload, type RapportLiquidateur } from '../../lib/rapportLiquidateur';
import {
  PiecesJointesPanel,
  type PieceJointeEntry,
} from '../../components/workflow/PiecesJointesPanel';
import {
  CONVOCATION_DELAI_JOURS,
  addDaysIso,
  convocationDelaiError,
} from '../../components/workflow/convocationDelai';
import {
  LIQUIDATION_DELAI_JOURS,
  joursCalendairesEntre,
  liquidationDelaiError,
} from '../../components/workflow/liquidationDelai';
import { buildDocFilename, formeLabel } from '../../components/workflow/workflowFilename';
import type { DossierLiquidateur } from '../../services/workflow.service';
import type { TemplateInfo } from '../../services/workflowDocumentService';
import type { AssocieInput, GerantInput } from '../../components/workflow/SeanceForm';
import type { DossierBrief } from '../../types/dataroom';
import { ErreurParcours, SqueletteParcours } from '../../components/workflow/EtatsParcours';
import { BandeauFinParcours } from '../../components/workflow/BandeauFinParcours';

const STEPS: RoadmapStep[] = etapesRoadmap(ETAPES_LIQUIDATION);

/**
 * RG-LI02 : la liquidation ne s'applique qu'aux sociétés DISSOUTES. (Le backend tolère
 * encore EN_LIQUIDATION pour ne pas casser les workflows déjà engagés.)
 */
const ALLOWED_STATUTS = ['DISSOUTE'];

/** Libellé « type de document » pour le nommage `type - dénom - forme (- v<n>)`. */
function liquidationDocType(code: string): string {
  if (code.startsWith('ANNONCE_LEGALE_LIQUIDATION')) return 'Annonce légale — Clôture de liquidation';
  if (code.startsWith('RAPPORT_LIQUIDATION')) return 'Rapport de liquidation';
  if (code.startsWith('PV_DISSOLUTION_LIQUIDATION')) return 'PV — Clôture de liquidation';
  if (code.startsWith('CONVOCATION')) return 'Convocation';
  if (code.startsWith('FEUILLE_PRESENCE')) return 'Feuille de présence';
  if (code.includes('DEFAUT_QUORUM')) return 'PV — Défaut de quorum';
  if (code.includes('IRREGULARITE')) return 'PV — Irrégularité de convocation';
  return code;
}

/**
 * Ordre du jour de l'assemblée de CLÔTURE de liquidation.
 *
 * Il était vide : le panneau de convocation ne recevait aucun `initialOrdreDuJour`,
 * et l'employé se retrouvait devant une liste à composer alors que l'ordre du jour
 * d'une clôture de liquidation est fixé par la loi et toujours le même. Il reste
 * éditable — c'est une amorce, pas un verrou.
 */
const ORDRE_DU_JOUR_CLOTURE_LIQUIDATION = [
  'Rapport du liquidateur sur les opérations de liquidation',
  'Approbation des comptes définitifs de liquidation',
  'Quitus au liquidateur et décharge de son mandat',
  'Constatation de la clôture de la liquidation',
  'Pouvoirs en vue des formalités de radiation au Registre du Commerce',
];

/** Convertit le liquidateur BD en état du sélecteur (cas de secours uniquement). */
function toLiquidateurState(bd: DossierLiquidateur): LiquidateurState {
  return {
    ...emptyLiquidateur(),
    source: bd.source === 'EXTERNE' ? 'EXTERNE' : 'BD',
    civilite: String(bd.civilite ?? 'M.'),
    prenom: String(bd.prenom ?? ''),
    nom: String(bd.nom ?? ''),
    cin: String(bd.cin ?? ''),
    adresse: String(bd.adresse ?? ''),
    remuneration: String(bd.remuneration ?? ''), // L4a (E2) : jamais supposee
    ...mandatBd(bd as Record<string, unknown>),
    ...personneMoraleBd(bd as Record<string, unknown>),
  };
}

/**
 * Lot L4d (E11d) : donnees du liquidateur enregistrees a la dissolution et lues par le PV
 * de cloture du corpus (piece, nationalite, remuneration, siege de la liquidation). La
 * valeur proposee avant L4d ("exercees a titre gratuit") vaut "non remunere".
 */
function mandatBd(bd: Record<string, unknown>): Partial<LiquidateurState> {
  const t = (k: string) => (bd[k] == null ? '' : String(bd[k]));
  const ouiNon = (k: string): '' | 'oui' | 'non' => (t(k) === 'oui' || t(k) === 'non' ? (t(k) as 'oui' | 'non') : '');
  let remunerationApplicable = ouiNon('remunerationApplicable');
  if (!remunerationApplicable && t('remuneration').toLowerCase().includes('gratuit')) remunerationApplicable = 'non';
  return {
    pieceType: t('pieceType') || undefined,
    nationalite: t('nationalite'),
    dateNaissance: t('dateNaissance'),
    ancienGerant: ouiNon('ancienGerant'),
    remunerationApplicable,
    remunerationMontant: t('remunerationMontant'),
    remunerationModalites: t('remunerationModalites'),
    siegeIdentique: t('siegeIdentique'),
  };
}

/** Lot L4d (E11d) : mandat fixe a la dissolution, reconduit tel quel quand le liquidateur est renvoye. */
const CLES_MANDAT_BD = ['mandatDateEffet', 'dureeMandat', 'redditionPeriodicite', 'pouvoirsComplementaires',
  'pouvoirsLimitation', 'siegeIdentique', 'siege'];
function mandatConserve(bd: Record<string, unknown> | null | undefined): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const k of CLES_MANDAT_BD) if (bd && bd[k] != null && bd[k] !== '') out[k] = bd[k];
  return out;
}

/** Lot L4d (E5) : liquidateur personne morale enregistre a la dissolution. */
function personneMoraleBd(bd: Record<string, unknown>): Partial<LiquidateurState> {
  if (String(bd.forme ?? '') !== 'morale') return {};
  const t = (k: string) => String(bd[k] ?? '');
  return {
    forme: 'morale', civilite: '', denomination: t('denomination'), formeJuridique: t('formeJuridique'),
    capital: t('capital'), siegeSocial: t('siegeSocial'), rcVille: t('rcVille'), rcNumero: t('rcNumero'),
    ice: t('ice'), representantNom: t('representantNom'), representantQualite: t('representantQualite'),
  };
}

/**
 * Monte {@link LiquidationWorkflowPageBody} SOUS {@link WorkflowBoot} : les champs de chaque
 * etape s'initialisent avec `useState(stepData...)`, qui ne lit sa valeur qu'au
 * premier render. Sans ce montage differe, ce premier render a lieu AVANT la
 * reponse du serveur et tous les champs restent vides apres un rechargement
 * (F5, deconnexion/reconnexion), meme sur une etape deja validee.
 */
export function LiquidationWorkflowPage() {
  return (
    <WorkflowBoot>
      <LiquidationWorkflowPageBody />
    </WorkflowBoot>
  );
}

function LiquidationWorkflowPageBody() {
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
  const [dateCloture, setDateCloture] = useState(
    (stepData.step1?.dateClotureLiquidation as string) ?? '',
  );
  const [totalActif, setTotalActif] = useState(() => {
    const cf = stepData.step1?.comptesFinaux as { totalActif?: number } | undefined;
    return cf?.totalActif != null ? String(cf.totalActif) : '';
  });
  const [totalPassif, setTotalPassif] = useState(() => {
    const cf = stepData.step1?.comptesFinaux as { totalPassif?: number } | undefined;
    return cf?.totalPassif != null ? String(cf.totalPassif) : '';
  });
  // Erreurs revelees a l'arrivee sur l'etape 1 si une saisie existe deja ; remises a zero a chaque changement d'etape.
  const [revealErrors, setRevealErrors] = useEtatReinitialise(viewStep, () => viewStep === 1 && !!dateCloture);
  const [convocationDate, setConvocationDate] = useState(
    ((stepData.step1?.convocation as { date?: string } | undefined) ?? {}).date ?? '',
  );
  const [convocationHeure, setConvocationHeure] = useState(
    ((stepData.step1?.convocation as { heure?: string } | undefined) ?? {}).heure ?? '',
  );
  const [convocationSnapshot, setConvocationSnapshot] = useState<Record<string, unknown> | null>(
    (stepData.step1?.convocationSnapshot as Record<string, unknown>) ?? null,
  );
  // Cas de secours (dossier dissous avant la persistance du liquidateur / de la date).
  const [liquidateurSecours, setLiquidateurSecours] = useState<LiquidateurState>(
    () => (stepData.step1?.liquidateur as LiquidateurState | undefined) ?? emptyLiquidateur(),
  );
  const [dateDissolutionSecours, setDateDissolutionSecours] = useState(
    (stepData.step1?.dateDissolution as string) ?? '',
  );

  // ---------- Etape 2 : generation ----------
  const [docsValidated, setDocsValidated] = useState<boolean>(
    !!(stepData.step2?.pvValide as boolean) && !!(stepData.step2?.rapportValide as boolean),
  );
  const [annonceValidated, setAnnonceValidated] = useState<boolean>(
    !!(stepData.step2?.annonceValide as boolean),
  );
  // L4b (E7, D5) : donnees de l'avis de cloture du corpus (brouillon de l'etape 2).
  const [avisCloture, setAvisCloture] = useState<AvisCloture>(
    () => (stepData.step2?.avisCloture as AvisCloture | undefined) ?? {},
  );
  const [depotLegalNumero, setDepotLegalNumero] = useState(
    ((stepData.step2?.depotLegal as { numero?: string } | undefined) ?? {}).numero ?? '',
  );
  // L4d (E11d) : compte definitif et decisions du PV de cloture (brouillon de l'etape 2).
  const [mentionsPv, setMentionsPv] = useState<MentionsPv>(
    () => (stepData.step2?.mentionsPv as MentionsPv | undefined) ?? {},
  );
  const [depotLegalDate, setDepotLegalDate] = useState(
    ((stepData.step2?.depotLegal as { date?: string } | undefined) ?? {}).date ?? '',
  );
  // L5 (E4) : operations de liquidation du rapport final du liquidateur (brouillon de l'etape 2).
  const [rapportLiquidateur, setRapportLiquidateur] = useState<RapportLiquidateur>(
    () => (stepData.step2?.rapportLiquidateur as RapportLiquidateur | undefined) ?? {},
  );
  const changerRapport = useCallback(
    (patch: RapportLiquidateur) => setRapportLiquidateur((r) => ({ ...r, ...patch })),
    [],
  );

  // ---------- Etape 3 : pieces jointes ----------
  const [piecesJointes, setPiecesJointes] = useState<PieceJointeEntry[]>(
    () => (stepData.step3?.piecesJointes as PieceJointeEntry[] | undefined) ?? [],
  );

  // Parties prenantes + faits de dissolution (liquidateur, siège, date) DÈS la sélection
  // de la société : c'est la source unique — aucune de ces données n'est re-saisie.
  // Best-effort : un echec de lecture laisse le cas de secours possible.
  const { dossierParties, partiesLoading } = useDossierParties(dossierId);

  // Succursales encore ouvertes : la finalisation les REFUSE (une succursale n'a pas de
  // personnalite juridique distincte, elle ne survit pas a la radiation de sa mere). On
  // les affiche des l'etape 1 — l'employe doit le savoir AVANT de generer tous les actes.
  // Lecture LIVE a chaque affichage de l'etape : une succursale fermee entre-temps
  // disparait de l'avertissement sans re-valider l'etape 1.
  // Best-effort : la garde du serveur reste la source de verite.
  const { succursales: succursalesOuvertes } = useSuccursalesActives(dossierId, viewStep);

  const formeJuridique = useMemo(
    () =>
      (dossier?.formeJuridique as string | null | undefined) ??
      dossierParties?.formeJuridique ??
      (stepData.step1?.formeJuridique as string | undefined) ??
      null,
    [dossier, dossierParties, stepData.step1?.formeJuridique],
  );
  const isAU = formeJuridique === 'SARL_AU';

  // ---- Données reprises de la BD (lecture seule) ----
  const liquidateurBd = dossierParties?.liquidateur ?? null;
  const hasLiquidateurBd = !!(liquidateurBd?.nom || (liquidateurBd as Record<string, unknown> | null)?.denomination);
  const dateDissolutionBd =
    dossierParties?.dateDissolution ?? dossier?.dateDissolution ?? null;
  const hasDateDissolutionBd = !!dateDissolutionBd;

  /** Date de dissolution effective : la BD d'abord, la saisie de secours sinon. */
  const dateDissolution = hasDateDissolutionBd
    ? String(dateDissolutionBd)
    : dateDissolutionSecours;

  /**
   * Liquidateur effectif — PRÉ-REMPLI depuis la BD, mais CORRIGEABLE (2026-08-14).
   *
   * Auparavant, dès qu'un liquidateur existait en base il était affiché en lecture
   * seule sur une seule ligne « civilité prénom nom » : le **N° CIN n'apparaissait
   * nulle part** et rien ne permettait de le renseigner. Or la Data Room le porte
   * rarement pour les gérants, et il est publié dans l'annonce.
   *
   * L'anti-duplication est préservée : la BD alimente les champs (aucune re-saisie),
   * elle ne les verrouille plus. Une correction est renvoyée au serveur, qui écrase
   * le bloc liquidateur de la fiche — l'information manquante est donc complétée
   * une fois pour toutes.
   */
  const liquidateur: LiquidateurState = liquidateurSecours;
  /** Le liquidateur BD a-t-il déjà servi à amorcer les champs ? (une seule fois) */
  const [liquidateurAmorce, setLiquidateurAmorce] = useState(false);
  if (!liquidateurAmorce && hasLiquidateurBd && liquidateurBd) {
    // Ajustement pendant le rendu (et non plus dans un effet). Une saisie déjà
    // persistée dans le brouillon prime sur la BD : sinon on effacerait la
    // correction faite au passage précédent.
    setLiquidateurAmorce(true);
    if (!stepData.step1?.liquidateur) setLiquidateurSecours(toLiquidateurState(liquidateurBd));
  }
  const siegeLiquidation = dossierParties?.siegeLiquidation ?? '';

  const liquidateurOptions = useMemo(
    () => buildLiquidateurOptions(dossierParties),
    [dossierParties],
  );

  // Associés / gérants BD → pré-remplissage des séances (convocation, feuille de
  // présence) : aucune re-saisie d'identité.
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

  // ---- Boni / mali CALCULÉ (jamais saisi séparément) ----
  const boniMali = useMemo(() => {
    if (!totalActif.trim() || !totalPassif.trim()) return null;
    const a = Number.parseFloat(totalActif);
    const p = Number.parseFloat(totalPassif);
    if (Number.isNaN(a) || Number.isNaN(p)) return null;
    return a - p;
  }, [totalActif, totalPassif]);
  const resultatType: 'boni' | 'mali' | null =
    boniMali === null ? null : boniMali < 0 ? 'mali' : 'boni';

  // ---- RG-LI03 : règle DURE des 15 jours (miroir exact du backend) ----
  const delaiError = useMemo(
    () => liquidationDelaiError(dateDissolution, dateCloture),
    [dateDissolution, dateCloture],
  );
  const joursDepuisDissolution = useMemo(
    () => joursCalendairesEntre(dateDissolution, dateCloture),
    [dateDissolution, dateCloture],
  );

  // ---- Règle des 16 jours de convocation (miroir exact du backend) ----
  const convocationError = useMemo(
    () => convocationDelaiError(convocationDate, dateCloture),
    [convocationDate, dateCloture],
  );

  const [{ maxDate }] = useState(bornesDateSeance);
  const [todayStr] = useState(() => new Date().toISOString().slice(0, 10));

  type FieldErrors = {
    dossierId?: string;
    dateCloture?: string;
    dateDissolution?: string;
    liquidateur?: string;
    comptes?: string;
    convocation?: string;
  };
  const fieldErrors = useMemo<FieldErrors>(() => {
    const errs: FieldErrors = {};
    if (!dossierId.trim()) errs.dossierId = 'Sélectionnez la société dissoute à liquider.';
    if (!dateDissolution) {
      errs.dateDissolution =
        'La date de dissolution est introuvable dans le dossier : renseignez-la (dossier antérieur à '
        + 'l’enregistrement automatique).';
    }
    if (!dateCloture) {
      errs.dateCloture = `La date ${isAU ? 'de la décision' : 'de l’AGE'} de clôture est obligatoire.`;
    } else if (delaiError) {
      errs.dateCloture = delaiError;
    }
    if (!identiteLiquidateur(liquidateur)) {
      errs.liquidateur =
        'Aucun liquidateur n’est enregistré pour cette société : désignez-le (il sera ensuite '
        + 'repris automatiquement).';
    }
    if (boniMali === null) {
      errs.comptes = 'Le total de l’actif et le total du passif (MAD) sont obligatoires.';
    } else if (Number.parseFloat(totalActif) < 0 || Number.parseFloat(totalPassif) < 0) {
      errs.comptes = 'Les totaux d’actif et de passif doivent être positifs ou nuls.';
    }
    if (convocationError) errs.convocation = convocationError;
    return errs;
  }, [
    dossierId, dateDissolution, dateCloture, delaiError, isAU,
    liquidateur, boniMali, totalActif, totalPassif, convocationError,
  ]);

  const targetDenomination =
    dossier?.raisonSociale ?? dossierParties?.denomination ?? ticket?.titre ?? '';

  /** Nommage transverse : `type - dénomination - forme (- v<n>)`. */
  const workflowFilenameFor = useCallback(
    (tpl: TemplateInfo, opts?: { version?: number }) =>
      buildDocFilename(
        liquidationDocType(tpl.code),
        targetDenomination,
        formeLabel(formeJuridique),
        opts?.version,
      ),
    [targetDenomination, formeJuridique],
  );

  const depositMotif = useMemo(
    () => `Liquidation : clôture au ${dateCloture || '—'}`,
    [dateCloture],
  );

  const convocationPayload = useCallback(() => {
    if (!convocationDate) return undefined;
    return { date: convocationDate, heure: convocationHeure || undefined };
  }, [convocationDate, convocationHeure]);

  // Bloc « opération » consommé par le PV et le rapport : le liquidateur vient de la BD,
  // les comptes de l'étape 1 — le sous-formulaire ne redemande donc RIEN.
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
        siegeIdentique: liquidateur.siegeIdentique,
        siege: siegeLiquidation || undefined,
      },
      cloture: {
        // L4d (E11d) : avis de cloture (quitus, dates) repris par le PV ; plus de "boni" suppose.
        ...avisCloture,
        resultatSens: resultatType ?? undefined,
        resultatMontant: boniMali === null ? null : Math.abs(boniMali),
        dateClotureLiquidation: dateCloture || null,
        actifRealise: totalActif || null,
        passifRegle: totalPassif || null,
      },
      dissolutionDate: dateDissolution,
      // L5 (E4) : operations de liquidation saisies pour le rapport du liquidateur.
      rapportLiquidateur: rapportPayload(rapportLiquidateur),
    }),
    [liquidateur, siegeLiquidation, resultatType, boniMali, dateCloture,
      totalActif, totalPassif, dateDissolution, avisCloture, rapportLiquidateur],
  );

  /** Contenu courant de l'étape — voir la note dans DissolutionWorkflowPage. */
  const draftPayloadFor = useCallback(
    (n: number): Record<string, unknown> | null => {
      if (n === 1) {
        return {
          dossierId,
          formeJuridique,
          dateClotureLiquidation: dateCloture,
          dateDissolution,
          liquidateur,
          comptesFinaux: {
            totalActif: totalActif ? Number.parseFloat(totalActif) : null,
            totalPassif: totalPassif ? Number.parseFloat(totalPassif) : null,
          },
          convocation: convocationPayload(),
          convocationSnapshot,
        };
      }
      if (n === 2) {
        return {
          pvValide: docsValidated,
          rapportValide: docsValidated,
          annonceValide: annonceValidated,
          depotLegal: { numero: depotLegalNumero, date: depotLegalDate },
          avisCloture,
          mentionsPv,
          rapportLiquidateur,
        };
      }
      if (n === 3) return { piecesJointes };
      return null;
    },
    [dossierId, formeJuridique, dateCloture, dateDissolution, liquidateur, totalActif, avisCloture, mentionsPv,
      rapportLiquidateur, totalPassif, convocationPayload, convocationSnapshot, docsValidated,
      annonceValidated, depotLegalNumero, depotLegalDate, piecesJointes],
  );

  // Appele avant les retours anticipes : l'ordre des hooks ne varie jamais.
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
        dateClotureLiquidation: dateCloture,
        formeJuridique: formeJuridique ?? undefined,
        // Date de dissolution : envoyee UNIQUEMENT en secours (la BD gagne cote serveur).
        dateDissolution: hasDateDissolutionBd ? undefined : dateDissolution,
        // Liquidateur : TOUJOURS envoye (2026-08-14). Il n'etait transmis qu'en
        // secours, la BD faisant foi — mais alors une correction saisie ici (le
        // N° CIN absent de la Data Room, typiquement) n'atteignait jamais le
        // serveur et se perdait a chaque passage. Les champs etant desormais
        // pre-remplis depuis la BD, renvoyer le bloc ne re-saisit rien : il
        // reconduit la valeur BD a l'identique, ou la complete.
        liquidateur: {
          source: liquidateur.source,
          civilite: liquidateur.civilite,
          prenom: liquidateur.prenom,
          nom: liquidateur.nom,
          cin: liquidateur.cin || undefined,
          adresse: liquidateur.adresse,
          remuneration: liquidateur.remuneration,
          ...mandatConserve(liquidateurBd as Record<string, unknown> | null), // L4d (E11d)
          ...champsPersonneMorale(liquidateur), // L4d (E5)
          ...champsMandatLiquidateur(liquidateur), // L4d (E11d)
        },
        comptesFinaux: {
          totalActif: Number.parseFloat(totalActif),
          totalPassif: Number.parseFloat(totalPassif),
          devise: 'MAD',
        },
        convocation: convocationPayload(),
      });
    } else if (step === 2) {
      if (!docsValidated) {
        setError('Générez puis validez le procès-verbal de clôture et le rapport de liquidation avant de continuer.');
        return;
      }
      if (!annonceValidated) {
        setError(
          'Générez puis validez l’annonce légale de clôture : sa publication dans un journal '
            + 'd’annonces légales fonde la radiation au registre du commerce (le numéro et la '
            + 'date du dépôt légal restent facultatifs à ce stade).',
        );
        return;
      }
      await executeStep(2, {
        pvValide: true,
        rapportValide: true,
        annonceValide: true,
        depotLegal:
          depotLegalNumero || depotLegalDate
            ? { numero: depotLegalNumero || null, date: depotLegalDate || null }
            : undefined,
        avisCloture,
        mentionsPv,
        rapportLiquidateur,
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

  const showErr = (field: keyof FieldErrors): string | null =>
    revealErrors ? fieldErrors[field] ?? null : null;

  const decisionLabel = isAU ? 'Décision de l’associé unique' : 'Assemblée générale extraordinaire (AGE)';
  const liquidateurLabel = identiteLiquidateur(liquidateur) || '—';

  // Identite complete de la societe : sans elle, les formulaires de seance
  // laissaient vides le lieu de l'assemblee et le lieu de signature (2026-08-14).
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
        footer={{
          validateLabel: step === 4 ? 'Finaliser' : 'Valider l’étape',
          // La cloture est REFUSEE par le backend tant qu'une succursale reste ouverte :
          // on desactive le bouton plutot que de laisser l'employe buter sur une erreur.
          validateDisabled: step === 4 && succursalesOuvertes.length > 0,
        }}
      >
        <AideEtape
          parcours="liquidation"
          etapes={ETAPES_LIQUIDATION}
          etape={step}
          manquants={step === 1 ? (Object.values(fieldErrors).filter(Boolean) as string[]) : undefined}
        />

        {step === 1 && (
          <div className="space-y-5" data-testid="liquidation-step1">
            <header>
              <h2 className="text-lg font-semibold text-fg">Étape 1 — Saisie</h2>
              <p className="text-sm text-fg-subtle">
                Sélectionnez la société <strong>dissoute</strong> à liquider, puis renseignez la
                date de l’assemblée de clôture et les comptes finaux. L’identité de la société,
                la <strong>date de dissolution</strong> et le <strong>liquidateur</strong> (nommé
                lors de la dissolution) sont lus dans le dossier : vous n’avez{' '}
                <strong>jamais à les ressaisir</strong>.
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
                label="Société à liquider"
                help="Seules les sociétés dissoutes peuvent être liquidées. Si la société n’apparaît pas, menez d’abord le parcours « Dissolution »."
                showDissolutionDelay
              />
              {showErr('dossierId') && (
                <p className="mt-1 flex items-start gap-1 text-xs text-danger" role="alert">
                  <AlertCircle className="mt-0.5 h-3 w-3 flex-shrink-0" />
                  {showErr('dossierId')}
                </p>
              )}
            </div>

            {dossierId && (
              <div className="rounded-lg border border-info/30 bg-info-soft p-3 text-xs text-fg-muted" role="status">
                Organe compétent : <strong>{decisionLabel}</strong>. Actes produits : procès-verbal
                de clôture de liquidation{isAU ? ' (SARL à associé unique)' : ' (SARL)'}, rapport du
                liquidateur et annonce légale de clôture.
              </div>
            )}

            {/* Une succursale n'a pas de personnalite juridique distincte : elle ne peut
                pas survivre a la radiation de sa societe. La finalisation est REFUSEE
                tant qu'il en reste une ouverte — autant l'annoncer des l'etape 1. */}
            {dossierId && succursalesOuvertes.length > 0 && (
              <div
                className="flex items-start gap-2 rounded-lg border border-warning/40 bg-warning-soft p-3 text-xs text-fg-muted"
                role="alert"
                data-testid="liquidation-succursales-ouvertes"
              >
                <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0 text-warning" />
                <span>
                  Cette société exploite encore{' '}
                  <strong>
                    {succursalesOuvertes.length} succursale
                    {succursalesOuvertes.length > 1 ? 's' : ''}
                  </strong>{' '}
                  :{' '}
                  {succursalesOuvertes
                    .map((s) => `${s.denomination ?? 'Succursale'}${s.ville ? ` (${s.ville})` : ''}`)
                    .join(', ')}
                  . La clôture de la liquidation sera <strong>refusée</strong> tant qu’elles ne
                  sont pas fermées (parcours «&nbsp;Fermeture de succursale&nbsp;») : chacune doit
                  être radiée au greffe de son lieu d’exploitation.
                </span>
              </div>
            )}

            {/* ---- Repris de la BD : lecture seule (zéro re-saisie) ---- */}
            {dossierId && (
              <section
                className="space-y-3 rounded-xl border border-border bg-bg-raised p-4"
                data-testid="liquidation-faits-bd"
              >
                <div className="flex items-center gap-2">
                  <Lock className="h-4 w-4 text-fg-muted" />
                  <h4 className="text-sm font-semibold text-fg">Repris du dossier (lecture seule)</h4>
                </div>
                {partiesLoading && (
                  <p className="text-[11px] text-fg-subtle">Chargement des données du dossier…</p>
                )}

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
                    <p className="text-xs text-fg-subtle">Date de dissolution</p>
                    <p className="text-sm font-medium text-fg" data-testid="liquidation-date-dissolution-bd">
                      {hasDateDissolutionBd ? dateDissolution : '— non enregistrée —'}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-fg-subtle">Liquidateur</p>
                    <p className="text-sm font-medium text-fg" data-testid="liquidation-liquidateur-bd">
                      {hasLiquidateurBd ? liquidateurLabel : '— non enregistré —'}
                    </p>
                  </div>
                  {/* Le CIN etait absent de ce recapitulatif : il est publie dans
                      l'annonce, son absence devait donc se voir. */}
                  <div>
                    <p className="text-xs text-fg-subtle">
                      {estPersonneMorale(liquidateur) ? 'RC du liquidateur' : 'N° CIN du liquidateur'}
                    </p>
                    <p className="text-sm font-medium text-fg" data-testid="liquidation-liquidateur-cin-bd">
                      {(estPersonneMorale(liquidateur)
                        ? [liquidateur.rcNumero, liquidateur.rcVille].filter(Boolean).join(' — ')
                        : liquidateur.cin) || '— à renseigner ci-dessous —'}
                    </p>
                  </div>
                  {siegeLiquidation && (
                    <div className="sm:col-span-2">
                      <p className="text-xs text-fg-subtle">Siège de la liquidation</p>
                      <p className="text-sm font-medium text-fg">{siegeLiquidation}</p>
                    </div>
                  )}
                </div>

                {hasLiquidateurBd && hasDateDissolutionBd && (
                  <p className="text-[11px] text-fg-subtle">
                    Ces données proviennent du parcours « Dissolution » : elles ne sont pas
                    modifiables ici.
                  </p>
                )}
              </section>
            )}

            {/* ---- Cas de secours : date de dissolution absente en base ---- */}
            {dossierId && !partiesLoading && !hasDateDissolutionBd && (
              <div data-testid="liquidation-secours-date">
                <TextField
                  label="Date de dissolution *"
                  type="date"
                  value={dateDissolutionSecours}
                  max={todayStr}
                  onChange={(e) => setDateDissolutionSecours(e.target.value)}
                  onBlur={() => setRevealErrors(true)}
                  error={showErr('dateDissolution') ?? undefined}
                  hint="Aucune date de dissolution dans le dossier (dossier antérieur à l’enregistrement automatique) : saisissez-la une seule fois."
                />
              </div>
            )}

            {/* ---- Liquidateur : pre-rempli depuis la BD, toujours corrigeable ---- */}
            {dossierId && !partiesLoading && (
              <div className="space-y-2" data-testid="liquidation-secours-liquidateur">
                {!hasLiquidateurBd && (
                  <div
                    role="status"
                    className="flex items-start gap-2 rounded-lg border-l-4 border-warning/40 bg-warning-soft p-3 text-xs text-warning"
                  >
                    <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0" />
                    <span>
                      Aucun liquidateur n’est enregistré pour cette société (dossier dissous avant
                      l’enregistrement automatique). Désignez-le <strong>une seule fois</strong> :
                      il sera enregistré au dossier et repris ensuite sans ressaisie.
                    </span>
                  </div>
                )}
                {hasLiquidateurBd && (
                  <div
                    role="status"
                    className="flex items-start gap-2 rounded-lg border-l-4 border-info bg-info-soft p-3 text-xs text-fg-muted"
                  >
                    <Lock className="mt-0.5 h-4 w-4 flex-shrink-0 text-fg-muted" />
                    <span>
                      Liquidateur <strong>repris de la dissolution</strong> : les champs sont
                      déjà remplis, rien n’est à ressaisir. Complétez seulement ce qui manque —
                      typiquement le <strong>n° de CIN</strong>, rarement présent dans la Data Room et
                      pourtant publié dans l’annonce. Toute correction est enregistrée au dossier.
                    </span>
                  </div>
                )}
                <LiquidateurPicker
                  value={liquidateurSecours}
                  onChange={setLiquidateurSecours}
                  options={liquidateurOptions}
                  loading={partiesLoading}
                  dossierId={dossierId}
                  error={showErr('liquidateur')}
                />
              </div>
            )}

            {/* ---- Date de l'AGE de clôture + règle des 15 jours ---- */}
            <TextField
              label={`Date ${isAU ? 'de la décision' : 'de l’AGE'} de clôture *`}
              type="date"
              value={dateCloture}
              min={dateDissolution || undefined}
              max={maxDate}
              data-testid="liquidation-date-cloture"
              onChange={(e) => setDateCloture(e.target.value)}
              onBlur={() => setRevealErrors(true)}
              error={showErr('dateCloture') ?? undefined}
              hint={
                isAU
                  ? 'La clôture relève de la décision de l’associé unique.'
                  : 'La clôture de la liquidation se décide toujours en assemblée générale extraordinaire.'
              }
            />

            {delaiError ? (
              <div
                role="alert"
                data-testid="liquidation-delai-15j-error"
                className="flex items-start gap-2 rounded-lg border border-danger/40 bg-danger-soft p-3 text-xs text-danger"
              >
                <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0" />
                <div>
                  <p className="font-semibold">Délai légal non respecté</p>
                  <p>{delaiError}</p>
                </div>
              </div>
            ) : (
              joursDepuisDissolution !== null && (
                <div
                  role="status"
                  data-testid="liquidation-delai-15j-ok"
                  className="flex items-center gap-2 rounded-lg border-l-4 border-success/40 bg-success-soft p-3 text-xs text-success"
                >
                  <CheckCircle className="h-4 w-4" />
                  <span>
                    Délai légal respecté : {joursDepuisDissolution} jour
                    {joursDepuisDissolution > 1 ? 's' : ''} entre la dissolution et la clôture
                    (minimum {LIQUIDATION_DELAI_JOURS} jours).
                  </span>
                </div>
              )
            )}

            {/* ---- Comptes finaux → boni / mali CALCULÉ ---- */}
            <section className="rounded-xl border border-border bg-bg-raised p-4" data-testid="liquidation-comptes">
              <h4 className="mb-3 flex items-center gap-1 text-sm font-semibold text-fg">
                Comptes finaux de liquidation
                <InfoBulle
                  libelle="Comment remplir les comptes finaux ?"
                  texte="Indiquez le total de l’actif réalisé et le total du passif réglé, en dirhams. L’écart donne le boni (actif supérieur au passif) ou le mali de liquidation, calculé automatiquement et repris dans le procès-verbal, le rapport et l’annonce."
                />
              </h4>
              <div className="grid gap-3 md:grid-cols-3">
                <TextField
                  label="Total actif (MAD) *"
                  type="number"
                  min={0}
                  step={0.01}
                  value={totalActif}
                  data-testid="liquidation-total-actif"
                  onChange={(e) => setTotalActif(e.target.value)}
                  onBlur={() => setRevealErrors(true)}
                />
                <TextField
                  label="Total passif (MAD) *"
                  type="number"
                  min={0}
                  step={0.01}
                  value={totalPassif}
                  data-testid="liquidation-total-passif"
                  onChange={(e) => setTotalPassif(e.target.value)}
                  onBlur={() => setRevealErrors(true)}
                />
                <div className="rounded-lg border border-border bg-bg-overlay p-3">
                  <p className="text-xs text-fg-subtle">Boni / mali de liquidation</p>
                  <p
                    className={`mt-1 text-lg font-bold ${
                      boniMali === null
                        ? 'text-fg-subtle'
                        : resultatType === 'boni'
                          ? 'text-success'
                          : 'text-danger'
                    }`}
                    data-testid="liquidation-boni-mali"
                  >
                    {boniMali === null
                      ? '—'
                      : `${Math.abs(boniMali).toLocaleString('fr-FR')} MAD (${resultatType})`}
                  </p>
                </div>
              </div>
              {showErr('comptes') && (
                <p className="mt-2 flex items-start gap-1 text-xs text-danger" role="alert">
                  <AlertCircle className="mt-0.5 h-3 w-3 flex-shrink-0" />
                  {showErr('comptes')}
                </p>
              )}
              <p className="mt-2 text-[11px] text-fg-subtle">
                Le sens (boni ou mali) et son montant sont <strong>calculés</strong> à partir de ces
                deux totaux : ils alimentent directement le procès-verbal, le rapport et l’annonce légale.
              </p>
            </section>

            {/* ---- Convocation OPTIONNELLE — règle des 16 jours ---- */}
            <section className="space-y-3 rounded-xl border border-border bg-bg-raised p-4" data-testid="liquidation-convocation">
              <div className="flex items-center gap-2">
                <Megaphone className="h-4 w-4 text-fg-muted" />
                <h4 className="text-sm font-semibold text-fg">Convocation (facultative)</h4>
              </div>
              <div className="grid gap-3 md:grid-cols-2">
                <TextField
                  label="Date de convocation"
                  type="date"
                  value={convocationDate}
                  data-testid="liquidation-convocation-date"
                  onChange={(e) => {
                    const v = e.target.value;
                    setConvocationDate(v);
                    // L'assemblee se tient au minimum 16 jours apres la convocation.
                    if (v && !dateCloture) setDateCloture(addDaysIso(v, CONVOCATION_DELAI_JOURS));
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
                Règle des <strong>{CONVOCATION_DELAI_JOURS} jours</strong> : l’assemblée ne peut
                se tenir moins de {CONVOCATION_DELAI_JOURS} jours calendaires après la
                convocation. Laissez vide si aucune convocation n’est émise. Les associés à
                convoquer sont repris du dossier.
              </p>
              {convocationError && (
                <div
                  role="alert"
                  data-testid="liquidation-convocation-16j-error"
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
                  date: dateCloture,
                  convDate: convocationDate,
                  convHeure: convocationHeure,
                }}
                initialAssocies={bdAssocies}
                initialGerants={bdGerants}
                initialOrdreDuJour={ORDRE_DU_JOUR_CLOTURE_LIQUIDATION}
                initialSnapshot={convocationSnapshot}
                onSnapshot={setConvocationSnapshot}
                lockType
                hideSeanceDate
                hideConvDate
              contexteSeance={`Liquidation${dateCloture ? ` du ${dateCloture}` : ''}`}
                embedded
                versioned
                motif={depositMotif}
                filenameFor={workflowFilenameFor}
              />
            </section>
          </div>
        )}

        {step === 2 && (
          <div className="space-y-4" data-testid="liquidation-step2">
            <header>
              <div className="flex items-center gap-2">
                <Sparkles className="h-5 w-5 text-info" />
                <h2 className="text-lg font-semibold text-fg">Étape 2 — Génération des actes</h2>
              </div>
              <p className="text-sm text-fg-subtle">
                Le <strong>procès-verbal de clôture</strong>, le <strong>rapport de liquidation</strong> et
                l’<strong>annonce légale de clôture</strong> sont produits à partir de l’étape 1
                et de la Data Room : aucune donnée n’est ressaisie. À la validation, chaque
                document est <strong>déposé automatiquement</strong> et versionné dans la
                Data Room juridique.
              </p>
            </header>

            <RapportLiquidateurPanel
              isAU={isAU}
              valeurs={rapportLiquidateur}
              onChange={changerRapport}
              contexte={{
                capital: dossierParties?.capitalSocial ?? null,
                remuneration: liquidateur.remunerationApplicable === 'oui' ? liquidateur.remunerationMontant ?? null : 0,
                impot: mentionsPv.isMontant ?? null,
                fraisPv: mentionsPv.frais ?? null,
                totalActif,
                totalPassif,
              }}
            />

            <SeanceOperationForm
              mode="liquidation"
              dossierId={ticket?.dossierId ?? dossierId}
              ticketId={ticket?.id}
              formeJuridique={isAU ? 'SARL_AU' : 'SARL'}
              societe={societeInput}
              defaultDate={dateCloture}
              defaultConvocation={
                convocationDate ? { date: convocationDate, heure: convocationHeure || undefined } : undefined
              }
              operation={operationInput}
              initialAssocies={bdAssocies}
              initialGerants={bdGerants}
              // L4d (E6) : reference lisible du dossier (annee, numero, societe), pas celle du ticket.
              referenceDossier={dossier?.reference ?? ''}
              initialMentions={mentionsPv}
              onMentions={setMentionsPv}
              onReady={setDocsValidated}
              versioned
              depositMotif={depositMotif}
              filenameFor={workflowFilenameFor}
            />

            <LiquidationAnnoncePanel
              dossierId={ticket?.dossierId ?? dossierId}
              ticketId={ticket?.id}
              formeJuridique={isAU ? 'SARL_AU' : 'SARL'}
              denomination={targetDenomination}
              dateCloture={dateCloture}
              liquidateur={liquidateur}
              avis={avisCloture}
              onAvisChange={setAvisCloture}
              dateDissolution={dateDissolution ?? undefined}
              totalActif={totalActif}
              totalPassif={totalPassif}
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
                  date: dateCloture,
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
                dossierId={ticket?.dossierId ?? dossierId}
                ticketId={ticket?.id}
                formeJuridique={isAU ? 'SARL_AU' : 'SARL'}
                societe={societeInput}
                // Type, date d'assemblee et convocation viennent de l'etape 1 :
                // ces panneaux les recoivent, ils ne les redemandent pas.
                defaultSeance={{
                  type: 'extraordinaire',
                  date: dateCloture,
                  convDate: convocationDate,
                  convHeure: convocationHeure,
                }}
                lockType
                hideSeanceDate
                hideConvDate
                contexteSeance={`Liquidation${dateCloture ? ` du ${dateCloture}` : ''}`}
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
          <div className="space-y-4" data-testid="liquidation-step3-pieces">
            <header>
              <div className="flex items-center gap-2">
                <Paperclip className="h-5 w-5 text-fg-muted" />
                <h2 className="text-lg font-semibold text-fg">Étape 3 — Pièces jointes (facultative)</h2>
              </div>
              <p className="text-sm text-fg-subtle">
                Déposez ici les <strong>versions légalisées</strong> des documents produits (procès-verbal
                de clôture signé, rapport signé, annonce publiée, modèle J…). Cette étape est{' '}
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
          <div className="space-y-4" data-testid="liquidation-synthese">
            {succursalesOuvertes.length > 0 ? (
              <div
                className="rounded-xl border border-danger/40 bg-danger-soft p-6"
                role="alert"
                data-testid="liquidation-blocage-succursales"
              >
                <AlertTriangle className="mb-2 h-8 w-8 text-danger" />
                <h2 className="text-xl font-bold text-fg">Clôture impossible en l’état</h2>
                <p className="mt-2 text-sm text-fg-muted">
                  Cette société exploite encore{' '}
                  <strong>
                    {succursalesOuvertes.length} succursale
                    {succursalesOuvertes.length > 1 ? 's' : ''}
                  </strong>
                  . Une succursale n’a pas de personnalité juridique distincte de sa société :
                  elle ne peut pas survivre à sa radiation. Fermez-les d’abord — chacune par le
                  parcours «&nbsp;Fermeture de succursale&nbsp;», avec son procès-verbal, son annonce légale
                  et sa radiation au greffe de son lieu d’exploitation.
                </p>
                <ul className="mt-3 space-y-1 text-sm text-fg">
                  {succursalesOuvertes.map((s) => (
                    <li key={s.id}>
                      • {s.denomination ?? 'Succursale'}
                      {s.ville ? ` — ${s.ville}` : ''}
                      {s.rcSecondaire ? ` (RC ${s.rcSecondaire})` : ''}
                    </li>
                  ))}
                </ul>
              </div>
            ) : (
              <BandeauFinParcours titre="Liquidation finalisée">
                  Le procès-verbal de clôture, le rapport de liquidation et l’annonce légale ont été
                  produits, validés et déposés dans la Data Room. La société est désormais{' '}
                  <strong>liquidée</strong>.
                </BandeauFinParcours>
            )}

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
                  <p className="text-xs text-fg-subtle">Date de dissolution</p>
                  <p className="text-sm font-medium text-fg">{dateDissolution || '—'}</p>
                </div>
                <div>
                  <p className="text-xs text-fg-subtle">
                    Date {isAU ? 'de la décision' : 'de l’AGE'} de clôture
                  </p>
                  <p className="text-sm font-medium text-fg">{dateCloture || '—'}</p>
                </div>
                <div>
                  <p className="text-xs text-fg-subtle">Liquidateur</p>
                  <p className="text-sm font-medium text-fg">
                    {liquidateurLabel}{' '}
                    <span className="text-xs font-normal text-fg-subtle">
                      ({hasLiquidateurBd ? 'nommé à la dissolution' : 'désigné à la liquidation'})
                    </span>
                  </p>
                </div>
                <div>
                  <p className="text-xs text-fg-subtle">Convocation</p>
                  <p className="text-sm font-medium text-fg">
                    {convocationDate
                      ? `${convocationDate}${convocationHeure ? ` à ${convocationHeure}` : ''}`
                      : 'Aucune'}
                  </p>
                </div>
              </div>
            </div>

            <div className="rounded-xl border border-border bg-bg-raised p-4" data-testid="synthese-comptes">
              <p className="mb-3 text-sm font-semibold text-fg">Comptes finaux</p>
              <div className="grid gap-4 rounded-lg bg-bg-overlay p-3 sm:grid-cols-3">
                <div>
                  <p className="text-xs text-fg-subtle">Total actif</p>
                  <p className="text-sm font-medium text-fg">
                    {totalActif ? `${Number.parseFloat(totalActif).toLocaleString('fr-FR')} MAD` : '—'}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-fg-subtle">Total passif</p>
                  <p className="text-sm font-medium text-fg">
                    {totalPassif ? `${Number.parseFloat(totalPassif).toLocaleString('fr-FR')} MAD` : '—'}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-fg-subtle">Résultat</p>
                  <p className="text-sm font-medium text-fg">
                    {boniMali === null
                      ? '—'
                      : `${Math.abs(boniMali).toLocaleString('fr-FR')} MAD (${resultatType})`}
                  </p>
                </div>
              </div>
            </div>

            <div className="rounded-xl border border-border bg-bg-raised p-4" data-testid="synthese-documents">
              <p className="mb-3 text-sm font-semibold text-fg">Documents et dépôts</p>
              <ul className="space-y-1 rounded-lg bg-bg-overlay p-3 text-sm text-fg-muted">
                <li>
                  Procès-verbal de clôture et rapport de liquidation :{' '}
                  {docsValidated ? 'produits et validés' : 'non validés'} — déposés et versionnés
                  dans la Data Room.
                </li>
                <li>
                  Annonce légale de clôture : {annonceValidated ? 'produite et validée' : 'non validée'}
                  {depotLegalNumero || depotLegalDate
                    ? ` — dépôt légal n° ${depotLegalNumero || '—'} du ${depotLegalDate || '—'}`
                    : ' — numéro et date du dépôt légal à compléter après le dépôt au greffe'}
                  .
                </li>
                <li>
                  Pièces jointes :{' '}
                  {piecesJointes.length > 0
                    ? piecesJointes
                        .map((p) => `${p.label}${p.version ? ` (v${p.version})` : ''}`)
                        .join(', ')
                    : 'aucune (étape facultative)'}
                  .
                </li>
              </ul>
            </div>

            <div className="rounded-xl border border-border bg-bg-raised p-4 text-sm text-fg-muted">
              <p><strong>Statut de la société :</strong> liquidée</p>
              <p className="mt-1"><strong>Archivage dans la Data Room :</strong> confirmé.</p>
              <p className="mt-1">
                <strong>Prochaine étape suggérée :</strong> engager la radiation au registre du
                commerce.
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
