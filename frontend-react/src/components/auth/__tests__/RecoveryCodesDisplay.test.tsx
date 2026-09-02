import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { RecoveryCodesDisplay } from '../RecoveryCodesDisplay';

// Sprint 14 bis / D2 — RecoveryCodesDisplay (RG-AU33 one-shot)
//
// 4 tests : rendu des 10 codes, bouton continue desactive par defaut,
// activation via checkbox + onContinue, action copy clipboard.

const CODES = [
  'A1B2-C3D4-E5F6-G7H8',
  'I9J0-K1L2-M3N4-O5P6',
  'Q7R8-S9T0-U1V2-W3X4',
  'Y5Z6-A7B8-C9D0-E1F2',
  'G3H4-I5J6-K7L8-M9N0',
  'O1P2-Q3R4-S5T6-U7V8',
  'W9X0-Y1Z2-A3B4-C5D6',
  'E7F8-G9H0-I1J2-K3L4',
  'M5N6-O7P8-Q9R0-S1T2',
  'U3V4-W5X6-Y7Z8-A9B0',
];

vi.mock('../../../store/authStore', () => ({
  useCurrentUser: () => ({
    userId: 'u1',
    workspaceId: 'w1',
    email: 'karim@jurika.ma',
    role: 'EMPLOYE',
  }),
}));

describe('RecoveryCodesDisplay', () => {
  beforeEach(() => {
    // jsdom n'implemente pas createObjectURL / clipboard nativement
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: vi.fn(() => 'blob:mock'),
      revokeObjectURL: vi.fn(),
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('affiche les 10 codes en clair avec leur index numerote', () => {
    render(
      <RecoveryCodesDisplay codes={CODES} onContinue={() => {}} context="INITIAL_SETUP" />,
    );

    for (const code of CODES) {
      expect(screen.getByText(code)).toBeInTheDocument();
    }
    expect(screen.getByText('01')).toBeInTheDocument();
    expect(screen.getByText('10')).toBeInTheDocument();
  });

  it('bouton Continuer desactive tant que la checkbox n\'est pas cochee', () => {
    render(
      <RecoveryCodesDisplay codes={CODES} onContinue={() => {}} context="INITIAL_SETUP" />,
    );

    const btn = screen.getByTestId('recovery-codes-continue');
    expect(btn).toBeDisabled();
  });

  it('coche la checkbox active le bouton et appelle onContinue au clic', async () => {
    const onContinue = vi.fn();
    const user = userEvent.setup();
    render(
      <RecoveryCodesDisplay codes={CODES} onContinue={onContinue} context="REGENERATION" />,
    );

    await user.click(screen.getByTestId('recovery-codes-checkbox'));
    const btn = screen.getByTestId('recovery-codes-continue');
    expect(btn).toBeEnabled();

    await user.click(btn);
    expect(onContinue).toHaveBeenCalledOnce();
  });

  it('clic sur "Copier" appelle navigator.clipboard.writeText avec les 10 codes', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    // navigator.clipboard est getter-only sous jsdom ; defineProperty contourne.
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      writable: true,
      value: { writeText },
    });

    render(
      <RecoveryCodesDisplay codes={CODES} onContinue={() => {}} context="INITIAL_SETUP" />,
    );

    fireEvent.click(screen.getByText(/Copier les 10 codes/i));
    await new Promise((r) => setTimeout(r, 0));

    expect(writeText).toHaveBeenCalledWith(CODES.join('\n'));
  });
});
