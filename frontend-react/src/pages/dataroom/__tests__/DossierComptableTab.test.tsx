import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { DossierComptableTab } from '../DossierComptableTab';
import type { DossierComptableView } from '../../../types/dataroom';

// Sprint 14 bis / D3 — DossierComptableTab (Sprint 7)
// 2 tests : 6 categories rendues + totaux, click categorie reload documents.

const getComptable = vi.fn();
const listComptableDocuments = vi.fn();
const uploadComptable = vi.fn();
const listExercices = vi.fn();

vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: {
    getComptable: (...args: unknown[]) => getComptable(...args),
    listComptableDocuments: (...args: unknown[]) => listComptableDocuments(...args),
    uploadComptable: (...args: unknown[]) => uploadComptable(...args),
    uploadComptableBatch: vi.fn(),
    deleteComptableDocument: vi.fn(),
    downloadComptableDocument: vi.fn(),
    listExercices: (...args: unknown[]) => listExercices(...args),
  },
}));

const VIEW: DossierComptableView = {
  dossierId: 'd1',
  annees: [2025, 2024],
  anneeCourante: 2025,
  totauxParCategorie: [
    { categorie: 'ACHATS', total: 12 },
    { categorie: 'VENTES', total: 8 },
    { categorie: 'BANQUE', total: 3 },
    { categorie: 'CAISSE', total: 0 },
    { categorie: 'NDF', total: 2 },
    { categorie: 'LA_PAIE', total: 5 },
  ],
};

describe('DossierComptableTab', () => {
  beforeEach(() => {
    getComptable.mockReset();
    listComptableDocuments.mockReset();
    uploadComptable.mockReset();
    listExercices.mockReset();
    listExercices.mockResolvedValue([]);
  });

  it('rend les 6 categories comptables avec leurs totaux', async () => {
    getComptable.mockResolvedValue(VIEW);
    listComptableDocuments.mockResolvedValue([]);

    render(<DossierComptableTab dossierId="d1" role="EMPLOYE" />);

    await waitFor(() => expect(screen.getByText(/Dossier comptable/i)).toBeInTheDocument());

    // Les 6 labels CATEGORIE_COMPTABLE sont rendus (Achats / Ventes / Banque / Caisse / NDF / Paie)
    expect(screen.getByText('Achats')).toBeInTheDocument();
    expect(screen.getByText('Ventes')).toBeInTheDocument();
    expect(screen.getByText('Banque')).toBeInTheDocument();
    // Totaux affiches
    expect(screen.getByText('12')).toBeInTheDocument();
    expect(screen.getByText('8')).toBeInTheDocument();
  });

  it('changer de categorie declenche un nouveau listComptableDocuments', async () => {
    getComptable.mockResolvedValue(VIEW);
    listComptableDocuments.mockResolvedValue([]);

    const user = userEvent.setup();
    render(<DossierComptableTab dossierId="d1" role="EMPLOYE" />);

    await waitFor(() => expect(listComptableDocuments).toHaveBeenCalledWith('d1', 2025, 'ACHATS'));

    await user.click(screen.getByText('Ventes'));
    await waitFor(() => expect(listComptableDocuments).toHaveBeenCalledWith('d1', 2025, 'VENTES'));
  });

  // Sprint 2026-06-23 — ajout d'une année arbitraire (ancien exercice).

  it('Ajouter une année 2022 -> option visible + selectedYear=2022 + upload cible 2022', async () => {
    getComptable.mockResolvedValue(VIEW);
    listComptableDocuments.mockResolvedValue([]);
    uploadComptable.mockResolvedValue(undefined);

    const user = userEvent.setup();
    render(<DossierComptableTab dossierId="d1" role="EMPLOYE" />);
    await waitFor(() =>
      expect(listComptableDocuments).toHaveBeenCalledWith('d1', 2025, 'ACHATS'),
    );

    await user.click(screen.getByTestId('comptable-add-year-toggle'));
    await user.type(screen.getByTestId('comptable-add-year-input'), '2022');
    await user.click(screen.getByTestId('comptable-add-year-submit'));

    // L'option 2022 doit apparaître dans le select.
    const select = screen.getByTestId('comptable-year-select') as HTMLSelectElement;
    expect(Array.from(select.options).some((o) => o.value === '2022')).toBe(true);
    // 2022 doit être la valeur sélectionnée.
    expect(select.value).toBe('2022');
    // Les documents rechargent pour 2022.
    await waitFor(() =>
      expect(listComptableDocuments).toHaveBeenCalledWith('d1', 2022, 'ACHATS'),
    );

    // Upload : doit cibler annee=2022.
    const file = new File(['x'], 'bilan-2022.pdf', { type: 'application/pdf' });
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(input, file);
    await waitFor(() => expect(uploadComptable).toHaveBeenCalledTimes(1));
    expect(uploadComptable).toHaveBeenCalledWith('d1', expect.objectContaining({
      annee: 2022,
      categorie: 'ACHATS',
    }));
  });

  it('Année hors bornes ou non numérique -> rejet avec message + pas d\'ajout', async () => {
    getComptable.mockResolvedValue(VIEW);
    listComptableDocuments.mockResolvedValue([]);

    const user = userEvent.setup();
    render(<DossierComptableTab dossierId="d1" role="EMPLOYE" />);
    await waitFor(() =>
      expect(listComptableDocuments).toHaveBeenCalledWith('d1', 2025, 'ACHATS'),
    );

    await user.click(screen.getByTestId('comptable-add-year-toggle'));
    // Saisir 'abcd' (rejeté par regex).
    await user.type(screen.getByTestId('comptable-add-year-input'), 'abcd');
    await user.click(screen.getByTestId('comptable-add-year-submit'));
    expect(await screen.findByTestId('comptable-add-year-error')).toHaveTextContent(
      /invalide/i,
    );
    let select = screen.getByTestId('comptable-year-select') as HTMLSelectElement;
    expect(Array.from(select.options).some((o) => o.value === 'abcd')).toBe(false);

    // Effacer + saisir 1500 (hors bornes).
    const input = screen.getByTestId('comptable-add-year-input') as HTMLInputElement;
    await user.clear(input);
    await user.type(input, '1500');
    await user.click(screen.getByTestId('comptable-add-year-submit'));
    expect(await screen.findByTestId('comptable-add-year-error')).toHaveTextContent(
      /hors bornes/i,
    );
    select = screen.getByTestId('comptable-year-select') as HTMLSelectElement;
    expect(Array.from(select.options).some((o) => o.value === '1500')).toBe(false);
  });
});
