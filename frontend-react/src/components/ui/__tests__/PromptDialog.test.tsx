import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PromptDialog } from '../PromptDialog';

describe('PromptDialog', () => {
  it('bloque la confirmation quand validate renvoie une erreur (aucun onConfirm)', async () => {
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    render(
      <PromptDialog
        open
        onOpenChange={() => {}}
        title="Motif de deverrouillage"
        label="Motif"
        confirmLabel="Valider"
        validate={(v) => (v.trim().length >= 3 ? null : 'Trop court.')}
        onConfirm={onConfirm}
      />,
    );

    // Saisie trop courte → message in-app, pas d'appel onConfirm.
    await user.type(screen.getByLabelText('Motif'), 'ab');
    await user.click(screen.getByRole('button', { name: 'Valider' }));

    expect(screen.getByText('Trop court.')).toBeInTheDocument();
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('confirme avec la valeur saisie quand valide', async () => {
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    render(
      <PromptDialog
        open
        onOpenChange={() => {}}
        title="Motif de deverrouillage"
        label="Motif"
        confirmLabel="Valider"
        validate={(v) => (v.trim().length >= 3 ? null : 'Trop court.')}
        onConfirm={onConfirm}
      />,
    );

    await user.type(screen.getByLabelText('Motif'), 'valable');
    await user.click(screen.getByRole('button', { name: 'Valider' }));

    expect(onConfirm).toHaveBeenCalledWith('valable');
  });
});
