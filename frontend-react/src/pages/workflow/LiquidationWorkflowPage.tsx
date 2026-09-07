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
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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
import { TextField } from '../../components/ui/TextField';
import { WorkflowShell } from '../../components/workflow/WorkflowShell';
import type { RoadmapStep } from '../../components/workflow/WorkflowRoadmap';
import { ticketService } from '../../services/ticket.service';
import { CancelTicketDialog } from '../tickets/CancelTicketDialog';
import { useWorkflow } from './useWorkflow';
import { useStepAutosave } from './useStepAutosave';
import { WorkflowBoot } from './WorkflowBoot';
import { DossierAutocomplete } from '../../components/workflow/DossierAutocomplete';
import { SeanceOperationForm } from '../../components/workflow/SeanceOperationForm';
import { ConvocationPanel, FeuillePresencePanel } from '../../components/workflow/SeanceDocPanels';
import { IncidentSeancePanel } from '../../components/workflow/IncidentSeancePanel';
import { LiquidationAnnoncePanel } from '../../components/workflow/LiquidationAnnoncePanel';
import {
  LiquidateurPicker,
  buildLiquidateurOptions,
  emptyLiquidateur,
  type LiquidateurState,
} from '../../components/workflow/LiquidateurPicker';
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
import {
  workflowService,
  type DossierLiquidateur,
  type DossierParties,
  type SuccursaleSummary,
} from '../../services/workflow.service';
import type { TemplateInfo } from '../../services/workflowDocumentService';
import type { AssocieInput, GerantInput } from '../../components/workflow/SeanceForm';
import type { DossierBrief } from '../../types/dataroom';

const STEPS: RoadmapStep[] = [
  { number: 1, label: 'Saisie' },
  { number: 2, label: 'Generation' },
  { number: 3, label: 'Pieces jointes' },
  { number: 4, label: 'Synthese' },
];

/**
 * RG-LI02 : la liquidation ne s'applique qu'aux sociétés DISSOUTES. (Le backend tolère
 * encore EN_LIQUIDATION pour ne pas casser les workflows déjà engagés.)
 */
const ALLOWED_STATUTS = ['DISSOUTE'];

/** Libellé « type de document » pour le nommage `type - dénom - forme (- v<n>)`. */
export function liquidationDocType(code: string): string {
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
    remuneration: String(bd.remuneration ?? 'exercées à titre gratuit'),
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
  const [dossierId, setDossierId] = useState(initialId);
  const [dossier, setDossier] = useState<DossierBrief | null>(null);
  /** Succursales encore ACTIVE : bloquent la finalisation tant qu'elles ne sont pas fermees. */
  const [succursalesOuvertes, setSuccursalesOuvertes] = useState<SuccursaleSummary[]>([]);
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
  const [revealErrors, setRevealErrors] = useState(false);
  const [convocationDate, setConvocationDate] = useState(
    ((stepData.step1?.convocation as { date?: string } | undefined) ?? {}).date ?? '',
  );
  const [convocationHeure, setConvocationHeure] = useState(
    ((stepData.step1?.convocation as { heure?: string } | undefined) ?? {}).heure ?? '',
  );
  const [convocationSnapshot, setConvocationSnapshot] = useState<Record<string, unknown> | null>(
    (stepData.step1?.convocationSnapshot as Record<string, unknown>) ?? null,
  );
  const [dossierParties, setDossierParties] = useState<DossierParties | null>(null);
  const [partiesLoading, setPartiesLoading] = useState(false);

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

  useEffect(() => {
    if (stepData.step1?.dossierId && !dossierId) {
      setDossierId(stepData.step1.dossierId as string);
    }
  }, [stepData.step1?.dossierId, dossierId]);

  useEffect(() => {
    setRevealErrors(viewStep === 1 && !!dateCloture);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [viewStep]);

  // Parties prenantes + faits de dissolution (liquidateur, siège, date) DÈS la sélection
  // de la société : c'est la source unique — aucune de ces données n'est re-saisie.
  useEffect(() => {
    if (!dossierId) {
      setDossierParties(null);
      return;
    }
    let cancelled = false;
    setPartiesLoading(true);
    workflowService
      .getDossierParties(dossierId)
      .then((parties) => {
        if (!cancelled) setDossierParties(parties);
      })
      .catch(() => {
        /* best-effort : le cas de secours reste possible */
      })
      .finally(() => {
        if (!cancelled) setPartiesLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [dossierId]);

  // Succursales encore ouvertes : la finalisation les REFUSE (une succursale n'a pas de
  // personnalite juridique distincte, elle ne survit pas a la radiation de sa mere). On
  // les affiche des l'etape 1 — l'employe doit le savoir AVANT de generer tous les actes.
  // Lecture LIVE a chaque affichage de l'etape : une succursale fermee entre-temps
  // disparait de l'avertissement sans re-valider l'etape 1.
  useEffect(() => {
    if (!dossierId) {
      setSuccursalesOuvertes([]);
      return;
    }
    let cancelled = false;
    workflowService
      .listSuccursales(dossierId)
      .then((items) => {
        if (!cancelled) setSuccursalesOuvertes(items.filter((s) => s.statut === 'ACTIVE'));
      })
      .catch(() => {
        /* best-effort : la garde backend reste la source de verite */
      });
    return () => {
      cancelled = true;
    };
  }, [dossierId, viewStep]);

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
  const hasLiquidateurBd = !!liquidateurBd?.nom;
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
  const liquidateurSeedRef = useRef(false);
  useEffect(() => {
    if (liquidateurSeedRef.current) return;
    if (!liquidateurBd?.nom) return;
    liquidateurSeedRef.current = true;
    // Une saisie déjà persistée dans le brouillon prime sur la BD : sinon on
    // effacerait la correction faite au passage précédent.
    if (stepData.step1?.liquidateur) return;
    setLiquidateurSecours(toLiquidateurState(liquidateurBd));
  }, [liquidateurBd, stepData.step1?.liquidateur]);
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
            presence: 'présent',
            mandataireNom: '',
            // Fix M6 (2026-08-16) — la CIN est en base depuis la création : sans
            // elle, la comparution des PV d'associé unique sortait « CIN n° , ».
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

  const maxDate = new Date(Date.now() + 30 * 24 * 3600 * 1000).toISOString().slice(0, 10);
  const todayStr = new Date().toISOString().slice(0, 10);

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
    if (!dossierId.trim()) errs.dossierId = 'Selectionnez la societe dissoute a liquider.';
    if (!dateDissolution) {
      errs.dateDissolution =
        "La date de dissolution est introuvable en base : renseignez-la (dossier anterieur a "
        + "l'enregistrement automatique).";
    }
    if (!dateCloture) {
      errs.dateCloture = `La date de l'${isAU ? "assemblee / decision" : 'AGE'} de cloture est obligatoire.`;
    } else if (delaiError) {
      errs.dateCloture = delaiError;
    }
    if (!liquidateur.nom.trim()) {
      errs.liquidateur =
        "Aucun liquidateur n'est enregistre pour cette societe : designez-le (il sera ensuite "
        + 'repris automatiquement).';
    }
    if (boniMali === null) {
      errs.comptes = "Le total de l'actif et le total du passif (MAD) sont obligatoires.";
    } else if (Number.parseFloat(totalActif) < 0 || Number.parseFloat(totalPassif) < 0) {
      errs.comptes = "Les totaux d'actif et de passif doivent etre positifs ou nuls.";
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
    () => `Liquidation : cloture au ${dateCloture || '—'}`,
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
        pieceType: 'CIN',
        pieceNumero: liquidateur.cin,
        genre: (liquidateur.civilite === 'Mme' || liquidateur.civilite === 'Mlle'
          ? 'féminin'
          : 'masculin') as 'masculin' | 'féminin',
        remuneration: liquidateur.remuneration,
        siege: siegeLiquidation || undefined,
      },
      cloture: {
        resultatSens: (resultatType ?? 'boni') as 'boni' | 'mali',
        resultatMontant: boniMali === null ? null : Math.abs(boniMali),
        dateClotureLiquidation: dateCloture || null,
        actifRealise: totalActif || null,
        passifRegle: totalPassif || null,
      },
      dissolutionDate: dateDissolution,
    }),
    [liquidateur, siegeLiquidation, resultatType, boniMali, dateCloture,
      totalActif, totalPassif, dateDissolution],
  );

  if (loading) return <Loader />;
  if (!ticket || !progress)
    return (
      <div className="rounded-lg border border-danger/40 bg-danger/10 p-6 text-sm text-danger">
        {error ?? 'Workflow introuvable'}
      </div>
    );

  const step = viewStep;
  const isTerminated = progress.statut === 'TERMINE';

  async function handleValidate() {
    setError(null);
    if (step === 1) {
      const errs = Object.values(fieldErrors).filter(Boolean) as string[];
      if (errs.length > 0) {
        setRevealErrors(true);
        setError(`Veuillez corriger ${errs.length} erreur${errs.length > 1 ? 's' : ''} (surlignees en rouge).`);
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
        setError('Generez puis validez le PV de cloture ET le rapport de liquidation avant de continuer.');
        return;
      }
      if (!annonceValidated) {
        setError(
          "Generez puis validez l'annonce legale de cloture : sa publication au Journal "
            + "d'Annonces Legales fonde la radiation au registre du commerce (les numero et "
            + 'date de depot legal restent facultatifs a ce stade).',
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
        };
      }
      if (n === 3) return { piecesJointes };
      return null;
    },
    [dossierId, formeJuridique, dateCloture, dateDissolution, liquidateur, totalActif,
      totalPassif, convocationDate, convocationHeure, convocationSnapshot, docsValidated,
      annonceValidated, depotLegalNumero, depotLegalDate, piecesJointes],
  );

  useStepAutosave(step, () => draftPayloadFor(step), registerDirty);

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
      setError((err as Error)?.message ?? 'Echec de cloture');
    }
  }

  const showErr = (field: keyof FieldErrors): string | null =>
    revealErrors ? fieldErrors[field] ?? null : null;

  const decisionLabel = isAU ? "Decision de l'associe unique" : 'Assemblee Generale Extraordinaire (AGE)';
  const liquidateurLabel =
    [liquidateur.civilite, liquidateur.prenom, liquidateur.nom].filter(Boolean).join(' ') || '—';

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
          validateLabel: step === 4 ? 'Finaliser' : "Valider l'etape",
          // La cloture est REFUSEE par le backend tant qu'une succursale reste ouverte :
          // on desactive le bouton plutot que de laisser l'employe buter sur une erreur.
          validateDisabled: step === 4 && succursalesOuvertes.length > 0,
        }}
      >
        {step === 1 && (
          <div className="space-y-5" data-testid="liquidation-step1">
            <header>
              <h2 className="text-lg font-semibold text-fg">Etape 1 — Saisie</h2>
              <p className="text-sm text-fg-subtle">
                Selectionnez la societe <strong>DISSOUTE</strong> a liquider, puis renseignez la
                date de l'assemblee de cloture et les comptes finaux. L'identite de la societe,
                la <strong>date de dissolution</strong> et le <strong>liquidateur</strong> (nomme
                lors de la dissolution) sont lus en base : ils ne sont{' '}
                <strong>jamais re-saisis</strong>.
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
                label="Societe a liquider"
                help="Seules les societes DISSOUTE sont eligibles. Si la societe n'apparait pas, executez d'abord le workflow Dissolution."
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
              <div className="rounded-lg border border-indigo-200 bg-accent/10 p-3 text-xs text-fg-muted" role="status">
                Formalisme detecte : <strong>{decisionLabel}</strong> — Modeles :{' '}
                <code className="rounded bg-bg-overlay px-1.5 py-0.5">
                  {isAU ? 'PV_DISSOLUTION_LIQUIDATION_SARL_AU' : 'PV_DISSOLUTION_LIQUIDATION_SARL'}
                </code>{' '}
                +{' '}
                <code className="rounded bg-bg-overlay px-1.5 py-0.5">RAPPORT_LIQUIDATION_DIRECTEUR</code>{' '}
                +{' '}
                <code className="rounded bg-bg-overlay px-1.5 py-0.5">
                  {isAU ? 'ANNONCE_LEGALE_LIQUIDATION_SARL_AU' : 'ANNONCE_LEGALE_LIQUIDATION_SARL'}
                </code>
              </div>
            )}

            {/* Une succursale n'a pas de personnalite juridique distincte : elle ne peut
                pas survivre a la radiation de sa societe. La finalisation est REFUSEE
                tant qu'il en reste une ouverte — autant l'annoncer des l'etape 1. */}
            {dossierId && succursalesOuvertes.length > 0 && (
              <div
                className="flex items-start gap-2 rounded-lg border border-warning/40 bg-warning/10 p-3 text-xs text-fg-muted"
                role="alert"
                data-testid="liquidation-succursales-ouvertes"
              >
                <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0 text-warning" />
                <span>
                  Cette societe exploite encore{' '}
                  <strong>
                    {succursalesOuvertes.length} succursale
                    {succursalesOuvertes.length > 1 ? 's' : ''}
                  </strong>{' '}
                  :{' '}
                  {succursalesOuvertes
                    .map((s) => `${s.denomination ?? 'Succursale'}${s.ville ? ` (${s.ville})` : ''}`)
                    .join(', ')}
                  . La cloture de la liquidation sera <strong>refusee</strong> tant qu'elles ne
                  sont pas fermees (workflow «&nbsp;Fermeture de succursale&nbsp;») : chacune doit
                  etre radiee au greffe de son lieu d'exploitation.
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
                  <Lock className="h-4 w-4 text-accent" />
                  <h4 className="text-sm font-semibold text-fg">Repris du dossier (lecture seule)</h4>
                </div>
                {partiesLoading && (
                  <p className="text-[11px] text-fg-subtle">Chargement des donnees du dossier…</p>
                )}

                <div className="grid gap-4 rounded-lg bg-bg-overlay p-3 sm:grid-cols-2">
                  <div>
                    <p className="text-xs text-fg-subtle">Denomination</p>
                    <p className="text-sm font-medium text-fg">{targetDenomination || '—'}</p>
                  </div>
                  <div>
                    <p className="text-xs text-fg-subtle">Forme juridique</p>
                    <p className="text-sm font-medium text-fg">{formeJuridique || '—'}</p>
                  </div>
                  <div>
                    <p className="text-xs text-fg-subtle">Date de dissolution</p>
                    <p className="text-sm font-medium text-fg" data-testid="liquidation-date-dissolution-bd">
                      {hasDateDissolutionBd ? dateDissolution : '— non enregistree —'}
                    </p>
                  </div>
                  <div>
                    <p className="text-xs text-fg-subtle">Liquidateur</p>
                    <p className="text-sm font-medium text-fg" data-testid="liquidation-liquidateur-bd">
                      {hasLiquidateurBd ? liquidateurLabel : '— non enregistre —'}
                    </p>
                  </div>
                  {/* Le CIN etait absent de ce recapitulatif : il est publie dans
                      l'annonce, son absence devait donc se voir. */}
                  <div>
                    <p className="text-xs text-fg-subtle">N° CIN du liquidateur</p>
                    <p className="text-sm font-medium text-fg" data-testid="liquidation-liquidateur-cin-bd">
                      {liquidateur.cin || '— a renseigner ci-dessous —'}
                    </p>
                  </div>
                  {siegeLiquidation && (
                    <div className="sm:col-span-2">
                      <p className="text-xs text-fg-subtle">Siege de la liquidation</p>
                      <p className="text-sm font-medium text-fg">{siegeLiquidation}</p>
                    </div>
                  )}
                </div>

                {hasLiquidateurBd && hasDateDissolutionBd && (
                  <p className="text-[11px] text-fg-subtle">
                    Ces donnees proviennent du workflow Dissolution : elles ne sont pas
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
                  hint="Aucune date de dissolution en base (dossier anterieur a l'enregistrement automatique) : saisissez-la une seule fois."
                />
              </div>
            )}

            {/* ---- Liquidateur : pre-rempli depuis la BD, toujours corrigeable ---- */}
            {dossierId && !partiesLoading && (
              <div className="space-y-2" data-testid="liquidation-secours-liquidateur">
                {!hasLiquidateurBd && (
                  <div
                    role="status"
                    className="flex items-start gap-2 rounded-lg border-l-4 border-amber-400 bg-warning/10 p-3 text-xs text-warning"
                  >
                    <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0" />
                    <span>
                      Aucun liquidateur n'est enregistre pour cette societe (dossier dissous avant
                      l'enregistrement automatique). Designez-le <strong>une seule fois</strong> :
                      il sera persiste au dossier et repris ensuite sans re-saisie.
                    </span>
                  </div>
                )}
                {hasLiquidateurBd && (
                  <div
                    role="status"
                    className="flex items-start gap-2 rounded-lg border-l-4 border-accent bg-accent/10 p-3 text-xs text-fg-muted"
                  >
                    <Lock className="mt-0.5 h-4 w-4 flex-shrink-0 text-accent" />
                    <span>
                      Liquidateur <strong>repris de la dissolution</strong> : les champs sont
                      deja remplis, rien n'est a re-saisir. Completez seulement ce qui manque —
                      typiquement le <strong>N° CIN</strong>, rarement present en Data Room et
                      pourtant publie dans l'annonce. Toute correction est enregistree au dossier.
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
              label={`Date ${isAU ? 'de la decision' : "de l'AGE"} de cloture *`}
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
                  ? "La cloture releve de la decision de l'associe unique."
                  : 'La cloture de la liquidation se decide toujours en assemblee generale EXTRAORDINAIRE.'
              }
            />

            {delaiError ? (
              <div
                role="alert"
                data-testid="liquidation-delai-15j-error"
                className="flex items-start gap-2 rounded-lg border border-danger/40 bg-danger/10 p-3 text-xs text-danger"
              >
                <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0" />
                <div>
                  <p className="font-semibold">Delai legal non respecte (RG-LI03)</p>
                  <p>{delaiError}</p>
                </div>
              </div>
            ) : (
              joursDepuisDissolution !== null && (
                <div
                  role="status"
                  data-testid="liquidation-delai-15j-ok"
                  className="flex items-center gap-2 rounded-lg border-l-4 border-emerald-400 bg-emerald-50 p-3 text-xs text-emerald-800"
                >
                  <CheckCircle className="h-4 w-4" />
                  <span>
                    Delai legal respecte : {joursDepuisDissolution} jour
                    {joursDepuisDissolution > 1 ? 's' : ''} entre la dissolution et la cloture
                    (minimum {LIQUIDATION_DELAI_JOURS} jours).
                  </span>
                </div>
              )
            )}

            {/* ---- Comptes finaux → boni / mali CALCULÉ ---- */}
            <section className="rounded-xl border border-border bg-bg-raised p-4" data-testid="liquidation-comptes">
              <h4 className="mb-3 text-sm font-semibold text-fg">Comptes finaux de liquidation</h4>
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
                          ? 'text-emerald-700'
                          : 'text-rose-700'
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
                Le sens (boni ou mali) et son montant sont <strong>calcules</strong> depuis ces
                deux totaux : ils alimentent directement le PV, le rapport et l'annonce legale.
              </p>
            </section>

            {/* ---- Convocation OPTIONNELLE — règle des 16 jours ---- */}
            <section className="space-y-3 rounded-xl border border-border bg-bg-raised p-4" data-testid="liquidation-convocation">
              <div className="flex items-center gap-2">
                <Megaphone className="h-4 w-4 text-accent" />
                <h4 className="text-sm font-semibold text-fg">Convocation (optionnelle)</h4>
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
                Regle des <strong>{CONVOCATION_DELAI_JOURS} jours</strong> : l'assemblee ne peut
                se tenir moins de {CONVOCATION_DELAI_JOURS} jours calendaires apres la
                convocation. Laisser vide si aucune convocation n'est emise. Les associes a
                convoquer sont repris du dossier.
              </p>
              {convocationError && (
                <div
                  role="alert"
                  data-testid="liquidation-convocation-16j-error"
                  className="flex items-start gap-2 rounded-lg border border-danger/40 bg-danger/10 p-2 text-xs text-danger"
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
                <Sparkles className="h-5 w-5 text-violet-600" />
                <h2 className="text-lg font-semibold text-fg">Etape 2 — Generation des actes</h2>
              </div>
              <p className="text-sm text-fg-subtle">
                Le <strong>PV de cloture</strong>, le <strong>rapport de liquidation</strong> et
                l'<strong>annonce legale de cloture</strong> sont generes a partir de l'etape 1
                et de la Data Room : aucune donnee n'est re-saisie. A la validation, chaque
                document est <strong>depose automatiquement</strong> et versionne dans la
                dataroom juridique.
              </p>
            </header>

            <SeanceOperationForm
              mode="liquidation"
              dossierId={ticket?.dossierId ?? dossierId}
              ticketId={ticket?.id}
              formeJuridique={isAU ? 'SARL_AU' : 'SARL'}
              societe={societeInput}
              defaultDate={dateCloture}
              operation={operationInput}
              initialAssocies={bdAssocies}
              initialGerants={bdGerants}
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
                <Paperclip className="h-5 w-5 text-accent" />
                <h2 className="text-lg font-semibold text-fg">Etape 3 — Pieces jointes (optionnelle)</h2>
              </div>
              <p className="text-sm text-fg-subtle">
                Deposez ici les <strong>versions legalisees</strong> des documents generes (PV de
                cloture signe, rapport signe, annonce publiee, modele J…). Cette etape est{' '}
                <strong>entierement facultative</strong> : vous pouvez la laisser vide et
                poursuivre vers la synthese. Chaque depot est versionne en Data Room.
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
                Aucune piece jointe deposee pour le moment — cette etape est facultative.
              </p>
            )}
          </div>
        )}

        {step === 4 && (
          <div className="space-y-4" data-testid="liquidation-synthese">
            {succursalesOuvertes.length > 0 ? (
              <div
                className="rounded-xl border border-danger/40 bg-danger/10 p-6"
                role="alert"
                data-testid="liquidation-blocage-succursales"
              >
                <AlertTriangle className="mb-2 h-8 w-8 text-danger" />
                <h2 className="text-xl font-bold text-fg">Cloture impossible en l'etat</h2>
                <p className="mt-2 text-sm text-fg-muted">
                  Cette societe exploite encore{' '}
                  <strong>
                    {succursalesOuvertes.length} succursale
                    {succursalesOuvertes.length > 1 ? 's' : ''}
                  </strong>
                  . Une succursale n'a pas de personnalite juridique distincte de sa societe :
                  elle ne peut pas survivre a sa radiation. Fermez-les d'abord — chacune par le
                  workflow «&nbsp;Fermeture de succursale&nbsp;», avec son PV, son annonce legale
                  et sa radiation au greffe de son lieu d'exploitation.
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
              <div className="rounded-xl bg-gradient-to-r from-violet-600 to-violet-700 p-6 text-bg-raised">
                <CheckCircle className="mb-2 h-8 w-8" />
                <h2 className="text-2xl font-bold">Liquidation finalisee</h2>
                <p className="text-sm text-violet-100">
                  Le PV de cloture, le rapport de liquidation et l'annonce legale ont ete generes,
                  valides et deposes en dataroom. La societe est desormais{' '}
                  <strong>liquidee</strong>.
                </p>
              </div>
            )}

            <div className="rounded-xl border border-border bg-bg-raised p-4" data-testid="synthese-societe">
              <p className="mb-3 text-sm font-semibold text-fg">Societe et decision</p>
              <div className="grid gap-4 rounded-lg bg-bg-overlay p-3 sm:grid-cols-2">
                <div>
                  <p className="text-xs text-fg-subtle">Denomination</p>
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
                    Date {isAU ? 'de la decision' : "de l'AGE"} de cloture
                  </p>
                  <p className="text-sm font-medium text-fg">{dateCloture || '—'}</p>
                </div>
                <div>
                  <p className="text-xs text-fg-subtle">Liquidateur</p>
                  <p className="text-sm font-medium text-fg">
                    {liquidateurLabel}{' '}
                    <span className="text-xs font-normal text-fg-subtle">
                      ({hasLiquidateurBd ? 'nomme a la dissolution' : 'designe en liquidation'})
                    </span>
                  </p>
                </div>
                <div>
                  <p className="text-xs text-fg-subtle">Convocation</p>
                  <p className="text-sm font-medium text-fg">
                    {convocationDate
                      ? `${convocationDate}${convocationHeure ? ` a ${convocationHeure}` : ''}`
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
                  <p className="text-xs text-fg-subtle">Resultat</p>
                  <p className="text-sm font-medium text-fg">
                    {boniMali === null
                      ? '—'
                      : `${Math.abs(boniMali).toLocaleString('fr-FR')} MAD (${resultatType})`}
                  </p>
                </div>
              </div>
            </div>

            <div className="rounded-xl border border-border bg-bg-raised p-4" data-testid="synthese-documents">
              <p className="mb-3 text-sm font-semibold text-fg">Documents et depots</p>
              <ul className="space-y-1 rounded-lg bg-bg-overlay p-3 text-sm text-fg-muted">
                <li>
                  PV de cloture + rapport de liquidation :{' '}
                  {docsValidated ? 'generes et valides' : 'non valides'} — deposes en Data Room
                  versionnee.
                </li>
                <li>
                  Annonce legale de cloture : {annonceValidated ? 'generee et validee' : 'non validee'}
                  {depotLegalNumero || depotLegalDate
                    ? ` — depot legal n° ${depotLegalNumero || '—'} du ${depotLegalDate || '—'}`
                    : ' — depot legal (n° + date) a completer apres depot au greffe'}
                  .
                </li>
                <li>
                  Pieces jointes :{' '}
                  {piecesJointes.length > 0
                    ? piecesJointes
                        .map((p) => `${p.label}${p.version ? ` (v${p.version})` : ''}`)
                        .join(', ')
                    : 'aucune (etape facultative)'}
                  .
                </li>
              </ul>
            </div>

            <div className="rounded-xl border border-border bg-bg-raised p-4 text-sm text-fg-muted">
              <p><strong>Statut societe :</strong> LIQUIDEE</p>
              <p className="mt-1"><strong>Archivage Data Room :</strong> confirme.</p>
              <p className="mt-1">
                <strong>Prochaine etape suggeree :</strong> engager la radiation au Registre du
                Commerce.
              </p>
            </div>

            {!isTerminated ? (
              <div className="flex gap-2">
                <Button onClick={handleCloturer}>Cloturer le workflow</Button>
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
            navigate('/tickets');
          }}
        />
      )}
    </>
  );
}

function Loader() {
  return (
    <div className="flex h-64 items-center justify-center">
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-border border-t-indigo-600" />
    </div>
  );
}
