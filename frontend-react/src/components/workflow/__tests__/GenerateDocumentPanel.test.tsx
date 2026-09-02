/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { GenerateDocumentPanel } from '../GenerateDocumentPanel';
import * as service from '../../../services/workflowDocumentService';

const TEMPLATE_FIXTURE: service.TemplateInfo[] = [
  {
    code: 'STATUTS_CONSTITUTIFS_SARL',
    documentKind: 'Statuts constitutifs SARL',
    file: 'STATUTS_CONSTITUTIFS_SARL.docx',
    origin: 'directeur',
    deprecated: false,
    placeholderStyle: 'BRACKET',
  },
];

beforeEach(() => {
  // jsdom n'a pas createObjectURL ni revokeObjectURL
  // @ts-expect-error — assignement test
  window.URL.createObjectURL = vi.fn(() => 'blob:fake');
  // @ts-expect-error — assignement test
  window.URL.revokeObjectURL = vi.fn();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('<GenerateDocumentPanel>', () => {
  it('rend les templates apres chargement initial', async () => {
    vi.spyOn(service, 'listTemplatesForWorkflow').mockResolvedValue(TEMPLATE_FIXTURE);

    render(
      <GenerateDocumentPanel
        workflowCodes={['CREATION_SARL']}
        buildPayload={() => ({ raisonSociale: 'ACME' })}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText('Statuts constitutifs SARL')).toBeInTheDocument();
    });
    expect(screen.getByTestId('generate-btn-STATUTS_CONSTITUTIFS_SARL')).toBeInTheDocument();
  });

  it('appelle generateDocument avec le bon templateCode et payload', async () => {
    vi.spyOn(service, 'listTemplatesForWorkflow').mockResolvedValue(TEMPLATE_FIXTURE);
    const generateSpy = vi
      .spyOn(service, 'generateDocument')
      .mockResolvedValue({ blob: new Blob(['x']), filename: 'out.docx' });

    const payload = { raisonSociale: 'ACME' };
    render(
      <GenerateDocumentPanel
        workflowCodes={['CREATION_SARL']}
        buildPayload={() => payload}
      />,
    );

    const btn = await screen.findByTestId('generate-btn-STATUTS_CONSTITUTIFS_SARL');
    fireEvent.click(btn);

    await waitFor(() => {
      expect(generateSpy).toHaveBeenCalledWith(
        'CREATION_SARL',
        'STATUTS_CONSTITUTIFS_SARL',
        payload,
      );
    });
  });

  it('affiche le empty state quand manifest vide', async () => {
    vi.spyOn(service, 'listTemplatesForWorkflow').mockResolvedValue([]);

    render(
      <GenerateDocumentPanel
        workflowCodes={['CREATION_SARL']}
        buildPayload={() => ({})}
      />,
    );

    // L'empty state porte le libelle a deux endroits (titre + texte explicatif) :
    // on cible le titre, et on verifie que l'explication est bien presente.
    await waitFor(() => {
      expect(
        screen.getByRole('heading', { name: /Aucun document disponible/i }),
      ).toBeInTheDocument();
    });
    expect(
      screen.getByText(/Aucun document disponible pour ce workflow \(manifest vide\)/i),
    ).toBeInTheDocument();
  });

  it('affiche un bouton Réessayer en cas d erreur generation', async () => {
    vi.spyOn(service, 'listTemplatesForWorkflow').mockResolvedValue(TEMPLATE_FIXTURE);
    vi.spyOn(service, 'generateDocument').mockRejectedValue(new Error('boom'));

    render(
      <GenerateDocumentPanel
        workflowCodes={['CREATION_SARL']}
        buildPayload={() => ({})}
      />,
    );

    const btn = await screen.findByTestId('generate-btn-STATUTS_CONSTITUTIFS_SARL');
    fireEvent.click(btn);

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/boom/i);
      expect(screen.getByText(/Réessayer/i)).toBeInTheDocument();
    });
  });
});
