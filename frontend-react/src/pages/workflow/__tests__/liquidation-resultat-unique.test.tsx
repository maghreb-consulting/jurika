import { render, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { calculer, type RapportLiquidateur } from '../../../lib/rapportLiquidateur';

/**
 * Lot L6b (E19, decision du 2026-10-17) : le resultat de la liquidation a une seule source,
 * le rapport final du liquidateur, calcule par le serveur. L'ecran n'envoie plus de sens ni de
 * montant de resultat (il envoyait actif - passif : "boni 100 000" pour un mali de 5 000) ;
 * la synthese affiche le meme calcul en miroir (lib/rapportLiquidateur.ts).
 */
const DOSSIER_ID = '3fa85f64-5717-4562-b3fc-2c963f66afa6';
const { operations } = vi.hoisted(() => ({ operations: [] as Record<string, unknown>[] }));

vi.mock('../../../services/workflow.service', () => ({
  workflowService: {
    getDossierParties: vi.fn(async () => ({
      associes: [], gerants: [], denomination: 'ATLAS NORD SARL', formeJuridique: 'SARL', statut: 'DISSOUTE',
      dateDissolution: '2026-09-17', capitalSocial: 100000,
      liquidateur: { source: 'BD', civilite: 'M.', prenom: 'Mehdi', nom: 'Alaoui', adresse: '8 rue des Essais' },
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
    stepData: {
      step1: {
        dossierId: DOSSIER_ID, dateClotureLiquidation: '2026-10-06',
        comptesFinaux: { totalActif: 120000, totalPassif: 20000 },
      },
    },
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

/** Rapport final du parcours de bout en bout : une realisation, un apurement, un frais. */
function rapport(actif: string): RapportLiquidateur {
  return {
    realisations: [{ designation: 'Fonds de commerce', prix: actif, valeurInventaire: actif }],
    apurements: [{ creancier: 'BANQUE', montantAdmis: '20000', montantRegle: '20000' }],
    frais: [{ nature: 'Honoraires', montant: '5000' }],
  } as RapportLiquidateur;
}

describe('Liquidation : resultat a source unique (rapport final)', () => {
  it("le PV et le rapport ne recoivent de l'ecran ni sens ni montant de resultat", async () => {
    render(<LiquidationWorkflowPage />);
    await waitFor(() => expect(operations.length).toBeGreaterThan(0));
    const cloture = (operations[operations.length - 1] as { cloture?: Record<string, unknown> }).cloture ?? {};
    expect(cloture.actifRealise).toBe('120000');
    expect(cloture.passifRegle).toBe('20000');
    for (const cle of ['resultatSens', 'resultatType', 'resultatMontant', 'boniExiste', 'boniMontant', 'maliMontant']) {
      expect(cloture, cle).not.toHaveProperty(cle);
    }
  });

  it('le miroir de la synthese suit la regle du serveur : mali de 5 000, boni de 25 000', () => {
    const ctx = { capital: 100000, remuneration: 0, totalActif: '120000', totalPassif: '20000', fraisPv: '5000' };
    expect(calculer(rapport('120000'), ctx).resultat).toEqual({ sens: 'mali', montant: 500000 });
    expect(calculer(rapport('150000'), { ...ctx, totalActif: '150000' }).resultat).toEqual({ sens: 'boni', montant: 2500000 });
  });
});
