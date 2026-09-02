import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { EcheancesPanel } from '../EcheancesPanel';
import type { EcheanceSummary } from '../../../types/dataroom';

// Le panneau doit afficher DEUX sections : « En retard » (échéances passées encore
// ouvertes, traitables) puis « 3 prochains mois ». Le bouton « Marquer traitée »
// fonctionne sur le retard ; après marquage, la ligne disparaît (reload).
const listEcheances = vi.fn();
const marquerEcheanceTraitee = vi.fn();

vi.mock('../../../services/dataroom.service', () => ({
  dataroomService: {
    listEcheances: (...a: unknown[]) => listEcheances(...a),
    marquerEcheanceTraitee: (...a: unknown[]) => marquerEcheanceTraitee(...a),
  },
}));

vi.mock('../../../components/ui/Toast', () => ({
  useToast: () => ({ error: vi.fn(), success: vi.fn(), info: vi.fn() }),
}));

const isoOffset = (days: number) => {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
};
const todayIso = isoOffset(0);

const ech = (over: Partial<EcheanceSummary>): EcheanceSummary => ({
  id: 'x',
  exerciceFiscalId: 'ex1',
  typeEcheance: 'TVA_MENSUELLE',
  dateEcheance: isoOffset(0),
  dateAlerte: isoOffset(0),
  statut: 'PLANIFIEE',
  documentId: null,
  sentAt: null,
  traiteAt: null,
  ...over,
});

const upcoming: EcheanceSummary[] = [
  ech({ id: 'up1', typeEcheance: 'TVA_TRIMESTRIELLE', dateEcheance: isoOffset(20) }),
];

describe('EcheancesPanel', () => {
  let past: EcheanceSummary[];

  beforeEach(() => {
    listEcheances.mockReset();
    marquerEcheanceTraitee.mockReset();
    past = [
      ech({ id: 'late1', typeEcheance: 'TVA_MENSUELLE', dateEcheance: isoOffset(-10), statut: 'PLANIFIEE' }),
      ech({ id: 'done1', typeEcheance: 'IS_ACOMPTE_T1', dateEcheance: isoOffset(-12), statut: 'TRAITEE' }),
    ];
    // upcoming = from == today ; retard = tout le reste (fenêtre passée).
    listEcheances.mockImplementation((_id: string, params: { from?: string }) =>
      Promise.resolve(params.from === todayIso ? upcoming : past),
    );
    marquerEcheanceTraitee.mockImplementation((id: string) => {
      past = past.filter((e) => e.id !== id);
      return Promise.resolve({});
    });
  });

  it('affiche le retard non traité dans « En retard » (TRAITEE exclue) + les à-venir', async () => {
    render(<EcheancesPanel dossierId="d1" canManage />);

    await waitFor(() => expect(screen.getByText('En retard')).toBeInTheDocument());
    // Retard PLANIFIEE affiché, avec badge « Depassee · 10 j ».
    expect(screen.getByText('TVA mensuelle')).toBeInTheDocument();
    expect(screen.getByText(/Depassee · 10 j/)).toBeInTheDocument();
    // L'échéance passée TRAITEE n'apparaît pas dans le retard.
    expect(screen.queryByText('IS acompte T1')).not.toBeInTheDocument();
    // Section à-venir présente.
    expect(screen.getByText('3 prochains mois')).toBeInTheDocument();
    expect(screen.getByText('TVA trimestrielle')).toBeInTheDocument();
  });

  it('marque une échéance en retard comme traitée puis la ligne disparaît', async () => {
    render(<EcheancesPanel dossierId="d1" canManage />);
    await waitFor(() => expect(screen.getByText('TVA mensuelle')).toBeInTheDocument());

    // Le 1er bouton « Traite » est celui de la section « En retard » (rendue en premier).
    fireEvent.click(screen.getAllByRole('button', { name: /Traite/i })[0]);
    // Confirmation.
    fireEvent.click(await screen.findByRole('button', { name: /Marquer traitee/i }));

    await waitFor(() => expect(marquerEcheanceTraitee).toHaveBeenCalledWith('late1'));
    // Après reload, l'échéance en retard a disparu.
    await waitFor(() => expect(screen.queryByText('TVA mensuelle')).not.toBeInTheDocument());
  });
});
