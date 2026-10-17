/**
 * Workflow FERMETURE DE SUCCURSALE en 4 étapes — spec directeur, lot DIVERS §D (2026-08-13).
 *
 *  1. Sélection — société mère (identité BD) puis **succursale choisie dans la liste**
 *     des succursales ACTIVE : enseigne, adresse, ville, activité et **RC** sont repris
 *     de la BD en LECTURE SEULE. S'ajoutent la date d'assemblée + son TYPE, la convocation
 *     OPTIONNELLE (16 jours), la **date d'effet de la fermeture** et le **motif**
 *     (obligatoire : il est publié dans l'annonce).
 *  2. Génération — PV de fermeture + annonce légale de fermeture ; optionnels non bloquants.
 *  3. Pièces jointes — dépôt des versions légalisées (OPTIONNELLE).
 *  4. Synthèse — récapitulatif ; la succursale passe FERMEE en base (date d'effet + motif).
 *
 * ANTI-DUPLICATION : c'est le workflow de référence cité par la spec. Rien de ce qui est
 * en base n'est re-saisi. Seule exception assumée : le **RC**, qui depuis la refonte des
 * ouvertures (§B/§C) n'est plus capturé à la création (le greffe l'attribue après dépôt).
 * S'il manque, il se saisit UNE fois ici et est persisté sur la succursale.
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AlertCircle,
  AlertTriangle,
  Megaphone,
  Paperclip,
  Sparkles,
  Store,
} from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { TextField, ZoneTexte } from '../../components/ui/TextField';
import { Select } from '../../components/ui/Select';
import { WorkflowShell } from '../../components/workflow/WorkflowShell';
import type { RoadmapStep } from '../../components/workflow/WorkflowRoadmap';
import { ticketService } from '../../services/ticket.service';
import { workflowService, type SuccursaleSummary } from '../../services/workflow.service';
import { extractError } from '../../lib/api';
import { CancelTicketDialog } from '../tickets/CancelTicketDialog';
import { useWorkflow } from './useWorkflow';
import { useStepAutosave } from './useStepAutosave';
import { WorkflowBoot } from './WorkflowBoot';
import { AideEtape } from './AideEtape';
import { ETAPES_FERMETURE_SUCCURSALE, etapesRoadmap } from './aidesParcours';
import { bornesDateSeance, useDossierParties, useEtatReinitialise } from './parcoursHooks';
import { DossierAutocomplete } from '../../components/workflow/DossierAutocomplete';
import { SuccursaleOperationForm } from '../../components/workflow/SuccursaleOperationForm';
import { SuccursaleAnnoncePanel } from '../../components/workflow/SuccursaleAnnoncePanel';
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

const STEPS: RoadmapStep[] = etapesRoadmap(ETAPES_FERMETURE_SUCCURSALE);

const ALLOWED_STATUTS_MERE = ['ACTIVE'];
/** Miroir du backend `FermetureSuccursaleWorkflow.MOTIF_MIN_LENGTH`. */
const MOTIF_MIN_LENGTH = 10;

/** Libellé « type de document » pour le nommage `type - dénomination - forme (- v<n>)`. */
function fermetureSuccursaleDocType(code: string): string {
  if (code.startsWith('ANNONCE_LEGALE_FERMETURE_SUCCURSALE')) {
    return 'Annonce légale — Fermeture de succursale';
  }
  if (code.startsWith('PV_FERMETURE_SUCCURSALE')) return 'PV — Fermeture de succursale';
  if (code.startsWith('CONVOCATION')) return 'Convocation';
  if (code.startsWith('FEUILLE_PRESENCE')) return 'Feuille de présence';
  if (code.includes('DEFAUT_QUORUM')) return 'PV — Défaut de quorum';
  if (code.includes('IRREGULARITE')) return 'PV — Irrégularité de convocation';
  return code;
}

/**
 * Monte {@link FermetureSuccursaleWorkflowPageBody} SOUS {@link WorkflowBoot} : les champs de chaque
 * etape s'initialisent avec `useState(stepData...)`, qui ne lit sa valeur qu'au
 * premier render. Sans ce montage differe, ce premier render a lieu AVANT la
 * reponse du serveur et tous les champs restent vides apres un rechargement
 * (F5, deconnexion/reconnexion), meme sur une etape deja validee.
 */
/**
 * Ordre du jour de l'assemblée décidant la fermeture d'une succursale — il était
 * vide. Amorce éditable, pas un verrou.
 */
const ORDRE_DU_JOUR_FERMETURE_SUCCURSALE = [
  'Fermeture de la succursale et cessation de son activité',
  'Fixation de la date d\'effet de la fermeture',
  'Radiation de la succursale au Registre du Commerce de son lieu d\'exploitation',
  'Pouvoirs en vue des formalités légales',
];

export function FermetureSuccursaleWorkflowPage() {
  return (
    <WorkflowBoot>
      <FermetureSuccursaleWorkflowPageBody />
    </WorkflowBoot>
  );
}

function FermetureSuccursaleWorkflowPageBody() {
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

  // ---------- Étape 1 : sélection ----------
  const initialMereId =
    (stepData.step1?.dossierId as string) ??
    (stepData.step1?.societeMereId as string) ??
    ticket?.dossierId ??
    '';
  const [dossierMereId, setDossierMereId] = useState(initialMereId);
  const [dossierMere, setDossierMere] = useState<DossierBrief | null>(null);

  // Succursales lues pour la societe mere `id` (erreur de lecture comprise).
  const [succLu, setSuccLu] = useState<{ id: string; items: SuccursaleSummary[]; err: string | null } | null>(null);
  const [succursaleDbId, setSuccursaleDbId] = useState(
    (stepData.step1?.succursaleDbId as string) ?? '',
  );
  /**
   * Saisie manuelle : la succursale n'est pas au referentiel (exploitee avant que la
   * table `succursales` n'existe). Le backend la CREE alors en base a la validation de
   * l'etape 1, puis la ferme normalement — c'est la fin de la « fermeture fantome », ou
   * le PV et l'annonce etaient produits sans qu'aucune succursale ne soit jamais fermee.
   */
  const [saisieManuelle, setSaisieManuelle] = useState(false);

  const persistedSucc = (stepData.step1?.succursale as Record<string, string> | undefined) ?? {};
  const [enseigne, setEnseigne] = useState(persistedSucc.enseigne ?? '');
  const [adresse, setAdresse] = useState(persistedSucc.adresse ?? '');
  const [ville, setVille] = useState(persistedSucc.ville ?? '');
  const [villeGreffe, setVilleGreffe] = useState(persistedSucc.villeGreffe ?? '');
  const [activite, setActivite] = useState(persistedSucc.activite ?? '');
  const [rcNumero, setRcNumero] = useState(persistedSucc.rcNumero ?? '');
  /** Le RC vient-il de la BD ? Si oui il est verrouillé ; sinon saisie de secours. */
  const [rcFromBase, setRcFromBase] = useState(false);

  const [dateFermeture, setDateFermeture] = useState(
    (persistedSucc.dateFermeture as string) ?? (stepData.step1?.dateFermeture as string) ?? '',
  );
  const [motif, setMotif] = useState(
    (persistedSucc.motif as string) ?? (stepData.step1?.motif as string) ?? '',
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
  const [convocationSnapshot, setConvocationSnapshot] = useState<Record<string, unknown> | null>(
    (stepData.step1?.convocationSnapshot as Record<string, unknown>) ?? null,
  );
  const [mandataire, setMandataire] = useState(
    (stepData.step1?.formalitesMandataireNom as string) ?? '',
  );
  // Remises a zero a chaque changement d'etape.
  const [revealErrors, setRevealErrors] = useEtatReinitialise(viewStep, () => false);

  // ---------- Étape 2 : génération ----------
  const [pvValidated, setPvValidated] = useState<boolean>(!!(stepData.step2?.pvValide as boolean));
  const [annonceValidated, setAnnonceValidated] = useState<boolean>(
    !!(stepData.step2?.annonceValide as boolean),
  );
  const [depotLegalNumero, setDepotLegalNumero] = useState(
    ((stepData.step2?.depotLegal as { numero?: string } | undefined) ?? {}).numero ?? '',
  );
  const [depotLegalDate, setDepotLegalDate] = useState(
    ((stepData.step2?.depotLegal as { date?: string } | undefined) ?? {}).date ?? '',
  );

  // ---------- Étape 3 : pièces jointes ----------
  const [piecesJointes, setPiecesJointes] = useState<PieceJointeEntry[]>(
    () => (stepData.step3?.piecesJointes as PieceJointeEntry[] | undefined) ?? [],
  );

  // Succursales ACTIVE de la mère : c'est LA source de vérité de l'étape 1.
  useEffect(() => {
    if (!dossierMereId) return;
    let cancelled = false;
    workflowService
      .listSuccursales(dossierMereId)
      .then((items) => {
        if (cancelled) return;
        const actives = items.filter((s) => s.statut === 'ACTIVE');
        setSuccLu({ id: dossierMereId, items: actives, err: null });
        // Aucune succursale au referentiel -> saisie manuelle d'office : sans elle,
        // l'employe ne pourrait pas fermer une succursale legacy.
        if (actives.length === 0) setSaisieManuelle(true);
      })
      .catch((e) => {
        if (!cancelled) setSuccLu({ id: dossierMereId, items: [], err: extractError(e).message });
      });
    return () => {
      cancelled = true;
    };
  }, [dossierMereId]);
  const succAJour = !!dossierMereId && succLu?.id === dossierMereId;
  const succursales = succAJour ? succLu.items : [];
  const loadingSucc = !!dossierMereId && !succAJour;
  const succErr = succAJour ? succLu.err : null;

  // Parties prenantes (convocation / feuille de présence) — aucune re-saisie d'identité.
  const { dossierParties } = useDossierParties(dossierMereId);

  /** Sélection d'une succursale : tous les champs connus sont repris de la BD. */
  function applySuccursale(id: string) {
    setSuccursaleDbId(id);
    if (id) setSaisieManuelle(false);
    const s = succursales.find((x) => x.id === id);
    if (!s) {
      setEnseigne('');
      setAdresse('');
      setVille('');
      setVilleGreffe('');
      setActivite('');
      setRcNumero('');
      setRcFromBase(false);
      return;
    }
    setEnseigne(s.denomination ?? '');
    setAdresse(s.adresse ?? '');
    setVille(s.ville ?? '');
    setVilleGreffe(s.ville ?? '');
    setActivite(s.activite ?? '');
    setRcNumero(s.rcSecondaire ?? '');
    // Le RC est verrouillé s'il existe en base ; sinon saisie de secours (une fois).
    setRcFromBase(!!s.rcSecondaire);
  }

  const formeJuridiqueRaw = useMemo(
    () =>
      (dossierMere?.formeJuridique as string | null | undefined) ??
      (stepData.step1?.formeJuridique as string | undefined) ??
      null,
    [dossierMere, stepData.step1?.formeJuridique],
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

  const convocationError = useMemo(
    () => convocationDelaiError(convocationDate, dateAG),
    [convocationDate, dateAG],
  );

  type Step1Errors = {
    dossierMereId?: string;
    succursale?: string;
    rcNumero?: string;
    dateFermeture?: string;
    motif?: string;
    dateAG?: string;
    convocation?: string;
  };
  const step1Errors = useMemo<Step1Errors>(() => {
    const e: Step1Errors = {};
    if (!dossierMereId) e.dossierMereId = 'Sélectionnez la société mère propriétaire de la succursale.';
    else if (saisieManuelle) {
      // Saisie manuelle : minimum permettant d'enregistrer PUIS de fermer la succursale.
      if (!enseigne.trim() || !ville.trim()) {
        e.succursale =
          'Renseignez au moins l’enseigne et la ville de la succursale : elle sera '
          + 'enregistrée, puis fermée.';
      }
    } else if (!succursaleDbId) e.succursale = 'Sélectionnez la succursale à fermer.';
    if (!rcNumero.trim()) {
      e.rcNumero =
        'Le numéro RC de la succursale est obligatoire : il est publié dans l’annonce.';
    }
    if (!dateFermeture) e.dateFermeture = 'La date d’effet de la fermeture est obligatoire.';
    if (motif.trim().length < MOTIF_MIN_LENGTH) {
      e.motif = `Motif trop court (${motif.trim().length}/${MOTIF_MIN_LENGTH} caractères minimum) : il est publié dans l’annonce.`;
    }
    if (!dateAG) e.dateAG = 'La date de l’assemblée est obligatoire.';
    if (convocationError) e.convocation = convocationError;
    return e;
  }, [
    dossierMereId,
    succursaleDbId,
    saisieManuelle,
    enseigne,
    ville,
    rcNumero,
    dateFermeture,
    motif,
    dateAG,
    convocationError,
  ]);

  const targetDenomination = dossierMere?.raisonSociale ?? ticket?.titre ?? '';

  const workflowFilenameFor = useCallback(
    (tpl: TemplateInfo, opts?: { version?: number }) =>
      buildDocFilename(
        fermetureSuccursaleDocType(tpl.code),
        targetDenomination,
        formeLabel(formeJuridiqueRaw),
        opts?.version,
      ),
    [targetDenomination, formeJuridiqueRaw],
  );

  const depositMotif = useMemo(
    () => `Fermeture de succursale : ${enseigne || 'succursale'}`,
    [enseigne],
  );

  /** Bloc succursale partagé PV ↔ annonce (source unique : l'étape 1). */
  const succursalePayload = useMemo(
    () => ({
      enseigne: enseigne.trim(),
      adresse: adresse.trim(),
      ville: ville.trim(),
      villeGreffe: (villeGreffe || ville).trim(),
      activite: activite.trim(),
      rcNumero: rcNumero.trim(),
      dateFermeture,
      motif: motif.trim(),
    }),
    [enseigne, adresse, ville, villeGreffe, activite, rcNumero, dateFermeture, motif],
  );

  const succursaleSeed = useMemo(
    () => ({
      enseigne: succursalePayload.enseigne,
      adresse: succursalePayload.adresse,
      ville: succursalePayload.ville,
      villeGreffe: succursalePayload.villeGreffe,
      activite: succursalePayload.activite,
      rcNumero: succursalePayload.rcNumero,
      dateFermeture: succursalePayload.dateFermeture,
      motif: succursalePayload.motif,
      responsablePresent: false,
    }),
    [succursalePayload],
  );

  const convocationPayload = useCallback(() => {
    if (!convocationDate) return undefined;
    return { date: convocationDate, heure: convocationHeure || undefined };
  }, [convocationDate, convocationHeure]);

  const draftPayloadFor = useCallback(
    (n: number): Record<string, unknown> | null => {
      if (n === 1) {
        return {
          dossierId: dossierMereId,
          societeMereId: dossierMereId,
          succursaleDbId,
          succursale: succursalePayload,
          dateAG,
          assembleeNature: typeAssemblee,
          convocation: convocationPayload(),
          convocationSnapshot,
          formeJuridique: formeJuridiqueRaw,
          formalitesMandataireNom: mandataire,
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
    [dossierMereId, succursaleDbId, succursalePayload, dateAG, typeAssemblee,
      convocationPayload, convocationSnapshot, formeJuridiqueRaw,
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
        dossierId: dossierMereId,
        succursaleDbId,
        succursale: succursalePayload,
        dateAG,
        typeAssemblee,
        convocation: convocationPayload(),
        formalitesMandataireNom: mandataire || undefined,
      });
    } else if (step === 2) {
      if (!pvValidated) {
        setError('Générez puis validez le procès-verbal de fermeture avant de continuer.');
        return;
      }
      if (!annonceValidated) {
        setError(
          'Générez puis validez l’annonce légale de fermeture : sa publication fonde la '
            + 'radiation de la succursale.',
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

  const societeInput = {
    // Identite complete : sans elle, les formulaires de seance laissaient vides le
    // lieu de l'assemblee (defaut = siege social) et le lieu de signature.
    denomination: targetDenomination,
    villeGreffe: dossierParties?.villeGreffe || dossierMere?.ville,
    siegeSocial: dossierParties?.siegeSocial,
    capitalChiffres: dossierParties?.capitalSocial,
    nombreParts: dossierParties?.nombreParts,
    rcNumero: dossierParties?.rcNumero,
  };

  const decisionLabel = isAU
    ? 'Décision de l’associé unique'
    : typeAssemblee === 'ordinaire'
      ? 'Assemblée générale ordinaire (AGO)'
      : 'Assemblée générale extraordinaire (AGE)';

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
          parcours="fermeture-succursale"
          etapes={ETAPES_FERMETURE_SUCCURSALE}
          etape={step}
          manquants={step === 1 ? (Object.values(step1Errors).filter(Boolean) as string[]) : undefined}
        />

        {step === 1 && (
          <div className="space-y-5" data-testid="fermeture-step1">
            <header>
              <div className="flex items-center gap-2">
                <Store className="h-5 w-5 text-fg-muted" />
                <h2 className="text-lg font-semibold text-fg">Étape 1 — Succursale à fermer</h2>
              </div>
              <p className="text-sm text-fg-subtle">
                Choisissez la société mère, puis la succursale dans la liste : son enseigne, son
                adresse, sa ville, son activité et son RC sont <strong>repris du dossier</strong> et
                verrouillés. Vous ne saisissez que ce qui est propre à la fermeture.
              </p>
            </header>

            <div>
              <DossierAutocomplete
                value={dossierMereId}
                label="Société mère"
                onSelect={(d) => {
                  setDossierMere(d);
                  setDossierMereId(d?.id ?? '');
                  applySuccursale('');
                }}
                allowedStatuts={ALLOWED_STATUTS_MERE}
                help="L’identité de la société (dénomination, capital, siège, RC) est lue dans le dossier."
              />
              {showErr1('dossierMereId') && (
                <p className="mt-1 flex items-start gap-1 text-xs text-danger" role="alert">
                  <AlertCircle className="mt-0.5 h-3 w-3 flex-shrink-0" />
                  {showErr1('dossierMereId')}
                </p>
              )}
            </div>

            {dossierMereId && (
              <section className="space-y-3 rounded-xl border border-border bg-bg-raised p-4">
                {/* Liste masquee quand la societe n'a aucune succursale enregistree :
                    un selecteur vide n'aiderait pas, la saisie manuelle prend le relais. */}
                {(loadingSucc || succursales.length > 0) && (
                <Select
                  label="Succursale à fermer *"
                  value={succursaleDbId}
                  data-testid="fermeture-succursale-select"
                  onChange={(e) => applySuccursale(e.target.value)}
                  error={showErr1('succursale')}
                  options={[
                    {
                      value: '',
                      label: loadingSucc ? 'Chargement…' : '— Sélectionner une succursale —',
                    },
                    ...succursales.map((s) => ({
                      value: s.id,
                      label: `${s.denomination ?? 'Succursale'} — ${s.ville ?? '?'}${
                        s.rcSecondaire ? ` (RC ${s.rcSecondaire})` : ' (RC à renseigner)'
                      }`,
                    })),
                  ]}
                />
                )}
                {succErr && (
                  <p className="text-xs text-danger" role="alert">
                    {succErr}
                  </p>
                )}
                {!loadingSucc && succursales.length === 0 && (
                  <div
                    className="flex items-start gap-2 rounded-lg border border-warning/40 bg-warning-soft p-3 text-xs text-fg-muted"
                    role="status"
                    data-testid="fermeture-aucune-succursale"
                  >
                    <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0 text-warning" />
                    <span>
                      Aucune succursale <strong>active</strong> n’est enregistrée pour cette société —
                      celles ouvertes avant leur enregistrement dans la plateforme n’y figurent pas.
                      Saisissez-la ci-dessous : elle sera <strong>enregistrée puis fermée</strong>, et
                      apparaîtra désormais dans l’historique de la société.
                    </span>
                  </div>
                )}
                {!loadingSucc && succursales.length > 0 && !saisieManuelle && (
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    className="self-start"
                    data-testid="fermeture-basculer-manuel"
                    onClick={() => {
                      setSaisieManuelle(true);
                      applySuccursale('');
                    }}
                  >
                    La succursale ne figure pas dans la liste : la saisir manuellement
                  </Button>
                )}
                {!loadingSucc && succursales.length > 0 && saisieManuelle && (
                  <Button type="button" variant="ghost" size="sm" className="self-start" onClick={() => setSaisieManuelle(false)}>
                    Revenir à la sélection dans la liste
                  </Button>
                )}

                {saisieManuelle ? (
                  <div
                    className="grid gap-4 rounded-lg bg-bg-overlay p-3 sm:grid-cols-2"
                    data-testid="fermeture-saisie-manuelle"
                  >
                    <TextField
                      label="Enseigne de la succursale *"
                      value={enseigne}
                      data-testid="fermeture-enseigne"
                      onChange={(e) => setEnseigne(e.target.value)}
                      onBlur={() => setRevealErrors(true)}
                      error={showErr1('succursale')}
                    />
                    <TextField
                      label="Activité"
                      value={activite}
                      onChange={(e) => setActivite(e.target.value)}
                    />
                    <TextField
                      label="Adresse"
                      value={adresse}
                      className="sm:col-span-2"
                      onChange={(e) => setAdresse(e.target.value)}
                    />
                    <TextField
                      label="Ville *"
                      value={ville}
                      data-testid="fermeture-ville"
                      onChange={(e) => {
                        setVille(e.target.value);
                        // Le greffe competent est celui du lieu d'exploitation : par
                        // defaut la ville de la succursale, jamais celle de la mere.
                        if (!villeGreffe) setVilleGreffe(e.target.value);
                      }}
                      onBlur={() => setRevealErrors(true)}
                    />
                    <TextField
                      label="Greffe de la succursale"
                      value={villeGreffe}
                      onChange={(e) => setVilleGreffe(e.target.value)}
                      hint="Par défaut, la ville de la succursale."
                    />
                    <TextField
                      label="N° RC de la succursale *"
                      value={rcNumero}
                      data-testid="fermeture-rc"
                      onChange={(e) => setRcNumero(e.target.value)}
                      error={showErr1('rcNumero')}
                      hint="Publié dans l’annonce (« immatriculée sous le n° … »)."
                    />
                  </div>
                ) : (
                  succursaleDbId && (
                    <div className="grid gap-4 rounded-lg bg-bg-overlay p-3 sm:grid-cols-2">
                      <Recap label="Enseigne" value={enseigne} />
                      <Recap label="Activité" value={activite} />
                      <Recap label="Adresse" value={adresse} className="sm:col-span-2" />
                      <Recap label="Ville" value={ville} />
                      <Recap label="Greffe de la succursale" value={villeGreffe || ville} />
                      {rcFromBase ? (
                        <Recap label="N° RC de la succursale" value={rcNumero} />
                      ) : (
                        <TextField
                          label="N° RC de la succursale *"
                          value={rcNumero}
                          data-testid="fermeture-rc"
                          onChange={(e) => setRcNumero(e.target.value)}
                          error={showErr1('rcNumero')}
                          hint="Non enregistré dans le dossier (attribué par le greffe après l’ouverture) : saisissez-le une fois, il sera conservé."
                        />
                      )}
                    </div>
                  )
                )}
              </section>
            )}

            <div className="grid gap-3 md:grid-cols-2">
              <TextField
                label="Date d’effet de la fermeture *"
                type="date"
                value={dateFermeture}
                data-testid="fermeture-date-effet"
                onChange={(e) => setDateFermeture(e.target.value)}
                onBlur={() => setRevealErrors(true)}
                error={showErr1('dateFermeture')}
                hint="Date à laquelle la succursale cesse son activité (publiée dans l’annonce)."
              />
              <TextField
                label={`Date ${isAU ? 'de la décision' : 'de l’assemblée'} *`}
                type="date"
                value={dateAG}
                min={minDate}
                max={maxDate}
                data-testid="fermeture-date-ag"
                onChange={(e) => setDateAG(e.target.value)}
                onBlur={() => setRevealErrors(true)}
                error={showErr1('dateAG')}
              />
              <Select
                label="Type d’assemblée *"
                value={typeAssemblee}
                data-testid="fermeture-type-assemblee"
                onChange={(e) => setTypeAssemblee(e.target.value as 'ordinaire' | 'extraordinaire')}
                options={[
                  { value: 'extraordinaire', label: 'Extraordinaire (AGE)' },
                  { value: 'ordinaire', label: 'Ordinaire (AGO)' },
                ]}
                hint={`Organe compétent : ${decisionLabel}.`}
              />
            </div>

            <div>
              <ZoneTexte
                id="fermeture-motif"
                label="Motif de la fermeture *"
                rows={3}
                value={motif}
                data-testid="fermeture-motif"
                onChange={(e) => setMotif(e.target.value)}
                onBlur={() => setRevealErrors(true)}
                placeholder="Cessation de l’activité exercée dans la succursale, réorganisation, transfert de l’activité au siège…"
                aria-invalid={!!showErr1('motif')}
                aria-describedby="fermeture-motif-compte"
              />
              <p
                id="fermeture-motif-compte"
                className={`mt-1 text-xs ${
                  motif.trim().length < MOTIF_MIN_LENGTH ? 'text-danger' : 'text-success'
                }`}
              >
                {motif.trim().length} / {MOTIF_MIN_LENGTH} caractères minimum
                {motif.trim().length >= MOTIF_MIN_LENGTH && ' ✓'}
              </p>
              <p className="mt-1 text-[11px] text-fg-subtle">
                Contrairement à la dissolution, le motif est <strong>publié</strong> dans l’annonce
                légale : « Cette fermeture est motivée par … ».
              </p>
              {showErr1('motif') && (
                <p className="mt-1 flex items-start gap-1 text-xs text-danger" role="alert">
                  <AlertCircle className="mt-0.5 h-3 w-3 flex-shrink-0" />
                  {showErr1('motif')}
                </p>
              )}
            </div>

            <section
              className="space-y-3 rounded-xl border border-border bg-bg-raised p-4"
              data-testid="fermeture-convocation"
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
                  data-testid="fermeture-convocation-date"
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
                  data-testid="fermeture-convocation-16j-error"
                  className="flex items-start gap-2 rounded-lg border border-danger/40 bg-danger-soft p-2 text-xs text-danger"
                >
                  <AlertTriangle className="mt-0.5 h-3.5 w-3.5 flex-shrink-0" />
                  <span>{convocationError}</span>
                </div>
              )}
              <ConvocationPanel
                dossierId={dossierMereId || ticket?.dossierId}
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
                initialOrdreDuJour={ORDRE_DU_JOUR_FERMETURE_SUCCURSALE}
                embedded
                initialAssocies={bdAssocies}
                initialGerants={bdGerants}
                initialSnapshot={convocationSnapshot}
                onSnapshot={setConvocationSnapshot}
                hideSeanceDate
                hideConvDate
              contexteSeance={`Fermeture succursale${dateAG ? ` du ${dateAG}` : ''}`}
                versioned
                motif={depositMotif}
                filenameFor={workflowFilenameFor}
              />
            </section>

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

        {step === 2 && (
          <div className="space-y-4" data-testid="fermeture-step2">
            <header>
              <div className="flex items-center gap-2">
                <Sparkles className="h-5 w-5 text-info" />
                <h2 className="text-lg font-semibold text-fg">Étape 2 — Génération des actes</h2>
              </div>
              <p className="text-sm text-fg-subtle">
                Le <strong>procès-verbal de fermeture</strong> et l’<strong>annonce légale</strong> sont
                produits à partir de l’étape 1 et de la Data Room : aucune donnée n’est ressaisie.
                À la validation, chaque document est déposé et versionné dans la Data Room.
              </p>
            </header>

            <SuccursaleOperationForm
              mode="fermeture"
              dossierId={dossierMereId || ticket?.dossierId}
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
              mode="fermeture"
              dossierId={dossierMereId || ticket?.dossierId}
              ticketId={ticket?.id}
              formeJuridique={formeJuridique}
              denomination={targetDenomination}
              dateAssemblee={dateAG}
              typeAssemblee={typeAssemblee}
              succursale={succursalePayload}
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
                dossierId={dossierMereId || ticket?.dossierId}
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
                initialOrdreDuJour={ORDRE_DU_JOUR_FERMETURE_SUCCURSALE}
                initialGerants={bdGerants}
              />
            )}
            {!(formeJuridique === 'SARL_AU') && (
              <FeuillePresencePanel
                dossierId={dossierMereId || ticket?.dossierId}
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
                contexteSeance={`Fermeture succursale${dateAG ? ` du ${dateAG}` : ''}`}
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
          <div className="space-y-4" data-testid="fermeture-step3-pieces">
            <header>
              <div className="flex items-center gap-2">
                <Paperclip className="h-5 w-5 text-fg-muted" />
                <h2 className="text-lg font-semibold text-fg">
                  Étape 3 — Pièces jointes (facultative)
                </h2>
              </div>
              <p className="text-sm text-fg-subtle">
                Déposez ici les <strong>versions légalisées</strong> (procès-verbal signé, annonce
                publiée, demande de radiation). Cette étape est <strong>entièrement facultative</strong>.
              </p>
            </header>
            <PiecesJointesPanel
              dossierId={dossierMereId || ticket?.dossierId}
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

        {step === 4 && (
          <div className="space-y-4" data-testid="fermeture-synthese">
            <BandeauFinParcours titre="Succursale fermée">
                Le procès-verbal de fermeture et l’annonce légale ont été produits, validés et
                déposés dans la Data Room. La succursale est enregistrée comme <strong>fermée</strong>.
              </BandeauFinParcours>

            <div className="rounded-xl border border-border bg-bg-raised p-4">
              <p className="mb-3 text-sm font-semibold text-fg">Succursale fermée</p>
              <div className="grid gap-4 rounded-lg bg-bg-overlay p-3 sm:grid-cols-2">
                <Recap label="Enseigne" value={enseigne} />
                <Recap label="Activité" value={activite} />
                <Recap label="Adresse" value={adresse} className="sm:col-span-2" />
                <Recap label="Ville" value={ville} />
                <Recap label="N° RC de la succursale" value={rcNumero} />
                <Recap label="Date d’effet de la fermeture" value={dateFermeture} />
                <Recap label={`Date ${isAU ? 'de la décision' : 'de l’assemblée'}`} value={dateAG} />
                <Recap label="Motif" value={motif} className="sm:col-span-2" />
              </div>
            </div>

            <div className="rounded-xl border border-border bg-bg-raised p-4">
              <p className="mb-3 text-sm font-semibold text-fg">Documents et dépôts</p>
              <ul className="space-y-1 rounded-lg bg-bg-overlay p-3 text-sm text-fg-muted">
                <li>
                  Procès-verbal de fermeture : {pvValidated ? 'produit et validé' : 'non validé'} — déposé
                  et versionné dans la Data Room.
                </li>
                <li>
                  Annonce légale de fermeture :{' '}
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
                <strong>Radiation :</strong> la succursale est radiée du registre du commerce de{' '}
                <strong>{villeGreffe || ville || '—'}</strong>, avec inscription modificative au
                registre du siège (loi 15-95, art. 40 et 51-52).
              </p>
              <p className="mt-1">
                <strong>Société mère :</strong> elle subsiste — seule la succursale est fermée.
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

function Recap({
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
