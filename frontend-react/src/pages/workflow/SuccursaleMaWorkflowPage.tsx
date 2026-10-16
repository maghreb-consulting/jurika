/**
 * Workflow SUCCURSALE MAROCAINE en 5 étapes — spec directeur, lot DIVERS §B (2026-08-13).
 *
 *  1. Société mère + assemblée — dossier existant (identité lue en BD, JAMAIS re-saisie),
 *     date d'assemblée + TYPE (ordinaire / extraordinaire), convocation OPTIONNELLE
 *     (règle des 16 jours calendaires).
 *  2. Saisie de la succursale — enseigne, adresse, ville, activité, date d'ouverture,
 *     ville du greffe PROPRE à la succursale, dotation facultative, responsable
 *     facultatif (BD d'abord, sinon externe + OCR CIN recto-verso).
 *  3. Génération — PV de création de succursale + annonce légale d'ouverture ;
 *     feuille de présence et PV d'incident optionnels (non bloquants).
 *  4. Pièces jointes — dépôt des versions légalisées (OPTIONNELLE, peut rester vide).
 *  5. Synthèse — récapitulatif + clôture.
 *
 * ANTI-DUPLICATION : chaque donnée n'a qu'un seul champ. L'identité de la mère vient de
 * la BD ; le bloc succursale saisi à l'étape 2 alimente à la fois le PV et l'annonce —
 * le sous-formulaire de génération l'affiche en récapitulatif verrouillé (`locked`).
 */
import { useCallback, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AlertCircle,
  AlertTriangle,
  Building2,
  Megaphone,
  Paperclip,
  Sparkles,
} from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/TextField';
import { Select } from '../../components/ui/Select';
import { WorkflowShell } from '../../components/workflow/WorkflowShell';
import type { RoadmapStep } from '../../components/workflow/WorkflowRoadmap';
import { ticketService } from '../../services/ticket.service';
import { CancelTicketDialog } from '../tickets/CancelTicketDialog';
import { useWorkflow } from './useWorkflow';
import { useStepAutosave } from './useStepAutosave';
import { WorkflowBoot } from './WorkflowBoot';
import { AideEtape } from './AideEtape';
import { ETAPES_SUCCURSALE_MA, etapesRoadmap } from './aidesParcours';
import { bornesDateSeance, useDossierParties, useEtatReinitialise } from './parcoursHooks';
import { DossierAutocomplete } from '../../components/workflow/DossierAutocomplete';
import { SuccursaleOperationForm } from '../../components/workflow/SuccursaleOperationForm';
import { SuccursaleAnnoncePanel } from '../../components/workflow/SuccursaleAnnoncePanel';
import { SuccursaleSaisieForm, type SuccursaleSaisieState } from '../../components/workflow/SuccursaleSaisieForm';
import { emptySuccursaleSaisie, succursaleSaisieErrors, toSuccursalePayload } from '../../components/workflow/succursaleSaisie';
import { ConvocationPanel, FeuillePresencePanel } from '../../components/workflow/SeanceDocPanels';
import { IncidentSeancePanel } from '../../components/workflow/IncidentSeancePanel';
import {
  PiecesJointesPanel,
  type PieceJointeEntry,
} from '../../components/workflow/PiecesJointesPanel';
import {
  CONVOCATION_DELAI_JOURS,
  addDaysIso,
  convocationDelaiError,
} from '../../components/workflow/convocationDelai';
import { buildDocFilename, formeLabel } from '../../components/workflow/workflowFilename';
import type { TemplateInfo } from '../../services/workflowDocumentService';
import type { AssocieInput, GerantInput } from '../../components/workflow/SeanceForm';
import type { DossierBrief } from '../../types/dataroom';
import { ErreurParcours, SqueletteParcours } from '../../components/workflow/EtatsParcours';
import { BandeauFinParcours } from '../../components/workflow/BandeauFinParcours';

const STEPS: RoadmapStep[] = etapesRoadmap(ETAPES_SUCCURSALE_MA);

/** Une société dissoute / liquidée / radiée n'ouvre pas de succursale. */
const ALLOWED_STATUTS_MERE = ['ACTIVE'];

/** Libellé « type de document » pour le nommage `type - dénomination - forme (- v<n>)`. */
function succursaleMaDocType(code: string): string {
  if (code.startsWith('ANNONCE_LEGALE_OUVERTURE_SUCCURSALE')) {
    return 'Annonce légale — Ouverture de succursale';
  }
  if (code.startsWith('PV_CREATION_SUCCURSALE_MAROC')) return 'PV — Création de succursale';
  if (code.startsWith('CONVOCATION')) return 'Convocation';
  if (code.startsWith('FEUILLE_PRESENCE')) return 'Feuille de présence';
  if (code.includes('DEFAUT_QUORUM')) return 'PV — Défaut de quorum';
  if (code.includes('IRREGULARITE')) return 'PV — Irrégularité de convocation';
  return code;
}

/**
 * Monte {@link SuccursaleMaWorkflowPageBody} SOUS {@link WorkflowBoot} : les champs de chaque
 * etape s'initialisent avec `useState(stepData...)`, qui ne lit sa valeur qu'au
 * premier render. Sans ce montage differe, ce premier render a lieu AVANT la
 * reponse du serveur et tous les champs restent vides apres un rechargement
 * (F5, deconnexion/reconnexion), meme sur une etape deja validee.
 */
/**
 * Ordre du jour de l'assemblée décidant l'ouverture d'une succursale — il était
 * vide. Amorce éditable, pas un verrou.
 */
const ORDRE_DU_JOUR_OUVERTURE_SUCCURSALE = [
  'Ouverture d\'une succursale et définition de son activité',
  'Fixation de l\'adresse de la succursale',
  'Désignation du responsable de la succursale et de ses pouvoirs',
  'Immatriculation au Registre du Commerce du lieu d\'exploitation',
  'Pouvoirs en vue des formalités légales',
];

export function SuccursaleMaWorkflowPage() {
  return (
    <WorkflowBoot>
      <SuccursaleMaWorkflowPageBody />
    </WorkflowBoot>
  );
}

function SuccursaleMaWorkflowPageBody() {
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

  // ---------- Étape 1 : société mère + assemblée ----------
  const initialId =
    (stepData.step1?.dossierId as string) ??
    (stepData.step1?.societeMereId as string) ??
    ticket?.dossierId ??
    '';
  const [dossierId, setDossierId] = useState(initialId);
  const [dossier, setDossier] = useState<DossierBrief | null>(null);
  const [dateAG, setDateAG] = useState((stepData.step1?.dateAG as string) ?? '');
  const [typeAssemblee, setTypeAssemblee] = useState<'ordinaire' | 'extraordinaire'>(
    (stepData.step1?.assembleeNature as 'ordinaire' | 'extraordinaire') ?? 'extraordinaire',
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
  // Remises a zero a chaque changement d'etape.
  const [revealErrors, setRevealErrors] = useEtatReinitialise(viewStep, () => false);

  // ---------- Étape 2 : saisie de la succursale ----------
  const [succursale, setSuccursale] = useState<SuccursaleSaisieState>(
    () =>
      (stepData.step2?.succursaleForm as SuccursaleSaisieState | undefined) ??
      emptySuccursaleSaisie(),
  );
  const [mandataire, setMandataire] = useState(
    (stepData.step2?.formalitesMandataireNom as string) ?? '',
  );

  // ---------- Étape 3 : génération ----------
  const [pvValidated, setPvValidated] = useState<boolean>(!!(stepData.step3?.pvValide as boolean));
  const [annonceValidated, setAnnonceValidated] = useState<boolean>(
    !!(stepData.step3?.annonceValide as boolean),
  );
  const [depotLegalNumero, setDepotLegalNumero] = useState(
    ((stepData.step3?.depotLegal as { numero?: string } | undefined) ?? {}).numero ?? '',
  );
  const [depotLegalDate, setDepotLegalDate] = useState(
    ((stepData.step3?.depotLegal as { date?: string } | undefined) ?? {}).date ?? '',
  );

  // ---------- Étape 4 : pièces jointes ----------
  const [piecesJointes, setPiecesJointes] = useState<PieceJointeEntry[]>(
    () => (stepData.step4?.piecesJointes as PieceJointeEntry[] | undefined) ?? [],
  );

  // Parties prenantes DÈS la sélection : elles peuplent le sélecteur de responsable
  // ET la convocation — aucune re-saisie d'identité.
  // Best-effort : un echec de lecture laisse la saisie externe possible.
  const { dossierParties, partiesLoading } = useDossierParties(dossierId);

  const formeJuridiqueRaw = useMemo(
    () =>
      (dossier?.formeJuridique as string | null | undefined) ??
      (stepData.step1?.formeJuridique as string | undefined) ??
      null,
    [dossier, stepData.step1?.formeJuridique],
  );
  const isAU = formeJuridiqueRaw === 'SARL_AU';
  const formeJuridique: 'SARL' | 'SARL_AU' = isAU ? 'SARL_AU' : 'SARL';

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

  // Règle DURE des 16 jours (miroir exact du backend WorkflowSteps.convocationDelaiError).
  const convocationError = useMemo(
    () => convocationDelaiError(convocationDate, dateAG),
    [convocationDate, dateAG],
  );

  type Step1Errors = { dossierId?: string; dateAG?: string; convocation?: string };
  const step1Errors = useMemo<Step1Errors>(() => {
    const errs: Step1Errors = {};
    if (!dossierId.trim()) errs.dossierId = 'Sélectionnez la société mère.';
    if (!dateAG) errs.dateAG = 'La date de l’assemblée est obligatoire.';
    if (convocationError) errs.convocation = convocationError;
    return errs;
  }, [dossierId, dateAG, convocationError]);

  const step2Errors = useMemo(() => succursaleSaisieErrors(succursale), [succursale]);

  const targetDenomination = dossier?.raisonSociale ?? ticket?.titre ?? '';

  const workflowFilenameFor = useCallback(
    (tpl: TemplateInfo, opts?: { version?: number }) =>
      buildDocFilename(
        succursaleMaDocType(tpl.code),
        targetDenomination,
        formeLabel(formeJuridiqueRaw),
        opts?.version,
      ),
    [targetDenomination, formeJuridiqueRaw],
  );

  const depositMotif = useMemo(
    () => `Ouverture de succursale : ${succursale.enseigne || 'succursale'}`,
    [succursale.enseigne],
  );

  const convocationPayload = useCallback(() => {
    if (!convocationDate) return undefined;
    return { date: convocationDate, heure: convocationHeure || undefined };
  }, [convocationDate, convocationHeure]);

  /** Bloc succursale partagé PV ↔ annonce (source unique : l'étape 2). */
  const succursalePayload = useMemo(() => toSuccursalePayload(succursale), [succursale]);

  /** Graine du sous-formulaire PV : verrouillée, jamais re-saisie. */
  const succursaleSeed = useMemo(
    () => ({
      enseigne: succursale.enseigne,
      adresse: succursale.adresse,
      ville: succursale.ville,
      villeGreffe: succursale.villeGreffe || succursale.ville,
      activite: succursale.activite,
      dateOuverture: succursale.dateOuverture,
      dotationPresente: succursale.dotationPresente,
      dotationMontant: succursale.dotationMontant,
      responsablePresent: succursale.responsable.present,
      responsableCivilite: succursale.responsable.civilite,
      responsablePrenom: succursale.responsable.prenom,
      responsableNom:
        succursale.responsable.typePersonne === 'MORALE'
          ? succursale.responsable.denomination
          : succursale.responsable.nom,
      responsableNationalite: succursale.responsable.nationalite,
      responsableAdresse: succursale.responsable.adresse,
      responsablePieceType: succursale.responsable.pieceType,
      responsablePieceNumero: succursale.responsable.pieceNumero,
      responsablePouvoirs: succursale.responsable.pouvoirs,
    }),
    [succursale],
  );

  const draftPayloadFor = useCallback(
    (n: number): Record<string, unknown> | null => {
      if (n === 1) {
        return {
          dossierId,
          societeMereId: dossierId,
          dateAG,
          assembleeNature: typeAssemblee,
          formeJuridique: formeJuridiqueRaw,
          convocation: convocationPayload(),
          convocationSnapshot,
        };
      }
      if (n === 2) {
        return {
          succursaleForm: succursale,
          succursale: succursalePayload,
          formalitesMandataireNom: mandataire,
        };
      }
      if (n === 3) {
        return {
          pvValide: pvValidated,
          annonceValide: annonceValidated,
          depotLegal: { numero: depotLegalNumero, date: depotLegalDate },
        };
      }
      if (n === 4) return { piecesJointes };
      return null;
    },
    [dossierId, dateAG, typeAssemblee, formeJuridiqueRaw, convocationPayload,
      convocationSnapshot, succursale, succursalePayload,
      mandataire, pvValidated, annonceValidated, depotLegalNumero, depotLegalDate,
      piecesJointes],
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
  const showErr1 = (field: keyof Step1Errors): string | undefined =>
    revealErrors ? step1Errors[field] : undefined;

  async function handleValidate() {
    setError(null);
    if (step === 1) {
      const errs = Object.values(step1Errors).filter(Boolean) as string[];
      if (errs.length > 0) {
        setRevealErrors(true);
        setError(
          `Veuillez corriger ${errs.length} erreur${errs.length > 1 ? 's' : ''} (signalée${errs.length > 1 ? 's' : ''} en rouge).`,
        );
        return;
      }
      await executeStep(1, {
        dossierId,
        dateAG,
        typeAssemblee,
        formeJuridique: formeJuridiqueRaw ?? undefined,
        convocation: convocationPayload(),
      });
    } else if (step === 2) {
      const errs = Object.values(step2Errors).filter(Boolean) as string[];
      if (errs.length > 0) {
        setRevealErrors(true);
        setError(
          `Veuillez corriger ${errs.length} erreur${errs.length > 1 ? 's' : ''} (signalée${errs.length > 1 ? 's' : ''} en rouge).`,
        );
        return;
      }
      await executeStep(2, {
        succursale: succursalePayload,
        formalitesMandataireNom: mandataire || undefined,
      });
    } else if (step === 3) {
      if (!pvValidated) {
        setError('Générez puis validez le procès-verbal de création de la succursale avant de continuer.');
        return;
      }
      if (!annonceValidated) {
        setError(
          'Générez puis validez l’annonce légale d’ouverture : sa publication dans un journal '
            + 'd’annonces légales est obligatoire (le numéro et la date du dépôt légal restent '
            + 'facultatifs à ce stade).',
        );
        return;
      }
      await executeStep(3, {
        pvValide: true,
        annonceValide: true,
        depotLegal:
          depotLegalNumero || depotLegalDate
            ? { numero: depotLegalNumero || null, date: depotLegalDate || null }
            : undefined,
      });
    } else if (step === 4) {
      // Étape OPTIONNELLE : jamais bloquante, peut être vide.
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

  const decisionLabel = isAU
    ? 'Décision de l’associé unique'
    : typeAssemblee === 'ordinaire'
      ? 'Assemblée générale ordinaire (AGO)'
      : 'Assemblée générale extraordinaire (AGE)';

  const societeInput = {
    // Identite complete : sans elle, les formulaires de seance laissaient vides le
    // lieu de l'assemblee (defaut = siege social) et le lieu de signature.
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
        onSaveDraft={!isTerminated && step < 5 ? handleSaveDraft : undefined}
        onValidate={!isTerminated ? handleValidate : undefined}
        onCancel={() => setShowCancel(true)}
        footer={{ validateLabel: step === 5 ? 'Finaliser' : 'Valider l’étape' }}
      >
        <AideEtape
          parcours="succursale-ma"
          etapes={ETAPES_SUCCURSALE_MA}
          etape={step}
          manquants={
            step === 1
              ? (Object.values(step1Errors).filter(Boolean) as string[])
              : step === 2
                ? (Object.values(step2Errors).filter(Boolean) as string[])
                : undefined
          }
        />

        {step === 1 && (
          <div className="space-y-5" data-testid="succursale-ma-step1">
            <header>
              <h2 className="text-lg font-semibold text-fg">Étape 1 — Société mère et assemblée</h2>
              <p className="text-sm text-fg-subtle">
                Sélectionnez la société <strong>active</strong> qui ouvre la succursale, puis la
                date et le type de l’assemblée qui la décide. L’identité de la société
                (dénomination, capital, siège, RC, ville du greffe) est lue dans le dossier : vous
                n’avez{' '}<strong>jamais à la ressaisir</strong>.
              </p>
            </header>

            <div>
              <DossierAutocomplete
                value={dossierId}
                onSelect={(d) => {
                  setDossier(d);
                  setDossierId(d?.id ?? '');
                }}
                allowedStatuts={ALLOWED_STATUTS_MERE}
                help="Seules les sociétés au statut « active » peuvent ouvrir une succursale."
              />
              {showErr1('dossierId') && (
                <p className="mt-1 flex items-start gap-1 text-xs text-danger" role="alert">
                  <AlertCircle className="mt-0.5 h-3 w-3 flex-shrink-0" />
                  {showErr1('dossierId')}
                </p>
              )}
            </div>

            {dossier && (
              <div
                className="rounded-lg border border-info/30 bg-info-soft p-3 text-xs text-fg-muted"
                role="status"
              >
                Organe compétent : <strong>{decisionLabel}</strong>. Actes produits : procès-verbal
                de création de la succursale{isAU ? ' (SARL à associé unique)' : ' (SARL)'} et
                annonce légale d’ouverture.
              </div>
            )}

            <div className="grid gap-3 md:grid-cols-2">
              <TextField
                label={`Date ${isAU ? 'de la décision' : 'de l’assemblée'} *`}
                type="date"
                value={dateAG}
                min={minDate}
                max={maxDate}
                data-testid="succursale-ma-date-ag"
                onChange={(e) => setDateAG(e.target.value)}
                onBlur={() => setRevealErrors(true)}
                error={showErr1('dateAG')}
              />
              <Select
                label="Type d’assemblée *"
                value={typeAssemblee}
                data-testid="succursale-ma-type-assemblee"
                onChange={(e) =>
                  setTypeAssemblee(e.target.value as 'ordinaire' | 'extraordinaire')
                }
                options={[
                  { value: 'extraordinaire', label: 'Extraordinaire (AGE)' },
                  { value: 'ordinaire', label: 'Ordinaire (AGO)' },
                ]}
                hint={
                  isAU
                    ? 'En SARL à associé unique, l’associé unique exerce les pouvoirs de l’assemblée.'
                    : 'Selon les statuts, l’ouverture d’une succursale peut relever de l’AGO ou de l’AGE.'
                }
              />
            </div>

            <section
              className="space-y-3 rounded-xl border border-border bg-bg-raised p-4"
              data-testid="succursale-ma-convocation"
            >
              <div className="flex items-center gap-2">
                <Megaphone className="h-4 w-4 text-fg-muted" />
                <h4 className="text-sm font-semibold text-fg">Convocation (facultative)</h4>
              </div>
              <div className="grid gap-3 md:grid-cols-2">
                <TextField
                  label="Date de convocation"
                  type="date"
                  value={convocationDate}
                  data-testid="succursale-ma-convocation-date"
                  onChange={(e) => {
                    const v = e.target.value;
                    setConvocationDate(v);
                    if (v && !dateAG) setDateAG(addDaysIso(v, CONVOCATION_DELAI_JOURS));
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
                  data-testid="succursale-ma-convocation-16j-error"
                  className="flex items-start gap-2 rounded-lg border border-danger/40 bg-danger-soft p-2 text-xs text-danger"
                >
                  <AlertTriangle className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" />
                  <span>{convocationError}</span>
                </div>
              )}
              <ConvocationPanel
                dossierId={dossierId || ticket?.dossierId}
                ticketId={ticket?.id}
                formeJuridique={formeJuridique}
                societe={societeInput}
                // Date d'assemblee, date ET heure de convocation viennent de cette
                // etape : le panneau les recoit, il ne les redemande pas.
                defaultSeance={{
                  type: typeAssemblee,
                  date: dateAG,
                  convDate: convocationDate,
                  convHeure: convocationHeure,
                }}
                initialOrdreDuJour={ORDRE_DU_JOUR_OUVERTURE_SUCCURSALE}
                embedded
                initialAssocies={bdAssocies}
                initialGerants={bdGerants}
                initialSnapshot={convocationSnapshot}
                onSnapshot={setConvocationSnapshot}
                hideSeanceDate
                hideConvDate
              contexteSeance={`Succursale${dateAG ? ` du ${dateAG}` : ''}`}
                versioned
                motif={depositMotif}
                filenameFor={workflowFilenameFor}
              />
            </section>
          </div>
        )}

        {step === 2 && (
          <div className="space-y-5" data-testid="succursale-ma-step2">
            <header>
              <div className="flex items-center gap-2">
                <Building2 className="h-5 w-5 text-fg-muted" />
                <h2 className="text-lg font-semibold text-fg">Étape 2 — Données de la succursale</h2>
              </div>
              <p className="text-sm text-fg-subtle">
                Ces informations alimentent <strong>à la fois</strong> le procès-verbal et l’annonce
                légale : elles ne sont saisies qu’ici. La <strong>ville du greffe</strong> de la
                succursale est distincte de celle du siège : la succursale s’immatricule au RC de son
                lieu d’exploitation.
              </p>
            </header>

            <SuccursaleSaisieForm
              value={succursale}
              onChange={setSuccursale}
              parties={dossierParties}
              partiesLoading={partiesLoading}
              dossierId={dossierId || ticket?.dossierId}
              errors={revealErrors ? step2Errors : {}}
            />

            <section className="space-y-3 rounded-xl border border-border bg-bg-raised p-4">
              <div className="flex items-center gap-2">
                <Sparkles className="h-4 w-4 text-info" />
                <h4 className="text-sm font-semibold text-fg">Formalités</h4>
              </div>
              <TextField
                label="Mandataire chargé des formalités"
                value={mandataire}
                onChange={(e) => setMandataire(e.target.value)}
                hint="Obligatoire : la résolution des pouvoirs le nomme."
              />
            </section>
          </div>
        )}

        {step === 3 && (
          <div className="space-y-4" data-testid="succursale-ma-step3">
            <header>
              <div className="flex items-center gap-2">
                <Sparkles className="h-5 w-5 text-info" />
                <h2 className="text-lg font-semibold text-fg">Étape 3 — Génération des actes</h2>
              </div>
              <p className="text-sm text-fg-subtle">
                Le <strong>procès-verbal de création de succursale</strong> et l’
                <strong>annonce légale d’ouverture</strong> sont produits à partir des étapes 1 et 2
                et de la Data Room : aucune donnée n’est ressaisie. À la validation, chaque document
                est <strong>déposé automatiquement</strong> et versionné dans la Data Room juridique.
              </p>
            </header>

            <SuccursaleOperationForm
              mode="creation-ma"
              dossierId={dossierId || ticket?.dossierId}
              ticketId={ticket?.id}
              formeJuridique={formeJuridique}
              societe={societeInput}
              defaultDate={dateAG}
              defaultConvocation={
                convocationDate ? { date: convocationDate, heure: convocationHeure || undefined } : undefined
              }
              succursaleSeed={succursaleSeed}
              denominationForFilename={targetDenomination}
              locked
              initialAssocies={bdAssocies}
              initialGerants={bdGerants}
              onReady={setPvValidated}
              versioned
              depositMotif={depositMotif}
              filenameFor={workflowFilenameFor}
            />

            <SuccursaleAnnoncePanel
              mode="ouverture"
              dossierId={dossierId || ticket?.dossierId}
              ticketId={ticket?.id}
              formeJuridique={formeJuridique}
              denomination={targetDenomination}
              dateAssemblee={dateAG}
              typeAssemblee={typeAssemblee}
              succursale={{
                enseigne: succursale.enseigne,
                adresse: succursale.adresse,
                ville: succursale.ville,
                villeGreffe: succursale.villeGreffe || succursale.ville,
                activite: succursale.activite,
                dateOuverture: succursale.dateOuverture,
                dotationPresente: succursale.dotationPresente,
                dotationMontant: succursale.dotationMontant,
                responsablePresent: succursale.responsable.present,
                responsable: {
                  civilite: succursale.responsable.civilite,
                  prenom: succursale.responsable.prenom,
                  nom:
                    succursale.responsable.typePersonne === 'MORALE'
                      ? succursale.responsable.denomination
                      : succursale.responsable.nom,
                  nationalite: succursale.responsable.nationalite,
                  adresse: succursale.responsable.adresse,
                  pieceType: succursale.responsable.pieceType,
                  pieceNumero: succursale.responsable.pieceNumero,
                  pouvoirs: succursale.responsable.pouvoirs,
                },
              }}
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
            {!(formeJuridique === 'SARL_AU') && (
              <IncidentSeancePanel
                dossierId={dossierId || ticket?.dossierId}
                ticketId={ticket?.id}
                formeJuridique={formeJuridique}
                societe={societeInput}
                // Type, date d'assemblee et convocation viennent de l'etape 1 :
                // ces panneaux les recoivent, ils ne les redemandent pas.
                defaultSeance={{
                  type: typeAssemblee,
                  date: dateAG,
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
            {!(formeJuridique === 'SARL_AU') && (
              <FeuillePresencePanel
                dossierId={dossierId || ticket?.dossierId}
                ticketId={ticket?.id}
                formeJuridique={formeJuridique}
                societe={societeInput}
                // Type, date d'assemblee et convocation viennent de l'etape 1 :
                // ces panneaux les recoivent, ils ne les redemandent pas.
                defaultSeance={{
                  type: typeAssemblee,
                  date: dateAG,
                  convDate: convocationDate,
                  convHeure: convocationHeure,
                }}
                lockType
                hideSeanceDate
                hideConvDate
                contexteSeance={`Succursale${dateAG ? ` du ${dateAG}` : ''}`}
                initialAssocies={bdAssocies}
                initialGerants={bdGerants}
                versioned
                motif={depositMotif}
                filenameFor={workflowFilenameFor}
              />
            )}
          </div>
        )}

        {step === 4 && (
          <div className="space-y-4" data-testid="succursale-ma-step4-pieces">
            <header>
              <div className="flex items-center gap-2">
                <Paperclip className="h-5 w-5 text-fg-muted" />
                <h2 className="text-lg font-semibold text-fg">
                  Étape 4 — Pièces jointes (facultative)
                </h2>
              </div>
              <p className="text-sm text-fg-subtle">
                Déposez ici les <strong>versions légalisées</strong> des documents produits
                (procès-verbal signé, annonce publiée, modèle 4…). Cette étape est{' '}
                <strong>entièrement facultative</strong> : vous pouvez la laisser vide et poursuivre
                vers la synthèse. Chaque dépôt est versionné dans la Data Room.
              </p>
            </header>
            <PiecesJointesPanel
              dossierId={dossierId || ticket?.dossierId}
              ticketId={ticket?.id}
              denomination={targetDenomination}
              forme={formeLabel(formeJuridiqueRaw)}
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

        {step === 5 && (
          <div className="space-y-4" data-testid="succursale-ma-synthese">
            <BandeauFinParcours titre="Succursale créée">
                Le procès-verbal de création et l’annonce légale d’ouverture ont été produits,
                validés et déposés dans la Data Room.
              </BandeauFinParcours>

            <div
              className="rounded-xl border border-border bg-bg-raised p-4"
              data-testid="synthese-societe"
            >
              <p className="mb-3 text-sm font-semibold text-fg">Société mère et décision</p>
              <div className="grid gap-4 rounded-lg bg-bg-overlay p-3 sm:grid-cols-2">
                <Field label="Dénomination" value={targetDenomination} />
                <Field label="Forme juridique" value={formeJuridiqueRaw} />
                <Field label={`Date ${isAU ? 'de la décision' : 'de l’assemblée'}`} value={dateAG} />
                <Field label="Type d’assemblée" value={typeAssemblee} />
                <Field
                  label="Convocation"
                  value={
                    convocationDate
                      ? `${convocationDate}${convocationHeure ? ` à ${convocationHeure}` : ''}`
                      : 'Aucune'
                  }
                />
              </div>
            </div>

            <div
              className="rounded-xl border border-border bg-bg-raised p-4"
              data-testid="synthese-succursale"
            >
              <p className="mb-3 text-sm font-semibold text-fg">Succursale</p>
              <div className="grid gap-4 rounded-lg bg-bg-overlay p-3 sm:grid-cols-2">
                <Field label="Enseigne" value={succursale.enseigne} />
                <Field label="Activité" value={succursale.activite} />
                <Field label="Adresse" value={succursale.adresse} className="sm:col-span-2" />
                <Field label="Ville" value={succursale.ville} />
                <Field
                  label="Greffe de la succursale"
                  value={succursale.villeGreffe || succursale.ville}
                />
                <Field label="Date d’ouverture" value={succursale.dateOuverture} />
                <Field
                  label="Dotation"
                  value={
                    succursale.dotationPresente
                      ? `${succursale.dotationMontant} MAD`
                      : 'Aucune'
                  }
                />
                <Field
                  label="Responsable"
                  className="sm:col-span-2"
                  value={
                    succursale.responsable.present
                      ? [
                          succursale.responsable.civilite,
                          succursale.responsable.prenom,
                          succursale.responsable.typePersonne === 'MORALE'
                            ? succursale.responsable.denomination
                            : succursale.responsable.nom,
                        ]
                          .filter(Boolean)
                          .join(' ')
                      : 'Aucun'
                  }
                />
              </div>
            </div>

            <div
              className="rounded-xl border border-border bg-bg-raised p-4"
              data-testid="synthese-documents"
            >
              <p className="mb-3 text-sm font-semibold text-fg">Documents et dépôts</p>
              <ul className="space-y-1 rounded-lg bg-bg-overlay p-3 text-sm text-fg-muted">
                <li>
                  Procès-verbal de création de succursale : {pvValidated ? 'produit et validé' : 'non validé'} —
                  déposé et versionné dans la Data Room.
                </li>
                <li>
                  Annonce légale d’ouverture :{' '}
                  {annonceValidated ? 'produite et validée' : 'non validée'}
                  {depotLegalNumero || depotLegalDate
                    ? ` — dépôt légal n° ${depotLegalNumero || '—'} du ${depotLegalDate || '—'}`
                    : ' — numéro et date du dépôt légal à compléter après le dépôt au greffe'}
                  .
                </li>
                <li>
                  Pièces jointes :{' '}
                  {piecesJointes.length > 0
                    ? piecesJointes.map((p) => p.label).join(', ')
                    : 'aucune (étape facultative)'}
                  .
                </li>
              </ul>
            </div>

            <div className="rounded-xl border border-border bg-bg-raised p-4 text-sm text-fg-muted">
              <p>
                <strong>Immatriculation :</strong> la succursale doit être immatriculée au registre
                du commerce de{' '}
                <strong>{succursale.villeGreffe || succursale.ville || '—'}</strong> dans les trois
                mois de son ouverture (loi 15-95, art. 75).
              </p>
              <p className="mt-1">
                <strong>N° RC de la succursale :</strong> attribué par le greffe après le dépôt — à
                saisir dans les identifiants du dossier, pas ici.
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

function Field({
  label,
  value,
  className = '',
}: {
  label: string;
  value?: string | null;
  className?: string;
}) {
  return (
    <div className={className}>
      <p className="text-xs text-fg-subtle">{label}</p>
      <p className="text-sm font-medium text-fg">{value?.trim() ? value : '—'}</p>
    </div>
  );
}

function Loader() {
  return <SqueletteParcours />;
}
