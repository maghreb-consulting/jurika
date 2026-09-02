/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DocumentVersionsDropdown } from '../DocumentVersionsDropdown';
import { dataroomService } from '../../../../services/dataroom.service';
import type { DocumentSummary } from '../../../../types/dataroom';

const DOC_ID = 'doc-statuts';

const versions: DocumentSummary[] = [
  {
    id: 'v3',
    dossierId: 'd1',
    ticketId: null,
    documentType: 'STATUTS',
    title: 'Statuts',
    version: 3,
    current: true,
    filename: 'statuts-v3.docx',
    contentType: null,
    sizeBytes: 100,
    createdAt: '2026-06-23T10:00:00Z',
    replacedAt: null,
    motif: null,
  },
  {
    id: 'v2',
    dossierId: 'd1',
    ticketId: null,
    documentType: 'STATUTS',
    title: 'Statuts',
    version: 2,
    current: false,
    filename: 'statuts-v2.docx',
    contentType: null,
    sizeBytes: 100,
    createdAt: '2026-06-22T10:00:00Z',
    replacedAt: '2026-06-23T10:00:00Z',
    motif: 'Modification : CHANGEMENT_DENOMINATION',
  },
  {
    id: 'v1',
    dossierId: 'd1',
    ticketId: null,
    documentType: 'STATUTS',
    title: 'Statuts',
    version: 1,
    current: false,
    filename: 'statuts-v1.docx',
    contentType: null,
    sizeBytes: 100,
    createdAt: '2026-06-21T10:00:00Z',
    replacedAt: '2026-06-22T10:00:00Z',
    motif: 'Initial',
  },
];

beforeEach(() => {
  vi.spyOn(dataroomService, 'listVersions').mockResolvedValue(versions);
  vi.spyOn(dataroomService, 'downloadVersion').mockResolvedValue();
  vi.spyOn(dataroomService, 'restoreVersion').mockResolvedValue(versions[2]);
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('<DocumentVersionsDropdown>', () => {
  it('lazy-load à la 1re ouverture + n\'inclut pas la version active', async () => {
    render(<DocumentVersionsDropdown documentId={DOC_ID} canRestore={false} />);

    // Pas de fetch avant ouverture.
    expect(dataroomService.listVersions).not.toHaveBeenCalled();

    fireEvent.click(screen.getByTestId('versions-toggle'));

    await waitFor(() => {
      expect(dataroomService.listVersions).toHaveBeenCalledWith(DOC_ID);
    });
    // Versions v2 et v1 visibles ; v3 (active) absente.
    await waitFor(() => {
      expect(screen.getByTestId('version-row-v2')).toBeInTheDocument();
      expect(screen.getByTestId('version-row-v1')).toBeInTheDocument();
      expect(screen.queryByTestId('version-row-v3')).toBeNull();
    });
    // Motif rendu.
    expect(screen.getByText('Modification : CHANGEMENT_DENOMINATION')).toBeInTheDocument();
  });

  it('Télécharger câblé à downloadVersion(docId, versionId, filename)', async () => {
    render(<DocumentVersionsDropdown documentId={DOC_ID} canRestore={false} />);
    fireEvent.click(screen.getByTestId('versions-toggle'));
    await waitFor(() => screen.getByTestId('download-version-v2'));

    fireEvent.click(screen.getByTestId('download-version-v2'));
    await waitFor(() => {
      expect(dataroomService.downloadVersion).toHaveBeenCalledWith(
        DOC_ID,
        'v2',
        'statuts-v2.docx',
      );
    });
  });

  it('Bouton Restaurer absent si canRestore=false', async () => {
    render(<DocumentVersionsDropdown documentId={DOC_ID} canRestore={false} />);
    fireEvent.click(screen.getByTestId('versions-toggle'));
    await waitFor(() => screen.getByTestId('version-row-v2'));
    expect(screen.queryByTestId('restore-version-v2')).toBeNull();
  });

  it('Restaurer : PromptDialog motif + restoreVersion + reload + onChanged', async () => {
    const onChanged = vi.fn();
    const user = userEvent.setup();
    render(
      <DocumentVersionsDropdown
        documentId={DOC_ID}
        canRestore
        onChanged={onChanged}
      />,
    );
    fireEvent.click(screen.getByTestId('versions-toggle'));
    await waitFor(() => screen.getByTestId('restore-version-v1'));

    fireEvent.click(screen.getByTestId('restore-version-v1'));

    // Le dialog stylé (remplace window.prompt) apparaît : on saisit un motif.
    const field = await screen.findByLabelText(/Motif/i);
    await user.clear(field);
    await user.type(field, 'Retour à v1');
    await user.click(screen.getByRole('button', { name: 'Restaurer' }));

    await waitFor(() => {
      expect(dataroomService.restoreVersion).toHaveBeenCalledWith(
        DOC_ID,
        'v1',
        'Retour à v1',
      );
      expect(onChanged).toHaveBeenCalled();
    });
    // listVersions appelé 2 fois : initial open + reload après restore.
    expect(dataroomService.listVersions).toHaveBeenCalledTimes(2);
  });

  it('Restaurer annulé : fermeture du dialog → pas d\'appel', async () => {
    const user = userEvent.setup();
    render(<DocumentVersionsDropdown documentId={DOC_ID} canRestore />);
    fireEvent.click(screen.getByTestId('versions-toggle'));
    await waitFor(() => screen.getByTestId('restore-version-v1'));
    fireEvent.click(screen.getByTestId('restore-version-v1'));

    // Annuler le dialog stylé au lieu de confirmer.
    await user.click(await screen.findByRole('button', { name: 'Annuler' }));

    await new Promise((r) => setTimeout(r, 50));
    expect(dataroomService.restoreVersion).not.toHaveBeenCalled();
  });
});
