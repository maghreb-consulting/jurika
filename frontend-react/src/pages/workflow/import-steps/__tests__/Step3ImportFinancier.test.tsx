/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Step3ImportFinancier } from '../Step3ImportFinancier';
import { dataroomService } from '../../../../services/dataroom.service';

const DOSSIER_ID = 'dossier-import-1';

beforeEach(() => {
  vi.spyOn(dataroomService, 'uploadComptable').mockResolvedValue(undefined);
  vi.spyOn(dataroomService, 'uploadFiscal').mockResolvedValue({
    id: 'fisc-1',
    dossierId: DOSSIER_ID,
    exerciceFiscalId: 'ex-2023',
    categorie: 'TVA',
    sousClassification: 'DECLARATION_MENSUELLE',
    title: 'TVA',
    filename: 'TVA__BETA__2023.pdf',
    contentType: 'application/pdf',
    sizeBytes: 1024,
    createdAt: '2026-06-23T10:00:00Z',
    replacedAt: null,
  } as any);
});

afterEach(() => {
  vi.restoreAllMocks();
});

function fileOf(name: string): File {
  return new File(['x'], name, { type: 'application/pdf' });
}

describe('<Step3ImportFinancier>', () => {
  it('rend 2 panneaux ImportFolderUploader (COMPTABLE + FISCAL) avec guide de complétude', () => {
    render(
      <Step3ImportFinancier
        dossierId={DOSSIER_ID}
        denomination="Beta Conseil SARL"
        saving={false}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
      />,
    );
    expect(screen.getByTestId('completeness-COMPTABLE')).toBeInTheDocument();
    expect(screen.getByTestId('completeness-FISCAL')).toBeInTheDocument();
    // CONTENTIEUX exclu du guide fiscal en import (commentaire ≥ 20 chars requis).
    expect(
      screen.queryByTestId('completeness-item-FISCAL-CONTENTIEUX'),
    ).toBeNull();
    // CATEGORIE_COMPTABLE inclut AUTRE.
    expect(screen.getByTestId('completeness-item-COMPTABLE-AUTRE')).toBeInTheDocument();
  });

  it('Upload COMPTABLE renommé : <ANNEE>/<CAT>__<SLUG>.<ext>', async () => {
    render(
      <Step3ImportFinancier
        dossierId={DOSSIER_ID}
        denomination="Beta Conseil SARL"
        saving={false}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
      />,
    );
    const input = screen.getByTestId('import-file-input-COMPTABLE') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [fileOf('bilan.xlsx')] } });

    const row = (await screen.findByText('bilan.xlsx')).closest('li')!;
    const select = row.querySelector('select') as HTMLSelectElement;
    const year = row.querySelector('input[type="number"]') as HTMLInputElement;
    fireEvent.change(select, { target: { value: 'BANQUE' } });

    // Fix C3 (2026-08-16) — l'annee n'est plus pre-remplie : le depot attend
    // qu'elle soit CHOISIE, sinon une piece d'un exercice anterieur partait
    // classee en annee courante et la corriger ensuite ne changeait rien.
    await new Promise((r) => setTimeout(r, 50));
    expect(dataroomService.uploadComptable).not.toHaveBeenCalled();
    fireEvent.change(year, { target: { value: '2023' } });

    await waitFor(() => {
      expect(dataroomService.uploadComptable).toHaveBeenCalledTimes(1);
    });
    const [, payload] = (dataroomService.uploadComptable as any).mock.calls[0];
    expect(payload.categorie).toBe('BANQUE');
    expect(payload.file.name).toMatch(/^BANQUE__BETA_CONSEIL_SARL\.xlsx$/);
    expect(payload.title).toMatch(/^2023\/BANQUE__BETA_CONSEIL_SARL\.xlsx$/);
    expect(payload.annee).toBe(2023);
  });

  it('Upload FISCAL : annee brute + sousClassification par défaut (back crée l\'exercice)', async () => {
    render(
      <Step3ImportFinancier
        dossierId={DOSSIER_ID}
        denomination="Beta Conseil SARL"
        saving={false}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
      />,
    );
    const input = screen.getByTestId('import-file-input-FISCAL') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [fileOf('liasse.pdf')] } });

    const row = (await screen.findByText('liasse.pdf')).closest('li')!;
    const select = row.querySelector('select') as HTMLSelectElement;
    const year = row.querySelector('input[type="number"]') as HTMLInputElement;
    fireEvent.change(select, { target: { value: 'TVA' } });
    // Fix C3 — meme regle cote fiscal : l'annee doit etre choisie avant le depot.
    fireEvent.change(year, { target: { value: '2023' } });

    await waitFor(() => {
      expect(dataroomService.uploadFiscal).toHaveBeenCalledTimes(1);
    });
    const [, payload] = (dataroomService.uploadFiscal as any).mock.calls[0];
    expect(payload.categorie).toBe('TVA');
    expect(payload.sousClassification).toBe('DECLARATION_MENSUELLE');
    expect(payload.annee).toBe(2023);
    expect(payload.exerciceId).toBeUndefined();
    expect(payload.file.name).toMatch(/^TVA__BETA_CONSEIL_SARL\.pdf$/);
  });

  it('dossierId absent : input désactivé, pas d\'appel d\'upload', () => {
    render(
      <Step3ImportFinancier
        dossierId={null}
        denomination="Beta"
        saving={false}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
      />,
    );
    const inputComp = screen.getByTestId('import-file-input-COMPTABLE') as HTMLInputElement;
    expect(inputComp.disabled).toBe(true);
    const inputFis = screen.getByTestId('import-file-input-FISCAL') as HTMLInputElement;
    expect(inputFis.disabled).toBe(true);
  });

  it('Bouton "Valider et continuer" toujours actif (dossier vide non bloquant)', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <Step3ImportFinancier
        dossierId={DOSSIER_ID}
        denomination="Beta Conseil SARL"
        saving={false}
        onSubmit={onSubmit}
      />,
    );
    const submit = screen.getByTestId('step3-submit');
    expect(submit).not.toBeDisabled();
    fireEvent.click(submit);
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    const payload = onSubmit.mock.calls[0][0];
    expect(payload).toHaveProperty('financier.documentsFinanciers');
    expect(payload).toHaveProperty('financier.documentsFiscaux');
  });
});
