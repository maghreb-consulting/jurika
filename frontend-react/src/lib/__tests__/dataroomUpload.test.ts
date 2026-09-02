/// <reference types="vitest" />
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  isMotifValid,
  MOTIF_MIN_LENGTH,
  stripExtension,
  uploadOrReplace,
} from '../dataroomUpload';
import { dataroomService } from '../../services/dataroom.service';

function fileOf(name: string): File {
  return new File(['x'], name, { type: 'application/pdf' });
}

beforeEach(() => {
  vi.spyOn(dataroomService, 'uploadJuridique').mockResolvedValue();
  vi.spyOn(dataroomService, 'replaceAsNewVersion').mockResolvedValue({
    id: 'v2',
    dossierId: 'd1',
    ticketId: null,
    documentType: 'STATUTS',
    title: 'Statuts',
    version: 2,
    current: true,
    filename: 'statuts.pdf',
    contentType: 'application/pdf',
    sizeBytes: 1,
    createdAt: '2026-06-24T00:00:00Z',
    replacedAt: null,
    motif: 'maj',
  });
});

afterEach(() => vi.restoreAllMocks());

describe('isMotifValid', () => {
  it(`exige >= ${MOTIF_MIN_LENGTH} caractères (trimmés)`, () => {
    expect(isMotifValid()).toBe(false);
    expect(isMotifValid('   ')).toBe(false);
    expect(isMotifValid('abcd')).toBe(false);
    expect(isMotifValid('abcde')).toBe(true);
    expect(isMotifValid('  abcde  ')).toBe(true);
  });
});

describe('stripExtension', () => {
  it('retire la dernière extension', () => {
    expect(stripExtension('statuts.pdf')).toBe('statuts');
    expect(stripExtension('a.b.docx')).toBe('a.b');
    expect(stripExtension('sansext')).toBe('sansext');
  });
});

describe('uploadOrReplace', () => {
  it("mode 'new' : appelle uploadJuridique avec type + titre", async () => {
    await uploadOrReplace(fileOf('contrat.pdf'), {
      dossierId: 'd1',
      mode: 'new',
      documentType: 'CONTRAT_BAIL',
      title: 'Bail commercial',
    });
    expect(dataroomService.uploadJuridique).toHaveBeenCalledWith('d1', {
      file: expect.any(File),
      documentType: 'CONTRAT_BAIL',
      title: 'Bail commercial',
    });
    expect(dataroomService.replaceAsNewVersion).not.toHaveBeenCalled();
  });

  it("mode 'new' : titre dérivé du nom de fichier si vide, type AUTRE par défaut", async () => {
    await uploadOrReplace(fileOf('mon-doc.pdf'), { dossierId: 'd1', mode: 'new' });
    expect(dataroomService.uploadJuridique).toHaveBeenCalledWith('d1', {
      file: expect.any(File),
      documentType: 'AUTRE',
      title: 'mon-doc',
    });
  });

  it("mode 'new' : transmet ticketId seulement s'il est fourni", async () => {
    await uploadOrReplace(fileOf('a.pdf'), {
      dossierId: 'd1',
      mode: 'new',
      title: 'A',
      ticketId: '  ticket-9  ',
    });
    expect(dataroomService.uploadJuridique).toHaveBeenCalledWith('d1', {
      file: expect.any(File),
      documentType: 'AUTRE',
      title: 'A',
      ticketId: 'ticket-9',
    });
  });

  it("mode 'version' : appelle replaceAsNewVersion avec motif trimmé", async () => {
    await uploadOrReplace(fileOf('statuts-v2.pdf'), {
      dossierId: 'd1',
      mode: 'version',
      targetDocId: 'doc-1',
      motif: '  Correction objet social  ',
    });
    expect(dataroomService.replaceAsNewVersion).toHaveBeenCalledWith(
      'doc-1',
      expect.any(File),
      'Correction objet social',
    );
    expect(dataroomService.uploadJuridique).not.toHaveBeenCalled();
  });

  it("mode 'version' : rejette sans cible", async () => {
    await expect(
      uploadOrReplace(fileOf('a.pdf'), {
        dossierId: 'd1',
        mode: 'version',
        motif: 'motif valide',
      }),
    ).rejects.toThrow(/cible/i);
    expect(dataroomService.replaceAsNewVersion).not.toHaveBeenCalled();
  });

  it("mode 'version' : rejette si motif trop court", async () => {
    await expect(
      uploadOrReplace(fileOf('a.pdf'), {
        dossierId: 'd1',
        mode: 'version',
        targetDocId: 'doc-1',
        motif: 'abc',
      }),
    ).rejects.toThrow(/motif/i);
    expect(dataroomService.replaceAsNewVersion).not.toHaveBeenCalled();
  });
});
