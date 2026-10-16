import { render, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

/**
 * Lot L6b (E15, parcours de bout en bout "dissolution puis liquidation") : le rapport final
 * du liquidateur etait refuse ("Date d'entree en fonctions du liquidateur" manquante) alors
 * que cette date, fixee a la dissolution, est enregistree avec le liquidateur du dossier et
 * qu'aucun champ ne la redemande a la liquidation. Le bloc "operation" transmis au PV de
 * cloture et au rapport reprend le mandat enregistre (comme l'etape 1 le fait deja).
 */
const DOSSIER_ID = '3fa85f64-5717-4562-b3fc-2c963f66afa6';
const { operations } = vi.hoisted(() => ({ operations: [] as Record<string, unknown>[] }));

vi.mock('../../../services/workflow.service', () => ({
  workflowService: {
    getDossierParties: vi.fn(async () => ({
      associes: [], gerants: [], denomination: 'ATLAS NORD SARL', formeJuridique: 'SARL', statut: 'DISSOUTE',
      dateDissolution: '2026-09-17',
      liquidateur: {
        source: 'BD', civilite: 'M.', prenom: 'Mehdi', nom: 'Alaoui', adresse: '8 rue des Essais',
        mandatDateEffet: '2026-09-17', dureeMandat: 'deux ans', redditionPeriodicite: 'tous les six mois',
      },
    })),
    listSuccursales: vi.fn(async () => []),
  },
}));
vi.mock('react-router-dom', () => ({ useNavigate: () => vi.fn() }));
vi.mock('../WorkflowBoot', () => ({
  WorkflowBoot: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));
vi.mock('../useWorkflow', () => ({
  useWorkflow: () => ({
    ticket: { id: 'ticket-1', titre: 'Cloture', dossierId: DOSSIER_ID },
    progress: { statut: 'EN_COURS' },
    loading: false,
    saving: false,
    error: null,
    setError: vi.fn(),
    stepData: { step1: { dossierId: DOSSIER_ID, dateClotureLiquidation: '2026-10-06' } },
    viewStep: 2,
    maxStep: 2,
    saveDraft: vi.fn(),
    executeStep: vi.fn(),
    goToStep: vi.fn(),
    goPrev: vi.fn(),
  }),
}));
vi.mock('../../../components/workflow/WorkflowShell', () => ({
  WorkflowShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));
vi.mock('../../../components/workflow/SeanceOperationForm', () => ({
  SeanceOperationForm: (p: { operation?: Record<string, unknown> }) => {
    if (p.operation) operations.push(p.operation);
    return null;
  },
}));
vi.mock('../../../components/workflow/RapportLiquidateurPanel', () => ({ RapportLiquidateurPanel: () => null }));
vi.mock('../../../components/workflow/LiquidationAnnoncePanel', () => ({ LiquidationAnnoncePanel: () => null }));
vi.mock('../../../components/workflow/IncidentSeancePanel', () => ({ IncidentSeancePanel: () => null }));
vi.mock('../../../components/workflow/SeanceDocPanels', () => ({
  ConvocationPanel: () => null,
  FeuillePresencePanel: () => null,
}));
vi.mock('../../tickets/CancelTicketDialog', () => ({ CancelTicketDialog: () => null }));

const { LiquidationWorkflowPage } = await import('../LiquidationWorkflowPage');

describe('Liquidation, etape 2 : mandat du liquidateur', () => {
  it('le PV de cloture et le rapport recoivent la date d\'entree en fonctions enregistree', async () => {
    render(<LiquidationWorkflowPage />);
    await waitFor(() => {
      const derniere = operations[operations.length - 1] as { liquidateur?: Record<string, unknown> } | undefined;
      expect(derniere?.liquidateur?.mandatDateEffet).toBe('2026-09-17');
      expect(derniere?.liquidateur?.dureeMandat).toBe('deux ans');
    });
  });
});
