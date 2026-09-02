import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { RecoveryCodeForm } from '../RecoveryCodeForm';

// Sprint 14 bis / D2 — RecoveryCodeForm (B4, RG-AU41/42)
//
// 2 tests : format input auto XXXX-XXXX-XXXX-XXXX + validation 16 chars,
// submit appelle onSubmit avec le code normalise (sans tirets, uppercase).

describe('RecoveryCodeForm', () => {
  it('formate la saisie en 4 groupes de 4 (uppercase + strip non-alphanum)', async () => {
    const user = userEvent.setup();
    render(
      <RecoveryCodeForm
        workspaceCode="JUR-DEMO1"
        email="karim@jurika.ma"
        loading={false}
        error={null}
        onSubmit={() => {}}
      />,
    );

    const input = screen.getByLabelText(/Code de recuperation/i) as HTMLInputElement;
    await user.type(input, 'a1b2c3d4e5f6g7h8');
    expect(input.value).toBe('A1B2-C3D4-E5F6-G7H8');

    // Bouton activable car 16 chars atteints
    expect(screen.getByRole('button', { name: /Verifier & Acceder/i })).toBeEnabled();
  });

  it('submit appelle onSubmit avec le code normalise (sans tirets)', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(
      <RecoveryCodeForm
        workspaceCode="JUR-DEMO1"
        email="karim@jurika.ma"
        loading={false}
        error={null}
        onSubmit={onSubmit}
      />,
    );

    const input = screen.getByLabelText(/Code de recuperation/i);
    await user.type(input, 'a1b2-c3d4-e5f6-g7h8');

    await user.click(screen.getByRole('button', { name: /Verifier & Acceder/i }));
    expect(onSubmit).toHaveBeenCalledWith('A1B2C3D4E5F6G7H8');
  });
});
