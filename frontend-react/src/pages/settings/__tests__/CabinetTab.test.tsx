import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

/**
 * Lot L5b (E3, RG-FIC-08) : l'apercu du papier a en-tete annonce exactement les documents
 * qui le portent (fiche client, fiche dossier, etat des debours, en PDF) et porte son aide.
 */

vi.mock('../../../services/workspace.service', () => ({
  workspaceService: {
    getProfile: () =>
      Promise.resolve({
        name: 'Maghreb Consulting', professionalType: null, ice: '002345678000089', city: 'Casablanca',
        contactEmail: 'contact@cabinet.ma', iceMissing: false, nomAfficheDocuments: null,
        documentDisplayName: 'Maghreb Consulting', adresse: '12, rue des Consultants', telephone: null,
        siteWeb: null, rcNumber: 'RC-45219', ifFiscal: null, hasLogo: false,
      }),
    getLogoObjectUrl: () => Promise.resolve(null),
  },
}));

import { CabinetTab } from '../CabinetTab';

describe('Onglet Cabinet : papier a en-tete des documents de synthese', () => {
  it('annonce les trois documents PDF et porte son aide', async () => {
    render(<CabinetTab />);
    expect(
      await screen.findByText(/documents de synthèse en PDF \(fiche client, fiche dossier, état des débours\)/),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Où apparaît ce papier à en-tête ?' })).toBeInTheDocument();
    expect(screen.getByText(/Édité par JURIKA le/)).toBeInTheDocument();
    expect(screen.queryByText(/Word/)).toBeNull();
  });
});
