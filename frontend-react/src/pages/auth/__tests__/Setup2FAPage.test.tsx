import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { Setup2FAPage } from '../Setup2FAPage';

// Sprint 14 bis / D2 — Setup2FAPage parcours scan QR -> confirm code -> show recovery.
// 3 tests : QR code rendu apres setup2fa, erreur sur confirm rejette, transition
// vers etape SHOW_RECOVERY avec affichage des codes apres succes.

const setup2fa = vi.fn();
const confirm2fa = vi.fn();
const generateRecoveryCodes = vi.fn();

vi.mock('../../../services/auth.service', () => ({
  authService: {
    setup2fa: (...args: unknown[]) => setup2fa(...args),
    confirm2fa: (...args: unknown[]) => confirm2fa(...args),
    generateRecoveryCodes: (...args: unknown[]) => generateRecoveryCodes(...args),
  },
}));

// Hotfix 2026-06-04 — confirm2fa renvoie desormais un AuthTokens que la page
// persiste via tokenStorage. On mock le module entier pour ne pas toucher
// localStorage dans jsdom.
const setTokensMock = vi.fn();
vi.mock('../../../lib/api', () => ({
  tokenStorage: {
    setTokens: (...args: unknown[]) => setTokensMock(...args),
    getAccessToken: () => null,
    getRefreshToken: () => null,
    clear: () => undefined,
  },
  extractError: (err: { response?: { data?: { message?: string } }; message?: string }) => ({
    message: err?.response?.data?.message ?? err?.message ?? 'Erreur',
    code: 'UNKNOWN',
  }),
}));

vi.mock('../../../store/authStore', () => ({
  useCurrentUser: () => ({
    userId: 'u1',
    workspaceId: 'w1',
    email: 'karim@jurika.ma',
    role: 'EMPLOYE',
  }),
}));

function renderPage() {
  return render(
    <MemoryRouter>
      <Setup2FAPage />
    </MemoryRouter>,
  );
}

describe('Setup2FAPage', () => {
  beforeEach(() => {
    setup2fa.mockReset();
    confirm2fa.mockReset();
    generateRecoveryCodes.mockReset();
    setTokensMock.mockReset();
  });

  it('rend le QR code + secret apres resolution de setup2fa()', async () => {
    setup2fa.mockResolvedValue({
      secret: 'ABCDEF123456',
      otpAuthUri: 'otpauth://test',
      qrCodePngBase64: 'iVBORw0KGgo=',
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByAltText('QR code 2FA')).toBeInTheDocument();
    });
    expect(screen.getByText('ABCDEF123456')).toBeInTheDocument();
  });

  it('affiche un message d\'erreur si confirm2fa rejette', async () => {
    setup2fa.mockResolvedValue({
      secret: 'X',
      otpAuthUri: 'o',
      qrCodePngBase64: 'png',
    });
    confirm2fa.mockRejectedValue(
      Object.assign(new Error('Bad code'), {
        isAxiosError: true,
        response: { data: { message: 'Code 2FA invalide' } },
      }),
    );

    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByRole('textbox')).toBeInTheDocument());
    await user.type(screen.getByRole('textbox'), '123456');
    await user.click(screen.getByRole('button', { name: /Confirmer et activer/i }));

    await waitFor(() => {
      expect(screen.getByText(/Code 2FA invalide/i)).toBeInTheDocument();
    });
  });

  it('apres confirm OK + generateRecoveryCodes, affiche les codes en mode SHOW_RECOVERY', async () => {
    setup2fa.mockResolvedValue({
      secret: 'X',
      otpAuthUri: 'o',
      qrCodePngBase64: 'png',
    });
    // Hotfix 2026-06-04 — confirm2fa renvoie un AuthTokens (r2s=false)
    const tokensAfterConfirm = {
      accessToken: 'new.access.jwt',
      refreshToken: 'new.refresh.opaque',
      accessExpiresAt: new Date(Date.now() + 900_000).toISOString(),
      refreshExpiresAt: new Date(Date.now() + 604_800_000).toISOString(),
      userId: 'u1',
      workspaceId: 'w1',
    };
    confirm2fa.mockResolvedValue(tokensAfterConfirm);
    const codes = Array.from({ length: 10 }, (_, i) => `CODE${i.toString().padStart(2, '0')}-AAAA-BBBB-CCCC`);
    generateRecoveryCodes.mockResolvedValue({ codes, warning: 'one-shot' });

    const user = userEvent.setup();
    renderPage();

    await waitFor(() => expect(screen.getByRole('textbox')).toBeInTheDocument());
    await user.type(screen.getByRole('textbox'), '654321');
    await user.click(screen.getByRole('button', { name: /Confirmer et activer/i }));

    await waitFor(() => {
      expect(screen.getByText(/2FA activee — vos codes de recuperation/i)).toBeInTheDocument();
    });
    expect(screen.getByTestId('recovery-codes-display')).toBeInTheDocument();
    // Hotfix 2026-06-04 — tokenStorage.setTokens DOIT etre appele AVANT
    // generateRecoveryCodes, sinon l'ancien token (r2s=true) provoque un 403.
    expect(setTokensMock).toHaveBeenCalledWith(tokensAfterConfirm);
    expect(setTokensMock.mock.invocationCallOrder[0])
      .toBeLessThan(generateRecoveryCodes.mock.invocationCallOrder[0]);
    // 10 codes presents
    for (const c of codes) {
      expect(screen.getByText(c)).toBeInTheDocument();
    }
  });
});
