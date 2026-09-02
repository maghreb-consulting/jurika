/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MultiUploadDrawer } from '../MultiUploadDrawer';
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
    contentType: 'application/pdf',
    sizeBytes: 12345,
    createdAt: '2026-06-23T10:00:00Z',
    replacedAt: null,
    motif: null,
  },
];

function fileOf(name: string): File {
  return new File(['hello'], name, { type: 'application/pdf' });
}

beforeEach(() => {
  vi.spyOn(dataroomService, 'uploadJuridique').mockResolvedValue();
  vi.spyOn(dataroomService, 'replaceAsNewVersion').mockResolvedValue({
    ...activeDocuments[0],
    id: 'doc-statuts-v4',
    version: 4,
  });
});

afterEach(() => vi.restoreAllMocks());

function setup() {
  const onUploaded = vi.fn();
  const onClose = vi.fn();
  render(
    <MultiUploadDrawer
      open
      dossierId={DOSSIER_ID}
      activeDocuments={activeDocuments}
      onClose={onClose}
      onUploaded={onUploaded}
    />,
  );
  return { onUploaded, onClose };
}

describe('<MultiUploadDrawer>', () => {
  it('chaque fichier en « Nouveau document » par défaut → uploadJuridique par fichier', async () => {
    const { onUploaded } = setup();

    fireEvent.change(screen.getByTestId('multi-file-input'), {
      target: { files: [fileOf('contrat.pdf'), fileOf('annexe.pdf')] },
    });

    fireEvent.click(screen.getByTestId('multi-submit'));

    await waitFor(() => {
      expect(dataroomService.uploadJuridique).toHaveBeenCalledTimes(2);
    });
    // Titre dérivé du nom de fichier, type AUTRE par défaut.
    expect(dataroomService.uploadJuridique).toHaveBeenCalledWith(DOSSIER_ID, {
      file: expect.any(File),
      documentType: 'AUTRE',
      title: 'contrat',
    });
    expect(dataroomService.replaceAsNewVersion).not.toHaveBeenCalled();
    expect(onUploaded).toHaveBeenCalled();
    expect(screen.getByTestId('multi-recap')).toHaveTextContent('2 fichier(s) déposé(s)');
  });

  it('« Nouvelle version de … » : submit bloqué tant que le motif < 5 car., puis replaceAsNewVersion', async () => {
    setup();

    fireEvent.change(screen.getByTestId('multi-file-input'), {
      target: { files: [fileOf('statuts-v4.pdf')] },
    });

    // Récupère l'id de ligne via le testid de l'action select.
    const actionSelect = screen.getByTestId(/^multi-action-/) as HTMLSelectElement;
    fireEvent.change(actionSelect, { target: { value: 'doc-statuts' } });

    const motifField = screen.getByTestId(/^multi-motif-/);
    // Pas de motif → bloqué.
    expect(screen.getByTestId('multi-submit')).toBeDisabled();
    fireEvent.change(motifField, { target: { value: 'abc' } });
    expect(screen.getByTestId('multi-submit')).toBeDisabled();
    fireEvent.change(motifField, { target: { value: 'Version corrigee' } });
    expect(screen.getByTestId('multi-submit')).not.toBeDisabled();

    fireEvent.click(screen.getByTestId('multi-submit'));

    await waitFor(() => {
      expect(dataroomService.replaceAsNewVersion).toHaveBeenCalledTimes(1);
    });
    expect(dataroomService.replaceAsNewVersion).toHaveBeenCalledWith(
      'doc-statuts',
      expect.any(File),
      'Version corrigee',
    );
    expect(dataroomService.uploadJuridique).not.toHaveBeenCalled();
  });

  it('mix nouveau + version dans le même lot', async () => {
    setup();

    fireEvent.change(screen.getByTestId('multi-file-input'), {
      target: { files: [fileOf('nouveau.pdf'), fileOf('statuts-v4.pdf')] },
    });

    // 2e ligne -> version de doc-statuts.
    const actionSelects = screen.getAllByTestId(/^multi-action-/);
    fireEvent.change(actionSelects[1], { target: { value: 'doc-statuts' } });
    const motifField = screen.getByTestId(/^multi-motif-/);
    fireEvent.change(motifField, { target: { value: 'Mise a jour statuts' } });

    fireEvent.click(screen.getByTestId('multi-submit'));

    await waitFor(() => {
      expect(dataroomService.uploadJuridique).toHaveBeenCalledTimes(1);
      expect(dataroomService.replaceAsNewVersion).toHaveBeenCalledTimes(1);
    });
  });

  it('échec partiel : recap signale les erreurs et onUploaded est tout de même appelé', async () => {
    const { onUploaded } = setup();
    vi.mocked(dataroomService.uploadJuridique)
      .mockResolvedValueOnce()
      .mockRejectedValueOnce(new Error('boom'));

    fireEvent.change(screen.getByTestId('multi-file-input'), {
      target: { files: [fileOf('ok.pdf'), fileOf('ko.pdf')] },
    });

    fireEvent.click(screen.getByTestId('multi-submit'));

    await waitFor(() => {
      expect(screen.getByTestId('multi-recap')).toHaveTextContent('1 échec(s)');
    });
    expect(onUploaded).toHaveBeenCalled();
  });
});
