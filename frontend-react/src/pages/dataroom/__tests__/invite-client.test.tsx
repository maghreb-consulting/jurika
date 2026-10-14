import '@testing-library/jest-dom/vitest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';

/**
 * Lot L6 (E9) : les identifiants copies apres l'invitation d'un client portent le code du
 * cabinet de l'employe connecte. Ils portaient en dur « JUR-DEMO1 », faux pour tout autre
 * cabinet.
 */
const { inviteClient } = vi.hoisted(() => ({ inviteClient: vi.fn() }));
vi.mock('../../../services/auth.service', () => ({ authService: { inviteClient } }));
vi.mock('../../../store/authStore', () => ({ useCurrentUser: () => ({ role: 'EMPLOYE', workspaceCode: 'JUR-ATLAS' }) }));

import { InviteClientDrawer } from '../InviteClientDrawer';

describe('Invitation du client', () => {
  beforeEach(() => {
    inviteClient.mockReset();
    inviteClient.mockResolvedValue({ userId: 'u1', userCreated: true, temporaryPassword: 'Tmp-123456',
      loginEmail: 'karim.alaoui@jurika.ma', contactEmail: 'karim@exemple.ma', message: 'Client invité.' });
  });

  it('copie le code du cabinet connecte et l identifiant attribue', async () => {
    const writeText = vi.fn(async () => undefined);
    Object.assign(navigator, { clipboard: { writeText } });
    render(<InviteClientDrawer open dossierId="d1" raisonSociale="ATLAS SARL" onClose={() => undefined} />);
    fireEvent.change(screen.getByLabelText(/Prénom/), { target: { value: 'Karim' } });
    fireEvent.change(screen.getByLabelText(/^Nom/), { target: { value: 'Alaoui' } });
    fireEvent.change(screen.getByLabelText(/Adresse électronique/), { target: { value: 'karim@exemple.ma' } });
    fireEvent.click(screen.getByRole('button', { name: /Inviter/ }));
    fireEvent.click(await screen.findByRole('button', { name: /Copier/ }));
    const copie = writeText.mock.calls[0][0] as string;
    expect(copie).toContain('Code du cabinet : JUR-ATLAS');
    expect(copie).toContain('Identifiant : karim.alaoui@jurika.ma');
    expect(copie).toContain('Mot de passe temporaire : Tmp-123456');
    expect(copie).not.toContain('JUR-DEMO1');
  });
});
