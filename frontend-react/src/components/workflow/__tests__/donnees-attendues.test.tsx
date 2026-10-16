import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

/**
 * Lot L4a (E1d) : une donnee attendue d'un organisme se saisit dans le bloc qui la
 * reclame ; quand toutes sont recues, l'acte est regenere d'office. Les identifiants de
 * la societe renvoient a sa fiche.
 */
const put = vi.fn();
vi.mock('../../../lib/api', () => ({
  api: { put: (...a: unknown[]) => put(...a), get: vi.fn(), post: vi.fn() },
  extractError: (e: unknown) => ({ message: String(e) }),
}));

import { DonneesAttendues } from '../DonneesAttendues';

const attendue = (variable: string, recue: boolean) => ({
  templateCode: 'ANNONCE_LEGALE_CONSTITUTION', workflowCode: 'CREATION_SARL', variable,
  libelle: variable === 'DATE_DEPOT_LEGAL' ? 'Date du dépôt légal' : 'ICE', reclameeLe: '2026-10-11T10:00:00Z', recue,
});

describe('Saisie des données attendues', () => {
  it('saisit la date du dépôt légal, puis régénère l’acte quand tout est reçu', async () => {
    put.mockResolvedValueOnce({ data: [attendue('DATE_DEPOT_LEGAL', true)] });
    const regenerer = vi.fn();
    render(<DonneesAttendues ticketId="t1" attendues={[attendue('DATE_DEPOT_LEGAL', false)]} onRegenerer={regenerer} />);
    fireEvent.change(screen.getByLabelText('Date du dépôt légal'), { target: { value: '2026-10-20' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));
    await waitFor(() => expect(regenerer).toHaveBeenCalledTimes(1));
    expect(put).toHaveBeenCalledWith('/workflows/t1/donnees-attendues/DATE_DEPOT_LEGAL', { valeur: '2026-10-20' });
    expect(screen.getByText('Reçue')).toBeInTheDocument();
  });

  it('L6b E14 : la regeneration a lieu meme si l\u2019ecran parent se redessine pendant la saisie', async () => {
    // Constate sur l'etape 7 : le parent passe `attendues.filter(...)`, un NOUVEAU tableau a chaque
    // rendu ; la liste mise a jour par la saisie etait alors ecartee et l'acte jamais regenere.
    let repondre: (v: unknown) => void = () => undefined;
    put.mockReturnValueOnce(new Promise((r) => { repondre = r; }));
    const regenerer = vi.fn();
    const { rerender } = render(<DonneesAttendues ticketId="t1" attendues={[attendue('DATE_DEPOT_LEGAL', false)]} onRegenerer={regenerer} />);
    fireEvent.change(screen.getByLabelText('Date du d\u00e9p\u00f4t l\u00e9gal'), { target: { value: '2026-10-20' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }));
    await waitFor(() => expect(put).toHaveBeenCalled());
    rerender(<DonneesAttendues ticketId="t1" attendues={[attendue('DATE_DEPOT_LEGAL', false)]} onRegenerer={regenerer} />);
    repondre({ data: [attendue('DATE_DEPOT_LEGAL', true)] });
    await waitFor(() => expect(screen.getByText('Re\u00e7ue')).toBeInTheDocument());
    rerender(<DonneesAttendues ticketId="t1" attendues={[attendue('DATE_DEPOT_LEGAL', false)]} onRegenerer={regenerer} />);
    await waitFor(() => expect(regenerer).toHaveBeenCalledTimes(1));
  });

  it('renvoie un identifiant de la société à sa fiche, sans champ de saisie', () => {
    render(<DonneesAttendues ticketId="t1" attendues={[attendue('ICE', false)]} />);
    expect(screen.queryByRole('button', { name: 'Enregistrer' })).toBeNull();
    expect(screen.getByText(/Identifiants de la société/)).toBeInTheDocument();
  });
});
