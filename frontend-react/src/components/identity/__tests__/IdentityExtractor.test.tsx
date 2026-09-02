/// <reference types="vitest" />
import '@testing-library/jest-dom/vitest';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { IdentityExtractor } from '../IdentityExtractor';
import type { ExtractedIdentity } from '../../../types/identity';

// Mock URL.createObjectURL (jsdom n'en a pas).
beforeEach(() => {
  // @ts-expect-error — assignement test
  window.URL.createObjectURL = vi.fn(() => 'blob:fake');
  // @ts-expect-error — assignement test
  window.URL.revokeObjectURL = vi.fn();
});

function pngFile(name = 'cin.png'): File {
  return new File([new Uint8Array([1, 2, 3])], name, { type: 'image/png' });
}

const CIN_RECTO_OK: ExtractedIdentity = {
  type: 'NOUVELLE',
  fields: {
    nom: 'BENATIK',
    prenom: 'OUSSAMA',
    cin: 'AB123456',
    adresse: '12 RUE X, CASABLANCA',
    date_naissance: '12.05.1990',
    sexe: 'M',
    nationalite: 'MAR',
  },
  source: 'merged',
  warnings: ['cin_format'],
  archivedDocumentId: '11111111-2222-3333-4444-555555555555',
};

const CN_OK: ExtractedIdentity = {
  type: 'CN',
  fields: {
    numero_cn: '98765',
    denomination: 'ATLAS HOLDING SA',
    ice: '001234567000077',
    beneficiaire: 'Karim ATLAS',
    activite: 'Negoce general',
    tribunal: 'Casablanca',
    date_expiration: '31.12.2030',
    date_delivrance: '01.01.2025',
  },
  source: 'kie',
  warnings: [],
  archivedDocumentId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
};

describe('<IdentityExtractor> mode="cin"', () => {
  it('rend recto + verso + toggle [Ancienne | Nouvelle] sans aucune option CN', () => {
    render(<IdentityExtractor mode="cin" onApply={vi.fn()} />);
    expect(screen.getByTestId('identity-extractor')).toBeInTheDocument();
    // Toggle exactement 2 boutons.
    expect(screen.getByTestId('id-type-NOUVELLE')).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByTestId('id-type-ANCIENNE')).toHaveAttribute('aria-checked', 'false');
    // AUCUN bouton CN visible.
    expect(screen.queryByTestId('id-type-CN')).not.toBeInTheDocument();
    // 2 dropzones (recto + verso) presents via inputs (uniques).
    expect(document.getElementById('id-recto-input')).toBeInTheDocument();
    expect(document.getElementById('id-verso-input')).toBeInTheDocument();
    // Extraire desactive sans recto.
    expect(screen.getByTestId('id-extract-btn')).toBeDisabled();
  });

  it('mode "cin" est le mode par defaut (omission de la prop)', () => {
    render(<IdentityExtractor onApply={vi.fn()} />);
    expect(screen.getByTestId('id-type-NOUVELLE')).toBeInTheDocument();
    expect(screen.queryByTestId('id-type-CN')).not.toBeInTheDocument();
  });

  it('toggle Ancienne change le type envoye au service', async () => {
    const extractFn = vi.fn().mockResolvedValue(CIN_RECTO_OK);

    render(<IdentityExtractor mode="cin" onApply={vi.fn()} extractFn={extractFn} />);
    fireEvent.click(screen.getByTestId('id-type-ANCIENNE'));

    fireEvent.change(document.getElementById('id-recto-input') as HTMLInputElement, {
      target: { files: [pngFile('cin.png')] },
    });
    fireEvent.click(screen.getByTestId('id-extract-btn'));

    await waitFor(() => expect(extractFn).toHaveBeenCalledTimes(1));
    const call = extractFn.mock.calls[0][0];
    expect(call.type).toBe('ANCIENNE');
  });

  it('succes : champs PHYSIQUE pre-remplis + Appliquer transmet aussi meta archive', async () => {
    const extractFn = vi.fn().mockResolvedValue(CIN_RECTO_OK);
    const onApply = vi.fn();

    render(
      <IdentityExtractor
        mode="cin"
        onApply={onApply}
        extractFn={extractFn}
        dossierId="dossier-xyz"
      />,
    );

    fireEvent.change(document.getElementById('id-recto-input') as HTMLInputElement, {
      target: { files: [pngFile('recto.png')] },
    });
    fireEvent.click(screen.getByTestId('id-extract-btn'));

    await screen.findByTestId('id-extracted-panel');
    expect((screen.getByTestId('id-field-nom') as HTMLInputElement).value).toBe('BENATIK');
    expect((screen.getByTestId('id-field-cin') as HTMLInputElement).value).toBe('AB123456');

    // Source merged -> badge "fusion" + archive visible.
    expect(screen.getByText(/fusion/i)).toBeInTheDocument();
    expect(screen.getByTestId('id-archived')).toBeInTheDocument();

    // Edition human-in-the-loop avant Appliquer.
    fireEvent.change(screen.getByTestId('id-field-prenom'), {
      target: { value: 'OUSSAMA EDITE' },
    });
    fireEvent.click(screen.getByTestId('id-apply-btn'));

    expect(onApply).toHaveBeenCalledTimes(1);
    const [applied, meta] = onApply.mock.calls[0];
    expect(applied.nom).toBe('BENATIK');
    expect(applied.prenom).toBe('OUSSAMA EDITE');
    expect(applied.cin).toBe('AB123456');
    // Meta archive transmis au parent pour que l'ecran flag cinUploaded.
    // Depuis 2026-07-05, le meta remonte aussi le TYPE de piece retenu (toggle
    // interne Ancienne/Nouvelle) pour que l'ecran appelant puisse le persister.
    expect(meta).toEqual({
      archivedDocumentId: '11111111-2222-3333-4444-555555555555',
      source: 'merged',
      type: 'NOUVELLE',
    });
  });

  it("l'appel passe archive=true ET dossierId si dossierId est fourni", async () => {
    const extractFn = vi.fn().mockResolvedValue(CIN_RECTO_OK);
    render(
      <IdentityExtractor
        mode="cin"
        onApply={vi.fn()}
        extractFn={extractFn}
        dossierId="dossier-archive-test"
      />,
    );
    fireEvent.change(document.getElementById('id-recto-input') as HTMLInputElement, {
      target: { files: [pngFile('r.png')] },
    });
    fireEvent.click(screen.getByTestId('id-extract-btn'));
    await waitFor(() => expect(extractFn).toHaveBeenCalledTimes(1));
    const call = extractFn.mock.calls[0][0];
    expect(call.archive).toBe(true);
    expect(call.dossierId).toBe('dossier-archive-test');
  });
});

describe('<IdentityExtractor> mode="cn"', () => {
  it('rend UN SEUL upload, AUCUN toggle, AUCUN verso', () => {
    render(<IdentityExtractor mode="cn" onApply={vi.fn()} />);
    expect(screen.getByTestId('identity-extractor')).toBeInTheDocument();
    // Aucun bouton de toggle.
    expect(screen.queryByTestId('id-type-NOUVELLE')).not.toBeInTheDocument();
    expect(screen.queryByTestId('id-type-ANCIENNE')).not.toBeInTheDocument();
    expect(screen.queryByTestId('id-type-CN')).not.toBeInTheDocument();
    // Pas de verso.
    expect(screen.queryByText(/Verso/i)).not.toBeInTheDocument();
    // Une seule dropzone — verifie l'input via son id (unique).
    expect(document.getElementById('id-recto-input')).toBeInTheDocument();
    expect(document.getElementById('id-verso-input')).not.toBeInTheDocument();
    // Extraire desactive sans fichier.
    expect(screen.getByTestId('id-extract-btn')).toBeDisabled();
  });

  it('envoie type=CN avec verso null au service', async () => {
    const extractFn = vi.fn().mockResolvedValue(CN_OK);

    render(<IdentityExtractor mode="cn" onApply={vi.fn()} extractFn={extractFn} dossierId="d-1" />);
    fireEvent.change(document.getElementById('id-recto-input') as HTMLInputElement, {
      target: { files: [pngFile('cn.png')] },
    });
    fireEvent.click(screen.getByTestId('id-extract-btn'));

    await waitFor(() => expect(extractFn).toHaveBeenCalledTimes(1));
    const call = extractFn.mock.calls[0][0];
    expect(call.type).toBe('CN');
    expect(call.verso).toBeFalsy();
    expect(call.archive).toBe(true);
    expect(call.dossierId).toBe('d-1');
  });

  it('succes CN : champs CN affiches (numero_cn, ice, tribunal...) + meta archive', async () => {
    const extractFn = vi.fn().mockResolvedValue(CN_OK);
    const onApply = vi.fn();

    render(
      <IdentityExtractor mode="cn" onApply={onApply} extractFn={extractFn} dossierId="d-1" />,
    );
    fireEvent.change(document.getElementById('id-recto-input') as HTMLInputElement, {
      target: { files: [pngFile('cn.png')] },
    });
    fireEvent.click(screen.getByTestId('id-extract-btn'));

    await screen.findByTestId('id-extracted-panel');
    // Champs CN, PAS les champs CIN.
    expect((screen.getByTestId('id-field-numero_cn') as HTMLInputElement).value).toBe('98765');
    expect((screen.getByTestId('id-field-denomination') as HTMLInputElement).value).toBe('ATLAS HOLDING SA');
    expect((screen.getByTestId('id-field-ice') as HTMLInputElement).value).toBe('001234567000077');
    expect((screen.getByTestId('id-field-tribunal') as HTMLInputElement).value).toBe('Casablanca');
    // Pas de champ CIN PHYSIQUE.
    expect(screen.queryByTestId('id-field-nom')).not.toBeInTheDocument();
    expect(screen.queryByTestId('id-field-cin')).not.toBeInTheDocument();
    expect(screen.queryByTestId('id-field-sexe')).not.toBeInTheDocument();

    // Archive visible.
    expect(screen.getByTestId('id-archived')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('id-apply-btn'));
    const [applied, meta] = onApply.mock.calls[0];
    expect(applied.numero_cn).toBe('98765');
    expect(applied.denomination).toBe('ATLAS HOLDING SA');
    // En mode "cn" le type est fige a 'CN' et remonte tel quel dans le meta.
    expect(meta).toEqual({
      archivedDocumentId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
      source: 'kie',
      type: 'CN',
    });
  });
});

describe('<IdentityExtractor> degradation gracieuse', () => {
  it('erreur 503 (mode cin) : message clair + panel masque + saisie manuelle preservee', async () => {
    const err = { response: { status: 503 } };
    const extractFn = vi.fn().mockRejectedValue(err);

    render(<IdentityExtractor mode="cin" onApply={vi.fn()} extractFn={extractFn as never} />);
    fireEvent.change(document.getElementById('id-recto-input') as HTMLInputElement, {
      target: { files: [pngFile('r.png')] },
    });
    fireEvent.click(screen.getByTestId('id-extract-btn'));

    const alert = await screen.findByTestId('id-error');
    expect(alert.textContent).toMatch(/indisponible/i);
    expect(alert.textContent).toMatch(/manuellement/i);
    expect(screen.queryByTestId('id-extracted-panel')).not.toBeInTheDocument();
  });

  it('erreur 503 (mode cn) : message clair + panel masque', async () => {
    const err = { response: { status: 503 } };
    const extractFn = vi.fn().mockRejectedValue(err);

    render(<IdentityExtractor mode="cn" onApply={vi.fn()} extractFn={extractFn as never} />);
    fireEvent.change(document.getElementById('id-recto-input') as HTMLInputElement, {
      target: { files: [pngFile('cn.png')] },
    });
    fireEvent.click(screen.getByTestId('id-extract-btn'));

    const alert = await screen.findByTestId('id-error');
    expect(alert.textContent).toMatch(/indisponible/i);
    expect(screen.queryByTestId('id-extracted-panel')).not.toBeInTheDocument();
  });
});

describe('<IdentityExtractor> mode="cin" — mapping enrichi date_validite + date_naissance', () => {
  it("expose date_validite dans le panel editable et le transmet a onApply", async () => {
    const ok: ExtractedIdentity = {
      type: 'NOUVELLE',
      fields: {
        nom: 'BENATIK',
        prenom: 'OUSSAMA',
        cin: 'AB123456',
        date_naissance: '12.05.1990',
        lieu_naissance: 'CASABLANCA',
        date_validite: '14.11.2030',
        adresse: '12 RUE X',
        sexe: 'M',
        nationalite: 'MAR',
      },
      source: 'kie',
      warnings: [],
      archivedDocumentId: null,
    };
    const extractFn = vi.fn().mockResolvedValue(ok);
    const onApply = vi.fn();

    render(
      <IdentityExtractor mode="cin" onApply={onApply} extractFn={extractFn} />,
    );
    fireEvent.change(document.getElementById('id-recto-input') as HTMLInputElement, {
      target: { files: [pngFile('r.png')] },
    });
    fireEvent.click(screen.getByTestId('id-extract-btn'));

    await screen.findByTestId('id-extracted-panel');
    // Champ "CIN valable jusqu'au" expose et pre-rempli.
    const dateValiditeInput = screen.getByTestId('id-field-date_validite') as HTMLInputElement;
    expect(dateValiditeInput).toBeInTheDocument();
    expect(dateValiditeInput.value).toBe('14.11.2030');
    // date_naissance toujours expose.
    expect((screen.getByTestId('id-field-date_naissance') as HTMLInputElement).value).toBe(
      '12.05.1990',
    );

    fireEvent.click(screen.getByTestId('id-apply-btn'));
    const [applied] = onApply.mock.calls[0];
    expect(applied.date_naissance).toBe('12.05.1990');
    expect(applied.date_validite).toBe('14.11.2030');
  });

  it("l'utilisateur peut editer date_validite avant Appliquer (human-in-the-loop)", async () => {
    const ok: ExtractedIdentity = {
      type: 'NOUVELLE',
      fields: {
        cin: 'AB123456',
        date_validite: '14.11.2030',
      },
      source: 'kie',
      warnings: [],
      archivedDocumentId: null,
    };
    const onApply = vi.fn();
    render(
      <IdentityExtractor
        mode="cin"
        onApply={onApply}
        extractFn={vi.fn().mockResolvedValue(ok) as never}
      />,
    );
    fireEvent.change(document.getElementById('id-recto-input') as HTMLInputElement, {
      target: { files: [pngFile('r.png')] },
    });
    fireEvent.click(screen.getByTestId('id-extract-btn'));
    await screen.findByTestId('id-extracted-panel');

    // Correction manuelle de la date avant validation.
    fireEvent.change(screen.getByTestId('id-field-date_validite'), {
      target: { value: '15.11.2030' },
    });
    fireEvent.click(screen.getByTestId('id-apply-btn'));

    expect(onApply.mock.calls[0][0].date_validite).toBe('15.11.2030');
  });
});
