import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * Recette R2 (2026-10-17) : double authentification par SMS.
 * - L'option SMS suit le SERVEUR (setup-options) : indisponible sans fournisseur (production),
 *   ouverte et annoncee SIMULEE en environnement de test.
 * - Activation : saisie du numero, code recu, verification ; codes de recuperation a la premiere
 *   configuration ; lors d'un changement de methode, code de la methode actuelle exige et codes
 *   de recuperation conserves (aucune regeneration).
 */

const getSetup2faOptions = vi.fn();
const choose2faMethod = vi.fn();
const sendSmsOtp = vi.fn();
const verifySmsOtp = vi.fn();
const generateRecoveryCodes = vi.fn();
const setup2fa = vi.fn();

vi.mock('../../../services/auth.service', () => ({
  authService: {
    getSetup2faOptions: (...a: unknown[]) => getSetup2faOptions(...a),
    choose2faMethod: (...a: unknown[]) => choose2faMethod(...a),
    sendSmsOtp: (...a: unknown[]) => sendSmsOtp(...a),
    verifySmsOtp: (...a: unknown[]) => verifySmsOtp(...a),
    generateRecoveryCodes: (...a: unknown[]) => generateRecoveryCodes(...a),
    setup2fa: (...a: unknown[]) => setup2fa(...a),
    confirm2fa: vi.fn(),
  },
}));

vi.mock('../../../lib/api', () => ({
  tokenStorage: { setTokens: vi.fn(), getAccessToken: () => null, getRefreshToken: () => null, clear: () => undefined },
  extractError: (err: { response?: { data?: { message?: string } }; message?: string }) => ({
    message: err?.response?.data?.message ?? err?.message ?? 'Erreur',
    code: 'UNKNOWN',
  }),
}));

import { Choose2faMethodPage } from '../Choose2faMethodPage';

function rendre(chemin = '/account/2fa-choose') {
  return render(
    <MemoryRouter initialEntries={[chemin]}>
      <Choose2faMethodPage />
    </MemoryRouter>,
  );
}

const OPTIONS = { totpAvailable: true, smsAvailable: true, smsSimule: false, maskedPhone: null, methodeActive: null };

describe('<Choose2faMethodPage> : SMS', () => {
  beforeEach(() => {
    [getSetup2faOptions, choose2faMethod, sendSmsOtp, verifySmsOtp, generateRecoveryCodes, setup2fa].forEach((f) => f.mockReset());
    choose2faMethod.mockResolvedValue(undefined);
    sendSmsOtp.mockResolvedValue({ otpId: 'o1', maskedPhone: '********3344', expiresAt: '2026-10-17T10:05:00Z', simule: false });
    verifySmsOtp.mockResolvedValue({ accessToken: 'a', refreshToken: 'r' });
    generateRecoveryCodes.mockResolvedValue({ codes: ['AAAA-BBBB-CCCC-DDDD'] });
  });

  it('sans fournisseur de SMS (production) : option indisponible, aucun bouton, message clair', async () => {
    getSetup2faOptions.mockResolvedValue({ ...OPTIONS, smsAvailable: false });
    rendre();
    expect(await screen.findByTestId('2fa-sms-indisponible')).toHaveTextContent(/aucun fournisseur de SMS/i);
    expect(screen.queryByRole('button', { name: /^SMS/ })).not.toBeInTheDocument();
  });

  it('si les options ne se chargent pas, le SMS n’est jamais proposé par défaut', async () => {
    getSetup2faOptions.mockRejectedValue(new Error('réseau'));
    rendre();
    expect(await screen.findByTestId('2fa-sms-indisponible')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^SMS/ })).not.toBeInTheDocument();
  });

  it('en environnement de test : SMS signalé simulé, avec son aide', async () => {
    getSetup2faOptions.mockResolvedValue({ ...OPTIONS, smsSimule: true });
    rendre();
    const bouton = await screen.findByRole('button', { name: /^SMS/ });
    expect(bouton).toHaveTextContent(/simulé/i);
    expect(screen.getByRole('note', { name: /SMS simulé/i })).toHaveTextContent(/journal du service d’authentification/i);
  });

  it('première configuration : numéro saisi, code vérifié, codes de récupération', async () => {
    getSetup2faOptions.mockResolvedValue(OPTIONS);
    const u = userEvent.setup();
    rendre();
    await u.click(await screen.findByRole('button', { name: /^SMS/ }));
    await u.type(screen.getByLabelText(/Numéro de téléphone mobile/i), '+212611223344');
    await u.click(screen.getByRole('button', { name: /Recevoir le code/i }));
    await waitFor(() =>
      expect(sendSmsOtp).toHaveBeenCalledWith('2FA_SETUP', { telephone: '+212611223344', codeActuel: undefined }),
    );
    expect(choose2faMethod).toHaveBeenCalledWith('SMS');
    await u.type(await screen.findByLabelText(/Code reçu par SMS/i), '123456');
    await u.click(screen.getByRole('button', { name: /Activer la double authentification/i }));
    await waitFor(() => expect(verifySmsOtp).toHaveBeenCalledWith('2FA_SETUP', '123456'));
    expect(await screen.findByText('AAAA-BBBB-CCCC-DDDD')).toBeInTheDocument();
    expect(generateRecoveryCodes).toHaveBeenCalledTimes(1);
  });

  it('numéro mal formé : erreur affichée, aucun envoi', async () => {
    getSetup2faOptions.mockResolvedValue(OPTIONS);
    const u = userEvent.setup();
    rendre();
    await u.click(await screen.findByRole('button', { name: /^SMS/ }));
    await u.type(screen.getByLabelText(/Numéro de téléphone mobile/i), '0611');
    await u.click(screen.getByRole('button', { name: /Recevoir le code/i }));
    expect(await screen.findByText(/Saisissez le numéro au format international/i)).toBeInTheDocument();
    expect(sendSmsOtp).not.toHaveBeenCalled();
  });

  it('changement depuis l’application : code actuel exigé, codes de récupération conservés', async () => {
    getSetup2faOptions.mockResolvedValue({ ...OPTIONS, methodeActive: 'TOTP' });
    const u = userEvent.setup();
    rendre('/account/2fa-choose?reset=1');
    await u.click(await screen.findByRole('button', { name: /^SMS/ }));
    await u.type(screen.getByLabelText(/Numéro de téléphone mobile/i), '+212611223344');
    await u.type(screen.getByLabelText(/Code affiché par votre application/i), '654321');
    await u.click(screen.getByRole('button', { name: /Recevoir le code/i }));
    await waitFor(() =>
      expect(sendSmsOtp).toHaveBeenCalledWith('2FA_SETUP', { telephone: '+212611223344', codeActuel: '654321' }),
    );
    await u.type(await screen.findByLabelText(/Code reçu par SMS/i), '123456');
    await u.click(screen.getByRole('button', { name: /Activer la double authentification/i }));
    expect(await screen.findByText(/codes de récupération restent valables/i)).toBeInTheDocument();
    expect(generateRecoveryCodes).not.toHaveBeenCalled();
  });

  it('changement depuis le SMS : code demandé sur le numéro actuel', async () => {
    getSetup2faOptions.mockResolvedValue({ ...OPTIONS, methodeActive: 'SMS', maskedPhone: '********3344' });
    const u = userEvent.setup();
    rendre('/account/2fa-choose?reset=1');
    await u.click(await screen.findByRole('button', { name: /^SMS/ }));
    await u.click(screen.getByRole('button', { name: /Recevoir un code sur mon numéro actuel/i }));
    await waitFor(() => expect(sendSmsOtp).toHaveBeenCalledWith('2FA_LOGIN'));
    expect(screen.getByLabelText(/Code reçu sur votre numéro actuel/i)).toBeInTheDocument();
  });
});
