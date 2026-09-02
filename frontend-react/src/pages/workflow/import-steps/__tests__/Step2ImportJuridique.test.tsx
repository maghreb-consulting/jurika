/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Step2ImportJuridique } from '../Step2ImportJuridique';
import { dataroomService } from '../../../../services/dataroom.service';

const DOSSIER_ID = 'dossier-import-1';

beforeEach(() => {
  vi.spyOn(dataroomService, 'uploadJuridique').mockResolvedValue(undefined);
});

afterEach(() => {
  vi.restoreAllMocks();
});

function fileOf(name: string): File {
  return new File(['x'], name, { type: 'application/pdf' });
}

/** Ajoute un fichier via l'input caché de la zone de dépôt. */
function addFile(name: string) {
  const input = document.getElementById('import-files') as HTMLInputElement;
  fireEvent.change(input, { target: { files: [fileOf(name)] } });
}

describe('<Step2ImportJuridique> — type choisi par fichier', () => {
  it('pas de type -> pas d\'upload (le fichier reste en attente)', async () => {
    render(
      <Step2ImportJuridique
        dossierId={DOSSIER_ID}
        denomination="Atlas Conseil SARL"
        saving={false}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
      />,
    );

    addFile('document-mystere.pdf');

    // Le fichier apparaît dans la liste...
    await screen.findByText('document-mystere.pdf');
    // ...avec un libellé "Type a definir" et un avertissement global.
    expect(screen.getByText(/Type a definir/i)).toBeInTheDocument();
    expect(screen.getByTestId('untyped-warning')).toBeInTheDocument();

    // Laisse passer un tick : AUCUN upload ne doit avoir été déclenché.
    await new Promise((r) => setTimeout(r, 50));
    expect(dataroomService.uploadJuridique).not.toHaveBeenCalled();

    // La validation est bloquée tant qu'un fichier n'a pas de type.
    const submit = screen.getByRole('button', { name: /Valider et continuer/i });
    expect(submit).toBeDisabled();
  });

  it('type choisi (RC) -> upload avec ce type + nom canonique RC__…', async () => {
    render(
      <Step2ImportJuridique
        dossierId={DOSSIER_ID}
        denomination="Atlas Conseil SARL"
        saving={false}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
      />,
    );

    addFile('scan.pdf');
    const li = (await screen.findByText('scan.pdf')).closest('li')!;
    const select = li.querySelector('select') as HTMLSelectElement;

    fireEvent.change(select, { target: { value: 'RC' } });

    await waitFor(() =>
      expect(dataroomService.uploadJuridique).toHaveBeenCalledTimes(1),
    );
    const [dossierArg, params] = (dataroomService.uploadJuridique as any).mock
      .calls[0];
    expect(dossierArg).toBe(DOSSIER_ID);
    // Le type backend (RC) est respecté et le fichier est renommé en conséquence.
    expect(params.documentType).toBe('RC');
    expect(params.file.name).toMatch(/^RC__ATLAS_CONSEIL_SARL\.pdf$/);
    expect(params.title).toMatch(/^RC__ATLAS_CONSEIL_SARL$/);
  });

  it('le nom reflète le vrai type pour BAIL et IF (pas de STATUTS par défaut)', async () => {
    render(
      <Step2ImportJuridique
        dossierId={DOSSIER_ID}
        denomination="Atlas Conseil SARL"
        saving={false}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
      />,
    );

    addFile('bail.pdf');
    const liBail = (await screen.findByText('bail.pdf')).closest('li')!;
    fireEvent.change(liBail.querySelector('select') as HTMLSelectElement, {
      target: { value: 'BAIL' },
    });
    await waitFor(() =>
      expect(dataroomService.uploadJuridique).toHaveBeenCalledTimes(1),
    );

    addFile('identifiant.pdf');
    const liIf = (await screen.findByText('identifiant.pdf')).closest('li')!;
    fireEvent.change(liIf.querySelector('select') as HTMLSelectElement, {
      target: { value: 'IF' },
    });
    await waitFor(() =>
      expect(dataroomService.uploadJuridique).toHaveBeenCalledTimes(2),
    );

    const calls = (dataroomService.uploadJuridique as any).mock.calls;
    // BAIL -> documentType CONTRAT_BAIL + nom CONTRAT_BAIL__…
    expect(calls[0][1].documentType).toBe('CONTRAT_BAIL');
    expect(calls[0][1].file.name).toMatch(/^CONTRAT_BAIL__ATLAS_CONSEIL_SARL\.pdf$/);
    // IF -> documentType AUTRE + nom AUTRE__… ; surtout PAS STATUTS.
    expect(calls[1][1].documentType).toBe('AUTRE');
    expect(calls[1][1].file.name).not.toMatch(/STATUTS/);
  });

  it('upload unique par fichier : ajouter 2 fichiers ne déclenche aucun upload avant choix du type', async () => {
    render(
      <Step2ImportJuridique
        dossierId={DOSSIER_ID}
        denomination="Atlas Conseil SARL"
        saving={false}
        onSubmit={vi.fn().mockResolvedValue(undefined)}
      />,
    );

    addFile('a.pdf');
    addFile('b.pdf');
    await screen.findByText('a.pdf');
    await screen.findByText('b.pdf');

    await new Promise((r) => setTimeout(r, 50));
    expect(dataroomService.uploadJuridique).not.toHaveBeenCalled();

    // Typer un seul des deux -> un seul upload, et la validation reste bloquée
    // (l'autre fichier est toujours sans type).
    const liA = (await screen.findByText('a.pdf')).closest('li')!;
    fireEvent.change(liA.querySelector('select') as HTMLSelectElement, {
      target: { value: 'STATUTS' },
    });
    await waitFor(() =>
      expect(dataroomService.uploadJuridique).toHaveBeenCalledTimes(1),
    );
    expect(
      screen.getByRole('button', { name: /Valider et continuer/i }),
    ).toBeDisabled();
  });
});
