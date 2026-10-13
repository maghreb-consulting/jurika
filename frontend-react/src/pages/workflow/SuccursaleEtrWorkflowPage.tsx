/**
 * Workflow SUCCURSALE ÉTRANGÈRE en 5 étapes — spec directeur, lot DIVERS §C (2026-08-13).
 *
 *  1. Société mère étrangère — saisie complète ($SOCIETE_MERE_*) OU sélection d'une mère
 *     déjà enregistrée (elle a alors sa propre Data Room) · date + type de décision de
 *     l'organe · convocation OPTIONNELLE (16 jours) · contrôles de conformité BLOQUANTS.
 *  2. Saisie de la succursale — identique au workflow marocain (composant partagé).
 *  3. Génération — PV de création (société étrangère) + annonce légale d'ouverture,
 *     VARIANTE ÉTRANGÈRE ⚠ dérivée (à faire valider par le directeur) · optionnels.
 *  4. Pièces jointes — dépôt des versions légalisées (OPTIONNELLE).
 *  5. Synthèse — récapitulatif + clôture.
 *
 * DATA ROOM DE LA MÈRE : à la finalisation, la société mère étrangère devient un dossier
 * à part entière (`origine = ETRANGERE`) — c'est ce qui lui donne sa Data Room et permet
 * de rattacher la succursale créée. Une mère déjà enregistrée est réutilisée, jamais
 * re-saisie.
 */
import { useCallback, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AlertCircle,
  AlertTriangle,
  Building2,
  CheckCircle,
  Globe,
  Megaphone,
  Paperclip,
  ShieldCheck,
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
import { ETAPES_SUCCURSALE_ETR, etapesRoadmap } from './aidesParcours';
import { bornesDateSeance, useEtatReinitialise } from './parcoursHooks';
import { DossierAutocomplete } from '../../components/workflow/DossierAutocomplete';
import { SuccursaleOperationForm } from '../../components/workflow/SuccursaleOperationForm';
import { SuccursaleAnnoncePanel } from '../../components/workflow/SuccursaleAnnoncePanel';
import { ConvocationPanel } from '../../components/workflow/SeanceDocPanels';
import { SuccursaleSaisieForm, type SuccursaleSaisieState } from '../../components/workflow/SuccursaleSaisieForm';
import { emptySuccursaleSaisie, succursaleSaisieErrors, toSuccursalePayload } from '../../components/workflow/succursaleSaisie';
import {
  PiecesJointesPanel,
  type PieceJointeEntry,
} from '../../components/workflow/PiecesJointesPanel';
import {
  CONVOCATION_DELAI_JOURS,
  addDaysIso,
  convocationDelaiError,
} from '../../components/workflow/convocationDelai';
import { buildDocFilename } from '../../components/workflow/workflowFilename';
import type { TemplateInfo } from '../../services/workflowDocumentService';
import type { DossierBrief } from '../../types/dataroom';

const STEPS: RoadmapStep[] = etapesRoadmap(ETAPES_SUCCURSALE_ETR);

/** Contrôles de conformité BLOQUANTS — miroir exact du backend. */
const CONTROLES = [
  { key: 'controleOffice', label: "OMPIC : dénomination disponible au Maroc" },
  { key: 'controleApostille', label: 'Statuts de la mère apostillés' },
  { key: 'controleTraduction', label: 'Traduction certifiée FR/AR' },
  { key: 'controleProcuration', label: 'Procuration au représentant résident' },
  { key: 'controleDomiciliation', label: 'Adresse au Maroc justifiée (domiciliation / bail)' },
  { key: 'controleConvention', label: "Convention Maroc — pays d'origine vérifiée" },
] as const;

type ControleKey = (typeof CONTROLES)[number]['key'];

interface SocieteMereState {
  denomination: string;
  forme: string;
  pays: string;
  capital: string;
  siege: string;
  registre: string;
  registreNumero: string;
  loiApplicable: string;
}

function emptyMere(): SocieteMereState {
  return {
    denomination: '',
    forme: '',
    pays: '',
    capital: '',
    siege: '',
    registre: '',
    registreNumero: '',
    loiApplicable: '',
  };
}

function succursaleEtrDocType(code: string): string {
  if (code.startsWith('ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE')) {
    return 'Annonce légale — Ouverture de succursale';
  }
  if (code.startsWith('PV_CREATION_SUCCURSALE_ETRANGERE')) return 'PV — Création de succursale';
  return code;
}

/**
 * Monte {@link SuccursaleEtrWorkflowPageBody} SOUS {@link WorkflowBoot} : les champs de chaque
 * etape s'initialisent avec `useState(stepData...)`, qui ne lit sa valeur qu'au
 * premier render. Sans ce montage differe, ce premier render a lieu AVANT la
 * reponse du serveur et tous les champs restent vides apres un rechargement
 * (F5, deconnexion/reconnexion), meme sur une etape deja validee.
 */
/**
 * Ordre du jour de l'organe décidant l'ouverture d'une succursale marocaine par une
 * société ÉTRANGÈRE. Amorce éditable.
 */
const ORDRE_DU_JOUR_OUVERTURE_SUCCURSALE_ETR = [
  'Ouverture d\'une succursale au Maroc et définition de son activité',
  'Fixation de l\'adresse de la succursale',
  'Désignation du représentant légal de la succursale au Maroc et de ses pouvoirs',
  'Immatriculation au Registre du Commerce du lieu d\'exploitation',
  'Pouvoirs en vue des formalités légales et de la publicité',
];

export function SuccursaleEtrWorkflowPage() {
  return (
    <WorkflowBoot>
      <SuccursaleEtrWorkflowPageBody />
    </WorkflowBoot>
  );
}

function SuccursaleEtrWorkflowPageBody() {
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

  // ---------- Étape 1 : société mère étrangère ----------
  const [reuseExisting, setReuseExisting] = useState<boolean>(
    !!(stepData.step1?.dossierMereEtrangereId as string),
  );
  const [dossierMereId, setDossierMereId] = useState(
    (stepData.step1?.dossierMereEtrangereId as string) ?? '',
  );
  const [dossierMere, setDossierMere] = useState<DossierBrief | null>(null);
  const [mere, setMere] = useState<SocieteMereState>(
    () => (stepData.step1?.societeMere as SocieteMereState | undefined) ?? emptyMere(),
  );
  const [organeCompetent, setOrganeCompetent] = useState(
    ((stepData.step1?.organe as { competent?: string } | undefined) ?? {}).competent ?? '',
  );
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
  const [controles, setControles] = useState<Record<ControleKey, boolean>>(() => {
    const persisted = (stepData.step1?.origine as Record<string, unknown> | undefined) ?? {};
    const init = {} as Record<ControleKey, boolean>;
    for (const c of CONTROLES) init[c.key] = !!persisted[c.key];
    return init;
  });
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

  /** Faits de la mère effectivement utilisés : sélection BD ou saisie. */
  const mereEffective = useMemo<SocieteMereState>(() => {
    if (!reuseExisting || !dossierMere) return mere;
    return {
      ...mere,
      denomination: dossierMere.raisonSociale,
      pays: dossierMere.pays ?? mere.pays,
    };
  }, [reuseExisting, dossierMere, mere]);

  /**
   * Uni/pluripersonnel : faute d'identité en base, on le déduit de l'organe
   * décisionnaire (« … unique ») ou de la forme d'origine — même règle que le backend.
   */
  const isAU = useMemo(() => {
    const organe = organeCompetent.toLowerCase();
    if (organe.includes('unique')) return true;
    const f = (mereEffective.forme ?? '').toUpperCase();
    return f.includes('AU') || f.includes('UNIQUE');
  }, [organeCompetent, mereEffective.forme]);
  const formeJuridique: 'SARL' | 'SARL_AU' = isAU ? 'SARL_AU' : 'SARL';

  const [{ minDate, maxDate }] = useState(bornesDateSeance);

  const convocationError = useMemo(
    () => convocationDelaiError(convocationDate, dateAG),
    [convocationDate, dateAG],
  );

  const controlesManquants = useMemo(
    () => CONTROLES.filter((c) => !controles[c.key]).map((c) => c.label),
    [controles],
  );

  type Step1Errors = {
    mere?: string;
    dateAG?: string;
    convocation?: string;
    conformite?: string;
  };
  const step1Errors = useMemo<Step1Errors>(() => {
    const errs: Step1Errors = {};
    if (reuseExisting) {
      if (!dossierMereId.trim()) {
        errs.mere = 'Sélectionnez la société mère étrangère déjà enregistrée.';
      }
    } else if (!mere.denomination.trim()) {
      errs.mere = 'La dénomination de la société mère est obligatoire.';
    } else if (!mere.forme.trim() || !mere.pays.trim() || !mere.siege.trim()) {
      errs.mere =
        'La forme juridique d’origine, le pays et le siège social sont obligatoires : ces mentions figurent dans l’annonce légale.';
    }
    if (!dateAG) errs.dateAG = 'La date de la décision de l’organe est obligatoire.';
    if (convocationError) errs.convocation = convocationError;
    if (controlesManquants.length > 0) {
      errs.conformite = `Contrôles de conformité manquants : ${controlesManquants.join(', ')}.`;
    }
    return errs;
  }, [reuseExisting, dossierMereId, mere, dateAG, convocationError, controlesManquants]);

  const step2Errors = useMemo(() => succursaleSaisieErrors(succursale), [succursale]);

  const targetDenomination = mereEffective.denomination || ticket?.titre || '';

  const workflowFilenameFor = useCallback(
    (tpl: TemplateInfo, opts?: { version?: number }) =>
      buildDocFilename(
        succursaleEtrDocType(tpl.code),
        targetDenomination,
        mereEffective.forme || 'Étrangère',
        opts?.version,
      ),
    [targetDenomination, mereEffective.forme],
  );

  const depositMotif = useMemo(
    () => `Ouverture de succursale au Maroc : ${succursale.enseigne || 'succursale'}`,
    [succursale.enseigne],
  );

  const succursalePayload = useMemo(() => toSuccursalePayload(succursale), [succursale]);

  const merePayload = useCallback(
    () => ({
      denomination: mereEffective.denomination,
      forme: mereEffective.forme,
      pays: mereEffective.pays,
      capital: mereEffective.capital,
      siege: mereEffective.siege,
      registre: mereEffective.registre,
      registreNumero: mereEffective.registreNumero,
      loiApplicable: mereEffective.loiApplicable,
    }),
    [mereEffective],
  );

  /**
   * Dossier de destination des dépôts Data Room.
   *
   * Depuis le lot DIVERS §C, la Data Room de la société mère est créée **dès la
   * validation de l'étape 1** : le backend renvoie son id dans `step1`. Les documents
   * générés (étape 3) et les pièces jointes (étape 4) y sont donc déposés directement,
   * sans attendre la finalisation. Avant validation de l'étape 1, l'id est absent :
   * les blocs de génération ne déposent alors rien (comportement inchangé).
   */
  const dossierMereEffectifId =
    (stepData.step1?.dossierMereEtrangereId as string | undefined) ||
    (reuseExisting ? dossierMereId : '') ||
    undefined;

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

  const convocationPayload = useCallback(() => {
    if (!convocationDate) return undefined;
    return { date: convocationDate, heure: convocationHeure || undefined };
  }, [convocationDate, convocationHeure]);

  /** Contenu courant de l'étape — voir la note dans DissolutionWorkflowPage. */
  const draftPayloadFor = useCallback(
    (n: number): Record<string, unknown> | null => {
      if (n === 1) {
        return {
          dossierMereEtrangereId: reuseExisting ? dossierMereId : null,
          societeMere: mereEffective,
          organe: { competent: organeCompetent, date: dateAG },
          dateAG,
          assembleeNature: typeAssemblee,
          convocation: convocationPayload(),
          origine: controles,
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
    [reuseExisting, dossierMereId, mereEffective, organeCompetent, dateAG,
      typeAssemblee, convocationPayload, controles, succursale,
      succursalePayload, mandataire, pvValidated, annonceValidated,
      depotLegalNumero, depotLegalDate, piecesJointes],
  );

  // Appele avant les retours anticipes : l'ordre des hooks ne varie jamais.
  useStepAutosave(viewStep, () => draftPayloadFor(viewStep), registerDirty);

  if (loading) return <Loader />;
  if (!ticket || !progress)
    return (
      <div className="rounded-lg border border-danger/40 bg-danger/10 p-6 text-sm text-danger" role="alert">
        {error ?? 'Parcours introuvable : rechargez la page ou revenez à la liste des tickets.'}
      </div>
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
        ...controles,
        dossierMereEtrangereId: reuseExisting ? dossierMereId : undefined,
        societeMere: merePayload(),
        organe: { competent: organeCompetent || undefined, date: dateAG },
        dateAG,
        typeAssemblee,
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
            + 'd’annonces légales est obligatoire.',
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

  const societeInput = { denomination: targetDenomination };

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
          parcours="succursale-etr"
          etapes={ETAPES_SUCCURSALE_ETR}
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
          <div className="space-y-5" data-testid="succursale-etr-step1">
            <header>
              <div className="flex items-center gap-2">
                <Globe className="h-5 w-5 text-accent" />
                <h2 className="text-lg font-semibold text-fg">Étape 1 — Société mère étrangère</h2>
              </div>
              <p className="text-sm text-fg-subtle">
                La société mère est établie hors du Maroc : son identité n’existe pas encore dans la
                plateforme et doit être saisie — sauf si elle a déjà été enregistrée lors d’une
                précédente ouverture ; dans ce cas, <strong>sélectionnez-la</strong> (elle possède
                alors sa propre Data Room, où seront classés les actes de la succursale).
              </p>
            </header>

            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => setReuseExisting(false)}
                data-testid="etr-mode-saisie"
                className={`rounded-lg border px-3 py-2 text-sm ${
                  !reuseExisting
                    ? 'border-accent bg-accent/10 font-semibold text-accent'
                    : 'border-border bg-bg-raised text-fg-muted'
                }`}
              >
                Nouvelle société mère
              </button>
              <button
                type="button"
                onClick={() => setReuseExisting(true)}
                data-testid="etr-mode-selection"
                className={`rounded-lg border px-3 py-2 text-sm ${
                  reuseExisting
                    ? 'border-accent bg-accent/10 font-semibold text-accent'
                    : 'border-border bg-bg-raised text-fg-muted'
                }`}
              >
                Mère déjà enregistrée
              </button>
            </div>

            {reuseExisting ? (
              <div>
                <DossierAutocomplete
                  value={dossierMereId}
                  label="Société mère étrangère"
                  origine="ETRANGERE"
                  onSelect={(d) => {
                    setDossierMere(d);
                    setDossierMereId(d?.id ?? '');
                  }}
                  help="Seules les sociétés mères étrangères déjà enregistrées sont proposées. Leur identité n’est pas ressaisie."
                />
                {showErr1('mere') && (
                  <p className="mt-1 flex items-start gap-1 text-xs text-danger" role="alert">
                    <AlertCircle className="mt-0.5 h-3 w-3 flex-shrink-0" />
                    {showErr1('mere')}
                  </p>
                )}
              </div>
            ) : (
              <section className="space-y-3 rounded-xl border border-border bg-bg-raised p-4">
                <div className="grid gap-3 md:grid-cols-2">
                  <TextField
                    label="Dénomination *"
                    value={mere.denomination}
                    data-testid="etr-mere-denomination"
                    onChange={(e) => setMere({ ...mere, denomination: e.target.value })}
                  />
                  <TextField
                    label="Forme juridique du pays d’origine *"
                    value={mere.forme}
                    data-testid="etr-mere-forme"
                    onChange={(e) => setMere({ ...mere, forme: e.target.value })}
                    placeholder="Ltd, GmbH, SAS, BV…"
                  />
                  <TextField
                    label="Pays d’origine *"
                    value={mere.pays}
                    data-testid="etr-mere-pays"
                    onChange={(e) => setMere({ ...mere, pays: e.target.value })}
                  />
                  <TextField
                    label="Capital (devise comprise)"
                    value={mere.capital}
                    onChange={(e) => setMere({ ...mere, capital: e.target.value })}
                    placeholder="Ex. : 500 000 EUR"
                    hint="Publié tel quel dans l’annonce : la devise d’origine est conservée."
                  />
                  <div className="md:col-span-2">
                    <TextField
                      label="Siège social *"
                      value={mere.siege}
                      data-testid="etr-mere-siege"
                      onChange={(e) => setMere({ ...mere, siege: e.target.value })}
                    />
                  </div>
                  <TextField
                    label="Registre du commerce (type)"
                    value={mere.registre}
                    onChange={(e) => setMere({ ...mere, registre: e.target.value })}
                    placeholder="Ex. : Companies House"
                  />
                  <TextField
                    label="N° de registre"
                    value={mere.registreNumero}
                    onChange={(e) => setMere({ ...mere, registreNumero: e.target.value })}
                  />
                  <div className="md:col-span-2">
                    <TextField
                      label="Loi applicable"
                      value={mere.loiApplicable}
                      onChange={(e) => setMere({ ...mere, loiApplicable: e.target.value })}
                      placeholder="Ex. : loi anglaise"
                    />
                  </div>
                </div>
                {showErr1('mere') && (
                  <p className="flex items-start gap-1 text-xs text-danger" role="alert">
                    <AlertCircle className="mt-0.5 h-3 w-3 flex-shrink-0" />
                    {showErr1('mere')}
                  </p>
                )}
              </section>
            )}

            <div className="grid gap-3 md:grid-cols-2">
              <TextField
                label="Organe compétent"
                value={organeCompetent}
                data-testid="etr-organe-competent"
                onChange={(e) => setOrganeCompetent(e.target.value)}
                placeholder="Ex. : le conseil d’administration, l’associé unique…"
                hint="Publié dans l’annonce : « Aux termes de la décision de … »."
              />
              <TextField
                label="Date de la décision *"
                type="date"
                value={dateAG}
                min={minDate}
                max={maxDate}
                data-testid="etr-date-decision"
                onChange={(e) => setDateAG(e.target.value)}
                onBlur={() => setRevealErrors(true)}
                error={showErr1('dateAG')}
              />
              <Select
                label="Type de décision *"
                value={typeAssemblee}
                data-testid="etr-type-assemblee"
                onChange={(e) => setTypeAssemblee(e.target.value as 'ordinaire' | 'extraordinaire')}
                options={[
                  { value: 'extraordinaire', label: 'Extraordinaire' },
                  { value: 'ordinaire', label: 'Ordinaire' },
                ]}
              />
              <div className="rounded-lg border border-indigo-200 bg-accent/10 p-3 text-xs text-fg-muted">
                Organe détecté : <strong>{isAU ? 'organe unipersonnel' : 'organe collégial'}</strong>.
                Acte produit : procès-verbal de création de succursale par une société étrangère
                {isAU ? ' (décision unipersonnelle)' : ' (décision collégiale)'}.
              </div>
            </div>

            <section
              className="space-y-3 rounded-xl border border-border bg-bg-raised p-4"
              data-testid="succursale-etr-convocation"
            >
              <div className="flex items-center gap-2">
                <Megaphone className="h-4 w-4 text-accent" />
                <h4 className="text-sm font-semibold text-fg">Convocation (facultative)</h4>
              </div>
              <div className="grid gap-3 md:grid-cols-2">
                <TextField
                  label="Date de convocation"
                  type="date"
                  value={convocationDate}
                  data-testid="succursale-etr-convocation-date"
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
                Règle des <strong>{CONVOCATION_DELAI_JOURS} jours</strong> : la réunion ne peut se
                tenir moins de {CONVOCATION_DELAI_JOURS} jours calendaires après la convocation.
                Laissez vide si aucune convocation n’est émise.
              </p>
              {convocationError && (
                <div
                  role="alert"
                  data-testid="succursale-etr-convocation-16j-error"
                  className="flex items-start gap-2 rounded-lg border border-danger/40 bg-danger/10 p-2 text-xs text-danger"
                >
                  <AlertTriangle className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" />
                  <span>{convocationError}</span>
                </div>
              )}
              {/*
                Ce workflow capturait une convocation (date + heure, regle des 16
                jours) sans jamais offrir de la GENERER : c'etait le seul des
                workflows a convocation dans ce cas. Le panneau est ajoute ici, en
                mode integre — la section porte deja le titre — et alimente par les
                champs ci-dessus : rien n'est re-saisi.
              */}
              {/*
                Fix E1 (2026-08-16) — LA CONVOCATION N'ÉTAIT PAS PERSISTÉE.
                Le panneau visait `dossierMereId`, qui n'est renseigné QUE si l'on
                réutilise un dossier existant. Pour une mère étrangère nouvelle, le
                dossier est créé à la validation de l'étape 1 et son id vit dans
                `step1.dossierMereEtrangereId` — c'est-à-dire `dossierMereEffectifId`.
                Le panneau ne le lisait pas : il n'avait donc aucune cible et le
                document généré n'atterrissait nulle part. On vise désormais l'id
                effectif, et on prévient quand la cible n'existe pas encore.
              */}
              {!dossierMereEffectifId && !ticket?.dossierId && (
                <div
                  role="status"
                  data-testid="succursale-etr-convocation-sans-dossier"
                  className="rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-900"
                >
                  Validez l'étape 1 avant de générer la convocation : le dossier de la
                  société mère est créé à ce moment-là, et c'est lui qui reçoit le
                  document en Data Room.
                </div>
              )}
              <ConvocationPanel
                dossierId={dossierMereEffectifId || ticket?.dossierId}
                ticketId={ticket?.id}
                formeJuridique={formeJuridique}
                societe={societeInput}
                defaultSeance={{
                  type: typeAssemblee,
                  date: dateAG,
                  convDate: convocationDate,
                  convHeure: convocationHeure,
                }}
                initialOrdreDuJour={ORDRE_DU_JOUR_OUVERTURE_SUCCURSALE_ETR}
                hideSeanceDate
                hideConvDate
                embedded
                versioned
                motif={depositMotif}
                filenameFor={workflowFilenameFor}
              />
            </section>

            <section
              className="space-y-3 rounded-xl border border-border bg-bg-raised p-4"
              data-testid="succursale-etr-conformite"
            >
              <div className="flex items-center gap-2">
                <ShieldCheck className="h-4 w-4 text-warning" />
                <h4 className="text-sm font-semibold text-fg">
                  Contrôles de conformité (bloquants)
                </h4>
              </div>
              <p className="text-[11px] text-fg-subtle">
                Ces six contrôles portent sur les pièces de la société mère : sans eux, le dossier
                n’est pas recevable au greffe. La plateforme les vérifie à nouveau à la validation.
              </p>
              <div className="grid gap-2 md:grid-cols-2">
                {CONTROLES.map((c) => (
                  <label key={c.key} className="flex items-start gap-2 text-sm text-fg-muted">
                    <input
                      type="checkbox"
                      checked={controles[c.key]}
                      data-testid={`etr-${c.key}`}
                      onChange={(e) => setControles({ ...controles, [c.key]: e.target.checked })}
                      className="mt-0.5 h-4 w-4 rounded border-border-hi"
                    />
                    {c.label}
                  </label>
                ))}
              </div>
              {showErr1('conformite') && (
                <p className="flex items-start gap-1 text-xs text-danger" role="alert">
                  <AlertCircle className="mt-0.5 h-3 w-3 flex-shrink-0" />
                  {showErr1('conformite')}
                </p>
              )}
            </section>
          </div>
        )}

        {step === 2 && (
          <div className="space-y-5" data-testid="succursale-etr-step2">
            <header>
              <div className="flex items-center gap-2">
                <Building2 className="h-5 w-5 text-accent" />
                <h2 className="text-lg font-semibold text-fg">Étape 2 — Données de la succursale</h2>
              </div>
              <p className="text-sm text-fg-subtle">
                Saisie <strong>identique</strong> à celle d’une succursale de société marocaine.
                Ces informations alimentent à la fois le procès-verbal et l’annonce légale : elles ne
                sont saisies qu’ici.
              </p>
            </header>

            <SuccursaleSaisieForm
              value={succursale}
              onChange={setSuccursale}
              parties={null}
              dossierId={dossierMereEffectifId}
              errors={revealErrors ? step2Errors : {}}
              roleLabel="Représentant résident au Maroc"
            />

            <section className="space-y-3 rounded-xl border border-border bg-bg-raised p-4">
              <div className="flex items-center gap-2">
                <Sparkles className="h-4 w-4 text-violet-600" />
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
          <div className="space-y-4" data-testid="succursale-etr-step3">
            <header>
              <div className="flex items-center gap-2">
                <Sparkles className="h-5 w-5 text-violet-600" />
                <h2 className="text-lg font-semibold text-fg">Étape 3 — Génération des actes</h2>
              </div>
              <p className="text-sm text-fg-subtle">
                Le <strong>procès-verbal de création</strong> et l’<strong>annonce légale d’ouverture</strong>
                {' '}sont produits à partir des étapes 1 et 2 : aucune donnée n’est ressaisie.
              </p>
            </header>

            <div
              className="flex items-start gap-2 rounded-lg border border-warning/40 bg-warning/10 p-3 text-xs text-fg-muted"
              role="status"
              data-testid="etr-annonce-derive-warning"
            >
              <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0 text-warning" />
              <span>
                <strong>Modèle dérivé, non fourni par le directeur.</strong> Le directeur n’a livré
                l’avis d’ouverture que pour une société mère <em>marocaine</em>. La variante
                étrangère reprend sa structure et sa formulation mot pour mot, en remplaçant le
                seul chapeau d’identification par les données de la société mère.
                <strong> À faire valider par le directeur</strong> avant publication.
              </span>
            </div>

            <SuccursaleOperationForm
              mode="creation-etr"
              dossierId={dossierMereEffectifId}
              ticketId={ticket?.id}
              formeJuridique={formeJuridique}
              societe={societeInput}
              defaultDate={dateAG}
              succursaleSeed={succursaleSeed}
              mereSeed={mereEffective}
              denominationForFilename={targetDenomination}
              locked
              onReady={setPvValidated}
              versioned
              depositMotif={depositMotif}
              filenameFor={workflowFilenameFor}
            />

            <SuccursaleAnnoncePanel
              mode="ouverture-etrangere"
              dossierId={dossierMereEffectifId}
              ticketId={ticket?.id}
              formeJuridique={formeJuridique}
              denomination={targetDenomination}
              dateAssemblee={dateAG}
              typeAssemblee={typeAssemblee}
              societeMere={merePayload()}
              organeCompetent={organeCompetent}
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
          </div>
        )}

        {step === 4 && (
          <div className="space-y-4" data-testid="succursale-etr-step4-pieces">
            <header>
              <div className="flex items-center gap-2">
                <Paperclip className="h-5 w-5 text-accent" />
                <h2 className="text-lg font-semibold text-fg">
                  Étape 4 — Pièces jointes (facultative)
                </h2>
              </div>
              <p className="text-sm text-fg-subtle">
                Déposez ici les <strong>versions légalisées</strong> : statuts apostillés, traduction
                certifiée, procuration, procès-verbal signé, annonce publiée. Cette étape est{' '}
                <strong>entièrement facultative</strong>.
              </p>
            </header>
            <PiecesJointesPanel
              dossierId={dossierMereEffectifId}
              ticketId={ticket?.id}
              denomination={targetDenomination}
              forme={mereEffective.forme || 'Étrangère'}
              motif={depositMotif}
              onDeposited={(entry) =>
                setPiecesJointes((prev) => {
                  const next = prev.filter((p) => p.id !== entry.id);
                  next.push(entry);
                  return next;
                })
              }
            />
            {dossierMereEffectifId ? (
              <p className="rounded-lg border border-border bg-bg-overlay p-3 text-xs text-fg-subtle">
                Les dépôts sont classés dans la Data Room de la société mère{' '}
                <strong>{targetDenomination || '—'}</strong>, créée dès la validation de
                l’étape 1.
              </p>
            ) : (
              <p className="rounded-lg border border-warning/40 bg-warning/10 p-3 text-xs text-fg-muted">
                Validez d’abord l’étape 1 : c’est elle qui crée (ou réutilise) la Data Room de la
                société mère, destination de ces dépôts.
              </p>
            )}
            {piecesJointes.length === 0 && (
              <p className="rounded-lg border border-border bg-bg-overlay p-3 text-xs text-fg-subtle">
                Aucune pièce jointe déposée pour le moment — cette étape est facultative.
              </p>
            )}
          </div>
        )}

        {step === 5 && (
          <div className="space-y-4" data-testid="succursale-etr-synthese">
            <div className="rounded-xl bg-gradient-to-r from-accent to-indigo-600 p-6 text-bg-raised">
              <CheckCircle className="mb-2 h-8 w-8" />
              <h2 className="text-2xl font-bold">Succursale créée</h2>
              <p className="text-sm text-indigo-100">
                Le procès-verbal de création et l’annonce légale d’ouverture ont été produits et validés.
              </p>
            </div>

            <div className="rounded-xl border border-border bg-bg-raised p-4">
              <p className="mb-3 text-sm font-semibold text-fg">Société mère étrangère</p>
              <div className="grid gap-4 rounded-lg bg-bg-overlay p-3 sm:grid-cols-2">
                <Field label="Dénomination" value={mereEffective.denomination} />
                <Field label="Forme d’origine" value={mereEffective.forme} />
                <Field label="Pays" value={mereEffective.pays} />
                <Field label="Capital" value={mereEffective.capital} />
                <Field label="Siège social" value={mereEffective.siege} className="sm:col-span-2" />
                <Field
                  label="Registre"
                  value={[mereEffective.registre, mereEffective.registreNumero]
                    .filter(Boolean)
                    .join(' n° ')}
                />
                <Field label="Loi applicable" value={mereEffective.loiApplicable} />
                <Field label="Organe décisionnaire" value={organeCompetent} />
                <Field label="Date de la décision" value={dateAG} />
              </div>
            </div>

            <div className="rounded-xl border border-border bg-bg-raised p-4">
              <p className="mb-3 text-sm font-semibold text-fg">Succursale au Maroc</p>
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
                    succursale.dotationPresente ? `${succursale.dotationMontant} MAD` : 'Aucune'
                  }
                />
              </div>
            </div>

            <div className="rounded-xl border border-border bg-bg-raised p-4 text-sm text-fg-muted">
              <p>
                <strong>Data Room de la société mère :</strong>{' '}
                {reuseExisting
                  ? 'réutilisée (mère déjà enregistrée).'
                  : 'créée dès la validation de l’étape 1 — la société mère est un dossier à part entière, où sont classés le procès-verbal, l’annonce et les pièces.'}
              </p>
              <p className="mt-1">
                <strong>Immatriculation :</strong> la succursale doit être immatriculée au registre
                du commerce de{' '}
                <strong>{succursale.villeGreffe || succursale.ville || '—'}</strong> dans les trois
                mois de son ouverture (loi 15-95, art. 75).
              </p>
              <p className="mt-1">
                <strong>N° RC et ICE de la succursale :</strong> attribués après le dépôt au greffe
                — à saisir dans les identifiants du dossier, pas ici.
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
  return (
    <div className="flex h-64 items-center justify-center">
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-border border-t-indigo-600" />
    </div>
  );
}
