import { describe, expect, it } from 'vitest';
import {
  buildDocFilename,
  docTypeForTemplateCode,
  extensionOf,
  formeLabel,
} from '../workflowFilename';

describe('workflowFilename — buildDocFilename', () => {
  it('nomme `type - dénomination - forme.docx` sans suffixe quand pas de version', () => {
    expect(buildDocFilename('PV — Modification (AGE)', 'PARACOSME', 'SARL')).toBe(
      'PV — Modification (AGE) - PARACOSME - SARL.docx',
    );
  });

  it('ajoute `- v<n>` uniquement quand un document de même type+nom existe déjà', () => {
    expect(buildDocFilename('Statuts', 'PARACOSME', 'SARL', 2)).toBe(
      'Statuts - PARACOSME - SARL - v2.docx',
    );
    // version 1 (ou absente) → pas de suffixe.
    expect(buildDocFilename('Statuts', 'PARACOSME', 'SARL', 1)).toBe('Statuts - PARACOSME - SARL.docx');
    expect(buildDocFilename('Statuts', 'PARACOSME', 'SARL')).toBe('Statuts - PARACOSME - SARL.docx');
  });

  it('normalise la forme SARL_AU → « SARL AU »', () => {
    expect(buildDocFilename('Statuts', 'X', 'SARL_AU', 3)).toBe('Statuts - X - SARL AU - v3.docx');
  });

  it('conserve une extension personnalisée (pièces jointes PDF/image)', () => {
    expect(buildDocFilename('Statuts légalisés', 'PARACOSME', 'SARL', undefined, '.pdf')).toBe(
      'Statuts légalisés - PARACOSME - SARL.pdf',
    );
  });

  it('nettoie les caractères interdits et remplace une dénomination vide', () => {
    expect(buildDocFilename('PV/Modif', 'A:B*C', 'SARL')).toBe('PV Modif - A B C - SARL.docx');
    expect(buildDocFilename('Statuts', '', 'SARL')).toBe('Statuts - Societe - SARL.docx');
  });
});

describe('workflowFilename — helpers', () => {
  it('formeLabel', () => {
    expect(formeLabel('SARL')).toBe('SARL');
    expect(formeLabel('SARL_AU')).toBe('SARL AU');
  });

  it('extensionOf', () => {
    expect(extensionOf('scan.PDF')).toBe('.pdf');
    expect(extensionOf('photo.jpeg')).toBe('.jpeg');
    expect(extensionOf('sansext')).toBe('.docx');
  });

  it('docTypeForTemplateCode mappe les codes template vers un libellé', () => {
    expect(docTypeForTemplateCode('PV_MODIFICATION_SARL')).toBe('PV — Modification (AGE)');
    expect(docTypeForTemplateCode('STATUTS_REFONDUS_SARL_AU')).toBe('Statuts');
    expect(docTypeForTemplateCode('CONVOCATION_AG')).toBe('Convocation');
    expect(docTypeForTemplateCode('FEUILLE_PRESENCE_AG')).toBe('Feuille de présence');
    expect(docTypeForTemplateCode('PV_DEFAUT_QUORUM_SARL')).toBe('PV — Défaut de quorum');
    expect(docTypeForTemplateCode('PV_IRREGULARITE_CONVOCATION_SARL')).toBe(
      'PV — Irrégularité de convocation',
    );
  });
});
