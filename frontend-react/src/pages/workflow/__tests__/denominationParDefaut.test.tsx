import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * Lot L6b (E15, parcours de bout en bout) : l'etape 1 d'une reprise (import) et d'une
 * creation propose comme denomination la RAISON SOCIALE saisie a l'ouverture du ticket
 * (celle du dossier cree avec lui), jamais le TITRE du ticket ("Import Atlas"), qui est un
 * libelle de travail. Defaut trouve a l'ecran : "Denomination (raison sociale)" valait le titre.
 */

const DOSSIER_ID = '3fa85f64-5717-4562-b3fc-2c963f66afa6';
const listDossiers = vi.fn();

vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: { listDossiers: (...a: unknown[]) => listDossiers(...a) },
}));
vi.mock('../WorkflowBoot', () => ({
  WorkflowBoot: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));
vi.mock('../useWorkflow', () => ({
  useWorkflow: () => ({
    ticket: { id: 'ticket-1', reference: 'T-1', titre: 'Import Atlas', type: 'IMPORT', statut: 'CREATION_TICKET', dossierId: DOSSIER_ID },
    progress: { statut: 'EN_COURS', currentStep: 1, totalSteps: 9, data: {} },
    loading: false,
    saving: false,
    error: null,
    setError: vi.fn(),
    stepData: {},
    viewStep: 1,
    maxStep: 1,
    saveDraft: vi.fn(),
    executeStep: vi.fn(),
    goToStep: vi.fn(),
    goPrev: vi.fn(),
    goNext: vi.fn(),
    reload: vi.fn(),
    registerDirty: () => () => undefined,
    enregistrement: { type: 'repos' },
    flushDraft: vi.fn(),
    conflits: [],
  }),
  useEnregistrement: () => ({ type: 'repos' }),
}));
vi.mock('../../tickets/CancelTicketDialog', () => ({ CancelTicketDialog: () => null }));
vi.mock('../../../components/workflow/IndicateurEnregistrement', () => ({ IndicateurEnregistrement: () => null }));

const { ImportWorkflowPage } = await import('../ImportWorkflowPage');
const { CreationSarlWorkflowPage } = await import('../CreationSarlWorkflowPage');

beforeEach(() => {
  listDossiers.mockReset();
  listDossiers.mockResolvedValue([
    { id: DOSSIER_ID, raisonSociale: 'ATLAS IMPORT SARL', formeJuridique: 'SARL', ice: null, ville: null, statut: 'BROUILLON' },
  ]);
});

describe('denomination proposee a l’etape 1', () => {
  it('reprise : la raison sociale du dossier, pas le titre du ticket', async () => {
    render(<MemoryRouter><ImportWorkflowPage /></MemoryRouter>);
    const champ = await screen.findByDisplayValue('ATLAS IMPORT SARL');
    expect(champ).toHaveAccessibleName(/Dénomination/);
    expect(screen.queryByDisplayValue('Import Atlas')).not.toBeInTheDocument();
  });

  it('reprise : dossier illisible, le champ reste vide (rien d’invente)', async () => {
    listDossiers.mockRejectedValue(new Error('coupure'));
    render(<MemoryRouter><ImportWorkflowPage /></MemoryRouter>);
    const champ = await screen.findByRole('textbox', { name: /Dénomination/ });
    expect(champ).toHaveValue('');
  });

  it('creation : la raison sociale du dossier, pas le titre du ticket', async () => {
    render(<MemoryRouter><CreationSarlWorkflowPage /></MemoryRouter>);
    const champ = await screen.findByDisplayValue('ATLAS IMPORT SARL');
    expect(champ).toHaveAccessibleName(/Dénomination/);
    expect(screen.queryByDisplayValue('Import Atlas')).not.toBeInTheDocument();
  });
});
