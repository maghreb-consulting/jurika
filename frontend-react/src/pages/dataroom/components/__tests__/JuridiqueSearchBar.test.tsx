import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { DEFAULT_FILTERS, JuridiqueSearchBar, type SearchFilters } from '../JuridiqueSearchBar';

// Sprint 14 bis / D3 — JuridiqueSearchBar
// 3 tests : input rend la query, debounce 300ms onChange, drawer s'ouvre.

describe('JuridiqueSearchBar', () => {
  it('rend la query courante dans l\'input', () => {
    const filters: SearchFilters = { ...DEFAULT_FILTERS, q: 'statuts' };
    render(<JuridiqueSearchBar filters={filters} onChange={() => {}} />);

    expect(screen.getByDisplayValue('statuts')).toBeInTheDocument();
  });

  it('debounce 300ms la query : onChange n\'est PAS appele instantanement', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();

    render(<JuridiqueSearchBar filters={DEFAULT_FILTERS} onChange={onChange} />);

    const input = screen.getByPlaceholderText(/Rechercher dans les documents/i);
    await user.type(input, 'sarl');

    // Pas encore d'appel immediat
    expect(onChange).not.toHaveBeenCalled();

    // Attendre que le debounce flush (300ms)
    await waitFor(() => expect(onChange).toHaveBeenCalled(), { timeout: 600 });
    const lastCall = onChange.mock.calls.at(-1)!;
    expect(lastCall[0].q).toBe('sarl');
  });

  it('clic sur "Filtres" ouvre le drawer avec les options de type', async () => {
    const user = userEvent.setup();
    render(<JuridiqueSearchBar filters={DEFAULT_FILTERS} onChange={() => {}} />);

    await user.click(screen.getByRole('button', { name: /Filtres/i }));

    // Le drawer affiche son titre "Filtres de recherche" + section "Type de document"
    await waitFor(() => {
      expect(screen.getByText(/Filtres de recherche/i)).toBeInTheDocument();
    });
    expect(screen.getByText(/Type de document/i)).toBeInTheDocument();
  });
});
