import { useNavigate } from 'react-router-dom';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ChevronLeft, ChevronRight, Save, X } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { ticketService } from '../../services/ticket.service';
import { workflowService } from '../../services/workflow.service';
import { dataroomService } from '../../services/dataroom.service';
import { extractError } from '../../lib/api';
import type { FormeJuridique } from '../../types/ticket';
import type { DocumentType } from '../../types/dataroom';
import { WorkflowRoadmap } from './WorkflowRoadmap';
// Refonte IMPORT 2026-06-25 — meme tronc de SAISIE que la CREATION (steps 1-6),
// reutilise via le flag `importMode` (masque CN / depot / liberation 25% / dates
// de bail ; ajoute RC + IF). Pas de Generation IA. 3 etapes d'upload typees.
import { Step1Denomination } from './steps/Step1Denomination';
import { Step2Siege } from './steps/Step2Siege';
import { Step3Capital } from './steps/Step3Capital';
import { Step4Activite } from './steps/Step4Activite';
import { Step5Dirigeants } from './steps/Step5Dirigeants';
import { Step6Associes } from './steps/Step6Associes';
import { Step2ImportJuridique } from './import-steps/Step2ImportJuridique';
import { StepImportFolder } from './import-steps/StepImportFolder';
import { StepSuiviExercices } from './import-steps/StepSuiviExercices';
import { Step5Validation } from './import-steps/Step5Validation';
import { CancelTicketDialog } from '../tickets/CancelTicketDialog';
import { useWorkflow } from './useWorkflow';
import { WorkflowBoot } from './WorkflowBoot';

/** Forme juridique restreinte aux 2 valeurs supportees par l'import SARL. */
type SarlForm = 'SARL' | 'SARL_AU';

const IMPORT_STEP_LABELS: Record<number, string> = {
  1: 'Denomination',
  2: 'Siege',
  3: 'Capital',
  4: 'Activite',
  5: 'Dirigeants',
  6: 'Associes',
  7: 'Upload juridique',
  8: 'Upload comptable',
  9: 'Upload fiscal',
  10: 'Suivi',
  11: 'Validation',
};

/** Resultat d'ouverture d'un exercice a la finalisation de l'import. */
export interface ExerciceOpenResult {
  annee: number;
  status: 'OPENED' | 'EXISTS' | 'ERROR';
  message?: string;
}

/**
 * Lit la couche metier d'une step en tolerant le nicheage backend
 * ("stepN.<inner>") et l'acces direct.
 */
function unwrap(
  data: Record<string, Record<string, unknown>>,
  stepKey: string,
  innerKey: string,
): Record<string, unknown> {
  const step = (data[stepKey] as Record<string, unknown> | undefined) ?? {};
  const inner = step[innerKey];
  if (inner && typeof inner === 'object' && !Array.isArray(inner)) {
    return inner as Record<string, unknown>;
  }
  return step;
}

function readImportForme(stepData: Record<string, Record<string, unknown>>): SarlForm {
  const step1 = stepData.step1 ?? {};
  const direct = step1.formeJuridique as FormeJuridique | undefined;
  const nested = (step1.denomination as Record<string, unknown> | undefined)?.formeJuridique as
    | FormeJuridique
    | undefined;
  const forme = direct ?? nested;
  return forme === 'SARL_AU' ? 'SARL_AU' : 'SARL';
}

/** Mapping minimal code piece -> DocumentType juridique (uploads inline optionnels). */
function mapPieceToDocumentType(code: string): DocumentType {
  if (code.startsWith('CIN_') || code.startsWith('CNIE')) return 'CNIE_GERANT';
  if (code === 'RC' || code.startsWith('RC_')) return 'RC';
  if (code.startsWith('STATUTS')) return 'STATUTS';
  return 'AUTRE';
}

/**
 * Workflow IMPORT (refonte 2026-06-25) — 11 etapes : meme tronc de SAISIE que la
 * CREATION (1 Denomination, 2 Siege, 3 Capital, 4 Activite, 5 Dirigeants,
 * 6 Associes), saisie FORCEE pour une fiche structuree COMPLETE, MAIS sans
 * Generation IA. A la place : 3 etapes d'UPLOAD typees deposees directement en
 * Data Room (7 Juridique, 8 Comptable, 9 Fiscal), puis 10 Suivi (regime TVA +
 * exercices) et 11 Validation/Synthese. Les composants de saisie de la CREATION
 * sont reutilises via le flag `importMode` (masque les champs purement
 * CREATION : CN, depot bancaire, liberation 25%, dates de bail).
 */
/**
 * Monte {@link ImportWorkflowPageBody} SOUS {@link WorkflowBoot} : les champs de chaque
 * etape s'initialisent avec `useState(stepData...)`, qui ne lit sa valeur qu'au
 * premier render. Sans ce montage differe, ce premier render a lieu AVANT la
 * reponse du serveur et tous les champs restent vides apres un rechargement
 * (F5, deconnexion/reconnexion), meme sur une etape deja validee.
 */
export function ImportWorkflowPage() {
  return (
    <WorkflowBoot>
      <ImportWorkflowPageBody />
    </WorkflowBoot>
  );
}

function ImportWorkflowPageBody() {
  const navigate = useNavigate();
  const {
    ticket,
    progress,
    loading,
    saving,
    error,
    stepData,
    viewStep,
    maxStep,
    saveDraft,
    executeStep,
    goToStep,
    goPrev,
    goNext,
    reload,
    registerDirty,
  } = useWorkflow();
  const [showCancel, setShowCancel] = useState(false);
  const [ticketForme, setTicketForme] = useState<SarlForm | null>(null);
  const [datasetWarnings, setDatasetWarnings] = useState<string[]>([]);
  // 2026-06-24 — Resultats d'ouverture des exercices a la finalisation (suivi).
  const [exerciceResults, setExerciceResults] = useState<ExerciceOpenResult[] | null>(null);
  const [echeancesTotal, setEcheancesTotal] = useState<number | null>(null);
  const [finalizing, setFinalizing] = useState(false);

  // Forme juridique definie a la creation du ticket -> lockee dans Step1.
  useEffect(() => {
    if (!ticket?.dossierId) return;
    let cancelled = false;
    void dataroomService.listDossiers()
      .then((list) => {
        if (cancelled) return;
        const d = list.find((x) => x.id === ticket.dossierId);
        const forme = (d?.formeJuridique ?? '').toString().toUpperCase();
        if (forme === 'SARL_AU' || forme === 'SARL') {
          setTicketForme(forme as SarlForm);
        }
      })
      .catch(() => {
        /* best-effort : fallback lecture depuis stepData.step1. */
      });
    return () => { cancelled = true; };
  }, [ticket?.dossierId]);

  /**
   * Depot Data Room best-effort pour les uploads inline OPTIONNELS des etapes de
   * saisie (CIN d'un dirigeant/associe extraite via l'assistant). En IMPORT les
   * pieces principales passent par les 3 etapes d'upload dediees ; cet inline
   * reste un confort et ne bloque jamais le wizard.
   */
  const onPieceUploaded = useCallback(
    async (code: string, label: string, file: File) => {
      if (!ticket?.id) return;
      try {
        await workflowService.registerPiece(ticket.id, {
          code,
          label,
          filename: file.name,
          sizeBytes: file.size,
          contentType: file.type || 'application/octet-stream',
          uploadedAtStep: viewStep,
        });
      } catch {
        /* best-effort */
      }
      if (ticket.dossierId) {
        try {
          await dataroomService.uploadJuridique(ticket.dossierId, {
            file,
            documentType: mapPieceToDocumentType(code),
            title: label,
            ticketId: ticket.id,
          });
        } catch (err) {
          setDatasetWarnings((prev) => [
            ...prev,
            `Depot Dataroom ${label} : ${err instanceof Error ? err.message : 'echec'}`,
          ]);
        }
      }
      void reload();
    },
    [ticket?.id, ticket?.dossierId, viewStep, reload],
  );

  // Dirigeants marques isAssociate=true (Step5) -> propages en associes (Step6).
  const propagatedAssociates = useMemo(() => {
    type RawDir = {
      id: string;
      typePersonne?: 'PHYSIQUE' | 'MORALE';
      civilite?: 'M' | 'Mme';
      nom?: string;
      prenom?: string;
      cinNumero?: string;
      adresse?: string;
      isAssociate?: boolean;
      cinUploaded?: boolean;
      cinFileName?: string;
      nationalite?: string;
      dateNaissance?: string;
      lieuNaissance?: string;
      pieceValidite?: string;
      denomination?: string;
      formeJuridiqueEntite?: string;
      rc?: string;
      ice?: string;
      ifFiscal?: string;
      siege?: string;
      repCivilite?: 'M' | 'Mme';
      repNom?: string;
      repPrenom?: string;
      repCin?: string;
      repAdresse?: string;
      repDateNaissance?: string;
      repLieuNaissance?: string;
      repNationalite?: string;
      repPieceValidite?: string;
      repQualite?: string;
      rcUploaded?: boolean;
      rcFileName?: string;
      statutsEntiteUploaded?: boolean;
      statutsEntiteFileName?: string;
      repCinUploaded?: boolean;
      repCinFileName?: string;
    };
    const dirs = ((unwrap(stepData, 'step5', 'dirigeants').dirigeants as RawDir[]) ?? []) as RawDir[];
    return dirs
      .filter((d) => d.isAssociate)
      .map((d) => ({
        id: d.id,
        typePersonne: (d.typePersonne ?? 'PHYSIQUE') as 'PHYSIQUE' | 'MORALE',
        civilite: d.civilite ?? 'M',
        nom: d.nom ?? '',
        prenom: d.prenom ?? '',
        cinNumero: d.cinNumero ?? '',
        adresse: d.adresse ?? '',
        cinUploaded: !!d.cinUploaded,
        cinFileName: d.cinFileName,
        nationalite: d.nationalite,
        dateNaissance: d.dateNaissance,
        lieuNaissance: d.lieuNaissance,
        pieceValidite: d.pieceValidite,
        denomination: d.denomination,
        formeJuridiqueEntite: d.formeJuridiqueEntite,
        rc: d.rc,
        ice: d.ice,
        ifFiscal: d.ifFiscal,
        siege: d.siege,
        repCivilite: d.repCivilite,
        repNom: d.repNom,
        repPrenom: d.repPrenom,
        repCin: d.repCin,
        repAdresse: d.repAdresse,
        repDateNaissance: d.repDateNaissance,
        repLieuNaissance: d.repLieuNaissance,
        repNationalite: d.repNationalite,
        repPieceValidite: d.repPieceValidite,
        repQualite: d.repQualite,
        rcUploaded: !!d.rcUploaded,
        rcFileName: d.rcFileName,
        statutsEntiteUploaded: !!d.statutsEntiteUploaded,
        statutsEntiteFileName: d.statutsEntiteFileName,
        repCinUploaded: !!d.repCinUploaded,
        repCinFileName: d.repCinFileName,
      }));
  }, [stepData]);

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-border border-t-indigo-600" />
      </div>
    );
  }

  if (!ticket || !progress) {
    return (
      <div className="rounded-lg border border-danger/40 bg-danger/10 p-6 text-sm text-danger">
        {error ?? 'Workflow introuvable'}
      </div>
    );
  }

  const step = viewStep;
  const formeJuridique: SarlForm = ticketForme ?? readImportForme(stepData);
  const formeLabel =
    formeJuridique === 'SARL_AU' ? 'Mode : SARL_AU (1 associe)' : 'Mode : SARL multi-associes';
  const denomination =
    (unwrap(stepData, 'step1', 'denomination').denomination as string | undefined) ?? null;

  async function submit(s: number, p: Record<string, unknown>): Promise<void> {
    await executeStep(s, p);
  }
  async function draft(s: number, p: Record<string, unknown>) {
    await saveDraft(s, p);
  }

  /**
   * Finalisation de l'import (etape 11). Apres la finalisation du workflow
   * (executeStep 11 -> ticket CLOTURE + consolidation fiche_structuree cote
   * backend), on OUVRE les exercices fiscaux choisis a l'etape Suivi (step10).
   * L'orchestration est cote front (workflow-service n'a pas de client
   * dataroom-service) : openExercice -> EcheancesGenerator -> echeances visibles.
   */
  async function finalizeImport(p: Record<string, unknown>): Promise<void> {
    setFinalizing(true);
    try {
      const dossierId = ticket?.dossierId ?? '';
      const suivi = unwrap(stepData, 'step10', 'suivi');
      const regimeTvaMensuel = suivi.regimeTvaMensuel !== false;
      const anneeExercice = Number(suivi.anneeExercice ?? new Date().getFullYear());
      const anterieures = Array.isArray(suivi.anneesAnterieuresSelectionnees)
        ? (suivi.anneesAnterieuresSelectionnees as unknown[]).map((y) => Number(y))
        : [];
      const annees = Array.from(new Set([anneeExercice, ...anterieures])).filter(
        (a) => Number.isFinite(a) && a > 0,
      );

      const results: ExerciceOpenResult[] = [];
      if (dossierId) {
        for (const annee of annees) {
          try {
            await dataroomService.openExercice(dossierId, {
              annee,
              regimeTvaMensuel,
              autoCreateComptable: true,
            });
            results.push({ annee, status: 'OPENED' });
          } catch (err) {
            const { code, message } = extractError(err);
            const exists =
              code === 'EXERCICE_EXISTS' || /exist|déjà|deja/i.test(message ?? '');
            results.push({
              annee,
              status: exists ? 'EXISTS' : 'ERROR',
              message: exists ? undefined : message,
            });
          }
        }
        try {
          const ech = await dataroomService.listEcheances(dossierId);
          setEcheancesTotal(ech.length);
        } catch {
          /* best-effort */
        }
      }
      setExerciceResults(results);

      await executeStep(11, p);
    } finally {
      setFinalizing(false);
    }
  }

  // Cibles depuis l'etape Capital (step3) pour la repartition des parts (Step6).
  const step3cap = (unwrap(stepData, 'step3', 'capital')) as Record<string, unknown>;
  const numOr = (v: unknown) => {
    const n = Number(v ?? 0);
    return n > 0 ? n : undefined;
  };

  return (
    <div className="space-y-5">
      <header className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <p className="font-mono text-xs text-fg-subtle">{ticket.reference}</p>
          <h1 className="text-2xl font-bold text-fg">{ticket.titre}</h1>
          <div className="mt-1">
            <Badge variant={formeJuridique === 'SARL_AU' ? 'info' : 'default'}>{formeLabel}</Badge>
          </div>
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => navigate(-1)}>
            <Save className="mr-1 h-4 w-4" /> Sauvegarder & Quitter
          </Button>
          <Button variant="danger" onClick={() => setShowCancel(true)}>
            <X className="mr-1 h-4 w-4" /> Annuler le ticket
          </Button>
        </div>
      </header>

      <WorkflowRoadmap
        currentStep={maxStep}
        viewStep={step}
        totalSteps={progress.totalSteps}
        onNavigate={goToStep}
        labels={IMPORT_STEP_LABELS}
      />

      {step < maxStep && (
        <div className="rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-800">
          Vous consultez l'etape {step}/{progress.totalSteps} (etape la plus avancee
          atteinte : {maxStep}). Vous pouvez modifier les valeurs puis revalider.
        </div>
      )}

      {error && (
        <div className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger">
          {error}
        </div>
      )}

      {datasetWarnings.length > 0 && (
        <div className="rounded-lg border border-warning/40 bg-warning/10 p-3 text-xs text-warning">
          <p className="font-semibold">Avertissements Data Room :</p>
          <ul className="ml-4 list-disc">
            {datasetWarnings.slice(-3).map((w, i) => (
              <li key={i}>{w}</li>
            ))}
          </ul>
          <button
            type="button"
            onClick={() => setDatasetWarnings([])}
            className="mt-1 text-[11px] underline"
          >
            Effacer
          </button>
        </div>
      )}

      <div className="rounded-2xl border border-border bg-bg-raised p-6">
        {step === 1 && (
          <Step1Denomination
            existing={stepData.step1}
            lockedFormeJuridique={ticketForme}
            defaultDenomination={ticket?.titre}
            importMode
            saving={saving}
            onSubmit={(p) => submit(1, p)}
            onSave={(p) => draft(1, { step1: p })}
            registerDirty={registerDirty}
            onPieceUploaded={onPieceUploaded}
            dossierId={ticket?.dossierId ?? null}
          />
        )}
        {step === 2 && (
          <Step2Siege
            existing={stepData.step2}
            importMode
            saving={saving}
            onSubmit={(p) => submit(2, p)}
            onSave={(p) => draft(2, { step2: p })}
            registerDirty={registerDirty}
            onPieceUploaded={onPieceUploaded}
          />
        )}
        {step === 3 && (
          <Step3Capital
            existing={stepData.step3}
            importMode
            saving={saving}
            onSubmit={(p) => submit(3, p)}
            onSave={(p) => draft(3, { step3: p })}
            registerDirty={registerDirty}
          />
        )}
        {step === 4 && (
          <Step4Activite
            existing={stepData.step4}
            importMode
            saving={saving}
            onSubmit={(p) => submit(4, p)}
            onSave={(p) => draft(4, { step4: p })}
            registerDirty={registerDirty}
          />
        )}
        {step === 5 && (
          <Step5Dirigeants
            existing={stepData.step5}
            formeJuridique={formeJuridique}
            importMode
            saving={saving}
            onSubmit={(p) => submit(5, p)}
            onSave={(p) => draft(5, { step5: p })}
            registerDirty={registerDirty}
            onPieceUploaded={onPieceUploaded}
            dossierId={ticket?.dossierId ?? null}
          />
        )}
        {step === 6 && (
          <Step6Associes
            existing={stepData.step6}
            formeJuridique={formeJuridique}
            importMode
            totalPartsExpected={numOr(step3cap.nombreParts)}
            capitalSocialExpected={numOr(step3cap.capitalSocialMad)}
            valeurNominaleExpected={numOr(step3cap.valeurNominaleMad ?? step3cap.valeurNominale)}
            apportNumeraireExpected={Number(step3cap.apportNumeraire ?? 0)}
            apportNatureExpected={Number(step3cap.apportNature ?? 0)}
            apportIndustrieExpected={Number(step3cap.apportIndustrie ?? 0)}
            propagatedFromDirigeants={propagatedAssociates}
            saving={saving}
            onSubmit={(p) => submit(6, p)}
            onSave={(p) => draft(6, { step6: p })}
            registerDirty={registerDirty}
            onPieceUploaded={onPieceUploaded}
            dossierId={ticket?.dossierId ?? null}
          />
        )}
        {step === 7 && (
          <Step2ImportJuridique
            existing={unwrap(stepData, 'step7', 'juridique')}
            dossierId={ticket.dossierId}
            denomination={denomination}
            saving={saving}
            onSubmit={(p) => submit(7, p)}
          />
        )}
        {step === 8 && (
          <StepImportFolder
            kind="COMPTABLE"
            existing={unwrap(stepData, 'step8', 'comptable')}
            dossierId={ticket.dossierId}
            denomination={denomination}
            saving={saving}
            onSubmit={(p) => submit(8, p)}
          />
        )}
        {step === 9 && (
          <StepImportFolder
            kind="FISCAL"
            existing={unwrap(stepData, 'step9', 'fiscal')}
            dossierId={ticket.dossierId}
            denomination={denomination}
            saving={saving}
            onSubmit={(p) => submit(9, p)}
          />
        )}
        {step === 10 && (
          <StepSuiviExercices
            existing={stepData.step10}
            data={stepData}
            saving={saving}
            onSubmit={(p) => submit(10, p)}
          />
        )}
        {step === 11 && (
          <Step5Validation
            existing={stepData.step11}
            data={stepData}
            dossierId={ticket.dossierId ?? null}
            exerciceResults={exerciceResults}
            echeancesTotal={echeancesTotal}
            saving={saving || finalizing}
            onSubmit={finalizeImport}
          />
        )}

        {progress.statut === 'TERMINE' && (
          <div className="mt-6 rounded-lg border border-emerald-300 bg-emerald-50 p-4 text-sm text-emerald-800">
            <p className="font-semibold">Import termine</p>
            <p className="mt-1">Le dossier {ticket.reference} a ete consolide.</p>
            <Button className="mt-3" onClick={() => navigate('/tickets')}>
              Retour aux tickets
            </Button>
          </div>
        )}
      </div>

      <footer className="flex flex-wrap items-center justify-between gap-2 pt-1">
        <Button variant="secondary" onClick={goPrev} disabled={step <= 1 || saving}>
          <ChevronLeft className="mr-1 h-4 w-4" /> Etape precedente
        </Button>
        {step < maxStep && (
          <Button variant="secondary" onClick={goNext} disabled={saving}>
            Suivant (sans modifier) <ChevronRight className="ml-1 h-4 w-4" />
          </Button>
        )}
      </footer>

      {showCancel && (
        <CancelTicketDialog
          ticket={ticket}
          onClose={() => setShowCancel(false)}
          onConfirm={async (comment) => {
            await ticketService.transition(ticket.id, { target: 'ANNULE', comment });
            navigate('/tickets');
          }}
        />
      )}
    </div>
  );
}
