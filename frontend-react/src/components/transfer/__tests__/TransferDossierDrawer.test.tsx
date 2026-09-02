/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';

// Mock services AVANT import du composant.
const listChatContacts = vi.fn();
vi.mock('../../../services/auth.service', () => ({
  authService: { listChatContacts: () => listChatContacts() },
}));
vi.mock('../../../services/transfer.service', () => ({
  transferService: { requestTransfer: vi.fn() },
}));

import { TransferDossierDrawer } from '../TransferDossierDrawer';

const EMPLOYE = {
  userId: 'emp-1', firstName: 'Sara', lastName: 'Bennani',
  email: 'sara@jurika.ma', role: 'EMPLOYE', status: 'ACTIVE',
};
const SUPERVISEUR = {
  userId: 'sup-1', firstName: 'Karim', lastName: 'Idrissi',
  email: 'karim@jurika.ma', role: 'SUPERVISEUR', status: 'ACTIVE',
};

describe('<TransferDossierDrawer>', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('exclut les SUPERVISEUR du picker de destinataires (seuls les EMPLOYE)', async () => {
    listChatContacts.mockResolvedValue([EMPLOYE, SUPERVISEUR]);

    render(
      <TransferDossierDrawer
        open
        dossierId="d-1"
        raisonSociale="ACME SARL"
        onClose={vi.fn()}
      />,
    );

    // L'option EMPLOYE apparaît...
    await waitFor(() =>
      expect(screen.getByRole('option', { name: /Sara Bennani/ })).toBeInTheDocument(),
    );
    // ...le SUPERVISEUR est absent du <select>.
    expect(screen.queryByRole('option', { name: /Karim Idrissi/ })).not.toBeInTheDocument();
    // Plus aucun libellé "(Superviseur)" (branche morte nettoyée).
    expect(screen.queryByText(/Superviseur/i)).not.toBeInTheDocument();
  });
});
