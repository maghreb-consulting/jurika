import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { WorkflowPiecesPanel } from '../WorkflowPiecesPanel';
import type { WorkflowProgress } from '../../../types/workflow';

vi.mock('../../../services/workflow.service', () => ({
  workflowService: {
    registerPiece: vi.fn(),
    unregisterPiece: vi.fn(),
  },
}));

import { workflowService } from '../../../services/workflow.service';

function makeProgress(pieces: Record<string, unknown> = {}): WorkflowProgress {
  return {
    id: 'p1',
    workspaceId: 'ws1',
    ticketId: 't1',
    type: 'CREATION',
    currentStep: 5,
    totalSteps: 9,
    data: { pieces },
    statut: 'GENERATION_DOCUMENTS',
    startedById: 'u1',
    completedAt: null,
    updatedAt: '2026-06-04T00:00:00Z',
  } as unknown as WorkflowProgress;
}

describe('WorkflowPiecesPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('affiche la liste des pieces deja deposees avec metadata', () => {
    const progress = makeProgress({
      CN: {
        code: 'CN',
        label: 'Certificat negatif',
        filename: 'cn.pdf',
        sizeBytes: 12345,
        uploadedAtStep: 1,
      },
    });
    render(<WorkflowPiecesPanel ticketId="t1" progress={progress} currentStep={5} />);
    expect(screen.getByText('Certificat negatif')).toBeDefined();
    expect(screen.getByText(/cn\.pdf/)).toBeDefined();
    expect(screen.getByText(/etape 1/)).toBeDefined();
  });

  it('propose un bouton Importer pour les pieces connues NON encore deposees', () => {
    const progress = makeProgress({
      CN: { code: 'CN', label: 'Certificat negatif', filename: 'cn.pdf' },
    });
    render(
      <WorkflowPiecesPanel
        ticketId="t1"
        progress={progress}
        currentStep={5}
        knownPieces={[
          { code: 'CN', label: 'Certificat negatif' },
          { code: 'JUSTIFICATIF_SIEGE', label: 'Justificatif siege', obligatoire: true },
        ]}
      />,
    );
    // CN deja deposee -> pas de bouton Importer pour CN
    expect(screen.getByText('Justificatif siege')).toBeDefined();
    expect(screen.getAllByText(/Importer/).length).toBe(1);
  });

  it('appelle unregisterPiece quand on clique sur le bouton retirer', async () => {
    const progress = makeProgress({
      CN: { code: 'CN', label: 'Certificat negatif', filename: 'cn.pdf' },
    });
    const updated = makeProgress({});
    (workflowService.unregisterPiece as ReturnType<typeof vi.fn>).mockResolvedValue(updated);
    const onUpdate = vi.fn();
    render(
      <WorkflowPiecesPanel
        ticketId="t1"
        progress={progress}
        currentStep={5}
        onProgressUpdate={onUpdate}
      />,
    );
    fireEvent.click(screen.getByLabelText('Retirer Certificat negatif'));
    await waitFor(() => {
      expect(workflowService.unregisterPiece).toHaveBeenCalledWith('t1', 'CN');
    });
    expect(onUpdate).toHaveBeenCalledWith(updated);
  });

  it('rend l empty state quand pas de pieces ni catalogue', () => {
    render(
      <WorkflowPiecesPanel ticketId="t1" progress={makeProgress({})} currentStep={1} />,
    );
    expect(screen.getByText(/Aucune piece deposee/)).toBeDefined();
  });
});
