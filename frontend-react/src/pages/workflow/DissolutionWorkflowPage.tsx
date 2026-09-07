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
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertCircle, AlertTriangle, CheckCircle, Megaphone, Paperclip, Sparkles } from 'lucide-react';
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
import { DissolutionAnnoncePanel } from '../../components/workflow/DissolutionAnnoncePanel';
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
  buildDocFilename,
  formeLabel,
} from '../../components/workflow/workflowFilename';
import { workflowService, type DossierParties } from '../../services/workflow.service';
import type { TemplateInfo } from '../../services/workflowDocumentService';
import type { AssocieInput, GerantInput } from '../../components/workflow/SeanceForm';
import type { DossierBrief } from '../../types/dataroom';

const STEPS: RoadmapStep[] = [
  { number: 1, label: 'Saisie' },
  { number: 2, label: 'Generation' },
  { number: 3, label: 'Pieces jointes' },
  { number: 4, label: 'Synthese' },
];

const MOTIF_MIN_LENGTH = 20;

// Lot W1 revise (2026-07-04) : seules les societes ACTIVE sont dissolvables.
const ALLOWED_STATUTS = ['ACTIVE'];

/** Libellé « type de document » pour le nommage `type - dénom - forme (- v<n>)`. */
export function dissolutionDocType(code: string): string {
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
  const [dossierId, setDossierId] = useState(initialId);
  const [dossier, setDossier] = useState<DossierBrief | null>(null);
  const [motif, setMotif] = useState((stepData.step1?.motif as string) ?? '');
  const [dateAGE, setDateAGE] = useState((stepData.step1?.dateAGE as string) ?? '');
  const [revealErrors, setRevealErrors] = useState<boolean>(false);
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
  const [dossierParties, setDossierParties] = useState<DossierParties | null>(null);
  const [partiesLoading, setPartiesLoading] = useState(false);

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

  useEffect(() => {
    if (stepData.step1?.dossierId && !dossierId) {
      setDossierId(stepData.step1.dossierId as string);
    }
  }, [stepData.step1?.dossierId, dossierId]);

  useEffect(() => {
    setRevealErrors(viewStep === 1 && (motif.length > 0 || !!dateAGE));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [viewStep]);

  // Parties prenantes (gerants/associes) DES la selection de la societe : elles
  // peuplent le selecteur de liquidateur ET la convocation, sans re-saisie.
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
        /* best-effort : la saisie externe reste possible */
      })
      .finally(() => {
        if (!cancelled) setPartiesLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [dossierId]);

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

  const minDate = new Date(Date.now() - 2 * 365 * 24 * 3600 * 1000).toISOString().slice(0, 10);
  const maxDate = new Date(Date.now() + 30 * 24 * 3600 * 1000).toISOString().slice(0, 10);

  // Regle DURE des 16 jours (miroir exact du backend WorkflowSteps.convocationDelaiError).
  const convocationError = useMemo(
    () => convocationDelaiError(convocationDate, dateAGE),
    [convocationDate, dateAGE],
  );

  type FieldErrors = {
    dossierId?: string;
    dateAGE?: string;
    motif?: string;
    liquidateur?: string;
    siegeLiquidation?: string;
    convocation?: string;
  };
  const fieldErrors = useMemo<FieldErrors>(() => {
    const errs: FieldErrors = {};
    if (!dossierId.trim()) errs.dossierId = 'Selectionnez la societe a dissoudre.';
    if (!dateAGE) errs.dateAGE = "La date de l'AGE est obligatoire.";
    if (motif.trim().length < MOTIF_MIN_LENGTH) {
      errs.motif = `Le motif est trop court (${motif.trim().length}/${MOTIF_MIN_LENGTH} caracteres minimum) — precisez la cause.`;
    }
    if (!liquidateur.nom.trim()) {
      errs.liquidateur = 'Designez le liquidateur (gerant/associe existant ou tiers).';
    } else if (!liquidateur.adresse.trim()) {
      errs.liquidateur = "L'adresse du liquidateur est obligatoire : elle figure dans l'annonce legale.";
    }
    if (!siegeLiquidation.trim()) {
      errs.siegeLiquidation = 'Le siege de la liquidation est obligatoire.';
    }
    if (convocationError) errs.convocation = convocationError;
    return errs;
  }, [dossierId, dateAGE, motif, liquidateur, siegeLiquidation, convocationError]);

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
    () => `Dissolution : ${motif || 'dissolution anticipee'}`,
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
        pieceType: 'CIN',
        pieceNumero: liquidateur.cin,
        genre: (liquidateur.civilite === 'Mme' || liquidateur.civilite === 'Mlle'
          ? 'féminin'
          : 'masculin') as 'masculin' | 'féminin',
        remuneration: liquidateur.remuneration,
        siege: siegeLiquidation,
      },
      dissolutionDate: dateAGE,
    }),
    [liquidateur, siegeLiquidation, dateAGE],
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
          siege: siegeLiquidation,
        },
        siegeLiquidation,
        convocation: convocationPayload(),
      });
    } else if (step === 2) {
      if (!pvValidated) {
        setError('Generez puis validez le PV de dissolution avant de continuer.');
        return;
      }
      if (!annonceValidated) {
        setError(
          "Generez puis validez l'annonce legale de dissolution : sa publication au Journal "
            + "d'Annonces Legales est obligatoire (les numero et date de depot legal restent "
            + 'facultatifs a ce stade).',
        );
        return;
      }
      await executeStep(2, {
        pvValide: true,
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
          dateAGE,
          formeJuridique,
          liquidateur,
          siegeLiquidation,
          convocation: convocationPayload(),
          convocationSnapshot,
        };
      }
      if (n === 2) {
        return {
          pvValide: pvValidated,
          annonceValide: annonceValidated,
          depotLegal: { numero: depotLegalNumero, date: depotLegalDate },
        };
      }
      if (n === 3) return { piecesJointes };
      return null;
    },
    // convocationPayload lit convocationDate/Heure : dependances explicitees.
    [dossierId, motif, dateAGE, formeJuridique, liquidateur, siegeLiquidation,
      convocationDate, convocationHeure, convocationSnapshot, pvValidated,
      annonceValidated, depotLegalNumero, depotLegalDate, piecesJointes],
  );

  // Enregistre le getter pour l'etape AFFICHEE : navigation ET sauvegarde
  // automatique (20 s) persistent desormais la saisie en cours.
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

  const decisionLabel = isAU ? "Decision de l'associe unique" : 'Assemblee Generale Extraordinaire (AGE)';
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
        footer={{ validateLabel: step === 4 ? 'Finaliser' : "Valider l'etape" }}
      >
        {step === 1 && (
          <div className="space-y-5" data-testid="dissolution-step1">
            <header>
              <h2 className="text-lg font-semibold text-fg">Etape 1 — Saisie</h2>
              <p className="text-sm text-fg-subtle">
                Selectionnez la societe <strong>ACTIVE</strong> a dissoudre, puis renseignez le
                motif, la date de l'assemblee, le liquidateur et le siege de la liquidation.
                L'identite de la societe (denomination, capital, siege, RC, ville du greffe) est
                lue en base : elle n'est <strong>jamais re-saisie</strong>.
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
                help="Seules les societes au statut ACTIVE peuvent etre dissoutes."
              />
              {showErr('dossierId') && (
                <p className="mt-1 flex items-start gap-1 text-xs text-danger" role="alert">
                  <AlertCircle className="mt-0.5 h-3 w-3 flex-shrink-0" />
                  {showErr('dossierId')}
                </p>
              )}
            </div>

            {dossier && (
              <div className="rounded-lg border border-indigo-200 bg-accent/10 p-3 text-xs text-fg-muted" role="status">
                Formalisme detecte : <strong>{decisionLabel}</strong> — Modeles :{' '}
                <code className="rounded bg-bg-overlay px-1.5 py-0.5">
                  {isAU ? 'PV_DISSOLUTION_LIQUIDATION_SARL_AU' : 'PV_DISSOLUTION_LIQUIDATION_SARL'}
                </code>{' '}
                +{' '}
                <code className="rounded bg-bg-overlay px-1.5 py-0.5">
                  {isAU ? 'ANNONCE_LEGALE_DISSOLUTION_SARL_AU' : 'ANNONCE_LEGALE_DISSOLUTION_SARL'}
                </code>
              </div>
            )}

            <TextField
              label={`Date ${isAU ? 'de la decision' : "de l'AGE"} *`}
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
                  ? "La dissolution releve de la decision de l'associe unique."
                  : 'La dissolution se decide toujours en assemblee generale EXTRAORDINAIRE.'
              }
            />

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
              <textarea
                id="dissolution-motif"
                rows={4}
                value={motif}
                onChange={(e) => setMotif(e.target.value)}
                onBlur={() => setRevealErrors(true)}
                placeholder="Cessation d'activite, mesentente entre associes, perte d'agrement..."
                className={`w-full rounded-lg border bg-bg-raised px-3 py-2 text-sm focus:outline-none focus:ring-2 ${
                  showErr('motif')
                    ? 'border-danger focus:border-danger focus:ring-danger/30'
                    : 'border-border-hi focus:border-indigo-500 focus:ring-indigo-200'
                }`}
                aria-invalid={!!showErr('motif')}
              />
              <p className={`mt-1 text-xs ${motif.trim().length < MOTIF_MIN_LENGTH ? 'text-danger' : 'text-emerald-600'}`}>
                {motif.trim().length} / {MOTIF_MIN_LENGTH} caracteres minimum
                {motif.trim().length >= MOTIF_MIN_LENGTH && ' ✓'}
              </p>
              {showErr('motif') && (
                <p className="mt-1 flex items-start gap-1 text-xs text-danger" role="alert">
                  <AlertCircle className="mt-0.5 h-3 w-3 flex-shrink-0" />
                  {showErr('motif')}
                </p>
              )}
              <p className="mt-1 text-[11px] text-fg-subtle">
                Le motif figure au proces-verbal ; il n'apparait pas dans l'annonce legale.
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

            <TextField
              label="Siege de la liquidation *"
              value={siegeLiquidation}
              data-testid="dissolution-siege-liquidation"
              onChange={(e) => setSiegeLiquidation(e.target.value)}
              onBlur={() => setRevealErrors(true)}
              error={showErr('siegeLiquidation') ?? undefined}
              hint="Adresse a laquelle sont notifies les actes de la liquidation (publiee au JAL)."
            />

            <section className="space-y-3 rounded-xl border border-border bg-bg-raised p-4" data-testid="dissolution-convocation">
              <div className="flex items-center gap-2">
                <Megaphone className="h-4 w-4 text-accent" />
                <h4 className="text-sm font-semibold text-fg">Convocation (optionnelle)</h4>
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
                Regle des <strong>{CONVOCATION_DELAI_JOURS} jours</strong> : l'assemblee ne peut se
                tenir moins de {CONVOCATION_DELAI_JOURS} jours calendaires apres la convocation.
                Laisser vide si aucune convocation n'est emise.
              </p>
              {convocationError && (
                <div
                  role="alert"
                  data-testid="dissolution-convocation-16j-error"
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
                <Sparkles className="h-5 w-5 text-violet-600" />
                <h2 className="text-lg font-semibold text-fg">Etape 2 — Generation des actes</h2>
              </div>
              <p className="text-sm text-fg-subtle">
                Le <strong>PV de dissolution</strong> et l'<strong>annonce legale</strong> sont
                generes a partir de l'etape 1 et de la Data Room : aucune donnee n'est re-saisie.
                A la validation, chaque document est <strong>depose automatiquement</strong> et
                versionne dans la dataroom juridique.
              </p>
            </header>

            <SeanceOperationForm
              mode="dissolution"
              dossierId={ticket?.dossierId ?? dossierId}
              ticketId={ticket?.id}
              formeJuridique={isAU ? 'SARL_AU' : 'SARL'}
              societe={societeInput}
              defaultDate={dateAGE}
              motif={motif}
              operation={operationInput}
              initialAssocies={bdAssocies}
              initialGerants={bdGerants}
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
              liquidateur={liquidateur}
              siegeLiquidation={siegeLiquidation}
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
                <Paperclip className="h-5 w-5 text-accent" />
                <h2 className="text-lg font-semibold text-fg">Etape 3 — Pieces jointes (optionnelle)</h2>
              </div>
              <p className="text-sm text-fg-subtle">
                Deposez ici les <strong>versions legalisees</strong> des documents generes (PV
                signe, annonce publiee, modele J…). Cette etape est{' '}
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
          <div className="space-y-4" data-testid="dissolution-synthese">
            <div className="rounded-xl bg-gradient-to-r from-rose-500 to-rose-600 p-6 text-bg-raised">
              <CheckCircle className="mb-2 h-8 w-8" />
              <h2 className="text-2xl font-bold">Dissolution finalisee</h2>
              <p className="text-sm text-rose-100">
                Le PV de dissolution et l'annonce legale ont ete generes, valides et deposes en
                dataroom. La societe est desormais <strong>dissoute</strong>.
              </p>
            </div>

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
                  <p className="text-xs text-fg-subtle">Date {isAU ? 'de la decision' : "de l'AGE"}</p>
                  <p className="text-sm font-medium text-fg">{dateAGE || '—'}</p>
                </div>
                <div>
                  <p className="text-xs text-fg-subtle">Convocation</p>
                  <p className="text-sm font-medium text-fg">
                    {convocationDate
                      ? `${convocationDate}${convocationHeure ? ` a ${convocationHeure}` : ''}`
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
                  <p className="text-xs text-fg-subtle">Identite</p>
                  <p className="text-sm font-medium text-fg">
                    {[liquidateur.civilite, liquidateur.prenom, liquidateur.nom]
                      .filter(Boolean)
                      .join(' ') || '—'}{' '}
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
                  <p className="text-xs text-fg-subtle">Siege de la liquidation</p>
                  <p className="text-sm font-medium text-fg">{siegeLiquidation || '—'}</p>
                </div>
              </div>
            </div>

            <div className="rounded-xl border border-border bg-bg-raised p-4" data-testid="synthese-documents">
              <p className="mb-3 text-sm font-semibold text-fg">Documents et depots</p>
              <ul className="space-y-1 rounded-lg bg-bg-overlay p-3 text-sm text-fg-muted">
                <li>
                  PV de dissolution : {pvValidated ? 'genere et valide' : 'non valide'} — deposes en
                  Data Room versionnee.
                </li>
                <li>
                  Annonce legale de dissolution : {annonceValidated ? 'generee et validee' : 'non validee'}
                  {depotLegalNumero || depotLegalDate
                    ? ` — depot legal n° ${depotLegalNumero || '—'} du ${depotLegalDate || '—'}`
                    : ' — depot legal (n° + date) a completer apres depot au greffe'}
                  .
                </li>
                <li>
                  Pieces jointes : {piecesJointes.length > 0
                    ? piecesJointes.map((p) => p.label).join(', ')
                    : 'aucune (etape facultative)'}
                  .
                </li>
              </ul>
            </div>

            <div className="rounded-xl border border-border bg-bg-raised p-4 text-sm text-fg-muted">
              <p><strong>Statut societe :</strong> DISSOUTE</p>
              <p className="mt-1"><strong>Archivage Data Room :</strong> confirme.</p>
              <p className="mt-1">
                <strong>Prochaine etape suggeree :</strong> ouvrir un ticket Liquidation lorsque le
                delai legal de 16 jours est ecoule (RG-LI03).
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
