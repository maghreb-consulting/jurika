/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { DocumentEditor } from '../DocumentEditor';

// Mock mammoth — convertit toujours en HTML stable pour les tests.
vi.mock('mammoth', () => ({
  default: { convertToHtml: vi.fn() },
  convertToHtml: vi.fn().mockResolvedValue({ value: '<p>Hello docx</p>', messages: [] }),
}));

// Mock du service document (export/save).
vi.mock('../../../services/document.service', () => ({
  documentService: {
    exportHtmlAsDocx: vi.fn().mockResolvedValue(undefined),
    exportHtmlAsPdf: vi.fn().mockResolvedValue(undefined),
  },
}));

describe('<DocumentEditor>', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders empty editor area with toolbar buttons in edit mode', async () => {
    render(
      <DocumentEditor
        value="<p>Initial content</p>"
        filename="test.docx"
        onSave={vi.fn()}
      />,
    );
    // Toolbar buttons (Gras / Italique / .docx / .pdf) doivent être présents.
    expect(await screen.findByTitle('Gras')).toBeInTheDocument();
    expect(screen.getByTitle('Italique')).toBeInTheDocument();
    expect(screen.getByTitle('Souligné')).toBeInTheDocument();
    expect(screen.getByTitle('Titre 1')).toBeInTheDocument();
    expect(screen.getByTitle('Liste à puces')).toBeInTheDocument();
    expect(screen.getByTitle('Insérer un tableau')).toBeInTheDocument();
    expect(screen.getByText('Enregistrer')).toBeInTheDocument();
    expect(screen.getByText('.docx')).toBeInTheDocument();
    expect(screen.getByText('.pdf')).toBeInTheDocument();
  });

  it('hides toolbar in readOnly mode', async () => {
    render(
      <DocumentEditor
        value="<p>Read only</p>"
        filename="test.docx"
        readOnly
      />,
    );
    // Attend que l'éditeur monte.
    await waitFor(() => {
      expect(document.querySelector('.tiptap, .ProseMirror')).toBeInTheDocument();
    });
    // Pas de boutons.
    expect(screen.queryByTitle('Gras')).not.toBeInTheDocument();
    expect(screen.queryByText('Enregistrer')).not.toBeInTheDocument();
    expect(screen.queryByText('.docx')).not.toBeInTheDocument();
  });

  it('calls onSave with HTML when Enregistrer clicked', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    render(
      <DocumentEditor
        value="<p>Edited</p>"
        filename="statuts.docx"
        onSave={onSave}
      />,
    );
    const saveBtn = await screen.findByText('Enregistrer');
    fireEvent.click(saveBtn);
    await waitFor(() => {
      expect(onSave).toHaveBeenCalledTimes(1);
      const html = onSave.mock.calls[0][0] as string;
      expect(html).toContain('Edited');
    });
  });

  it('calls documentService.exportHtmlAsPdf when .pdf clicked', async () => {
    const { documentService } = await import('../../../services/document.service');
    render(
      <DocumentEditor
        value="<p>Pdf export test</p>"
        filename="acte.docx"
        title="Acte de nomination"
      />,
    );
    const pdfBtn = await screen.findByText('.pdf');
    fireEvent.click(pdfBtn);
    await waitFor(() => {
      expect(documentService.exportHtmlAsPdf).toHaveBeenCalledTimes(1);
    });
    const call = (documentService.exportHtmlAsPdf as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(call[0]).toContain('Pdf export test');
    expect(call[1]).toBe('acte.pdf'); // remplace extension par .pdf
    expect(call[2]).toBe('Acte de nomination');
  });

  it('calls documentService.exportHtmlAsDocx when .docx clicked', async () => {
    const { documentService } = await import('../../../services/document.service');
    render(
      <DocumentEditor
        value="<p>Docx export</p>"
        filename="jal.docx"
      />,
    );
    const docxBtn = await screen.findByText('.docx');
    fireEvent.click(docxBtn);
    await waitFor(() => {
      expect(documentService.exportHtmlAsDocx).toHaveBeenCalledTimes(1);
    });
  });
});
