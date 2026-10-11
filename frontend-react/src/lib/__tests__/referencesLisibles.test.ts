import { describe, expect, it } from 'vitest';
import { nomFichierDocument, referenceDocument } from '../referencesLisibles';

describe('Références lisibles (lot L4d, E6)', () => {
  it('un document se lit par son type, la société et sa version', () => {
    expect(referenceDocument('Statuts', 'PARACOSME', 2)).toBe('Statuts — PARACOSME — version 2');
  });

  it('le fichier téléchargé porte la référence, extension conservée', () => {
    expect(nomFichierDocument('Statuts — PARACOSME — version 2', 'STATUTS_SARL.docx'))
      .toBe('Statuts — PARACOSME — version 2.docx');
    expect(nomFichierDocument('PV — A/B — version 1', 'x.pdf')).toBe('PV — A B — version 1.pdf');
  });
});
