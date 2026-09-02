/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { UploadDocumentDialog } from '../UploadDocumentDialog';
import { dataroomService } from '../../../../services/dataroom.service';
import type { DocumentSummary } from '../../../../types/dataroom';

const DOSSIER_ID = 'dossier-1';

const activeDocuments: DocumentSummary[] = [
  {
    id: 'doc-statuts',
    dossierId: DOSSIER_ID,
    ticketId: null,
    documentType: 'STATUTS',
    title: 'Statuts SARL',
    version: 3,
    current: true,
    filename: 'statuts-v3.docx',
    contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    sizeBytes: 12345,
    createdAt: '2026-06-23T10:00:00Z',
    replacedAt: null,
    motif: null,
  },
];

beforeEach(() => {
  vi.spyOn(dataroomService, 'uploadJuridique').mockResolvedValue();
  vi.spyOn(dataroomService, 'replaceAsNewVersion').mockResolvedValue({
    ...activeDocuments[0],
    id: 'doc-statuts-v4',
    version: 4,
    motif: 'Correction typo',
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

function fileOf(name: string, content = 'hello'): File {
  return new File([content], name, { type: 'application/pdf' });
}

describe('<UploadDocumentDialog>', () => {
  it('« Nouveau document » : motif caché, soumet via uploadJuridique', async () => {
    const onUploaded = vi.fn();
    const onClose = vi.fn();
    render(
      <UploadDocumentDialog
        open
        dossierId={DOSSIER_ID}
        activeDocuments={activeDocuments}
        onClose={onClose}
        onUploaded={onUploaded}
      />,
    );

    // Le sélecteur est sur "Nouveau document" par défaut.
    expect(screen.getByTestId('upload-target-select')).toHaveValue('__NEW__');
    // Champ motif absent.
    expect(screen.queryByTestId('upload-motif')).toBeNull();

    fireEvent.change(screen.getByTestId('upload-new-title'), {
      target: { value: 'Nouveau document SARL' },
    });
    fireEvent.change(screen.getByTestId('upload-file-input'), {
      target: { files: [fileOf('nouveau.pdf')] },
    });

    fireEvent.click(screen.getByTestId('upload-submit'));

    await waitFor(() => {
      expect(dataroomService.uploadJuridique).toHaveBeenCalledTimes(1);
    });
    expect(dataroomService.uploadJuridique).toHaveBeenCalledWith(DOSSIER_ID, {
      file: expect.any(File),
      documentType: 'AUTRE',
      title: 'Nouveau document SARL',
    });
    expect(dataroomService.replaceAsNewVersion).not.toHaveBeenCalled();
    expect(onUploaded).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('« Nouvelle version de … » : motif requis, soumet via replaceAsNewVersion', async () => {
    const onUploaded = vi.fn();
    const onClose = vi.fn();
    render(
      <UploadDocumentDialog
        open
        dossierId={DOSSIER_ID}
        activeDocuments={activeDocuments}
        onClose={onClose}
        onUploaded={onUploaded}
      />,
    );

    fireEvent.change(screen.getByTestId('upload-target-select'), {
      target: { value: 'doc-statuts' },
    });

    // Le champ motif apparaît.
    const motifField = screen.getByTestId('upload-motif');
    expect(motifField).toBeInTheDocument();

    fireEvent.change(screen.getByTestId('upload-file-input'), {
      target: { files: [fileOf('statuts-v4.pdf')] },
    });
    // Submit bloqué tant que le motif est < 5 chars.
    expect(screen.getByTestId('upload-submit')).toBeDisabled();
    fireEvent.change(motifField, { target: { value: 'abc' } });
    expect(screen.getByTestId('upload-submit')).toBeDisabled();
    fireEvent.change(motifField, { target: { value: 'Correction typo' } });
    expect(screen.getByTestId('upload-submit')).not.toBeDisabled();

    fireEvent.click(screen.getByTestId('upload-submit'));

    await waitFor(() => {
      expect(dataroomService.replaceAsNewVersion).toHaveBeenCalledTimes(1);
    });
    expect(dataroomService.replaceAsNewVersion).toHaveBeenCalledWith(
      'doc-statuts',
      expect.any(File),
      'Correction typo',
    );
    expect(dataroomService.uploadJuridique).not.toHaveBeenCalled();
    expect(onUploaded).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('Submit désactivé sans fichier (Nouveau document)', () => {
    render(
      <UploadDocumentDialog
        open
        dossierId={DOSSIER_ID}
        activeDocuments={activeDocuments}
        onClose={vi.fn()}
        onUploaded={vi.fn()}
      />,
    );
    fireEvent.change(screen.getByTestId('upload-new-title'), {
      target: { value: 'Foo' },
    });
    expect(screen.getByTestId('upload-submit')).toBeDisabled();
  });
});
