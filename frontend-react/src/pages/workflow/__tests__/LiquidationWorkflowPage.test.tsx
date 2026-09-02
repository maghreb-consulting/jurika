import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * Lot « Liquidation 4 étapes » (2026-08-13) — garde-fous d'ANTI-DUPLICATION.
 *
 * Le liquidateur est nommé lors de la DISSOLUTION et persisté au dossier ; la date de
 * dissolution vit en base. L'étape 1 de la liquidation doit donc les AFFICHER en lecture
 * seule, sans aucun champ de saisie. Le cas de secours (dossier dissous avant cette
 * évolution) doit rester fonctionnel : le sélecteur de liquidateur réapparaît alors.
 */

const DOSSIER_ID = '3fa85f64-5717-4562-b3fc-2c963f66afa6';

const getDossierParties = vi.fn();
/** Succursales encore ouvertes : elles bloquent la clôture (lot DIVERS, 2026-08-13). */
const listSuccursales = vi.fn();

vi.mock('../../../services/workflow.service', () => ({
  workflowService: {
    getDossierParties: (...args: unknown[]) => getDossierParties(...args),
    listSuccursales: (...args: unknown[]) => listSuccursales(...args),
  },
}));

vi.mock('react-router-dom', () => ({ useNavigate: () => vi.fn() }));

// La page s'enveloppe dans <WorkflowBoot> (montage différé : les champs ne sont
// montés qu'une fois les données chargées). Ici `useWorkflow` est déjà mocké et
// fournit les données d'emblée — le boot n'a donc rien à retarder.
vi.mock('../WorkflowBoot', () => ({
  WorkflowBoot: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock('../useWorkflow', () => ({
  useWorkflow: () => ({
    ticket: { id: 'ticket-1', titre: 'PARACOSME', dossierId: DOSSIER_ID },
    progress: { statut: 'EN_COURS' },
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
  }),
}));

// Coquilles légères : ces sous-composants ont leurs propres tests.
vi.mock('../../../components/workflow/WorkflowShell', () => ({
  WorkflowShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));
vi.mock('../../../components/workflow/DossierAutocomplete', () => ({
  DossierAutocomplete: () => <div data-testid="dossier-autocomplete" />,
}));
vi.mock('../../../components/workflow/SeanceDocPanels', () => ({
  ConvocationPanel: () => <div data-testid="convocation-panel" />,
  FeuillePresencePanel: () => <div data-testid="feuille-panel" />,
}));
vi.mock('../../../components/identity/IdentityExtractor', () => ({
  IdentityExtractor: () => <div data-testid="identity-extractor" />,
}));
vi.mock('../../tickets/CancelTicketDialog', () => ({ CancelTicketDialog: () => null }));

const { LiquidationWorkflowPage } = await import('../LiquidationWorkflowPage');

/** Dossier dissous APRÈS l'évolution : liquidateur + date de dissolution en base. */
function partiesAvecLiquidateur() {
  return {
    associes: [],
    gerants: [],
    denomination: 'PARACOSME',
    formeJuridique: 'SARL',
    statut: 'DISSOUTE',
    dateDissolution: '2026-05-15',
    siegeLiquidation: '12 RUE DES FOULES, CASABLANCA',
    liquidateur: {
      source: 'BD',
      civilite: 'M.',
      prenom: 'Ahmed',
      nom: 'ALAOUI',
      adresse: '45 BD ZERKTOUNI, CASABLANCA',
    },
  };
}

/** Dossier dissous AVANT l'évolution : aucun liquidateur enregistré. */
function partiesSansLiquidateur() {
  return {
    associes: [{ nom: 'BENALI', prenom: 'Karim', civilite: 'M.', nombreParts: 1000 }],
    gerants: [{ nom: 'BENALI', prenom: 'Karim', civilite: 'M.' }],
    denomination: 'PARACOSME',
    formeJuridique: 'SARL',
    statut: 'DISSOUTE',
    dateDissolution: '2026-05-15',
  };
}

beforeEach(() => {
  getDossierParties.mockReset();
  listSuccursales.mockReset();
  listSuccursales.mockResolvedValue([]);
});

describe('LiquidationWorkflowPage — étape 1, anti-duplication', () => {
  it('PRÉ-REMPLIT le liquidateur depuis la BD sans le verrouiller, CIN compris', async () => {
    // Anti-duplication ≠ verrouillage : la BD alimente les champs, elle ne doit pas
    // interdire de compléter ce qu'elle ne porte pas (le N° CIN, typiquement).
    getDossierParties.mockResolvedValue(partiesAvecLiquidateur());
    render(<LiquidationWorkflowPage />);

    await waitFor(() => {
      expect(screen.getByTestId('liquidation-liquidateur-bd')).toHaveTextContent('M. Ahmed ALAOUI');
    });
    expect(screen.getByTestId('liquidation-date-dissolution-bd')).toHaveTextContent('2026-05-15');
    expect(screen.getByTestId('liquidation-faits-bd')).toHaveTextContent(
      '12 RUE DES FOULES, CASABLANCA',
    );

    // Les champs d'identité sont présents ET pré-remplis depuis la BD.
    const picker = await screen.findByTestId('liquidateur-picker');
    expect(picker).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByTestId('liquidateur-nom')).toHaveValue('ALAOUI');
    });
    expect(screen.getByTestId('liquidateur-prenom')).toHaveValue('Ahmed');

    // Aucun d'eux n'est verrouillé : c'était le défaut — un CIN manquant en base
    // ne pouvait alors plus être renseigné nulle part.
    expect(screen.getByTestId('liquidateur-nom')).not.toBeDisabled();
    expect(screen.getByTestId('liquidateur-prenom')).not.toBeDisabled();
    expect(screen.getByTestId('liquidateur-cin')).not.toBeDisabled();
    expect(screen.getByTestId('liquidateur-civilite')).not.toBeDisabled();

    // Le CIN apparaît aussi au récapitulatif : il est publié dans l'annonce.
    expect(screen.getByTestId('liquidation-liquidateur-cin-bd')).toBeInTheDocument();

    // La date de dissolution, elle, reste lue en base : pas de saisie de secours.
    expect(screen.queryByTestId('liquidation-secours-date')).not.toBeInTheDocument();
  });

  it('cas de secours : sans liquidateur en base, le sélecteur est proposé une fois', async () => {
    getDossierParties.mockResolvedValue(partiesSansLiquidateur());
    render(<LiquidationWorkflowPage />);

    await waitFor(() => {
      expect(screen.getByTestId('liquidation-secours-liquidateur')).toBeInTheDocument();
    });
    expect(screen.getByTestId('liquidateur-picker')).toBeInTheDocument();
    expect(screen.getByTestId('liquidation-liquidateur-bd')).toHaveTextContent('non enregistre');
    // Champs vides : rien a reprendre de la BD.
    expect(screen.getByTestId('liquidateur-nom')).toHaveValue('');
    // La date de dissolution, elle, reste lue en base : pas de saisie de secours.
    expect(screen.queryByTestId('liquidation-secours-date')).not.toBeInTheDocument();
  });

  it('cas de secours : sans date de dissolution en base, la saisie est proposée', async () => {
    const parties = partiesAvecLiquidateur();
    delete (parties as { dateDissolution?: string }).dateDissolution;
    getDossierParties.mockResolvedValue(parties);
    render(<LiquidationWorkflowPage />);

    await waitFor(() => {
      expect(screen.getByTestId('liquidation-secours-date')).toBeInTheDocument();
    });
    // Le liquidateur, lui, est repris de la BD : pré-rempli, donc aucune re-saisie
    // — mais restant corrigeable (le CIN manque souvent en Data Room).
    // `waitFor` car le pré-remplissage passe par un effet, résolu après le render.
    await waitFor(() => {
      expect(screen.getByTestId('liquidateur-nom')).toHaveValue('ALAOUI');
    });
  });

  it('expose les comptes finaux et le calcul boni/mali, sans champ de résultat saisissable', async () => {
    getDossierParties.mockResolvedValue(partiesAvecLiquidateur());
    render(<LiquidationWorkflowPage />);

    await waitFor(() => {
      expect(screen.getByTestId('liquidation-comptes')).toBeInTheDocument();
    });
    expect(screen.getByTestId('liquidation-total-actif')).toBeInTheDocument();
    expect(screen.getByTestId('liquidation-total-passif')).toBeInTheDocument();
    // Le boni/mali est un AFFICHAGE calculé, pas une saisie.
    const resultat = screen.getByTestId('liquidation-boni-mali');
    expect(resultat.tagName).toBe('P');
    expect(resultat).toHaveTextContent('—');
  });

  it("avertit dès l'étape 1 des succursales encore ouvertes", async () => {
    // Une succursale ne survit pas à la radiation de sa société : la clôture sera
    // refusée. L'employé doit l'apprendre AVANT de générer PV, rapport et annonce.
    getDossierParties.mockResolvedValue(partiesAvecLiquidateur());
    listSuccursales.mockResolvedValue([
      { id: 's1', denomination: 'Agence Marrakech', ville: 'Marrakech', statut: 'ACTIVE' },
      { id: 's2', denomination: 'Agence Fès', ville: 'Fès', statut: 'ACTIVE' },
      { id: 's3', denomination: 'Agence Tanger', ville: 'Tanger', statut: 'FERMEE' },
    ]);
    render(<LiquidationWorkflowPage />);

    const alerte = await screen.findByTestId('liquidation-succursales-ouvertes');
    // Seules les ACTIVE comptent : la succursale déjà fermée est exclue.
    expect(alerte).toHaveTextContent('2 succursales');
    expect(alerte).toHaveTextContent('Agence Marrakech');
    expect(alerte).toHaveTextContent('Agence Fès');
    expect(alerte).not.toHaveTextContent('Agence Tanger');
  });

  it("n'affiche aucun avertissement quand toutes les succursales sont fermées", async () => {
    getDossierParties.mockResolvedValue(partiesAvecLiquidateur());
    listSuccursales.mockResolvedValue([
      { id: 's1', denomination: 'Agence Marrakech', ville: 'Marrakech', statut: 'FERMEE' },
    ]);
    render(<LiquidationWorkflowPage />);

    await waitFor(() => {
      expect(screen.getByTestId('liquidation-faits-bd')).toBeInTheDocument();
    });
    expect(screen.queryByTestId('liquidation-succursales-ouvertes')).not.toBeInTheDocument();
  });
});
