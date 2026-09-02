import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitForElementToBeRemoved } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ToastProvider, useToast } from '../Toast';

function Trigger() {
  const toast = useToast();
  return (
    <div>
      {/* durée courte pour tester l'auto-dismiss sans fake timers */}
      <button onClick={() => toast.success('Source ajoutée', 400)}>ok</button>
      <button onClick={() => toast.error('Échec', 0)}>ko</button>
    </div>
  );
}

describe('Toast', () => {
  it('affiche un toast de succès puis l’auto-dismiss', async () => {
    const user = userEvent.setup();
    render(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    );

    await user.click(screen.getByText('ok'));
    const toast = screen.getByText('Source ajoutée');
    expect(toast).toBeInTheDocument();

    // Auto-dismiss après la durée fournie (400 ms).
    await waitForElementToBeRemoved(() => screen.queryByText('Source ajoutée'), {
      timeout: 2000,
    });
  });

  it('fermeture manuelle via le bouton (toast persistant)', async () => {
    const user = userEvent.setup();
    render(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    );

    await user.click(screen.getByText('ko'));
    expect(screen.getByText('Échec')).toBeInTheDocument();

    await user.click(screen.getByLabelText('Fermer la notification'));
    expect(screen.queryByText('Échec')).not.toBeInTheDocument();
  });

  it('useToast hors provider lève une erreur', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => render(<Trigger />)).toThrow(/ToastProvider/);
    spy.mockRestore();
  });
});
