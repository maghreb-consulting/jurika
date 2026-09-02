import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CheckCircle } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { WorkflowShell } from '../../components/workflow/WorkflowShell';
import type { RoadmapStep } from '../../components/workflow/WorkflowRoadmap';
import { ticketService } from '../../services/ticket.service';
import { CancelTicketDialog } from '../tickets/CancelTicketDialog';
import { useWorkflow } from './useWorkflow';
import { WorkflowBoot } from './WorkflowBoot';
import { GenerateDocumentPanel } from '../../components/workflow/GenerateDocumentPanel';

/**
 * Monte {@link AutreWorkflowPageBody} SOUS {@link WorkflowBoot} : les champs de chaque
 * etape s'initialisent avec `useState(stepData...)`, qui ne lit sa valeur qu'au
 * premier render. Sans ce montage differe, ce premier render a lieu AVANT la
 * reponse du serveur et tous les champs restent vides apres un rechargement
 * (F5, deconnexion/reconnexion), meme sur une etape deja validee.
 */
export function AutreWorkflowPage() {
  return (
    <WorkflowBoot>
      <AutreWorkflowPageBody />
    </WorkflowBoot>
  );
}

function AutreWorkflowPageBody() {
  const navigate = useNavigate();
  const { ticket, progress, loading, saving, error, setError, stepData, saveDraft, executeStep, viewStep, maxStep, goToStep, goPrev: navPrev } = useWorkflow();
  const [showCancel, setShowCancel] = useState(false);
  const [rawJson, setRawJson] = useState('{}');

  useEffect(() => {
    if (progress) {
      const current = (stepData[`step${progress.currentStep}`] as Record<string, unknown>) ?? {};
      setRawJson(JSON.stringify(current, null, 2));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [progress?.currentStep, progress?.id]);

  if (loading) return <Loader />;
  if (!ticket || !progress)
    return (
      <div className="rounded-lg border border-danger/40 bg-danger/10 p-6 text-sm text-danger">
        {error ?? 'Workflow introuvable'}
      </div>
    );

  const step = viewStep;
  const total = progress.totalSteps || 1;
  const steps: RoadmapStep[] = Array.from({ length: total }, (_, i) => ({
    number: i + 1,
    label: `Etape ${i + 1}`,
  }));
  const isTerminated = progress.statut === 'TERMINE';

  function parsePayload(): Record<string, unknown> | null {
    try {
      const v = JSON.parse(rawJson);
      if (typeof v !== 'object' || Array.isArray(v) || v === null) {
        setError('Le payload doit etre un objet JSON.');
        return null;
      }
      return v as Record<string, unknown>;
    } catch {
      setError('JSON invalide.');
      return null;
    }
  }

  async function handleValidate() {
    setError(null);
    const payload = parsePayload();
    if (!payload) return;
    await executeStep(step, payload);
  }

  async function handleSaveDraft() {
    setError(null);
    const payload = parsePayload();
    if (!payload) return;
    await saveDraft(step, { [`step${step}`]: payload });
  }

  function goPrev() {
    navPrev();
  }

  return (
    <>
      <WorkflowShell
        ticket={ticket}
        steps={steps}
        currentStep={maxStep}
        viewStep={step}
        onNavigate={goToStep}
        error={error}
        saving={saving}
        onPrev={step > 1 ? goPrev : undefined}
        onSaveDraft={!isTerminated ? handleSaveDraft : undefined}
        onValidate={!isTerminated ? handleValidate : undefined}
        onCancel={() => setShowCancel(true)}
        footer={{ validateLabel: step === total ? 'Finaliser' : "Valider l'etape" }}
      >
        <div className="space-y-3">
          <header>
            <h2 className="text-lg font-semibold text-fg">
              Workflow {progress.type} — Etape {step} / {total}
            </h2>
            <p className="text-sm text-fg-subtle">
              Ce workflow n'a pas de UI dediee. Saisissez le payload JSON pour cette etape.
            </p>
          </header>
          <div>
            <label className="mb-1 block text-sm font-medium text-fg-muted">Payload JSON</label>
            <textarea
              rows={12}
              value={rawJson}
              onChange={(e) => setRawJson(e.target.value)}
              className="w-full rounded-lg border border-border-hi px-3 py-2 font-mono text-xs focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200"
            />
          </div>
          {isTerminated && (
            <div className="rounded-lg border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-800">
              <CheckCircle className="mr-2 inline h-4 w-4" /> Workflow termine.
              <Button className="ml-3" onClick={() => navigate('/tickets')}>
                Retour aux tickets
              </Button>
            </div>
          )}
          <div className="mt-6 border-t border-border pt-6">
            <h3 className="mb-3 text-base font-semibold text-fg">Documents disponibles</h3>
            <GenerateDocumentPanel
              workflowCodes={[]}
              buildPayload={() => ({})}
            />
          </div>
        </div>
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
