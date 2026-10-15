import { useState } from 'react';
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
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { TICKET_TYPE_LABELS } from '../../types/ticket';
import { AIDE_PARCOURS_GENERIQUE } from './aidesParcours';
import { useEtatReinitialise } from './parcoursHooks';
import { useStepAutosave } from './useStepAutosave';

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
  const { ticket, progress, loading, saving, error, setError, stepData, saveDraft, executeStep, viewStep, maxStep, goToStep, goPrev: navPrev, registerDirty } = useWorkflow();
  const [showCancel, setShowCancel] = useState(false);
  // Contenu de l'etape AFFICHEE, recharge quand le parcours ou l'etape affichee change
  // (ajustement pendant le rendu, et non plus dans un effet). Lot L7 (E2) : auparavant
  // l'etape courante ; revenir sur une etape anterieure montrait alors le contenu d'une
  // autre etape, que l'enregistrement automatique aurait range sous la mauvaise cle.
  const [rawJson, setRawJson] = useEtatReinitialise(
    progress ? `${progress.id}|${viewStep}` : null,
    () => {
      if (!progress) return '{}';
      const current = (stepData[`step${viewStep}`] as Record<string, unknown>) ?? {};
      return JSON.stringify(current, null, 2);
    },
  );
  // Lot L7 (E2) : enregistrement automatique ; un JSON incomplet (en cours de frappe)
  // n'est pas envoye, le dernier JSON valide l'a ete.
  useStepAutosave(viewStep, () => objetJson(rawJson), progress?.statut === 'TERMINE' ? undefined : registerDirty);

  if (loading) return <Loader />;
  if (!ticket || !progress)
    return (
      <div className="rounded-lg border border-danger/40 bg-danger/10 p-6 text-sm text-danger" role="alert">
        {error ?? 'Parcours introuvable : rechargez la page ou revenez à la liste des tickets.'}
      </div>
    );

  const step = viewStep;
  const total = progress.totalSteps || 1;
  const steps: RoadmapStep[] = Array.from({ length: total }, (_, i) => ({
    number: i + 1,
    label: `Étape ${i + 1}`,
  }));
  const isTerminated = progress.statut === 'TERMINE';

  function parsePayload(): Record<string, unknown> | null {
    try {
      const v = JSON.parse(rawJson);
      if (typeof v !== 'object' || Array.isArray(v) || v === null) {
        setError('Le contenu saisi doit être un objet JSON, entre accolades { }.');
        return null;
      }
      return v as Record<string, unknown>;
    } catch {
      setError('Le contenu saisi n’est pas un JSON valide : vérifiez les guillemets, les virgules et les accolades.');
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
        footer={{ validateLabel: step === total ? 'Finaliser' : 'Valider l’étape' }}
      >
        <div className="space-y-3">
          <header>
            <h2 className="text-lg font-semibold text-fg">
              Parcours « {TICKET_TYPE_LABELS[ticket.type] ?? ticket.type} » — Étape {step} / {total}
            </h2>
            <p className="text-sm text-fg-subtle">
              Ce parcours n’a pas encore d’écran dédié : ses données se saisissent sous forme
              structurée (JSON).
            </p>
          </header>
          <TexteAide cle="parcours.autre" titre="Aide de cet écran">
            <ul className="space-y-1">
              <li>
                <span className="font-medium text-fg">À faire : </span>
                {AIDE_PARCOURS_GENERIQUE.faire}
              </li>
              <li>
                <span className="font-medium text-fg">Ce qui manque : </span>
                {AIDE_PARCOURS_GENERIQUE.manque}
              </li>
              <li>
                <span className="font-medium text-fg">Ensuite : </span>
                {AIDE_PARCOURS_GENERIQUE.ensuite}
              </li>
            </ul>
          </TexteAide>
          <div>
            <label htmlFor="parcours-autre-donnees" className="mb-1 flex items-center gap-1 text-sm font-medium text-fg-muted">
              Données de l’étape (JSON)
              <InfoBulle
                libelle="Comment saisir les données ?"
                texte="Saisissez un objet entre accolades, par exemple { &quot;motif&quot;: &quot;…&quot; }. Les données déjà enregistrées pour cette étape sont reprises ; chaque contenu JSON valide est enregistré automatiquement en brouillon, sans valider l’étape."
              />
            </label>
            <textarea
              id="parcours-autre-donnees"
              rows={12}
              value={rawJson}
              onChange={(e) => setRawJson(e.target.value)}
              className="w-full rounded-lg border border-border-hi px-3 py-2 font-mono text-xs focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200"
            />
          </div>
          {isTerminated && (
            <div className="rounded-lg border border-emerald-300 bg-emerald-50 p-3 text-sm text-emerald-800">
              <CheckCircle className="mr-2 inline h-4 w-4" /> Parcours terminé.
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
            navigate(`/tickets?ticket=${ticket.id}`);
          }}
        />
      )}
    </>
  );
}

/** Objet JSON saisi, ou null s'il n'est pas (encore) valide. */
function objetJson(texte: string): Record<string, unknown> | null {
  try {
    const v: unknown = JSON.parse(texte);
    return typeof v === 'object' && v !== null && !Array.isArray(v) ? (v as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

function Loader() {
  return (
    <div className="flex h-64 items-center justify-center">
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-border border-t-indigo-600" />
    </div>
  );
}
