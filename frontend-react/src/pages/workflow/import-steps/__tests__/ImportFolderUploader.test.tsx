/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ImportFolderUploader } from '../ImportFolderUploader';
import type { FolderCategoryOption } from '../ImportFolderUploader';

const JURIDIQUE_CATS: FolderCategoryOption[] = [
  { value: 'STATUTS', label: 'Statuts' },
  { value: 'PV_AGE', label: 'PV AGE' },
  { value: 'RC', label: 'RC' },
  { value: 'AUTRE', label: 'Autre' },
];

const COMPTABLE_CATS: FolderCategoryOption[] = [
  { value: 'ACHATS', label: 'Achats' },
  { value: 'VENTES', label: 'Ventes' },
  { value: 'BANQUE', label: 'Banque' },
];

function fileOf(name: string, content = 'x'): File {
  return new File([content], name, { type: 'application/octet-stream' });
}

beforeEach(() => {
  if (typeof crypto === 'undefined' || !('randomUUID' in crypto)) {
    // jsdom : crypto.randomUUID dispo en Node 19+, sinon fallback déjà géré côté composant.
  }
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('<ImportFolderUploader>', () => {
  it('Vide : message "non bloquant" + completeness 0/N ; pas d\'appel à onUpload', () => {
    const onUpload = vi.fn().mockResolvedValue(undefined);
    render(
      <ImportFolderUploader
        kind="JURIDIQUE"
        categories={JURIDIQUE_CATS}
        denomination="Atlas SARL"
        onUpload={onUpload}
      />,
    );
    expect(screen.getByText(/Aucun fichier ajouté/i)).toBeInTheDocument();
    // Toutes les cases ⬜.
    JURIDIQUE_CATS.forEach((c) => {
      const item = screen.getByTestId(`completeness-item-JURIDIQUE-${c.value}`);
      expect(item.getAttribute('data-done')).toBe('false');
    });
    expect(onUpload).not.toHaveBeenCalled();
  });

  it('JURIDIQUE : ajouter fichier sans catégorie -> pas d\'upload (PENDING_META)', async () => {
    const onUpload = vi.fn().mockResolvedValue(undefined);
    render(
      <ImportFolderUploader
        kind="JURIDIQUE"
        categories={JURIDIQUE_CATS}
        denomination="Atlas SARL"
        onUpload={onUpload}
      />,
    );
    const input = screen.getByTestId('import-file-input-JURIDIQUE') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [fileOf('statuts.pdf')] } });

    // Le fichier apparaît dans la liste.
    await screen.findByText('statuts.pdf');
    // Petite attente pour s'assurer que l'auto-upload n'a PAS été déclenché.
    await new Promise((r) => setTimeout(r, 50));
    expect(onUpload).not.toHaveBeenCalled();
  });

  it('JURIDIQUE : choix catégorie déclenche upload + complétude passe à ✔', async () => {
    const onUpload = vi.fn().mockResolvedValue(undefined);
    render(
      <ImportFolderUploader
        kind="JURIDIQUE"
        categories={JURIDIQUE_CATS}
        denomination="Atlas SARL"
        onUpload={onUpload}
      />,
    );
    const input = screen.getByTestId('import-file-input-JURIDIQUE') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [fileOf('statuts.pdf')] } });
    const row = await screen.findByText('statuts.pdf');
    const li = row.closest('li')!;
    const catSelect = li.querySelector('select') as HTMLSelectElement;

    fireEvent.change(catSelect, { target: { value: 'STATUTS' } });

    await waitFor(() => expect(onUpload).toHaveBeenCalledTimes(1));
    const [renamedFile, meta] = onUpload.mock.calls[0];
    expect(renamedFile).toBeInstanceOf(File);
    expect((renamedFile as File).name).toMatch(/^STATUTS__ATLAS_SARL/);
    expect((renamedFile as File).name).toMatch(/\.pdf$/);
    expect(meta).toMatchObject({
      category: 'STATUTS',
      annee: null,
      canonicalName: expect.stringMatching(/^STATUTS__ATLAS_SARL/),
    });

    // Le badge "Déposé" est visible.
    await screen.findByText(/Déposé/i);
    // La case STATUTS passe en ✔.
    await waitFor(() => {
      const statuts = screen.getByTestId('completeness-item-JURIDIQUE-STATUTS');
      expect(statuts.getAttribute('data-done')).toBe('true');
    });
  });

  it('COMPTABLE : année requise -> sans année pas d\'upload, avec année OK', async () => {
    const onUpload = vi.fn().mockResolvedValue(undefined);
    render(
      <ImportFolderUploader
        kind="COMPTABLE"
        categories={COMPTABLE_CATS}
        denomination="Beta SA"
        yearRequired
        yearMin={2000}
        yearMax={2099}
        onUpload={onUpload}
      />,
    );
    const input = screen.getByTestId('import-file-input-COMPTABLE') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [fileOf('bilan-2023.xlsx')] } });
    const li = (await screen.findByText('bilan-2023.xlsx')).closest('li')!;
    const select = li.querySelector('select') as HTMLSelectElement;
    const year = li.querySelector('input[type="number"]') as HTMLInputElement;

    /*
     * Fix C3 (2026-08-16) — ATTENTE CORRIGÉE.
     *
     * Ce test exigeait auparavant que le dépôt parte DÈS le choix de la catégorie,
     * « l'année restant valide (currentYear) ». C'était précisément le défaut : une
     * pièce d'un exercice antérieur était classée en année courante, et corriger
     * l'année ensuite ne changeait plus rien — le fichier était déjà déposé.
     * L'année n'est donc plus pré-remplie : le dépôt attend qu'elle soit choisie.
     */
    fireEvent.change(select, { target: { value: 'VENTES' } });
    await new Promise((r) => setTimeout(r, 50));
    expect(onUpload).not.toHaveBeenCalled();

    fireEvent.change(year, { target: { value: '2023' } });
    await waitFor(() => expect(onUpload).toHaveBeenCalledTimes(1));
    const [renamed] = onUpload.mock.calls[0];
    expect((renamed as File).name).toMatch(/^VENTES__BETA_SA\.xlsx$/);
    // Le canonicalName porte l'année CHOISIE, pas un défaut implicite.
    const meta = onUpload.mock.calls[0][1];
    expect(meta.annee).toBe(2023);
    expect(meta.canonicalName).toMatch(/^2023\/VENTES__BETA_SA\.xlsx$/);

    // 2e fichier sans année (vidée).
    onUpload.mockClear();
    fireEvent.change(input, { target: { files: [fileOf('grand-livre.xlsx')] } });
    const li2 = (await screen.findByText('grand-livre.xlsx')).closest('li')!;
    const yearInput2 = li2.querySelector('input[type="number"]') as HTMLInputElement;
    fireEvent.change(yearInput2, { target: { value: '' } });
    const sel2 = li2.querySelector('select') as HTMLSelectElement;
    fireEvent.change(sel2, { target: { value: 'BANQUE' } });
    // Sans année : upload bloqué.
    await new Promise((r) => setTimeout(r, 50));
    expect(onUpload).not.toHaveBeenCalled();
    // Renseigner l'année -> upload se déclenche.
    fireEvent.change(yearInput2, { target: { value: '2022' } });
    await waitFor(() => expect(onUpload).toHaveBeenCalledTimes(1));
    expect(onUpload.mock.calls[0][1].annee).toBe(2022);
  });

  it('Erreur d\'upload : badge "Echec" + message dans la liste', async () => {
    const onUpload = vi.fn().mockRejectedValue(new Error('Boom'));
    render(
      <ImportFolderUploader
        kind="JURIDIQUE"
        categories={JURIDIQUE_CATS}
        denomination="Atlas"
        onUpload={onUpload}
      />,
    );
    const input = screen.getByTestId('import-file-input-JURIDIQUE') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [fileOf('foo.pdf')] } });
    const li = (await screen.findByText('foo.pdf')).closest('li')!;
    const select = li.querySelector('select') as HTMLSelectElement;
    fireEvent.change(select, { target: { value: 'STATUTS' } });

    await screen.findByText(/Echec/i);
    expect(li.textContent).toContain('Boom');
  });
});
