import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

/**
 * Lot L6b (E7) : le tiroir "Nouveau ticket" (<dialog>). Focus au premier champ,
 * Echap ferme, le bouton de creation reste dans le pied (le formulaire long defile).
 */
const { create } = vi.hoisted(() => ({ create: vi.fn() }));
vi.mock('../../../services/ticket.service', () => ({
  ticketService: { create: (...a: unknown[]) => create(...a) },
}));

import { NewTicketDrawer } from '../NewTicketDrawer';

function monter() {
  const onClose = vi.fn();
  const onCreated = vi.fn(async () => {});
  render(
    <MemoryRouter>
      <NewTicketDrawer open onClose={onClose} onCreated={onCreated} />
    </MemoryRouter>,
  );
  return { onClose, onCreated };
}

describe('Tiroir « Nouveau ticket »', () => {
  it('place le focus sur le titre a l’ouverture', () => {
    monter();
    expect(screen.getByLabelText('Titre')).toHaveFocus();
  });

  it('Echap ferme le tiroir', () => {
    const { onClose } = monter();
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });
    expect(onClose).toHaveBeenCalled();
  });

  it('le bouton du pied soumet le formulaire : titre requis nomme, puis creation', async () => {
    create.mockResolvedValue({ id: 't-9' });
    const { onCreated } = monter();
    await userEvent.click(screen.getByRole('button', { name: 'Créer et ouvrir le parcours' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Le titre est requis.');
    expect(create).not.toHaveBeenCalled();

    await userEvent.type(screen.getByLabelText('Titre'), 'Constitution Atlas');
    await userEvent.type(screen.getByLabelText('Raison sociale'), 'ATLAS SARL');
    await userEvent.click(screen.getByRole('button', { name: 'Créer et ouvrir le parcours' }));
    await waitFor(() => expect(create).toHaveBeenCalled());
    expect(create.mock.calls[0][0]).toMatchObject({
      titre: 'Constitution Atlas',
      type: 'CREATION',
      companyInfo: { raisonSociale: 'ATLAS SARL', formeJuridique: 'SARL' },
    });
    await waitFor(() => expect(onCreated).toHaveBeenCalled());
  });
});
