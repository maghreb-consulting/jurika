/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

/**
 * Non-regression du defaut **C1** (simulation 2026-08-15, GROUPE 0).
 *
 * **Le bug** : le backend `ImportWorkflow.handleActivite` fait
 * `require(p, "description", "dateDebutExercice")` a l'etape 4. La de-duplication
 * d'aout 2026 a retire « Date de commencement de l'exercice » de Step4Activite
 * (saisie unique a l'etape Capital) — mais Step3Capital MASQUE ce champ en
 * `importMode`. Resultat : **aucun champ de l'UI ne fournissait plus
 * `dateDebutExercice`**, et l'etape 4 d'un IMPORT etait refusee avec
 * « Champ requis : dateDebutExercice ». Le parcours IMPORT etait infranchissable.
 *
 * **Ce que ce test verrouille** : les deux cotes du contrat qui avaient derive.
 * 1. la page IMPORT passe bien `importMode` a l'etape 4 (c'est LE cablage qui manquait) ;
 * 2. le payload envoye a `executeStep(4, ...)` porte `dateDebutExercice` ;
 * 3. la CREATION, elle, ne le renvoie PAS (la de-dup reste acquise).
 *
 * Le pendant backend (traversee 11/11 etapes) vit dans `ImportWorkflowTest`.
 */

const executeStep = vi.fn().mockResolvedValue(undefined);
const saveDraft = vi.fn().mockResolvedValue(undefined);

vi.mock('react-router-dom', () => ({ useNavigate: () => vi.fn() }));

// La page s'enveloppe dans <WorkflowBoot> (montage differe). `useWorkflow` etant
// mocke, il n'y a rien a attendre : on court-circuite le boot.
vi.mock('../WorkflowBoot', () => ({
  WorkflowBoot: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock('../useWorkflow', () => ({
  useWorkflow: () => ({
    ticket: { id: 'ticket-1', reference: 'T-2026-00614', titre: 'MEDITERRANEE NEGOCE', dossierId: null },
    progress: { statut: 'EN_COURS', totalSteps: 11 },
    loading: false,
    saving: false,
    error: null,
    setError: vi.fn(),
    stepData: {},
    viewStep: 4,
    maxStep: 4,
    saveDraft,
    executeStep,
    goToStep: vi.fn(),
    goPrev: vi.fn(),
    goNext: vi.fn(),
    reload: vi.fn(),
    registerDirty: undefined,
  }),
}));

vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: {
    listDossiers: vi.fn().mockResolvedValue([]),
    listEcheances: vi.fn().mockResolvedValue([]),
    uploadJuridique: vi.fn().mockResolvedValue(undefined),
    openExercice: vi.fn().mockResolvedValue(undefined),
  },
}));
vi.mock('../../../services/ticket.service', () => ({ ticketService: {} }));
vi.mock('../../../services/workflow.service', () => ({
  workflowService: { registerPiece: vi.fn().mockResolvedValue(undefined) },
}));
vi.mock('../../tickets/CancelTicketDialog', () => ({ CancelTicketDialog: () => null }));

const { ImportWorkflowPage } = await import('../ImportWorkflowPage');
const { Step4Activite } = await import('../steps/Step4Activite');

const OBJET = 'Negoce et distribution de produits alimentaires';

beforeEach(() => {
  executeStep.mockClear();
  saveDraft.mockClear();
});

describe('C1 — IMPORT : dateDebutExercice saisissable a l’etape 4', () => {
  it('la page IMPORT expose le champ date a l’etape 4 (cablage importMode)', async () => {
    render(<ImportWorkflowPage />);
    // Le champ n'existe QUE si ImportWorkflowPage passe `importMode` a Step4Activite.
    await waitFor(() =>
      expect(screen.getByLabelText(/Date de début du premier exercice repris/i))
        .toBeInTheDocument(),
    );
  });

  it('le payload envoye a executeStep(4) porte dateDebutExercice — le champ exige par le backend', async () => {
    render(<ImportWorkflowPage />);

    fireEvent.change(await screen.findByLabelText(/Activites de la societe/i), {
      target: { value: OBJET },
    });
    fireEvent.change(screen.getByLabelText(/Date de début du premier exercice repris/i), {
      target: { value: '2024-01-01' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Valider et continuer/i }));

    await waitFor(() => expect(executeStep).toHaveBeenCalledTimes(1));
    const [step, payload] = executeStep.mock.calls[0];
    expect(step).toBe(4);
    // `require(p, "description", "dateDebutExercice")` cote ImportWorkflow.
    expect(payload).toMatchObject({ description: OBJET, dateDebutExercice: '2024-01-01' });
  });

  it('sans date saisie, l’etape n’est pas soumise (le backend la refuserait)', async () => {
    render(<ImportWorkflowPage />);
    fireEvent.change(await screen.findByLabelText(/Activites de la societe/i), {
      target: { value: OBJET },
    });
    fireEvent.click(screen.getByRole('button', { name: /Valider et continuer/i }));
    await waitFor(() => expect(executeStep).not.toHaveBeenCalled());
  });
});

describe('C1 — la CREATION reste de-dupliquee (le champ ne revient pas)', () => {
  it('hors importMode : ni champ date, ni cle dateDebutExercice dans le payload', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<Step4Activite saving={false} onSubmit={onSubmit} />);

    // En CREATION la date de commencement d'exercice est saisie a l'etape Capital.
    expect(screen.queryByLabelText(/Date de début du premier exercice repris/i)).toBeNull();

    fireEvent.change(screen.getByLabelText(/Activites de la societe/i), {
      target: { value: OBJET },
    });
    fireEvent.click(screen.getByRole('button', { name: /Valider et continuer/i }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0][0]).not.toHaveProperty('dateDebutExercice');
  });
});

describe('C1 — persistance : la date saisie survit a un retour d’etape', () => {
  it('re-hydrate dateDebutExercice depuis le bag step4 persiste', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <Step4Activite
        importMode
        saving={false}
        onSubmit={onSubmit}
        existing={{ activite: { description: OBJET, dateDebutExercice: '2023-07-01' } }}
      />,
    );

    expect(screen.getByLabelText(/Date de début du premier exercice repris/i))
      .toHaveValue('2023-07-01');

    fireEvent.click(screen.getByRole('button', { name: /Valider et continuer/i }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0][0]).toMatchObject({ dateDebutExercice: '2023-07-01' });
  });
});
