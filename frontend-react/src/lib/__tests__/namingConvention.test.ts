/// <reference types="vitest" />
import { describe, it, expect } from 'vitest';
import {
  extensionOf,
  forComptable,
  forFiscal,
  forJuridique,
  slugDenomination,
} from '../namingConvention';

describe('namingConvention', () => {
  it('slugDenomination : accents + ponctuation + espaces → MAJ_UNDERSCORE', () => {
    expect(slugDenomination('Atlas Société SARL')).toBe('ATLAS_SOCIETE_SARL');
    expect(slugDenomination('Société à Responsabilité Limitée')).toBe(
      'SOCIETE_A_RESPONSABILITE_LIMITEE',
    );
    expect(slugDenomination('  J&K Conseil — 2026  ')).toBe('J_K_CONSEIL_2026');
  });

  it('slugDenomination : null / vide / caractères filtrés → SOCIETE', () => {
    expect(slugDenomination(null)).toBe('SOCIETE');
    expect(slugDenomination(undefined)).toBe('SOCIETE');
    expect(slugDenomination('')).toBe('SOCIETE');
    expect(slugDenomination('   ')).toBe('SOCIETE');
    expect(slugDenomination('---!')).toBe('SOCIETE');
  });

  it('extensionOf : extrait + lowercase + bin fallback', () => {
    expect(extensionOf('bilan-2023.XLSX')).toBe('xlsx');
    expect(extensionOf('statuts.docx')).toBe('docx');
    expect(extensionOf('noext')).toBe('bin');
    expect(extensionOf('trailing.')).toBe('bin');
    expect(extensionOf(null)).toBe('bin');
  });

  it('forJuridique : avec date', () => {
    expect(
      forJuridique({
        documentType: 'STATUTS',
        denominationSlug: 'ATLAS_SARL',
        dateOrYear: '2026-06-23',
        extension: 'pdf',
      }),
    ).toBe('STATUTS__ATLAS_SARL__2026-06-23.pdf');
  });

  it('forJuridique : sans date', () => {
    expect(
      forJuridique({
        documentType: 'PV_AGE',
        denominationSlug: 'BETA_SOCIETE',
        extension: 'docx',
      }),
    ).toBe('PV_AGE__BETA_SOCIETE.docx');
  });

  it('forJuridique : fallbacks (type null → AUTRE ; slug vide → SOCIETE ; ext vide → bin)', () => {
    expect(
      forJuridique({
        documentType: '',
        denominationSlug: '',
        extension: '',
      }),
    ).toBe('AUTRE__SOCIETE.bin');
  });

  it('forComptable : <ANNEE>/<CAT>__<SLUG>.<ext>', () => {
    expect(
      forComptable({
        annee: 2023,
        categorie: 'BANQUE',
        denominationSlug: 'ATLAS_SARL',
        extension: 'xlsx',
      }),
    ).toBe('2023/BANQUE__ATLAS_SARL.xlsx');
  });

  it('forComptable : année null → 0000 ; categorie lower → upper', () => {
    expect(
      forComptable({
        annee: null,
        categorie: 'ventes',
        denominationSlug: 'X',
        extension: 'csv',
      }),
    ).toBe('0000/VENTES__X.csv');
  });

  it('forFiscal : même format que comptable', () => {
    expect(
      forFiscal({
        annee: 2024,
        categorie: 'TVA',
        denominationSlug: 'BETA_SOCIETE',
        extension: 'pdf',
      }),
    ).toBe('2024/TVA__BETA_SOCIETE.pdf');
  });
});
