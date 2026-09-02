import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { Calendar, ChevronLeft, FolderOpen, Save, X } from 'lucide-react';
import { Button } from '../ui/Button';
import { Badge } from '../ui/Badge';
import type { Ticket } from '../../types/ticket';
import { TICKET_TYPE_LABELS } from '../../types/ticket';
import { WorkflowRoadmap, type RoadmapStep } from './WorkflowRoadmap';

interface FooterButtons {
  showPrev?: boolean;
  showSaveDraft?: boolean;
  showValidate?: boolean;
  validateLabel?: string;
  prevDisabled?: boolean;
  validateDisabled?: boolean;
}

interface Props {
  ticket: Ticket;
  steps: RoadmapStep[];
  currentStep: number;
  /** Etape affichee par l'UI (peut differer de currentStep si nav arriere). */
  viewStep?: number;
  /** Callback de navigation arriere via la roadmap. */
  onNavigate?: (step: number) => void;
  blocked?: boolean;
  blockedReason?: string;
  error?: string | null;
  saving: boolean;
  onPrev?: () => void;
  onSaveDraft?: () => void;
  onValidate?: () => void;
  onCancel?: () => void;
  footer?: FooterButtons;
  children: ReactNode;
}

export function WorkflowShell({
  ticket,
  steps,
  currentStep,
  viewStep,
  onNavigate,
  blocked,
  blockedReason,
  error,
  saving,
  onPrev,
  onSaveDraft,
  onValidate,
  onCancel,
  footer,
  children,
}: Props) {
  const navigate = useNavigate();
  const f: FooterButtons = {
    showPrev: true,
    showSaveDraft: true,
    showValidate: true,
    validateLabel: 'Valider et continuer',
    ...footer,
  };

  return (
    <div className="space-y-5">
      <header className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div className="flex items-start gap-3">
          <button
            onClick={() => navigate(-1)}
            className="mt-1 inline-flex h-9 w-9 items-center justify-center rounded-lg border border-border bg-bg-raised text-fg-muted hover:bg-bg-overlay"
            aria-label="Retour"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          <div>
            <div className="flex items-center gap-2">
              <p className="font-mono text-xs text-fg-subtle">{ticket.reference}</p>
              <Badge>{TICKET_TYPE_LABELS[ticket.type]}</Badge>
            </div>
            <h1 className="text-2xl font-bold text-fg">{ticket.titre}</h1>
            <div className="mt-1 flex flex-wrap gap-3 text-xs text-fg-subtle">
              {ticket.dossierId && (
                <span className="inline-flex items-center gap-1">
                  <FolderOpen className="h-3 w-3" /> Dossier : {ticket.dossierId.slice(0, 8)}
                </span>
              )}
              {ticket.deadline && (
                <span className="inline-flex items-center gap-1">
                  <Calendar className="h-3 w-3" /> Echeance : {new Date(ticket.deadline).toLocaleDateString('fr-FR')}
                </span>
              )}
            </div>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" onClick={() => navigate(-1)}>
            <Save className="mr-1 h-4 w-4" /> Sauvegarder & Quitter
          </Button>
          {onCancel && (
            <Button variant="danger" onClick={onCancel}>
              <X className="mr-1 h-4 w-4" /> Annuler le ticket
            </Button>
          )}
        </div>
      </header>

      {blocked && (
        <div className="rounded-2xl border-2 border-rose-300 bg-danger/10 p-4">
          <p className="text-sm font-bold text-danger">ETAPE BLOQUANTE</p>
          {blockedReason && (
            <p className="mt-1 text-xs text-danger">{blockedReason}</p>
          )}
        </div>
      )}

      <WorkflowRoadmap currentStep={currentStep} steps={steps} blocked={blocked} viewStep={viewStep} onNavigate={onNavigate} />

      {error && (
        <div className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger">
          {error}
        </div>
      )}

      <div className="rounded-2xl border border-border bg-bg-raised p-6">{children}</div>

      <footer className="flex flex-wrap items-center justify-between gap-2 pt-1">
        <div>
          {f.showPrev && onPrev && (
            <Button variant="secondary" onClick={onPrev} disabled={f.prevDisabled || saving}>
              <ChevronLeft className="mr-1 h-4 w-4" /> Etape precedente
            </Button>
          )}
        </div>
        <div className="flex flex-wrap gap-2">
          {f.showSaveDraft && onSaveDraft && (
            <Button variant="ghost" onClick={onSaveDraft} disabled={saving}>
              <Save className="mr-1 h-4 w-4" /> Sauvegarder brouillon
            </Button>
          )}
          {f.showValidate && onValidate && (
            <Button onClick={onValidate} loading={saving} disabled={f.validateDisabled || saving}>
              {saving ? 'Validation en cours...' : f.validateLabel}
            </Button>
          )}
        </div>
      </footer>
    </div>
  );
}
